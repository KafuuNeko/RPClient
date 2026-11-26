package me.kafuuneko.rpclient.libs.agent

import com.google.gson.JsonObject
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import me.kafuuneko.rpclient.libs.llm.tool.parseObject
import okio.buffer
import okio.source

/** 本地合成模型服务，记录真实 HTTP 请求并可模拟断流、拒绝及保持连接。 */
internal class AgentHttpServer(private val respond: (Int, JsonObject) -> Reply) : AutoCloseable {
    data class Reply(
        val body: String,
        val stream: Boolean = false,
        val hold: Boolean = false,
        val status: Int = 200
    )

    private val mSocket = ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"))
    private val mClients = CopyOnWriteArrayList<Socket>()
    val requests = CopyOnWriteArrayList<JsonObject>()
    val requested = Channel<JsonObject>(Channel.UNLIMITED)
    val disconnected = CompletableDeferred<Unit>()
    val port: Int get() = mSocket.localPort
    val baseUrl: String get() = "http://127.0.0.1:$port"
    private val mWorker = thread(isDaemon = true, name = "agent-model-fixture") {
        while (!mSocket.isClosed) try {
            mSocket.accept().use { client ->
                mClients.add(client)
                client.soTimeout = 10_000
                val input = client.getInputStream().source().buffer()
                input.readUtf8LineStrict()
                var length = 0L
                while (true) {
                    val line = input.readUtf8LineStrict()
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", true)) length =
                        line.substringAfter(':').trim().toLong()
                }
                val request = parseObject(input.readUtf8(length))
                requests.add(request)
                requested.trySend(request)
                val reply = respond(requests.size, request)
                val bytes = reply.body.toByteArray()
                val headers =
                    "HTTP/1.1 ${reply.status} Fixture\r\nContent-Type: ${if (reply.stream) "text/event-stream" else "application/json"}\r\nConnection: close\r\n" + if (reply.hold) "\r\n" else "Content-Length: ${bytes.size}\r\n\r\n"
                client.getOutputStream().apply {
                    write(headers.toByteArray())
                    bytes.asList().chunked(17).forEach { write(it.toByteArray()) }
                    flush()
                }
                if (reply.hold) {
                    check(input.exhausted())
                    disconnected.complete(Unit)
                }
                mClients.remove(client)
            }
        } catch (error: Exception) {
            // 客户端可在服务端写分片时取消；Broken pipe 同样证明连接已释放。
            if (error is SocketException) disconnected.complete(Unit)
            else if (!mSocket.isClosed) disconnected.completeExceptionally(error)
        }
    }

    /** 即使断言失败，也关闭本测试的监听和活动连接。 */
    override fun close() {
        mSocket.close()
        mClients.forEach { it.close() }
        mWorker.join(1000)
        requested.close()
    }
}
