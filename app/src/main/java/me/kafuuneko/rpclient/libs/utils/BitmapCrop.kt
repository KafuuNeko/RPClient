package me.kafuuneko.rpclient.libs.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import me.kafuuneko.rpclient.model.ImageCropSelection
import kotlin.math.roundToInt
import androidx.core.graphics.createBitmap

/**
 * 按归一化选区直接绘制目标位图，避免额外分配整张旋转图片。
 * @param source 已修正 EXIF 方向的源图，调用方仍持有其生命周期。
 * @param selection 以旋转和翻转后的图片为坐标系的选区。
 * @param outputSizePx 固定方形输出边长；为空时保留选区的实际像素尺寸。
 * @return 由调用方释放的独立裁剪位图。
 */
internal fun cropBitmap(
    source: Bitmap,
    selection: ImageCropSelection,
    outputSizePx: Int? = null,
): Bitmap {
    // 先求变换后的图像边界，将旋转产生的负坐标移回图像左上角。
    val matrix = Matrix().apply {
        if (selection.isFlippedHorizontal) postScale(-1f, 1f)
        postRotate(selection.rotationDegrees.toFloat())
    }
    val bounds = RectF(0f, 0f, source.width.toFloat(), source.height.toFloat())
    matrix.mapRect(bounds)
    val width = bounds.width().roundToInt()
    val height = bounds.height().roundToInt()
    val cropWidth = (width * selection.widthFraction).roundToInt().coerceIn(1, width)
    val cropHeight = (height * selection.heightFraction).roundToInt().coerceIn(1, height)
    val left =
        (selection.centerX * width - cropWidth / 2f).coerceIn(0f, (width - cropWidth).toFloat())
    val top =
        (selection.centerY * height - cropHeight / 2f).coerceIn(0f, (height - cropHeight).toFloat())
    matrix.postTranslate(-bounds.left - left, -bounds.top - top)

    // 头像可直接缩放到固定尺寸，背景保留矩形选区，不改变调用方的源图。
    val outputWidth = outputSizePx ?: cropWidth
    val outputHeight = outputSizePx ?: cropHeight
    matrix.postScale(outputWidth.toFloat() / cropWidth, outputHeight.toFloat() / cropHeight)
    val output = createBitmap(outputWidth, outputHeight)
    try {
        output.setHasAlpha(source.hasAlpha())
        Canvas(output).drawBitmap(
            source,
            matrix,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        return output
    } catch (error: Throwable) {
        output.recycle()
        throw error
    }
}
