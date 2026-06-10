package me.kafuuneko.rpclient.feature.groupchatcreate.presentation

import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession

sealed class GroupChatCreateUiIntent {
    data object Init : GroupChatCreateUiIntent()
    data object Back : GroupChatCreateUiIntent()
    data class ChangeTitle(val value: String) : GroupChatCreateUiIntent()
    data class ChangeSearchQuery(val value: String) : GroupChatCreateUiIntent()
    data class ToggleCharacter(val characterId: Long) : GroupChatCreateUiIntent()
    data class SelectStrategy(
        val strategy: GroupChatSession.ActivationStrategy
    ) : GroupChatCreateUiIntent()
    data class ToggleAllowSelfResponses(val enabled: Boolean) : GroupChatCreateUiIntent()
    data class ToggleCharacterGreetings(val enabled: Boolean) : GroupChatCreateUiIntent()
    data class ToggleLorebook(val lorebookId: Long) : GroupChatCreateUiIntent()
    data class ToggleLorebookEntry(val entryId: Long) : GroupChatCreateUiIntent()
    data object Create : GroupChatCreateUiIntent()
}
