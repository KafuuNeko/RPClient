package me.kafuuneko.rpclient.feature.imagecrop.model

import me.kafuuneko.rpclient.model.SquareCropSelection

/** 正方形裁剪框中的缩放、平移、旋转与翻转状态，偏移量以裁剪框边长为单位。 */
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
    val isFlippedHorizontal: Boolean = false
) {
    /** 是否处于 90 度或 270 度垂直旋转状态。 */
    val isRotated90: Boolean
        get() = rotationDegrees % 180 != 0

    /** 考虑旋转后的有效宽高比。 */
    val effectiveAspectRatio: Float
        get() = if (isRotated90) 1f / sourceAspectRatio else sourceAspectRatio

    /** 是否处于未进行任何平移、缩放、旋转或翻转的初始默认状态。 */
    val isDefault: Boolean
        get() = zoom == MIN_ZOOM &&
            offsetX == 0f &&
            offsetY == 0f &&
            rotationDegrees == 0 &&
            !isFlippedHorizontal

    /**
     * 根据平移量与缩放比例增量计算新的裁剪变换状态。
     *
     * @param panX X 轴平移增量。
     * @param panY Y 轴平移增量。
     * @param zoomChange 缩放比例乘数。
     */
    fun update(panX: Float, panY: Float, zoomChange: Float): ImageCropTransform {
        val newZoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val appliedZoomChange = newZoom / zoom
        val baseWidth = maxOf(effectiveAspectRatio, 1f)
        val baseHeight = maxOf(1f / effectiveAspectRatio, 1f)
        val maxOffsetX = ((baseWidth * newZoom - 1f) / 2f).coerceAtLeast(0f)
        val maxOffsetY = ((baseHeight * newZoom - 1f) / 2f).coerceAtLeast(0f)
        return copy(
            zoom = newZoom,
            offsetX = (offsetX * appliedZoomChange + panX).coerceIn(-maxOffsetX, maxOffsetX),
            offsetY = (offsetY * appliedZoomChange + panY).coerceIn(-maxOffsetY, maxOffsetY)
        )
    }

    /** 顺时针旋转 90 度并重新限制平移边界。 */
    fun rotateRight(): ImageCropTransform {
        val nextRotation = (rotationDegrees + 90) % 360
        val isNextRotated90 = nextRotation % 180 != 0
        val nextEffectiveAspect = if (isNextRotated90) 1f / sourceAspectRatio else sourceAspectRatio
        val nextBaseW = maxOf(nextEffectiveAspect, 1f)
        val nextBaseH = maxOf(1f / nextEffectiveAspect, 1f)
        val maxOffsetX = ((nextBaseW * zoom - 1f) / 2f).coerceAtLeast(0f)
        val maxOffsetY = ((nextBaseH * zoom - 1f) / 2f).coerceAtLeast(0f)
        return copy(
            rotationDegrees = nextRotation,
            offsetX = offsetX.coerceIn(-maxOffsetX, maxOffsetX),
            offsetY = offsetY.coerceIn(-maxOffsetY, maxOffsetY)
        )
    }

    /** 水平镜像翻转并反转 X 轴偏移量。 */
    fun flipHorizontal(): ImageCropTransform {
        val baseWidth = maxOf(effectiveAspectRatio, 1f)
        val maxOffsetX = ((baseWidth * zoom - 1f) / 2f).coerceAtLeast(0f)
        return copy(
            isFlippedHorizontal = !isFlippedHorizontal,
            offsetX = (-offsetX).coerceIn(-maxOffsetX, maxOffsetX)
        )
    }

    /** 重置为初始裁剪变换状态。 */
    fun reset(): ImageCropTransform = copy(
        zoom = MIN_ZOOM,
        offsetX = 0f,
        offsetY = 0f,
        rotationDegrees = 0,
        isFlippedHorizontal = false
    )

    /** 将当前变换状态转换为与分辨率无关的 [SquareCropSelection] 归一化选区。 */
    fun toSelection(): SquareCropSelection {
        val baseWidth = maxOf(effectiveAspectRatio, 1f)
        val baseHeight = maxOf(1f / effectiveAspectRatio, 1f)
        val displayedWidth = baseWidth * zoom
        val displayedHeight = baseHeight * zoom
        return SquareCropSelection(
            centerX = 0.5f - offsetX / displayedWidth,
            centerY = 0.5f - offsetY / displayedHeight,
            sizeFractionOfShortEdge = 1f / zoom,
            rotationDegrees = rotationDegrees,
            isFlippedHorizontal = isFlippedHorizontal
        )
    }

    companion object {
        /** 最小缩放倍数（填满裁剪框）。 */
        const val MIN_ZOOM = 1f
        /** 最大缩放倍数。 */
        const val MAX_ZOOM = 8f
    }
}

