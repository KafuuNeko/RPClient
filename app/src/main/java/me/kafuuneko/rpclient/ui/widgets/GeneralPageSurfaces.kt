package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.libs.appearance.PageSurfaceKind
import me.kafuuneko.rpclient.libs.appearance.surfaceBlur
import me.kafuuneko.rpclient.libs.appearance.surfaceOpacity
import me.kafuuneko.rpclient.ui.theme.LocalAppDarkTheme
import me.kafuuneko.rpclient.ui.theme.LocalDialogControlOpacity

/** 页面外观开关与区域分类；弹窗可显式隔离，嵌套容器继续继承背景参数。 */
internal val LocalGeneralPageSurfaces = staticCompositionLocalOf { false }

/** 外层已铺设模糊背景时，内层只调整填充，避免重画壁纸覆盖父容器。 */
internal val LocalPageSurfaceCovered = staticCompositionLocalOf { false }
internal val LocalGeneralSurfaceKind = staticCompositionLocalOf { PageSurfaceKind.Content }

/** 通用壁纸启用后清除页面重复铺设的画布底色，其他场景保留传入颜色。 */
@Composable
fun pageCanvasColor(color: Color = MaterialTheme.colorScheme.background): Color =
    if (LocalGeneralPageSurfaces.current) Color.Transparent else color

/** 独立输入区补充内容底板；卡片内部不重复绘制，未配置通用背景时保留原有透明输入框。 */
@Composable
fun Modifier.generalContentBackground(shape: Shape, hasLabel: Boolean = false): Modifier {
    if (!LocalGeneralPageSurfaces.current || LocalPageSurfaceCovered.current) return this
    val settings = LocalPageBackdropState.current.settings ?: return this
    val kind = LocalGeneralSurfaceKind.current
    val opacity = settings.surfaceOpacity(kind)
    if (settings.fileUuid == null && opacity == 1f && settings.surfaceBlur(kind) == 0) return this
    // 描边输入框为浮动标签预留半行高度；底板避开此留白，防止出现错位的双圆角。
    val labelInset = if (hasLabel) with(LocalDensity.current) {
        (MaterialTheme.typography.bodySmall.lineHeight / 2).toDp()
    } else 0.dp
    val backgroundShape = if (hasLabel) LabelInsetShape(shape, labelInset) else shape
    return pageSurfaceBlur(kind, backgroundShape)
        .background(MaterialTheme.colorScheme.surface.copy(alpha = opacity), backgroundShape)
}

/** 将底板边界下移到输入容器，不修改输入框的测量、光标或触摸坐标。 */
private data class LabelInsetShape(val shape: Shape, val inset: Dp) : Shape {
    /** 按标签实际字号折算留白，并将原容器轮廓平移到正确位置。 */
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val top = with(density) { inset.toPx() }.coerceAtMost(size.height)
        val outline =
            shape.createOutline(Size(size.width, size.height - top), layoutDirection, density)
        val path = when (outline) {
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Generic -> Path().apply { addPath(outline.path) }
        }
        path.translate(Offset(x = 0f, y = top))
        return Outline.Generic(path)
    }
}

/** 优先读取弹窗内部控件参数，否则沿用所属页面区域的不透明度。 */
@Composable
internal fun generalOpacity(kind: PageSurfaceKind): Float =
    LocalDialogControlOpacity.current
        ?: if (LocalGeneralPageSurfaces.current) LocalPageBackdropState.current.settings?.surfaceOpacity(
            kind
        ) ?: 1f else 1f

/**
 * 亮色半透明卡片内的主题底板使用更深的色阶，避免内外层浅色叠加后边界消失。
 * - 只处理主题中性和主色容器，保留品牌色、错误色及原始 alpha。
 * - 加深量随区域透明度平滑增加，最终仍统一乘以用户的不透明度，完全透明时不留底板。
 */
@Composable
private fun nestedSurfaceColor(color: Color, opacity: Float): Color {
    val shouldKeepOriginalColor = !LocalGeneralPageSurfaces.current
        || !LocalPageSurfaceCovered.current
        || LocalAppDarkTheme.current
        || opacity >= 1f
        || color.alpha == 0f
    if (shouldKeepOriginalColor) return color
    val colors = MaterialTheme.colorScheme
    val base = color.copy(alpha = 1f)
    // 不从壁纸重新取样，也不增加描边；仅拉开内层填充与外层卡片的色差。
    val adjusted = when (base) {
        colors.primaryContainer -> lerp(base, colors.primary, (1f - opacity) * 0.8f)
        colors.surface, colors.surfaceVariant, colors.secondaryContainer,
        colors.surfaceContainer, colors.surfaceContainerHigh, colors.surfaceContainerHighest ->
            lerp(base, colors.onSurface, (1f - opacity) * 0.6f)

        else -> base
    }
    return adjusted.copy(alpha = color.alpha)
}

/** 弹窗与独立预览使用自己的表面，不继承页面透明度。 */
@Composable
fun WithoutGeneralPageSurfaces(content: @Composable () -> Unit) {
    CompositionLocalProvider(value = LocalGeneralPageSurfaces provides false, content = content)
}

/**
 * 通用表面保留 Material 点击、语义和布局，仅调整背景。
 * - 透明容器去除装饰描边与投影，沿用 PageSurface 的圆角模糊裁切。
 * - 内部控件继承背景透明度，但不重复取样壁纸；文字与图标保持清晰。
 */
@Composable
fun AppSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    kind: PageSurfaceKind = LocalGeneralSurfaceKind.current,
    content: @Composable () -> Unit,
) {
    val opacity = generalOpacity(kind)
    val decorated = if (LocalGeneralPageSurfaces.current && !LocalPageSurfaceCovered.current)
        modifier.pageSurfaceBlur(kind, shape) else modifier
    val fill = nestedSurfaceColor(color, opacity).copy(alpha = color.alpha * opacity)
    val outline = if (opacity < 1f) null else border
    val elevation = if (opacity < 1f) 0.dp else shadowElevation
    // 两种 Surface 重载保留各自的点击与无障碍语义。
    if (onClick == null) {
        Surface(
            modifier = decorated,
            shape = shape,
            color = fill,
            contentColor = contentColor,
            tonalElevation = tonalElevation,
            shadowElevation = elevation,
            border = outline,
        ) {
            CompositionLocalProvider(
                LocalPageSurfaceCovered provides true,
                LocalGeneralSurfaceKind provides kind,
                content = content,
            )
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = decorated,
            enabled = enabled,
            shape = shape,
            color = fill,
            contentColor = contentColor,
            tonalElevation = tonalElevation,
            shadowElevation = elevation,
            border = outline,
            interactionSource = interactionSource,
        ) {
            CompositionLocalProvider(
                LocalPageSurfaceCovered provides true,
                LocalGeneralSurfaceKind provides kind,
                content = content,
            )
        }
    }
}

/** 通用内容卡片共享区域外观；内部 ColumnScope 和点击语义保持 Material Card 行为。 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val kind = LocalGeneralSurfaceKind.current
    val opacity = generalOpacity(kind)
    val decorated = if (LocalGeneralPageSurfaces.current && !LocalPageSurfaceCovered.current)
        modifier.pageSurfaceBlur(kind, shape) else modifier
    val fill = colors.copy(
        containerColor = nestedSurfaceColor(colors.containerColor, opacity)
            .copy(alpha = colors.containerColor.alpha * opacity),
        disabledContainerColor = nestedSurfaceColor(colors.disabledContainerColor, opacity)
            .copy(alpha = colors.disabledContainerColor.alpha * opacity),
    )
    val outline = if (opacity < 1f) null else border
    val depth = if (opacity < 1f) CardDefaults.cardElevation(
        defaultElevation = 0.dp,
        pressedElevation = 0.dp,
        focusedElevation = 0.dp,
        hoveredElevation = 0.dp,
        draggedElevation = 0.dp,
        disabledElevation = 0.dp,
    ) else elevation
    // 子内容仍在 Card 的 ColumnScope 中布局，只隔离外观效果。
    if (onClick == null) {
        Card(
            modifier = decorated,
            shape = shape,
            colors = fill,
            elevation = depth,
            border = outline
        ) {
            CompositionLocalProvider(
                LocalPageSurfaceCovered provides true,
                LocalGeneralSurfaceKind provides kind
            ) { content() }
        }
    } else {
        Card(
            onClick = onClick,
            modifier = decorated,
            enabled = enabled,
            shape = shape,
            colors = fill,
            elevation = depth,
            border = outline,
            interactionSource = interactionSource,
        ) {
            CompositionLocalProvider(
                LocalPageSurfaceCovered provides true,
                LocalGeneralSurfaceKind provides kind
            ) { content() }
        }
    }
}

/** Scaffold 负责区域分类和透明画布，保持各页原有的安全区、悬浮按钮和 Snackbar 行为。 */
@Composable
fun AppPageScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    // 栏位分类由槽位提供，普通内容卡片不需要逐页传递设置。
    Scaffold(
        modifier = modifier,
        topBar = {
            CompositionLocalProvider(
                value = LocalGeneralSurfaceKind provides PageSurfaceKind.TopBar,
                content = topBar,
            )
        },
        bottomBar = {
            CompositionLocalProvider(
                value = LocalGeneralSurfaceKind provides PageSurfaceKind.BottomBar,
                content = bottomBar,
            )
        },
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = pageCanvasColor(containerColor),
        contentColor = contentColor,
        contentWindowInsets = contentWindowInsets,
        content = content,
    )
}
