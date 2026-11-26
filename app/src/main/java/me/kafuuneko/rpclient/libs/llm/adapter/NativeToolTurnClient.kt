package me.kafuuneko.rpclient.libs.llm.adapter

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.ImageInputCapabilityResolver
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMToolGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMToolProtocolException
import me.kafuuneko.rpclient.libs.llm.model.LLMToolStreamEvent
import me.kafuuneko.rpclient.libs.llm.model.LLMToolTurn
import me.kafuuneko.rpclient.libs.llm.model.requireValidToolTurn
import me.kafuuneko.rpclient.libs.llm.tool.ToolWireRequest
import me.kafuuneko.rpclient.libs.llm.tool.parseObject
import me.kafuuneko.rpclient.libs.media.MessageImageRuntime
import me.kafuuneko.rpclient.libs.room.repository.LLMRequestLogRepository
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 原生工具 HTTP 传输，供五个普通客户端独立委托。
 * - 共用取消感知网络与图片租约，任何异常都关闭连接及临时文件。
 * - 流式预览与完整可执行轮次分离，EOF 不补造协议终态。
 */
internal class NativeToolTurnClient(
    private val mHttp: OkHttpClient,
    private val mLogs: LLMRequestLogRepository,
    private val mProvider: LLMProviderConfig,
    private val mImages: MessageImageRuntime?,
    private val mCapabilities: ImageInputCapabilityResolver,
) {
    /** 非流式工具响应必须完整通过协议终态校验。 */
    suspend fun generate(request: LLMToolGenerationRequest): LLMToolTurn {
        val model = request.base.model ?: mProvider.model
        val http = prepare(request, false)
        var raw: String? = null
        var failure: Throwable? = null
        try {
            // 在解析与终态校验前保留原始响应，便于调试日志定位截断或协议错误。
            raw = mHttp.await(http.request, MAX_RESPONSE_BYTES)
            if (raw.toByteArray().size > MAX_RESPONSE_BYTES) throw LLMToolProtocolException("response_too_large")
            return ToolWireRequest.codec(mProvider.protocol)
                .decode(parseObject(raw), model)
                .requireValidToolTurn()
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            try {
                // 调试关闭时 trySaveLog 直接返回，避免构建日志正文或阻塞释放。
                mLogs.trySaveLog(
                    provider = mProvider,
                    model = model,
                    isStreaming = false,
                    requestJson = http.payloadJson,
                    responseJson = { raw ?: failure?.toErrorJson() },
                )
            } finally {
                http.temporaryFile?.delete()
            }
        }
    }

    /** 分片只用于预览，整轮 Finished 在 EOF 且终态确认后唯一发送。 */
    fun stream(request: LLMToolGenerationRequest): Flow<LLMToolStreamEvent> = flow {
        val model = request.base.model ?: mProvider.model
        val http = prepare(request, true)
        // 请求开始时决定是否采集，普通模式不创建日志缓冲或补录中途开启前的内容。
        val log = if (AppModel.debugModeEnabled) LLMStreamLog() else null
        try {
            val decoder = SseEventDecoder()
            val accumulator = ToolWireRequest.codec(mProvider.protocol).stream()
            var bytes = 0L

            // 限制整条响应，独立于可关闭的请求日志缓冲。
            suspend fun frame(event: SseEvent) {
                if (event.data == "[DONE]" || event.data.isBlank()) return
                val data = parseObject(event.data)
                val type = data.cleanString("type")
                    .ifEmpty { data.cleanString("event_type").ifEmpty { event.event } }
                accumulator.accept(type, data)
                // 提取文本预览分片，仅用于实时展示，不影响最终工具参数解析。
                val text = when (mProvider.protocol) {
                    LLMProviderProtocol.OpenAICompatible -> data.arrayOrNull("choices")
                        ?.firstOrNull()?.asJsonObject?.objectOrNull("delta")?.cleanString("content")

                    LLMProviderProtocol.OpenAIResponses -> if (type == "response.output_text.delta") data.cleanString(
                        "delta"
                    ) else null

                    LLMProviderProtocol.AnthropicMessages -> data.objectOrNull("delta")
                        ?.takeIf { it.cleanString("type") == "text_delta" }?.cleanString("text")

                    LLMProviderProtocol.Gemini -> data.arrayOrNull("candidates")
                        ?.firstOrNull()?.asJsonObject?.objectOrNull("content")?.arrayOrNull("parts")
                        ?.filter { it.asJsonObject.get("thought")?.asBoolean != true }
                        ?.joinToString("") { it.asJsonObject.cleanString("text") }

                    LLMProviderProtocol.GeminiInteractions -> data.objectOrNull("delta")
                        ?.takeIf { it.cleanString("type") == "text" }?.cleanString("text")
                }
                if (!text.isNullOrEmpty()) emit(LLMToolStreamEvent.Preview(text))
                // 提取推理预览分片，与正文预览分离展示。
                val reasoning = when (mProvider.protocol) {
                    LLMProviderProtocol.OpenAICompatible -> data.arrayOrNull("choices")
                        ?.firstOrNull()?.asJsonObject?.objectOrNull("delta")
                        ?.cleanString("reasoning_content")

                    LLMProviderProtocol.OpenAIResponses -> if (type in setOf(
                            "response.reasoning_summary_text.delta",
                            "response.reasoning_text.delta"
                        )
                    ) data.cleanString("delta") else null

                    LLMProviderProtocol.AnthropicMessages -> data.objectOrNull("delta")
                        ?.takeIf { it.cleanString("type") == "thinking_delta" }
                        ?.cleanString("thinking")

                    LLMProviderProtocol.Gemini -> data.arrayOrNull("candidates")
                        ?.firstOrNull()?.asJsonObject?.objectOrNull("content")?.arrayOrNull("parts")
                        ?.filter { it.asJsonObject.get("thought")?.asBoolean == true }
                        ?.joinToString("") { it.asJsonObject.cleanString("text") }

                    LLMProviderProtocol.GeminiInteractions -> data.objectOrNull("delta")
                        ?.takeIf { it.cleanString("type") == "thought_summary" }
                        ?.objectOrNull("content")?.cleanString("text")
                }
                if (!reasoning.isNullOrEmpty()) emit(
                    LLMToolStreamEvent.Preview(
                        reasoning,
                        reasoning = true
                    )
                )
            }
            // 按行消费 SSE 流并同步写入有界调试日志，在 EOF 且终态合法后发射 Finished。
            mHttp.streamLines(http.request) { emit(LLMToolStreamEvent.Connected) }.collect { line ->
                log?.append(line)
                bytes += line.toByteArray().size
                if (bytes > MAX_RESPONSE_BYTES) throw LLMToolProtocolException("response_too_large")
                decoder.accept(line)?.let { frame(it) }
            }
            decoder.finish()?.let { frame(it) }
            val turn = accumulator.finish(model).requireValidToolTurn()
            emit(LLMToolStreamEvent.Finished(turn))
        } catch (error: Throwable) {
            log?.addError(error.message)
            throw error
        } finally {
            try {
                // 流完成、失败或取消时均尽力写入脱敏请求日志，再释放临时图片文件。
                mLogs.trySaveLog(
                    provider = mProvider,
                    model = model,
                    isStreaming = true,
                    requestJson = http.payloadJson,
                    responseJson = { log?.toJson() },
                )
            } finally {
                http.temporaryFile?.delete()
            }
        }
    }

    private suspend fun prepare(
        request: LLMToolGenerationRequest,
        stream: Boolean
    ): LLMHttpRequest {
        val provider = mProvider.copy(model = request.base.model ?: mProvider.model)
        if (request.base.messages.any { it.images.isNotEmpty() }) mCapabilities.requireImages(
            provider
        )
        val wire = MultimodalWireCodec(provider)
        val json = ToolWireRequest.encode(provider, request, stream, wire::registerImage)
        val prepared = wire.prepare(json, request.base, mImages)
        // 自定义请求头仍沿用模型配置，工具层不另持有 Provider 凭据。
        return prepared.toHttpRequest {
            val builder =
                Request.Builder().post(prepared.body).header("Content-Type", "application/json")
            val base = provider.normalizedBaseUrl()
            when (provider.protocol) {
                LLMProviderProtocol.OpenAICompatible -> builder.url("$base/chat/completions")
                    .header("Authorization", "Bearer ${provider.apiKey}")

                LLMProviderProtocol.OpenAIResponses -> builder.url("$base/responses")
                    .header("Authorization", "Bearer ${provider.apiKey}")

                LLMProviderProtocol.AnthropicMessages -> builder.url("$base/v1/messages")
                    .header("x-api-key", provider.apiKey).header("anthropic-version", "2023-06-01")

                LLMProviderProtocol.GeminiInteractions -> builder.url("$base/interactions")
                    .header("x-goog-api-key", provider.apiKey)

                LLMProviderProtocol.Gemini -> builder.url(
                    "$base/v1beta/models/${provider.model}:${if (stream) "streamGenerateContent" else "generateContent"}".toHttpUrl()
                        .newBuilder().apply {
                            if (stream) addQueryParameter("alt", "sse")
                            if (provider.apiKey.isNotBlank()) addQueryParameter(
                                "key",
                                provider.apiKey
                            )
                        }.build()
                )
            }
            builder.applyProviderHeaders(provider).build()
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
    }
}
