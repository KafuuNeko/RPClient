package me.kafuuneko.rpclient.libs.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.llm.ImageRequestException
import me.kafuuneko.rpclient.libs.llm.ImageRequestFailure
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.room.model.MessageImagePolicy
import me.kafuuneko.rpclient.libs.room.model.MessageWithImages
import me.kafuuneko.rpclient.libs.room.model.PreparedFile
import me.kafuuneko.rpclient.libs.room.repository.FileRepository

/**
 * 共享图片处理服务。
 * - 原图保留在文件仓库，解码、方向校正与发送缓存均在 IO 线程串行执行。
 * - 发送参数固定，避免摘要前缀搜索时改变图片成本；可重建缓存不参与备份。
 */
class MessageImageRuntime(private val mContext: Context, private val mFiles: FileRepository) {
    private val mMutex = Mutex()
    private val mCache = File(mContext.cacheDir, "message-images-v1").apply { mkdirs() }

    /** 请求暂存与可重建图片缓存分开；旧进程中断留下的文件延迟回收。 */
    fun createRequestFile(): File {
        val directory = File(mContext.cacheDir, "image-requests").apply { mkdirs() }
        directory.listFiles().orEmpty().filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }
            .forEach { it.delete() }
        return File.createTempFile("request-", ".json", directory)
    }

    /** 复制选择器 URI 并验证实际图像；失败时释放暂存，调用方只保存已就绪凭据。 */
    suspend fun prepare(owner: String, uri: Uri): PreparedFile {
        val prepared = mFiles.prepareFile(owner, uri)
        try {
            mFiles.withPreparedFile(prepared) { file ->
                mMutex.withLock { prepareVersion(prepared.file.uuid, prepared.file.hash, file) }
            }
            return prepared
        } catch (error: Throwable) {
            mFiles.releasePrepared(prepared)
            throw error
        }
    }

    /** 为一致聚合历史生成图片元数据，纯逻辑 Builder 不访问文件系统。 */
    suspend fun references(messages: List<MessageWithImages>): Map<Long, List<LLMImageReference>> =
        withContext(Dispatchers.IO) {
            messages.associate { message ->
                message.key.messageId to message.images.map { attachment ->
                    val index = attachment.file ?: throw ImageRequestException(ImageRequestFailure.Missing)
                    try {
                        mFiles.withFileLease(index.uuid) { original ->
                            mMutex.withLock { prepareVersion(index.uuid, index.hash, original) }
                        }
                    } catch (error: Exception) {
                        currentCoroutineContext().ensureActive()
                        throw if (error is ImageRequestException) error else ImageRequestException(ImageRequestFailure.InvalidImage)
                    }
                }
            }
        }

    /** 持原图租约重新核对发送快照，回调期间缓存不会被其他图片处理回收。 */
    suspend fun <T> withSendFile(reference: LLMImageReference, block: (File) -> T): T =
        mFiles.withFileLease(reference.uuid) { original ->
            val index = requireNotNull(mFiles.getFileEntity(reference.uuid)) { "历史图片缺失" }
            mMutex.withLock {
                val current = prepareVersion(index.uuid, index.hash, original)
                require(current == reference) { "图片发送版本已变化，请重试" }
                block(File(mCache, current.cacheKey))
            }
        }

    /** 按需加载列表缩略图或查看器版本；失败返回 null，由 UI 显示缺图占位。 */
    suspend fun load(uuid: String, prepared: PreparedFile? = null, longEdge: Int = 384): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                if (prepared != null) mFiles.withPreparedFile(prepared) { decode(it, longEdge) }
                else mFiles.withFileLease(uuid) { decode(it, longEdge) }
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                null
            }
        }

    /** 用户显式保存图片时复制原始字节，不导出缓存或内部路径。 */
    suspend fun save(uuid: String, destination: Uri, prepared: PreparedFile? = null) {
        val copy: suspend (File) -> Unit = { original ->
            val output = mContext.contentResolver.openOutputStream(destination)
                ?: error("无法保存图片")
            output.use { target -> original.inputStream().use { it.copyTo(target) } }
        }
        if (prepared != null) mFiles.withPreparedFile(prepared, copy)
        else mFiles.withFileLease(uuid, copy)
    }

    /** 预览真实编码后的发送缓存，与请求使用相同压缩和尺寸。 */
    suspend fun loadSendPreview(uuid: String, prepared: PreparedFile?): Bitmap? = withContext(Dispatchers.IO) {
        val read: suspend (File, String) -> Bitmap? = { original, hash ->
            mMutex.withLock {
                val reference = prepareVersion(uuid, hash, original)
                decode(File(mCache, reference.cacheKey), MessageImagePolicy.SEND_LONG_EDGE)
            }
        }
        if (prepared != null) mFiles.withPreparedFile(prepared) { read(it, prepared.file.hash) }
        else mFiles.withFileLease(uuid) { read(it, requireNotNull(mFiles.getFileEntity(uuid)).hash) }
    }

    /** 固定处理策略生成发送版本，缓存发布前检查像素和实际文件大小。 */
    private suspend fun prepareVersion(uuid: String, hash: String, original: File): LLMImageReference {
        val key = "$hash-send"
        val target = File(mCache, key)
        if (!target.isFile) {
            validate(original)
            var bitmap = decode(original, MessageImagePolicy.SEND_LONG_EDGE)
                ?: error("图片损坏或设备不支持此格式")
            val temporary = File(mCache, UUID.randomUUID().toString())
            try {
                // 透明图片保留 Alpha；高噪声图片逐步缩小，确保发送大小受控。
                while (true) {
                    currentCoroutineContext().ensureActive()
                    withContext(Dispatchers.IO) {
                        FileOutputStream(temporary).use { output ->
                            val format =
                                if (bitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                            check(bitmap.compress(format, 85, output)) { "无法处理图片" }
                        }
                    }
                    if (temporary.length() <= MessageImagePolicy.MAX_SEND_BYTES) break
                    require(maxOf(bitmap.width, bitmap.height) > 128) { "图片超过发送大小限制" }
                    val scaled = bitmap.scale(
                        (bitmap.width * 0.75).roundToInt().coerceAtLeast(1),
                        (bitmap.height * 0.75).roundToInt().coerceAtLeast(1)
                    )
                    bitmap.recycle()
                    bitmap = scaled
                }
                check(temporary.renameTo(target)) { "无法缓存图片" }
            } finally {
                bitmap.recycle()
                temporary.delete()
            }
        }
        // 保留最近使用的缓存，正在使用的目标始终排除在回收范围之外。
        target.setLastModified(System.currentTimeMillis())
        var cacheBytes = mCache.listFiles().orEmpty().sumOf { it.length() }
        mCache.listFiles().orEmpty().filter { it != target }.sortedBy { it.lastModified() }.forEach {
            if (cacheBytes > 64L * 1024 * 1024) {
                val size = it.length()
                if (it.delete()) cacheBytes -= size
            }
        }
        val bounds = bounds(target)
        return LLMImageReference(uuid, key, bounds.outMimeType, bounds.outWidth, bounds.outHeight, target.length())
    }

    /** 通过解码器与容器结构校验真实格式，禁止伪 MIME、动画和超大像素。 */
    private fun validate(file: File) {
        require(file.length() <= MessageImagePolicy.MAX_ORIGINAL_BYTES) { "原图超过 32 MiB" }
        val bounds = bounds(file)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "图片损坏或设备不支持此格式" }
        require(bounds.outWidth.toLong() * bounds.outHeight <= MessageImagePolicy.MAX_ORIGINAL_PIXELS) {
            "原图超过 4800 万像素"
        }
        require(bounds.outMimeType in setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")) {
            "仅支持静态 JPEG、PNG、WebP 和设备可读取的 HEIC 图片"
        }
        // PNG / WebP 按容器块跳转检查动画标记，不将压缩像素误识别成块头。
        RandomAccessFile(file, "r").use { input ->
            var offset = if (bounds.outMimeType == "image/png") 8L else 12L
            while (bounds.outMimeType in setOf("image/png", "image/webp") && offset + 8 <= input.length()) {
                input.seek(offset)
                val png = bounds.outMimeType == "image/png"
                val size = if (png) input.readInt().toLong() and 0xffffffffL else 0L
                val name = ByteArray(4).also { input.readFully(it) }.toString(Charsets.US_ASCII)
                val length = if (png) size else Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL
                require(name !in setOf("acTL", "ANIM", "ANMF")) { "暂不支持动画图片，请选择静态图片" }
                offset += 8 + length + if (png) 4 else length % 2
            }
        }
    }

    /** 读取像素信息，不分配整图内存。 */
    private fun bounds(file: File): BitmapFactory.Options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, this)
    }

    /** 有界采样后应用全部 EXIF 镜像及旋转方向，最后精确限制长边。 */
    private fun decode(file: File, edge: Int): Bitmap? {
        val bounds = bounds(file)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > edge * 2) sample *= 2
        var bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample
        }) ?: return null
        // ExifInterface 统一表示旋转和水平翻转，涵盖八种方向。
        val exif = runCatching { ExifInterface(file) }.getOrNull()
        val matrix = Matrix().apply {
            if (exif?.isFlipped == true) postScale(-1f, 1f)
            postRotate((exif?.rotationDegrees ?: 0).toFloat())
        }
        val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (oriented !== bitmap) bitmap.recycle()
        bitmap = oriented
        val ratio = edge.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (ratio >= 1f) return bitmap
        val scaled = bitmap.scale(
            (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
            (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}
