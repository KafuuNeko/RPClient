package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.unit.sp
import me.kafuuneko.rpclient.utils.MarkdownBlock
import me.kafuuneko.rpclient.utils.MarkdownInline
import me.kafuuneko.rpclient.utils.MarkdownParser

/**
 * 渲染聊天消息支持的轻量 Markdown 子集。
 *
 * 解析器刻意不执行 HTML，也不加载链接或远程资源；支持角色动作/心理描写（斜体弱化）、
 * 对白（高亮）与纯净粗体排版。单条正文使用独立选择容器，避免选择跨越消息边界。
 *
 * @param content 待解析和渲染的 Markdown 消息正文
 * @param isUser 是否使用用户消息气泡的配色
 * @param modifier 应用于单条消息正文选择容器的修饰符
 */
@Composable
fun MarkdownMessageText(
    content: String,
    isUser: Boolean,
    modifier: Modifier = Modifier
) {
    val blocks = remember(content) { MarkdownParser.parseBlocks(content) }
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val linkColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    val strongColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val subtleColor = textColor.copy(alpha = if (isUser) 0.78f else 0.65f)
    val narrationColor = textColor.copy(alpha = if (isUser) 0.85f else 0.75f)
    val blockColor = if (isUser) {
        textColor.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    val selectionColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }
    val selectionColors = remember(selectionColor) {
        TextSelectionColors(
            handleColor = selectionColor,
            backgroundColor = selectionColor.copy(alpha = 0.35f)
        )
    }

    // 每条 Markdown 正文独立选择，避免把外层懒列表纳入选择范围。
    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        SelectionContainer(modifier = modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                blocks.forEach { block ->
                    when (block) {
                        is MarkdownBlock.Paragraph -> MarkdownInlineText(
                            content = block.content,
                            color = textColor,
                            narrationColor = narrationColor,
                            linkColor = linkColor,
                            strongColor = strongColor,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        is MarkdownBlock.Heading -> MarkdownInlineText(
                            content = block.content,
                            color = textColor,
                            narrationColor = narrationColor,
                            linkColor = linkColor,
                            strongColor = strongColor,
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
                            narrationColor = narrationColor,
                            linkColor = linkColor,
                            strongColor = strongColor,
                            backgroundColor = blockColor
                        )

                        is MarkdownBlock.ListBlock -> MarkdownListBlock(
                            block = block,
                            color = textColor,
                            narrationColor = narrationColor,
                            linkColor = linkColor,
                            strongColor = strongColor,
                            markerColor = subtleColor
                        )

                        MarkdownBlock.Divider -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(subtleColor.copy(alpha = 0.25f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownBlock.Heading.headingStyle(): TextStyle {
    return when (level) {
        1 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        2 -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
        else -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MarkdownInlineText(
    content: String,
    color: Color,
    narrationColor: Color,
    linkColor: Color,
    strongColor: Color,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val text = remember(content, color, narrationColor, linkColor, strongColor) {
        buildAnnotatedString {
            appendMarkdownInline(
                parts = MarkdownParser.parseInline(content),
                textColor = color,
                narrationColor = narrationColor,
                linkColor = linkColor,
                strongColor = strongColor
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
        color = backgroundColor,
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!language.isNullOrBlank()) {
                Text(
                    text = language,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = color.copy(alpha = 0.70f)
                )
            }
            Text(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                text = content.ifBlank { " " },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                ),
                color = color
            )
        }
    }
}

@Composable
private fun MarkdownQuoteBlock(
    content: String,
    color: Color,
    narrationColor: Color,
    linkColor: Color,
    strongColor: Color,
    backgroundColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .heightIn(min = 22.dp)
                    .background(color.copy(alpha = 0.55f), CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
            MarkdownInlineText(
                modifier = Modifier.weight(1f),
                content = content,
                color = color,
                narrationColor = narrationColor,
                linkColor = linkColor,
                strongColor = strongColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun MarkdownListBlock(
    block: MarkdownBlock.ListBlock,
    color: Color,
    narrationColor: Color,
    linkColor: Color,
    strongColor: Color,
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
                    narrationColor = narrationColor,
                    linkColor = linkColor,
                    strongColor = strongColor,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/** 将纯 Markdown 行内模型映射为带有 Compose 样式的 AnnotatedString。 */
private fun AnnotatedString.Builder.appendMarkdownInline(
    parts: List<MarkdownInline>,
    textColor: Color,
    narrationColor: Color,
    linkColor: Color,
    strongColor: Color
) {
    // 解析器已经完成语法判断，这里只把纯模型映射为 Compose 样式。
    parts.forEach { part ->
        when (part) {
            is MarkdownInline.Text -> append(part.content)
            is MarkdownInline.Code -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = textColor.copy(alpha = 0.10f)
                )
            ) {
                append(part.content)
            }

            is MarkdownInline.Strong -> withStyle(
                SpanStyle(
                    color = strongColor,
                    fontWeight = FontWeight.Bold
                )
            ) {
                appendMarkdownInline(part.content, textColor, narrationColor, linkColor, strongColor)
            }

            is MarkdownInline.Emphasis -> withStyle(
                SpanStyle(
                    fontStyle = FontStyle.Italic,
                    color = narrationColor
                )
            ) {
                appendMarkdownInline(part.content, textColor, narrationColor, linkColor, strongColor)
            }

            is MarkdownInline.Strikethrough -> withStyle(
                SpanStyle(textDecoration = TextDecoration.LineThrough)
            ) {
                appendMarkdownInline(part.content, textColor, narrationColor, linkColor, strongColor)
            }

            is MarkdownInline.Link -> withStyle(
                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
            ) {
                appendMarkdownInline(part.content, textColor, narrationColor, linkColor, strongColor)
            }
        }
    }
}
