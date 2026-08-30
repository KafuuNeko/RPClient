package me.kafuuneko.rpclient.utils

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** JSON 语法高亮分词类别。 */
enum class JsonSyntaxTokenType {
    Key,
    String,
    Number,
    Literal,
    Punctuation
}

/** 语法高亮 Token 区间。 */
data class JsonSyntaxToken(
    /** 当前区间的起始位置，包含该位置。 */
    val start: Int,
    /** 当前区间的结束位置，不包含该位置。 */
    val end: Int,
    /** 当前对象所属的业务类型。 */
    val type: JsonSyntaxTokenType
)

/** 语法高亮颜色配置，默认自适应 MaterialTheme。 */
data class JsonSyntaxColors(
    /** JSON 对象字段名使用的颜色。 */
    val key: Color,
    /** JSON 字符串字面量使用的颜色。 */
    val string: Color,
    /** JSON 数字字面量使用的颜色。 */
    val number: Color,
    /** JSON 布尔值和 null 字面量使用的颜色。 */
    val literal: Color,
    /** JSON 标点符号使用的颜色。 */
    val punctuation: Color
)

/**
 * 记住并创建与当前 MaterialTheme 颜色匹配的 JSON 语法高亮转换器。
 */
@Composable
fun rememberJsonSyntaxVisualTransformation(
    colors: JsonSyntaxColors = rememberDefaultJsonSyntaxColors()
): VisualTransformation {
    return remember(colors) {
        JsonSyntaxVisualTransformation(colors)
    }
}

/**
 * 从当前 MaterialTheme 提取 JSON 语法高亮颜色配置。
 */
@Composable
fun rememberDefaultJsonSyntaxColors(): JsonSyntaxColors {
    val colorScheme = MaterialTheme.colorScheme
    return remember(
        colorScheme.primary,
        colorScheme.tertiary,
        colorScheme.secondary,
        colorScheme.onSurfaceVariant
    ) {
        JsonSyntaxColors(
            key = colorScheme.primary,
            string = colorScheme.tertiary,
            number = colorScheme.secondary,
            literal = colorScheme.primary,
            punctuation = colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 为 JSON 源码提供实时语法高亮的 [VisualTransformation]。
 *
 * 词法分析器支持未闭合与正在输入的半完整 JSON，保证编辑输入时高亮平稳不闪烁。
 */
class JsonSyntaxVisualTransformation(
    private val mColors: JsonSyntaxColors
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = buildAnnotatedString {
            append(text.text)
            tokenizeJsonSyntax(text.text).forEach { token ->
                addStyle(
                    style = token.style(mColors),
                    start = token.start,
                    end = token.end
                )
            }
        }
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

private fun JsonSyntaxToken.style(colors: JsonSyntaxColors): SpanStyle {
    return when (type) {
        JsonSyntaxTokenType.Key -> SpanStyle(
            color = colors.key,
            fontWeight = FontWeight.SemiBold
        )
        JsonSyntaxTokenType.String -> SpanStyle(color = colors.string)
        JsonSyntaxTokenType.Number -> SpanStyle(color = colors.number)
        JsonSyntaxTokenType.Literal -> SpanStyle(
            color = colors.literal,
            fontWeight = FontWeight.SemiBold
        )
        JsonSyntaxTokenType.Punctuation -> SpanStyle(color = colors.punctuation)
    }
}

/** 单遍流式 JSON 词法分析器。 */
fun tokenizeJsonSyntax(source: String): List<JsonSyntaxToken> {
    val tokens = mutableListOf<JsonSyntaxToken>()
    var index = 0
    while (index < source.length) {
        val start = index
        when (source[index]) {
            '"' -> {
                index = readStringEnd(source, start)
                val type = if (isObjectKey(source, index)) {
                    JsonSyntaxTokenType.Key
                } else {
                    JsonSyntaxTokenType.String
                }
                tokens += JsonSyntaxToken(start, index, type)
            }

            '-', in '0'..'9' -> {
                index = readNumberEnd(source, start)
                tokens += JsonSyntaxToken(start, index, JsonSyntaxTokenType.Number)
            }

            '{', '}', '[', ']', ':', ',' -> {
                index++
                tokens += JsonSyntaxToken(start, index, JsonSyntaxTokenType.Punctuation)
            }

            else -> {
                val literalEnd = readLiteralEnd(source, start)
                if (literalEnd != null) {
                    index = literalEnd
                    tokens += JsonSyntaxToken(start, index, JsonSyntaxTokenType.Literal)
                } else {
                    index++
                }
            }
        }
    }
    return tokens
}

private fun readStringEnd(source: String, start: Int): Int {
    var index = start + 1
    var escaped = false
    while (index < source.length) {
        val char = source[index]
        if (escaped) {
            escaped = false
        } else {
            when (char) {
                '\\' -> escaped = true
                '"' -> return index + 1
            }
        }
        index++
    }
    return source.length
}

private fun isObjectKey(source: String, stringEnd: Int): Boolean {
    var index = stringEnd
    while (index < source.length && source[index].isWhitespace()) index++
    return index < source.length && source[index] == ':'
}

private fun readNumberEnd(source: String, start: Int): Int {
    var index = start
    if (source[index] == '-') index++
    while (index < source.length && source[index].isDigit()) index++
    if (index < source.length && source[index] == '.') {
        index++
        while (index < source.length && source[index].isDigit()) index++
    }
    if (index < source.length && (source[index] == 'e' || source[index] == 'E')) {
        index++
        if (index < source.length && (source[index] == '+' || source[index] == '-')) index++
        while (index < source.length && source[index].isDigit()) index++
    }
    return index
}

private fun readLiteralEnd(source: String, start: Int): Int? {
    if (start > 0 && source[start - 1].isIdentifierCharacter()) return null
    val literal = when (source[start]) {
        't' -> "true"
        'f' -> "false"
        'n' -> "null"
        else -> return null
    }
    if (!source.startsWith(literal, start)) return null
    val end = start + literal.length
    if (end < source.length && source[end].isIdentifierCharacter()) return null
    return end
}

private fun Char.isIdentifierCharacter(): Boolean = isLetterOrDigit() || this == '_'
