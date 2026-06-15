package me.kafuuneko.rpclient.feature.groupchatcreate.presentation

import me.kafuuneko.rpclient.feature.groupchatcreate.model.GroupChatGreetingMode
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession

/** 新建群聊页面的成员、策略、世界书和提交意图。 */
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
    data class SelectGreetingMode(
        val mode: GroupChatGreetingMode
    ) : GroupChatCreateUiIntent()
    data class SelectGreetingCharacter(
        val characterId: Long
    ) : GroupChatCreateUiIntent()
    data class SelectGreeting(
        val greetingIndex: Int
    ) : GroupChatCreateUiIntent()
    data class ChangeCustomGreeting(
        val value: String
    ) : GroupChatCreateUiIntent()
    data class ToggleLorebook(val lorebookId: Long) : GroupChatCreateUiIntent()
    data class ToggleLorebookEntry(val entryId: Long) : GroupChatCreateUiIntent()
    data object Create : GroupChatCreateUiIntent()
}
