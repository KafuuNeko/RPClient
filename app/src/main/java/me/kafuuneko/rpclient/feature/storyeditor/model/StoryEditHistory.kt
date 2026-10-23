package me.kafuuneko.rpclient.feature.storyeditor.model

import me.kafuuneko.rpclient.libs.room.repository.StoryLorebookRuntimeState

/**
 * 保存当前编辑器会话中的正文修改历史。
 *
 * AI 结果与用户输入进入同一个 undo/redo 栈。相邻且时间接近的用户输入会合并为一次
 * 修改，使标记和撤销以输入片段为单位，而不是每个字符一条记录。
 * 容量限制避免长时间编辑时无限保留生成文本副本。
 */
internal class StoryEditHistory(
    private val mMaxEntries: Int = DEFAULT_MAX_ENTRIES
) {
    private val mUndoEntries = ArrayDeque<StoryUndoEntry>()
    private val mRedoEntries = ArrayDeque<StoryUndoEntry>()
    private var mLastManualEditAtMillis: Long? = null

    val canUndo: Boolean
        get() = mUndoEntries.isNotEmpty()

    val canRedo: Boolean
        get() = mRedoEntries.isNotEmpty()

    init {
        require(mMaxEntries > 0) { "Story edit history capacity must be positive" }
    }

    fun record(entry: StoryUndoEntry) {
        addUndoEntry(entry)
        mRedoEntries.clear()
        mLastManualEditAtMillis = null
    }

    fun nextUndo(): StoryUndoEntry? = mUndoEntries.lastOrNull()

    fun nextRedo(): StoryUndoEntry? = mRedoEntries.lastOrNull()

    fun confirmUndo(entry: StoryUndoEntry) {
        check(mUndoEntries.lastOrNull() == entry) { "Undo history changed before commit" }
        mUndoEntries.removeLast()
        mRedoEntries.addLast(entry)
        mLastManualEditAtMillis = null
    }

    fun confirmRedo(entry: StoryUndoEntry) {
        check(mRedoEntries.lastOrNull() == entry) { "Redo history changed before commit" }
        mRedoEntries.removeLast()
        mUndoEntries.addLast(entry)
        mLastManualEditAtMillis = null
    }

    /**
     * 世界书选择变化后将历史快照对齐到当前条目集合。
     *
     * 正文撤销不应反向修改用户的世界书选择：已取消的条目从旧快照移除，
     * 新增条目使用当前运行态补齐，已保留条目仍保留各次生成前后的时序状态。
     */
    fun rebaseWorldInfoStates(currentStates: List<StoryLorebookRuntimeState>) {
        fun rebase(states: List<StoryLorebookRuntimeState>): List<StoryLorebookRuntimeState> {
            val stateById = states.associateBy { it.lorebookEntryId }
            return currentStates.map { current ->
                stateById[current.lorebookEntryId] ?: current
            }
        }

        fun StoryUndoEntry.rebased(): StoryUndoEntry = copy(
            previousWorldInfoStates = rebase(previousWorldInfoStates),
            nextWorldInfoStates = rebase(nextWorldInfoStates),
        )

        val rebasedUndo = mUndoEntries.map { it.rebased() }
        val rebasedRedo = mRedoEntries.map { it.rebased() }
        mUndoEntries.clear()
        mUndoEntries.addAll(rebasedUndo)
        mRedoEntries.clear()
        mRedoEntries.addAll(rebasedRedo)
        mLastManualEditAtMillis = null
    }

    /**
     * 记录用户正文修改。连续修改同一片段时合并为一条历史，移动到其他位置编辑或
     * 超过合并时间窗口后创建新条目。
     */
    fun recordManualEdit(
        previousContent: String,
        currentContent: String,
        worldInfoStates: List<StoryLorebookRuntimeState>,
        worldInfoGenerationStep: Int,
        eventTimeMillis: Long = System.currentTimeMillis(),
    ) {
        if (previousContent == currentContent) return
        // 计算文本前后差异范围
        val change = StoryTextChange.between(previousContent, currentContent)
        val latest = mUndoEntries.lastOrNull()
        val lastManualEditAtMillis = mLastManualEditAtMillis
        // 判断是否符合连续手写编辑合并条件（同为用户操作、在时间窗口内且编辑范围重叠）
        val canMerge = latest?.source == StoryEditSource.User
            && lastManualEditAtMillis != null
            && eventTimeMillis - lastManualEditAtMillis in 0..MANUAL_EDIT_MERGE_MILLIS
            && latest.matches(previousContent)
            && change.touches(latest)
        // 合并上一次历史或基于上一次文本生成新历史记录
        val baseContent = if (canMerge) {
            val mergedEntry = requireNotNull(latest)
            mUndoEntries.removeLast()
            previousContent.replaceRange(
                startIndex = mergedEntry.start,
                endIndex = mergedEntry.start + mergedEntry.insertedText.length,
                replacement = mergedEntry.replacedText,
            )
        } else {
            previousContent
        }

        mRedoEntries.clear()
        if (baseContent == currentContent) {
            mLastManualEditAtMillis = null
            return
        }
        val combinedChange = StoryTextChange.between(baseContent, currentContent)
        addUndoEntry(
            entry = StoryUndoEntry(
                start = combinedChange.start,
                insertedText = currentContent.substring(
                    startIndex = combinedChange.start,
                    endIndex = combinedChange.currentEnd,
                ),
                replacedText = baseContent.substring(
                    startIndex = combinedChange.start,
                    endIndex = combinedChange.previousEnd,
                ),
                previousWorldInfoStates = worldInfoStates.toList(),
                previousWorldInfoGenerationStep = worldInfoGenerationStep,
                nextWorldInfoStates = worldInfoStates.toList(),
                nextWorldInfoGenerationStep = worldInfoGenerationStep,
                source = StoryEditSource.User,
            ),
        )
        mLastManualEditAtMillis = eventTimeMillis
    }

    fun clear() {
        mUndoEntries.clear()
        mRedoEntries.clear()
        mLastManualEditAtMillis = null
    }

    private fun addUndoEntry(entry: StoryUndoEntry) {
        if (mUndoEntries.size == mMaxEntries) mUndoEntries.removeFirst()
        mUndoEntries.addLast(entry)
    }

    companion object {
        /** 撤销栈最大容量。 */
        const val DEFAULT_MAX_ENTRIES = 50
        /** 用户连续手写输入合并为同一撤销项的最大时间窗口（毫秒）。 */
        const val MANUAL_EDIT_MERGE_MILLIS = 1_500L
    }
}

private data class StoryTextChange(
    val start: Int,
    val previousEnd: Int,
    val currentEnd: Int
) {
    fun touches(entry: StoryUndoEntry): Boolean {
        val entryEnd = entry.start + entry.insertedText.length
        return start <= entryEnd && previousEnd >= entry.start
    }

    companion object {
        fun between(previousContent: String, currentContent: String): StoryTextChange {
            val sharedLimit = minOf(previousContent.length, currentContent.length)
            var prefixLength = 0
            while (
                prefixLength < sharedLimit
                && previousContent[prefixLength] == currentContent[prefixLength]
            ) {
                prefixLength++
            }

            var suffixLength = 0
            while (
                suffixLength < previousContent.length - prefixLength
                && suffixLength < currentContent.length - prefixLength
                && previousContent[previousContent.lastIndex - suffixLength] ==
                currentContent[currentContent.lastIndex - suffixLength]
            ) {
                suffixLength++
            }

            return StoryTextChange(
                start = prefixLength,
                previousEnd = previousContent.length - suffixLength,
                currentEnd = currentContent.length - suffixLength,
            )
        }
    }
}

private fun StoryUndoEntry.matches(content: String): Boolean {
    val end = start + insertedText.length
    return start in 0..end
        && end <= content.length
        && content.substring(start, end) == insertedText
}
