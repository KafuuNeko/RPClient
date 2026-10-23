package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp

/** 描边按钮沿用所在区域的透明度，边框至少保留一成，文字与触摸语义不变。 */
@Composable
fun PageOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val opacity = generalOpacity(LocalGeneralSurfaceKind.current)
    val borderColor = (border?.brush as? SolidColor)?.value
    // 只淡化底板与描边，避免把标签、禁用状态和点击反馈一起变透明。
    val outline = if (borderColor != null) {
        BorderStroke(
            width = border.width,
            color = borderColor.copy(alpha = borderColor.alpha * opacity.coerceAtLeast(0.1f)),
        )
    } else border
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors.withSurfaceOpacity(opacity),
        elevation = if (opacity < 1f) null else elevation,
        border = outline,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/** 实心按钮跟随所属区域；卡片内复用父背景，独立按钮补充局部模糊。 */
@Composable
fun PageButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val opacity = generalOpacity(LocalGeneralSurfaceKind.current)
    // 主色填充淡出后改用页面前景，防止 onPrimary 文字落在壁纸上失去对比。
    val fill = colors.withSurfaceOpacity(opacity).copy(
        contentColor = if (opacity < 0.5f) MaterialTheme.colorScheme.onSurface else colors.contentColor,
    )
    // 可见底板覆盖最小触摸高度，防止模糊画进 Material 按钮上下的透明触摸留白。
    val bounds = modifier.heightIn(min = 48.dp)
    val background = if (LocalGeneralPageSurfaces.current && !LocalPageSurfaceCovered.current) {
        bounds.pageSurfaceBlur(LocalGeneralSurfaceKind.current, shape)
    } else bounds
    Button(
        onClick = onClick,
        modifier = background,
        enabled = enabled,
        shape = shape,
        colors = fill,
        elevation = if (opacity < 1f) null else ButtonDefaults.buttonElevation(),
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/** 背景独立于内容颜色，禁用按钮也遵循同一套区域参数。 */
private fun ButtonColors.withSurfaceOpacity(opacity: Float): ButtonColors = copy(
    containerColor = containerColor.copy(alpha = containerColor.alpha * opacity),
    disabledContainerColor = disabledContainerColor.copy(alpha = disabledContainerColor.alpha * opacity),
)
