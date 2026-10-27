package me.kafuuneko.rpclient.libs.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.CancellationSignal
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.utils.cropBitmap
import me.kafuuneko.rpclient.libs.utils.withBlockingIoCancellation
import me.kafuuneko.rpclient.model.ImageCropSelection
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * 裁剪图片的编解码工具，只依赖系统资源读取能力。
 * - 采样解码并校正 EXIF，取消或失败时释放尚未交付的位图。
 * - 将头像选区编码为图片字节，文件持久化由调用方交给文件仓库处理。
 */
class ImageCropCodec(private val mContext: Context) {
    /** 编码后的图片及其实际 MIME 类型，供文件保存接口直接使用。 */
    data class EncodedImage(val bytes: ByteArray, val mimeType: String)

    /**
     * 解码供交互裁剪使用的图片，并在进入 UI 前应用 EXIF 方向。
     * - 取消时中止资源打开并关闭读取流，不等待云端图片自然返回。
     * @param uri 系统选择器授予读取权限的源图片。
     * @param maxDimensionPx 长边采样的目标尺寸。
     * @return 由调用方释放的位图；无效尺寸或无法解码时返回 null。
     * @throws CancellationException 调用方取消加载时抛出，不转换为图片读取失败。
     */
    suspend fun loadBitmapForCrop(
        uri: Uri,
        maxDimensionPx: Int = CROP_SOURCE_DIMENSION,
    ): Bitmap? {
        var completed: Bitmap? = null
        try {
            // 保存后台结果，覆盖切回主线程时发生取消、位图尚未交给 ViewModel 的窗口。
            return withContext(Dispatchers.IO) {
                decodeBitmapForCrop(uri, maxDimensionPx).also { completed = it }
            }
        } catch (error: Throwable) {
            completed?.recycle()
            throw error
        }
    }

    /**
     * 将头像选区裁剪为固定尺寸的正方形并编码，源位图仍由调用方持有。
     * @param bitmap 已修正 EXIF 方向的源图。
     * @param selection 旋转和翻转后的归一化选区。
     * @param outputSizePx 输出正方形的边长。
     * @return 透明图片的 PNG 字节或普通照片的 JPEG 字节，以及对应 MIME 类型。
     */
    suspend fun encodeAvatarCrop(
        bitmap: Bitmap,
        selection: ImageCropSelection,
        outputSizePx: Int = AVATAR_OUTPUT_DIMENSION,
    ): EncodedImage = withContext(Dispatchers.IO) {
        require(outputSizePx in 1..MAX_IMAGE_DIMENSION)
        // 直接生成目标尺寸，旋转和裁剪共用背景的像素变换规则。
        val output = cropBitmap(bitmap, selection, outputSizePx)
        val hasAlpha = bitmap.hasAlpha()
        val format = if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val mimeType = if (hasAlpha) "image/png" else "image/jpeg"
        try {
            ByteArrayOutputStream().use { bytes ->
                check(output.compress(format, AVATAR_JPEG_QUALITY, bytes))
                EncodedImage(bytes.toByteArray(), mimeType)
            }
        } finally {
            // 只释放本次裁剪生成的位图，预览源图在页面结束时统一回收。
            output.recycle()
        }
    }

    /**
     * 在后台采样解码并修正 EXIF，失败时释放尚未交付的位图。
     * @param uri 已获得读取权限的源图片。
     * @param maxDimensionPx 长边采样的目标尺寸。
     * @return 校正方向后的独立位图；源图片不可解码时返回 null。
     */
    private suspend fun decodeBitmapForCrop(uri: Uri, maxDimensionPx: Int): Bitmap? {
        if (maxDimensionPx !in 1..MAX_IMAGE_DIMENSION) return null
        // 先读取边界，避免完整解码相册中的大图。
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        withSourceInput(uri) { input -> BitmapFactory.decodeStream(input, null, bounds) }
        val sourceWidth = bounds.outWidth.takeIf { it > 0 } ?: return null
        val sourceHeight = bounds.outHeight.takeIf { it > 0 } ?: return null
        // 先读取 EXIF 再分配像素，读取失败不会遗留已解码的大图。
        val orientation = readExifOrientation(uri)
        val sampleSize = calculateLongEdgeSampleSize(sourceWidth, sourceHeight, maxDimensionPx)
        var decoded: Bitmap? = null
        try {
            withSourceInput(uri) { input ->
                // 在取消作用域返回前保留像素所有权，避免取消恰好发生在解码完成后时泄漏。
                decoded = BitmapFactory.decodeStream(
                    input,
                    null,
                    BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    },
                )
            }
            return decoded?.let { bitmap -> applyExifOrientation(bitmap, orientation) }
        } catch (error: Throwable) {
            decoded?.recycle()
            throw error
        }
    }

    /**
     * 以可取消方式打开并消费源图，边界、EXIF 和像素解码共用同一资源生命周期。
     * @param uri 系统选择器或本地文件提供的图片位置。
     * @param read 在当前 IO 线程消费输入流的操作，不得在返回后持有流。
     * @return 读取结果；提供者未返回描述符时返回 null。
     */
    private suspend fun <T> withSourceInput(uri: Uri, read: (InputStream) -> T): T? {
        currentCoroutineContext().ensureActive()
        val signal = CancellationSignal()
        // 打开云端资源与读取字节分别绑定取消动作，取消不能依赖阻塞线程自行恢复。
        return withBlockingIoCancellation(cleanup = { signal.cancel() }) {
            val descriptor = mContext.contentResolver.openAssetFileDescriptor(uri, "r", signal)
                ?: return@withBlockingIoCancellation null
            descriptor.use { asset ->
                currentCoroutineContext().ensureActive()
                asset.createInputStream().use { input ->
                    withBlockingIoCancellation(cleanup = { input.close() }) {
                        currentCoroutineContext().ensureActive()
                        read(input)
                    }
                }
            }
        }
    }

    /** 按长边计算二次幂采样率，保持预览所需的像素精度。 */
    private fun calculateLongEdgeSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        val longEdge = maxOf(width, height)
        while (longEdge / (sampleSize * 2) >= maxDimension) sampleSize *= 2
        return sampleSize
    }

    /**
     * 不带 EXIF 或不支持元数据的图片按正常方向处理，但取消必须立即向上传递。
     * @param uri 已获得读取权限的源图片。
     * @return EXIF 方向；无法读取元数据时返回正常方向。
     */
    private suspend fun readExifOrientation(uri: Uri): Int = try {
        withSourceInput(uri) { input ->
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        currentCoroutineContext().ensureActive()
        ExifInterface.ORIENTATION_NORMAL
    }

    /** 校正八种 EXIF 方向，生成新图后释放替换掉的解码位图。 */
    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        // 镜像与旋转必须按 EXIF 约定组合，避免转置方向与裁剪预览不一致。
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (oriented !== bitmap) bitmap.recycle()
        return oriented
    }

    private companion object {
        const val MAX_IMAGE_DIMENSION = 4_096
        const val CROP_SOURCE_DIMENSION = 2_048
        const val AVATAR_OUTPUT_DIMENSION = 1_024
        const val AVATAR_JPEG_QUALITY = 92
    }
}
