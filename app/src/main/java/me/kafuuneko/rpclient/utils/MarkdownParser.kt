package me.kafuuneko.rpclient.utils

/** Markdown 渲染器支持的块级语法模型，不依赖 Compose 类型。 */
internal sealed class MarkdownBlock {
    data class Paragraph(val content: String) : MarkdownBlock()
    data class Heading(val level: Int, val content: String) : MarkdownBlock()
    data class Code(val language: String?, val content: String) : MarkdownBlock()
    data class Quote(val content: String) : MarkdownBlock()
    data class ListBlock(val items: List<MarkdownListItem>) : MarkdownBlock()
    data object Divider : MarkdownBlock()
}

/** Markdown 渲染器支持的行内语法模型，不携带颜色、字体等界面信息。 */
internal sealed class MarkdownInline {
    data class Text(val content: String) : MarkdownInline()
    data class Code(val content: String) : MarkdownInline()
    data class Strong(val content: List<MarkdownInline>) : MarkdownInline()
    data class Emphasis(val content: List<MarkdownInline>) : MarkdownInline()
    data class Strikethrough(val content: List<MarkdownInline>) : MarkdownInline()
    data class Link(val content: List<MarkdownInline>) : MarkdownInline()
}

/** 行内列表项的标记与正文。 */
internal data class MarkdownListItem(
    val marker: String,
    val content: String,
    /** 每两个前导空格或等效制表符宽度表示一级嵌套。 */
    val indentLevel: Int = 0
)

/**
 * 轻量 Markdown 解析器。
 *
 * - 只负责把文本转换为稳定的块级和行内模型；
 * - 不依赖 Compose，因此可以在非 UI 层复用和单元测试；
 * - 未识别或未闭合的语法保留为普通文本，兼容流式输出中间态。
 */
internal object MarkdownParser {
    private val mEscapableMarkdownCharacters = setOf(
        '\\', '`', '*', '_', '{', '}', '[', ']', '(', ')', '#', '+', '-', '.', '!', '>', '|', '~'
    )
    private val mHeadingRegex = Regex("""^(#{1,6})\s+(.+)$""")
    private val mUnorderedListRegex = Regex("""^\s*[-*+]\s+(.+)$""")
    private val mOrderedListRegex = Regex("""^\s*(\d+)[.)]\s+(.+)$""")
    private val mDividerRegex = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")

    /** 顺序切分消息支持的块级 Markdown。 */
    fun parseBlocks(source: String): List<MarkdownBlock> {
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').lines()
        val blocks = mutableListOf<MarkdownBlock>()
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()
            // 空白行只分隔块，不产生空的展示模型。
            if (trimmed.isBlank()) {
                index += 1
                continue
            }
            if (line.trimStart().startsWith("```")) {
                // 代码围栏优先于其他块级语法，未闭合时消费到消息末尾。
                val parsed = parseCodeBlock(lines, index)
                blocks += parsed.block
                index = parsed.nextIndex
                continue
            }
            if (mDividerRegex.matches(line)) {
                blocks += MarkdownBlock.Divider
                index += 1
                continue
            }
            mHeadingRegex.matchEntire(trimmed)?.let { match ->
                blocks += MarkdownBlock.Heading(
                    level = match.groupValues[1].length,
                    content = match.groupValues[2].trim().trimEnd('#').trim()
                )
                index += 1
                continue
            }
            if (line.trimStart().startsWith(">")) {
                // 连续引用行合并为单个块，保留内部换行。
                val parsed = parseQuoteBlock(lines, index)
                blocks += parsed.block
                index = parsed.nextIndex
                continue
            }
            if (line.isListLine()) {
                // 连续列表项合并为列表块，标记在模型中保留。
                val parsed = parseListBlock(lines, index)
                blocks += parsed.block
                index = parsed.nextIndex
                continue
            }
            // 无法识别为特殊块的内容回退为普通段落。
            val parsed = parseParagraph(lines, index)
            blocks += parsed.block
            index = parsed.nextIndex
        }
        return blocks
    }

    /** 从左到右解析受支持的行内 Markdown，并保留可嵌套的样式结构。 */
    fun parseInline(source: String): List<MarkdownInline> {
        val parts = mutableListOf<MarkdownInline>()
        var index = 0
        var plainStart = 0

        fun flushPlain(end: Int) {
            if (plainStart < end) parts += MarkdownInline.Text(source.substring(plainStart, end))
        }

        while (index < source.length) {
            // 转义标记保留目标字符，不展示前导反斜杠。
            if (source[index] == '\\' && index + 1 < source.length &&
                source[index + 1] in mEscapableMarkdownCharacters
            ) {
                flushPlain(index)
                parts += MarkdownInline.Text(source.substring(index + 1, index + 2))
                index += 2
                plainStart = index
                continue
            }

            // 单词内部的下划线按原文展示，例如 file_name 和 user_id。
            if ((source.startsWith("**", index) || source.startsWith("__", index)) &&
                !canOpenDelimiter(source, index, source.substring(index, index + 2))
            ) {
                index += 2
                continue
            }
            if ((source[index] == '*' || source[index] == '_') &&
                !source.startsWith("**", index) && !source.startsWith("__", index) &&
                !canOpenDelimiter(source, index, source[index].toString())
            ) {
                index += 1
                continue
            }

            // 未闭合定界符保留为普通文本，兼容流式输出的中间状态。
            val parsed = when {
                source.startsWith("***", index) -> parseDelimited(source, index, "***") {
                    MarkdownInline.Strong(
                        listOf(MarkdownInline.Emphasis(parseInline(it)))
                    )
                }
                source.startsWith("___", index) -> parseDelimited(source, index, "___") {
                    MarkdownInline.Strong(
                        listOf(MarkdownInline.Emphasis(parseInline(it)))
                    )
                }
                source[index] == '`' -> {
                    val runLength = source.substring(index).takeWhile { it == '`' }.length
                    val delimiter = "`".repeat(runLength)
                    parseDelimited(source, index, delimiter) { MarkdownInline.Code(it) }
                }
                source.startsWith("**", index) -> parseDelimited(source, index, "**") {
                    MarkdownInline.Strong(parseInline(it))
                }
                source.startsWith("__", index) -> parseDelimited(source, index, "__") {
                    MarkdownInline.Strong(parseInline(it))
                }
                source.startsWith("~~", index) -> parseDelimited(source, index, "~~") {
                    MarkdownInline.Strikethrough(parseInline(it))
                }
                source[index] == '*' -> parseDelimited(source, index, "*") {
                    MarkdownInline.Emphasis(parseInline(it))
                }
                source[index] == '_' -> parseDelimited(source, index, "_") {
                    MarkdownInline.Emphasis(parseInline(it))
                }
                source[index] == '[' -> parseLink(source, index)
                else -> null
            }
            if (parsed == null) {
                index += 1
                continue
            }
            flushPlain(index)
            parts += parsed.part
            index = parsed.nextIndex
            plainStart = index
        }
        flushPlain(source.length)
        return parts
    }

    private fun parseCodeBlock(lines: List<String>, startIndex: Int): ParsedBlock {
        val fence = lines[startIndex].trimStart()
        val fenceLength = fence.takeWhile { it == '`' }.length.coerceAtLeast(3)
        val language = fence.drop(fenceLength).trim().takeIf { it.isNotBlank() }
        val codeLines = mutableListOf<String>()
        var index = startIndex + 1
        // 结束围栏长度不得短于开始围栏，且不能附带语言等信息。
        while (index < lines.size && !isClosingCodeFence(lines[index], fenceLength)) {
            codeLines += lines[index]
            index += 1
        }
        if (index < lines.size) index += 1
        return ParsedBlock(
            block = MarkdownBlock.Code(language = language, content = codeLines.joinToString("\n")),
            nextIndex = index
        )
    }

    private fun isClosingCodeFence(line: String, minimumFenceLength: Int): Boolean {
        val trimmed = line.trimStart(' ', '\t')
        val closingLength = trimmed.takeWhile { it == '`' }.length
        return closingLength >= minimumFenceLength && trimmed.drop(closingLength).isBlank()
    }

    private fun parseQuoteBlock(lines: List<String>, startIndex: Int): ParsedBlock {
        val quoteLines = mutableListOf<String>()
        var index = startIndex
        // 只合并相邻引用行，下一种块级语法交给外层扫描。
        while (index < lines.size && lines[index].trimStart().startsWith(">")) {
            quoteLines += lines[index].trimStart().removePrefix(">").trimStart()
            index += 1
        }
        return ParsedBlock(
            block = MarkdownBlock.Quote(quoteLines.joinToString("\n").trim()),
            nextIndex = index
        )
    }

    private fun parseListBlock(lines: List<String>, startIndex: Int): ParsedBlock {
        val items = mutableListOf<MarkdownListItem>()
        var index = startIndex
        // 保持原有列表顺序，并统一无序列表的展示标记。
        while (index < lines.size && lines[index].isListLine()) {
            val line = lines[index]
            var indentWidth = 0
            for (character in line) {
                when (character) {
                    ' ' -> indentWidth += 1
                    '\t' -> indentWidth += 4
                    else -> break
                }
            }
            val indentLevel = indentWidth / 2
            val ordered = mOrderedListRegex.matchEntire(line)
            val unordered = mUnorderedListRegex.matchEntire(line)
            if (ordered != null) {
                items += MarkdownListItem(
                    marker = "${ordered.groupValues[1]}.",
                    content = ordered.groupValues[2],
                    indentLevel = indentLevel
                )
            } else if (unordered != null) {
                items += MarkdownListItem(
                    marker = "-",
                    content = unordered.groupValues[1],
                    indentLevel = indentLevel
                )
            }
            index += 1
        }
        return ParsedBlock(MarkdownBlock.ListBlock(items), index)
    }

    private fun parseParagraph(lines: List<String>, startIndex: Int): ParsedBlock {
        val paragraphLines = mutableListOf<String>()
        var index = startIndex
        // 段落遇到下一个块级起点时停止，避免跨块吞并文本。
        while (index < lines.size && lines[index].trim().isNotBlank() &&
            !lines[index].startsMarkdownBlock()
        ) {
            paragraphLines += lines[index].trimEnd()
            index += 1
        }
        return ParsedBlock(
            block = MarkdownBlock.Paragraph(paragraphLines.joinToString("\n").trim()),
            nextIndex = index
        )
    }

    private fun parseDelimited(
        source: String,
        startIndex: Int,
        delimiter: String,
        wrap: (String) -> MarkdownInline
    ): ParsedInline? {
        val contentStart = startIndex + delimiter.length
        // 跳过转义符，未闭合的标记继续保留原文。
        var end = contentStart
        while (end <= source.length - delimiter.length) {
            if (source[end] == '\\') {
                end += 2
                continue
            }
            if (source.startsWith(delimiter, end) &&
                (delimiter.startsWith("`") || canCloseDelimiter(source, end, delimiter))
            ) {
                return ParsedInline(
                    part = wrap(source.substring(contentStart, end)),
                    nextIndex = end + delimiter.length
                )
            }
            end += 1
        }
        return null
    }

    private fun canOpenDelimiter(source: String, index: Int, delimiter: String): Boolean {
        val next = source.getOrNull(index + delimiter.length) ?: return false
        if (next.isWhitespace()) return false
        if (delimiter.contains('_')) {
            val previous = source.getOrNull(index - 1)
            if (previous?.isLetterOrDigit() == true && next.isLetterOrDigit()) return false
        }
        return true
    }

    private fun canCloseDelimiter(source: String, index: Int, delimiter: String): Boolean {
        val previous = source.getOrNull(index - 1) ?: return false
        if (previous.isWhitespace()) return false
        if (delimiter.contains('_')) {
            val next = source.getOrNull(index + delimiter.length)
            if (previous.isLetterOrDigit() && next?.isLetterOrDigit() == true) return false
        }
        return true
    }

    /** 平衡标签与 URL 内的括号，允许地址包含合法的括号片段。 */
    private fun parseLink(source: String, startIndex: Int): ParsedInline? {
        // 标签与 URL 分别计数，转义的括号不参与嵌套深度。
        var labelDepth = 0
        var labelEnd = -1
        var cursor = startIndex + 1
        while (cursor < source.length) {
            when {
                source[cursor] == '\\' -> cursor += 2
                source[cursor] == '[' -> {
                    labelDepth += 1
                    cursor += 1
                }
                source[cursor] == ']' && labelDepth == 0 -> {
                    labelEnd = cursor
                    break
                }
                source[cursor] == ']' -> {
                    labelDepth -= 1
                    cursor += 1
                }
                else -> cursor += 1
            }
        }
        val urlStart = labelEnd + 1
        if (labelEnd < 0 || urlStart >= source.length || source[urlStart] != '(') return null

        var nesting = 1
        var urlEnd = -1
        cursor = urlStart + 1
        while (cursor < source.length) {
            when {
                source[cursor] == '\\' -> cursor += 2
                source[cursor] == '(' -> {
                    nesting += 1
                    cursor += 1
                }
                source[cursor] == ')' -> {
                    nesting -= 1
                    if (nesting == 0) {
                        urlEnd = cursor
                        break
                    }
                    cursor += 1
                }
                source[cursor] == '\n' || source[cursor] == '\r' -> return null
                else -> cursor += 1
            }
        }
        if (urlEnd < 0) return null
        return ParsedInline(
            part = MarkdownInline.Link(
                parseInline(source.substring(startIndex + 1, labelEnd))
            ),
            nextIndex = urlEnd + 1
        )
    }

    private fun String.startsMarkdownBlock(): Boolean {
        val trimmedStart = trimStart()
        val trimmed = trim()
        return trimmedStart.startsWith("```") ||
            trimmedStart.startsWith(">") ||
            mDividerRegex.matches(this) ||
            mHeadingRegex.matches(trimmed) ||
            isListLine()
    }

    private fun String.isListLine(): Boolean {
        return mUnorderedListRegex.matches(this) || mOrderedListRegex.matches(this)
    }

    private data class ParsedBlock(
        val block: MarkdownBlock,
        val nextIndex: Int
    )

    private data class ParsedInline(
        val part: MarkdownInline,
        val nextIndex: Int
    )
}
