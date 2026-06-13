package me.kafuuneko.rpclient.feature.chatcreate.presentation

import me.kafuuneko.rpclient.feature.chatcreate.model.ChatCreateForm
import me.kafuuneko.rpclient.feature.chatcreate.model.ChatCreateLorebookGroupItem
import me.kafuuneko.rpclient.libs.room.entity.Character

/** 新建单聊页面状态树。 */
sealed class ChatCreateUiState {
    data object None : ChatCreateUiState()

    /** 表单、候选角色、角色开场白和世界书选择的稳定页面状态。 */
    data class Normal(
        val loadState: ChatCreateLoadState = ChatCreateLoadState.None,
        val form: ChatCreateForm = ChatCreateForm(),
        val characters: List<Character> = emptyList(),
        val selectedCharacterFirstMessages: List<String> = emptyList(),
        val lorebookGroups: List<ChatCreateLorebookGroupItem> = emptyList()
    ) : ChatCreateUiState()

    data object Finished : ChatCreateUiState()
}

/** 新建单聊页面的数据加载与创建状态。 */
sealed class ChatCreateLoadState {
    data object None : ChatCreateLoadState()
    data object Loading : ChatCreateLoadState()
    data object Creating : ChatCreateLoadState()
}
