package me.kafuuneko.rpclient.feature.charactercontent

import android.os.Bundle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.charactercontent.model.CharacterContentItemSelection
import me.kafuuneko.rpclient.feature.charactercontent.model.toUiModel
import me.kafuuneko.rpclient.feature.charactercontent.presentation.CharacterContentDialogState
import me.kafuuneko.rpclient.feature.charactercontent.presentation.CharacterContentListState
import me.kafuuneko.rpclient.feature.charactercontent.presentation.CharacterContentSelectionState
import me.kafuuneko.rpclient.feature.charactercontent.presentation.CharacterContentUiIntent
import me.kafuuneko.rpclient.feature.charactercontent.presentation.CharacterContentUiState
import me.kafuuneko.rpclient.feature.chat.ChatActivity
import me.kafuuneko.rpclient.feature.chatcreate.ChatCreateActivity
import me.kafuuneko.rpclient.feature.groupchat.GroupChatActivity
import me.kafuuneko.rpclient.feature.storyeditor.StoryEditorActivity
import me.kafuuneko.rpclient.libs.appearance.PageBackgroundProvider
import me.kafuuneko.rpclient.libs.character.CharacterContentKind
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository
import me.kafuuneko.rpclient.libs.room.repository.StoryRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 单个角色的内容导航页。
 * - 恢复页面时刷新关联记录，保留当前筛选。
 * - 使用内容类型与 ID 打开原记录，并将角色带入新建单聊流程。
 * - 管理跨类型多选和删除确认，复用各内容仓库清理记录及关联资源。
 */
class CharacterContentViewModel :
    CoreViewModelWithEvent<CharacterContentUiIntent, CharacterContentUiState>(
        CharacterContentUiState.Loading
    ), KoinComponent {
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mChatRepository by inject<ChatRepository>()
    private val mGroupChatRepository by inject<GroupChatRepository>()
    private val mStoryRepository by inject<StoryRepository>()
    private val mBackgrounds by inject<PageBackgroundProvider>()
    private var mCharacterId = 0L

    /**
     * 订阅当前角色在内容页使用的背景。
     * @param characterId 此页面打开的角色 ID。
     * @return 包含角色背景或通用背景的显示状态流。
     */
    fun observeBackground(characterId: Long) = mBackgrounds.observeCharacterContent(characterId)

    /**
     * 根据宿主传入的角色刷新列表。
     *
     * @param intent 包含当前角色 ID 的恢复意图。
     */
    @UiIntentObserver(CharacterContentUiIntent.Resume::class)
    private suspend fun onResume(intent: CharacterContentUiIntent.Resume) {
        if (isStateOf<CharacterContentUiState.Finished>()) return
        val dialog = getOrNull<CharacterContentUiState.Normal>()?.dialogState
            as? CharacterContentDialogState.DeleteSelectedItems
        if (dialog?.isDeleting == true) return
        mCharacterId = intent.characterId
        refresh()
    }

    /** 读取失败后允许重新加载。 */
    @UiIntentObserver(CharacterContentUiIntent.Retry::class)
    private suspend fun onRetry() {
        if (!isStateOf<CharacterContentUiState.LoadFailed>()) return
        CharacterContentUiState.Loading.setup()
        refresh()
    }

    /** 恢复时按当前成员关系查询并退出多选，避免沿用已经解除关联或删除的选择。 */
    private suspend fun refresh() {
        val previous = getOrNull<CharacterContentUiState.Normal>()
        val content = try {
            mCharacterRepository.getCharacterContent(mCharacterId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            CharacterContentUiState.LoadFailed.setup()
            return
        }
        // 角色可能在其他页面已被删除，此时不再提供创建或打开入口。
        if (content == null) {
            AppViewEvent.PopupToastMessageByResId(R.string.character_not_found).tryEmit()
            CharacterContentUiState.Finished(uiStateFlow.value).setup()
            return
        }
        CharacterContentUiState.Normal(
            characterName = content.name,
            list = CharacterContentListState(
                items = content.items.map { it.toUiModel() },
                filter = previous?.list?.filter
            ),
        ).setup()
    }

    /**
     * 在快照内切换内容类型。
     *
     * @param intent 包含目标分类，null 表示全部内容。
     */
    @UiIntentObserver(CharacterContentUiIntent.SelectFilter::class)
    private fun onSelectFilter(intent: CharacterContentUiIntent.SelectFilter) {
        val state = getOrNull<CharacterContentUiState.Normal>() ?: return
        if (state.dialogState != CharacterContentDialogState.None) return
        state.copy(list = state.list.copy(filter = intent.filter)).setup()
    }

    /**
     * 长按可见条目进入多选，并勾选当前条目。
     *
     * @param intent 包含类型与记录 ID 的选择键。
     */
    @UiIntentObserver(CharacterContentUiIntent.EnterMultiSelect::class)
    private fun onEnterMultiSelect(intent: CharacterContentUiIntent.EnterMultiSelect) {
        val state = getOrNull<CharacterContentUiState.Normal>() ?: return
        if (state.dialogState != CharacterContentDialogState.None) return
        if (state.list.selectionState != CharacterContentSelectionState.None) return
        if (state.list.visibleItems.none { it.selection == intent.item }) return
        val selection = CharacterContentSelectionState.Selecting(selectedItems = setOf(intent.item))
        state.copy(list = state.list.copy(selectionState = selection)).setup()
    }

    /**
     * 切换可见条目的勾选，筛选隐藏的已选项继续保留。
     *
     * @param intent 包含待切换条目的选择键。
     */
    @UiIntentObserver(CharacterContentUiIntent.ToggleItemSelection::class)
    private fun onToggleItemSelection(intent: CharacterContentUiIntent.ToggleItemSelection) {
        val state = getOrNull<CharacterContentUiState.Normal>() ?: return
        if (state.dialogState != CharacterContentDialogState.None) return
        val selection =
            state.list.selectionState as? CharacterContentSelectionState.Selecting ?: return
        if (state.list.visibleItems.none { it.selection == intent.item }) return
        // 只替换当前选择键，跨筛选的选择集合保持不变。
        val selectedItems = if (intent.item in selection.selectedItems) {
            selection.selectedItems - intent.item
        } else {
            selection.selectedItems + intent.item
        }
        state.copy(list = state.list.copy(selectionState = selection.copy(selectedItems = selectedItems)))
            .setup()
    }

    /** 全选或取消选择当前筛选结果，保留其他分类中已选的条目。 */
    @UiIntentObserver(CharacterContentUiIntent.ToggleVisibleItemSelection::class)
    private fun onToggleVisibleItemSelection() {
        val state = getOrNull<CharacterContentUiState.Normal>() ?: return
        if (state.dialogState != CharacterContentDialogState.None) return
        val selection =
            state.list.selectionState as? CharacterContentSelectionState.Selecting ?: return
        val visibleSelections = state.list.visibleItems.mapTo(mutableSetOf()) { it.selection }
        if (visibleSelections.isEmpty()) return
        // 只切换当前筛选范围，避免清空其他分类的选择或误选隐藏内容。
        val selectedItems = if (selection.selectedItems.containsAll(visibleSelections)) {
            selection.selectedItems - visibleSelections
        } else {
            selection.selectedItems + visibleSelections
        }
        state.copy(list = state.list.copy(selectionState = selection.copy(selectedItems = selectedItems)))
            .setup()
    }

    /** 冻结当前选择集合，显示与首页一致的删除确认。 */
    @UiIntentObserver(CharacterContentUiIntent.ShowDeleteSelectedDialog::class)
    private fun onShowDeleteSelectedDialog() {
        val state = getOrNull<CharacterContentUiState.Normal>() ?: return
        if (state.dialogState != CharacterContentDialogState.None) return
        val selection =
            state.list.selectionState as? CharacterContentSelectionState.Selecting ?: return
        if (selection.selectedItems.isEmpty()) return
        state.copy(
            dialogState = CharacterContentDialogState.DeleteSelectedItems(items = selection.selectedItems)
        ).setup()
    }

    /** 异步删除确认的记录；删除期间阻止重复确认及其他页面操作。 */
    @UiIntentObserver(CharacterContentUiIntent.ConfirmDeleteSelected::class)
    private fun onConfirmDeleteSelected() {
        val state = getOrNull<CharacterContentUiState.Normal>() ?: return
        val dialog = state.dialogState as? CharacterContentDialogState.DeleteSelectedItems ?: return
        if (dialog.isDeleting || dialog.items.isEmpty()) return
        state.copy(dialogState = dialog.copy(isDeleting = true)).setup()
        // 使用独立任务处理批量 IO，意图收集仍可响应删除期间的状态守卫。
        viewModelScope.launch {
            val deletedItems = mutableSetOf<CharacterContentItemSelection>()
            try {
                withContext(Dispatchers.IO) {
                    dialog.items.forEach { item ->
                        when (item.type) {
                            CharacterContentKind.Chat -> mChatRepository.deleteSession(item.contentId)
                            CharacterContentKind.Group -> mGroupChatRepository.deleteSession(item.contentId)
                            CharacterContentKind.Story -> mStoryRepository.deleteStory(item.contentId)
                        }
                        deletedItems.add(item)
                    }
                }
                // 完成后重新读取关联内容，保留筛选并清除多选与确认窗口。
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                restoreFailedDeletion(dialog = dialog, deletedItems = deletedItems)
                AppViewEvent.PopupToastMessageByResId(R.string.delete_selected_items_failed)
                    .tryEmit()
            }
        }
    }

    /**
     * 部分删除失败后剔除已完成项，仅保留未完成项用于重试。
     *
     * @param dialog 本次确认时冻结的删除目标。
     * @param deletedItems 已成功删除的条目。
     */
    private fun restoreFailedDeletion(
        dialog: CharacterContentDialogState.DeleteSelectedItems,
        deletedItems: Set<CharacterContentItemSelection>,
    ) {
        val state = getOrNull<CharacterContentUiState.Normal>() ?: return
        val remaining = dialog.items - deletedItems
        // 仓库按条目提交，失败不会回滚之前已经删除的会话和关联文件。
        state.copy(
            list = state.list.copy(
                items = state.list.items.filterNot { it.selection in deletedItems },
                selectionState = CharacterContentSelectionState.Selecting(selectedItems = remaining),
            ),
            dialogState = dialog.copy(items = remaining, isDeleting = false),
        ).setup()
    }

    /** 删除提交期间保持窗口可见，其余时候允许取消并保留勾选。 */
    @UiIntentObserver(CharacterContentUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val state = getOrNull<CharacterContentUiState.Normal>() ?: return
        val dialog = state.dialogState as? CharacterContentDialogState.DeleteSelectedItems ?: return
        if (dialog.isDeleting) return
        state.copy(dialogState = CharacterContentDialogState.None).setup()
    }

    /**
     * 根据目标页的既有参数契约打开单聊、群聊或故事。
     *
     * @param intent 包含内容类型和对应表内的记录 ID。
     */
    @UiIntentObserver(CharacterContentUiIntent.OpenContent::class)
    private fun onOpenContent(intent: CharacterContentUiIntent.OpenContent) {
        val state = getOrNull<CharacterContentUiState.Normal>() ?: return
        if (state.dialogState != CharacterContentDialogState.None) return
        if (state.list.selectionState != CharacterContentSelectionState.None) return
        if (state.list.visibleItems.none { it.type == intent.type && it.contentId == intent.contentId }) return
        // 聊天页沿用字符串会话 ID，故事编辑器沿用数值 ID。
        val extras = Bundle()
        val activity = when (intent.type) {
            CharacterContentKind.Chat -> {
                extras.putString(ChatActivity.EXTRA_SESSION_ID, intent.contentId.toString())
                ChatActivity::class.java
            }

            CharacterContentKind.Group -> {
                extras.putString(GroupChatActivity.EXTRA_SESSION_ID, intent.contentId.toString())
                GroupChatActivity::class.java
            }

            CharacterContentKind.Story -> {
                extras.putLong(StoryEditorActivity.EXTRA_STORY_ID, intent.contentId)
                StoryEditorActivity::class.java
            }
        }
        AppViewEvent.StartActivity(activity = activity, extras = extras).tryEmit()
    }

    /** 打开标准创建页，并预选当前角色。 */
    @UiIntentObserver(CharacterContentUiIntent.CreateChat::class)
    private fun onCreateChat() {
        val state = getOrNull<CharacterContentUiState.Normal>() ?: return
        if (state.dialogState != CharacterContentDialogState.None) return
        if (state.list.selectionState != CharacterContentSelectionState.None) return
        // 复用标准创建表单，只传入角色预选参数。
        AppViewEvent.StartActivity(
            activity = ChatCreateActivity::class.java,
            extras = Bundle().apply {
                putLong(
                    ChatCreateActivity.EXTRA_CHARACTER_ID,
                    mCharacterId
                )
            },
        ).tryEmit()
    }

    /** 优先关闭确认或退出多选，普通浏览时保留最后可见状态并通知宿主退出。 */
    @UiIntentObserver(CharacterContentUiIntent.Back::class)
    private fun onBack() {
        if (isStateOf<CharacterContentUiState.Finished>()) return
        val state = getOrNull<CharacterContentUiState.Normal>()
        // 删除期间不能离开页面，待确认时返回只关闭窗口。
        if (state?.dialogState is CharacterContentDialogState.DeleteSelectedItems) {
            onDismissDialog()
            return
        }
        if (state?.list?.selectionState is CharacterContentSelectionState.Selecting) {
            state.copy(list = state.list.copy(selectionState = CharacterContentSelectionState.None))
                .setup()
            return
        }
        CharacterContentUiState.Finished(uiStateFlow.value).setup()
    }
}
