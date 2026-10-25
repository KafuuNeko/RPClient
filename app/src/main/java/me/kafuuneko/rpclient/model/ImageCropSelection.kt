package me.kafuuneko.rpclient.model

/**
 * 与解码分辨率无关的矩形裁剪选区。
 * - 中心与宽高分别按旋转、翻转后的图片宽高归一化。
 * - 预览和最终源图共用同一选区，背景无需先生成临时裁剪文件再重复编码。
 */
data class ImageCropSelection(
    val centerX: Float,
    val centerY: Float,
    val widthFraction: Float,
    val heightFraction: Float,
    val rotationDegrees: Int = 0,
    val isFlippedHorizontal: Boolean = false,
) {
    init {
        require(centerX.isFinite() && centerY.isFinite()) { "Invalid crop center" }
        require(widthFraction > 0f && widthFraction <= 1f) { "Invalid crop width" }
        require(heightFraction > 0f && heightFraction <= 1f) { "Invalid crop height" }
        require(rotationDegrees in setOf(0, 90, 180, 270)) { "Invalid crop rotation" }
    }
}
