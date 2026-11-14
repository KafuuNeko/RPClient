package me.kafuuneko.rpclient.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import me.kafuuneko.rpclient.libs.theme.AppThemePalette

private val DarkColorScheme = darkColorScheme(
    background = BackgroundDarkColor,
    surface = SurfaceDarkColor,
    surfaceVariant = SurfaceVariantDarkColor,
    onBackground = OnBackgroundDarkColor,
    onSurface = OnSurfaceDarkColor,
    onSurfaceVariant = OnSurfaceVariantDarkColor,
    primary = PrimaryDarkColor,
    onPrimary = OnPrimaryDarkColor,
    secondary = SecondaryDarkColor,
    onSecondary = OnSecondaryDarkColor,
    error = ErrorDarkColor,
    onError = OnErrorColor,
    primaryContainer = PrimaryContainerDarkColor,
    onPrimaryContainer = OnPrimaryContainerDarkColor,
    secondaryContainer = SecondaryContainerDarkColor,
    onSecondaryContainer = OnSecondaryContainerDarkColor,
    outline = OutlineDarkColor,
    outlineVariant = OutlineVariantDarkColor,
)

private val LightColorScheme = lightColorScheme(
    background = BackgroundColor,
    surface = SurfaceColor,
    surfaceVariant = SurfaceVariantColor,
    onBackground = OnBackgroundColor,
    onSurface = OnSurfaceColor,
    onSurfaceVariant = OnSurfaceVariantColor,
    primary = PrimaryColor,
    onPrimary = OnPrimaryColor,
    secondary = SecondaryColor,
    onSecondary = OnSecondaryColor,
    error = ErrorColor,
    onError = OnErrorColor,
    primaryContainer = PrimaryContainerColor,
    onPrimaryContainer = OnPrimaryContainerColor,
    secondaryContainer = SecondaryContainerColor,
    onSecondaryContainer = OnSecondaryContainerColor,
    outline = OutlineColor,
    outlineVariant = OutlineVariantColor,
    surfaceContainer = SurfaceVariantColor,
    surfaceContainerLow = BackgroundColor,
    surfaceContainerHigh = SecondaryContainerColor,
    surfaceContainerHighest = SecondaryContainerColor,
    surfaceDim = OutlineVariantColor,
)

/** 页面遮罩使用实际应用的明暗模式，包括用户强制指定的模式。 */
val LocalAppDarkTheme = staticCompositionLocalOf { false }

/**
 * 应用所选配色，并为页面画布、正文和次级控件提供对应的表面色。
 * @param darkTheme 实际生效的明暗模式。
 * @param dynamicColor 显式使用系统动态配色，优先于预设。
 * @param palette 用户选择的配色，系统不支持动态配色时回退到经典蓝。
 * @param content 使用主题的页面内容。
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    palette: AppThemePalette = AppThemePalette.Classic,
    content: @Composable () -> Unit,
) {
    // Android 12 起可从系统获取色阶，其余情况使用用户选择的预设。
    val useSystemColors = dynamicColor || palette == AppThemePalette.Dynamic
    val paletteColors = if (useSystemColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        presetColorScheme(palette = palette, dark = darkTheme)
    }

    val pageColors = when {
        !darkTheme -> {
            // 亮色使用相邻的两档容器色，避免卡片和壁纸遮罩叠加发白。
            val colors = paletteColors ?: LightColorScheme
            colors.copy(
                background = colors.surfaceContainerLow,
                surface = colors.surfaceContainer,
                surfaceBright = colors.surfaceContainer,
            )
        }

        // 经典蓝已手工区分画布、正文和按钮。
        paletteColors == null -> DarkColorScheme

        // 深色保留画布底色，正文和按钮依次使用容器色阶，避免气泡融入背景或快捷按钮过亮。
        else -> paletteColors.copy(
            surface = paletteColors.surfaceContainer,
            surfaceVariant = paletteColors.surfaceContainerHigh,
        )
    }
    CompositionLocalProvider(LocalAppDarkTheme provides darkTheme) {
        MaterialTheme(colorScheme = pageColors, typography = Typography) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                content = content,
            )
        }
    }
}
