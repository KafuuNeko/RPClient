package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.llm.LLMEmptyResponseException
import me.kafuuneko.rpclient.libs.llm.LLMHttpStatusException
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMReasoningKind
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.llm.model.LLMUsage
import me.kafuuneko.rpclient.libs.room.repository.LLMRequestLogRepository
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal val JsonMediaType = "application/json; charset=utf-8".toMediaType()

/** 已序列化的协议请求，用于同时发起网络调用和记录原始请求日志。 */
internal data class LLMHttpRequest(
    val request: Request,
    val payloadJson: String
)

/**
 * 执行普通 HTTP 请求并读取完整响应体。
 */
internal suspend fun OkHttpClient.await(request: Request): String {
    val call = newCall(request)
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        continuation.resumeWithException(
                            LLMHttpStatusException(it.code, body.ifBlank { it.message })
                        )
                        return
                    }
                    continuation.resume(body)
                }
            }
        })
    }
}

/**
 * 按行读取 SSE/行流响应。调用方负责解析各模型服务的 data 内容。
 */
internal fun OkHttpClient.streamLines(
    request: Request,
    onConnected: suspend () -> Unit = {}
): Flow<String> = flow {
    val response = withContext(Dispatchers.IO) { newCall(request).execute() }
    response.use {
        val body = it.body ?: throw LLMEmptyResponseException()
        if (!it.isSuccessful) {
            val errorBody = withContext(Dispatchers.IO) { body.string() }
            throw LLMHttpStatusException(it.code, errorBody.ifBlank { it.message })
        }
        onConnected()
        while (true) {
            val line = withContext(Dispatchers.IO) { body.source().readUtf8Line() } ?: break
            emit(line)
        }
    }
}

/**
 * 只在值不为空时写入 JSON 字段。
 */
internal fun JSONObject.putIfNotNull(name: String, value: Any?) {
    if (value != null) put(name, value)
}

/**
 * 将字符串列表转换为 JSONArray。
 */
internal fun List<String>.toJsonArray(): JSONArray {
    return JSONArray().also { array -> forEach { array.put(it) } }
}

/**
 * 拼接响应块中的 text 字段。
 *
 * Gemini 的 parts 与 Anthropic 的 content 都使用对象数组承载文本，因此在此统一读取。
 */
internal fun JSONArray.joinTextFields(type: String? = null): String {
    return joinStringFields(field = "text", type = type)
}

/** 按可选类型拼接响应对象数组中的指定字符串字段。 */
internal fun JSONArray.joinStringFields(field: String, type: String? = null): String {
    return buildString {
        for (index in 0 until length()) {
            val block = optJSONObject(index) ?: continue
            if (type == null || block.optString("type") == type) {
                append(block.optString(field))
            }
        }
    }
}

/** 按聊天兼容格式合并推理和正文，推理为空时保持原正文不变。 */
internal fun mergeReasoningContent(reasoningContent: String, content: String): String {
    if (reasoningContent.isBlank()) return content
    return "<think>\n$reasoningContent\n</think>\n\n$content".trim()
}

/** 使用纯 JVM 可测试的 Gson 解析模型服务流式 JSON 对象。 */
internal fun parseStreamJsonObject(value: String): JsonObject? {
    return runCatching { JsonParser.parseString(value).asJsonObject }.getOrNull()
}

/** 安全读取 Gson 对象中的子对象。 */
internal fun JsonObject.objectOrNull(name: String): JsonObject? {
    val element = get(name) ?: return null
    return element.takeIf { it.isJsonObject }?.asJsonObject
}

/** 安全读取 Gson 对象中的数组。 */
internal fun JsonObject.arrayOrNull(name: String): JsonArray? {
    val element = get(name) ?: return null
    return element.takeIf { it.isJsonArray }?.asJsonArray
}

/** 将缺失、JSON null 或伪 null 字符串统一读取为空文本。 */
internal fun JsonObject.cleanString(name: String): String {
    val element = get(name) ?: return ""
    if (element.isJsonNull || !element.isJsonPrimitive) return ""
    return cleanContentString(runCatching { element.asString }.getOrDefault(""))
}

/** 清理兼容网关返回的伪 null 文本，供流式与非流式解析共享。 */
internal fun cleanContentString(value: String): String {
    return if (value.equals("null", ignoreCase = true)) "" else value
}

/** 安全读取 Gson 对象中的布尔值。 */
internal fun JsonObject.booleanOrFalse(name: String): Boolean {
    val element = get(name) ?: return false
    if (!element.isJsonPrimitive) return false
    return runCatching { element.asBoolean }.getOrDefault(false)
}

/** 安全读取可能缺失或类型异常的整数字段。 */
internal fun JsonObject.intOrNull(name: String): Int? {
    val element = get(name) ?: return null
    if (!element.isJsonPrimitive) return null
    return runCatching { element.asInt }.getOrNull()
}

/** 合并同一流不同响应块上报的用量，较新的非空字段优先。 */
internal fun LLMUsage?.mergeWith(newer: LLMUsage?): LLMUsage? {
    if (this == null) return newer
    if (newer == null) return this
    return LLMUsage(
        promptTokens = newer.promptTokens ?: promptTokens,
        completionTokens = newer.completionTokens ?: completionTokens,
        totalTokens = newer.totalTokens ?: totalTokens,
        cachedPromptTokens = newer.cachedPromptTokens ?: cachedPromptTokens,
        reasoningTokens = newer.reasoningTokens ?: reasoningTokens
    )
}

/** 协议解析器输出的正文、推理或完成片段。 */
internal sealed class LLMProviderStreamPart {
    data class Text(
        val content: String,
        val rawChunk: String
    ) : LLMProviderStreamPart()

    data class Reasoning(
        val content: String,
        val rawChunk: String,
        val kind: LLMReasoningKind = LLMReasoningKind.Detailed
    ) : LLMProviderStreamPart()

    data class Finished(
        val rawChunk: String? = null,
        val finishReason: String? = null,
        val model: String? = null,
        val usage: LLMUsage? = null,
        /** true 表示协议已到达不可再追加用量的最终结束标记。 */
        val terminal: Boolean = true
    ) : LLMProviderStreamPart()

    data class Usage(
        val usage: LLMUsage,
        val model: String? = null
    ) : LLMProviderStreamPart()
}

/**
 * 将协议推理片段映射为结构化事件或兼容的 `<think>` 正文块。
 *
 * - 故事写作只捕获独立推理事件，避免内部构思污染稿件；
 * - 聊天继续按原有标签格式保存，保持展示、Regex 与历史上下文兼容；
 * - 流在推理阶段结束时统一补齐闭合标签。
 */
internal class LLMStreamPartMapper(
    private val includeReasoningInContent: Boolean,
    private val captureReasoning: Boolean
) {
    private var mIsThinking = false
    private var mUsage: LLMUsage? = null
    private var mFinishReason: String? = null
    private var mModel: String? = null
    private var mRawChunk: String? = null
    private var mHasFinished = false

    /** 将单个协议片段转换为一个或多个通用流事件。 */
    fun map(part: LLMProviderStreamPart): List<LLMStreamEvent> {
        return when (part) {
            is LLMProviderStreamPart.Text -> mapText(part)
            is LLMProviderStreamPart.Reasoning -> mapReasoning(part)
            is LLMProviderStreamPart.Finished -> mapFinished(part)
            is LLMProviderStreamPart.Usage -> {
                mUsage = mUsage.mergeWith(part.usage)
                mModel = part.model ?: mModel
                emptyList()
            }
        }
    }

    /** 在响应流正常关闭时补齐思考标签和统一完成事件。 */
    fun finish(): List<LLMStreamEvent> {
        if (mHasFinished) return listOfNotNull(closeReasoning())
        return finishEvents()
    }

    private fun mapText(
        part: LLMProviderStreamPart.Text
    ): List<LLMStreamEvent> {
        if (part.content.isBlank()) return emptyList()
        val prefix = closeReasoning()?.content.orEmpty()
        return listOf(
            LLMStreamEvent.Delta(
                content = prefix + part.content,
                rawChunk = part.rawChunk
            )
        )
    }

    private fun mapReasoning(
        part: LLMProviderStreamPart.Reasoning
    ): List<LLMStreamEvent> {
        if (part.content.isBlank()) return emptyList()
        // 聊天兼容模式将推理并入正文，结构化捕获模式则保持独立事件
        if (includeReasoningInContent) {
            val content = if (mIsThinking) part.content else "<think>\n${part.content}"
            mIsThinking = true
            return listOf(
                LLMStreamEvent.Delta(
                    content = content,
                    rawChunk = part.rawChunk
                )
            )
        }
        if (!captureReasoning) return emptyList()
        return listOf(
            LLMStreamEvent.ReasoningDelta(
                content = part.content,
                rawChunk = part.rawChunk,
                kind = part.kind
            )
        )
    }

    private fun mapFinished(part: LLMProviderStreamPart.Finished): List<LLMStreamEvent> {
        mUsage = mUsage.mergeWith(part.usage)
        mFinishReason = part.finishReason ?: mFinishReason
        mModel = part.model ?: mModel
        mRawChunk = part.rawChunk ?: mRawChunk
        if (!part.terminal || mHasFinished) return emptyList()
        return finishEvents()
    }

    private fun finishEvents(): List<LLMStreamEvent> = buildList {
        closeReasoning()?.let(::add)
        add(
            LLMStreamEvent.Finished(
                rawChunk = mRawChunk,
                finishReason = mFinishReason,
                model = mModel,
                usage = mUsage
            )
        )
        mHasFinished = true
    }

    private fun closeReasoning(): LLMStreamEvent.Delta? {
        if (!mIsThinking) return null
        mIsThinking = false
        return LLMStreamEvent.Delta(
            content = "\n</think>\n\n",
            rawChunk = "reasoning_close"
        )
    }
}

/**
 * 转换为 OpenAI-compatible 协议角色。
 */
internal fun LLMMessage.toOpenAIRole(): String {
    return when (role) {
        LLMMessageRole.System -> "system"
        LLMMessageRole.User -> "user"
        LLMMessageRole.Assistant -> "assistant"
    }
}

/**
 * 转换为 Anthropic Messages 协议角色。
 */
internal fun LLMMessage.toAnthropicRole(): String {
    return when (role) {
        LLMMessageRole.Assistant -> "assistant"
        LLMMessageRole.System,
        LLMMessageRole.User -> "user"
    }
}

/**
 * 转换为 Gemini 协议角色。
 */
internal fun LLMMessage.toGeminiRole(): String {
    return when (role) {
        LLMMessageRole.Assistant -> "model"
        LLMMessageRole.System,
        LLMMessageRole.User -> "user"
    }
}

/**
 * 提取开头连续的 system 消息。
 *
 * 中途 system 消息仍保留在原始位置，由消息正文转换阶段降级为 user。
 */
internal fun List<LLMMessage>.leadingSystemPrompt(): String {
    return takeWhile { it.role == LLMMessageRole.System }
        .joinToString("\n\n") { it.content }
}

/**
 * 为仅支持 user/assistant 轮次的协议构建消息正文。
 *
 * 开头连续的 system 已由协议专用字段承载；其余 system 在原位置降级为 user，
 * 再合并连续同角色消息，确保 Anthropic 和 Gemini 收到合法的交替轮次。
 */
internal fun List<LLMMessage>.toAlternatingConversationMessages(
    emptyPlaceholder: String = "Let's get started."
): List<LLMMessage> {
    val converted = dropWhile { it.role == LLMMessageRole.System }
        .map { message ->
            if (message.role == LLMMessageRole.System) {
                message.copy(role = LLMMessageRole.User)
            } else {
                message
            }
        }
    if (converted.isEmpty()) {
        return listOf(LLMMessage(LLMMessageRole.User, emptyPlaceholder))
    }
    return converted.fold(mutableListOf()) { merged, message ->
        val previous = merged.lastOrNull()
        if (previous?.role == message.role) {
            merged[merged.lastIndex] = previous.copy(
                content = listOf(previous.content, message.content)
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n")
            )
        } else {
            merged += message
        }
        merged
    }
}

/**
 * 标准化模型配置的 baseUrl，避免拼接路径时出现重复斜杠。
 */
internal fun LLMProviderConfig.normalizedBaseUrl(): String {
    return baseUrl.trim().trimEnd('/')
}

/**
 * 解析用户自定义请求头 JSON。格式错误时返回空 Map，避免阻断主流程。
 */
internal fun LLMProviderConfig.customHeaders(): Map<String, String> {
    if (customHeadersJson.isBlank()) return emptyMap()
    return runCatching {
        val json = JSONObject(customHeadersJson)
        json.keys().asSequence().associateWith { key -> json.optString(key) }
    }.getOrDefault(emptyMap())
}

/**
 * 将模型配置的自定义请求头应用到当前请求。
 */
internal fun Request.Builder.applyProviderHeaders(provider: LLMProviderConfig): Request.Builder {
    provider.customHeaders().forEach { (key, value) -> header(key, value) }
    return this
}

/**
 * 尽力写入请求日志。
 *
 * 日志失败不得影响实际生成请求，因此此处有意吞掉持久化异常。
 */
internal suspend fun LLMRequestLogRepository.trySaveLog(
    provider: LLMProviderConfig,
    model: String,
    isStreaming: Boolean,
    requestJson: String,
    responseJson: String
) {
    runCatching {
        saveLog(
            provider = provider,
            model = model,
            isStreaming = isStreaming,
            requestJson = requestJson,
            responseJson = responseJson
        )
    }
}

/**
 * 将 JSONObject 转为 JSON 请求体。
 */
internal fun JSONObject.toRequestBody() = toString().toRequestBody(JsonMediaType)

/** 将网络或解析异常转换为可持久化的最小 JSON 结构。 */
internal fun Throwable.toErrorJson(): String {
    return JSONObject()
        .put("error", message.orEmpty())
        .toString()
}
