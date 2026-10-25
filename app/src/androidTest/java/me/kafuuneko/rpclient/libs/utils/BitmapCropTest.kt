package me.kafuuneko.rpclient.libs.utils

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.kafuuneko.rpclient.model.ImageCropSelection
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** 使用可区分的像素验证旋转、镜像与矩形选区，保护裁剪预览和最终输出的一致性。 */
@RunWith(AndroidJUnit4::class)
class BitmapCropTest {
    /** 八种方向都应得到手工排列的像素顺序，且不修改调用方源图。 */
    @Test
    fun everyRotationAndMirrorKeepsExpectedPixelOrder() {
        val expected = mapOf(
            (0 to false) to listOf(1, 2, 3, 4, 5, 6, 7, 8),
            (90 to false) to listOf(5, 1, 6, 2, 7, 3, 8, 4),
            (180 to false) to listOf(8, 7, 6, 5, 4, 3, 2, 1),
            (270 to false) to listOf(4, 8, 3, 7, 2, 6, 1, 5),
            (0 to true) to listOf(4, 3, 2, 1, 8, 7, 6, 5),
            (90 to true) to listOf(8, 4, 7, 3, 6, 2, 5, 1),
            (180 to true) to listOf(5, 6, 7, 8, 1, 2, 3, 4),
            (270 to true) to listOf(1, 5, 2, 6, 3, 7, 4, 8),
        )
        val source =
            Bitmap.createBitmap((1..8).map(::color).toIntArray(), 4, 2, Bitmap.Config.ARGB_8888)
        try {
            // 每种方向独立从原图生成，不能累计上一次变换的误差。
            for ((orientation, pixels) in expected) {
                val selection = ImageCropSelection(
                    centerX = 0.5f,
                    centerY = 0.5f,
                    widthFraction = 1f,
                    heightFraction = 1f,
                    rotationDegrees = orientation.first,
                    isFlippedHorizontal = orientation.second,
                )
                val output = cropBitmap(source, selection)
                try {
                    val rotated90 = orientation.first % 180 != 0
                    assertEquals(if (rotated90) 2 else 4, output.width)
                    assertEquals(if (rotated90) 4 else 2, output.height)
                    assertArrayEquals(pixels.map(::color).toIntArray(), output.pixels())
                } finally {
                    output.recycle()
                }
            }
            assertArrayEquals((1..8).map(::color).toIntArray(), source.pixels())
        } finally {
            source.recycle()
        }
    }

    /** 宽高分别归一化，右侧矩形选区不能被当成以短边为单位的方形。 */
    @Test
    fun rectangularCropUsesIndependentWidthAndHeightFractions() {
        val source =
            Bitmap.createBitmap((1..24).map(::color).toIntArray(), 6, 4, Bitmap.Config.ARGB_8888)
        val selection = ImageCropSelection(0.75f, 0.5f, 0.5f, 0.5f)
        val output = cropBitmap(source, selection)
        try {
            assertEquals(3, output.width)
            assertEquals(2, output.height)
            assertArrayEquals(
                listOf(10, 11, 12, 16, 17, 18).map(::color).toIntArray(),
                output.pixels()
            )
        } finally {
            output.recycle()
            source.recycle()
        }
    }

    private fun color(index: Int): Int = Color.rgb(index * 9, index * 7, index * 5)

    private fun Bitmap.pixels(): IntArray = IntArray(width * height).also { pixels ->
        getPixels(pixels, 0, width, 0, 0, width, height)
    }
}
