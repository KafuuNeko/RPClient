package me.kafuuneko.rpclient.libs.appearance

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证自有像素算法的透明边缘、零强度和协程取消，不测试框架映射。 */
class BackgroundBlurTest {
    /** 清晰图必须完全保留半透明与隐藏 RGB，避免零强度也改变图片。 */
    @Test
    fun zeroRadiusPreservesExactPixels() = runBlocking {
        val pixels = intArrayOf(0x00112233, 0x80446688.toInt(), 0xff123456.toInt())
        assertArrayEquals(
            pixels,
            BackgroundBlur.apply(pixels = pixels, width = 3, height = 1, radius = 0)
        )
    }

    /** 边缘扩展必须保持常量图像，单像素宽高同样可处理。 */
    @Test
    fun edgeClampingPreservesOpaqueUniformImage() = runBlocking {
        for ((width, height) in listOf(1 to 1, 1 to 17, 19 to 1, 13 to 7)) {
            val pixels = IntArray(width * height) { 0xff386294.toInt() }
            assertArrayEquals(
                pixels,
                BackgroundBlur.apply(pixels = pixels, width = width, height = height, radius = 20),
            )
        }
    }

    /** 完全透明红色不能污染白色的可见边缘。 */
    @Test
    fun transparentHiddenColorDoesNotBleed() = runBlocking {
        val result = BackgroundBlur.apply(
            pixels = intArrayOf(0x00ff0000, 0xffffffff.toInt(), 0x00ff0000),
            width = 3,
            height = 1,
            radius = 1,
        )
        result.forEach { pixel ->
            assertTrue(pixel ushr 24 in 1..254)
            assertEquals(255, pixel ushr 16 and 255)
            assertEquals(255, pixel ushr 8 and 255)
            assertEquals(255, pixel and 255)
        }
    }

    /** 过期预览的取消必须传播给调用方，不能继续发布图像结果。 */
    @Test(expected = CancellationException::class)
    fun cancelledRequestDoesNotProduceResult(): Unit = runBlocking {
        withContext(Job().apply { cancel() }) {
            BackgroundBlur.apply(pixels = IntArray(4096), width = 64, height = 64, radius = 10)
        }
        Unit
    }
}
