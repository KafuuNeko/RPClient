package me.kafuuneko.rpclient.utils

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** 提示词高亮颜色配置。 */
data class PromptHighlightColors(
    /** Prompt 宏高亮使用的前景色。 */
    val macroForeground: Color,
    /** Prompt 宏高亮使用的背景色。 */
    val macroBackground: Color,
    /** Prompt 标签高亮使用的前景色。 */
    val tagForeground: Color,
    /** Prompt 标签高亮使用的背景色。 */
    val tagBackground: Color,
    /** Prompt 结构分段标记使用的前景色。 */
    val sectionForeground: Color
)

/**
 * 记住并创建与当前 MaterialTheme 颜色匹配的 Prompt 宏与标签语法高亮转换器。
 */
@Composable
fun rememberPromptMacroVisualTransformation(
    colors: PromptHighlightColors = rememberDefaultPromptHighlightColors()
): VisualTransformation {
    return remember(colors) {
        PromptMacroVisualTransformation(colors)
    }
}

/** 记住供状态式文本框使用的 Prompt 宏与标签语法高亮转换器。 */
@Composable
fun rememberPromptMacroOutputTransformation(
    colors: PromptHighlightColors = rememberDefaultPromptHighlightColors()
): OutputTransformation = remember(colors) {
    OutputTransformation {
        val raw = asCharSequence().toString()
        // 仅附加样式而不改变字符，光标和选择区可继续使用原始偏移。
        PROMPT_TAG_REGEX.findAll(raw).forEach { match ->
            addStyle(
                spanStyle = SpanStyle(
                    color = colors.tagForeground,
                    background = colors.tagBackground,
                    fontWeight = FontWeight.Medium
                ),
                start = match.range.first,
                end = match.range.last + 1
            )
        }
        PROMPT_SECTION_REGEX.findAll(raw).forEach { match ->
            addStyle(
                spanStyle = SpanStyle(
                    color = colors.sectionForeground,
                    fontWeight = FontWeight.Bold
                ),
                start = match.range.first,
                end = match.range.last + 1
            )
        }
        PROMPT_MACRO_REGEX.findAll(raw).forEach { match ->
            addStyle(
                spanStyle = SpanStyle(
                    color = colors.macroForeground,
                    background = colors.macroBackground,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                start = match.range.first,
                end = match.range.last + 1
            )
        }
    }
}

/**
 * 从当前 MaterialTheme 提取 Prompt 宏与标签高亮颜色配置。
 */
@Composable
fun rememberDefaultPromptHighlightColors(): PromptHighlightColors {
    val colorScheme = MaterialTheme.colorScheme
    return remember(
        colorScheme.primary,
        colorScheme.tertiary,
        colorScheme.secondary
    ) {
        PromptHighlightColors(
            macroForeground = colorScheme.primary,
            macroBackground = colorScheme.primary.copy(alpha = 0.12f),
            tagForeground = colorScheme.tertiary,
            tagBackground = colorScheme.tertiary.copy(alpha = 0.10f),
            sectionForeground = colorScheme.secondary
        )
    }
}

/**
 * 为 Prompt 模板中的宏变量（如 `{{char}}`、`{{user}}`、`{0}`）与系统边界标签提供实时高亮显示的 [VisualTransformation]。
 */
class PromptMacroVisualTransformation(
    private val mColors: PromptHighlightColors
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val highlighted = buildAnnotatedString {
            append(raw)

            PROMPT_TAG_REGEX.findAll(raw).forEach { match ->
                addStyle(
                    style = SpanStyle(
                        color = mColors.tagForeground,
                        background = mColors.tagBackground,
                        fontWeight = FontWeight.Medium
                    ),
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }

            PROMPT_SECTION_REGEX.findAll(raw).forEach { match ->
                addStyle(
                    style = SpanStyle(
                        color = mColors.sectionForeground,
                        fontWeight = FontWeight.Bold
                    ),
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }

            PROMPT_MACRO_REGEX.findAll(raw).forEach { match ->
                addStyle(
                    style = SpanStyle(
                        color = mColors.macroForeground,
                        background = mColors.macroBackground,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }
        }
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

private val PROMPT_MACRO_REGEX =
    Regex("""\{\{[^{}\n\r]+\}\}|\{\d+\}|<START>|<START_EXAMPLES>|<END_EXAMPLES>""")
private val PROMPT_TAG_REGEX = Regex("""\[[^\[\]\n\r]+\]""")
private val PROMPT_SECTION_REGEX = Regex("""---[^\n\r]+---""")
