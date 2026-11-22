package me.kafuuneko.rpclient.feature.chat.model

import me.kafuuneko.rpclient.feature.chat.presentation.ChatSettingsState

/**
 * 单聊设置显式保存字段的基线，不包含即时持久化的世界书配置。
 *
 * 摘要独立比较正文与覆盖位置，生成或恢复草稿不会重置其他字段的基线。
 */
data class ChatSettingsSnapshot(
    val title: String,
    val userNote: String,
    val userPersonaId: Long,
    val userName: String,
    val userDescription: String,
    val creatorNotes: String,
    val includeCharacterExamples: Boolean,
    val autoSummaryPaused: Boolean,
)

/**
 * 按保存规则归一化快照，保留作者备注覆盖与继承的区别。
 * @param fallbackTitle 空白标题沿用的持久化标题。
 * @return 用于实质变更比对的快照。
 */
internal fun ChatSettingsSnapshot.toComparableSnapshot(
    fallbackTitle: String,
): ChatSettingsSnapshot = copy(
    title = title.trim().ifBlank { fallbackTitle.trim() },
    userNote = userNote.trim(),
    creatorNotes = creatorNotes.ifBlank { "" },
)

/** 提取当前表单中随保存按钮一起提交的字段。 */
internal fun ChatSettingsState.toSettingsSnapshot(): ChatSettingsSnapshot = ChatSettingsSnapshot(
    title = titleDraft,
    userNote = userNoteDraft,
    userPersonaId = persona.selectedId,
    userName = userNameDraft,
    userDescription = userDescriptionDraft,
    creatorNotes = creatorNotesDraft,
    includeCharacterExamples = includeCharacterExamples,
    autoSummaryPaused = autoSummaryPaused,
)

/** 将加载或保存完成后的表单记录为新的持久化基线。 */
internal fun ChatSettingsState.withCurrentAsSavedSnapshot(): ChatSettingsState =
    copy(savedSnapshot = toSettingsSnapshot())

/**
 * 比较原始备注覆盖，清空后即使有效正文相同也要恢复继承。
 * @return 覆盖正文或继承状态相对加载基线不同时返回 true。
 */
internal fun ChatSettingsState.hasCreatorNotesChange(): Boolean {
    val baseline = savedSnapshot ?: return false
    return creatorNotesDraft.ifBlank { "" } != baseline.creatorNotes.ifBlank { "" }
}

/** 判断摘要正文或覆盖位置是否变化，正文忽略保存时会裁剪的首尾空白。 */
internal fun ChatSettingsState.hasSummaryChange(): Boolean =
    summary.hasChanges

/**
 * 按落库规则判断设置草稿是否需要保存，名称与人设描述保留原始空白。
 * @return 表单或摘要存在实质变更时返回 true。
 */
internal fun ChatSettingsState.hasUnsavedChanges(): Boolean {
    val baseline = savedSnapshot ?: return false
    val current = toSettingsSnapshot().toComparableSnapshot(
        fallbackTitle = baseline.title,
    )
    val saved = baseline.toComparableSnapshot(
        fallbackTitle = baseline.title,
    )
    return current != saved || hasSummaryChange()
}
