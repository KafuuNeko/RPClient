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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kafuuneko.rpclient.utils.MarkdownBlock
import me.kafuuneko.rpclient.utils.MarkdownHighlightMapper
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
    modifier: Modifier = Modifier,
    blockSpacing: Dp = 10.dp,
    highlightedText: String? = null,
    highlightedOccurrenceIndex: Int = 0,
    onHighlightedPosition: ((Float?) -> Unit)? = null,
) {
    val blocks = remember(content) { MarkdownParser.parseBlocks(content) }
    val textColor = if (isUser) pageUserBubbleContentColor() else MaterialTheme.colorScheme.onSurface
    val linkColor = if (isUser) pageUserBubbleContentColor() else MaterialTheme.colorScheme.primary
    val strongColor = if (isUser) pageUserBubbleContentColor() else MaterialTheme.colorScheme.onSurface
    val subtleColor = textColor.copy(alpha = if (isUser) 0.78f else 0.65f)
    val narrationColor = textColor.copy(alpha = if (isUser) 0.85f else 0.75f)
    val blockColor = if (isUser) {
        textColor.copy(alpha = 0.12f * LocalPageBubbleOpacity.current)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f * LocalPageBubbleOpacity.current)
    }
    val selectionColor = if (isUser) {
        pageUserBubbleContentColor()
    } else {
        MaterialTheme.colorScheme.primary
    }
    val selectionColors = remember(selectionColor) {
        TextSelectionColors(
            handleColor = selectionColor,
            backgroundColor = selectionColor.copy(alpha = 0.35f),
        )
    }

    // 每条 Markdown 正文独立选择，避免把外层懒列表纳入选择范围。
    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        SelectionContainer(modifier = modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(blockSpacing),
            ) {
                val highlightRanges = remember(blocks, highlightedText, highlightedOccurrenceIndex) {
                    MarkdownHighlightMapper.findRanges(
                        blocks = blocks,
                        highlightedText = highlightedText,
                        occurrenceIndex = highlightedOccurrenceIndex,
                    )
                }
                val rangesByBlock = remember(highlightRanges) {
                    highlightRanges.groupBy { markdownHighlightRange -> markdownHighlightRange.blockIndex }
                }
                val highlightedStartBlock = highlightRanges.firstOrNull()?.blockIndex
                if (highlightedStartBlock == null && onHighlightedPosition != null) {
                    // 最新修改无法映射时清除旧坐标，避免滚动到上一次结果。
                    SideEffect { onHighlightedPosition(null) }
                }
                blocks.forEachIndexed { blockIndex, block ->
                    val blockRanges = rangesByBlock[blockIndex].orEmpty()
                        .filter { markdownHighlightRange -> markdownHighlightRange.listItemIndex == null }
                        .map { markdownHighlightRange -> markdownHighlightRange.start until markdownHighlightRange.endExclusive }
                    // 高亮内容或范围变化时重建该块，使布局回调对应最新生成结果。
                    key(
                        blockIndex,
                        block.takeIf { blockIndex == highlightedStartBlock },
                        blockRanges.takeIf { blockIndex == highlightedStartBlock },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned(
                                    onGloballyPositioned = { coordinates ->
                                        if (blockIndex == highlightedStartBlock) {
                                            onHighlightedPosition?.invoke(coordinates.positionInParent().y)
                                        }
                                    },
                                ),
                        ) {
                            when (block) {
                                is MarkdownBlock.Paragraph -> MarkdownInlineText(
                                    content = block.content,
                                    color = textColor,
                                    narrationColor = narrationColor,
                                    linkColor = linkColor,
                                    strongColor = strongColor,
                                    highlightedRanges = blockRanges,
                                    style = MaterialTheme.typography.bodyMedium,
                                )

                                is MarkdownBlock.Heading -> MarkdownInlineText(
                                    content = block.content,
                                    color = textColor,
                                    narrationColor = narrationColor,
                                    linkColor = linkColor,
                                    strongColor = strongColor,
                                    highlightedRanges = blockRanges,
                                    style = block.headingStyle(),
                                )

                                is MarkdownBlock.Code -> MarkdownCodeBlock(
                                    content = block.content,
                                    language = block.language,
                                    color = textColor,
                                    backgroundColor = blockColor,
                                    highlightedRanges = blockRanges,
                                )

                                is MarkdownBlock.Quote -> MarkdownQuoteBlock(
                                    content = block.content,
                                    color = textColor,
                                    narrationColor = narrationColor,
                                    linkColor = linkColor,
                                    strongColor = strongColor,
                                    highlightedRanges = blockRanges,
                                    backgroundColor = blockColor,
                                )

                                is MarkdownBlock.ListBlock -> MarkdownListBlock(
                                    block = block,
                                    color = textColor,
                                    narrationColor = narrationColor,
                                    linkColor = linkColor,
                                    strongColor = strongColor,
                                    highlightedRangesByItem = List(block.items.size) { itemIndex ->
                                        rangesByBlock[blockIndex].orEmpty()
                                            .filter { markdownHighlightRange -> markdownHighlightRange.listItemIndex == itemIndex }
                                            .map { markdownHighlightRange -> markdownHighlightRange.start until markdownHighlightRange.endExclusive }
                                    },
                                    markerColor = subtleColor,
                                )

                                MarkdownBlock.Divider -> Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(subtleColor.copy(alpha = 0.25f)),
                                )
                            }
                        }
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
    highlightedRanges: List<IntRange> = emptyList(),
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val highlightBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val highlightForeground = MaterialTheme.colorScheme.primary
    val text = remember(
        content,
        color,
        narrationColor,
        linkColor,
        strongColor,
        highlightedRanges,
        highlightBackground,
        highlightForeground,
    ) {
        val rendered = buildAnnotatedString {
            appendMarkdownInline(
                parts = MarkdownParser.parseInline(content),
                textColor = color,
                narrationColor = narrationColor,
                linkColor = linkColor,
                strongColor = strongColor,
            )
        }
        // 高亮作用于渲染后的可见范围，不能直接套用 Markdown 原文下标。
        rendered.withHighlightRanges(highlightedRanges, highlightForeground, highlightBackground)
    }
    Text(
        modifier = modifier,
        text = text,
        style = style,
        color = color,
    )
}

@Composable
private fun MarkdownCodeBlock(
    content: String,
    language: String?,
    color: Color,
    backgroundColor: Color,
    highlightedRanges: List<IntRange> = emptyList(),
) {
    val highlightBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val highlightForeground = MaterialTheme.colorScheme.primary
    val highlightedCode = remember(content, highlightedRanges, highlightBackground, highlightForeground) {
        // 代码块不解析行内语法，空内容仍保留占位高度。
        AnnotatedString(content.ifBlank { " " })
            .withHighlightRanges(highlightedRanges, highlightForeground, highlightBackground)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = BorderStroke(width = 0.5.dp, color = color.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!language.isNullOrBlank()) {
                Text(
                    text = language,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = color.copy(alpha = 0.70f),
                )
            }
            Text(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                text = highlightedCode,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp,
                ),
                color = color,
            )
        }
    }
}

/** 限制高亮范围到可见文本长度，正文与代码块共用相同样式。 */
private fun AnnotatedString.withHighlightRanges(
    ranges: List<IntRange>,
    foreground: Color,
    background: Color,
): AnnotatedString {
    if (ranges.isEmpty()) return this
    return buildAnnotatedString {
        append(this@withHighlightRanges)
        // 生成内容可能与上一帧布局不同，越界片段只裁剪，不影响正文渲染。
        ranges.forEach { range ->
            val start = range.first.coerceIn(minimumValue = 0, maximumValue = length)
            val end = (range.last + 1).coerceIn(start, length)
            if (start < end) addStyle(
                style = SpanStyle(
                    background = background,
                    color = foreground,
                    fontWeight = FontWeight.Medium,
                ),
                start = start,
                end = end,
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
    highlightedRanges: List<IntRange> = emptyList(),
    backgroundColor: Color,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = BorderStroke(width = 0.5.dp, color = color.copy(alpha = 0.08f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .heightIn(min = 22.dp)
                    .background(color.copy(alpha = 0.55f), CircleShape),
            )
            Spacer(modifier = Modifier.width(10.dp))
            MarkdownInlineText(
                modifier = Modifier.weight(1f),
                content = content,
                color = color,
                narrationColor = narrationColor,
                linkColor = linkColor,
                strongColor = strongColor,
                highlightedRanges = highlightedRanges,
                style = MaterialTheme.typography.bodyMedium,
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
    highlightedRangesByItem: List<List<IntRange>> = emptyList(),
    markerColor: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        block.items.forEachIndexed { itemIndex, item ->
            Row(
                modifier = Modifier.padding(start = (item.indentLevel * 14).dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    modifier = Modifier.width(24.dp),
                    text = item.marker,
                    style = MaterialTheme.typography.bodyMedium,
                    color = markerColor,
                )
                MarkdownInlineText(
                    modifier = Modifier.weight(1f),
                    content = item.content,
                    color = color,
                    narrationColor = narrationColor,
                    linkColor = linkColor,
                    strongColor = strongColor,
                    highlightedRanges = highlightedRangesByItem.getOrNull(itemIndex).orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
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
    strongColor: Color,
) {
    // 解析器已经完成语法判断，这里只把纯模型映射为 Compose 样式。
    parts.forEach { part ->
        when (part) {
            is MarkdownInline.Text -> append(part.content)
            is MarkdownInline.Code -> withStyle(
                style = SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = textColor.copy(alpha = 0.10f),
                ),
            ) {
                append(part.content)
            }

            is MarkdownInline.Strong -> withStyle(
                style = SpanStyle(
                    color = strongColor,
                    fontWeight = FontWeight.Bold,
                ),
            ) {
                appendMarkdownInline(part.content, textColor, narrationColor, linkColor, strongColor)
            }

            is MarkdownInline.Emphasis -> withStyle(
                style = SpanStyle(
                    fontStyle = FontStyle.Italic,
                    color = narrationColor,
                ),
            ) {
                appendMarkdownInline(part.content, textColor, narrationColor, linkColor, strongColor)
            }

            is MarkdownInline.Strikethrough -> withStyle(
                style = SpanStyle(textDecoration = TextDecoration.LineThrough),
            ) {
                appendMarkdownInline(part.content, textColor, narrationColor, linkColor, strongColor)
            }

            is MarkdownInline.Link -> withStyle(
                style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
            ) {
                appendMarkdownInline(part.content, textColor, narrationColor, linkColor, strongColor)
            }
        }
    }
}
