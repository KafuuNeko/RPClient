package me.kafuuneko.rpclient.feature.groupchatcreate.presentation

import me.kafuuneko.rpclient.feature.groupchatcreate.model.GroupChatCreateCharacterItem
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatLorebookGroupItem
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession

sealed class GroupChatCreateUiState {
    data object None : GroupChatCreateUiState()

    data class Normal(
        val loadState: GroupChatCreateLoadState = GroupChatCreateLoadState.None,
        val title: String = "",
        val searchQuery: String = "",
        val characters: List<GroupChatCreateCharacterItem> = emptyList(),
        val visibleCharacters: List<GroupChatCreateCharacterItem> = emptyList(),
        val lorebookGroups: List<GroupChatLorebookGroupItem> = emptyList(),
        val selectedLorebookEntryIds: Set<Long> = emptySet(),
        val activationStrategy: GroupChatSession.ActivationStrategy =
            GroupChatSession.ActivationStrategy.Natural,
        val allowSelfResponses: Boolean = false,
        val useCharacterGreetings: Boolean = true
    ) : GroupChatCreateUiState() {
        val selectedCount: Int
            get() = characters.count { it.selected }
    }

    data object Finished : GroupChatCreateUiState()
}

sealed class GroupChatCreateLoadState {
    data object None : GroupChatCreateLoadState()
    data object Loading : GroupChatCreateLoadState()
    data object Creating : GroupChatCreateLoadState()
}
