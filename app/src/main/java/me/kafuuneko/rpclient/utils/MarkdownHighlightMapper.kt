package me.kafuuneko.rpclient.utils

/**
 * A range in the visible text of a rendered Markdown block (or one list item).
 * [endExclusive] follows the same UTF-16 indexing convention as Compose text ranges.
 */
internal data class MarkdownHighlightRange(
    val blockIndex: Int,
    val listItemIndex: Int?,
    val start: Int,
    val endExclusive: Int
)

/**
 * Maps a generated source edit to the visible-text ranges produced by [MarkdownParser].
 *
 * Both the source document and the selected edit are parsed first, then whitespace and
 * block boundaries are normalized in the same way. Markdown markers therefore do not
 * offset the highlight, and edits spanning multiple blocks yield multiple visible ranges.
 */
internal object MarkdownHighlightMapper {
    fun findRanges(
        blocks: List<MarkdownBlock>,
        highlightedText: String?,
        occurrenceIndex: Int = 0
    ): List<MarkdownHighlightRange> {
        if (highlightedText.isNullOrBlank()) return emptyList()

        val needle = buildVisibleDocument(MarkdownParser.parseBlocks(highlightedText)).text
        if (needle.isBlank()) return emptyList()

        val document = buildVisibleDocument(blocks)
        var searchFrom = 0
        var found = -1
        var remainingOccurrence = occurrenceIndex.coerceAtLeast(0)
        while (searchFrom <= document.text.length - needle.length) {
            val match = document.text.indexOf(needle, searchFrom)
            if (match < 0) return emptyList()
            if (remainingOccurrence == 0) {
                found = match
                break
            }
            remainingOccurrence -= 1
            searchFrom = match + needle.length
        }
        if (found < 0) return emptyList()

        val output = mutableListOf<MarkdownHighlightRange>()
        var active: SourceLocation? = null
        var activeEnd = -1

        fun flush() {
            val start = active ?: return
            output += MarkdownHighlightRange(
                blockIndex = start.blockIndex,
                listItemIndex = start.listItemIndex,
                start = start.start,
                endExclusive = activeEnd
            )
            active = null
            activeEnd = -1
        }

        for (position in found until (found + needle.length)) {
            val location = document.locations[position]
            if (location == null) {
                flush()
                continue
            }
            val current = active
            if (
                current != null &&
                current.blockIndex == location.blockIndex &&
                current.listItemIndex == location.listItemIndex &&
                location.start == activeEnd
            ) {
                activeEnd = location.endExclusive
            } else {
                flush()
                active = location
                activeEnd = location.endExclusive
            }
        }
        flush()
        return output
    }

    /**
     * Counts visible occurrences in the text preceding the edited range. This intentionally
     * differs from searching raw Markdown: a formatted and an unformatted occurrence should
     * share the same rendered coordinates.
     */
    fun countOccurrencesBefore(prefix: String, highlightedText: String): Int {
        if (highlightedText.isBlank()) return 0
        val needle = buildVisibleDocument(MarkdownParser.parseBlocks(highlightedText)).text
        if (needle.isBlank()) return 0

        val haystack = buildVisibleDocument(MarkdownParser.parseBlocks(prefix)).text
        var from = 0
        var count = 0
        while (from <= haystack.length - needle.length) {
            val match = haystack.indexOf(needle, from)
            if (match < 0) break
            count += 1
            from = match + needle.length
        }
        return count
    }

    private data class VisibleChunk(
        val blockIndex: Int,
        val listItemIndex: Int?,
        val text: String
    )

    private data class SourceLocation(
        val blockIndex: Int,
        val listItemIndex: Int?,
        val start: Int,
        val endExclusive: Int
    )

    private data class VisibleDocument(
        val text: String,
        val locations: List<SourceLocation?>
    )

    private fun buildVisibleDocument(blocks: List<MarkdownBlock>): VisibleDocument {
        val chunks = buildList {
            blocks.forEachIndexed { blockIndex, block ->
                when (block) {
                    is MarkdownBlock.Paragraph -> add(
                        VisibleChunk(blockIndex, null, visibleInlineText(block.content))
                    )
                    is MarkdownBlock.Heading -> add(
                        VisibleChunk(blockIndex, null, visibleInlineText(block.content))
                    )
                    is MarkdownBlock.Code -> add(
                        VisibleChunk(blockIndex, null, block.content)
                    )
                    is MarkdownBlock.Quote -> add(
                        VisibleChunk(blockIndex, null, visibleInlineText(block.content))
                    )
                    is MarkdownBlock.ListBlock -> block.items.forEachIndexed { itemIndex, item ->
                        add(VisibleChunk(blockIndex, itemIndex, visibleInlineText(item.content)))
                    }
                    MarkdownBlock.Divider -> Unit
                }
            }
        }

        val visible = StringBuilder()
        val locations = mutableListOf<SourceLocation?>()

        fun appendNormalizedSpace(location: SourceLocation?) {
            if (visible.isNotEmpty() && visible.last() != ' ') {
                visible.append(' ')
                locations += location
            }
        }

        chunks.forEach { chunk ->
            if (chunk.text.isBlank()) return@forEach
            if (visible.isNotEmpty() && visible.last() != ' ') {
                appendNormalizedSpace(null)
            }

            var index = 0
            while (index < chunk.text.length) {
                val character = chunk.text[index]
                if (character.isWhitespace()) {
                    var end = index + 1
                    while (end < chunk.text.length && chunk.text[end].isWhitespace()) {
                        end += 1
                    }
                    appendNormalizedSpace(
                        SourceLocation(
                            blockIndex = chunk.blockIndex,
                            listItemIndex = chunk.listItemIndex,
                            start = index,
                            endExclusive = end
                        )
                    )
                    index = end
                } else {
                    visible.append(character)
                    locations += SourceLocation(
                        blockIndex = chunk.blockIndex,
                        listItemIndex = chunk.listItemIndex,
                        start = index,
                        endExclusive = index + 1
                    )
                    index += 1
                }
            }
        }

        if (visible.isNotEmpty() && visible.last() == ' ') {
            visible.deleteCharAt(visible.lastIndex)
            locations.removeAt(locations.lastIndex)
        }
        return VisibleDocument(visible.toString(), locations)
    }

    private fun visibleInlineText(source: String): String = buildString {
        fun appendParts(parts: List<MarkdownInline>) {
            parts.forEach { part ->
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
        appendParts(MarkdownParser.parseInline(source))
    }
}
