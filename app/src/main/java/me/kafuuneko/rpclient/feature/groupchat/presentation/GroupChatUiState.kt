package me.kafuuneko.rpclient.feature.groupchat.presentation

import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatGenerationState
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatAvailableCharacterItem
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatMemberItem
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatMessageItem
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatActivationStrategy
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatCharacterCardMode
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatLorebookGroupItem
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspection

/** 群聊页面状态树，统一承载会话、成员、消息、设置草稿和生成状态。 */
sealed class GroupChatUiState {
    /** 页面尚未完成会话参数解析与首次数据加载。 */
    data object None : GroupChatUiState()

    /**
     * 群聊页面稳定状态。
     *
     * 设置字段以 Draft 形式保留，只有 SaveSettings 意图才写回数据库；
     * [GroupChatConversationState.generationState] 独立描述模型生成，
     * 不与普通保存/删除加载状态混用。
     */
    data class Normal(
        val loadState: GroupChatLoadState = GroupChatLoadState.None,
        val sessionId: Long,
        val title: String,
        val members: List<GroupChatMemberItem>,
        val activeActivationStrategy: GroupChatActivationStrategy,
        val page: GroupChatPage = GroupChatPage.Conversation,
        val conversationState: GroupChatConversationState,
        val settingsState: GroupChatSettingsState,
        val hasPromptInspection: Boolean = false,
        val hasAvailableProvider: Boolean = true,
        val dialogState: GroupChatDialogState = GroupChatDialogState.None
    ) : GroupChatUiState()

    data class Finished(val previous: GroupChatUiState) : GroupChatUiState()

    companion object {
        fun finished(previous: GroupChatUiState): GroupChatUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

/** 更新未保存的设置草稿，同时保持会话当前正在使用的根状态字段不变。 */
internal fun GroupChatUiState.Normal.withSettingsDraft(
    transform: GroupChatSettingsState.() -> GroupChatSettingsState
): GroupChatUiState.Normal {
    return copy(settingsState = settingsState.transform())
}

/** 群聊消息、成员选择、输入与生成生命周期子状态。 */
data class GroupChatConversationState(
    val messages: List<GroupChatMessageItem>,
    val selectedSpeakerId: Long?,
    val inputDraft: String = "",
    val generationState: GroupChatGenerationState = GroupChatGenerationState.Idle,
    val expandedThinkBlockIds: Set<String> = emptySet(),
    val editingMessageId: Long? = null,
    val editingMessageDraft: String = ""
)

/** 群聊设置草稿、候选成员与世界书选择子状态。 */
data class GroupChatSettingsState(
    val activationStrategy: GroupChatActivationStrategy,
    val characterCardMode: GroupChatCharacterCardMode = GroupChatCharacterCardMode.Swap,
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
    val availableCharacters: List<GroupChatAvailableCharacterItem> = emptyList(),
    val lorebookGroups: List<GroupChatLorebookGroupItem> = emptyList(),
    val visibleLorebookGroups: List<GroupChatLorebookGroupItem> = lorebookGroups,
    val lorebookQuery: String = ""
)

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
    data class SessionLorebook(
        val query: String,
        val visibleGroups: List<GroupChatLorebookGroupItem>,
        val enabledEntryIds: Set<Long>
    ) : GroupChatDialogState()
    data class ModelSettingsGuide(
        val title: String,
        val message: String
    ) : GroupChatDialogState()
    data class PromptInspector(val inspection: PromptInspection) : GroupChatDialogState()
    data class DeleteMessageConfirm(val messageId: Long) : GroupChatDialogState()
    data class DeleteSessionConfirm(val title: String) : GroupChatDialogState()
}

/** 群聊页面当前展示的一级页面。 */
enum class GroupChatPage {
    Conversation,
    Settings
}
