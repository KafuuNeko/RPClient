package me.kafuuneko.rpclient.model

import me.kafuuneko.rpclient.libs.room.model.SummaryDraftCommit
import me.kafuuneko.rpclient.libs.room.model.SummaryDraftContext
import me.kafuuneko.rpclient.libs.room.model.SummaryDraftVersion
import me.kafuuneko.rpclient.libs.room.model.SummaryInputSnapshot
import me.kafuuneko.rpclient.libs.room.model.SummaryVersion

/**
 * 单聊与群聊共用的摘要编辑历史，所有手动操作均在显式保存前留在内存。
 *
 * - 连续输入只改 text；恢复先撤销这一轮输入，再逐份弹出生成版本。
 * - 历史版本同时携带覆盖位置与生成依赖，回退不会错误推进摘要进度。
 * - 已保存历史按需读取，本次中间稿在保存或放弃后释放。
 */
data class SummaryEditorState(
    val saved: SummaryVersion = SummaryVersion(),
    val current: SummaryDraftVersion = SummaryDraftVersion(saved),
    val text: String = current.summary.content,
    val previous: List<SummaryDraftVersion> = emptyList(),
    val latestMessageId: Long = 0L,
) {
    /** 正文编辑沿用当前覆盖位置；从空白开始手写摘要时覆盖现有会话。 */
    val effectiveSummary: SummaryVersion
        get() = current.summary.copy(
            content = text.trim(),
            coveredMessageId = when {
                text.isBlank() -> 0L
                current.summary.content.isNotBlank() -> current.summary.coveredMessageId
                else -> latestMessageId
            },
        )

    /** 重做按钮依据当前草稿覆盖位置，不能只读取持久化摘要。 */
    val canResummarize: Boolean
        get() = text.isNotBlank() && latestMessageId != 0L &&
            effectiveSummary.coveredMessageId == latestMessageId

    /** 内容相同而覆盖位置改变也必须保存；仅历史栈变化不构成未保存修改。 */
    val hasChanges: Boolean
        get() = !effectiveSummary.hasSameMemory(saved)

    /** 提取当前有效记忆与先前版本，供增量摘要和重新摘要选材。 */
    fun toGenerationContext(): SummaryDraftContext = SummaryDraftContext(
        current = current.copy(
            summary = effectiveSummary,
            inputSnapshot = current.inputSnapshot.takeIf { text.isNotBlank() },
            sources = current.sources.takeIf { text.isNotBlank() }.orEmpty(),
        ),
        // 从清空状态手写后重做，必须以清空稿为基础，不能让更早的记忆重新生效。
        previous = previous + if (!effectiveSummary.hasSameMemory(current.summary)) listOf(current) else emptyList(),
        baseline = saved,
    )

    /** 仅在记忆发生实质变化时生成保存命令，未改摘要不覆盖数据库新版本。 */
    fun toCommit(): SummaryDraftCommit? = if (hasChanges) SummaryDraftCommit(
        summary = effectiveSummary,
        baseline = saved,
        sources = current.sources.takeIf { text.isNotBlank() }.orEmpty(),
        inputSnapshot = current.inputSnapshot.takeIf { text.isNotBlank() },
    ) else null

    /**
     * 接受完整生成结果，并将被替换的正文及其手工编辑基线依次压入历史。
     * @param content 完整摘要正文。
     * @param coveredMessageId 本次请求实际覆盖的末条消息。
     * @param inputSnapshot 本次及前序增量生成的完整输入。
     * @param sources 实际参与生成的已保存摘要。
     * @return 包含新版本的编辑状态；相同记忆不会追加重复的回退步骤。
     */
    fun generated(
        content: String,
        coveredMessageId: Long,
        inputSnapshot: SummaryInputSnapshot,
        sources: List<SummaryVersion>,
    ): SummaryEditorState {
        val generated = SummaryVersion(content = content, coveredMessageId = coveredMessageId)
        if (generated.hasSameMemory(effectiveSummary)) return this
        // 一轮手工改写只保留编辑前和编辑后的两份，不按击键记录历史。
        val edited = !effectiveSummary.hasSameMemory(current.summary)
        val history = previous + current + if (edited) {
            listOf(current.copy(summary = effectiveSummary))
        } else emptyList()
        return copy(
            current = SummaryDraftVersion(
                summary = generated,
                inputSnapshot = inputSnapshot,
                sources = sources,
                historyCursor = current.historyCursor,
            ),
            text = content,
            previous = history,
        )
    }

    /**
     * 优先恢复本轮手工编辑的基线，再恢复上一份内存版本。
     * @return 恢复后的状态；需要读取更早的持久化历史时返回 null。
     */
    fun restoreLocal(): SummaryEditorState? {
        if (!effectiveSummary.hasSameMemory(current.summary)) {
            return copy(text = current.summary.content)
        }
        val version = previous.lastOrNull() ?: return null
        return copy(
            current = version,
            text = version.summary.content,
            previous = previous.dropLast(1)
        )
    }

    /** 只读取历史稿进入草稿，不删除数据库中的当前摘要。 */
    fun restorePersisted(version: SummaryVersion): SummaryEditorState = copy(
        current = SummaryDraftVersion(version),
        text = version.content,
        previous = emptyList(),
    )
}
