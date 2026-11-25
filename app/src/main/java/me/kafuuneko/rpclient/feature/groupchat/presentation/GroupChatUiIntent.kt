package me.kafuuneko.rpclient.feature.groupchat.presentation

import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatActivationStrategy
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatCharacterCardMode
import me.kafuuneko.rpclient.libs.media.MessageImageAction

/** 群聊页面可接收的全部用户意图和生命周期事件。 */
sealed class GroupChatUiIntent {
    data class ChangeUserName(val value: String) : GroupChatUiIntent()
    data class ChangeUserDescription(val value: String) : GroupChatUiIntent()
    data class SelectPersona(val id: Long) : GroupChatUiIntent()

    /** 从摘要额度提示前往全局设置。 */
    data object OpenSummarySettings : GroupChatUiIntent()

    /** 从生成失败弹窗重试当前生成批次。 */
    data object RetryGeneration : GroupChatUiIntent()

    data class ImageAction(val action: MessageImageAction) : GroupChatUiIntent()

    data class Init(val sessionId: String?, val searchMessageId: Long? = null) : GroupChatUiIntent()

    data object Resume : GroupChatUiIntent()

    data object LoadOlderMessages : GroupChatUiIntent()

    data object LoadNewerMessages : GroupChatUiIntent()

    data object ReturnToLatest : GroupChatUiIntent()

    /** 列表已经到达真实末尾，携带尾消息 ID 防止过期滚动事件误清除定位。 */
    data class ReachLatestMessage(val messageId: Long) : GroupChatUiIntent()

    data object Back : GroupChatUiIntent()

    data object OpenSettings : GroupChatUiIntent()

    data object ShowSessionLoreDialog : GroupChatUiIntent()

    data object OpenWorldBookManager : GroupChatUiIntent()

    data class ChangeSessionLorebookDialogQuery(val value: String) : GroupChatUiIntent()

    data class ToggleSessionLorebookDialogSelectedOnly(val selectedOnly: Boolean) :
        GroupChatUiIntent()

    data class ToggleSessionLorebookDialogEntry(val entryId: Long) : GroupChatUiIntent()

    data class ToggleSessionLorebookDialogGroup(val lorebookId: Long) : GroupChatUiIntent()

    data object ConfirmSessionLorebookSelection : GroupChatUiIntent()

    data object ShowAddMembersDialog : GroupChatUiIntent()

    data class ChangeAddMembersQuery(val value: String) : GroupChatUiIntent()

    data class VisibleAddMemberCharactersChanged(
        val characterIds: Set<Long>,
        val targetSizePx: Int,
    ) : GroupChatUiIntent()

    data class ToggleAddMembersSelectedOnly(val selectedOnly: Boolean) : GroupChatUiIntent()

    data class ToggleAddMemberSelection(val characterId: Long) : GroupChatUiIntent()

    data object ConfirmAddMembers : GroupChatUiIntent()

    data object ShowAddLorebooksDialog : GroupChatUiIntent()

    data class ChangeAddLorebooksQuery(val value: String) : GroupChatUiIntent()

    data class ToggleAddLorebooksSelectedOnly(val selectedOnly: Boolean) : GroupChatUiIntent()

    data class ToggleAddLorebooksGroup(val lorebookId: Long) : GroupChatUiIntent()

    data class ToggleAddLorebooksEntry(val entryId: Long) : GroupChatUiIntent()

    data object ConfirmAddLorebooks : GroupChatUiIntent()

    data object OpenPromptInspector : GroupChatUiIntent()

    data class CopyPromptItem(val text: String) : GroupChatUiIntent()

    data object CloseSettings : GroupChatUiIntent()

    /** 放弃群聊设置页中尚未保存的草稿并返回会话页。 */
    data object DiscardSettingsChanges : GroupChatUiIntent()

    data class ChangeInputDraft(val value: String) : GroupChatUiIntent()

    data class SelectSpeaker(val characterId: Long) : GroupChatUiIntent()

    data class ToggleMemberMuted(val characterId: Long) : GroupChatUiIntent()

    data object SendMessage : GroupChatUiIntent()

    data object ImpersonateUser : GroupChatUiIntent()

    data object ToggleWritingReasoning : GroupChatUiIntent()

    data object StopGeneration : GroupChatUiIntent()

    data object SummarizeNow : GroupChatUiIntent()

    data object CancelSummary : GroupChatUiIntent()

    data object RestorePreviousSummary : GroupChatUiIntent()

    data class ChangeTitle(val value: String) : GroupChatUiIntent()

    data class ChangeScenario(val value: String) : GroupChatUiIntent()

    data class ChangeUserNote(val value: String) : GroupChatUiIntent()

    data class ChangeSummary(val value: String) : GroupChatUiIntent()

    data class ToggleAutoSummaryPaused(val paused: Boolean) : GroupChatUiIntent()

    data class ChangeSystemPrompt(val value: String) : GroupChatUiIntent()

    data class ChangeGroupNudgePrompt(val value: String) : GroupChatUiIntent()

    data class ChangeNewGroupChatPrompt(val value: String) : GroupChatUiIntent()

    data object SaveSettings : GroupChatUiIntent()

    data class SelectActivationStrategy(
        val strategy: GroupChatActivationStrategy
    ) : GroupChatUiIntent()

    data class SelectCharacterCardMode(
        val mode: GroupChatCharacterCardMode
    ) : GroupChatUiIntent()

    data class ToggleIncludeMutedCards(val enabled: Boolean) : GroupChatUiIntent()

    data class ToggleIncludeCharacterExamples(val enabled: Boolean) : GroupChatUiIntent()

    data class ToggleAutoMode(val enabled: Boolean) : GroupChatUiIntent()

    data class ToggleTrimOtherSpeakers(val enabled: Boolean) : GroupChatUiIntent()

    data class ToggleAllowSelfResponses(val enabled: Boolean) : GroupChatUiIntent()

    data class ToggleLorebook(val lorebookId: Long) : GroupChatUiIntent()

    data class ToggleLorebookEntry(val entryId: Long) : GroupChatUiIntent()

    data class RemoveMember(val characterId: Long) : GroupChatUiIntent()

    data class MoveMember(val characterId: Long, val offset: Int) : GroupChatUiIntent()

    data class ReorderMember(
        val fromCharacterId: Long,
        val toCharacterId: Long
    ) : GroupChatUiIntent()

    data object CommitMemberOrder : GroupChatUiIntent()

    data class CopyMessage(val messageId: Long) : GroupChatUiIntent()

    /** 复制截至所选消息的历史，创建独立群聊。 */
    data class BranchFromMessage(val messageId: Long) : GroupChatUiIntent()

    data class ToggleThinkBlock(val blockId: String) : GroupChatUiIntent()

    data class StartEditMessage(val messageId: Long) : GroupChatUiIntent()

    data class ChangeEditingMessageDraft(val value: String) : GroupChatUiIntent()

    data object SaveEditingMessage : GroupChatUiIntent()

    data object CancelEditingMessage : GroupChatUiIntent()

    data class DeleteMessageClick(val messageId: Long) : GroupChatUiIntent()

    data object ConfirmDeleteMessage : GroupChatUiIntent()

    data class RegenerateMessage(val messageId: Long) : GroupChatUiIntent()

    data object RegenerateLast : GroupChatUiIntent()

    data object ContinueLast : GroupChatUiIntent()

    data object DeleteSessionClick : GroupChatUiIntent()

    data object ConfirmDeleteSession : GroupChatUiIntent()

    data object OpenProviderSettings : GroupChatUiIntent()

    data object DismissDialog : GroupChatUiIntent()
}
