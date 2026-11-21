package me.kafuuneko.rpclient.feature.groupchat.presentation

import androidx.compose.ui.graphics.ImageBitmap
import me.kafuuneko.rpclient.model.ModelSettingsGuideContent
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatAvailableCharacterItem
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatGenerationState
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatMemberItem
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatMessageItem
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatSettingsSnapshot
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatActivationStrategy
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatCharacterCardMode
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatLorebookGroupItem
import me.kafuuneko.rpclient.libs.media.MessageImageState
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspection
import me.kafuuneko.rpclient.libs.room.model.PersonaSelection
import me.kafuuneko.rpclient.model.MessageHistoryLoadState

/** 群聊页面状态树，统一承载会话、成员、消息、设置草稿和生成状态。 */
sealed class GroupChatUiState {
    /** 页面尚未接收初始化参数。 */
    data object None : GroupChatUiState()

    /** 会话参数已就绪，正在读取首次展示所需的数据。 */
    data object Loading : GroupChatUiState()

    /**
     * 群聊页面稳定状态。
     *
     * 设置字段以 Draft 形式保留，只有 SaveSettings 意图才写回数据库；
     * [GroupChatConversationState.generationState] 独立描述模型生成，
     * [dialogState] 统一表达设置保存、分支创建、会话删除与手动摘要的进度。
     */
    data class Normal(
        val imageState: MessageImageState = MessageImageState(),
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
    transform: GroupChatSettingsState.() -> GroupChatSettingsState,
): GroupChatUiState.Normal {
    return copy(settingsState = settingsState.transform())
}

/** 群聊消息、成员选择、输入与生成生命周期子状态。 */
data class GroupChatConversationState(
    /** 当前状态或请求包含的消息列表。 */
    val messages: List<GroupChatMessageItem>,
    /** 完整群聊历史中是否存在角色消息，不受当前分页窗口限制。 */
    val hasCharacterMessage: Boolean,
    /** 当前已加载窗口之前是否仍有更早消息。 */
    val canLoadOlderMessages: Boolean = false,
    /** 当前历史窗口的加载方向，同一时间只允许一个分页任务。 */
    val historyLoadState: MessageHistoryLoadState = MessageHistoryLoadState.None,
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
    val editingMessageDraft: String = "",
    /** 搜索历史位置；非空时禁止流式更新抢回底部。 */
    val searchAnchorMessageId: Long? = null,
    val canLoadNewerMessages: Boolean = false,
)

/** 群聊设置草稿、候选成员与世界书选择子状态。 */
data class GroupChatSettingsState(
    val persona: PersonaSelection = PersonaSelection(),
    /** 当前场景采用的世界书激活策略。 */
    val activationStrategy: GroupChatActivationStrategy,
    /** 群聊 Prompt 注入成员角色卡的方式。 */
    val characterCardMode: GroupChatCharacterCardMode = GroupChatCharacterCardMode.Swap,
    /** 群聊轮询时是否允许同一角色连续发言。 */
    val allowSelfResponses: Boolean = false,
    /** 构建群聊 Prompt 时是否包含被禁言成员的角色卡。 */
    val includeMutedCards: Boolean = false,
    /** 是否允许注入本轮参与 Prompt 的成员角色卡示例对话。 */
    val includeCharacterExamples: Boolean = true,
    /** 全局允许示例对话时才展示会话开关，隐藏不改变设置草稿。 */
    val showCharacterExamplesSwitch: Boolean = true,
    /** 群聊是否启用自动选择发言者。 */
    val autoModeEnabled: Boolean = false,
    /** 群聊输出是否移除模型擅自生成的其他成员台词。 */
    val trimOtherSpeakers: Boolean = true,
    /** 群聊场景设定的未保存草稿。 */
    val scenarioDraft: String = "",
    /** 群聊用户备注的未保存草稿。 */
    val userNoteDraft: String = "",
    val userNameDraft: String = "",
    val userDescriptionDraft: String = "",
    val inheritedDescription: String = "",
    /** 群聊摘要的未保存草稿。 */
    val summaryDraft: String = "",
    /** 表单上次同步到的摘要正文，用来判断用户是否实际编辑。 */
    val savedSummary: String = "",
    /** 当前摘要是否已覆盖群聊最新消息。 */
    val canResummarize: Boolean = false,
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
    /** 按世界书分组后的全部条目列表。 */
    val lorebookGroups: List<GroupChatLorebookGroupItem> = emptyList(),
    /** 当前群聊已启用的世界书分组列表，供设置页直接展示与操作。 */
    val enabledLorebookGroups: List<GroupChatLorebookGroupItem> = lorebookGroups.filter { it.enabledCount > 0 },
    /** 设置页可操作的回复来自完整历史，不受当前搜索窗口影响。 */
    val canContinueLatestReply: Boolean = false,
    val canRegenerateLatestReply: Boolean = false,
    /** 表单上次从持久化层同步的基线快照，用于判断返回时是否存在未保存修改。 */
    val savedSnapshot: GroupChatSettingsSnapshot? = null,
)

/** 群聊页面互斥显示的业务对话框。 */
sealed class GroupChatDialogState {
    data object Saving : GroupChatDialogState()

    /** 群聊设置存在未保存修改，等待用户选择保存、放弃或继续编辑。 */
    data object UnsavedSettingsChanges : GroupChatDialogState()

    /** 正在复制会话前缀，导航完成前保持互斥状态。 */
    data object Branching : GroupChatDialogState()

    data object Deleting : GroupChatDialogState()

    data object Summarizing : GroupChatDialogState()

    /** 摘要被输出额度截断，等待用户前往全局设置调整。 */
    data object SummaryTokenLimit : GroupChatDialogState()

    data object None : GroupChatDialogState()

    data class SessionLorebook(
        val query: String,
        val visibleGroups: List<GroupChatLorebookGroupItem>,
        val enabledEntryIds: Set<Long>,
        val showSelectedOnly: Boolean = false
    ) : GroupChatDialogState()

    /** 群聊设置页批量添加成员的可检索多选弹窗状态。 */
    data class AddMembers(
        val query: String,
        val showSelectedOnly: Boolean,
        val visibleCharacters: List<GroupChatAvailableCharacterItem>,
        val selectedCharacterIds: Set<Long>,
        /** 当前视口内已解码的候选角色头像。 */
        val characterAvatars: Map<Long, ImageBitmap> = emptyMap(),
    ) : GroupChatDialogState()

    /** 群聊设置页添加新世界书的可检索多选弹窗状态（仅展示当前未启用的世界书）。 */
    data class AddLorebooks(
        val candidateGroups: List<GroupChatLorebookGroupItem>,
        val query: String,
        val showSelectedOnly: Boolean,
        val visibleGroups: List<GroupChatLorebookGroupItem>,
        val selectedEntryIds: Set<Long>
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

/** 将公共模型配置引导转换为群聊页面的对话框状态。 */
internal fun ModelSettingsGuideContent.toGroupChatDialogState(): GroupChatDialogState.ModelSettingsGuide {
    return GroupChatDialogState.ModelSettingsGuide(title = title, message = message)
}
