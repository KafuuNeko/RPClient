package me.kafuuneko.rpclient.libs.llm.adapter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
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
                            IOException("HTTP ${it.code}: ${body.ifBlank { it.message }}")
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
 * 按行读取 SSE/行流响应。调用方负责解析各供应商的 data 内容。
 */
internal fun OkHttpClient.streamLines(request: Request): Flow<String> = flow {
    val response = withContext(Dispatchers.IO) { newCall(request).execute() }
    response.use {
        val body = it.body ?: throw IOException("HTTP ${it.code}: empty body")
        if (!it.isSuccessful) {
            val errorBody = withContext(Dispatchers.IO) { body.string() }
            throw IOException("HTTP ${it.code}: ${errorBody.ifBlank { it.message }}")
        }
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
    return buildString {
        for (index in 0 until length()) {
            val block = optJSONObject(index) ?: continue
            if (type == null || block.optString("type") == type) {
                append(block.optString("text"))
            }
        }
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
 * 不能收集中途 system 消息，否则会改变原始对话顺序；中途消息由适配器加标签降级。
 */
internal fun List<LLMMessage>.leadingSystemPrompt(): String {
    return takeWhile { it.role == LLMMessageRole.System }
        .joinToString("\n\n") { it.content }
}

/** 为无法原生表达中途 system 角色的协议保留角色边界。 */
internal fun LLMMessage.contentWithSystemPrefix(): String {
    return if (role == LLMMessageRole.System) "[System]\n$content" else content
}

/**
 * 标准化 Provider baseUrl，避免拼接路径时出现重复斜杠。
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
 * 将 Provider 自定义请求头应用到当前请求。
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
