package me.kafuuneko.rpclient.ui.message

import me.kafuuneko.rpclient.utils.MarkdownBlock
import me.kafuuneko.rpclient.utils.MarkdownHighlightMapper
import me.kafuuneko.rpclient.utils.MarkdownHighlightRange
import me.kafuuneko.rpclient.utils.MarkdownInline
import me.kafuuneko.rpclient.utils.MarkdownListItem
import me.kafuuneko.rpclient.utils.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownParserTest {
    private fun visibleInline(parts: List<MarkdownInline>): String = buildString {
        fun appendParts(items: List<MarkdownInline>) {
            items.forEach { part ->
                when (part) {
                    is MarkdownInline.Text -> append(part.content)
                    is MarkdownInline.Code -> append(part.content)
                    is MarkdownInline.Strong -> appendParts(part.content)
                    is MarkdownInline.Emphasis -> appendParts(part.content)
                    is MarkdownInline.Strikethrough -> appendParts(part.content)
                    is MarkdownInline.Link -> appendParts(part.content)
                }
            }
        }
        appendParts(parts)
    }

    @Test
    fun parseBlocksPreservesSupportedBlockTypes() {
        val blocks = MarkdownParser.parseBlocks(
            source = "# Heading\n\nParagraph\n```kotlin\nval answer = 42\n```\n> Quote\n- One\n2. Two\n---",
        )

        assertEquals(
            listOf(
                MarkdownBlock.Heading(level = 1, content = "Heading"),
                MarkdownBlock.Paragraph("Paragraph"),
                MarkdownBlock.Code("kotlin", "val answer = 42"),
                MarkdownBlock.Quote("Quote"),
                MarkdownBlock.ListBlock(
                    items = listOf(
                        MarkdownListItem("-", "One"),
                        MarkdownListItem("2.", "Two"),
                    ),
                ),
                MarkdownBlock.Divider,
            ),
            blocks,
        )
    }

    @Test
    fun unclosedCodeFenceConsumesTheRemainingMessage() {
        val blocks = MarkdownParser.parseBlocks("before\n```text\npartial")

        assertEquals(
            listOf(
                MarkdownBlock.Paragraph("before"),
                MarkdownBlock.Code("text", "partial"),
            ),
            blocks,
        )
    }

    @Test
    fun parseInlineKeepsNestedStylesAndLinksAsModels() {
        val parts = MarkdownParser.parseInline("before **bold _inner_** [link](https://example.com) `code`")

        assertEquals(
            listOf(
                MarkdownInline.Text("before "),
                MarkdownInline.Strong(
                    content = listOf(
                        MarkdownInline.Text("bold "),
                        MarkdownInline.Emphasis(listOf(MarkdownInline.Text("inner"))),
                    ),
                ),
                MarkdownInline.Text(" "),
                MarkdownInline.Link(listOf(MarkdownInline.Text("link"))),
                MarkdownInline.Text(" "),
                MarkdownInline.Code("code"),
            ),
            parts,
        )
    }

    @Test
    fun intrawordUnderscoresAndEscapedMarkersRemainLiteral() {
        assertEquals(
            "file_name and user_id with *literal* markers",
            visibleInline(parts = MarkdownParser.parseInline("file_name and user_id with \\*literal\\* markers")),
        )
    }

    @Test
    fun linksSupportParenthesesInUrls() {
        assertEquals(
            "Read the reference",
            visibleInline(
                parts = MarkdownParser.parseInline("[Read the reference](https://example.com/page_(section))"),
            ),
        )
    }

    @Test
    fun nestedListIndentationIsPreserved() {
        val blocks = MarkdownParser.parseBlocks("- Parent\n  - Child\n    - Grandchild")
        val list = blocks.single() as MarkdownBlock.ListBlock

        assertEquals(
            listOf(
                MarkdownListItem(marker = "-", content = "Parent", indentLevel = 0),
                MarkdownListItem(marker = "-", content = "Child", indentLevel = 1),
                MarkdownListItem(marker = "-", content = "Grandchild", indentLevel = 2),
            ),
            list.items,
        )
    }

    @Test
    fun longerCodeFenceIsNotClosedByShorterFence() {
        assertEquals(
            listOf(
                MarkdownBlock.Code("kotlin", "first\n```\nlast"),
                MarkdownBlock.Paragraph("After"),
            ),
            MarkdownParser.parseBlocks("````kotlin\nfirst\n```\nlast\n````\n\nAfter"),
        )
    }

    @Test
    fun highlightMapsMultilineEditsAcrossMarkdownBlocks() {
        val blocks = MarkdownParser.parseBlocks("Before\n\n**Edited first.**\n\n> Edited second.\n\n- Edited third.")
        val ranges = MarkdownHighlightMapper.findRanges(
            blocks = blocks,
            highlightedText = "**Edited first.**\n\n> Edited second.\n\n- Edited third.",
        )

        assertEquals(
            listOf(
                MarkdownHighlightRange(
                    blockIndex = 1,
                    listItemIndex = null,
                    start = 0,
                    endExclusive = "Edited first.".length,
                ),
                MarkdownHighlightRange(
                    blockIndex = 2,
                    listItemIndex = null,
                    start = 0,
                    endExclusive = "Edited second.".length,
                ),
                MarkdownHighlightRange(
                    blockIndex = 3,
                    listItemIndex = 0,
                    start = 0,
                    endExclusive = "Edited third.".length,
                ),
            ),
            ranges,
        )
    }

    @Test
    fun highlightOccurrenceCountsRenderedTextRatherThanRawMarkdown() {
        val source = "Hello\n\n**Hello**"
        val prefix = "Hello\n\n"
        val selectedText = "**Hello**"
        val occurrenceIndex = MarkdownHighlightMapper.countOccurrencesBefore(prefix, selectedText)
        val ranges = MarkdownHighlightMapper.findRanges(
            blocks = MarkdownParser.parseBlocks(source),
            highlightedText = selectedText,
            occurrenceIndex = occurrenceIndex,
        )

        assertEquals(1, occurrenceIndex)
        assertEquals(
            listOf(
                element = MarkdownHighlightRange(
                    blockIndex = 1,
                    listItemIndex = null,
                    start = 0,
                    endExclusive = "Hello".length,
                ),
            ),
            ranges,
        )
    }

    @Test
    fun unclosedInlineDelimiterRemainsPlainText() {
        assertEquals(
            listOf(MarkdownInline.Text("keep *the text")),
            MarkdownParser.parseInline("keep *the text"),
        )
    }
    @Test
    fun tripleAsterisksAndUnderscoresRenderAsBoldItalic() {
        assertEquals(
            listOf(
                element = MarkdownInline.Strong(
                    content = listOf(MarkdownInline.Emphasis(listOf(MarkdownInline.Text("xxx")))),
                ),
            ),
            MarkdownParser.parseInline("***xxx***"),
        )
        assertEquals(
            listOf(
                element = MarkdownInline.Strong(
                    content = listOf(MarkdownInline.Emphasis(listOf(MarkdownInline.Text("xxx")))),
                ),
            ),
            MarkdownParser.parseInline("___xxx___"),
        )
        assertEquals("xxx", visibleInline(MarkdownParser.parseInline("***xxx***")))
    }

}
