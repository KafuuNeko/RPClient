package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.ScrollIndicatorState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 为纵向 Lazy 列表绘制可拖拽的滚动指示器。
 *
 * - 使用 Foundation 提供的 [ScrollIndicatorState] 估算长列表的位置与可见比例。
 * - 视觉宽度保持轻量，触控区域扩展到无障碍建议尺寸。
 * - 仅滑块附近拦截手势，列表其余区域继续响应常规滚动与消息操作。
 *
 * @param state 目标列表的滚动状态
 * @param color 滑块颜色
 * @param onDragStateChanged 滑块拖动开始或结束时回调
 * @return 安装滚动指示器后的 Modifier
 */
@Composable
fun Modifier.draggableLazyListScrollIndicator(
    state: LazyListState,
    color: Color = MaterialTheme.colorScheme.outline.copy(
        alpha = LAZY_LIST_SCROLL_INDICATOR_THUMB_OPACITY
    ),
    onDragStateChanged: (Boolean) -> Unit = {}
): Modifier {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val currentOnDragStateChanged = rememberUpdatedState(onDragStateChanged)
    val thumbDraggedState = remember { mutableStateOf(false) }
    val indicatorAlpha = rememberScrollIndicatorAlpha(
        state = state,
        thumbDraggedState = thumbDraggedState
    )

    return drawWithContent {
        drawContent()
        val alpha = indicatorAlpha.value
        if (alpha == 0f) return@drawWithContent
        val geometry = density.calculateLazyListScrollIndicatorGeometry(
            containerLength = size.height,
            state = state
        ) ?: return@drawWithContent
        drawLazyListScrollIndicator(
            geometry = geometry,
            color = color.copy(alpha = color.alpha * alpha),
            layoutDirection = layoutDirection,
            density = density
        )
    }.pointerInput(state, layoutDirection, density) {
        coroutineScope {
            // 合并高频拖动目标，避免列表依次执行已经过期的跳转请求。
            val scrollTargets = Channel<LazyListScrollTarget>(Channel.CONFLATED)
            launch {
                for (target in scrollTargets) {
                    state.scrollToItem(target.index, target.scrollOffset)
                }
            }
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial
                )
                // 完全隐藏后不保留透明命中区，避免抢占消息点击与文本选择。
                if (indicatorAlpha.value == 0f) return@awaitEachGesture
                val geometry = density.calculateLazyListScrollIndicatorGeometry(
                    containerLength = size.height.toFloat(),
                    state = state
                ) ?: return@awaitEachGesture

                // 只接管逻辑末端滑块附近的按压，避免影响消息气泡中的选择和点击。
                if (!density.isInsideLazyListScrollThumb(
                        position = down.position,
                        containerWidth = size.width.toFloat(),
                        geometry = geometry,
                        layoutDirection = layoutDirection
                    )
                ) return@awaitEachGesture

                val dragAnchor = down.position.y - geometry.thumbStart
                down.consume()
                thumbDraggedState.value = true
                currentOnDragStateChanged.value(true)
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break

                        // 将滑块在轨道中的位置转换为 Lazy 列表的项目索引与项目内偏移。
                        val currentGeometry = density.calculateLazyListScrollIndicatorGeometry(
                            containerLength = size.height.toFloat(),
                            state = state
                        ) ?: break
                        val targetFraction = (
                            (change.position.y - dragAnchor - currentGeometry.trackStart) /
                                currentGeometry.thumbTravel
                            ).coerceIn(0f, 1f)
                        state.calculateLazyListScrollTarget(targetFraction)?.let {
                            scrollTargets.trySend(it)
                        }
                    }
                } finally {
                    thumbDraggedState.value = false
                    currentOnDragStateChanged.value(false)
                }
            }
        }
    }
}

/** Lazy 列表滚动条的轨道与滑块像素几何。 */
private data class LazyListScrollIndicatorGeometry(
    val trackStart: Float,
    val trackLength: Float,
    val thumbStart: Float,
    val thumbLength: Float
) {
    val thumbEnd: Float
        get() = thumbStart + thumbLength

    val thumbTravel: Float
        get() = trackLength - thumbLength
}

/** Lazy 列表跳转目标，包含项目索引及项目内像素偏移。 */
private data class LazyListScrollTarget(
    val index: Int,
    val scrollOffset: Int
)

/** 按 Foundation 提供的估算尺寸计算滑块绘制位置。 */
private fun Density.calculateLazyListScrollIndicatorGeometry(
    containerLength: Float,
    state: LazyListState
): LazyListScrollIndicatorGeometry? {
    val indicatorState: ScrollIndicatorState = state.scrollIndicatorState ?: return null
    val viewportSize = indicatorState.viewportSize.toFloat()
    val contentSize = indicatorState.contentSize.toFloat()
    val maxScrollOffset = contentSize - viewportSize
    if (viewportSize <= 0f || maxScrollOffset <= 0f) return null

    // 上下留白让滑块与页面边缘保持与故事正文一致的视觉距离。
    val trackStart = LazyListScrollIndicatorMainAxisInset.toPx()
    val trackLength = containerLength - trackStart * 2f
    val minThumbLength = LazyListScrollIndicatorMinThumbLength.toPx()
    if (trackLength < minThumbLength) return null
    val maxThumbLength = maxOf(
        minThumbLength,
        trackLength * LAZY_LIST_SCROLL_INDICATOR_MAX_THUMB_LENGTH_FRACTION
    )
    val thumbLength = (trackLength * viewportSize / contentSize)
        .coerceIn(minThumbLength, maxThumbLength)
    val thumbTravel = trackLength - thumbLength
    if (thumbTravel <= 0f) return null
    // 边界以列表真实可滚动状态为准，避免可变高度消息的平均尺寸估算留下端点误差。
    val scrollFraction = when {
        !state.canScrollBackward -> 0f
        !state.canScrollForward -> 1f
        else -> (indicatorState.scrollOffset.toFloat() / maxScrollOffset)
            .coerceIn(0f, 1f)
    }
    return LazyListScrollIndicatorGeometry(
        trackStart = trackStart,
        trackLength = trackLength,
        thumbStart = trackStart + scrollFraction * thumbTravel,
        thumbLength = thumbLength
    )
}

/** 绘制位于逻辑末端的圆角滑块。 */
private fun DrawScope.drawLazyListScrollIndicator(
    geometry: LazyListScrollIndicatorGeometry,
    color: Color,
    layoutDirection: LayoutDirection,
    density: Density
) {
    // 使用逻辑末端定位，确保滑块在 RTL 布局中切换到左侧。
    val thickness = with(density) { LazyListScrollIndicatorThickness.toPx() }
    val crossAxisInset = with(density) { LazyListScrollIndicatorCrossAxisInset.toPx() }
    val left = if (layoutDirection == LayoutDirection.Ltr) {
        size.width - crossAxisInset - thickness
    } else {
        crossAxisInset
    }
    drawRoundRect(
        color = color,
        topLeft = Offset(left, geometry.thumbStart),
        size = Size(thickness, geometry.thumbLength),
        cornerRadius = CornerRadius(thickness / 2f, thickness / 2f)
    )
}

/** 判断按压点是否落在滑块扩展后的触控区域内。 */
private fun Density.isInsideLazyListScrollThumb(
    position: Offset,
    containerWidth: Float,
    geometry: LazyListScrollIndicatorGeometry,
    layoutDirection: LayoutDirection
): Boolean {
    // 横向扩大命中范围，纵向仅在短滑块不足最小触控尺寸时对称扩展。
    val touchTargetSize = LazyListScrollIndicatorTouchTargetSize.toPx()
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

/** 将轨道比例转换为与 Foundation 列表位置估算一致的跳转目标。 */
private fun LazyListState.calculateLazyListScrollTarget(
    targetFraction: Float
): LazyListScrollTarget? {
    val layoutInfo = layoutInfo
    val totalItemsCount = layoutInfo.totalItemsCount
    val indicatorState = scrollIndicatorState ?: return null
    if (totalItemsCount <= 0) return null
    if (targetFraction <= 0f) return LazyListScrollTarget(index = 0, scrollOffset = 0)
    if (targetFraction >= LAZY_LIST_SCROLL_INDICATOR_END_FRACTION) {
        return LazyListScrollTarget(index = totalItemsCount - 1, scrollOffset = 0)
    }

    // 使用 Foundation 同源的可见项目平均尺寸与间距反算索引，保持拖动和绘制估算一致。
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return null
    val averageItemSize = (
        visibleItems.sumOf { it.size } / visibleItems.size + layoutInfo.mainAxisItemSpacing
    ).coerceAtLeast(1).toFloat()
    val maxScrollOffset = (indicatorState.contentSize - indicatorState.viewportSize)
        .coerceAtLeast(0)
    val targetScrollOffset = targetFraction * maxScrollOffset
    val targetIndex = floor(targetScrollOffset / averageItemSize)
        .toInt()
        .coerceIn(0, totalItemsCount - 1)
    val targetItemOffset = (targetScrollOffset - targetIndex * averageItemSize)
        .roundToInt()
        .coerceAtLeast(0)
    return LazyListScrollTarget(
        index = targetIndex,
        scrollOffset = targetItemOffset
    )
}

private const val LAZY_LIST_SCROLL_INDICATOR_THUMB_OPACITY = 0.7f
private const val LAZY_LIST_SCROLL_INDICATOR_MAX_THUMB_LENGTH_FRACTION = 0.9f
private const val LAZY_LIST_SCROLL_INDICATOR_END_FRACTION = 0.999f
private val LazyListScrollIndicatorThickness = 4.dp
private val LazyListScrollIndicatorMinThumbLength = 24.dp
private val LazyListScrollIndicatorMainAxisInset = 10.dp
private val LazyListScrollIndicatorCrossAxisInset = 4.dp
private val LazyListScrollIndicatorTouchTargetSize = 48.dp
