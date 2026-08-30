package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 为纵向 Lazy 列表绘制可拖拽的滚动指示器。
 *
 * - 使用可见项目的小数索引表达列表位置，避免可变高度项目改变总像素估算。
 * - 同一列表结构内保持滑块比例稳定，项目数量或视口尺寸变化后重新计算。
 * - 拖动期间由指针直接控制滑块，列表重测不会反向改变手指下方的位置。
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
    val draggedGeometryState = remember { mutableStateOf<LazyListScrollIndicatorGeometry?>(null) }
    val sizeCache = remember(state) { LazyListScrollIndicatorSizeCache() }
    val indicatorAlpha = rememberScrollIndicatorAlpha(
        state = state,
        thumbDraggedState = thumbDraggedState
    )

    return drawWithContent {
        drawContent()
        val alpha = indicatorAlpha.value
        if (alpha == 0f) return@drawWithContent
        val geometry = draggedGeometryState.value
            ?: density.calculateLazyListScrollIndicatorGeometry(
                containerLength = size.height,
                state = state,
                sizeCache = sizeCache
            )
            ?: return@drawWithContent
        drawLazyListScrollIndicator(
            geometry = geometry,
            color = color.copy(alpha = color.alpha * alpha),
            layoutDirection = layoutDirection,
            density = density
        )
    }.pointerInput(state, layoutDirection, density) {
        coroutineScope {
            val scrollScope = this
            var dragGeneration = 0L
            // 合并高频拖动目标，避免列表依次执行已经过期的跳转请求。
            val scrollRequests = Channel(
                capacity = Channel.CONFLATED,
                onUndeliveredElement = { request: LazyListScrollRequest ->
                    request.completion?.complete(Unit)
                }
            )
            launch {
                for (request in scrollRequests) {
                    try {
                        state.scrollToLazyListTarget(request.target)
                    } finally {
                        request.completion?.complete(Unit)
                    }
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
                    state = state,
                    sizeCache = sizeCache
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
                var latestTarget: LazyListScrollTarget? = null
                val currentDragGeneration = ++dragGeneration
                down.consume()
                thumbDraggedState.value = true
                draggedGeometryState.value = geometry
                currentOnDragStateChanged.value(true)
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break

                        // 冻结本次手势的轨道几何，让滑块位置始终由触点唯一决定。
                        val targetFraction = (
                            (change.position.y - dragAnchor - geometry.trackStart) /
                                geometry.thumbTravel
                            ).coerceIn(0f, 1f)
                        draggedGeometryState.value = geometry.withScrollFraction(targetFraction)
                        latestTarget = calculateLazyListScrollTarget(
                            targetFraction = targetFraction,
                            totalItemsCount = geometry.totalItemsCount,
                            visibleItemSpan = geometry.visibleItemSpan
                        )
                        latestTarget?.let { target ->
                            scrollRequests.trySend(LazyListScrollRequest(target))
                        }
                    }
                } finally {
                    val finalTarget = latestTarget
                    if (finalTarget == null || !scrollScope.isActive) {
                        draggedGeometryState.value = null
                        thumbDraggedState.value = false
                        currentOnDragStateChanged.value(false)
                    } else {
                        // 在受限手势作用域外确认最终位置，再将滑块控制权交还给列表。
                        scrollScope.launch {
                            val completion = CompletableDeferred<Unit>()
                            try {
                                scrollRequests.send(
                                    LazyListScrollRequest(
                                        target = finalTarget,
                                        completion = completion
                                    )
                                )
                                completion.await()
                            } finally {
                                if (dragGeneration == currentDragGeneration) {
                                    draggedGeometryState.value = null
                                    thumbDraggedState.value = false
                                    currentOnDragStateChanged.value(false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Lazy 列表滚动条的轨道、滑块和项空间几何。 */
private data class LazyListScrollIndicatorGeometry(
    /** 滚动条轨道在主轴方向上的起始坐标。 */
    val trackStart: Float,
    /** 滚动条轨道在主轴方向上的长度。 */
    val trackLength: Float,
    /** 滚动条滑块在主轴方向上的起始坐标。 */
    val thumbStart: Float,
    /** 滚动条滑块在主轴方向上的长度。 */
    val thumbLength: Float,
    /** 滚动位置相对完整内容范围的比例。 */
    val scrollFraction: Float,
    /** 惰性列表当前包含的项目总数。 */
    val totalItemsCount: Int,
    /** 当前视口内可见项目覆盖的有效跨度。 */
    val visibleItemSpan: Float
) {
    val thumbEnd: Float
        get() = thumbStart + thumbLength

    val thumbTravel: Float
        get() = trackLength - thumbLength

    fun withScrollFraction(fraction: Float): LazyListScrollIndicatorGeometry = copy(
        thumbStart = trackStart + fraction * thumbTravel,
        scrollFraction = fraction
    )
}

/** Lazy 列表跳转目标，使用项目内比例延迟换算真实像素偏移。 */
internal data class LazyListScrollTarget(
    /** 当前对象在所属有序集合中的位置。 */
    val index: Int,
    /** 拖动开始时滚动偏移所占的比例。 */
    val scrollOffsetFraction: Float,
    /** 是否请求将列表直接滚动到末尾。 */
    val scrollToEnd: Boolean = false
)

/** 高频拖动请求及可选的最终位置确认。 */
private data class LazyListScrollRequest(
    /** 当前操作作用的目标。 */
    val target: LazyListScrollTarget,
    /** 异步系统操作完成后需要触发的回调。 */
    val completion: CompletableDeferred<Unit>? = null
)

/** 与 Compose 布局对象解耦的可见项目快照。 */
internal data class LazyListVisibleItem(
    /** 当前对象在所属有序集合中的位置。 */
    val index: Int,
    /** 当前数据相对起点的偏移量。 */
    val offset: Int,
    /** 当前图像、视口或数据块的尺寸。 */
    val size: Int
)

/** 列表在项空间中的连续位置与当前可见跨度。 */
internal data class LazyListItemSpaceMetrics(
    /** 惰性列表当前包含的项目总数。 */
    val totalItemsCount: Int,
    /** 当前滚动容器沿主轴方向的可视尺寸。 */
    val viewportSize: Int,
    /** 当前视口中第一个可见列表项的位置。 */
    val firstVisibleItemIndex: Float,
    /** 当前视口内可见项目覆盖的有效跨度。 */
    val visibleItemSpan: Float
)

/**
 * 在列表结构稳定期间缓存可见比例。
 *
 * 可见窗口内项目高度会随滚动位置剧烈变化，因此只有项目数量或视口尺寸变化时才
 * 开启新的比例周期，避免滑块在一次滚动手势中伸缩。
 */
internal class LazyListScrollIndicatorSizeCache {
    private var mTotalItemsCount = -1
    private var mViewportSize = -1
    private var mSizeFraction = 0f

    /** 根据结构周期返回稳定的滑块比例。 */
    fun resolve(metrics: LazyListItemSpaceMetrics): Float {
        if (
            metrics.totalItemsCount != mTotalItemsCount ||
            metrics.viewportSize != mViewportSize ||
            mSizeFraction <= 0f
        ) {
            mTotalItemsCount = metrics.totalItemsCount
            mViewportSize = metrics.viewportSize
            mSizeFraction = (
                metrics.visibleItemSpan / metrics.totalItemsCount.coerceAtLeast(1)
                ).coerceIn(0f, 1f)
        }
        return mSizeFraction
    }
}

/** 按稳定的项空间比例计算滑块绘制位置。 */
private fun Density.calculateLazyListScrollIndicatorGeometry(
    containerLength: Float,
    state: LazyListState,
    sizeCache: LazyListScrollIndicatorSizeCache
): LazyListScrollIndicatorGeometry? {
    if (!state.canScrollBackward && !state.canScrollForward) return null
    val layoutInfo = state.layoutInfo
    val metrics = calculateLazyListItemSpaceMetrics(
        totalItemsCount = layoutInfo.totalItemsCount,
        viewportStartOffset = layoutInfo.viewportStartOffset,
        viewportEndOffset = layoutInfo.viewportEndOffset,
        visibleItems = layoutInfo.visibleItemsInfo.map { item ->
            LazyListVisibleItem(
                index = item.index,
                offset = item.offset,
                size = item.size
            )
        }
    ) ?: return null

    // 上下留白让滑块与页面边缘保持与故事正文一致的视觉距离。
    val trackStart = LazyListScrollIndicatorMainAxisInset.toPx()
    val trackLength = containerLength - trackStart * 2f
    val minThumbLength = LazyListScrollIndicatorMinThumbLength.toPx()
    if (trackLength < minThumbLength) return null
    val maxThumbLength = maxOf(
        minThumbLength,
        trackLength * LAZY_LIST_SCROLL_INDICATOR_MAX_THUMB_LENGTH_FRACTION
    )
    val stableSizeFraction = sizeCache.resolve(metrics)
    val thumbLength = (trackLength * stableSizeFraction)
        .coerceIn(minThumbLength, maxThumbLength)
    val thumbTravel = trackLength - thumbLength
    if (thumbTravel <= 0f) return null

    // 项空间位置和拖动反算共享同一稳定跨度，保证两条路径严格互逆。
    val stableVisibleItemSpan = stableSizeFraction * metrics.totalItemsCount
    val listScrollFraction = calculateLazyListScrollFraction(
        metrics = metrics,
        stableVisibleItemSpan = stableVisibleItemSpan,
        canScrollBackward = state.canScrollBackward,
        canScrollForward = state.canScrollForward
    )
    return LazyListScrollIndicatorGeometry(
        trackStart = trackStart,
        trackLength = trackLength,
        thumbStart = trackStart + listScrollFraction * thumbTravel,
        thumbLength = thumbLength,
        scrollFraction = listScrollFraction,
        totalItemsCount = metrics.totalItemsCount,
        visibleItemSpan = stableVisibleItemSpan
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

/**
 * 将 Compose 可见项目转换为连续的项空间指标。
 *
 * 首尾项目按实际可见像素折算为小数索引，中间项目只贡献逻辑跨度。项目高度变化
 * 只影响当前项目内的连续进度，不再参与未知总内容高度的推算。
 */
internal fun calculateLazyListItemSpaceMetrics(
    totalItemsCount: Int,
    viewportStartOffset: Int,
    viewportEndOffset: Int,
    visibleItems: List<LazyListVisibleItem>
): LazyListItemSpaceMetrics? {
    if (totalItemsCount <= 0 || viewportEndOffset <= viewportStartOffset) return null
    val viewportItems = visibleItems.filter { item ->
        item.size > 0 &&
            item.offset < viewportEndOffset &&
            item.offset + item.size > viewportStartOffset
    }
    val firstItem = viewportItems.firstOrNull() ?: return null
    val lastItem = viewportItems.last()

    // 只对首尾项目计算局部像素比例，避免不同项目高度污染整个列表的总量估算。
    val firstVisibleStart = maxOf(firstItem.offset, viewportStartOffset)
    val firstVisibleItemIndex = firstItem.index +
        (firstVisibleStart - firstItem.offset).toFloat() / firstItem.size
    val lastVisibleEnd = minOf(lastItem.offset + lastItem.size, viewportEndOffset)
    val lastVisibleItemIndex = lastItem.index +
        (lastVisibleEnd - lastItem.offset).toFloat() / lastItem.size
    val visibleItemSpan = (lastVisibleItemIndex - firstVisibleItemIndex)
        .coerceIn(LAZY_LIST_SCROLL_INDICATOR_MIN_VISIBLE_ITEM_SPAN, totalItemsCount.toFloat())
    return LazyListItemSpaceMetrics(
        totalItemsCount = totalItemsCount,
        viewportSize = viewportEndOffset - viewportStartOffset,
        firstVisibleItemIndex = firstVisibleItemIndex,
        visibleItemSpan = visibleItemSpan
    )
}

/** 使用稳定的可见跨度将小数首项索引转换为轨道比例。 */
internal fun calculateLazyListScrollFraction(
    metrics: LazyListItemSpaceMetrics,
    stableVisibleItemSpan: Float,
    canScrollBackward: Boolean,
    canScrollForward: Boolean
): Float = when {
    !canScrollBackward -> 0f
    !canScrollForward -> 1f
    else -> {
        val scrollableItemSpan = (
            metrics.totalItemsCount - stableVisibleItemSpan
            ).coerceAtLeast(LAZY_LIST_SCROLL_INDICATOR_MIN_VISIBLE_ITEM_SPAN)
        (metrics.firstVisibleItemIndex / scrollableItemSpan).coerceIn(0f, 1f)
    }
}

/** 将轨道比例转换为逻辑项目索引及项目内比例。 */
internal fun calculateLazyListScrollTarget(
    targetFraction: Float,
    totalItemsCount: Int,
    visibleItemSpan: Float
): LazyListScrollTarget? {
    if (totalItemsCount <= 0) return null
    if (targetFraction <= 0f) {
        return LazyListScrollTarget(index = 0, scrollOffsetFraction = 0f)
    }
    if (targetFraction >= LAZY_LIST_SCROLL_INDICATOR_END_FRACTION) {
        return LazyListScrollTarget(
            index = totalItemsCount - 1,
            scrollOffsetFraction = 0f,
            scrollToEnd = true
        )
    }

    // 使用绘制阶段缓存的跨度保持正反换算一致，避免拖动过程重新采样项目高度。
    val scrollableItemSpan = (totalItemsCount - visibleItemSpan)
        .coerceAtLeast(0f)
    val targetItemIndex = targetFraction * scrollableItemSpan
    val targetIndex = floor(targetItemIndex).toInt()
        .coerceIn(0, totalItemsCount - 1)
    return LazyListScrollTarget(
        index = targetIndex,
        scrollOffsetFraction = (targetItemIndex - targetIndex).coerceIn(0f, 1f)
    )
}

/** 跳转到逻辑项目位置，并在目标项目测量后补齐项目内像素偏移。 */
private suspend fun LazyListState.scrollToLazyListTarget(target: LazyListScrollTarget) {
    if (target.scrollToEnd) {
        // 最后一项可能高于视口，额外消费剩余距离才能保证真正抵达列表底部。
        scrollToItem(target.index)
        scrollBy(Float.MAX_VALUE)
        return
    }
    val visibleTarget = layoutInfo.visibleItemsInfo.firstOrNull { it.index == target.index }
    if (visibleTarget != null) {
        scrollToItem(
            index = target.index,
            scrollOffset = visibleTarget.calculateScrollOffset(target.scrollOffsetFraction)
        )
        return
    }

    // 未测量项目无法预知像素高度，先定位项目，再用真实尺寸细调项目内位置。
    scrollToItem(target.index)
    val measuredTarget = layoutInfo.visibleItemsInfo.firstOrNull { it.index == target.index }
        ?: return
    val scrollOffset = measuredTarget.calculateScrollOffset(target.scrollOffsetFraction)
    if (scrollOffset > 0) {
        scrollToItem(index = target.index, scrollOffset = scrollOffset)
    }
}

/** 将项目内比例换算为不越过项目末端的像素偏移。 */
private fun LazyListItemInfo.calculateScrollOffset(
    scrollOffsetFraction: Float
): Int = (size * scrollOffsetFraction)
    .roundToInt()
    .coerceIn(0, (size - 1).coerceAtLeast(0))

private const val LAZY_LIST_SCROLL_INDICATOR_THUMB_OPACITY = 0.7f
private const val LAZY_LIST_SCROLL_INDICATOR_MAX_THUMB_LENGTH_FRACTION = 0.9f
private const val LAZY_LIST_SCROLL_INDICATOR_END_FRACTION = 0.999f
private const val LAZY_LIST_SCROLL_INDICATOR_MIN_VISIBLE_ITEM_SPAN = 0.0001f
private val LazyListScrollIndicatorThickness = 4.dp
private val LazyListScrollIndicatorMinThumbLength = 24.dp
private val LazyListScrollIndicatorMainAxisInset = 10.dp
private val LazyListScrollIndicatorCrossAxisInset = 4.dp
private val LazyListScrollIndicatorTouchTargetSize = 48.dp
