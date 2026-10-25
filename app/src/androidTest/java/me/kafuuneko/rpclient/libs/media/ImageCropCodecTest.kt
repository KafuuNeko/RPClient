package me.kafuuneko.rpclient.libs.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.model.ImageCropSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/** 通过真实图片验证裁剪编解码的方向、格式与位图所有权，不创建文件仓库或数据库。 */
@RunWith(AndroidJUnit4::class)
class ImageCropCodecTest {
    /** 八种 EXIF 方向都应与手工排列的色块一致，防止移动解码入口后漏掉镜像或转置。 */
    @Test
    fun exifOrientationsDecodeToExpectedPixels() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val codec = ImageCropCodec(context)
        val file = File.createTempFile("crop-exif-", ".jpg", context.cacheDir)
        val source = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        try {
            // 大色块中心避开 JPEG 边缘误差，独立校验解码后的像素位置。
            for (y in 0 until source.height) {
                for (x in 0 until source.width) {
                    val value = (y / 20 * 4 + x / 20 + 1) * 24
                    source.setPixel(x, y, Color.rgb(value, value, value))
                }
            }
            file.outputStream().use { output ->
                check(source.compress(Bitmap.CompressFormat.JPEG, 100, output))
            }
            val expected = mapOf(
                ExifInterface.ORIENTATION_NORMAL to listOf(1, 2, 3, 4, 5, 6, 7, 8),
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL to listOf(4, 3, 2, 1, 8, 7, 6, 5),
                ExifInterface.ORIENTATION_ROTATE_180 to listOf(8, 7, 6, 5, 4, 3, 2, 1),
                ExifInterface.ORIENTATION_FLIP_VERTICAL to listOf(5, 6, 7, 8, 1, 2, 3, 4),
                ExifInterface.ORIENTATION_TRANSPOSE to listOf(1, 5, 2, 6, 3, 7, 4, 8),
                ExifInterface.ORIENTATION_ROTATE_90 to listOf(5, 1, 6, 2, 7, 3, 8, 4),
                ExifInterface.ORIENTATION_TRANSVERSE to listOf(8, 4, 7, 3, 6, 2, 5, 1),
                ExifInterface.ORIENTATION_ROTATE_270 to listOf(4, 8, 3, 7, 2, 6, 1, 5),
            )
            // 每次只更新元数据，确保图像方向来自解码校正而非重复旋转源像素。
            for ((orientation, pixels) in expected) {
                ExifInterface(file).apply {
                    setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                    saveAttributes()
                }
                val decoded = requireNotNull(codec.loadBitmapForCrop(Uri.fromFile(file)))
                try {
                    val transposed = orientation >= ExifInterface.ORIENTATION_TRANSPOSE
                    assertEquals(if (transposed) 40 else 80, decoded.width)
                    assertEquals(if (transposed) 80 else 40, decoded.height)
                    val columns = decoded.width / 20
                    for ((index, value) in pixels.withIndex()) {
                        val actual = Color.red(
                            decoded.getPixel(
                                index % columns * 20 + 10,
                                index / columns * 20 + 10
                            )
                        )
                        assertTrue(
                            "Unexpected pixels for EXIF $orientation",
                            abs(actual - value * 24) <= 2
                        )
                    }
                } finally {
                    decoded.recycle()
                }
            }
        } finally {
            source.recycle()
            file.delete()
        }
    }

    /** 透明图保留 PNG 与透明度，普通照片输出 JPEG，二者都使用选区且不释放预览源图。 */
    @Test
    fun avatarEncodingPreservesCropAndTransparency() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val codec = ImageCropCodec(context)
        for (hasAlpha in listOf(true, false)) {
            val source = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
            try {
                // 左半图作为方形选区，右半图的蓝色不得出现在最终头像中心。
                source.setHasAlpha(hasAlpha)
                source.eraseColor(Color.BLUE)
                val selectedColor = if (hasAlpha) Color.argb(128, 255, 0, 0) else Color.RED
                for (y in 0 until 40) {
                    for (x in 0 until 40) source.setPixel(x, y, selectedColor)
                }
                val encoded = codec.encodeAvatarCrop(
                    bitmap = source,
                    selection = ImageCropSelection(0.25f, 0.5f, 0.5f, 1f),
                )
                val options = BitmapFactory.Options()
                val decoded = requireNotNull(
                    BitmapFactory.decodeByteArray(
                        encoded.bytes,
                        0,
                        encoded.bytes.size,
                        options
                    )
                )
                try {
                    // MIME 必须与实际编码一致，输出尺寸继续遵循头像保存约定。
                    assertEquals(if (hasAlpha) "image/png" else "image/jpeg", encoded.mimeType)
                    assertEquals(encoded.mimeType, options.outMimeType)
                    assertEquals(1024, decoded.width)
                    assertEquals(1024, decoded.height)
                    val pixel = decoded.getPixel(512, 512)
                    assertEquals(if (hasAlpha) 128 else 255, Color.alpha(pixel))
                    assertTrue(Color.red(pixel) >= 252)
                    assertTrue(Color.blue(pixel) <= 3)
                    assertFalse(source.isRecycled)
                    assertEquals(Color.BLUE, source.getPixel(60, 20))
                } finally {
                    decoded.recycle()
                }
            } finally {
                source.recycle()
            }
        }
    }
}
