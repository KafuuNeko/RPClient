package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import me.kafuuneko.rpclient.feature.appearance.presentation.PageBackgroundUiState
import me.kafuuneko.rpclient.libs.appearance.PageSurfaceKind
import me.kafuuneko.rpclient.libs.appearance.surfaceBlur
import me.kafuuneko.rpclient.libs.appearance.surfaceOpacity

/** 页面画布的坐标和尺寸，用于将各栏位的模糊背景对齐到同一张图片。 */
internal data class PageBackdropFrame(val origin: Offset = Offset.Zero, val size: IntSize = IntSize.Zero)

/** 背景图与画布边界由外层一次提供，子表面仅承担绘制。 */
internal val LocalPageBackdropState = staticCompositionLocalOf { PageBackgroundUiState() }
internal val LocalPageBackdropFrame = staticCompositionLocalOf { PageBackdropFrame() }

/**
 * 在表面下绘制局部模糊壁纸，文字、按钮与容器底色继续由 Material Surface 渲染。
 * @param kind 当前表面对应的独立外观栏位。
 */
@Composable
fun PageSurface(
    kind: PageSurfaceKind,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    content: @Composable () -> Unit
) {
    val opacity = LocalPageBackdropState.current.settings?.surfaceOpacity(kind) ?: 1f
    // 半透明背景不投影，避免外侧阴影在圆角形成描边；不透明表面保留原有层次。
    val elevation = if (opacity < 1f) 0.dp else shadowElevation
    Surface(
        modifier = modifier.shadow(elevation, shape, clip = false).pageSurfaceBlur(kind, shape),
        shape = shape, color = color, contentColor = contentColor, tonalElevation = tonalElevation,
        shadowElevation = 0.dp, border = border, content = content
    )
}

/**
 * 在容器底色之前补画已处理的背景，裁切与焦点沿用页面坐标，滚动时不重新解码。
 * - 完全不透明或强度为零时直接返回原 Modifier。
 * - 仅装饰背景，不添加语义节点或参与点击处理。
 */
@Composable
fun Modifier.pageSurfaceBlur(kind: PageSurfaceKind, shape: Shape = RectangleShape): Modifier {
    val state = LocalPageBackdropState.current
    val settings = state.settings ?: return this
    if (settings.surfaceOpacity(kind) >= 1f || settings.surfaceBlur(kind) == 0) return this
    val image = state.surfaceImages[kind] ?: return this
    val frame = LocalPageBackdropFrame.current
    val background = MaterialTheme.colorScheme.background
    var origin by remember { mutableStateOf(Offset.Zero) }
    // 只记录布局边界；图片始终相对于外层画布裁切，不能在每个气泡中重新居中。
    return onGloballyPositioned { origin = it.positionInRoot() }.drawWithCache {
        val path = when (val outline = shape.createOutline(size, layoutDirection, this)) {
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Generic -> outline.path
        }
        val scale = maxOf(frame.size.width.toFloat() / image.width, frame.size.height.toFloat() / image.height)
        val width = image.width * scale
        val height = image.height * scale
        val relative = origin - frame.origin
        val scaledSize = IntSize(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1))
        val crop = BiasAlignment(settings.focalX * 2 - 1, settings.focalY * 2 - 1)
            .align(scaledSize, frame.size, layoutDirection)
        val destination = IntOffset(crop.x - relative.x.roundToInt(), crop.y - relative.y.roundToInt())
        val layerBounds = Rect(Offset.Zero, size)
        val layerPaint = Paint()
        // 先合成底色、壁纸和遮罩，再统一应用圆角覆盖率，避免底色在抗锯齿边缘渗出。
        onDrawWithContent {
            if (frame.size.width > 0 && frame.size.height > 0) clipPath(path) {
                drawContext.canvas.saveLayer(layerBounds, layerPaint)
                try {
                    drawRect(background)
                    drawImage(image, dstOffset = destination, dstSize = scaledSize)
                    drawRect(background.copy(alpha = settings.scrim))
                } finally {
                    drawContext.canvas.restore()
                }
            }
            drawContent()
        }
    }
}
