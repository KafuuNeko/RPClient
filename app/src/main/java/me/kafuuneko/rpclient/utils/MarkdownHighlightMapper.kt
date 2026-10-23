package me.kafuuneko.rpclient.utils

/**
 * Markdown 块或列表项渲染后的可见文本范围。
 * [endExclusive] 与 Compose 文本范围一致，使用 UTF-16 偏移且不包含结束位置。
 */
internal data class MarkdownHighlightRange(
    val blockIndex: Int,
    val listItemIndex: Int?,
    val start: Int,
    val endExclusive: Int
)

/**
 * 将生成内容在原文中的修改映射到 [MarkdownParser] 输出的可见文本范围。
 *
 * 原文和修改片段采用相同的空白与块边界归一化，避免 Markdown 标记导致高亮偏移；
 * 跨块修改拆成多个可见范围。
 */
internal object MarkdownHighlightMapper {
    /**
     * 在可见文档中定位指定次数的命中，并拆分为各渲染节点的 UTF-16 范围。
     * @param occurrenceIndex 从零开始的非重叠匹配序号。
     * @return 不跨越块或列表项边界的高亮范围，未匹配时为空。
     */
    fun findRanges(
        blocks: List<MarkdownBlock>,
        highlightedText: String?,
        occurrenceIndex: Int = 0,
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

        // 合并相邻可见字符，跨块空白只用于匹配，不生成高亮范围。
        val output = mutableListOf<MarkdownHighlightRange>()
        var active: SourceLocation? = null
        var activeEnd = -1

        fun flush() {
            val start = active ?: return
            output += MarkdownHighlightRange(
                blockIndex = start.blockIndex,
                listItemIndex = start.listItemIndex,
                start = start.start,
                endExclusive = activeEnd,
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
            val continuesActiveRange = current != null
                && current.blockIndex == location.blockIndex
                && current.listItemIndex == location.listItemIndex
                && location.start == activeEnd
            if (continuesActiveRange) {
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
     * 统计修改位置之前的可见文本命中次数，确保格式化和未格式化的相同文本使用一致的定位规则。
     */
    fun countOccurrencesBefore(prefix: String, highlightedText: String): Int {
        if (highlightedText.isBlank()) return 0
        val needle = buildVisibleDocument(MarkdownParser.parseBlocks(highlightedText)).text
        if (needle.isBlank()) return 0

        // 与最终渲染共用归一化逻辑，Markdown 标记不参与出现次数。
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

    /** 保留归一化字符到原渲染节点的映射，连续空白折叠但不丢失来源范围。 */
    private fun buildVisibleDocument(blocks: List<MarkdownBlock>): VisibleDocument {
        val chunks = buildList {
            blocks.forEachIndexed { blockIndex, block ->
                when (block) {
                    is MarkdownBlock.Paragraph -> add(
                        element = VisibleChunk(
                            blockIndex = blockIndex,
                            listItemIndex = null,
                            text = visibleInlineText(block.content),
                        ),
                    )
                    is MarkdownBlock.Heading -> add(
                        element = VisibleChunk(
                            blockIndex = blockIndex,
                            listItemIndex = null,
                            text = visibleInlineText(block.content),
                        ),
                    )
                    is MarkdownBlock.Code -> add(
                        element = VisibleChunk(blockIndex = blockIndex, listItemIndex = null, text = block.content),
                    )
                    is MarkdownBlock.Quote -> add(
                        element = VisibleChunk(
                            blockIndex = blockIndex,
                            listItemIndex = null,
                            text = visibleInlineText(block.content),
                        ),
                    )
                    is MarkdownBlock.ListBlock -> block.items.forEachIndexed { itemIndex, item ->
                        add(
                            element = VisibleChunk(
                                blockIndex = blockIndex,
                                listItemIndex = itemIndex,
                                text = visibleInlineText(item.content),
                            ),
                        )
                    }
                    MarkdownBlock.Divider -> Unit
                }
            }
        }

        // 块边界以无来源空格连接，字符空白则保留所属节点与原始跨度。
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
                appendNormalizedSpace(location = null)
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
                        location = SourceLocation(
                            blockIndex = chunk.blockIndex,
                            listItemIndex = chunk.listItemIndex,
                            start = index,
                            endExclusive = end,
                        ),
                    )
                    index = end
                } else {
                    visible.append(character)
                    locations += SourceLocation(
                        blockIndex = chunk.blockIndex,
                        listItemIndex = chunk.listItemIndex,
                        start = index,
                        endExclusive = index + 1,
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
        // 只收集实际可见内容，忽略格式标记与链接地址。
        appendParts(MarkdownParser.parseInline(source))
    }
}
