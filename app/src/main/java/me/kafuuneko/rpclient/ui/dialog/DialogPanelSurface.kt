package me.kafuuneko.rpclient.ui.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.ui.theme.LocalDialogAppearance
import me.kafuuneko.rpclient.ui.theme.LocalDialogControlOpacity
import me.kafuuneko.rpclient.ui.widgets.LocalGeneralPageSurfaces
import me.kafuuneko.rpclient.ui.widgets.LocalPageSurfaceCovered

/** 公共弹窗底板，独立控制底色和窗口后景，内部控件不继承页面壁纸或区域参数。 */
@Composable
internal fun DialogPanelSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val settings = LocalDialogAppearance.current.resolved(rememberDialogBlurAvailable())
    val opacity = 1f - settings.transparency / 100f
    // 半透明底板取消装饰描边与外阴影，文字和按钮前景保持独立不透明。
    Surface(
        modifier = modifier.dialogWindowAppearance(settings),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = opacity),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = if (opacity == 1f) 6.dp else 0.dp,
        shadowElevation = if (opacity == 1f) 12.dp else 0.dp,
        border = if (opacity == 1f) BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ) else null,
    ) {
        DialogControlAppearance(1f - settings.controlTransparency / 100f, content)
    }
}

/** 内部控件复用整个弹窗的后景，不再次采样页面图片。 */
@Composable
internal fun DialogControlAppearance(opacity: Float, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalGeneralPageSurfaces provides false,
        LocalPageSurfaceCovered provides true,
        LocalDialogControlOpacity provides opacity,
        content = content,
    )
}

/** 仅淡化内部装饰底色，不能用于文字和状态图标。 */
@Composable
internal fun dialogControlColor(color: Color): Color =
    color.copy(alpha = color.alpha * (LocalDialogControlOpacity.current ?: 1f))

/** 按钮只调整填充，高透明度时使用主题前景，避免浅色按钮文字失去对比。 */
@Composable
internal fun dialogButtonColors(
    colors: ButtonColors,
    fadedContentColor: Color = MaterialTheme.colorScheme.onSurface,
): ButtonColors = colors.copy(
    containerColor = dialogControlColor(colors.containerColor),
    disabledContainerColor = dialogControlColor(colors.disabledContainerColor),
    contentColor = if ((LocalDialogControlOpacity.current
            ?: 1f) < 0.5f
    ) fadedContentColor else colors.contentColor,
)
