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
        /** 当前页面数据库或资源操作的加载状态。 */
        val loadState: GroupChatLoadState = GroupChatLoadState.None,
        /** 当前操作关联的会话 ID。 */
        val sessionId: Long,
        /** 供界面展示或持久化的标题。 */
        val title: String,
        /** 当前群聊包含的成员列表。 */
        val members: List<GroupChatMemberItem>,
        /** 本次生成实际采用的世界书激活策略。 */
        val activeActivationStrategy: GroupChatActivationStrategy,
        /** 当前页面正在展示的一级功能区域。 */
        val page: GroupChatPage = GroupChatPage.Conversation,
        /** 单聊消息列表、输入和生成生命周期状态。 */
        val conversationState: GroupChatConversationState,
        /** 当前页面设置区域的可渲染状态。 */
        val settingsState: GroupChatSettingsState,
        /** 当前会话是否存在可供查看的 Prompt 明细。 */
        val hasPromptInspection: Boolean = false,
        /** 当前是否存在可用于生成的模型配置。 */
        val hasAvailableProvider: Boolean = true,
        /** 当前页面互斥展示的对话框状态。 */
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
    /** 当前状态或请求包含的消息列表。 */
    val messages: List<GroupChatMessageItem>,
    /** 当前已加载窗口之前是否仍有更早消息。 */
    val canLoadOlderMessages: Boolean = false,
    /** 当前是否正在向列表头部加载更早消息。 */
    val isLoadingOlderMessages: Boolean = false,
    /** 当前手动选中的群聊发言者角色 ID。 */
    val selectedSpeakerId: Long?,
    /** 消息输入框中尚未发送的草稿。 */
    val inputDraft: String = "",
    /** 当前模型生成任务的生命周期状态。 */
    val generationState: GroupChatGenerationState = GroupChatGenerationState.Idle,
    /** 当前已展开显示的推理块 ID 集合。 */
    val expandedThinkBlockIds: Set<String> = emptySet(),
    /** 当前正在编辑的消息 ID；未编辑时为空。 */
    val editingMessageId: Long? = null,
    /** 当前消息编辑器中尚未保存的草稿。 */
    val editingMessageDraft: String = ""
)

/** 群聊设置草稿、候选成员与世界书选择子状态。 */
data class GroupChatSettingsState(
    /** 当前场景采用的世界书激活策略。 */
    val activationStrategy: GroupChatActivationStrategy,
    /** 群聊 Prompt 注入成员角色卡的方式。 */
    val characterCardMode: GroupChatCharacterCardMode = GroupChatCharacterCardMode.Swap,
    /** 群聊轮询时是否允许同一角色连续发言。 */
    val allowSelfResponses: Boolean = false,
    /** 构建群聊 Prompt 时是否包含被禁言成员的角色卡。 */
    val includeMutedCards: Boolean = false,
    /** 群聊是否启用自动选择发言者。 */
    val autoModeEnabled: Boolean = false,
    /** 群聊输出是否移除模型擅自生成的其他成员台词。 */
    val trimOtherSpeakers: Boolean = true,
    /** 群聊场景设定的未保存草稿。 */
    val scenarioDraft: String = "",
    /** 群聊用户备注的未保存草稿。 */
    val userNoteDraft: String = "",
    /** 群聊摘要的未保存草稿。 */
    val summaryDraft: String = "",
    /** 当前会话的自动摘要是否暂停。 */
    val autoSummaryPaused: Boolean = false,
    /** 群聊系统提示词的未保存草稿。 */
    val systemPromptDraft: String = "",
    /** 群聊推进提示词的未保存草稿。 */
    val groupNudgePromptDraft: String = "",
    /** 新建群聊提示词的未保存草稿。 */
    val newGroupChatPromptDraft: String = "",
    /** 群聊标题的未保存草稿。 */
    val titleDraft: String = "",
    /** 当前流程允许选择的角色列表。 */
    val availableCharacters: List<GroupChatAvailableCharacterItem> = emptyList(),
    /** 按世界书分组后的条目列表。 */
    val lorebookGroups: List<GroupChatLorebookGroupItem> = emptyList(),
    /** 按搜索条件过滤后实际展示的世界书分组。 */
    val visibleLorebookGroups: List<GroupChatLorebookGroupItem> = lorebookGroups,
    /** 世界书列表当前使用的搜索关键词。 */
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
