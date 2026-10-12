package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import me.kafuuneko.rpclient.feature.appearance.presentation.PageBackgroundUiState

/** 仅作用于消息／正文容器颜色，不降低文字或交互控件的透明度。 */
val LocalPageContentOpacity = staticCompositionLocalOf { 1f }

/** 菜单栏背景不透明度，独立于正文与底部操作栏。 */
val LocalPageTopBarOpacity = staticCompositionLocalOf { 1f }

/** 底部操作栏及其内嵌输入背景的不透明度。 */
val LocalPageBottomBarOpacity = staticCompositionLocalOf { 1f }

/** 聊天气泡背景不透明度，独立作用于各消息角色。 */
val LocalPageBubbleOpacity = staticCompositionLocalOf { 1f }

/** 绘制已准备的背景和主题遮罩，图片位于滚动内容之外且不处理任何 IO。 */
@Composable
fun PageBackdrop(state: PageBackgroundUiState, content: @Composable () -> Unit) {
    val settings = state.settings
    val image = state.image
    var frame by remember { mutableStateOf(PageBackdropFrame()) }
    // 图片失效只回退画布底色；各栏位透明度不依赖图片是否存在。
    Box(Modifier.fillMaxSize().clipToBounds().background(MaterialTheme.colorScheme.background)
        .onGloballyPositioned { frame = PageBackdropFrame(it.positionInRoot(), it.size) }) {
        if (image != null && settings != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(settings.focalX * 2 - 1, settings.focalY * 2 - 1)
            )
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.background.copy(alpha = settings.scrim)))
        }
        CompositionLocalProvider(
            LocalPageBackdropState provides state,
            LocalPageBackdropFrame provides frame,
            LocalPageContentOpacity provides (settings?.opacity ?: 1f),
            LocalPageTopBarOpacity provides (settings?.topBarOpacity ?: 1f),
            LocalPageBottomBarOpacity provides (settings?.bottomBarOpacity ?: 1f),
            LocalPageBubbleOpacity provides (settings?.bubbleOpacity ?: 1f)
        ) {
            content()
        }
    }
}

/** 按气泡与主题底色合成后的亮度选择前景，避免透明浅色气泡仍使用白字。 */
@Composable
fun pageUserBubbleContentColor(): Color {
    val colors = MaterialTheme.colorScheme
    val background = colors.primary.copy(alpha = LocalPageBubbleOpacity.current)
        .compositeOver(colors.background).luminance()
    val primary = colors.onPrimary.luminance()
    val surface = colors.onSurface.luminance()
    val primaryContrast = (maxOf(background, primary) + 0.05f) / (minOf(background, primary) + 0.05f)
    val surfaceContrast = (maxOf(background, surface) + 0.05f) / (minOf(background, surface) + 0.05f)
    return if (primaryContrast >= surfaceContrast) colors.onPrimary else colors.onSurface
}
