package me.kafuuneko.rpclient.feature.groupchatcreate.presentation

import me.kafuuneko.rpclient.feature.groupchatcreate.model.GroupChatCreateCharacterItem
import me.kafuuneko.rpclient.feature.groupchatcreate.model.GroupChatCreateGreetingState
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatActivationStrategy
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatLorebookGroupItem

/** 新建群聊页面状态树。 */
sealed class GroupChatCreateUiState {
    data object None : GroupChatCreateUiState()

    /** 群聊标题、候选成员、世界书和发言策略的可交互状态。 */
    data class Normal(
        /** 当前页面数据库或资源操作的加载状态。 */
        val loadState: GroupChatCreateLoadState = GroupChatCreateLoadState.None,
        /** 供界面展示或持久化的标题。 */
        val title: String = "",
        /** 当前列表使用的搜索关键词。 */
        val searchQuery: String = "",
        /** 当前页面或流程可使用的角色列表。 */
        val characters: List<GroupChatCreateCharacterItem> = emptyList(),
        /** 按搜索条件过滤后实际展示的角色列表。 */
        val visibleCharacters: List<GroupChatCreateCharacterItem> = emptyList(),
        /** 按世界书分组后的条目列表。 */
        val lorebookGroups: List<GroupChatLorebookGroupItem> = emptyList(),
        /** 按搜索条件过滤后实际展示的世界书分组。 */
        val visibleLorebookGroups: List<GroupChatLorebookGroupItem> = lorebookGroups,
        /** 世界书列表当前使用的搜索关键词。 */
        val lorebookQuery: String = "",
        /** 当前已选中的世界书条目 ID 集合。 */
        val selectedLorebookEntryIds: Set<Long> = emptySet(),
        /** 当前场景采用的世界书激活策略。 */
        val activationStrategy: GroupChatActivationStrategy =
            GroupChatActivationStrategy.Natural,
        /** 群聊轮询时是否允许同一角色连续发言。 */
        val allowSelfResponses: Boolean = false,
        /** 群聊创建页的开场白选择状态。 */
        val greetingState: GroupChatCreateGreetingState = GroupChatCreateGreetingState()
    ) : GroupChatCreateUiState() {
        /** 当前已选择加入群聊的角色数量。 */
        val selectedCount: Int
            get() = characters.count { it.selected }

        /** 当前成员数量和开场白配置是否允许提交。 */
        val canCreate: Boolean
            get() = selectedCount >= 2 && greetingState.canCreate
    }

    data class Finished(val previous: GroupChatCreateUiState) : GroupChatCreateUiState()

    companion object {
        fun finished(previous: GroupChatCreateUiState): GroupChatCreateUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

/** 新建群聊页面的数据加载与创建状态。 */
sealed class GroupChatCreateLoadState {
    data object None : GroupChatCreateLoadState()
    data object Loading : GroupChatCreateLoadState()
    data object Creating : GroupChatCreateLoadState()
}
