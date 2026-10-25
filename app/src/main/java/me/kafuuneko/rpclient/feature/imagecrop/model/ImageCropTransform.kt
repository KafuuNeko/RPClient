package me.kafuuneko.rpclient.feature.imagecrop.model

import me.kafuuneko.rpclient.model.ImageCropSelection

/**
 * 矩形裁剪框中的缩放、平移、旋转与翻转状态，偏移量分别以框的宽高为单位。
 *
 * 由已解码位图的宽高比创建；缩放和平移边界由 [update] 与 [rotateRight] 维持。
 */
data class ImageCropTransform(
    /** 原始图像的宽高比。 */
    val sourceAspectRatio: Float,
    /** 当前图像相对适配尺寸的缩放倍数。 */
    val zoom: Float = MIN_ZOOM,
    /** 当前图像中心相对裁剪区域的水平偏移。 */
    val offsetX: Float = 0f,
    /** 当前图像中心相对裁剪区域的垂直偏移。 */
    val offsetY: Float = 0f,
    /** 图像当前顺时针旋转的角度。 */
    val rotationDegrees: Int = 0,
    /** 图像是否相对原始方向水平翻转。 */
    val isFlippedHorizontal: Boolean = false,
    /** 裁剪框宽高比，头像固定为正方形。 */
    val cropAspectRatio: Float = 1f,
) {
    init {
        require(sourceAspectRatio.isFinite() && sourceAspectRatio > 0f) { "Invalid image aspect ratio" }
        require(cropAspectRatio.isFinite() && cropAspectRatio > 0f) { "Invalid crop aspect ratio" }
    }

    /** 是否处于 90 度或 270 度垂直旋转状态。 */
    val isRotated90: Boolean
        get() = rotationDegrees % 180 != 0

    /** 考虑旋转后的有效宽高比。 */
    val effectiveAspectRatio: Float
        get() = if (isRotated90) 1f / sourceAspectRatio else sourceAspectRatio

    /** 填满裁剪框时的图片宽度，以裁剪框宽度为单位。 */
    val baseWidth: Float
        get() = maxOf(effectiveAspectRatio / cropAspectRatio, 1f)

    /** 填满裁剪框时的图片高度，以裁剪框高度为单位。 */
    val baseHeight: Float
        get() = maxOf(cropAspectRatio / effectiveAspectRatio, 1f)

    /** 是否处于未进行任何平移、缩放、旋转或翻转的初始默认状态。 */
    val isDefault: Boolean
        get() = zoom == MIN_ZOOM
            && offsetX == 0f
            && offsetY == 0f
            && rotationDegrees == 0
            && !isFlippedHorizontal

    /**
     * 根据平移量与缩放比例增量计算新的裁剪变换状态。
     *
     * @param panX X 轴平移增量。
     * @param panY Y 轴平移增量。
     * @param zoomChange 缩放比例乘数。
     */
    fun update(panX: Float, panY: Float, zoomChange: Float): ImageCropTransform {
        // 缩放与位移必须同时限制，保证目标比例下裁剪框始终被图片完全覆盖。
        if (!panX.isFinite() || !panY.isFinite() || !zoomChange.isFinite() || zoomChange <= 0f) return this
        val newZoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val appliedZoomChange = newZoom / zoom
        val maxOffsetX = (baseWidth * newZoom - 1f) / 2f
        val maxOffsetY = (baseHeight * newZoom - 1f) / 2f
        return copy(
            zoom = newZoom,
            offsetX = (offsetX * appliedZoomChange + panX).coerceIn(-maxOffsetX, maxOffsetX),
            offsetY = (offsetY * appliedZoomChange + panY).coerceIn(-maxOffsetY, maxOffsetY),
        )
    }

    /** 顺时针旋转 90 度并重新限制平移边界。 */
    fun rotateRight(): ImageCropTransform {
        return copy(rotationDegrees = (rotationDegrees + 90) % 360)
            .update(panX = 0f, panY = 0f, zoomChange = 1f)
    }

    /** 更换构图比例时保留旋转与镜像，并恢复填满裁剪框的初始选区。 */
    fun withAspectRatio(aspectRatio: Float): ImageCropTransform = copy(
        cropAspectRatio = aspectRatio,
        zoom = MIN_ZOOM,
        offsetX = 0f,
        offsetY = 0f,
    )

    /** 沿原图水平方向镜像，旋转后同步反转对应的选区偏移。 */
    fun flipHorizontal(): ImageCropTransform {
        return copy(
            isFlippedHorizontal = !isFlippedHorizontal,
            offsetX = if (isRotated90) offsetX else -offsetX,
            offsetY = if (isRotated90) -offsetY else offsetY,
        )
    }

    /** 重置为初始裁剪变换状态。 */
    fun reset(): ImageCropTransform = copy(
        zoom = MIN_ZOOM,
        offsetX = 0f,
        offsetY = 0f,
        rotationDegrees = 0,
        isFlippedHorizontal = false,
    )

    /** 将当前变换转换为预览和最终图片共用的归一化矩形选区。 */
    fun toSelection(): ImageCropSelection {
        val displayedWidth = baseWidth * zoom
        val displayedHeight = baseHeight * zoom
        return ImageCropSelection(
            centerX = 0.5f - offsetX / displayedWidth,
            centerY = 0.5f - offsetY / displayedHeight,
            widthFraction = (1f / displayedWidth).coerceAtMost(1f),
            heightFraction = (1f / displayedHeight).coerceAtMost(1f),
            rotationDegrees = rotationDegrees,
            isFlippedHorizontal = isFlippedHorizontal,
        )
    }

    companion object {
        /** 最小缩放倍数（填满裁剪框）。 */
        const val MIN_ZOOM = 1f

        /** 最大缩放倍数。 */
        const val MAX_ZOOM = 8f
    }
}
