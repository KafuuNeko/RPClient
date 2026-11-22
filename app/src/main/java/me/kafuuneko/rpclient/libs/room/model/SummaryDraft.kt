package me.kafuuneko.rpclient.libs.room.model

import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSummary

/** 摘要草稿依赖已被修改，由页面映射为可操作的本地化冲突提示。 */
class SummaryDraftConflictException : IllegalArgumentException("Summary draft inputs changed")

/** 将单聊持久化摘要转换为编辑器使用的正文与边界快照。 */
fun ChatMessage?.toSummaryVersion(): SummaryVersion = SummaryVersion(
    content = this?.content.orEmpty(),
    coveredMessageId = this?.coveredMessageId ?: 0L,
    id = this?.id,
)

/** 将群聊持久化摘要转换为编辑器使用的正文与边界快照。 */
fun GroupChatSummary?.toSummaryVersion(): SummaryVersion = SummaryVersion(
    content = this?.content.orEmpty(),
    coveredMessageId = this?.coveredMessageId ?: 0L,
    id = this?.id,
)

/** 摘要正文及其覆盖位置；空记录表示会话尚无摘要。 */
data class SummaryVersion(
    val content: String = "",
    val coveredMessageId: Long = 0L,
    val id: Long? = null,
) {
    /** 按保存规则比较正文与覆盖位置，快照身份不参与脏检测。 */
    fun hasSameMemory(other: SummaryVersion): Boolean =
        content.trim() == other.content.trim() && normalizedBoundary() == other.normalizedBoundary()

    /** 空摘要不覆盖任何消息，兼容旧数据中残留的覆盖位置。 */
    fun normalizedBoundary(): Long = if (content.isBlank()) 0L else coveredMessageId
}

/**
 * 一份可回退的摘要草稿，连同生成依赖和历史读取位置一起保存。
 *
 * - sources 保留实际参与生成的已保存摘要，提交时复核其仍然有效。
 * - historyCursor 限制历史读取范围，恢复旧稿后不能混入后来保存的摘要。
 */
data class SummaryDraftVersion(
    val summary: SummaryVersion = SummaryVersion(),
    val inputSnapshot: SummaryInputSnapshot? = null,
    val sources: List<SummaryVersion> = listOfNotNull(summary.takeIf { it.id != null }),
    val historyCursor: Long? = summary.id,
)

/** 手动摘要构建时使用的当前记忆及本次编辑的先前版本。 */
data class SummaryDraftContext(
    val current: SummaryDraftVersion,
    val previous: List<SummaryDraftVersion>,
    val baseline: SummaryVersion,
    /** 分批重做的固定末尾，防止生成期间新到的消息混入原摘要范围。 */
    val throughMessageId: Long? = null,
)

/** 设置保存事务提交的摘要及乐观并发校验信息。 */
data class SummaryDraftCommit(
    val summary: SummaryVersion,
    val baseline: SummaryVersion,
    val sources: List<SummaryVersion>,
    val inputSnapshot: SummaryInputSnapshot?,
)

/**
 * 合并增量摘要继承的输入，保证保存后续草稿时也校验前序生成依赖。
 * @param dependency 作为已有记忆参与本次生成的草稿输入。
 * @return 按首次出现顺序去重的完整输入快照。
 */
fun SummaryInputSnapshot.withDependency(dependency: SummaryInputSnapshot?): SummaryInputSnapshot {
    if (dependency == null) return this
    if (summaryVersion != dependency.summaryVersion) throw SummaryDraftConflictException()
    val combined = (dependency.messages + messages).groupBy { it.key }
    if (combined.values.any { versions -> versions.any { it != versions.first() } }) {
        throw SummaryDraftConflictException()
    }
    return copy(messages = combined.values.map { it.first() })
}
