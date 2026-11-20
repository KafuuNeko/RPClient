package me.kafuuneko.rpclient.feature.chatcreate.presentation

import me.kafuuneko.rpclient.feature.chatcreate.model.ChatCreateCharacterItem
import me.kafuuneko.rpclient.feature.chatcreate.model.ChatCreateForm
import me.kafuuneko.rpclient.feature.chatcreate.model.ChatCreateLorebookGroupItem
import me.kafuuneko.rpclient.libs.room.model.PersonaSelection

/** 新建单聊页面状态树。 */
sealed class ChatCreateUiState {
    data object None : ChatCreateUiState()

    /** 表单、候选角色、角色开场白和世界书选择的稳定页面状态。 */
    data class Normal(
        val persona: PersonaSelection = PersonaSelection(),
        /** 当前页面数据库或资源操作的加载状态。 */
        val loadState: ChatCreateLoadState = ChatCreateLoadState.None,
        /** 当前页面正在编辑的表单数据。 */
        val form: ChatCreateForm = ChatCreateForm(),
        /** 全局允许示例对话时才展示会话开关，隐藏不改变表单选择。 */
        val showCharacterExamplesSwitch: Boolean = true,
        /** 当前页面或流程可使用的角色列表。 */
        val characters: List<ChatCreateCharacterItem> = emptyList(),
        /** 当前角色可供选择的开场白列表。 */
        val selectedCharacterFirstMessages: List<String> = emptyList(),
        /** 按世界书分组后的条目列表。 */
        val lorebookGroups: List<ChatCreateLorebookGroupItem> = emptyList(),
        /** 当前页面互斥展示的选择器弹窗状态。 */
        val dialogState: ChatCreateDialogState = ChatCreateDialogState.None
    ) : ChatCreateUiState()

    data class Finished(val previous: ChatCreateUiState) : ChatCreateUiState()

    companion object {
        fun finished(previous: ChatCreateUiState): ChatCreateUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

/** 新建单聊页面的互斥选择器弹窗状态。 */
sealed class ChatCreateDialogState {
    data object None : ChatCreateDialogState()

    /** 单选角色检索弹窗状态。 */
    data class CharacterPicker(
        val query: String,
        val visibleCharacters: List<ChatCreateCharacterItem>,
    ) : ChatCreateDialogState()

    /** 世界书条目检索与草稿选择弹窗状态。 */
    data class LorebookPicker(
        val query: String,
        val showSelectedOnly: Boolean,
        val visibleGroups: List<ChatCreateLorebookGroupItem>,
        val enabledEntryIds: Set<Long>,
    ) : ChatCreateDialogState()
}

/** 新建单聊页面的数据加载与创建状态。 */
sealed class ChatCreateLoadState {
    data object None : ChatCreateLoadState()

    data object Loading : ChatCreateLoadState()

    data object Creating : ChatCreateLoadState()
}
