package me.kafuuneko.rpclient.libs.appearance

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.room.model.PreparedFile
import me.kafuuneko.rpclient.libs.room.repository.FileRepository

/**
 * 背景专用图像管线，不依赖发送图片设置或模型附件。
 * - 私有暂存保存标准化源图，派生图按 hash 与算法版本缓存。
 * - 串行限制像素任务，取消后不发布旧结果；所有位图 IO 均在后台执行。
 */
class BackgroundImageRuntime(private val mContext: Context, private val mFiles: FileRepository) {
    private val mMutex = Mutex()
    private val mCleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mLowMemory = (mContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).let {
        it.isLowRamDevice || it.memoryClass < 192
    }
    private val mCache = object : LruCache<String, Bitmap>(if (mLowMemory) 8 * 1024 * 1024 else 32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val mDirectory by lazy { File(mContext.cacheDir, "backgrounds").apply { mkdirs() } }

    /** 有界导入并重编码，去掉无关元数据；取消或失败释放本次所有草稿。 */
    suspend fun prepare(owner: String, uri: Uri): PreparedFile {
        var result: PreparedFile? = null
        val source = mFiles.prepareFile(owner, uri)
        try {
            // 锁顺序始终为图像处理锁、文件锁，与预览渲染保持一致。
            try {
                result = mFiles.prepareGenerated(owner) { target ->
                    mMutex.withLock {
                        mFiles.withPreparedFile(source) { file -> normalizeSource(file, source.byteCount, target) }
                    }
                    "image/png"
                }
            } finally { mFiles.releasePrepared(source) }
            currentCoroutineContext().ensureActive()
            return requireNotNull(result)
        } catch (error: Throwable) {
            result?.let { mFiles.releasePrepared(it) }
            throw error
        }
    }

    /** 在像素分配前校验格式与资源上限，再生成不带 EXIF 的标准源图。 */
    private fun normalizeSource(file: File, byteCount: Long, target: File) {
        require(byteCount <= 32L * 1024 * 1024) { "Background is too large" }
        val info = bounds(file)
        require(info.outMimeType in setOf("image/jpeg", "image/png", "image/webp")) { "Unsupported background" }
        require(info.outWidth > 0 && info.outHeight > 0 &&
            info.outWidth.toLong() * info.outHeight <= 48_000_000) { "Invalid background dimensions" }
        // 始终从标准源图生成模糊结果，不叠加处理上一张预览。
        val bitmap = decode(file, if (mLowMemory) 2048 else 3072, orient = true)
        try {
            target.outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Background encoding failed" }
            }
        } finally { bitmap.recycle() }
    }

    /** 释放未提交草稿；独立清理作用域保证 ViewModel 销毁后仍能完成回收。 */
    fun discard(prepared: PreparedFile?) {
        if (prepared != null) mCleanupScope.launch { mFiles.releasePrepared(prepared) }
    }

    /**
     * 读取标准化图片并生成静态模糊结果，返回位图由缓存与 UI 共享，不得提前 recycle。
     * @param preview 拖动时使用较小处理分辨率且不写磁盘。
     * @param surfaceBlur 在页面背景模糊之上叠加的栏位强度，默认不做附加处理。
     */
    suspend fun render(settings: PageBackground, prepared: PreparedFile? = null, preview: Boolean = false, surfaceBlur: Int = 0): Bitmap? {
        if (settings.fileUuid == null && prepared == null) return null
        return withContext(Dispatchers.IO) {
            val entity = prepared?.file ?: mFiles.getFileEntity(requireNotNull(settings.fileUuid))
                ?: error("Missing background")
            val edge = if (preview) 400 else if (mLowMemory) 1280 else 2560
            val extraBlur = surfaceBlur.coerceIn(0, 100)
            val key = "v4_${entity.hash}_${settings.blur}_${extraBlur}_$edge"
            mCache.get(key)?.let { return@withContext it }
            // 同时只允许一项图像处理，避免三页恢复或快速预览造成内存峰值。
            mMutex.withLock {
                mCache.get(key)?.let { return@withLock it }
                val cached = File(mDirectory, "$key.png")
                if (!preview && cached.isFile) {
                    BitmapFactory.decodeFile(cached.path)?.let {
                        cached.setLastModified(System.currentTimeMillis())
                        mCache.put(key, it)
                        return@withLock it
                    }
                }
                val source = if (prepared != null) mFiles.withPreparedFile(prepared) { decode(it, edge) }
                    else mFiles.withFileLease(entity.uuid) { decode(it, edge) }
                // 附加模糊只改变背景像素，不对任何文字或操作控件截图。
                val result = blur(blur(source, settings.blur), extraBlur)
                currentCoroutineContext().ensureActive()
                if (!preview) runCatching { store(cached, result) }
                mCache.put(key, result)
                result
            }
        }
    }

    /**
     * 生成仍能透出背景的栏位派生图；不透明、零强度或无图时跳过计算。
     * - 同一强度复用已有缓存，四种栏位依次处理以限制内存峰值。
     * @return 仅包含实际启用模糊的栏位。
     */
    suspend fun renderSurfaces(
        settings: PageBackground, prepared: PreparedFile? = null, preview: Boolean = false
    ): Map<PageSurfaceKind, Bitmap> {
        if (settings.fileUuid == null && prepared == null) return emptyMap()
        val result = mutableMapOf<PageSurfaceKind, Bitmap>()
        // 透明度为零即不透明，不为不可见的背景模糊消耗像素内存。
        for (kind in PageSurfaceKind.entries) {
            val amount = settings.surfaceBlur(kind)
            if (settings.surfaceOpacity(kind) >= 1f || amount == 0) continue
            render(settings, prepared, preview, amount)?.let { result[kind] = it }
        }
        return result
    }

    /** 模糊半径按标准图片长边归一化，预览和最终图使用相同视觉强度。 */
    private suspend fun blur(source: Bitmap, amount: Int): Bitmap {
        if (amount == 0) return source
        try {
            return withContext(Dispatchers.Default) {
                val pixels = IntArray(source.width * source.height)
                source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
                val radius = (maxOf(source.width, source.height) * amount / 100f * 0.018f).roundToInt().coerceAtLeast(1)
                val blurred = BackgroundBlur.apply(pixels, source.width, source.height, radius)
                Bitmap.createBitmap(blurred, source.width, source.height, Bitmap.Config.ARGB_8888)
            }
        } finally { source.recycle() }
    }

    /** 按像素上限采样并正确处理八种 EXIF 方向，输出不超过目标长边。 */
    private fun decode(file: File, edge: Int, orient: Boolean = false): Bitmap {
        val info = bounds(file)
        require(info.outWidth > 0 && info.outHeight > 0) { "Invalid background image" }
        // 普通模式允许解码 2K 长屏图片，避免先被像素预算采样到目标分辨率以下。
        val maxPixels = if (mLowMemory) 4_194_304L else 8_388_608L
        var sample = 1
        while (maxOf(info.outWidth, info.outHeight) / sample > edge * 2 ||
            info.outWidth.toLong() * info.outHeight / sample / sample > maxPixels) sample *= 2
        var bitmap = requireNotNull(BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })) { "Background decode failed" }
        // 只在导入时应用 EXIF，后续缓存已经使用修正后的像素。
        if (orient) {
            val exif = ExifInterface(file)
            val matrix = Matrix().apply {
                if (exif.isFlipped) postScale(-1f, 1f)
                postRotate(exif.rotationDegrees.toFloat())
            }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            bitmap = rotated
        }
        val scale = edge.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (scale < 1f) {
            val resized = Bitmap.createScaledBitmap(bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1), true)
            if (resized !== bitmap) bitmap.recycle()
            bitmap = resized
        }
        return bitmap
    }

    /** 仅读取图像边界，不分配像素数组。 */
    private fun bounds(file: File): BitmapFactory.Options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.path, this)
    }

    /** 原子替换派生缓存，并按访问时间限制总磁盘占用。 */
    private fun store(target: File, bitmap: Bitmap) {
        val temporary = File(mDirectory, "${UUID.randomUUID()}.tmp")
        try {
            temporary.outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Background cache encoding failed" }
            }
            check(temporary.renameTo(target)) { "Background cache publish failed" }
            // 缓存可随时重建，清理绝不触及 FileRepository 的源图。
            val files = mDirectory.listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
            var total = 0L
            files.forEach { file ->
                total += file.length()
                if (total > 48L * 1024 * 1024) file.delete()
            }
        } finally { temporary.delete() }
    }
}
