package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 带统一侧边滚动指示器的纵向惰性列表。
 *
 * 参数与 [LazyColumn] 的常用布局能力保持一致，页面和对话框无需重复管理仅供指示器
 * 使用的 [LazyListState]。需要监听滑块拖动状态的消息列表应直接组合底层指示器。
 *
 * @param modifier 列表滚动视口的布局修饰符。页面内容的水平留白应通过
 * [contentPadding] 提供，以免收窄指示器的绘制与拖动区域
 * @param state 列表滚动状态
 * @param contentPadding 列表项目的内容内边距
 * @param reverseLayout 是否反转布局方向
 * @param verticalArrangement 列表项目的纵向排列方式
 * @param horizontalAlignment 列表项目的横向对齐方式
 * @param flingBehavior 列表惯性滚动行为
 * @param userScrollEnabled 是否允许用户滚动
 * @param content 列表内容
 */
@Composable
fun RpLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) {
        Arrangement.Top
    } else {
        Arrangement.Bottom
    },
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = modifier.draggableLazyListScrollIndicator(state),
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        content = content
    )
}
