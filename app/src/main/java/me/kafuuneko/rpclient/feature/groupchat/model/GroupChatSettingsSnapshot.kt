package me.kafuuneko.rpclient.feature.groupchat.model

import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatSettingsState
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatActivationStrategy
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatCharacterCardMode

/**
 * 群聊设置表单的持久化字段快照，用于与当前草稿比对是否存在未保存修改。
 *
 * - 仅包含点击保存后写入会话的表单字段，不包含成员与世界书等即时持久化的配置。
 * - 摘要独立比较正文与覆盖位置，手动生成和恢复同样参与未保存检测。
 */
data class GroupChatSettingsSnapshot(
    val title: String,
    val scenario: String,
    val userPersonaId: Long,
    val userName: String,
    val userDescription: String,
    val userNote: String,
    val activationStrategy: GroupChatActivationStrategy,
    val characterCardMode: GroupChatCharacterCardMode,
    val allowSelfResponses: Boolean,
    val includeMutedCards: Boolean,
    val includeCharacterExamples: Boolean,
    val autoModeEnabled: Boolean,
    val trimOtherSpeakers: Boolean,
    val autoSummaryPaused: Boolean,
    val systemPromptOverride: String,
    val groupNudgePromptOverride: String,
    val newGroupChatPromptOverride: String,
)

/**
 * 按保存落库规则标准化文本字段，避免无意义的首尾空白触发误提示。
 *
 * - 标题为空白时沿用持久化标题，与保存逻辑保持一致。
 * - 用户名称与人设描述保留原始文本，与保存时的非空覆盖规则一致。
 *
 * @param fallbackTitle 标题为空白时回退的已保存标题
 * @return 可直接用于差异比对的标准化快照
 */
internal fun GroupChatSettingsSnapshot.toComparableSnapshot(
    fallbackTitle: String,
): GroupChatSettingsSnapshot {
    val normalizedFallbackTitle = fallbackTitle.trim()
    return copy(
        title = title.trim().ifBlank { normalizedFallbackTitle },
        scenario = scenario.trim(),
        userNote = userNote.trim(),
        systemPromptOverride = systemPromptOverride.trim(),
        groupNudgePromptOverride = groupNudgePromptOverride.trim(),
        newGroupChatPromptOverride = newGroupChatPromptOverride.trim(),
    )
}

/**
 * 提取当前设置状态中参与显式保存的表单快照。
 *
 * @return 对应当前表单草稿的 [GroupChatSettingsSnapshot]
 */
internal fun GroupChatSettingsState.toSettingsSnapshot(): GroupChatSettingsSnapshot {
    // 提取点击保存时随会话一并写入的表单字段，忽略即时持久化的成员与世界书状态。
    return GroupChatSettingsSnapshot(
        title = titleDraft,
        scenario = scenarioDraft,
        userPersonaId = persona.selectedId,
        userName = userNameDraft,
        userDescription = userDescriptionDraft,
        userNote = userNoteDraft,
        activationStrategy = activationStrategy,
        characterCardMode = characterCardMode,
        allowSelfResponses = allowSelfResponses,
        includeMutedCards = includeMutedCards,
        includeCharacterExamples = includeCharacterExamples,
        autoModeEnabled = autoModeEnabled,
        trimOtherSpeakers = trimOtherSpeakers,
        autoSummaryPaused = autoSummaryPaused,
        systemPromptOverride = systemPromptDraft,
        groupNudgePromptOverride = groupNudgePromptDraft,
        newGroupChatPromptOverride = newGroupChatPromptDraft,
    )
}

/**
 * 将当前设置表单记录为已持久化的基线快照。
 *
 * @return 已同步 [GroupChatSettingsState.savedSnapshot] 的设置状态
 */
internal fun GroupChatSettingsState.withCurrentAsSavedSnapshot(): GroupChatSettingsState {
    return copy(savedSnapshot = toSettingsSnapshot())
}

/**
 * 判断群聊设置草稿相对已持久化快照是否存在未保存修改。
 *
 * - 表单字段按保存落库规则归一化后与基线快照比对。
 * - 摘要字段同时比较正文与覆盖位置，回到加载时的记忆后不再提示保存。
 *
 * @return 存在会被保存写入的实质差异时返回 true
 */
internal fun GroupChatSettingsState.hasUnsavedChanges(): Boolean {
    val baseline = savedSnapshot ?: return false
    val currentComparable =
        toSettingsSnapshot().toComparableSnapshot(fallbackTitle = baseline.title)
    val baselineComparable = baseline.toComparableSnapshot(fallbackTitle = baseline.title)
    if (currentComparable != baselineComparable) return true
    return summary.hasChanges
}
