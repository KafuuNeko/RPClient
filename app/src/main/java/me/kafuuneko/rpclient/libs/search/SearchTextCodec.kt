package me.kafuuneko.rpclient.libs.search

import me.kafuuneko.rpclient.utils.MarkdownBlock
import me.kafuuneko.rpclient.utils.MarkdownInline
import me.kafuuneko.rpclient.utils.MarkdownParser
import java.text.BreakIterator
import java.text.Normalizer
import java.util.Locale

/**
 * 完整内容检索的字面编码器。
 * - 按 Unicode 码点编码，符号不会进入 MATCH 语法。
 * - 先投影、规范化再分块，重叠覆盖最长查询跨块的情况。
 * - 创建页保持既有 contains 规则，不使用本编码器。
 */
object SearchTextCodec {
    /** 编码、可见正文投影或分块规则变化时递增，工作器据此失效旧索引。 */
    const val FORMAT_VERSION = 1L
    const val MAX_QUERY_POINTS = 128
    const val BLOCK_POINTS = 4096
    private val mWhitespace = Regex("[\\s\\p{Z}]+")
    private val mThink = Regex("(?is)<think>.*?(?:</think>|$)")

    /** 生成本地检索副本，不改写用户输入或业务正文。 */
    fun normalize(text: String): String = mWhitespace.replace(
        input = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT),
        replacement = " ",
    ).trim()

    /** 把可见 Markdown 转成文字，保留代码和链接显示内容，排除思考块。 */
    fun project(text: String): String = MarkdownParser.parseBlocks(mThink.replace(text, ""))
        .joinToString("\n") { block ->
            when (block) {
                is MarkdownBlock.Code -> block.content
                is MarkdownBlock.Paragraph -> inlineText(block.content)
                is MarkdownBlock.Heading -> inlineText(block.content)
                is MarkdownBlock.Quote -> inlineText(block.content)
                is MarkdownBlock.ListBlock -> block.items.joinToString("\n") { markdownListItem ->
                    inlineText(
                        markdownListItem.content
                    )
                }

                MarkdownBlock.Divider -> "\n"
            }
        }

    /** 编码已规范化文字，每个码点对应一个不会与其它码点冲突的 token。 */
    fun tokens(normalized: String): String = buildString {
        // 使用码点而非 Char，避免 emoji 的代理对被分开编码。
        var offset = 0
        while (offset < normalized.length) {
            val point = normalized.codePointAt(offset)
            if (isNotEmpty()) append(' ')
            if (Character.isLetterOrDigit(point)) appendCodePoint(point)
            else append('x').append(point.toString(radix = 16))
            offset += Character.charCount(point)
        }
    }

    /**
     * 还原当前页的字符 token，不分配逐 token 的字符串列表。
     * @param encoded 由 tokens 生成的单个正文块
     * @return 与编码前完全一致的规范化文字，偏移仍采用 UTF-16
     * @throws IllegalArgumentException 编码损坏或不属于当前格式时拒绝静默漏字
     */
    fun decodeTokens(encoded: String): String = buildString {
        var start = 0
        // 每个单码点 token 都是字母或数字；其它字符使用规范的小写十六进制转义。
        while (start < encoded.length) {
            val end = encoded.indexOf(' ', start)
                .let { value -> if (value < 0) encoded.length else value }
            require(end > start) { "Empty search token" }
            val first = encoded.codePointAt(start)
            val point = if (start + Character.charCount(first) == end) {
                require(Character.isLetterOrDigit(first)) { "Invalid literal search token" }
                first
            } else {
                require(first == 'x'.code && end - start in 2..7) { "Invalid escaped search token" }
                require(end - start == 2 || encoded[start + 1] != '0') { "Noncanonical search token" }
                var value = 0
                for (offset in start + 1 until end) {
                    val char = encoded[offset]
                    val digit = when (char) {
                        in '0'..'9' -> char - '0'
                        in 'a'..'f' -> char - 'a' + 10
                        else -> throw IllegalArgumentException("Invalid search token digit")
                    }
                    value = value * 16 + digit
                }
                require(Character.isValidCodePoint(value) && !Character.isLetterOrDigit(value)) { "Invalid search token point" }
                value
            }
            appendCodePoint(point)
            require(end != encoded.length - 1) { "Trailing search token separator" }
            start = end + 1
        }
    }

    /** 返回绑定参数用的完整短语，原始引号、星号和 OR 均不具语法意义。 */
    fun phrase(normalized: String): String = "\"${tokens(normalized)}\""

    /** 延迟生成有重叠的完整正文块，position 使用规范化文本 UTF-16 偏移。 */
    fun chunks(normalized: String): Sequence<Pair<Int, String>> = sequence {
        var start = 0
        var remaining = normalized.codePointCount(beginIndex = 0, endIndex = normalized.length)
        // 每次只生成一块，避免同时分配整篇正文的 token 副本。
        while (start < normalized.length) {
            val end = normalized.offsetByCodePoints(start, minOf(BLOCK_POINTS, remaining))
            yield(start to normalized.substring(start, end))
            if (end == normalized.length) break
            start = normalized.offsetByCodePoints(end, -(MAX_QUERY_POINTS - 1))
            remaining -= BLOCK_POINTS - (MAX_QUERY_POINTS - 1)
        }
    }

    /**
     * 将可可靠匹配的原文片段映射为 UTF-16 区间，供故事编辑器定位。
     * Markdown 投影无法直接映射时返回 null，仍可打开正确章节，避免错误高亮。
     */
    fun literalRange(text: String, query: String): IntRange? {
        val needle = normalize(query)
        if (needle.isEmpty()) return null
        // 含格式标记时原文偏移与可见投影可能不同，宁可定位章节也不误选链接地址。
        val visible = mThink.replace(text, "")
        if (normalize(visible) != normalize(project(text))) return null
        val prefix = IntArray(needle.length)
        for (index in 1 until needle.length) {
            var matched = prefix[index - 1]
            while (matched > 0 && needle[index] != needle[matched]) matched = prefix[matched - 1]
            if (needle[index] == needle[matched]) matched++
            prefix[index] = matched
        }
        val starts = IntArray(needle.length)
        val hidden = mThink.findAll(text).iterator()
        var hiddenRange = if (hidden.hasNext()) hidden.next().range else null
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
        var start = iterator.first()
        var end = iterator.next()
        var matched = 0
        var emitted = 0
        var wasSpace = true
        // KMP 只保留最长查询对应的原始偏移，超长章节不建立逐字装箱的映射数组。
        while (end != BreakIterator.DONE) {
            while (hiddenRange != null && start > hiddenRange.last) {
                hiddenRange = if (hidden.hasNext()) hidden.next().range else null
            }
            if (hiddenRange == null || start !in hiddenRange) {
                val part = Normalizer.normalize(text.substring(start, end), Normalizer.Form.NFKC)
                    .lowercase(Locale.ROOT)
                for (value in part) {
                    val char = if (value.isWhitespace()) ' ' else value
                    if (char == ' ' && wasSpace) continue
                    wasSpace = char == ' '
                    starts[emitted % starts.size] = start
                    emitted++
                    while (matched > 0 && char != needle[matched]) matched = prefix[matched - 1]
                    if (char == needle[matched]) matched++
                    if (matched == needle.length) return starts[(emitted - needle.length) % starts.size] until end
                }
            }
            start = end
            end = iterator.next()
        }
        return null
    }

    private fun inlineText(text: String): String = flatten(MarkdownParser.parseInline(text))

    private fun flatten(parts: List<MarkdownInline>): String =
        parts.joinToString("") { markdownInline ->
            when (markdownInline) {
                is MarkdownInline.Text -> markdownInline.content
                is MarkdownInline.Code -> markdownInline.content
                is MarkdownInline.Strong -> flatten(markdownInline.content)
                is MarkdownInline.Emphasis -> flatten(markdownInline.content)
                is MarkdownInline.Strikethrough -> flatten(markdownInline.content)
                is MarkdownInline.Link -> flatten(markdownInline.content)
            }
        }
}
