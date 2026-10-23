package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.ui.theme.LocalAppDarkTheme

/**
 * 筛选项沿用所属区域的背景透明度和模糊度，标签与选择语义保持 Material 行为。
 * @param colors 通过 [pageFilterChipColors] 创建，避免外部自定义配色绕过透明度。
 */
@Composable
fun PageFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = FilterChipDefaults.shape,
    colors: SelectableChipColors = pageFilterChipColors(),
    elevation: SelectableChipElevation? = FilterChipDefaults.filterChipElevation(),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled, selected),
    interactionSource: MutableInteractionSource? = null,
) {
    val opacity = generalOpacity(LocalGeneralSurfaceKind.current)
    val color = (border?.brush as? SolidColor)?.value
    val outline = if (opacity < 1f && color != null) BorderStroke(
        width = border.width,
        color = color.copy(alpha = color.alpha * opacity),
    ) else border
    // 背景避开最小触摸区域的上下留白，禁止模糊层变成方形的 48dp 底板。
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier.controlBackground(CenteredControlShape(shape, FilterChipDefaults.Height)),
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = colors,
        elevation = if (opacity < 1f) null else elevation,
        border = outline,
        interactionSource = interactionSource,
    )
}

/** 调整筛选项的填充色，保留调用方指定的文字和图标颜色。 */
@Composable
fun pageFilterChipColors(
    containerColor: Color = Color.Transparent,
    labelColor: Color = Color.Unspecified,
    iconColor: Color = Color.Unspecified,
    disabledContainerColor: Color = Color.Transparent,
    disabledLabelColor: Color = Color.Unspecified,
    disabledLeadingIconColor: Color = Color.Unspecified,
    disabledTrailingIconColor: Color = Color.Unspecified,
    selectedContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    disabledSelectedContainerColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    selectedLabelColor: Color = Color.Unspecified,
    selectedLeadingIconColor: Color = Color.Unspecified,
    selectedTrailingIconColor: Color = Color.Unspecified,
): SelectableChipColors {
    val opacity = generalOpacity(LocalGeneralSurfaceKind.current)
    // 完全透明时通过强调文字保留选中反馈；不对整个控件使用 alpha。
    return FilterChipDefaults.filterChipColors(
        containerColor = containerColor.copy(alpha = containerColor.alpha * opacity),
        labelColor = labelColor,
        iconColor = iconColor,
        disabledContainerColor = disabledContainerColor.copy(alpha = disabledContainerColor.alpha * opacity),
        disabledLabelColor = disabledLabelColor,
        disabledLeadingIconColor = disabledLeadingIconColor,
        disabledTrailingIconColor = disabledTrailingIconColor,
        selectedContainerColor = selectedContainerColor.copy(alpha = selectedContainerColor.alpha * opacity),
        disabledSelectedContainerColor = disabledSelectedContainerColor.copy(alpha = disabledSelectedContainerColor.alpha * opacity),
        selectedLabelColor = if (opacity < 1f) MaterialTheme.colorScheme.primary else selectedLabelColor,
        selectedLeadingIconColor = selectedLeadingIconColor,
        selectedTrailingIconColor = selectedTrailingIconColor,
    )
}

/** 开关仅调整轨道与描边，滑块位置、颜色及触摸范围继续表达开启或关闭状态。 */
@Composable
fun PageSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val opacity = interactiveSurfaceOpacity()
    val emphasizeThumb = !LocalAppDarkTheme.current && opacity < 1f
    fun Color.track() = copy(alpha = alpha * opacity)
    // 内部轨道与外层最小触摸尺寸不同，取样只落在居中的胶囊范围。
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.controlBackground(CenteredControlShape(shape = CircleShape, height = 32.dp, width = 52.dp)),
        thumbContent = thumbContent,
        enabled = enabled,
        colors = colors.copy(
            // 亮色透明轨道不再能承托浅色滑块，开启时用原轨道强调色保证前景可见。
            checkedThumbColor = if (emphasizeThumb) colors.checkedTrackColor else colors.checkedThumbColor,
            checkedIconColor = if (emphasizeThumb) colors.checkedThumbColor else colors.checkedIconColor,
            checkedTrackColor = colors.checkedTrackColor.track(),
            uncheckedTrackColor = colors.uncheckedTrackColor.track(),
            checkedBorderColor = colors.checkedBorderColor.track(),
            uncheckedBorderColor = colors.uncheckedBorderColor.track(),
            disabledCheckedTrackColor = colors.disabledCheckedTrackColor.track(),
            disabledUncheckedTrackColor = colors.disabledUncheckedTrackColor.track(),
            disabledCheckedBorderColor = colors.disabledCheckedBorderColor.track(),
            disabledUncheckedBorderColor = colors.disabledUncheckedBorderColor.track(),
        ),
        interactionSource = interactionSource,
    )
}

/** 滑块轨道与刻度跟随所属区域，手柄保持实色以便定位和拖动。 */
@Composable
fun pageSliderColors(colors: SliderColors = SliderDefaults.colors()): SliderColors {
    val opacity = interactiveSurfaceOpacity()
    fun Color.track() = copy(alpha = alpha * opacity)
    return colors.copy(
        activeTrackColor = colors.activeTrackColor.track(),
        inactiveTrackColor = colors.inactiveTrackColor.track(),
        activeTickColor = colors.activeTickColor.track(),
        inactiveTickColor = colors.inactiveTickColor.track(),
        disabledActiveTrackColor = colors.disabledActiveTrackColor.track(),
        disabledInactiveTrackColor = colors.disabledInactiveTrackColor.track(),
        disabledActiveTickColor = colors.disabledActiveTickColor.track(),
        disabledInactiveTickColor = colors.disabledInactiveTickColor.track(),
    )
}

/** 输入边框跟随内容区，文字、浮动标签、光标以及错误提示保持原有颜色。 */
@Composable
fun pageOutlinedTextFieldColors(colors: TextFieldColors = OutlinedTextFieldDefaults.colors()): TextFieldColors {
    val opacity = interactiveSurfaceOpacity()
    return colors.copy(
        focusedContainerColor = colors.focusedContainerColor.copy(alpha = colors.focusedContainerColor.alpha * opacity),
        unfocusedContainerColor = colors.unfocusedContainerColor.copy(alpha = colors.unfocusedContainerColor.alpha * opacity),
        disabledContainerColor = colors.disabledContainerColor.copy(alpha = colors.disabledContainerColor.alpha * opacity),
        errorContainerColor = colors.errorContainerColor.copy(alpha = colors.errorContainerColor.alpha * opacity),
        focusedIndicatorColor = colors.focusedIndicatorColor.copy(alpha = colors.focusedIndicatorColor.alpha * opacity),
        unfocusedIndicatorColor = colors.unfocusedIndicatorColor.copy(alpha = colors.unfocusedIndicatorColor.alpha * opacity),
        disabledIndicatorColor = colors.disabledIndicatorColor.copy(alpha = colors.disabledIndicatorColor.alpha * opacity),
        errorIndicatorColor = colors.errorIndicatorColor.copy(alpha = colors.errorIndicatorColor.alpha * opacity),
    )
}

/** 交互轨道和输入边框最多衰减 90%，避免内容区完全透明时失去操作边界。 */
@Composable
private fun interactiveSurfaceOpacity(): Float = generalOpacity(LocalGeneralSurfaceKind.current).coerceAtLeast(0.1f)

/** 嵌套控件复用父底板的模糊，独立控件才需要局部取样。 */
@Composable
private fun Modifier.controlBackground(shape: Shape): Modifier =
    if (LocalGeneralPageSurfaces.current && !LocalPageSurfaceCovered.current)
        pageSurfaceBlur(LocalGeneralSurfaceKind.current, shape) else this

/** Material 的触摸区域大于实际底板，将取样轮廓居中到控件的可见背景。 */
private data class CenteredControlShape(val shape: Shape, val height: Dp, val width: Dp? = null) : Shape {
    /** 保持触摸坐标不变，只缩小绘制轮廓。 */
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val bounds = with(density) { Size(
            width = width?.toPx()?.coerceAtMost(size.width) ?: size.width,
            height = height.toPx().coerceAtMost(size.height),
        ) }
        val path = when (val outline = shape.createOutline(bounds, layoutDirection, density)) {
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Generic -> Path().apply { addPath(outline.path) }
        }
        path.translate(Offset((size.width - bounds.width) / 2, (size.height - bounds.height) / 2))
        return Outline.Generic(path)
    }
}
