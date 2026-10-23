package me.kafuuneko.rpclient.libs.appearance

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 静态壁纸的可取消 box blur；预乘 alpha 后处理，避免透明边缘出现暗色光晕。 */
object BackgroundBlur {
    /**
     * 返回模糊后的独立像素数组，不修改调用方源数据。
     * @param radius 在当前处理分辨率下的卷积半径；零值保留原像素。
     */
    suspend fun apply(
        pixels: IntArray,
        width: Int,
        height: Int,
        radius: Int,
    ): IntArray {
        require(width > 0 && height > 0 && width.toLong() * height == pixels.size.toLong())
        if (radius <= 0) return pixels.copyOf()
        val context = currentCoroutineContext()
        // 在预乘空间计算，透明像素隐藏的 RGB 不得渗入可见边缘。
        val source = IntArray(pixels.size) { index ->
            val color = pixels[index]
            val alpha = color ushr 24
            (alpha shl 24) or (((color ushr 16 and 255) * alpha / 255) shl 16) or
                (((color ushr 8 and 255) * alpha / 255) shl 8) or ((color and 255) * alpha / 255)
        }
        val target = IntArray(pixels.size)
        repeat(times = 3) {
            pass(source, target, width, height, radius.coerceAtMost(128)) { context.ensureActive() }
            pass(target, source, height, width, radius.coerceAtMost(128)) { context.ensureActive() }
        }
        // 解预乘后再交给 Bitmap；完全透明像素统一归零。
        for (index in source.indices) {
            if (index % 4096 == 0) context.ensureActive()
            val color = source[index]
            val alpha = color ushr 24
            source[index] = if (alpha == 0) 0 else (alpha shl 24) or
                (((color ushr 16 and 255) * 255 / alpha).coerceAtMost(255) shl 16) or
                (((color ushr 8 and 255) * 255 / alpha).coerceAtMost(255) shl 8) or
                ((color and 255) * 255 / alpha).coerceAtMost(255)
        }
        return source
    }

    /** 滑动窗口使单次卷积保持线性复杂度；转置写入以复用水平算法处理纵向。 */
    private inline fun pass(
        source: IntArray,
        target: IntArray,
        width: Int,
        height: Int,
        radius: Int,
        checkCancelled: () -> Unit,
    ) {
        val count = radius * 2 + 1
        for (y in 0 until height) {
            checkCancelled()
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            // 超出图片的采样钳制到最近边缘，不引入透明或黑色边框。
            for (offset in -radius..radius) {
                val color =
                    source[y * width + offset.coerceIn(minimumValue = 0, maximumValue = width - 1)]
                a += color ushr 24
                r += color ushr 16 and 255
                g += color ushr 8 and 255
                b += color and 255
            }
            for (x in 0 until width) {
                target[x * height + y] = ((a / count) shl 24) or ((r / count) shl 16) or
                    ((g / count) shl 8) or (b / count)
                val before = source[y * width + (x - radius).coerceIn(
                    minimumValue = 0,
                    maximumValue = width - 1
                )]
                val after = source[y * width + (x + radius + 1).coerceIn(
                    minimumValue = 0,
                    maximumValue = width - 1
                )]
                a += (after ushr 24) - (before ushr 24)
                r += (after ushr 16 and 255) - (before ushr 16 and 255)
                g += (after ushr 8 and 255) - (before ushr 8 and 255)
                b += (after and 255) - (before and 255)
            }
        }
    }
}
