package me.kafuuneko.rpclient.libs.agent.connection

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Proxy
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import javax.net.SocketFactory
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import me.kafuuneko.rpclient.libs.agent.tool.ToolErrorCode
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * 一次有界响应；不会把认证头、Cookie 或底层 Response 暴露给调用者。
 */
data class ToolHttpPayload(val text: String, val contentType: String)

/**
 * 工具专用 HTTP 传输，独立于允许内网模型的 Provider 客户端。
 * - 每次执行使用独立连接池、无代理、无 Cookie、无自动重试与重定向。
 * - 每跳经过安全 DNS；跨源读取丢弃全部自定义头，写入不跨源重定向。
 * - 回调内关闭响应并限制解压字节数，取消会主动断开 socket。
 */
class ToolHttpEngine(
    private val mDns: Dns = Dns.SYSTEM,
    private val mSocketFactory: SocketFactory = SocketFactory.getDefault(),
) {
    /**
     * 派发已冻结的请求；secret 仅在构造认证头和响应脱敏时使用。
     *
     * @param config 冻结的连接配置。
     * @param query 已完成绑定并等待 URL 编码的查询参数。
     * @param body 已完成结构化绑定的请求体。
     * @param secret 仅用于本机加密或请求认证的凭据正文。
     * @param readUrl 是否启用无认证的公网文本读取限制。
     * @return 已限流、解压和脱敏的响应文本及内容类型。
     */
    suspend fun execute(
        config: HttpToolConnection,
        query: Map<String, String>,
        body: String?,
        secret: String? = null,
        readUrl: Boolean = false,
    ): ToolHttpPayload {
        // 网页读取模式禁止携带任何凭据或自定义请求头。
        if (readUrl && (config.authMode != ToolAuthMode.None || config.credentialRef != null || config.headers.isNotEmpty() || secret != null)) {
            throw ToolHttpException(ToolErrorCode.HTTP_INVALID_CONFIGURATION)
        }
        if (config.authMode != ToolAuthMode.None && secret == null) {
            throw ToolHttpException(ToolErrorCode.HTTP_NEEDS_CONFIGURATION)
        }
        // 组装目标 URL 与查询参数；Query 认证放在最后注入，防止被模型参数覆盖。
        val url = ToolNetworkPolicy.url(config.url).newBuilder().apply {
            (config.query + query).forEach { (name, value) -> setQueryParameter(name, value) }
            if (config.authMode == ToolAuthMode.Query) {
                setQueryParameter(config.authHeader, requireNotNull(secret))
            }
        }.build()
        // 构造请求头与请求体；Header 类认证统一经此注入。
        val builder = Request.Builder().url(url).header("Accept-Encoding", "gzip, deflate")
        config.headers.forEach { (name, value) -> builder.header(name, value) }
        if (config.authMode in setOf(
                ToolAuthMode.Bearer,
                ToolAuthMode.ApiKey,
                ToolAuthMode.Header
            )
        ) {
            builder.header(
                config.authHeader,
                if (config.authMode == ToolAuthMode.Bearer) "Bearer $secret" else requireNotNull(
                    secret
                ),
            )
        }
        val requestBody = body?.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = builder.method(
            config.method,
            if (config.method in ToolHttpMethod.requiresBody) {
                requestBody ?: "{}".toRequestBody()
            } else {
                requestBody
            },
        ).build()
        // 不复用模型网络栈或系统代理，DNS 校验结果直接用于本次真实连接。
        val client = OkHttpClient.Builder()
            .dns(ToolSafeDns(config.allowPrivateAddresses && !readUrl, mDns))
            .socketFactory(mSocketFactory)
            .proxy(Proxy.NO_PROXY)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .callTimeout(config.timeoutMillis, TimeUnit.MILLISECONDS)
            .connectTimeout(config.timeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(config.timeoutMillis, TimeUnit.MILLISECONDS)
            .build()
        try {
            return redirects(client, request, config, secret, readUrl)
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    private suspend fun redirects(
        client: OkHttpClient,
        initial: Request,
        config: HttpToolConnection,
        secret: String?,
        readUrl: Boolean,
    ): ToolHttpPayload {
        var request = initial
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(config.timeoutMillis)
        // 总时限包含重定向链，不能用每跳重置计时绕开单工具限额。
        for (hop in 0..5) {
            ToolNetworkPolicy.checkLiteralHost(
                request.url.host,
                config.allowPrivateAddresses && !readUrl
            )
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) throw ToolHttpException(ToolErrorCode.HTTP_TIMEOUT)
            val result = await(client, request, config.maxResponseBytes, remaining, readUrl)
            if (result is HopResult.Payload) {
                val payload = result.value
                return payload.copy(
                    text = if (secret.isNullOrEmpty()) payload.text else payload.text.replace(
                        secret,
                        "[redacted]"
                    )
                )
            }
            result as HopResult.Redirect
            if (hop == 5) throw ToolHttpException(ToolErrorCode.HTTP_REDIRECT_LIMIT)
            val next =
                request.url.resolve(result.location)
                    ?: throw ToolHttpException(ToolErrorCode.HTTP_INVALID_URL)
            ToolNetworkPolicy.url(next.toString())
            val sameOrigin =
                request.url.scheme == next.scheme && request.url.host == next.host && request.url.port == next.port
            val writes = request.method !in ToolHttpMethod.reads
            if (writes && (!sameOrigin || result.status !in setOf(
                    307,
                    308
                ))
            ) throw ToolHttpException(ToolErrorCode.HTTP_UNSAFE_REDIRECT)
            if (request.url.isHttps && !next.isHttps) throw ToolHttpException(ToolErrorCode.HTTP_UNSAFE_REDIRECT)
            request = if (sameOrigin) request.newBuilder().url(next).build() else {
                Request.Builder().url(next).method(request.method, null)
                    .header("Accept-Encoding", "gzip, deflate").build()
            }
        }
        throw ToolHttpException(ToolErrorCode.HTTP_REDIRECT_LIMIT)
    }

    private suspend fun await(
        client: OkHttpClient,
        request: Request,
        maxBytes: Int,
        remainingNanos: Long,
        readUrl: Boolean,
    ): HopResult = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        call.timeout().timeout(remainingNanos, TimeUnit.NANOSECONDS)
        continuation.invokeOnCancellation { call.cancel() }
        // 回调线程负责完整读取与关闭；异常不携带服务端文本或 URL。
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                val error = when (e) {
                    is ToolHttpException -> e
                    is SocketTimeoutException -> ToolHttpException(ToolErrorCode.HTTP_TIMEOUT)
                    else -> ToolHttpException(ToolErrorCode.HTTP_NETWORK_ERROR)
                }
                continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use { consume(it, maxBytes, readUrl) } }
                if (!continuation.isCancelled) result.fold(
                    onSuccess = { continuation.resume(it) },
                    onFailure = {
                        continuation.resumeWithException(
                            if (it is ToolHttpException) it else ToolHttpException(
                                ToolErrorCode.HTTP_INVALID_RESPONSE
                            )
                        )
                    },
                )
            }
        })
    }

    private fun consume(response: Response, maxBytes: Int, readUrl: Boolean): HopResult {
        if (response.code in setOf(301, 302, 303, 307, 308)) {
            return HopResult.Redirect(
                response.header("Location")
                    ?: throw ToolHttpException(ToolErrorCode.HTTP_INVALID_REDIRECT),
                response.code
            )
        }
        if (!response.isSuccessful) throw ToolHttpException(
            when (response.code) {
                401, 403 -> ToolErrorCode.HTTP_AUTH_REQUIRED
                429 -> ToolErrorCode.HTTP_RATE_LIMITED
                else -> ToolErrorCode.HTTP_STATUS_ERROR
            }
        )
        // HEAD 和无正文成功状态仍可提供明确结果，避免把已成功的写入误报为未知。
        if (!readUrl && (response.request.method == ToolHttpMethod.Head || response.code in setOf(
                204,
                205
            ))
        ) {
            return HopResult.Payload(
                ToolHttpPayload(
                    "{\"status\":${response.code}}",
                    "application/json"
                )
            )
        }
        val contentType =
            response.body?.contentType()?.let { "${it.type}/${it.subtype}" }.orEmpty().lowercase()
        if (readUrl && contentType !in setOf(
                "text/plain",
                "text/markdown",
                "text/html",
                "application/xhtml+xml"
            )
        ) throw ToolHttpException(ToolErrorCode.HTTP_UNSUPPORTED_CONTENT)
        val input = response.body?.byteStream()
            ?: throw ToolHttpException(ToolErrorCode.HTTP_INVALID_RESPONSE)
        // 显式处理 gzip/deflate，并在解压流上限流，不能信任 Content-Length。
        val decoded = when (response.header("Content-Encoding")?.lowercase()?.trim()) {
            null, "", "identity" -> input
            "gzip" -> GZIPInputStream(input)
            "deflate" -> InflaterInputStream(input)
            else -> throw ToolHttpException(ToolErrorCode.HTTP_UNSUPPORTED_ENCODING)
        }
        val bytes = decoded.use { readBounded(it, maxBytes) }
        val charset = response.body?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        return HopResult.Payload(ToolHttpPayload(String(bytes, charset), contentType))
    }

    private fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, maxBytes + 1 - output.size()))
            if (count < 0) return output.toByteArray()
            if (output.size() + count > maxBytes) throw ToolHttpException(ToolErrorCode.HTTP_RESPONSE_TOO_LARGE)
            output.write(buffer, 0, count)
        }
    }

    private sealed interface HopResult {
        data class Redirect(val location: String, val status: Int) : HopResult
        data class Payload(val value: ToolHttpPayload) : HopResult
    }
}
