package me.kafuuneko.rpclient.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    // 大号展示文本
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
    ),
    // 中号展示文本
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.25).sp,
    ),
    // 小号展示文本
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),

    // 大号副标题/板块大标题
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    // 中号副标题/板块中标题
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    // 小号副标题/板块小标题
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),

    // 大号标题（弹窗、重要容器）
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
    ),
    // 中号标题（卡片标题、顶部栏标题）
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    ),
    // 小号标题（列表项标题、分区小标题）
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    ),

    // 大号正文（创作大段正文）
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.15.sp,
    ),
    // 中号正文（对话气泡正文、常规描述）
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.15.sp,
    ),
    // 小号正文（辅助说明、次级描述）
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.2.sp,
    ),

    // 大号标签（主按钮、突出药丸）
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    ),
    // 中号标签（小按钮、选择芯片）
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp,
    ),
    // 小号标签（状态徽章、时间戳、元数据胶囊）
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.25.sp,
    ),
)
