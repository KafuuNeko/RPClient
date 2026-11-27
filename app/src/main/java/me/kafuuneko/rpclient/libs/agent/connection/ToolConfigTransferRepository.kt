package me.kafuuneko.rpclient.libs.agent.connection

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 工具便携包的系统文件仓库。
 * - 使用 IO 调度器和有界 UTF-8 读取，拒绝损坏或超量输入。
 * - 只读写用户选择的配置文档，不访问凭据目录及运行检查点。
 */
class ToolConfigTransferRepository(private val mContext: Context) {
    /**
     * 读取有界 UTF-8 文档，拒绝错误编码和伪造文件大小。
     *
     * @param uri 系统选择器返回的已授权文档 URI。
     * @return 通过大小和 UTF-8 检查的便携包文本。
     */
    suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        val input =
            requireNotNull(mContext.contentResolver.openInputStream(uri)) { "Cannot read tool package" }
        val output = ByteArrayOutputStream()
        // 实际读取流时限流，不依赖文档提供者的元数据。
        input.use { source ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                require(output.size() + count <= ToolConfigCodec.MAX_PACKAGE_BYTES) { "Tool package is too large" }
                output.write(buffer, 0, count)
            }
        }
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(output.toByteArray())).toString()
    }

    /**
     * 写入点击导出时捕获的非敏感配置快照。
     *
     * @param uri 系统选择器返回的已授权文档 URI。
     * @param json 点击导出时冻结的非敏感配置快照。
     */
    suspend fun write(uri: Uri, json: String) = withContext(Dispatchers.IO) {
        require(json.toByteArray(Charsets.UTF_8).size <= ToolConfigCodec.MAX_PACKAGE_BYTES) { "Tool package is too large" }
        requireNotNull(
            mContext.contentResolver.openOutputStream(
                uri,
                "wt"
            )
        ) { "Cannot write tool package" }
            .bufferedWriter(Charsets.UTF_8).use { it.write(json) }
    }
}
