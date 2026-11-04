package me.kafuuneko.rpclient.libs.media

import android.graphics.BitmapFactory
import java.io.File
import java.io.RandomAccessFile
import me.kafuuneko.rpclient.libs.llm.ImageRequestException
import me.kafuuneko.rpclient.libs.llm.ImageRequestFailure
import me.kafuuneko.rpclient.libs.room.model.MessageImagePolicy

/**
 * 本地与归档图片共用的原始文件校验器。
 * - 只读取图像边界和容器块，不解码完整像素，也不改变原始字节。
 * - 统一限制大小、像素、格式及动画，避免备份恢复绕过普通导入约束。
 */
internal object MessageImageValidator {
    /**
     * 校验文件并返回真实 MIME，忽略归档自行声明的类型。
     * @param file 已复制到调用方暂存目录的原始图片。
     * @return 解码器识别的受支持 MIME。
     * @throws ImageRequestException 图片超限、格式无效或包含动画。
     */
    fun mimeType(file: File): String {
        requireValidImage(file.length() <= MessageImagePolicy.MAX_ORIGINAL_BYTES)
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.absolutePath, this)
        }
        // 像素上限与普通选图一致，伪造 MIME 不能成为有效图片。
        requireValidImage(bounds.outWidth > 0 && bounds.outHeight > 0)
        requireValidImage(bounds.outWidth.toLong() * bounds.outHeight <= MessageImagePolicy.MAX_ORIGINAL_PIXELS)
        requireValidImage(
            bounds.outMimeType in setOf(
                "image/jpeg",
                "image/png",
                "image/webp",
                "image/heic",
                "image/heif"
            )
        )
        val mimeType = requireNotNull(bounds.outMimeType)
        rejectAnimation(file, mimeType)
        return mimeType
    }

    /** 按容器块长度跳转，避免将压缩像素里的相同字节误判成动画标记。 */
    private fun rejectAnimation(file: File, mimeType: String) {
        if (mimeType != "image/png" && mimeType != "image/webp") return
        RandomAccessFile(file, "r").use { input ->
            val png = mimeType == "image/png"
            var offset = if (png) 8L else 12L
            // PNG 和 WebP 的块长度端序不同，尾部对齐也按各自容器规则处理。
            while (offset + 8 <= input.length()) {
                input.seek(offset)
                val size = if (png) input.readInt().toLong() and 0xffffffffL else 0L
                val name = ByteArray(4).also { bytes -> input.readFully(bytes) }
                    .toString(Charsets.US_ASCII)
                val length = if (png) size else Integer.reverseBytes(input.readInt())
                    .toLong() and 0xffffffffL
                requireValidImage(name !in setOf("acTL", "ANIM", "ANMF"))
                offset += 8 + length + if (png) 4 else length % 2
            }
        }
    }

    /** 保持普通选图与归档导入的受控错误类型一致。 */
    private fun requireValidImage(valid: Boolean) {
        if (!valid) throw ImageRequestException(ImageRequestFailure.InvalidImage)
    }
}
