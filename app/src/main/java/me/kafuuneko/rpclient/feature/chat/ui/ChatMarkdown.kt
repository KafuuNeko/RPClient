package me.kafuuneko.rpclient.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
internal fun MarkdownMessageText(
    content: String,
    isUser: Boolean,
    modifier: Modifier = Modifier
) {
    val blocks = remember(content) { content.parseMarkdownBlocks() }
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val linkColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    val subtleColor = textColor.copy(alpha = if (isUser) 0.72f else 0.58f)
    val blockColor = if (isUser) {
        textColor.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> MarkdownInlineText(
                    content = block.content,
                    color = textColor,
                    linkColor = linkColor,
                    style = MaterialTheme.typography.bodyMedium
                )

                is MarkdownBlock.Heading -> MarkdownInlineText(
                    content = block.content,
                    color = textColor,
                    linkColor = linkColor,
                    style = block.headingStyle()
                )

                is MarkdownBlock.Code -> MarkdownCodeBlock(
                    content = block.content,
                    language = block.language,
                    color = textColor,
                    backgroundColor = blockColor
                )

                is MarkdownBlock.Quote -> MarkdownQuoteBlock(
                    content = block.content,
                    color = textColor,
                    linkColor = linkColor,
                    backgroundColor = blockColor
                )

                is MarkdownBlock.ListBlock -> MarkdownListBlock(
                    block = block,
                    color = textColor,
                    linkColor = linkColor,
                    markerColor = subtleColor
                )

                MarkdownBlock.Divider -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(subtleColor.copy(alpha = 0.35f))
                )
            }
        }
    }
}

@Composable
private fun MarkdownBlock.Heading.headingStyle(): TextStyle {
    return when (level) {
        1 -> MaterialTheme.typography.titleSmall
        2 -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
        else -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MarkdownInlineText(
    content: String,
    color: Color,
    linkColor: Color,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val text = remember(content, color, linkColor) {
        buildAnnotatedString {
            appendMarkdownInline(
                source = content,
                textColor = color,
                linkColor = linkColor
            )
        }
    }
    Text(
        modifier = modifier,
        text = text,
        style = style,
        color = color
    )
}

@Composable
private fun MarkdownCodeBlock(
    content: String,
    language: String?,
    color: Color,
    backgroundColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!language.isNullOrBlank()) {
                Text(
                    text = language,
                    style = MaterialTheme.typography.labelSmall,
                    color = color.copy(alpha = 0.68f)
                )
            }
            Text(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                text = content.ifBlank { " " },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = color
            )
        }
    }
}

@Composable
private fun MarkdownQuoteBlock(
    content: String,
    color: Color,
    linkColor: Color,
    backgroundColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .heightIn(min = 22.dp)
                    .background(color.copy(alpha = 0.62f), CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            MarkdownInlineText(
                modifier = Modifier.weight(1f),
                content = content,
                color = color,
                linkColor = linkColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun MarkdownListBlock(
    block: MarkdownBlock.ListBlock,
    color: Color,
    linkColor: Color,
    markerColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        block.items.forEach { item ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    modifier = Modifier.width(24.dp),
                    text = item.marker,
                    style = MaterialTheme.typography.bodyMedium,
                    color = markerColor
                )
                MarkdownInlineText(
                    modifier = Modifier.weight(1f),
                    content = item.content,
                    color = color,
                    linkColor = linkColor,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private sealed class MarkdownBlock {
    data class Paragraph(val content: String) : MarkdownBlock()
    data class Heading(val level: Int, val content: String) : MarkdownBlock()
    data class Code(val language: String?, val content: String) : MarkdownBlock()
    data class Quote(val content: String) : MarkdownBlock()
    data class ListBlock(val items: List<MarkdownListItem>) : MarkdownBlock()
    data object Divider : MarkdownBlock()
}

private data class MarkdownListItem(
    val marker: String,
    val content: String
)

private val headingRegex = Regex("""^(#{1,6})\s+(.+)$""")
private val unorderedListRegex = Regex("""^\s*[-*+]\s+(.+)$""")
private val orderedListRegex = Regex("""^\s*(\d+)[.)]\s+(.+)$""")
private val dividerRegex = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")

private fun String.parseMarkdownBlocks(): List<MarkdownBlock> {
    val lines = replace("\r\n", "\n").replace('\r', '\n').lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()

        if (trimmed.isBlank()) {
            index += 1
            continue
        }

        if (line.trimStart().startsWith("```")) {
            val fence = line.trimStart()
            val language = fence.removePrefix("```").trim().takeIf { it.isNotBlank() }
            val codeLines = mutableListOf<String>()
            index += 1
            while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                codeLines += lines[index]
                index += 1
            }
            if (index < lines.size) index += 1
            blocks += MarkdownBlock.Code(language = language, content = codeLines.joinToString("\n"))
            continue
        }

        if (dividerRegex.matches(line)) {
            blocks += MarkdownBlock.Divider
            index += 1
            continue
        }

        headingRegex.matchEntire(trimmed)?.let { match ->
            blocks += MarkdownBlock.Heading(
                level = match.groupValues[1].length,
                content = match.groupValues[2].trim().trimEnd('#').trim()
            )
            index += 1
            continue
        }

        if (line.trimStart().startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (index < lines.size && lines[index].trimStart().startsWith(">")) {
                quoteLines += lines[index].trimStart().removePrefix(">").trimStart()
                index += 1
            }
            blocks += MarkdownBlock.Quote(quoteLines.joinToString("\n").trim())
            continue
        }

        if (line.isListLine()) {
            val items = mutableListOf<MarkdownListItem>()
            while (index < lines.size && lines[index].isListLine()) {
                val current = lines[index]
                val ordered = orderedListRegex.matchEntire(current)
                val unordered = unorderedListRegex.matchEntire(current)
                if (ordered != null) {
                    items += MarkdownListItem("${ordered.groupValues[1]}.", ordered.groupValues[2])
                } else if (unordered != null) {
                    items += MarkdownListItem("-", unordered.groupValues[1])
                }
                index += 1
            }
            blocks += MarkdownBlock.ListBlock(items)
            continue
        }

        val paragraphLines = mutableListOf<String>()
        while (index < lines.size && lines[index].trim().isNotBlank() && !lines[index].startsMarkdownBlock()) {
            paragraphLines += lines[index].trimEnd()
            index += 1
        }
        blocks += MarkdownBlock.Paragraph(paragraphLines.joinToString("\n").trim())
    }

    return blocks
}

private fun String.startsMarkdownBlock(): Boolean {
    val trimmedStart = trimStart()
    val trimmed = trim()
    return trimmedStart.startsWith("```") ||
        trimmedStart.startsWith(">") ||
        dividerRegex.matches(this) ||
        headingRegex.matches(trimmed) ||
        isListLine()
}

private fun String.isListLine(): Boolean {
    return unorderedListRegex.matches(this) || orderedListRegex.matches(this)
}

private fun AnnotatedString.Builder.appendMarkdownInline(
    source: String,
    textColor: Color,
    linkColor: Color
) {
    var index = 0
    while (index < source.length) {
        when {
            source.startsWith("`", index) -> {
                val end = source.indexOf('`', startIndex = index + 1)
                if (end == -1) {
                    append(source[index])
                    index += 1
                } else {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = textColor.copy(alpha = 0.14f)
                        )
                    ) {
                        append(source.substring(index + 1, end))
                    }
                    index = end + 1
                }
            }

            source.startsWith("**", index) -> {
                index = appendDelimitedMarkdown(source, index, "**", textColor, linkColor) {
                    SpanStyle(fontWeight = FontWeight.Bold)
                }
            }

            source.startsWith("__", index) -> {
                index = appendDelimitedMarkdown(source, index, "__", textColor, linkColor) {
                    SpanStyle(fontWeight = FontWeight.Bold)
                }
            }

            source.startsWith("~~", index) -> {
                index = appendDelimitedMarkdown(source, index, "~~", textColor, linkColor) {
                    SpanStyle(textDecoration = TextDecoration.LineThrough)
                }
            }

            source[index] == '*' -> {
                index = appendDelimitedMarkdown(source, index, "*", textColor, linkColor) {
                    SpanStyle(fontStyle = FontStyle.Italic)
                }
            }

            source[index] == '_' -> {
                index = appendDelimitedMarkdown(source, index, "_", textColor, linkColor) {
                    SpanStyle(fontStyle = FontStyle.Italic)
                }
            }

            source[index] == '[' -> {
                val labelEnd = source.indexOf(']', startIndex = index + 1)
                val urlStart = labelEnd + 1
                if (labelEnd != -1 && urlStart < source.length && source[urlStart] == '(') {
                    val urlEnd = source.indexOf(')', startIndex = urlStart + 1)
                    if (urlEnd != -1) {
                        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                            appendMarkdownInline(source.substring(index + 1, labelEnd), textColor, linkColor)
                        }
                        index = urlEnd + 1
                    } else {
                        append(source[index])
                        index += 1
                    }
                } else {
                    append(source[index])
                    index += 1
                }
            }

            else -> {
                append(source[index])
                index += 1
            }
        }
    }
}

private fun AnnotatedString.Builder.appendDelimitedMarkdown(
    source: String,
    start: Int,
    delimiter: String,
    textColor: Color,
    linkColor: Color,
    style: () -> SpanStyle
): Int {
    val contentStart = start + delimiter.length
    val end = source.indexOf(delimiter, startIndex = contentStart)
    return if (end == -1) {
        append(source[start])
        start + 1
    } else {
        withStyle(style()) {
            appendMarkdownInline(source.substring(contentStart, end), textColor, linkColor)
        }
        end + delimiter.length
    }
}
