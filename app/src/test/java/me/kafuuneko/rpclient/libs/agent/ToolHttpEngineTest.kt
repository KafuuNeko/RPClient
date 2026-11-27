package me.kafuuneko.rpclient.libs.agent

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.GZIPOutputStream
import java.util.zip.DeflaterOutputStream
import javax.net.SocketFactory
import kotlin.concurrent.thread
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.kafuuneko.rpclient.libs.agent.connection.HttpToolConnection
import me.kafuuneko.rpclient.libs.agent.connection.ToolHttpEngine
import me.kafuuneko.rpclient.libs.agent.connection.ToolHttpException
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 真实 socket 验证每跳安全边界、认证隔离、解压上限和取消，不依赖外网。 */
class ToolHttpEngineTest {
    @Test
    fun privateAddressRequiresExplicitConnectionPermissionAndReaderNeverInheritsIt(): Unit =
        runBlocking {
            // 数字 IP 会绕过 OkHttp Dns，实际请求必须仍被字面地址检查拦截。
            Fixture { Reply("ok") }.use { server ->
                expect("http_address_blocked") {
                    ToolHttpEngine().execute(
                        HttpToolConnection(server.localUrl),
                        emptyMap(),
                        null
                    )
                }
                assertEquals(
                    "ok",
                    ToolHttpEngine().execute(
                        HttpToolConnection(
                            server.localUrl,
                            allowPrivateAddresses = true
                        ), emptyMap(), null
                    ).text
                )
                expect("http_address_blocked") {
                    ToolHttpEngine().execute(
                        HttpToolConnection(
                            server.localUrl,
                            allowPrivateAddresses = true
                        ), emptyMap(), null, readUrl = true
                    )
                }
                assertEquals(1, server.requests.size)
            }
        }

    @Test
    fun queryEncodingAndCredentialRedactionUseIsolatedHttpClient(): Unit = runBlocking {
        // 认证仅存在于线上请求头，服务端回显也要在返回上下文前脱敏。
        Fixture { Reply("secret-fixture", contentType = "application/json") }.use { server ->
            val result = ToolHttpEngine().execute(
                HttpToolConnection(
                    server.localUrl,
                    authMode = "Bearer",
                    allowPrivateAddresses = true
                ), mapOf("q" to "a&b +中文"), null, "secret-fixture"
            )
            assertEquals("[redacted]", result.text)
            val request = server.requests.single()
            assertTrue(request.first.contains("a%26b%20%2B%E4%B8%AD%E6%96%87"))
            assertEquals("Bearer secret-fixture", request.second["authorization"])
            assertFalse(request.second.containsKey("cookie"))
        }
    }

    @Test
    fun redirectsRecheckDnsStripCrossOriginHeadersAndStopAfterFiveHops(): Unit = runBlocking {
        // 每跳重新授权地址，不允许通过已放行的公共主机跳入内网。
        Fixture { index ->
            if (index == 1) Reply(
                "",
                status = 302,
                location = "/next"
            ) else Reply("ok")
        }.use { server ->
            assertEquals(
                "ok",
                engine().execute(HttpToolConnection(server.publicUrl), emptyMap(), null).text
            )
            assertEquals(2, server.requests.size)
        }
        Fixture { Reply("", status = 302, location = "http://private.fixture/") }.use { server ->
            expect("http_address_blocked") {
                engine().execute(
                    HttpToolConnection(server.publicUrl),
                    emptyMap(),
                    null
                )
            }
            assertEquals(1, server.requests.size)
        }
        Fixture { Reply("", status = 302, location = "/again") }.use { server ->
            expect("http_redirect_limit") {
                engine().execute(
                    HttpToolConnection(server.publicUrl),
                    emptyMap(),
                    null
                )
            }
            assertEquals(6, server.requests.size)
        }
        Fixture { Reply("ok") }.use { destination ->
            Fixture {
                Reply(
                    "",
                    status = 302,
                    location = destination.publicUrl.replace("public.fixture", "other.fixture")
                )
            }.use { origin ->
                engine().execute(
                    HttpToolConnection(
                        origin.publicUrl,
                        headers = mapOf("X-Fixture" to "private"),
                        authMode = "Bearer"
                    ), emptyMap(), null, "credential"
                )
                assertFalse(destination.requests.single().second.containsKey("authorization"))
                assertFalse(destination.requests.single().second.containsKey("x-fixture"))
            }
        }
    }

    @Test
    fun unsafeSchemesAndCrossOriginWriteRedirectsNeverDispatch(): Unit = runBlocking {
        // 写入确认绑定固定目标，跨源跳转不能继承原确认。
        Fixture { Reply("", status = 302, location = "file:///etc/hosts") }.use { server ->
            expect("http_invalid_url") {
                engine().execute(
                    HttpToolConnection(server.publicUrl),
                    emptyMap(),
                    null
                )
            }
        }
        Fixture { Reply("", status = 307, location = "http://other.fixture/") }.use { server ->
            expect("http_unsafe_redirect") {
                engine().execute(
                    HttpToolConnection(
                        server.publicUrl,
                        method = "POST"
                    ), emptyMap(), "{}"
                )
            }
            assertEquals(1, server.requests.size)
        }
    }

    @Test
    fun limitsApplyAfterBothGzipAndDeflateDecompression(): Unit = runBlocking {
        // 压缩体积很小但展开超限时，仍须在有界读取中明确失败。
        for (encoding in listOf("gzip", "deflate")) {
            val output = ByteArrayOutputStream()
            val compressor =
                if (encoding == "gzip") GZIPOutputStream(output) else DeflaterOutputStream(output)
            compressor.use { it.write(ByteArray(2 * 1024 * 1024 + 1) { 65 }) }
            Fixture {
                Reply(
                    "",
                    compressed = output.toByteArray(),
                    encoding = encoding
                )
            }.use { server ->
                expect("http_response_too_large") {
                    engine().execute(
                        HttpToolConnection(server.publicUrl),
                        emptyMap(),
                        null,
                        readUrl = true
                    )
                }
                assertTrue(output.size() < 10_000)
            }
        }
    }

    @Test
    fun cancellationClosesSocketWhileWaitingForResponseBody(): Unit = runBlocking {
        // 服务端保持响应体打开，验证取消不依赖 read 自行返回。
        Fixture { Reply("", hold = true) }.use { server ->
            val job =
                async { engine().execute(HttpToolConnection(server.publicUrl), emptyMap(), null) }
            withTimeout(3000) { while (server.requests.isEmpty()) delay(10) }
            withTimeout(3000) { job.cancelAndJoin() }
            withTimeout(3000) { while (!server.disconnected) delay(10) }
        }
    }

    @Test
    fun noContentWritesReturnExplicitSuccessMetadata(): Unit = runBlocking {
        Fixture { Reply("", status = 204) }.use { server ->
            val result = engine().execute(
                HttpToolConnection(server.publicUrl, method = "DELETE"),
                emptyMap(),
                "{}"
            )
            assertEquals("{\"status\":204}", result.text)
            assertEquals("application/json", result.contentType)
            assertEquals(1, server.requests.size)
        }
    }

    @Test
    fun queryAuthInjectsCredentialPreventsModelOverrideAndRedactsEcho(): Unit = runBlocking {
        // Query 鉴权在模型参数之后写入，避免被模型覆盖且在响应中脱敏。
        Fixture { Reply("echo query-secret-123", contentType = "application/json") }.use { server ->
            val result = ToolHttpEngine().execute(
                HttpToolConnection(
                    server.localUrl,
                    authMode = "Query",
                    authHeader = "key",
                    allowPrivateAddresses = true,
                ),
                mapOf("q" to "hello", "key" to "spoofed"),
                null,
                "query-secret-123",
            )
            assertEquals("echo [redacted]", result.text)
            val firstLine = server.requests.single().first
            assertTrue(firstLine.contains("key=query-secret-123"))
            assertFalse(firstLine.contains("spoofed"))
        }
    }

    @Test
    fun readUrlAcceptsTextMarkdownContentType(): Unit = runBlocking {
        // 文档站常见的 text/markdown 响应在网页读取模式中也应放行。
        Fixture { Reply("# Heading\nBody", contentType = "text/markdown; charset=utf-8") }.use { server ->
            val result = engine().execute(
                HttpToolConnection(server.publicUrl),
                emptyMap(),
                null,
                readUrl = true,
            )
            assertEquals("# Heading\nBody", result.text)
        }
    }

    private suspend fun expect(code: String, block: suspend () -> Unit) {
        try {
            block()
            throw AssertionError("Expected $code")
        } catch (error: ToolHttpException) {
            assertEquals(code, error.code)
        }
    }

    private fun engine(): ToolHttpEngine = ToolHttpEngine(
        object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                listOf(InetAddress.getByName(if (hostname == "private.fixture") "127.0.0.1" else "8.8.8.8"))
        },
        FixtureSocketFactory(),
    )

    private data class Reply(
        val body: String,
        val contentType: String = "text/plain",
        val status: Int = 200,
        val location: String? = null,
        val compressed: ByteArray? = null,
        val encoding: String? = null,
        val hold: Boolean = false,
    )

    private class Fixture(private val respond: (Int) -> Reply) : AutoCloseable {
        private val mListener = ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"))
        private val mClients = CopyOnWriteArrayList<Socket>()
        val requests = CopyOnWriteArrayList<Pair<String, Map<String, String>>>()

        @Volatile
        var disconnected = false
        val localUrl get() = "http://127.0.0.1:${mListener.localPort}/"
        val publicUrl get() = "http://public.fixture:${mListener.localPort}/"
        private val mWorker = thread(isDaemon = true) {
            while (!mListener.isClosed) runCatching {
                mListener.accept().use { socket ->
                    mClients.add(socket)
                    socket.soTimeout = 5000
                    val input = socket.getInputStream().bufferedReader()
                    val first = input.readLine()
                    val headers = mutableMapOf<String, String>()
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                        headers[line.substringBefore(':').lowercase()] =
                            line.substringAfter(':').trim()
                    }
                    repeat(headers["content-length"]?.toIntOrNull() ?: 0) { input.read() }
                    requests.add(first to headers)
                    val reply = respond(requests.size)
                    val bytes = reply.compressed ?: reply.body.toByteArray()
                    val response = buildString {
                        append("HTTP/1.1 ${reply.status} Fixture\r\nContent-Type: ${reply.contentType}\r\nConnection: close\r\n")
                        reply.location?.let { append("Location: $it\r\n") }
                        reply.encoding?.let { append("Content-Encoding: $it\r\n") }
                        append("Content-Length: ${if (reply.hold) 100 else bytes.size}\r\n\r\n")
                    }
                    socket.getOutputStream()
                        .apply { write(response.toByteArray()); write(bytes); flush() }
                    if (reply.hold) disconnected = input.read() == -1
                    mClients.remove(socket)
                }
            }
        }

        override fun close() {
            mListener.close()
            mClients.forEach { runCatching { it.close() } }
            mWorker.join(1000)
        }
    }

    /** 只在测试中把已通过 DNS 检查的公网路由接到本地合成服务。 */
    private class FixtureSocketFactory : SocketFactory() {
        override fun createSocket(): Socket = object : Socket() {
            override fun connect(endpoint: SocketAddress, timeout: Int) {
                endpoint as InetSocketAddress
                super.connect(InetSocketAddress("127.0.0.1", endpoint.port), timeout)
            }
        }

        override fun createSocket(host: String, port: Int): Socket = error("Unused socket overload")
        override fun createSocket(
            host: String,
            port: Int,
            localHost: InetAddress,
            localPort: Int
        ): Socket = error("Unused socket overload")

        override fun createSocket(host: InetAddress, port: Int): Socket =
            error("Unused socket overload")

        override fun createSocket(
            address: InetAddress,
            port: Int,
            localAddress: InetAddress,
            localPort: Int
        ): Socket = error("Unused socket overload")
    }
}
