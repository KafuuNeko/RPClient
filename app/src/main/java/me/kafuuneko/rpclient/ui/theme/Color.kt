package me.kafuuneko.rpclient.ui.theme

import androidx.compose.ui.graphics.Color

// 背景颜色（浅色模式与暗色模式）
val BackgroundColor = Color(0xFFF8FAFC)
val BackgroundDarkColor = Color(0xFF090C10)

// 主色（浅色模式与暗色模式 - 现代鸢尾靛蓝 / 沉浸蓝）
val PrimaryColor = Color(0xFF3B66F5)
val PrimaryDarkColor = Color(0xFF4C75F8)

// 辅助色（浅色模式与暗色模式）
val SecondaryColor = Color(0xFF475569)
val SecondaryDarkColor = Color(0xFF94A3B8)

// 主色与辅助色容器颜色
val PrimaryContainerColor = Color(0xFFEEF2FF)
val PrimaryContainerDarkColor = Color(0xFF182238)
val SecondaryContainerColor = Color(0xFFE2E8F0)
val SecondaryContainerDarkColor = Color(0xFF1E283D)

// 卡片与表面元素颜色
val SurfaceColor = Color(0xFFFFFFFF)
val SurfaceDarkColor = Color(0xFF111722)
val SurfaceVariantColor = Color(0xFFF1F5F9)
val SurfaceVariantDarkColor = Color(0xFF182030)

// 边框颜色（微弱高光边框）
val OutlineColor = Color(0xFF94A3B8)
val OutlineDarkColor = Color(0xFF64748B)
val OutlineVariantColor = Color(0xFFE2E8F0)
val OutlineVariantDarkColor = Color(0xFF232D40)

// 极细微弱边框（用于去重描边后的高光微修饰）
val HairlineBorderLight = Color(0x180F172A)
val HairlineBorderDark = Color(0x1FFFFFFF)

// 错误颜色（用于警告或错误提示）
val ErrorColor = Color(0xFFDC2626)
val ErrorDarkColor = Color(0xFFF87171)

// 功能图标与状态提示共用的强调色
val AccentSkyColor = Color(0xFF0EA5E9)
val AccentEmeraldColor = Color(0xFF10B981)
val AccentIndigoColor = Color(0xFF6366F1)
val AccentPinkColor = Color(0xFFEC4899)
val AccentAmberColor = Color(0xFFF59E0B)
val AccentBlueColor = Color(0xFF3B82F6)
val AccentVioletColor = Color(0xFF8B5CF6)
val AccentRedColor = Color(0xFFEF4444)

// 浅色背景色上的文本颜色
val OnBackgroundColor = Color(0xFF0F172A)

// 暗色背景色上的文本颜色
val OnBackgroundDarkColor = Color(0xFFF1F5F9)

// 主色上的文本颜色
val OnPrimaryColor = Color(0xFFFFFFFF)
val OnPrimaryDarkColor = Color(0xFFFFFFFF)

// 表面上的文本颜色
val OnSurfaceColor = Color(0xFF0F172A)
val OnSurfaceDarkColor = Color(0xFFF1F5F9)
val OnSurfaceVariantColor = Color(0xFF64748B)
val OnSurfaceVariantDarkColor = Color(0xFF94A3B8)

// 容器上的文本颜色（用于 FilterChip 选态、高亮胶囊等）
val OnPrimaryContainerColor = Color(0xFF1E3A8A)
val OnPrimaryContainerDarkColor = Color(0xFFD3E0FF)

val OnSecondaryContainerColor = Color(0xFF1E293B)
val OnSecondaryContainerDarkColor = Color(0xFFD3E0FF)

// 辅助色上的文本颜色
val OnSecondaryColor = Color(0xFFFFFFFF)
val OnSecondaryDarkColor = Color(0xFF090C10)

// 错误色上的文本颜色
val OnErrorColor = Color(0xFFFFFFFF)

// 浅色背景上的遮罩颜色
val MarkColor = Color(0x140F172A)

// 暗色背景上的遮罩颜色
val MarkDarkColor = Color(0x1FFFFFFF)

// 角色头像与强调色彩
val CharacterAccentColors = listOf(
    Color(0xFF3B66F5),
    Color(0xFF0D9488),
    Color(0xFFEA580C),
    Color(0xFF8B5CF6),
    Color(0xFFE11D48)
)
val DefaultCharacterAccentColor = CharacterAccentColors.first()
val NarratorAvatarColor = Color(0xFF64748B)

// 模型配置状态颜色
val ProviderDisabledColor = AccentRedColor
val ProviderPendingColor = AccentAmberColor
val ProviderAvailableColor = AccentEmeraldColor

// Token 消耗统计与图表语义色彩
val TokenUsageInputColor = Color(0xFF6366F1)
val TokenUsageOutputColor = Color(0xFFF59E0B)
val TokenUsageCachedColor = Color(0xFF10B981)
val TokenUsageReasoningColor = Color(0xFFEC4899)
val TokenUsageLatencyColor = Color(0xFF0EA5E9)

// 模型消耗排行榜徽章色彩
val RankGoldBadgeColor = Color(0xFFF59E0B)
val RankGoldTextColor = Color(0xFFD97706)
val RankSilverBadgeColor = Color(0xFF94A3B8)
val RankSilverTextColor = Color(0xFF475569)
val RankBronzeBadgeColor = Color(0xFFD97706)
val RankBronzeTextColor = Color(0xFFB45309)

/**
 * 根据名称稳定生成会话列表使用的浅色强调色。
 */
fun getMacaronColor(name: String): Color {
    val hue = kotlin.math.abs(name.hashCode() % 360).toFloat()
    return Color.hsl(hue, saturation = 0.60f, lightness = 0.80f)
}
