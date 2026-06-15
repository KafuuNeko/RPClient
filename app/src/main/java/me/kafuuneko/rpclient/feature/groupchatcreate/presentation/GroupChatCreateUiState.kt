package me.kafuuneko.rpclient.feature.groupchatcreate.presentation

import me.kafuuneko.rpclient.feature.groupchatcreate.model.GroupChatCreateCharacterItem
import me.kafuuneko.rpclient.feature.groupchatcreate.model.GroupChatCreateGreetingState
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatLorebookGroupItem
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession

/** 新建群聊页面状态树。 */
sealed class GroupChatCreateUiState {
    data object None : GroupChatCreateUiState()

    /** 群聊标题、候选成员、世界书和发言策略的可交互状态。 */
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
        val greetingState: GroupChatCreateGreetingState = GroupChatCreateGreetingState()
    ) : GroupChatCreateUiState() {
        /** 当前已选择加入群聊的角色数量。 */
        val selectedCount: Int
            get() = characters.count { it.selected }

        /** 当前成员数量和开场白配置是否允许提交。 */
        val canCreate: Boolean
            get() = selectedCount >= 2 && greetingState.canCreate
    }

    data object Finished : GroupChatCreateUiState()
}

/** 新建群聊页面的数据加载与创建状态。 */
sealed class GroupChatCreateLoadState {
    data object None : GroupChatCreateLoadState()
    data object Loading : GroupChatCreateLoadState()
    data object Creating : GroupChatCreateLoadState()
}
