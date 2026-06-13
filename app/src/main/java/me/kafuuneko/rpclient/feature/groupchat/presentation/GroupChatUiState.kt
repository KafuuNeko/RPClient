package me.kafuuneko.rpclient.feature.groupchat.presentation

import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatGenerationState
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatAvailableCharacterItem
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatLorebookGroupItem
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatMemberItem
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatMessageItem
import me.kafuuneko.rpclient.libs.prompt.PromptInspection
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession

/** 群聊页面状态树，统一承载会话、成员、消息、设置草稿和生成状态。 */
sealed class GroupChatUiState {
    /** 页面尚未完成会话参数解析与首次数据加载。 */
    data object None : GroupChatUiState()

    /**
     * 群聊页面稳定状态。
     *
     * 设置字段以 Draft 形式保留，只有 SaveSettings 意图才写回数据库；
     * [generationState] 独立描述模型生成，不与普通保存/删除加载状态混用。
     */
    data class Normal(
        val loadState: GroupChatLoadState = GroupChatLoadState.None,
        val sessionId: Long,
        val title: String,
        val page: GroupChatPage = GroupChatPage.Conversation,
        val activationStrategy: GroupChatSession.ActivationStrategy,
        val characterCardMode: GroupChatSession.CharacterCardMode =
            GroupChatSession.CharacterCardMode.Swap,
        val allowSelfResponses: Boolean = false,
        val includeMutedCards: Boolean = false,
        val autoModeEnabled: Boolean = false,
        val trimOtherSpeakers: Boolean = true,
        val scenarioDraft: String = "",
        val userNoteDraft: String = "",
        val summaryDraft: String = "",
        val autoSummaryPaused: Boolean = false,
        val systemPromptDraft: String = "",
        val groupNudgePromptDraft: String = "",
        val newGroupChatPromptDraft: String = "",
        val titleDraft: String = "",
        val members: List<GroupChatMemberItem>,
        val availableCharacters: List<GroupChatAvailableCharacterItem> = emptyList(),
        val lorebookGroups: List<GroupChatLorebookGroupItem> = emptyList(),
        val messages: List<GroupChatMessageItem>,
        val selectedSpeakerId: Long?,
        val inputDraft: String = "",
        val generationState: GroupChatGenerationState = GroupChatGenerationState.Idle,
        val hasPromptInspection: Boolean = false,
        val editingMessageId: Long? = null,
        val editingMessageDraft: String = "",
        val dialogState: GroupChatDialogState = GroupChatDialogState.None
    ) : GroupChatUiState()

    data object Finished : GroupChatUiState()
}

/** 群聊数据库操作和摘要操作的页面级加载状态。 */
sealed class GroupChatLoadState {
    data object None : GroupChatLoadState()
    data object Loading : GroupChatLoadState()
    data object Deleting : GroupChatLoadState()
    data object Saving : GroupChatLoadState()
    data object Summarizing : GroupChatLoadState()
}

/** 群聊页面互斥显示的业务对话框。 */
sealed class GroupChatDialogState {
    data object None : GroupChatDialogState()
    data class PromptInspector(val inspection: PromptInspection) : GroupChatDialogState()
    data class DeleteSessionConfirm(val title: String) : GroupChatDialogState()
}

/** 群聊页面当前展示的一级页面。 */
enum class GroupChatPage {
    Conversation,
    Settings
}
