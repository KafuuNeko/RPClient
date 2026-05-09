package me.kafuuneko.rpclient.libs.llm.adapter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
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
 * 标准化 Provider baseUrl，避免拼接路径时出现重复斜杠。
 */
internal fun LLMProvider.normalizedBaseUrl(): String {
    return baseUrl.trim().trimEnd('/')
}

/**
 * 解析用户自定义请求头 JSON。格式错误时返回空 Map，避免阻断主流程。
 */
internal fun LLMProvider.customHeaders(): Map<String, String> {
    if (customHeadersJson.isBlank()) return emptyMap()
    return runCatching {
        val json = JSONObject(customHeadersJson)
        json.keys().asSequence().associateWith { key -> json.optString(key) }
    }.getOrDefault(emptyMap())
}

/**
 * 将 Provider 自定义请求头应用到当前请求。
 */
internal fun Request.Builder.applyProviderHeaders(provider: LLMProvider): Request.Builder {
    provider.customHeaders().forEach { (key, value) -> header(key, value) }
    return this
}

/**
 * 将 JSONObject 转为 JSON 请求体。
 */
internal fun JSONObject.toRequestBody() = toString().toRequestBody(JsonMediaType)
