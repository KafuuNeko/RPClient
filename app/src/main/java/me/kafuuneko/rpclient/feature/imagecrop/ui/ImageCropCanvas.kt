package me.kafuuneko.rpclient.feature.imagecrop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.feature.imagecrop.presentation.CropMaskShape
import me.kafuuneko.rpclient.feature.imagecrop.presentation.ImageCropUiIntent
import me.kafuuneko.rpclient.feature.imagecrop.presentation.ImageCropUiState
import kotlin.math.roundToInt

/** 按可用视口适配矩形裁剪框，手势和绘制共用同一套宽高归一化坐标。 */
@Composable
internal fun ImageCropCanvas(
    state: ImageCropUiState.Normal,
    emit: ImageCropUiIntent.() -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val density = LocalDensity.current
        // 不设置最小框尺寸，横屏或小窗口下也必须完整放进可用画布。
        val availableWidth =
            (constraints.maxWidth.toFloat() - with(density) { 32.dp.toPx() }).coerceAtLeast(1f)
        val availableHeight =
            (constraints.maxHeight.toFloat() - with(density) { 24.dp.toPx() }).coerceAtLeast(1f)
        val ratio = state.transform.cropAspectRatio
        val width = minOf(availableWidth, availableHeight * ratio, with(density) { 420.dp.toPx() })
        val height = width / ratio
        val left = (constraints.maxWidth - width) / 2f
        val top = (constraints.maxHeight - height) / 2f
        val cropRect = Rect(left, top, left + width, top + height)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .cropGestures(state, cropRect, emit),
        ) {
            drawCropImage(state, cropRect)
            drawCropOverlay(cropRect, state.maskShape)
        }
    }
}

/** 图片手势以裁剪框各自的宽高为单位，双击缩放保持原有交互。 */
private fun Modifier.cropGestures(
    state: ImageCropUiState.Normal,
    cropRect: Rect,
    emit: ImageCropUiIntent.() -> Unit,
): Modifier = pointerInput(state.saving, cropRect) {
    if (state.saving) return@pointerInput
    detectTransformGestures { _, pan, zoom, _ ->
        ImageCropUiIntent.Transform(
            panX = pan.x / cropRect.width,
            panY = pan.y / cropRect.height,
            zoomChange = zoom,
        ).emit()
    }
}.pointerInput(state.saving, state.transform) {
    if (state.saving) return@pointerInput
    // 缩回初始构图时使用零位移，避免缩放后的旧偏移再次被过量抵消。
    detectTapGestures(onDoubleTap = {
        val zoomChange = if (state.transform.zoom > 1.05f) 1f / state.transform.zoom else 2f
        ImageCropUiIntent.Transform(panX = 0f, panY = 0f, zoomChange = zoomChange).emit()
    })
}

/** 将与最终裁剪一致的旋转和镜像应用到画布，避免预览与输出方向不同。 */
private fun DrawScope.drawCropImage(state: ImageCropUiState.Normal, cropRect: Rect) {
    val transform = state.transform
    val width = transform.baseWidth * cropRect.width * transform.zoom
    val height = transform.baseHeight * cropRect.height * transform.zoom
    val centerX = cropRect.center.x + transform.offsetX * cropRect.width
    val centerY = cropRect.center.y + transform.offsetY * cropRect.height
    // 先镜像再旋转；画布变换与 BitmapCrop 中的像素矩阵采用相同顺序。
    withTransform(transformBlock = {
        translate(left = centerX, top = centerY)
        rotate(degrees = transform.rotationDegrees.toFloat(), pivot = Offset.Zero)
        scale(
            scaleX = if (transform.isFlippedHorizontal) -1f else 1f,
            scaleY = 1f,
            pivot = Offset.Zero,
        )
    }) {
        val imageWidth = if (transform.isRotated90) height else width
        val imageHeight = if (transform.isRotated90) width else height
        drawImage(
            image = state.image,
            dstOffset = IntOffset(
                (-imageWidth / 2f).roundToInt(),
                (-imageHeight / 2f).roundToInt()
            ),
            dstSize = IntSize(
                imageWidth.roundToInt().coerceAtLeast(1),
                imageHeight.roundToInt().coerceAtLeast(1)
            ),
            filterQuality = FilterQuality.High,
        )
    }
}

/** 背景使用矩形蒙版，头像保留圆形或圆角预览与三分法辅助线。 */
private fun DrawScope.drawCropOverlay(rect: Rect, shape: CropMaskShape) {
    val radius = if (shape == CropMaskShape.Rectangle) 0f else 24.dp.toPx()
    val mask = Path().apply {
        addRect(Rect(0f, 0f, size.width, size.height))
        if (shape == CropMaskShape.Circle) addOval(rect)
        else addRoundRect(RoundRect(rect, CornerRadius(radius)))
        fillType = PathFillType.EvenOdd
    }
    drawPath(mask, Color(0xB8000000))
    // 辅助线按宽高分别分割，矩形构图也保持正确的三分法位置。
    for (fraction in listOf(1f / 3f, 2f / 3f)) {
        drawLine(
            color = Color(0x30FFFFFF),
            start = Offset(rect.left + rect.width * fraction, rect.top),
            end = Offset(rect.left + rect.width * fraction, rect.bottom),
            strokeWidth = 1.dp.toPx(),
        )
        drawLine(
            color = Color(0x30FFFFFF),
            start = Offset(rect.left, rect.top + rect.height * fraction),
            end = Offset(rect.right, rect.top + rect.height * fraction),
            strokeWidth = 1.dp.toPx(),
        )
    }
    if (shape == CropMaskShape.Circle) {
        drawOval(Color(0x80FFFFFF), rect.topLeft, rect.size, style = Stroke(1.5.dp.toPx()))
    } else {
        drawRoundRect(
            Color(0x60FFFFFF),
            rect.topLeft,
            rect.size,
            CornerRadius(radius),
            style = Stroke(1.5.dp.toPx())
        )
        drawCropCorners(rect)
    }
}

/** 保留四角高光，矩形模式与头像使用同一轮廓反馈。 */
private fun DrawScope.drawCropCorners(rect: Rect) {
    val length = minOf(18.dp.toPx(), minOf(rect.width, rect.height) / 4f)
    // 角标方向由角点相对中心的位置决定，避免横竖框分别维护坐标。
    for (corner in listOf(rect.topLeft, rect.topRight, rect.bottomLeft, rect.bottomRight)) {
        val xDirection = if (corner.x < rect.center.x) 1f else -1f
        val yDirection = if (corner.y < rect.center.y) 1f else -1f
        drawLine(Color.White, corner, corner + Offset(length * xDirection, 0f), 3.dp.toPx())
        drawLine(Color.White, corner, corner + Offset(0f, length * yDirection), 3.dp.toPx())
    }
}
