package me.kafuuneko.rpclient.feature.appearance.presentation

import me.kafuuneko.rpclient.libs.appearance.PageBackground
import kotlin.math.roundToInt

/** 将滑块输入转换为背景草稿参数；调用方负责状态守卫和预览调度。 */
internal fun PageBackground.withAdjustment(
    field: BackgroundAdjustment,
    value: Float
): PageBackground {
    // UI 使用透明度百分比，持久化与颜色渲染统一使用不透明度。
    return when (field) {
        BackgroundAdjustment.Blur -> copy(blur = value.roundToInt())
        BackgroundAdjustment.TopBarBlur -> copy(topBarBlur = value.roundToInt())
        BackgroundAdjustment.BottomBarBlur -> copy(bottomBarBlur = value.roundToInt())
        BackgroundAdjustment.BubbleBlur -> copy(bubbleBlur = value.roundToInt())
        BackgroundAdjustment.ContentBlur -> copy(contentBlur = value.roundToInt())
        BackgroundAdjustment.Scrim -> copy(scrim = value)
        BackgroundAdjustment.ContentTransparency -> copy(opacity = 1f - value)
        BackgroundAdjustment.TopBarTransparency -> copy(topBarOpacity = 1f - value)
        BackgroundAdjustment.BottomBarTransparency -> copy(bottomBarOpacity = 1f - value)
        BackgroundAdjustment.BubbleTransparency -> copy(bubbleOpacity = 1f - value)
        BackgroundAdjustment.Horizontal -> copy(focalX = value)
        BackgroundAdjustment.Vertical -> copy(focalY = value)
    }.normalized()
}
