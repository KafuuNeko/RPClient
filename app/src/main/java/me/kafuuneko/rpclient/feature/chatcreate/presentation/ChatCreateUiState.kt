package me.kafuuneko.rpclient.feature.chatcreate.presentation

import me.kafuuneko.rpclient.feature.chatcreate.model.ChatCreateForm
import me.kafuuneko.rpclient.feature.chatcreate.model.ChatCreateCharacterItem
import me.kafuuneko.rpclient.feature.chatcreate.model.ChatCreateLorebookGroupItem

/** 新建单聊页面状态树。 */
sealed class ChatCreateUiState {
    data object None : ChatCreateUiState()

    /** 表单、候选角色、角色开场白和世界书选择的稳定页面状态。 */
    data class Normal(
        /** 当前页面数据库或资源操作的加载状态。 */
        val loadState: ChatCreateLoadState = ChatCreateLoadState.None,
        /** 当前页面正在编辑的表单数据。 */
        val form: ChatCreateForm = ChatCreateForm(),
        /** 当前页面或流程可使用的角色列表。 */
        val characters: List<ChatCreateCharacterItem> = emptyList(),
        /** 当前角色可供选择的开场白列表。 */
        val selectedCharacterFirstMessages: List<String> = emptyList(),
        /** 世界书列表当前使用的搜索关键词。 */
        val lorebookQuery: String = "",
        /** 按世界书分组后的条目列表。 */
        val lorebookGroups: List<ChatCreateLorebookGroupItem> = emptyList(),
        /** 按搜索条件过滤后实际展示的世界书分组。 */
        val visibleLorebookGroups: List<ChatCreateLorebookGroupItem> = lorebookGroups
    ) : ChatCreateUiState()

    data class Finished(val previous: ChatCreateUiState) : ChatCreateUiState()

    companion object {
        fun finished(previous: ChatCreateUiState): ChatCreateUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

/** 新建单聊页面的数据加载与创建状态。 */
sealed class ChatCreateLoadState {
    data object None : ChatCreateLoadState()
    data object Loading : ChatCreateLoadState()
    data object Creating : ChatCreateLoadState()
}
