package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 统一管理滚动指示器的可见性：滚动或拖动滑块时显示，停止一段时间后淡出。
 *
 * 使用 [ScrollableState.isScrollInProgress] 同时覆盖触摸滚动、惯性滚动与代码滚动；
 * 单独保留滑块拖动状态，避免离散的 scrollTo 调用让指示器在手势中闪烁。
 */
@Composable
internal fun rememberScrollIndicatorAlpha(
    state: ScrollableState,
    thumbDraggedState: State<Boolean>
): State<Float> {
    val alpha = remember(state) { Animatable(0f) }

    LaunchedEffect(state, thumbDraggedState) {
        snapshotFlow {
            state.isScrollInProgress || thumbDraggedState.value
        }.distinctUntilChanged().collectLatest { active ->
            if (active) {
                alpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(SCROLL_INDICATOR_FADE_IN_DURATION_MILLIS)
                )
            } else {
                delay(SCROLL_INDICATOR_HIDE_DELAY_MILLIS)
                alpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(SCROLL_INDICATOR_FADE_OUT_DURATION_MILLIS)
                )
            }
        }
    }

    return alpha.asState()
}

private const val SCROLL_INDICATOR_FADE_IN_DURATION_MILLIS = 100
private const val SCROLL_INDICATOR_HIDE_DELAY_MILLIS = 800L
private const val SCROLL_INDICATOR_FADE_OUT_DURATION_MILLIS = 250
