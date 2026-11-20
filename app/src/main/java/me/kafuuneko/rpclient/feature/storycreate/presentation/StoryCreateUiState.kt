package me.kafuuneko.rpclient.feature.storycreate.presentation

import androidx.compose.ui.graphics.ImageBitmap
import me.kafuuneko.rpclient.feature.storycreate.model.StoryCreateCharacterItem
import me.kafuuneko.rpclient.feature.storycreate.model.StoryCreateForm
import me.kafuuneko.rpclient.feature.storycreate.model.StoryCreateLorebookGroupItem
import me.kafuuneko.rpclient.ui.model.WorldBookSelectionSummaryItem

/** 新建 Story 页面状态树。 */
sealed class StoryCreateUiState {
    data object None : StoryCreateUiState()

    data class Normal(
        /** 当前页面数据库或资源操作的加载状态。 */
        val loadState: StoryCreateLoadState = StoryCreateLoadState.Loading,
        /** 当前页面正在编辑的表单数据。 */
        val form: StoryCreateForm = StoryCreateForm(),
        /** 当前页面或流程可使用的角色列表。 */
        val characters: List<StoryCreateCharacterItem> = emptyList(),
        /** 主表单展示的已选参演角色。 */
        val selectedCharacters: List<StoryCreateCharacterItem> = emptyList(),
        /** 仅包含可见或常驻角色的已解码头像。 */
        val characterAvatars: Map<Long, ImageBitmap> = emptyMap(),
        /** 按世界书分组后的条目列表。 */
        val lorebookGroups: List<StoryCreateLorebookGroupItem> = emptyList(),
        /** 主表单展示的已启用世界书摘要。 */
        val selectedWorldBookSummaries: List<WorldBookSelectionSummaryItem> = emptyList(),
        /** 当前页面互斥展示的选择器弹窗状态。 */
        val dialogState: StoryCreateDialogState = StoryCreateDialogState.None
    ) : StoryCreateUiState() {
        val selectedCharacterCount: Int
            get() = selectedCharacters.size
    }

    data class Finished(val previous: StoryCreateUiState) : StoryCreateUiState()

    companion object {
        /** 保留最后一个可渲染状态，供页面退出过渡使用。 */
        fun finished(previous: StoryCreateUiState): StoryCreateUiState {
            return previous as? Finished ?: Finished(previous)
        }
    }
}

/** 新建 Story 页面的互斥选择器弹窗状态。 */
sealed class StoryCreateDialogState {
    data object None : StoryCreateDialogState()

    /** 多选角色参考检索弹窗状态。 */
    data class CharacterPicker(
        val query: String,
        val showSelectedOnly: Boolean,
        val visibleCharacters: List<StoryCreateCharacterItem>,
        val selectedCharacterOrder: List<Long>,
    ) : StoryCreateDialogState() {
        /** 顺序用于默认主角，集合用于勾选判断和已选筛选。 */
        val selectedCharacterIds: Set<Long> = selectedCharacterOrder.toSet()
    }

    /** 世界书条目检索与草稿选择弹窗状态。 */
    data class LorebookPicker(
        val query: String,
        val showSelectedOnly: Boolean,
        val visibleGroups: List<StoryCreateLorebookGroupItem>,
        val enabledEntryIds: Set<Long>,
    ) : StoryCreateDialogState()
}

/** 新建 Story 页面的加载和提交状态。 */
sealed class StoryCreateLoadState {
    data object Loading : StoryCreateLoadState()

    data object Ready : StoryCreateLoadState()

    data object Creating : StoryCreateLoadState()
}
