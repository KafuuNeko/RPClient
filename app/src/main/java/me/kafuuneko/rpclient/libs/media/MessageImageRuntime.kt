package me.kafuuneko.rpclient.libs.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.ImageRequestException
import me.kafuuneko.rpclient.libs.llm.ImageRequestFailure
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.prompt.model.PromptImagePreparation
import me.kafuuneko.rpclient.libs.prompt.model.UnavailablePromptImage
import me.kafuuneko.rpclient.libs.room.model.MessageImageInput
import me.kafuuneko.rpclient.libs.room.model.MessageImagePolicy
import me.kafuuneko.rpclient.libs.room.model.MessageImageWithFile
import me.kafuuneko.rpclient.libs.room.model.MessageWithImages
import me.kafuuneko.rpclient.libs.room.model.PreparedFile
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import kotlin.math.roundToInt

/**
 * 共享图片处理服务。
 * - 选图只校验暂存，提交时处理新增图片，最终字节统一交给文件仓库持有。
 * - 历史、预览与请求读取同一文件，不再生成独立发送缓存或重复压缩。
 */
class MessageImageRuntime(
    private val mContext: Context,
    private val mFiles: FileRepository,
    private val mSettings: () -> ImageSendSettings = { ImageSendSettings.decode(AppModel.imageSendSettings) }
) {
    /**
     * 请求 JSON 只在网络调用期间保留；旧进程中断留下的文件延迟回收。
     *
     * @return 本次请求独占的临时 JSON 文件，调用方使用后删除。
     */
    fun createRequestFile(): File {
        val directory = File(mContext.cacheDir, "image-requests").apply { mkdirs() }
        directory.listFiles().orEmpty()
            .filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }
            .forEach { it.delete() }
        return File.createTempFile("request-", ".json", directory)
    }

    /**
     * 复制选择器 URI 并验证实际图像；压缩延迟至发送或编辑保存时执行。
     *
     * @param owner 当前草稿所有者标识。
     * @param uri 系统选择器授予读取权限的资源 URI。
     * @return 已完成格式校验的源图片暂存凭据。
     */
    suspend fun prepare(owner: String, uri: Uri): PreparedFile {
        // 原图复制完成后仍由草稿持有，只有图像校验成功才交给页面。
        val prepared = mFiles.prepareFile(owner, uri)
        try {
            mFiles.withPreparedFile(prepared) { file ->
                validate(file)
            }
            return prepared
        } catch (error: Throwable) {
            mFiles.releasePrepared(prepared)
            throw error
        }
    }

    /** 在文件仓库保护范围内检查归档资源，导出 MIME 必须与实际字节一致。 */
    internal fun archiveMimeType(file: File): String {
        validate(file)
        return requireNotNull(bounds(file).outMimeType)
    }

    /**
     * 恢复归档中的原有字节，只校验实际格式，不应用当前发送压缩设置。
     * @param owner 本次导入所有者。
     * @param input 调用方提供的资源流，由本方法关闭。
     * @return 可在消息导入事务中提交的最终图片凭据。
     */
    suspend fun prepareArchive(owner: String, input: InputStream): PreparedFile =
        input.use { source ->
            mFiles.prepareGenerated(owner) { target ->
                // 边复制边检查资源上限，避免先写完超大文件再校验。
                target.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        requireValidImage(total <= MessageImagePolicy.MAX_ORIGINAL_BYTES)
                        output.write(buffer, 0, count)
                    }
                }
                validate(target)
                requireNotNull(bounds(target).outMimeType)
            }
        }

    /**
     * 为候选历史收集资源与受控失败，错误是否阻断发送由最终选择决定。
     *
     * @param messages 包含有序附件的候选消息列表。
     * @return 按消息 ID 组织的就绪资源和不可用原因。
     */
    suspend fun prepareCandidates(messages: List<MessageWithImages>): PromptImagePreparation =
        withContext(Dispatchers.IO) {
            val ready = mutableMapOf<Long, List<LLMImageReference>>()
            val unavailable = mutableMapOf<Long, List<UnavailablePromptImage>>()
            // 按消息和附件原顺序遍历，失败项不能使纯图消息变成空消息。
            for (message in messages) {
                val images = mutableListOf<LLMImageReference>()
                val failures = mutableListOf<UnavailablePromptImage>()
                for (attachment in message.images) {
                    try {
                        images += prepareAttachment(attachment)
                    } catch (error: ImageRequestException) {
                        // 超限或原图格式错误同样延迟至最终保留，旧历史被裁剪后不阻断发送。
                        if (error.failure !in setOf(
                                ImageRequestFailure.Missing,
                                ImageRequestFailure.InvalidImage,
                                ImageRequestFailure.OriginalUnsupported,
                                ImageRequestFailure.OriginalTooLarge,
                            )
                        ) throw error
                        failures += UnavailablePromptImage(
                            messageId = message.key.messageId,
                            uuid = attachment.image.imageUuid,
                            position = attachment.image.position,
                            failure = error.failure,
                        )
                    }
                }
                if (images.isNotEmpty()) ready[message.key.messageId] = images
                if (failures.isNotEmpty()) unavailable[message.key.messageId] = failures
            }
            PromptImagePreparation(ready, unavailable)
        }

    /** 只归类明确资源 IO 错误；协程取消和未知程序错误继续传播。 */
    private suspend fun prepareAttachment(
        attachment: MessageImageWithFile,
    ): LLMImageReference {
        val index = attachment.file ?: throw ImageRequestException(ImageRequestFailure.Missing)
        return try {
            mFiles.withFileLease(index.uuid) { original ->
                describe(index.uuid, original)
            }
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            throw ImageRequestException(ImageRequestFailure.InvalidImage)
        }
    }

    /**
     * 严格准备入口供直接发送资源的调用方使用，候选裁剪使用 prepareCandidates。
     *
     * @param messages 本次需要发送的完整消息列表。
     * @return 按消息 ID 组织、保持附件顺序的发送引用。
     * @throws ImageRequestException 任一消息存在缺失或无效图片时抛出。
     */
    suspend fun references(messages: List<MessageWithImages>): Map<Long, List<LLMImageReference>> {
        val prepared = prepareCandidates(messages)
        prepared.unavailable.values.firstOrNull { it.isNotEmpty() }?.first()
            ?.let { unavailablePromptImage ->
                throw ImageRequestException(unavailablePromptImage.failure)
            }
        return prepared.references
    }

    /**
     * 持仓库读取保护消费已提交图片；不按当前设置重新编码历史附件。
     *
     * @param reference 请求准备阶段读取的不可变文件描述。
     * @param block 在读取保护范围内同步消费图片的操作。
     * @return 消费操作的结果。
     */
    suspend fun <T> withSendFile(reference: LLMImageReference, block: (File) -> T): T =
        mFiles.withFileLease(reference.uuid) { file -> block(file) }

    /**
     * 为发送和编辑保存统一处理新增附件；已有附件保持不变。
     * - 图片处理可取消且在事务外完成；提交失败只释放处理结果，保留源草稿供重试。
     * - 提交与成功收尾不可中断，避免数据库已提交却仍把原草稿当作待发送图片。
     *
     * @param inputs 本次冻结的有序附件列表。
     * @param commit 接收最终附件并原子提交消息的操作，不应包含网络请求。
     * @return 提交结果。
     */
    suspend fun <T> submit(
        inputs: List<MessageImageInput>,
        commit: suspend (List<MessageImageInput>) -> T,
    ): T {
        val originals = inputs.filterIsInstance<MessageImageInput.Prepared>().map { it.value }
        require(originals.map { it.handle }.distinct().size == originals.size) {
            "The same image draft cannot be committed twice"
        }
        val generated = mutableListOf<PreparedFile>()
        val settings = mSettings()
        try {
            // 同一次提交共用设置快照；最终凭据在处理完成后才交给消息事务。
            val finalInputs = inputs.map { input ->
                if (input !is MessageImageInput.Prepared) input
                else MessageImageInput.Prepared(process(input.value, settings).also(generated::add))
            }
            currentCoroutineContext().ensureActive()
            return withContext(NonCancellable) {
                commit(finalInputs).also {
                    originals.forEach { preparedFile ->
                        mFiles.releasePrepared(
                            preparedFile
                        )
                    }
                }
            }
        } finally {
            generated.forEach { mFiles.releasePrepared(it) }
        }
    }

    /** 按提交设置生成最终字节和实际 MIME；源草稿保留到整个消息提交成功。 */
    private suspend fun process(prepared: PreparedFile, settings: ImageSendSettings): PreparedFile {
        require(settings.isValid()) { "Invalid image send settings" }
        return mFiles.prepareGenerated(prepared.ownerId) { target ->
            mFiles.withPreparedFile(prepared) { source ->
                validate(source)
                // 原图模式只复制已校验的字节，其他模式只在此次入库前执行一次压缩。
                if (settings.mode == ImageSendMode.Original) {
                    validateSendable(source)
                    source.inputStream().use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                } else {
                    encodeCompressed(source, target, settings)
                }
                requireNotNull(bounds(target).outMimeType) { "Invalid generated image" }
            }
        }
    }

    /**
     * 按需加载列表缩略图或查看器版本；失败返回 null，由 UI 显示缺图占位。
     *
     * @param uuid 图片文件 ID。
     * @param prepared 未提交草稿的凭据；历史图片传 null。
     * @param longEdge 解码结果的最大长边像素数。
     * @return 有界解码后的图片；文件不可用时返回 null，取消继续传播。
     */
    suspend fun load(uuid: String, prepared: PreparedFile? = null, longEdge: Int = 384): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                if (prepared != null) mFiles.withPreparedFile(prepared) { file ->
                    decode(
                        file,
                        longEdge
                    )
                }
                else mFiles.withFileLease(uuid) { file -> decode(file, longEdge) }
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                null
            }
        }

    /**
     * 校验原始文件并生成保存元数据，不信任选图来源声明的 MIME。
     *
     * @param uuid 图片文件 ID。
     * @param prepared 尚未持久化的草稿凭据；历史图片传 null。
     * @return 与原始字节格式一致的 MIME 和带扩展名的默认文件名。
     * @throws ImageRequestException 图片格式无效或文件缺失时抛出。
     */
    suspend fun exportMetadata(uuid: String, prepared: PreparedFile? = null): ImageExportMetadata {
        // 在文件仓库保护范围内检查实际容器，草稿和历史图片都以当前字节为准。
        val inspect: suspend (File) -> ImageExportMetadata = { original ->
            validate(original)
            val mimeType = bounds(original).outMimeType
            val extension = when (mimeType) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/heic" -> "heic"
                "image/heif" -> "heif"
                else -> throw ImageRequestException(ImageRequestFailure.InvalidImage)
            }
            ImageExportMetadata(mimeType, "image.$extension")
        }
        // 草稿与历史文件沿用各自的所有权保护，IO 由文件仓库调度。
        return if (prepared != null) mFiles.withPreparedFile(prepared, inspect)
        else mFiles.withFileLease(uuid, inspect)
    }

    /**
     * 用户显式保存图片时复制仓库字节，不导出内部路径。
     *
     * @param uuid 选择器启动前冻结的图片文件 ID。
     * @param destination 用户确认的目标文档 URI。
     * @param prepared 未提交草稿的凭据；历史图片传 null。
     */
    suspend fun save(uuid: String, destination: Uri, prepared: PreparedFile? = null) {
        val copy: suspend (File) -> Unit = { original ->
            val output = mContext.contentResolver.openOutputStream(destination)
                ?: error("Could not save the image")
            output.use { target ->
                original.inputStream().use { fileInputStream -> fileInputStream.copyTo(target) }
            }
        }
        if (prepared != null) mFiles.withPreparedFile(prepared, copy)
        else mFiles.withFileLease(uuid, copy)
    }

    /** 读取已入库图片的实际元数据，历史请求不再依赖发送设置。 */
    private fun describe(uuid: String, file: File): LLMImageReference {
        val bounds = bounds(file)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw ImageRequestException(ImageRequestFailure.InvalidImage)
        }
        validateSendable(file)
        val rotation =
            runCatching { ExifInterface(file).rotationDegrees }.getOrDefault(defaultValue = 0)
        val swap = rotation == 90 || rotation == 270
        return LLMImageReference(
            uuid = uuid,
            mimeType = bounds.outMimeType,
            width = if (swap) bounds.outHeight else bounds.outWidth,
            height = if (swap) bounds.outWidth else bounds.outHeight,
            byteCount = file.length(),
        )
    }

    /** 原图模式与历史读取共用协议支持范围，禁止发送不支持或超过硬上限的文件。 */
    private fun validateSendable(file: File) {
        val bounds = bounds(file)
        if (bounds.outMimeType !in setOf("image/jpeg", "image/png", "image/webp")) {
            throw ImageRequestException(ImageRequestFailure.OriginalUnsupported)
        }
        if (file.length() > MessageImagePolicy.MAX_SEND_BYTES
            || maxOf(bounds.outWidth, bounds.outHeight) > 8000
        ) {
            throw ImageRequestException(ImageRequestFailure.OriginalTooLarge)
        }
    }

    /** 透明图片保留 Alpha；逐步缩小高噪声图片，直到实际文件满足用户上限。 */
    private suspend fun encodeCompressed(
        original: File,
        target: File,
        settings: ImageSendSettings
    ) {
        // 自动模式保留既有采样和质量，自定义模式额外施加宽高包围框。
        val edge =
            if (settings.mode == ImageSendMode.Custom) maxOf(settings.maxWidth, settings.maxHeight)
            else MessageImagePolicy.SEND_LONG_EDGE
        var bitmap =
            decode(original, edge) ?: throw ImageRequestException(ImageRequestFailure.InvalidImage)
        try {
            val (width, height) = settings.fitDimensions(bitmap.width, bitmap.height)
            if (width != bitmap.width || height != bitmap.height) {
                val scaled = bitmap.scale(width, height)
                bitmap.recycle()
                bitmap = scaled
            }
            // 不依赖压缩质量与文件大小单调对应，使用真实编码结果检查预算。
            while (true) {
                currentCoroutineContext().ensureActive()
                FileOutputStream(target).use { output ->
                    val format =
                        if (bitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                    if (!bitmap.compress(format, 85, output)) throw ImageRequestException(
                        ImageRequestFailure.InvalidImage
                    )
                }
                if (target.length() <= settings.maxBytes) break
                if (maxOf(bitmap.width, bitmap.height) <= 128) throw ImageRequestException(
                    ImageRequestFailure.InvalidImage
                )
                val scaled = bitmap.scale(
                    width = (bitmap.width * 0.75).roundToInt().coerceAtLeast(1),
                    height = (bitmap.height * 0.75).roundToInt().coerceAtLeast(1),
                )
                bitmap.recycle()
                bitmap = scaled
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** 通过解码器与容器结构校验真实格式，禁止伪 MIME、动画和超大像素。 */
    private fun validate(file: File) {
        requireValidImage(file.length() <= MessageImagePolicy.MAX_ORIGINAL_BYTES)
        val bounds = bounds(file)
        requireValidImage(bounds.outWidth > 0 && bounds.outHeight > 0)
        requireValidImage(bounds.outWidth.toLong() * bounds.outHeight <= MessageImagePolicy.MAX_ORIGINAL_PIXELS)
        requireValidImage(
            valid = bounds.outMimeType in setOf(
                "image/jpeg",
                "image/png",
                "image/webp",
                "image/heic",
                "image/heif",
            ),
        )
        // PNG / WebP 按容器块跳转检查动画标记，不将压缩像素误识别成块头。
        RandomAccessFile(file, "r").use { input ->
            var offset = if (bounds.outMimeType == "image/png") 8L else 12L
            while (bounds.outMimeType in setOf(
                    "image/png",
                    "image/webp"
                ) && offset + 8 <= input.length()
            ) {
                input.seek(offset)
                val png = bounds.outMimeType == "image/png"
                val size = if (png) input.readInt().toLong() and 0xffffffffL else 0L
                val name = ByteArray(4).also { byteArray -> input.readFully(byteArray) }
                    .toString(Charsets.US_ASCII)
                val length = if (png) size else Integer.reverseBytes(input.readInt())
                    .toLong() and 0xffffffffL
                requireValidImage(name !in setOf("acTL", "ANIM", "ANMF"))
                offset += 8 + length + if (png) 4 else length % 2
            }
        }
    }

    /** 解码和格式限制属于受控资源错误，不能与程序性 require 混淆。 */
    private fun requireValidImage(valid: Boolean) {
        if (!valid) throw ImageRequestException(ImageRequestFailure.InvalidImage)
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
        // 自定义上限可达 4096 px，额外约束采样像素，避免原图及旋转副本同时撑满堆内存。
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > edge * 2
            || bounds.outWidth.toLong() * bounds.outHeight / sample / sample > 4096L * 4096
        ) {
            sample *= 2
        }
        var bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample
            },
        ) ?: return null
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
            width = (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
            height = (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}
