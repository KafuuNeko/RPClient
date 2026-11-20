package me.kafuuneko.rpclient.libs.memory

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * 独立记忆包的文件边界。
 * - 文件读写在 IO 调度器执行，Activity 仅提供系统选择的 URI。
 * - 限制输入体积并拒绝损坏的 UTF-8，解析成功后再由记忆仓库原子写入。
 */
class MemoryTransferRepository(
    private val mContext: Context,
) {
    /**
     * 读取系统选择的文件，映射确认前不写入仓库。
     * @param uri 获得读取授权的文档 URI。
     * @return 待用户确认映射的记忆包。
     */
    suspend fun readPackage(uri: Uri): MemoryPackage = withContext(Dispatchers.IO) {
        val source =
            requireNotNull(mContext.contentResolver.openInputStream(uri)) { "Cannot read memory package" }
        val bytes = source.use(::readBounded)
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        MemoryPackageCodec.decode(decoder.decode(ByteBuffer.wrap(bytes)).toString())
    }

    /**
     * 写入点击导出时捕获的快照，文件选择期间的编辑不会替换导出内容。
     * @param uri 获得写入授权的文档 URI。
     * @param json 已校验的便携包快照。
     */
    suspend fun writePackage(uri: Uri, json: String) = withContext(Dispatchers.IO) {
        require(json.toByteArray(Charsets.UTF_8).size <= MemoryPackageCodec.MAX_BYTES) {
            "Memory package exceeds size limit"
        }
        requireNotNull(
            mContext.contentResolver.openOutputStream(uri, "wt"),
        ) { "Cannot write memory package" }
            .bufferedWriter(Charsets.UTF_8).use { it.write(json) }
    }

    /** 在缓冲区追加前检查字节上限，不信任文档提供者报告的文件大小。 */
    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return output.toByteArray()
            require(output.size() + count <= MemoryPackageCodec.MAX_BYTES) {
                "Memory package exceeds size limit"
            }
            output.write(buffer, 0, count)
        }
    }
}
