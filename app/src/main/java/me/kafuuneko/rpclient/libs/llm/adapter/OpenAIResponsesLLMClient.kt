package me.kafuuneko.rpclient.libs.llm.adapter

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.ImageInputCapabilityResolver
import me.kafuuneko.rpclient.libs.llm.LLMClient
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationResponse
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.media.MessageImageRuntime
import me.kafuuneko.rpclient.libs.room.repository.LLMRequestLogRepository
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * OpenAI Responses 原生协议客户端。
 * - 每次发送本地最终 Prompt，支持文本、图片、推理摘要与服务端工具输出。
 * - HTTP 与 SSE 共用独立编解码器，网络取消及图片资源释放沿用统一传输边界。
 * - 协议错误不会转为成功，原始响应仅写入用户启用的脱敏调试日志。
 */
class OpenAIResponsesLLMClient(
    private val mOkHttpClient: OkHttpClient,
    private val mLLMRequestLogRepository: LLMRequestLogRepository,
    private val mProvider: LLMProviderConfig,
    private val mImageRuntime: MessageImageRuntime? = null,
    private val mImageCapabilities: ImageInputCapabilityResolver = ImageInputCapabilityResolver()
) : LLMClient {
    /** 发送完整请求并解析所有原生输出 Item。 */
    override suspend fun generate(request: LLMGenerationRequest): LLMGenerationResponse {
        val model = request.model ?: mProvider.model
        val http = buildRequest(request = request, model = model, stream = false)
        var raw: String? = null
        var failure: Throwable? = null
        try {
            // 在解析前保留响应，解析失败仍可通过受控日志定位服务端返回的结构。
            raw = mOkHttpClient.await(http.request)
            return OpenAIResponsesResponseCodec.decode(
                raw = raw,
                fallbackModel = model,
                provider = mProvider.providerType,
                includeReasoningInContent = request.includeReasoningInContent,
            )
        } catch (error: Throwable) {
            failure = error
            throw recordImageFailure(request, model, error)
        } finally {
            try {
                mLLMRequestLogRepository.trySaveLog(
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

    /** 按 SSE 帧处理流，在终态立即关闭网络调用并保留停止生成语义。 */
    override fun streamGenerate(request: LLMGenerationRequest): Flow<LLMStreamEvent> = flow {
        val model = request.model ?: mProvider.model
        val http = buildRequest(request = request, model = model, stream = true)
        val frames = SseEventDecoder()
        val decoder = OpenAIResponsesStreamDecoder()
        val mapper =
            LLMStreamPartMapper(request.includeReasoningInContent, request.captureReasoning)
        // 请求开始时决定是否采集，普通模式不创建日志缓冲或补录中途开启前的内容。
        val log = if (AppModel.debugModeEnabled) LLMStreamLog() else null
        try {
            // takeWhile 在终态帧处理完后主动结束收集，不等待服务端再次发送或关闭连接。
            mOkHttpClient.streamLines(http.request) { emit(LLMStreamEvent.Connected) }
                .takeWhile { line ->
                    log?.append(line)
                    frames.accept(line)?.let { event ->
                        decoder.accept(event)
                            .forEach { part -> mapper.map(part).forEach { event -> emit(event) } }
                    }
                    !decoder.isFinished
                }.collect {}
            // 尾帧可能缺少空行，但缺少响应终态始终是断流。
            frames.finish()?.let { event ->
                decoder.accept(event)
                    .forEach { part -> mapper.map(part).forEach { event -> emit(event) } }
            }
            decoder.requireFinished()
        } catch (error: Throwable) {
            log?.addError(error.message)
            throw recordImageFailure(request, model, error)
        } finally {
            try {
                mLLMRequestLogRepository.trySaveLog(
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

    /** 按最终配置生成原生请求，图像数据在 HTTP 生命周期内持有临时文件。 */
    private suspend fun buildRequest(
        request: LLMGenerationRequest,
        model: String,
        stream: Boolean
    ): LLMHttpRequest {
        val provider = mProvider.copy(model = model)
        if (request.messages.any { it.images.isNotEmpty() }) mImageCapabilities.requireImages(
            provider
        )
        val images = MultimodalWireCodec(provider)
        val payload = OpenAIResponsesRequestCodec.encode(
            provider,
            request,
            model,
            stream,
            images::registerImage
        )
        val prepared = images.prepare(payload, request, mImageRuntime)
        // 请求头或地址不合法时，toHttpRequest 同样负责释放已准备的图片文件。
        return prepared.toHttpRequest {
            Request.Builder()
                .url("${provider.normalizedBaseUrl()}/responses")
                .post(prepared.body)
                .header("Authorization", "Bearer ${provider.apiKey}")
                .header("Content-Type", "application/json")
                .header("Accept", if (stream) "text/event-stream" else "application/json")
                .applyProviderHeaders(provider)
                .build()
        }
    }

    private fun recordImageFailure(
        request: LLMGenerationRequest,
        model: String,
        error: Throwable
    ): Throwable {
        return if (request.messages.any { it.images.isNotEmpty() }) {
            mImageCapabilities.recordFailure(mProvider.copy(model = model), error)
        } else error
    }

}
