package me.kafuuneko.rpclient.feature.groupchat.presentation

import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatGenerationState
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatAvailableCharacterItem
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatLorebookGroupItem
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatMemberItem
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatMessageItem
import me.kafuuneko.rpclient.libs.prompt.PromptInspection
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession

sealed class GroupChatUiState {
    data object None : GroupChatUiState()

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

sealed class GroupChatLoadState {
    data object None : GroupChatLoadState()
    data object Loading : GroupChatLoadState()
    data object Deleting : GroupChatLoadState()
    data object Saving : GroupChatLoadState()
    data object Summarizing : GroupChatLoadState()
}

sealed class GroupChatDialogState {
    data object None : GroupChatDialogState()
    data class PromptInspector(val inspection: PromptInspection) : GroupChatDialogState()
    data class DeleteSessionConfirm(val title: String) : GroupChatDialogState()
}

enum class GroupChatPage {
    Conversation,
    Settings
}
