package me.kafuuneko.rpclient.feature.groupchatcreate.presentation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.groupchatcreate.model.GroupChatCreateCharacterItem
import me.kafuuneko.rpclient.feature.groupchatcreate.model.GroupChatCreateGreetingState
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatActivationStrategy
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatLorebookGroupItem
import me.kafuuneko.rpclient.libs.room.model.PersonaSelection
import me.kafuuneko.rpclient.ui.model.WorldBookSelectionSummaryItem

/** 新建群聊页面状态树。 */
sealed class GroupChatCreateUiState {
    data object None : GroupChatCreateUiState()

    /** 群聊标题、候选成员、世界书和发言策略的可交互状态。 */
    data class Normal(
        val persona: PersonaSelection = PersonaSelection(),
        /** 当前页面数据库或资源操作的加载状态。 */
        val loadState: GroupChatCreateLoadState = GroupChatCreateLoadState.None,
        /** 供界面展示或持久化的标题。 */
        val title: String = "",
        /** 当前页面或流程可使用的角色列表。 */
        val characters: List<GroupChatCreateCharacterItem> = emptyList(),
        /** 主表单展示的已选群成员。 */
        val selectedCharacters: List<GroupChatCreateCharacterItem> = emptyList(),
        /** 仅包含可见或常驻角色的已解码头像。 */
        val characterAvatars: Map<Long, ImageBitmap> = emptyMap(),
        /** 按世界书分组后的条目列表。 */
        val lorebookGroups: List<GroupChatLorebookGroupItem> = emptyList(),
        /** 当前已选中的世界书条目 ID 集合。 */
        val selectedLorebookEntryIds: Set<Long> = emptySet(),
        /** 主表单展示的已启用世界书摘要。 */
        val selectedWorldBookSummaries: List<WorldBookSelectionSummaryItem> = emptyList(),
        /** 当前场景采用的世界书激活策略。 */
        val activationStrategy: GroupChatActivationStrategy =
            GroupChatActivationStrategy.Natural,
        /** 群聊轮询时是否允许同一角色连续发言。 */
        val allowSelfResponses: Boolean = false,
        /** 是否允许注入参与当前群聊 Prompt 的角色卡示例对话。 */
        val includeCharacterExamples: Boolean = true,
        /** 全局允许示例对话时才展示会话开关，隐藏不改变表单选择。 */
        val showCharacterExamplesSwitch: Boolean = true,
        /** 群聊创建页的开场白选择状态。 */
        val greetingState: GroupChatCreateGreetingState = GroupChatCreateGreetingState(),
        /** 当前页面互斥展示的选择器弹窗状态。 */
        val dialogState: GroupChatCreateDialogState = GroupChatCreateDialogState.None
    ) : GroupChatCreateUiState() {
        /** 当前已选择加入群聊的角色数量。 */
        val selectedCount: Int
            get() = selectedCharacters.size

        /** 群聊至少需要两名成员。 */
        val hasEnoughMembers: Boolean
            get() = selectedCount >= 2

        /** 当前成员数量和开场白配置是否允许提交。 */
        val canCreate: Boolean
            get() = hasEnoughMembers && greetingState.canCreate

        /** 成员卡片与底栏共用的创建条件提示，与提交按钮采用相同规则。 */
        @get:StringRes
        val creationStatusResId: Int
            get() = when {
                !hasEnoughMembers -> R.string.group_chat_select_at_least_two
                !greetingState.canCreate -> R.string.group_chat_greeting_incomplete
                else -> R.string.group_chat_ready_to_begin
            }
    }

    data class Finished(val previous: GroupChatCreateUiState) : GroupChatCreateUiState()

    companion object {
        /** 保留最后一个可渲染状态，供页面退出过渡使用。 */
        fun finished(previous: GroupChatCreateUiState): GroupChatCreateUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

/** 新建群聊页面的互斥选择器弹窗状态。 */
sealed class GroupChatCreateDialogState {
    data object None : GroupChatCreateDialogState()

    /** 多选群成员检索弹窗状态。 */
    data class CharacterPicker(
        val query: String,
        val showSelectedOnly: Boolean,
        val visibleCharacters: List<GroupChatCreateCharacterItem>,
        val selectedCharacterIds: Set<Long>,
    ) : GroupChatCreateDialogState()

    /** 世界书条目检索与草稿选择弹窗状态。 */
    data class LorebookPicker(
        val query: String,
        val showSelectedOnly: Boolean,
        val visibleGroups: List<GroupChatLorebookGroupItem>,
        val enabledEntryIds: Set<Long>,
    ) : GroupChatCreateDialogState()
}

/** 新建群聊页面的数据加载与创建状态。 */
sealed class GroupChatCreateLoadState {
    data object None : GroupChatCreateLoadState()

    data object Loading : GroupChatCreateLoadState()

    data object Creating : GroupChatCreateLoadState()
}
