package me.kafuuneko.rpclient.libs.story

import me.kafuuneko.rpclient.libs.prompt.PromptTokenizer

/** 一段可独立预算和追踪来源的连续正文上下文。 */
data class StoryContextChunk(
    /** 当前对象承载的正文内容。 */
    val content: String,
    /** 当前区间的起始位置，包含该位置。 */
    val start: Int,
    /** 当前区间的结束位置，不包含该位置。 */
    val end: Int,
    /** 故事片段与当前续写位置之间的段落距离。 */
    val distance: Int,
    /** 当前故事片段是否必须保留在最终上下文中。 */
    val required: Boolean
)

/** 正文裁剪结果及其世界书、角色激活扫描文本。 */
data class StoryContextSelection(
    /** 用于判断故事角色是否激活的扫描文本。 */
    val activationScanText: String,
    /** 用于激活故事世界书条目的扫描文本。 */
    val worldBookScanText: String,
    /** 按段落和 Token 预算切分后的故事上下文片段。 */
    val chunks: List<StoryContextChunk>,
    /** 因上下文预算不足而省略的故事片段数量。 */
    val omittedChunkCount: Int = 0,
    /** 因上下文预算不足而省略的 Token 估算总数。 */
    val omittedTokenCount: Int = 0
)

/** 将连续正文临时拆成邻近目标的 Prompt 块，不改变正文持久化结构。 */
class StoryContextSelector {
    /**
     * 根据当前编辑光标位置与 Prompt 预算，从故事正文中选取最优邻近段落分块。
     *
     * 处理步骤：
     * - 校验编辑目标位于正文末尾；
     * - 将正文解析为段落范围列表；
     * - 计算各段落距编辑目标的物理距离并排序；
     * - 提取用于世界书和角色激活判定的邻近扫描文本（前 N 个段落）；
     * - 将段落按 Token 预算切分为细粒度候选 Chunk；
     * - 从距目标最近的 Chunk 开始贪婪纳入预算，超出部分记录遗漏。
     */
    fun select(
        content: String,
        target: StoryEditTarget,
        authorNote: String,
        tokenizer: PromptTokenizer,
        promptBudget: Int,
        continuationGuidance: String = ""
    ): StoryContextSelection {
        require(target.end == content.length) {
            "Story continuation target must be at the end of the document"
        }
        // 解析正文全量段落范围
        val paragraphs = paragraphRanges(content)
        // 计算各段落与编辑目标的物理距离
        val neighboring = paragraphs
            .map { range ->
                val distance = target.start - range.end
                range to distance
            }
            .sortedWith(compareBy<Pair<TextRange, Int>> { it.second }.thenBy { it.first.start })

        // 提取邻近段落文本用于世界书与角色激活匹配
        val activationRanges = neighboring.take(ACTIVATION_PARAGRAPH_COUNT).map { it.first }
        val documentScanText = buildList {
            activationRanges.sortedBy { it.start }.forEach { add(content.substring(it.start, it.end)) }
        }.joinToString("\n\n")
        val worldBookScanText = buildList {
            documentScanText.takeIf(String::isNotBlank)?.let(::add)
            continuationGuidance.takeIf(String::isNotBlank)?.let(::add)
        }.joinToString("\n\n")
        val activationScanText = buildList {
            worldBookScanText.takeIf(String::isNotBlank)?.let(::add)
            authorNote.takeIf(String::isNotBlank)?.let(::add)
        }.joinToString("\n\n")

        // 将段落按 Token 粒度拆解为候选上下文块
        val maxChunkTokens = (promptBudget / 4).coerceIn(MIN_CHUNK_TOKENS, MAX_CHUNK_TOKENS)
        val chunks = neighboring.flatMap { (range, _) ->
            splitRange(content, range, tokenizer, maxChunkTokens).map { chunk ->
                CandidateChunk(
                    range = chunk,
                    distance = (target.start - chunk.end).coerceAtLeast(0),
                    tokenCount = tokenizer.countText(content.substring(chunk.start, chunk.end))
                )
            }
        }.sortedWith(compareBy<CandidateChunk> { it.distance }.thenBy { it.range.start })

        // 贪婪选择符合预算的邻近 Chunk
        val selected = mutableListOf<CandidateChunk>()
        val omitted = mutableListOf<CandidateChunk>()
        var usedTokens = 0
        chunks.forEachIndexed { index, chunk ->
            if (
                index == 0 || (
                    selected.size < MAX_SELECTED_CHUNKS &&
                        usedTokens + chunk.tokenCount <= promptBudget.coerceAtLeast(1)
                    )
            ) {
                selected += chunk
                usedTokens += chunk.tokenCount
            } else {
                omitted += chunk
            }
        }
        val nearest = selected.firstOrNull()

        // 构造正文上下文选择结果（按正文原生顺序重排）
        return StoryContextSelection(
            activationScanText = activationScanText,
            worldBookScanText = worldBookScanText,
            chunks = selected
                .map { candidate ->
                    val range = candidate.range
                    StoryContextChunk(
                        content = content.substring(range.start, range.end),
                        start = range.start,
                        end = range.end,
                        distance = candidate.distance,
                        required = range == nearest?.range
                    )
                }
                .sortedBy { it.start },
            omittedChunkCount = omitted.size,
            omittedTokenCount = omitted.sumOf { it.tokenCount }
        )
    }

    /** 将连续文本按双换行符分割为段落文本区间列表。 */
    private fun paragraphRanges(content: String): List<TextRange> {
        if (content.isEmpty()) return emptyList()
        val result = mutableListOf<TextRange>()
        var start = 0
        PARAGRAPH_SEPARATOR.findAll(content).forEach { match ->
            val end = match.range.first
            if (end > start) result += TextRange(start, end)
            start = match.range.last + 1
        }
        if (start < content.length) result += TextRange(start, content.length)
        return result.filter { content.substring(it.start, it.end).isNotBlank() }
    }

    /** 将过长段落二分拆解为不超过 Token 上限的安全文本子区间。 */
    private fun splitRange(
        content: String,
        range: TextRange,
        tokenizer: PromptTokenizer,
        maxTokens: Int
    ): List<TextRange> {
        if (tokenizer.countText(content.substring(range.start, range.end)) <= maxTokens) {
            return listOf(range)
        }
        val result = mutableListOf<TextRange>()
        var start = range.start
        while (start < range.end) {
            var low = start + 1
            var high = range.end
            var best = low
            while (low <= high) {
                val middle = (low + high) ushr 1
                val safeMiddle = content.safeUtf16Boundary(middle, start, range.end)
                if (tokenizer.countText(content.substring(start, safeMiddle)) <= maxTokens) {
                    best = safeMiddle
                    low = middle + 1
                } else {
                    high = middle - 1
                }
            }
            val end = content.safeUtf16Boundary(best.coerceAtLeast(start + 1), start, range.end)
            result += TextRange(start, end)
            start = end
        }
        return result
    }

    /** 确保切分位置不落入 UTF-16 代理对中间。 */
    private fun String.safeUtf16Boundary(index: Int, minimum: Int, maximum: Int): Int {
        var safe = index.coerceIn(minimum + 1, maximum)
        if (safe in (minimum + 1)..<maximum && this[safe].isLowSurrogate() && this[safe - 1].isHighSurrogate()) {
            safe = if (safe - 1 > minimum) safe - 1 else (safe + 1).coerceAtMost(maximum)
        }
        return safe.coerceAtLeast(minimum + 1)
    }

    /** 文本起止索引范围。 */
    private data class TextRange(val start: Int, val end: Int)

    /** 候选正文切片模型。 */
    private data class CandidateChunk(
        /** 当前对象覆盖的有效区间。 */
        val range: TextRange,
        /** 故事片段与当前续写位置之间的段落距离。 */
        val distance: Int,
        /** 当前文本或 Prompt 项估算得到的 Token 数。 */
        val tokenCount: Int
    )

    private companion object {
        /** 段落分隔正则表达式（匹配连续换行）。 */
        val PARAGRAPH_SEPARATOR = Regex("(?:\\r?\\n){2,}")
        /** 纳入激活匹配的邻近段落数量。 */
        const val ACTIVATION_PARAGRAPH_COUNT = 6
        /** 单个 Chunk 最小 Token 数。 */
        const val MIN_CHUNK_TOKENS = 128
        /** 单个 Chunk 最大 Token 数。 */
        const val MAX_CHUNK_TOKENS = 768
        /** 最大允许纳入的 Chunk 数量上限。 */
        const val MAX_SELECTED_CHUNKS = 512
    }
}
