package me.kafuuneko.rpclient.ui.theme

import androidx.compose.ui.graphics.Color

// 背景颜色（浅色模式与暗色模式）
val BackgroundColor = Color(0xFFF7F3EC)
val BackgroundDarkColor = Color(0xFF0B0D12)

// 主色（浅色模式与暗色模式）
val PrimaryColor = Color(0xFF2563EB)
val PrimaryDarkColor = Color(0xFF8FB2FF)

// 辅助色（浅色模式与暗色模式）
val SecondaryColor = Color(0xFF5D6D55)
val SecondaryDarkColor = Color(0xFFBFD6B1)

// 其他颜色(卡片、表面元素的颜色)
val SurfaceColor = Color(0xFFFFFCF7)
val SurfaceDarkColor = Color(0xFF171A22)
val SurfaceVariantColor = Color(0xFFE7E1D8)
val SurfaceVariantDarkColor = Color(0xFF242936)
val OutlineColor = Color(0xFF81766A)
val OutlineDarkColor = Color(0xFF8C94A3)

// 错误颜色（用于警告或错误提示）
val ErrorColor = Color(0xFFB00020)
val ErrorDarkColor = Color(0xFFCF6679)

// 浅色背景色上的文本颜色
val OnBackgroundColor = Color(0xFF181B20)

// 暗色背景色上的文本颜色
val OnBackgroundDarkColor = Color(0xFFF1F3F8)

// 浅色模式主色上的文本颜色
val OnPrimaryColor = Color(0xFFFFFFFF)

// 暗色模式主色上的文本颜色
val OnPrimaryDarkColor = Color(0xFF0A1020)

// 浅色模式表面上的文本颜色
val OnSurfaceColor = Color(0xFF1A1C20)

// 暗色模式表面上的文本颜色
val OnSurfaceDarkColor = Color(0xFFE9ECF3)
val OnSurfaceVariantColor = Color(0xFF4E463E)
val OnSurfaceVariantDarkColor = Color(0xFFC5CBD6)

// 辅助色上的文本颜色
val OnSecondaryColor = Color(0xFFFFFFFF)
val OnSecondaryDarkColor = Color(0xFF182113)

// 错误色上的文本颜色
val OnErrorColor = Color(0xFFFFFFFF)

// 浅色背景上的遮罩颜色
val MarkColor = Color(0x1A000000)

// 暗色背景上的遮罩颜色
val MarkDarkColor = Color(0x1AFFFFFF)

// 角色头像颜色
val CharacterAccentColors = listOf(
    Color(0xFF315EFD),
    Color(0xFF0F9F8F),
    Color(0xFFB55A12),
    Color(0xFF8A4FFF),
    Color(0xFFB3261E)
)
val DefaultCharacterAccentColor = CharacterAccentColors.first()
val NarratorAvatarColor = Color.Gray

// 模型 Provider 状态颜色
val ProviderDisabledColor = Color(0xFFE53935)
val ProviderPendingColor = Color(0xFFFFB300)
val ProviderAvailableColor = Color(0xFF4CAF50)

/**
 * 根据名称稳定生成会话列表使用的浅色强调色。
 */
fun getMacaronColor(name: String): Color {
    val hue = kotlin.math.abs(name.hashCode() % 360).toFloat()
    return Color.hsl(hue, saturation = 0.65f, lightness = 0.82f)
}
