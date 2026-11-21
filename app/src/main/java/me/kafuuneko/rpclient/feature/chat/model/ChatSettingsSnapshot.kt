package me.kafuuneko.rpclient.feature.chat.model

import me.kafuuneko.rpclient.feature.chat.presentation.ChatSettingsState

/**
 * 单聊设置显式保存字段的基线，不包含即时持久化的世界书配置。
 *
 * 摘要独立比对 savedSummary，生成或恢复摘要后无需重置其他字段的基线。
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
 * 按保存规则归一化快照；作者备注的空白表示继承角色默认值。
 * @param fallbackTitle 空白标题沿用的持久化标题。
 * @param inheritedCreatorNotes 清空作者备注时生效的角色默认值。
 * @return 用于实质变更比对的快照。
 */
internal fun ChatSettingsSnapshot.toComparableSnapshot(
    fallbackTitle: String,
    inheritedCreatorNotes: String,
): ChatSettingsSnapshot = copy(
    title = title.trim().ifBlank { fallbackTitle.trim() },
    userNote = userNote.trim(),
    creatorNotes = creatorNotes.ifBlank { inheritedCreatorNotes },
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
 * 判断作者备注是否发生实质变化，避免保存其他字段时将继承值固化为会话覆盖。
 * @return 有效备注相对加载基线不同时返回 true。
 */
internal fun ChatSettingsState.hasCreatorNotesChange(): Boolean {
    val baseline = savedSnapshot ?: return false
    return creatorNotesDraft.ifBlank { inheritedCreatorNotes } !=
        baseline.creatorNotes.ifBlank { inheritedCreatorNotes }
}

/** 判断摘要正文是否发生实质变化，忽略保存时会裁剪的首尾空白。 */
internal fun ChatSettingsState.hasSummaryChange(): Boolean =
    summaryDraft.trim() != savedSummary.trim()

/**
 * 按落库规则判断设置草稿是否需要保存，名称与人设描述保留原始空白。
 * @return 表单或摘要存在实质变更时返回 true。
 */
internal fun ChatSettingsState.hasUnsavedChanges(): Boolean {
    val baseline = savedSnapshot ?: return false
    val current = toSettingsSnapshot().toComparableSnapshot(
        fallbackTitle = baseline.title,
        inheritedCreatorNotes = inheritedCreatorNotes,
    )
    val saved = baseline.toComparableSnapshot(
        fallbackTitle = baseline.title,
        inheritedCreatorNotes = inheritedCreatorNotes,
    )
    return current != saved || hasSummaryChange()
}
