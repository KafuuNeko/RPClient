package me.kafuuneko.rpclient.libs.agent

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.SocketFactory
import kotlin.concurrent.thread
import me.kafuuneko.rpclient.libs.agent.connection.ToolHttpEngine
import okhttp3.Dns
import okio.buffer
import okio.source

/** HTTP 工具合成服务，区分目标、认证与请求体，支持 JSON 和 HTML。 */
internal class ToolFixtureServer(private val respond: (Request) -> Reply) : AutoCloseable {
    data class Request(val target: String, val headers: Map<String, String>, val body: String)
    data class Reply(val body: String, val type: String = "application/json", val status: Int = 200)

    private val mListener = ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"))
    private val mClients = CopyOnWriteArrayList<Socket>()
    val requests = CopyOnWriteArrayList<Request>()
    val url: String get() = "http://tool.fixture:${mListener.localPort}/"
    private val mWorker = thread(isDaemon = true, name = "http-tool-fixture") {
        while (!mListener.isClosed) runCatching {
            mListener.accept().use { client ->
                mClients.add(client)
                client.soTimeout = 5000
                val input = client.getInputStream().source().buffer()
                val target = input.readUtf8LineStrict()
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = input.readUtf8LineStrict()
                    if (line.isEmpty()) break
                    headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
                }
                val request = Request(
                    target,
                    headers,
                    input.readUtf8(headers["content-length"]?.toLongOrNull() ?: 0)
                )
                requests.add(request)
                val reply = respond(request)
                val bytes = reply.body.toByteArray()
                client.getOutputStream().apply {
                    write("HTTP/1.1 ${reply.status} Fixture\r\nContent-Type: ${reply.type}\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(bytes)
                    flush()
                }
                mClients.remove(client)
            }
        }
    }

    override fun close() {
        mListener.close()
        mClients.forEach { runCatching { it.close() } }
        mWorker.join(1000)
    }
}

/** 保留生产 DNS 校验，只在测试 socket 上把公共路由接到隔离合成服务。 */
internal fun fixtureToolHttpEngine(): ToolHttpEngine = ToolHttpEngine(
    object : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            listOf(InetAddress.getByName("8.8.8.8"))
    },
    object : SocketFactory() {
        override fun createSocket(): Socket = object : Socket() {
            override fun connect(endpoint: SocketAddress, timeout: Int) {
                endpoint as InetSocketAddress
                super.connect(InetSocketAddress("127.0.0.1", endpoint.port), timeout)
            }
        }

        override fun createSocket(host: String, port: Int): Socket =
            error("Unused fixture overload")

        override fun createSocket(
            host: String,
            port: Int,
            localHost: InetAddress,
            localPort: Int
        ): Socket = error("Unused fixture overload")

        override fun createSocket(host: InetAddress, port: Int): Socket =
            error("Unused fixture overload")

        override fun createSocket(
            address: InetAddress,
            port: Int,
            localAddress: InetAddress,
            localPort: Int
        ): Socket = error("Unused fixture overload")
    },
)
