package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 为纵向像素滚动容器绘制可拖拽的滚动指示器。
 *
 * - 几何信息直接来自 [ScrollState]，适用于普通滚动容器和可换行文本输入区域。
 * - 指示器位于布局逻辑末端，并随滚动停止自动淡出。
 * - 仅滑块附近的最小触控区域拦截拖动，其余区域保留原有点击与文本选择行为。
 *
 * @param state 目标容器的纵向滚动状态
 * @param color 滑块颜色
 * @return 安装滚动指示器后的 Modifier
 */
@Composable
fun Modifier.draggableScrollIndicator(
    state: ScrollState,
    color: Color = MaterialTheme.colorScheme.outline.copy(
        alpha = SCROLL_INDICATOR_THUMB_OPACITY
    )
): Modifier {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val thumbDraggedState = remember { mutableStateOf(false) }
    val indicatorAlpha = rememberScrollIndicatorAlpha(
        state = state,
        thumbDraggedState = thumbDraggedState
    )

    return drawWithContent {
        drawContent()
        val alpha = indicatorAlpha.value
        if (alpha == 0f) return@drawWithContent
        val geometry = density.calculateScrollIndicatorGeometry(size.height, state)
            ?: return@drawWithContent

        // 使用逻辑末端定位，保持 LTR 与 RTL 布局行为一致。
        val thickness = ScrollIndicatorThickness.toPx()
        val crossAxisInset = ScrollIndicatorCrossAxisInset.toPx()
        val left = if (layoutDirection == LayoutDirection.Ltr) {
            size.width - crossAxisInset - thickness
        } else {
            crossAxisInset
        }
        drawRoundRect(
            color = color.copy(alpha = color.alpha * alpha),
            topLeft = Offset(left, geometry.thumbStart),
            size = Size(thickness, geometry.thumbLength),
            cornerRadius = CornerRadius(thickness / 2f, thickness / 2f)
        )
    }.pointerInput(state, layoutDirection, density) {
        coroutineScope {
            // 合并高频拖动位置，避免累积已经过期的滚动请求。
            val scrollTargets = Channel<Int>(Channel.CONFLATED)
            launch {
                for (target in scrollTargets) state.scrollTo(target)
            }
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial
                )
                // 完全隐藏后不保留透明命中区，避免抢占容器原有手势。
                if (indicatorAlpha.value == 0f) return@awaitEachGesture
                val geometry = density.calculateScrollIndicatorGeometry(
                    containerLength = size.height.toFloat(),
                    state = state
                ) ?: return@awaitEachGesture
                if (!density.isInsideScrollThumb(
                        position = down.position,
                        containerWidth = size.width.toFloat(),
                        geometry = geometry,
                        layoutDirection = layoutDirection
                    )
                ) return@awaitEachGesture

                val dragAnchor = down.position.y - geometry.thumbStart
                down.consume()
                thumbDraggedState.value = true
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break

                        // 将滑块顶部在轨道中的比例映射为内容像素偏移。
                        val currentGeometry = density.calculateScrollIndicatorGeometry(
                            containerLength = size.height.toFloat(),
                            state = state
                        ) ?: break
                        val targetThumbStart = change.position.y - dragAnchor
                        val targetFraction = (
                            (targetThumbStart - currentGeometry.trackStart) /
                                currentGeometry.thumbTravel
                            ).coerceIn(0f, 1f)
                        scrollTargets.trySend(
                            (targetFraction * currentGeometry.maxScrollOffset).roundToInt()
                        )
                    }
                } finally {
                    thumbDraggedState.value = false
                }
            }
        }
    }
}

/** 像素滚动容器的轨道与滑块几何。 */
private data class ScrollIndicatorGeometry(
    val trackStart: Float,
    val thumbStart: Float,
    val thumbLength: Float,
    val maxScrollOffset: Int,
    val thumbTravel: Float
) {
    val thumbEnd: Float
        get() = thumbStart + thumbLength
}

/** 从滚动状态计算指示器几何，供绘制与拖动共用。 */
private fun Density.calculateScrollIndicatorGeometry(
    containerLength: Float,
    state: ScrollState
): ScrollIndicatorGeometry? {
    // 部分文本框仅维护视口与最大偏移，总尺寸需要由两者重建。
    val viewportSize = state.viewportSize.toFloat()
    val maxScrollOffset = state.maxValue
    if (
        viewportSize <= 0f ||
        maxScrollOffset <= 0 ||
        maxScrollOffset == Int.MAX_VALUE
    ) return null
    val contentSize = viewportSize + maxScrollOffset.toFloat()

    val trackStart = ScrollIndicatorMainAxisInset.toPx()
    val trackLength = containerLength - trackStart * 2f
    val minThumbLength = ScrollIndicatorMinThumbLength.toPx()
    if (trackLength < minThumbLength) return null

    // 滑块长度反映可见比例，同时保留可辨识的最小尺寸。
    val maxThumbLength = maxOf(
        minThumbLength,
        trackLength * SCROLL_INDICATOR_MAX_THUMB_LENGTH_FRACTION
    )
    val thumbLength = (trackLength * viewportSize / contentSize)
        .coerceIn(minThumbLength, maxThumbLength)
    val thumbTravel = trackLength - thumbLength
    if (thumbTravel <= 0f) return null
    val thumbStart = trackStart + (state.value.toFloat() / maxScrollOffset.toFloat())
        .coerceIn(0f, 1f) * thumbTravel
    return ScrollIndicatorGeometry(
        trackStart = trackStart,
        thumbStart = thumbStart,
        thumbLength = thumbLength,
        maxScrollOffset = maxScrollOffset,
        thumbTravel = thumbTravel
    )
}

/** 判断按压点是否落在滑块扩展后的触控区域内。 */
private fun Density.isInsideScrollThumb(
    position: Offset,
    containerWidth: Float,
    geometry: ScrollIndicatorGeometry,
    layoutDirection: LayoutDirection
): Boolean {
    // 横向扩大命中范围，纵向仅在短滑块不足最小触控尺寸时对称扩展。
    val touchTargetSize = ScrollIndicatorTouchTargetSize.toPx()
    val inEndLane = if (layoutDirection == LayoutDirection.Ltr) {
        position.x >= containerWidth - touchTargetSize
    } else {
        position.x <= touchTargetSize
    }
    val verticalExpansion = ((touchTargetSize - geometry.thumbLength) / 2f)
        .coerceAtLeast(0f)
    val inThumbTarget = position.y in
        (geometry.thumbStart - verticalExpansion)..
        (geometry.thumbEnd + verticalExpansion)
    return inEndLane && inThumbTarget
}

private const val SCROLL_INDICATOR_THUMB_OPACITY = 0.7f
private const val SCROLL_INDICATOR_MAX_THUMB_LENGTH_FRACTION = 0.9f
private val ScrollIndicatorThickness = 4.dp
private val ScrollIndicatorMinThumbLength = 24.dp
private val ScrollIndicatorMainAxisInset = 10.dp
private val ScrollIndicatorCrossAxisInset = 4.dp
private val ScrollIndicatorTouchTargetSize = 48.dp
