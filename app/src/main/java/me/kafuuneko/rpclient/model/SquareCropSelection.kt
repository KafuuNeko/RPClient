package me.kafuuneko.rpclient.model

/**
 * 原图坐标系中的正方形裁剪选区。
 *
 * 中心点按变换后原图宽高归一化，边长按原图短边归一化，使选区与具体解码尺寸无关。
 */
data class SquareCropSelection(
    /** 裁剪区域中心相对原图宽度的归一化横坐标。 */
    val centerX: Float,
    /** 裁剪区域中心相对原图高度的归一化纵坐标。 */
    val centerY: Float,
    /** 裁剪正方形边长相对原图短边的比例。 */
    val sizeFractionOfShortEdge: Float,
    /** 图像当前顺时针旋转的角度。 */
    val rotationDegrees: Int = 0,
    /** 图像是否相对原始方向水平翻转。 */
    val isFlippedHorizontal: Boolean = false
)
