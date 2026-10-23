package me.kafuuneko.rpclient.libs.chat

import me.kafuuneko.rpclient.libs.room.model.MessageImagePolicy
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 归档图片字节的无损传输封装。
 * - 摘要始终针对解压后的文件字节，保持与 File 仓库相同的内容标识。
 * - GZIP 至少节省百分之五才采用；导入限制解压后的实际大小。
 */
internal object ChatArchiveImagePayload {
    /** 打开原始文件字节流；调用方负责关闭并在读取过程中限制大小。 */
    fun open(image: ChatArchiveImage): InputStream {
        require(
            image.compression in setOf(
                "none",
                "gzip"
            )
        ) { "Unsupported archive image compression" }
        val input = ChatArchiveImageCodec.openData(requireNotNull(image.data))
        return try {
            if (image.compression == "gzip") GZIPInputStream(input) else input
        } catch (error: Throwable) {
            input.close()
            throw error
        }
    }

    /** 边复制边限制解压后的字节数并计算原始摘要，返回已校验的实际 hash。 */
    fun copyOriginal(
        input: InputStream,
        output: OutputStream,
        expectedHash: String? = null,
        checkActive: () -> Unit = {},
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var total = 0L
        // 限制按实际输出累计，不信任 GZIP 元数据中的原始大小。
        while (true) {
            checkActive()
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MessageImagePolicy.MAX_ORIGINAL_BYTES) { "Archive image is too large" }
            digest.update(buffer, 0, count)
            output.write(buffer, 0, count)
        }
        require(total > 0) { "Empty archive image" }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        require(expectedHash == null || expectedHash == hash) { "Archive image hash mismatch" }
        return hash
    }

    /** 为文件导出试压缩；临时文件由调用方拥有并在成功、异常或取消后释放。 */
    fun compress(
        file: File,
        temporary: File,
        hash: String,
        checkActive: () -> Unit,
    ): Boolean {
        file.inputStream().use { input ->
            GZIPOutputStream(temporary.outputStream()).use { output ->
                copyOriginal(input, output, hash, checkActive)
            }
        }
        return shouldCompress(file.length(), temporary.length())
    }

    /** 小型内存归档入口；正式文件导出使用 compress 和分块 Base64，不缓存整张图片。 */
    fun encode(image: ChatArchiveImage): ChatArchiveImage {
        val original = ByteArrayOutputStream()
        // 即使输入已经是 GZIP，也先恢复原始字节再校验，不对压缩载荷计算内容标识。
        val hash = open(image).use { copyOriginal(it, original, image.hash) }
        val bytes = original.toByteArray()
        val compressed = ByteArrayOutputStream()
        GZIPOutputStream(compressed).use { it.write(bytes) }
        val useGzip = shouldCompress(bytes.size.toLong(), compressed.size().toLong())
        return ChatArchiveImage(
            mimeType = requireNotNull(image.mimeType),
            data = Base64.getEncoder()
                .encodeToString(if (useGzip) compressed.toByteArray() else bytes),
            hash = hash,
            compression = if (useGzip) "gzip" else "none",
        )
    }

    private fun shouldCompress(original: Long, compressed: Long): Boolean =
        compressed * 100 <= original * 95
}
