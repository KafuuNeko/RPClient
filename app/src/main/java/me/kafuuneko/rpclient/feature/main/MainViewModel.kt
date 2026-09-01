package me.kafuuneko.rpclient.feature.main

import android.content.Context
import android.os.Bundle
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.about.AboutActivity
import me.kafuuneko.rpclient.feature.characterlist.CharacterListActivity
import me.kafuuneko.rpclient.feature.chat.ChatActivity
import me.kafuuneko.rpclient.feature.chatcreate.ChatCreateActivity
import me.kafuuneko.rpclient.feature.groupchat.GroupChatActivity
import me.kafuuneko.rpclient.feature.groupchatcreate.GroupChatCreateActivity
import me.kafuuneko.rpclient.feature.llmprovideredit.LLMProviderEditActivity
import me.kafuuneko.rpclient.feature.llmproviderlist.LLMProviderListActivity
import me.kafuuneko.rpclient.feature.main.model.MainChatSessionGroup
import me.kafuuneko.rpclient.feature.main.model.items.MainChatSessionItem
import me.kafuuneko.rpclient.feature.main.model.MainGenerationParameter
import me.kafuuneko.rpclient.feature.main.model.items.MainGroupChatSessionItem
import me.kafuuneko.rpclient.feature.main.model.MainHomeItemSelection
import me.kafuuneko.rpclient.feature.main.model.MainHomeItemType
import me.kafuuneko.rpclient.feature.main.model.MainImportCharacterItem
import me.kafuuneko.rpclient.feature.main.model.MainProviderItem
import me.kafuuneko.rpclient.feature.main.model.items.MainStoryItem
import me.kafuuneko.rpclient.feature.main.presentation.MainChatDataManagementState
import me.kafuuneko.rpclient.feature.main.presentation.MainDebugSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainDialogState
import me.kafuuneko.rpclient.feature.main.presentation.MainGenerationParametersState
import me.kafuuneko.rpclient.feature.main.presentation.MainHomeResourceState
import me.kafuuneko.rpclient.feature.main.presentation.MainHomeSelectionState
import me.kafuuneko.rpclient.feature.main.presentation.MainHomeState
import me.kafuuneko.rpclient.feature.main.presentation.MainPage
import me.kafuuneko.rpclient.feature.main.presentation.MainPromptBehaviorState
import me.kafuuneko.rpclient.feature.main.presentation.MainProviderPostProcessingState
import me.kafuuneko.rpclient.feature.main.presentation.MainProviderSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainRecentChatsState
import me.kafuuneko.rpclient.feature.main.presentation.MainRecentGroupChatsState
import me.kafuuneko.rpclient.feature.main.presentation.MainRecentStoriesState
import me.kafuuneko.rpclient.feature.main.presentation.MainSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainSummaryInjectionState
import me.kafuuneko.rpclient.feature.main.presentation.MainSummarySettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainUiIntent
import me.kafuuneko.rpclient.feature.main.presentation.MainUiState
import me.kafuuneko.rpclient.feature.main.presentation.MainUserAvatarState
import me.kafuuneko.rpclient.feature.main.presentation.MainUserIdentityState
import me.kafuuneko.rpclient.feature.main.presentation.MainViewEvent
import me.kafuuneko.rpclient.feature.main.presentation.MainWorldInfoBudgetState
import me.kafuuneko.rpclient.feature.main.presentation.canOpenDialog
import me.kafuuneko.rpclient.feature.main.presentation.mergeAllRecentItems
import me.kafuuneko.rpclient.feature.main.presentation.mergeResumeRefresh
import me.kafuuneko.rpclient.feature.main.presentation.preserveCollapsedGroupsFrom
import me.kafuuneko.rpclient.feature.main.presentation.toggleItem
import me.kafuuneko.rpclient.feature.main.presentation.toMainSummaryInjectionState
import me.kafuuneko.rpclient.feature.promptpreset.PromptPresetActivity
import me.kafuuneko.rpclient.feature.requestlog.RequestLogActivity
import me.kafuuneko.rpclient.feature.regexscript.RegexScriptActivity
import me.kafuuneko.rpclient.feature.storycreate.StoryCreateActivity
import me.kafuuneko.rpclient.feature.storyeditor.StoryEditorActivity
import me.kafuuneko.rpclient.feature.tokenusage.TokenUsageActivity
import me.kafuuneko.rpclient.feature.worldbooklist.WorldBookListActivity
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.chat.ChatArchive
import me.kafuuneko.rpclient.libs.chat.ChatArchiveRepository
import me.kafuuneko.rpclient.libs.chat.ChatCharacterMatcher
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.defaults.normalizedUserName
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.resolveCharacterUserMacros
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionPosition
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionRole
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.model.ChatSessionOverview
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.libs.room.repository.StoryRepository
import me.kafuuneko.rpclient.utils.formatTimestamp
import me.kafuuneko.rpclient.utils.stripThinkBlocks
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 连续空白字符匹配正则，用于故事内容摘要的空白压缩与规整。 */
private val WHITESPACE_REGEX = Regex("\\s+")

/**
 * 应用主页面状态持有者。
 *
 * 核心职责：
 * - 聚合展示首页数据：单聊分组列表、群聊会话列表、故事列表及资产统计（角色数、世界书数）；
 * - 驱动首页多选管理模式，支持批量删除单聊、群聊与故事数据；
 * - 驱动单聊存档（ChatArchive）的解析导入流程（角色自动匹配建议与导入落库）；
 * - 管理全局设置：用户身份（头像/昵称/人设）、当前 LLM 供应商参数（温度/TopP/上下文/最大生成Token）、Prompt 后处理模式与流式输出；
 * - 管理世界书 Token 预算分配与溢出提醒；
 * - 管理自动会话总结（Auto Summary）配置与注入策略（System/User/Assistant/深度）；
 * - 调度所有二级功能模块页面的跳转。
 */
class MainViewModel : CoreViewModelWithEvent<MainUiIntent, MainUiState>(
    MainUiState.None
), KoinComponent {
    private val mLLMRepository by inject<LLMRepository>()
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mChatRepository by inject<ChatRepository>()
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mGroupChatRepository by inject<GroupChatRepository>()
    private val mStoryRepository by inject<StoryRepository>()
    private val mFileRepository by inject<FileRepository>()
    private val mChatArchiveRepository by inject<ChatArchiveRepository>()
    private val mContext by inject<Context>()

    /** 文件解析结果只在用户确认角色前暂存，不进入可持久状态或数据库。 */
    private var mPendingChatImport: ChatArchive? = null
    /** 导入文件读取与最终事务共用单任务守卫，阻止重复选择或重复提交。 */
    private var mChatImportJob: Job? = null

    /** 初始化主页与设置页全量状态。 */
    @UiIntentObserver(MainUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<MainUiState.None>()) return
        val allProviders = mLLMRepository.getAllProviders()
        val providers = allProviders.filter { it.isEnabled }
        val currentId = AppModel.currentLLMProvider
        val selectedProvider = providers.firstOrNull { it.id == currentId } ?: providers.firstOrNull()
        MainUiState.Normal(
            homeState = buildHomeState(),
            settingsState = buildSettingsState(providers, selectedProvider, allProviders)
        ).setup()
    }

    /** 页面从后台恢复可见时触发，增量刷新首页列表与设置项。 */
    @UiIntentObserver(MainUiIntent.Resume::class)
    private suspend fun onResume() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val allProviders = mLLMRepository.getAllProviders()
        val providers = allProviders.filter { it.isEnabled }
        val currentId = AppModel.currentLLMProvider
        val selectedProvider = providers.firstOrNull { it.id == currentId } ?: providers.firstOrNull()
        val homeState = buildHomeState()
        val settingsState = buildSettingsState(providers, selectedProvider, allProviders)
        val current = getOrNull<MainUiState.Normal>() ?: return
        current.mergeResumeRefresh(
            homeState = homeState,
            settingsState = settingsState
        ).setup()
    }

    /** 处理返回键操作：若在多选状态则退出多选；若在设置页则返回首页；若在首页则退出应用。 */
    @UiIntentObserver(MainUiIntent.Back::class)
    private fun onBack() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        if (uiState.homeState.selectionState is MainHomeSelectionState.Selecting) {
            uiState.copy(
                homeState = uiState.homeState.copy(
                    selectionState = MainHomeSelectionState.None
                )
            ).setup()
            return
        }
        if (uiState.selectedPage != MainPage.Home) {
            uiState.copy(selectedPage = MainPage.Home).setup()
            return
        }
        MainUiState.finished(uiStateFlow.value).setup()
    }

    /** 长按首页列表项进入多选模式并选中当前长按的条目。 */
    @UiIntentObserver(MainUiIntent.EnterMultiSelect::class)
    private fun onEnterMultiSelect(intent: MainUiIntent.EnterMultiSelect) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        if (uiState.homeState.selectionState is MainHomeSelectionState.Selecting) return
        uiState.copy(
            homeState = uiState.homeState.copy(
                selectionState = MainHomeSelectionState.Selecting(
                    selectedItems = setOf(intent.item)
                )
            )
        ).setup()
    }

    /** 切换指定列表项在多选状态下的勾选/反选。 */
    @UiIntentObserver(MainUiIntent.ToggleItemSelection::class)
    private fun onToggleItemSelection(intent: MainUiIntent.ToggleItemSelection) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val selectionState = uiState.homeState.selectionState
            as? MainHomeSelectionState.Selecting
            ?: return
        uiState.copy(
            homeState = uiState.homeState.copy(
                selectionState = selectionState.toggleItem(intent.item)
            )
        ).setup()
    }

    /** 展开/折叠指定角色下的单聊会话分组折叠面板。 */
    @UiIntentObserver(MainUiIntent.ToggleSessionGroup::class)
    private fun onToggleSessionGroup(intent: MainUiIntent.ToggleSessionGroup) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val recentChatsState = uiState.homeState.recentChatsState
            as? MainRecentChatsState.Content
            ?: return
        if (recentChatsState.sessionGroups.none { it.characterId == intent.characterId }) return
        val collapsedCharacterIds = recentChatsState.collapsedCharacterIds
        val updated = if (intent.characterId in collapsedCharacterIds) {
            collapsedCharacterIds - intent.characterId
        } else {
            collapsedCharacterIds + intent.characterId
        }
        uiState.copy(
            homeState = uiState.homeState.copy(
                recentChatsState = recentChatsState.copy(
                    collapsedCharacterIds = updated
                )
            )
        ).setup()
    }

    /** 退出首页多选模式并清空已选项。 */
    @UiIntentObserver(MainUiIntent.ExitMultiSelect::class)
    private fun onExitMultiSelect() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        if (uiState.homeState.selectionState !is MainHomeSelectionState.Selecting) return
        uiState.copy(
            homeState = uiState.homeState.copy(
                selectionState = MainHomeSelectionState.None
            )
        ).setup()
    }

    /** 点击批量删除按钮，弹出二次确认弹窗。 */
    @UiIntentObserver(MainUiIntent.ShowDeleteSelectedDialog::class)
    private fun onShowDeleteSelectedDialog() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        if (!uiState.canOpenDialog()) return
        val selectionState = uiState.homeState.selectionState
            as? MainHomeSelectionState.Selecting
            ?: return
        val count = selectionState.selectedItems.size
        if (count == 0) return
        uiState.copy(
            dialogState = MainDialogState.DeleteSelectedItems(count = count)
        ).setup()
    }

    /** 确认批量删除选中的单聊、群聊或故事会话。 */
    @UiIntentObserver(MainUiIntent.ConfirmDeleteSelected::class)
    private suspend fun onConfirmDeleteSelected() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? MainDialogState.DeleteSelectedItems ?: return
        if (dialog.isDeleting) return
        val selectionState =
            uiState.homeState.selectionState as? MainHomeSelectionState.Selecting ?: return
        val selections = selectionState.selectedItems
        if (selections.isEmpty()) return
        uiState.copy(dialogState = dialog.copy(isDeleting = true)).setup()
        try {
            // 在 IO 线程遍历删除选中的各项会话
            withContext(Dispatchers.IO) {
                selections.forEach { selection ->
                    val itemId = selection.itemId.toLongOrNull() ?: return@forEach
                    when (selection.type) {
                        MainHomeItemType.Chat -> mChatRepository.deleteSession(itemId)
                        MainHomeItemType.GroupChat -> mGroupChatRepository.deleteSession(itemId)
                        MainHomeItemType.Story -> mStoryRepository.deleteStory(itemId)
                    }
                }
            }
            // 重新拉取首页数据并继承折叠状态
            val homeState = buildHomeState()
            val current = getOrNull<MainUiState.Normal>() ?: return
            current.copy(
                dialogState = MainDialogState.None,
                homeState = homeState.preserveCollapsedGroupsFrom(current.homeState)
            ).setup()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            AppViewEvent.PopupToastMessageByResId(R.string.delete_selected_items_failed).tryEmit()
            val current = getOrNull<MainUiState.Normal>() ?: return
            val currentDialog = current.dialogState as? MainDialogState.DeleteSelectedItems ?: return
            current.copy(dialogState = currentDialog.copy(isDeleting = false)).setup()
        }
    }

    /** 弹出首页内容重命名弹窗。 */
    @UiIntentObserver(MainUiIntent.ShowRenameItemDialog::class)
    private fun onShowRenameItemDialog(intent: MainUiIntent.ShowRenameItemDialog) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        if (!uiState.canOpenDialog()) return
        val title = uiState.homeState.findItemTitle(intent.item) ?: return
        uiState.copy(
            dialogState = MainDialogState.RenameItem(
                item = intent.item,
                title = title
            )
        ).setup()
    }

    /** 修改首页内容重命名弹窗中的标题草稿。 */
    @UiIntentObserver(MainUiIntent.ChangeItemTitleDraft::class)
    private fun onChangeItemTitleDraft(intent: MainUiIntent.ChangeItemTitleDraft) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? MainDialogState.RenameItem ?: return
        if (dialog.isSaving) return
        uiState.copy(dialogState = dialog.copy(title = intent.value)).setup()
    }

    /** 确认保存首页内容的新标题并刷新列表。 */
    @UiIntentObserver(MainUiIntent.ConfirmItemRename::class)
    private suspend fun onConfirmItemRename() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? MainDialogState.RenameItem ?: return
        val title = dialog.title.trim()
        if (title.isEmpty() || dialog.isSaving) return
        uiState.copy(dialogState = dialog.copy(isSaving = true)).setup()
        try {
            withContext(Dispatchers.IO) {
                val itemId = checkNotNull(dialog.item.itemId.toLongOrNull())
                when (dialog.item.type) {
                    MainHomeItemType.Chat -> {
                        checkNotNull(mChatRepository.getSessionById(itemId))
                        mChatRepository.updateSessionTitle(itemId, title)
                    }

                    MainHomeItemType.GroupChat -> {
                        val session = checkNotNull(mGroupChatRepository.getSessionById(itemId))
                        mGroupChatRepository.updateSession(session.copy(title = title))
                    }

                    MainHomeItemType.Story -> {
                        check(mStoryRepository.renameStory(itemId, title))
                    }
                }
            }
            val homeState = buildHomeState()
            val current = getOrNull<MainUiState.Normal>() ?: return
            current.copy(
                dialogState = MainDialogState.None,
                homeState = homeState.preserveCollapsedGroupsFrom(current.homeState)
            ).setup()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            AppViewEvent.PopupToastMessageByResId(R.string.story_save_failed_short).tryEmit()
            val current = getOrNull<MainUiState.Normal>() ?: return
            val currentDialog = current.dialogState as? MainDialogState.RenameItem ?: return
            current.copy(dialogState = currentDialog.copy(isSaving = false)).setup()
        }
    }

    /** 关闭当前活跃的弹窗并清理临时暂存对象。 */
    @UiIntentObserver(MainUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val importDialog = uiState.dialogState as? MainDialogState.ImportChatCharacterSelection
        if (importDialog?.isImporting == true) return
        val deleteDialog = uiState.dialogState as? MainDialogState.DeleteSelectedItems
        if (deleteDialog?.isDeleting == true) return
        val renameDialog = uiState.dialogState as? MainDialogState.RenameItem
        if (renameDialog?.isSaving == true) return
        if (importDialog != null) {
            mPendingChatImport = null
        }
        uiState.copy(dialogState = MainDialogState.None).setup()
    }

    /** 触发系统文件选择器以导入聊天存档文件。 */
    @UiIntentObserver(MainUiIntent.ImportChatClick::class)
    private fun onImportChatClick() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        if (!uiState.canOpenDialog() || mChatImportJob?.isActive == true) return
        MainViewEvent.OpenChatImporter.tryEmit()
    }

    /** 读取并解析导入的聊天存档 JSON 文件，匹配候选角色并弹出角色绑定弹窗。 */
    @UiIntentObserver(MainUiIntent.ImportChatResult::class)
    private fun onImportChatResult(intent: MainUiIntent.ImportChatResult) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        if (!uiState.canOpenDialog() || mChatImportJob?.isActive == true) return
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                chatDataManagementState = MainChatDataManagementState.Reading
            )
        ).setup()
        mChatImportJob = viewModelScope.launch {
            try {
                // 在 IO 线程解析聊天存档并检索全量候选角色
                val archive = mChatArchiveRepository.readImportFromUri(
                    uri = intent.uri,
                    fallbackUserName = AppModel.resolvedUserName
                )
                val characters = mCharacterRepository.getAllCharacters()
                mPendingChatImport = archive
                val current = getOrNull<MainUiState.Normal>() ?: return@launch
                val items = characters.map { it.toImportCharacterItem() }
                // 推荐最佳匹配角色并展示角色绑定选择弹窗
                current.copy(
                    settingsState = current.settingsState.copy(
                        chatDataManagementState = MainChatDataManagementState.Idle
                    ),
                    dialogState = MainDialogState.ImportChatCharacterSelection(
                        title = archive.title,
                        sourceCharacterName = archive.characterNameHint,
                        messageCount = archive.messages.size,
                        query = "",
                        characters = items,
                        visibleCharacters = items,
                        selectedCharacterId = ChatCharacterMatcher.suggestCharacterId(
                            archive,
                            characters
                        )
                    )
                ).setup()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                mPendingChatImport = null
                AppViewEvent.PopupToastMessageByResId(R.string.import_chat_failed).tryEmit()
                getOrNull<MainUiState.Normal>()?.let { current ->
                    current.copy(
                        settingsState = current.settingsState.copy(
                            chatDataManagementState = MainChatDataManagementState.Idle
                        )
                    ).setup()
                }
            } finally {
                mChatImportJob = null
            }
        }
    }

    /** 过滤导入角色绑定弹窗中的候选角色列表。 */
    @UiIntentObserver(MainUiIntent.ChangeImportCharacterQuery::class)
    private fun onChangeImportCharacterQuery(
        intent: MainUiIntent.ChangeImportCharacterQuery
    ) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? MainDialogState.ImportChatCharacterSelection ?: return
        if (dialog.isImporting) return
        val query = intent.value.trim()
        val visible = if (query.isBlank()) {
            dialog.characters
        } else {
            dialog.characters.filter { item ->
                item.name.contains(query, ignoreCase = true) ||
                    item.details.contains(query, ignoreCase = true)
            }
        }
        uiState.copy(
            dialogState = dialog.copy(
                query = intent.value,
                visibleCharacters = visible
            )
        ).setup()
    }

    /** 选择导入聊天记录所归属的目标角色。 */
    @UiIntentObserver(MainUiIntent.SelectImportCharacter::class)
    private fun onSelectImportCharacter(intent: MainUiIntent.SelectImportCharacter) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? MainDialogState.ImportChatCharacterSelection ?: return
        if (dialog.isImporting || dialog.characters.none { it.id == intent.characterId }) return
        uiState.copy(
            dialogState = dialog.copy(selectedCharacterId = intent.characterId)
        ).setup()
    }

    /** 确认将暂存的聊天存档与选定角色绑定并原子事务落库，导入成功后立即进入聊天页。 */
    @UiIntentObserver(MainUiIntent.ConfirmImportChat::class)
    private fun onConfirmImportChat() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? MainDialogState.ImportChatCharacterSelection ?: return
        val characterId = dialog.selectedCharacterId ?: return
        val archive = mPendingChatImport ?: return
        if (dialog.isImporting || mChatImportJob?.isActive == true) return
        uiState.copy(dialogState = dialog.copy(isImporting = true)).setup()
        mChatImportJob = viewModelScope.launch {
            try {
                // 在 IO 线程事务保存会话与全量导入消息
                val sessionId = mChatArchiveRepository.saveImport(archive, characterId)
                mPendingChatImport = null
                val homeState = buildHomeState()
                val current = getOrNull<MainUiState.Normal>() ?: return@launch
                // 更新首页会话列表并关闭导入弹窗
                current.copy(
                    homeState = homeState.preserveCollapsedGroupsFrom(current.homeState),
                    dialogState = if (
                        current.dialogState is MainDialogState.ImportChatCharacterSelection
                    ) {
                        MainDialogState.None
                    } else {
                        current.dialogState
                    }
                ).setup()
                AppViewEvent.PopupToastMessageByResId(R.string.import_chat_success).tryEmit()
                // 导航进入新导入的会话页
                AppViewEvent.StartActivity(
                    activity = ChatActivity::class.java,
                    extras = Bundle().apply {
                        putString(ChatActivity.EXTRA_SESSION_ID, sessionId.toString())
                    }
                ).tryEmit()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                AppViewEvent.PopupToastMessageByResId(R.string.import_chat_failed).tryEmit()
                val current = getOrNull<MainUiState.Normal>() ?: return@launch
                val currentDialog = current.dialogState
                    as? MainDialogState.ImportChatCharacterSelection
                    ?: return@launch
                current.copy(
                    dialogState = currentDialog.copy(isImporting = false)
                ).setup()
            } finally {
                mChatImportJob = null
            }
        }
    }

    /** 切换主页底部导航标签页（首页 / 设置）。 */
    @UiIntentObserver(MainUiIntent.SelectPage::class)
    private fun onSelectPage(intent: MainUiIntent.SelectPage) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        uiState.copy(selectedPage = intent.page).setup()
    }

    /** 切换首页内容子标签页（单聊 / 群聊 / 故事）。 */
    @UiIntentObserver(MainUiIntent.SelectHomeContentTab::class)
    private fun onSelectHomeContentTab(intent: MainUiIntent.SelectHomeContentTab) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        if (uiState.homeState.selectedContentTab == intent.tab) return
        uiState.copy(
            homeState = uiState.homeState.copy(selectedContentTab = intent.tab)
        ).setup()
    }

    /** 打开指定的单聊会话页面。 */
    @UiIntentObserver(MainUiIntent.OpenChat::class)
    private fun onOpenChat(intent: MainUiIntent.OpenChat) {
        if (!isStateOf<MainUiState.Normal>()) return
        AppViewEvent.StartActivity(
            activity = ChatActivity::class.java,
            extras = Bundle().apply { putString(ChatActivity.EXTRA_SESSION_ID, intent.sessionId) }
        ).tryEmit()
    }

    /** 打开新建单聊会话页。 */
    @UiIntentObserver(MainUiIntent.OpenCreateChat::class)
    private fun onOpenCreateChat() {
        if (!isStateOf<MainUiState.Normal>()) return
        AppViewEvent.StartActivity(ChatCreateActivity::class.java).tryEmit()
    }

    /** 打开指定的群聊会话页面。 */
    @UiIntentObserver(MainUiIntent.OpenGroupChat::class)
    private fun onOpenGroupChat(intent: MainUiIntent.OpenGroupChat) {
        if (!isStateOf<MainUiState.Normal>()) return
        AppViewEvent.StartActivity(
            activity = GroupChatActivity::class.java,
            extras = Bundle().apply {
                putString(GroupChatActivity.EXTRA_SESSION_ID, intent.sessionId)
            }
        ).tryEmit()
    }

    /** 打开新建群聊会话页。 */
    @UiIntentObserver(MainUiIntent.OpenCreateGroupChat::class)
    private fun onOpenCreateGroupChat() {
        if (!isStateOf<MainUiState.Normal>()) return
        AppViewEvent.StartActivity(GroupChatCreateActivity::class.java).tryEmit()
    }

    /** 打开指定的故事编辑器页面。 */
    @UiIntentObserver(MainUiIntent.OpenStory::class)
    private fun onOpenStory(intent: MainUiIntent.OpenStory) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        if (uiState.homeState.findStory(intent.storyId) == null) return
        AppViewEvent.StartActivity(
            activity = StoryEditorActivity::class.java,
            extras = Bundle().apply {
                putLong(StoryEditorActivity.EXTRA_STORY_ID, intent.storyId)
            }
        ).tryEmit()
    }

    /** 打开新建故事页面。 */
    @UiIntentObserver(MainUiIntent.OpenCreateStory::class)
    private fun onOpenCreateStory() {
        if (!isStateOf<MainUiState.Normal>()) return
        AppViewEvent.StartActivity(StoryCreateActivity::class.java).tryEmit()
    }

    /** 打开角色管理列表页。 */
    @UiIntentObserver(MainUiIntent.OpenCharacterManager::class)
    private fun onOpenCharacterManager() {
        if (!isStateOf<MainUiState.Normal>()) return
        AppViewEvent.StartActivity(CharacterListActivity::class.java).tryEmit()
    }

    /** 打开世界书管理列表页。 */
    @UiIntentObserver(MainUiIntent.OpenWorldBookManager::class)
    private fun onOpenWorldBookManager() {
        if (!isStateOf<MainUiState.Normal>()) return
        AppViewEvent.StartActivity(WorldBookListActivity::class.java).tryEmit()
    }

    /** 打开模型供应商管理列表页。 */
    @UiIntentObserver(MainUiIntent.OpenProviderManager::class)
    private fun onOpenProviderManager() {
        if (!isStateOf<MainUiState.Normal>()) return
        AppViewEvent.StartActivity(LLMProviderListActivity::class.java).tryEmit()
    }

    /** 打开当前选中的模型供应商详情编辑页。 */
    @UiIntentObserver(MainUiIntent.OpenSelectedProviderEdit::class)
    private fun onOpenSelectedProviderEdit() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val providerState = uiState.settingsState.providerState
            as? MainProviderSettingsState.Available
            ?: return
        AppViewEvent.StartActivity(
            activity = LLMProviderEditActivity::class.java,
            extras = Bundle().apply {
                putLong(LLMProviderEditActivity.EXTRA_PROVIDER_ID, providerState.selectedProviderId)
            }
        ).tryEmit()
    }

    /** 弹出模型生成参数（温度 / TopP / MaxTokens / ContextTokens）快速编辑弹窗。 */
    @UiIntentObserver(MainUiIntent.ShowGenerationParameterDialog::class)
    private suspend fun onShowGenerationParameterDialog(
        intent: MainUiIntent.ShowGenerationParameterDialog
    ) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        if (!uiState.canOpenDialog()) return
        val providerState = uiState.settingsState.providerState
            as? MainProviderSettingsState.Available
            ?: return
        // 在 IO 线程加载当前供应商最新参数
        val provider = withContext(Dispatchers.IO) {
            mLLMRepository.getProviderById(providerState.selectedProviderId)
        } ?: return
        val current = getOrNull<MainUiState.Normal>() ?: return
        if (!current.canOpenDialog()) return
        val currentProviderState = current.settingsState.providerState
            as? MainProviderSettingsState.Available
            ?: return
        if (currentProviderState.selectedProviderId != provider.id) return
        // 装填当前参数草稿并弹出对话框
        current.copy(
            dialogState = MainDialogState.EditGenerationParameter(
                parameter = intent.parameter,
                draftValue = intent.parameter.valueOf(provider)
            )
        ).setup()
    }

    /** 修改模型生成参数编辑弹窗中的草稿数值。 */
    @UiIntentObserver(MainUiIntent.ChangeGenerationParameterDraft::class)
    private fun onChangeGenerationParameterDraft(
        intent: MainUiIntent.ChangeGenerationParameterDraft
    ) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? MainDialogState.EditGenerationParameter ?: return
        uiState.copy(
            dialogState = dialog.copy(draftValue = intent.value)
        ).setup()
    }

    /** 确认并持久化修改后的模型生成参数。 */
    @UiIntentObserver(MainUiIntent.ConfirmGenerationParameter::class)
    private suspend fun onConfirmGenerationParameter() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? MainDialogState.EditGenerationParameter ?: return
        val providerState = uiState.settingsState.providerState
            as? MainProviderSettingsState.Available
            ?: return
        val provider = withContext(Dispatchers.IO) {
            mLLMRepository.getProviderById(providerState.selectedProviderId)
        } ?: return
        // 校验输入数值合法性与 Token 上限约束关系
        val updatedProvider = dialog.parameter.updateProviderOrNull(provider, dialog.draftValue)
        if (updatedProvider == null) {
            val messageRes = if (dialog.hasInvalidTokenRelationship(provider)) {
                R.string.max_tokens_must_be_less_than_context
            } else {
                R.string.generation_params_invalid
            }
            AppViewEvent.PopupToastMessageByResId(messageRes).tryEmit()
            return
        }
        // 持久化更新到数据库
        withContext(Dispatchers.IO) {
            mLLMRepository.saveProvider(updatedProvider)
        }
        val current = getOrNull<MainUiState.Normal>() ?: return
        val currentProviderState = current.settingsState.providerState
            as? MainProviderSettingsState.Available
            ?: return
        if (currentProviderState.selectedProviderId != updatedProvider.id) return
        // 关闭弹窗并同步更新模型设置面板
        current.copy(
            dialogState = if (
                current.dialogState is MainDialogState.EditGenerationParameter
            ) {
                MainDialogState.None
            } else {
                current.dialogState
            },
            settingsState = current.settingsState.copy(
                providerState = currentProviderState.copy(
                    generationParametersState = updatedProvider.toGenerationParametersState()
                )
            )
        ).setup()
    }

    /** 校验最大生成 Token 是否大于等于上下文总 Token 限制。 */
    private fun MainDialogState.EditGenerationParameter.hasInvalidTokenRelationship(
        provider: LLMProvider
    ): Boolean {
        val value = draftValue.toIntOrNull() ?: return false
        return when (parameter) {
            MainGenerationParameter.MaxTokens -> value >= provider.contextTokens
            MainGenerationParameter.ContextTokens -> value <= provider.maxTokens
            MainGenerationParameter.Temperature, MainGenerationParameter.TopP -> false
        }
    }

    /** 触发系统图片选择器选择用户自定义头像。 */
    @UiIntentObserver(MainUiIntent.PickUserAvatarClick::class)
    private fun onPickUserAvatarClick() {
        if (!isStateOf<MainUiState.Normal>()) return
        MainViewEvent.OpenUserAvatarPicker.tryEmit()
    }

    /** 接收裁剪页生成的方形头像文件并替换旧头像。 */
    @UiIntentObserver(MainUiIntent.UserAvatarCropped::class)
    private suspend fun onUserAvatarCropped(intent: MainUiIntent.UserAvatarCropped) {
        if (!isStateOf<MainUiState.Normal>()) return
        val oldAvatar = AppModel.userAvatar
        AppModel.userAvatar = intent.fileUuid
        // 清理旧头像物理文件
        if (oldAvatar.isNotBlank() && oldAvatar != intent.fileUuid) {
            runCatching {
                withContext(Dispatchers.IO) {
                    mFileRepository.deleteFile(oldAvatar)
                }
            }
        }
        // 解析新头像 Bitmap 并更新 UI 状态
        val avatarImage = resolveUserAvatarImage()
        val current = getOrNull<MainUiState.Normal>() ?: return
        current.copy(
            settingsState = current.settingsState.copy(
                identityState = current.settingsState.identityState.copy(
                    avatarState = MainUserAvatarState.Configured(avatarImage)
                )
            )
        ).setup()
    }

    /** 清空用户自定义头像并删除对应本地文件。 */
    @UiIntentObserver(MainUiIntent.ClearUserAvatar::class)
    private suspend fun onClearUserAvatar() {
        if (!isStateOf<MainUiState.Normal>()) return
        val oldAvatar = AppModel.userAvatar
        AppModel.userAvatar = ""
        // 在 IO 线程删除旧头像物理文件
        if (oldAvatar.isNotBlank()) {
            runCatching {
                withContext(Dispatchers.IO) {
                    mFileRepository.deleteFile(oldAvatar)
                }
            }
        }
        // 重置为无头像状态
        val current = getOrNull<MainUiState.Normal>() ?: return
        current.copy(
            settingsState = current.settingsState.copy(
                identityState = current.settingsState.identityState.copy(
                    avatarState = MainUserAvatarState.None
                )
            )
        ).setup()
    }

    /** 打开全局 Prompt 提示词预设页。 */
    @UiIntentObserver(MainUiIntent.OpenPromptPreset::class)
    private fun onOpenPromptPreset() {
        if (!isStateOf<MainUiState.Normal>()) return
        AppViewEvent.StartActivity(PromptPresetActivity::class.java).tryEmit()
    }

    /** 打开正则脚本管理器页面。 */
    @UiIntentObserver(MainUiIntent.OpenRegexScripts::class)
    private fun onOpenRegexScripts() {
        if (!isStateOf<MainUiState.Normal>()) return
        AppViewEvent.StartActivity(RegexScriptActivity::class.java).tryEmit()
    }

    /** 打开 LLM 请求与调试日志列表页。 */
    @UiIntentObserver(MainUiIntent.OpenRequestLogs::class)
    private fun onOpenRequestLogs() {
        if (!isStateOf<MainUiState.Normal>()) return
        AppViewEvent.StartActivity(RequestLogActivity::class.java).tryEmit()
    }

    /** 打开成功 LLM 请求的 Token 消耗统计页。 */
    @UiIntentObserver(MainUiIntent.OpenTokenUsage::class)
    private fun onOpenTokenUsage() {
        if (!isStateOf<MainUiState.Normal>()) return
        AppViewEvent.StartActivity(TokenUsageActivity::class.java).tryEmit()
    }

    /** 打开关于软件页面。 */
    @UiIntentObserver(MainUiIntent.OpenAbout::class)
    private fun onOpenAbout() {
        if (!isStateOf<MainUiState.Normal>()) return
        AppViewEvent.StartActivity(AboutActivity::class.java).tryEmit()
    }

    /** 修改用户默认显示名称并持久化。 */
    @UiIntentObserver(MainUiIntent.ChangeUserName::class)
    private fun onChangeUserName(intent: MainUiIntent.ChangeUserName) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val value = intent.value.normalizedUserName()
        AppModel.userName = value
        val identityState = uiState.settingsState.identityState
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                identityState = identityState.copy(
                    userName = value,
                    userDescriptionPreview = resolveCharacterUserMacros(
                        template = identityState.userDescription,
                        characterName = null,
                        userName = value
                    )
                )
            )
        ).setup()
    }

    /** 修改用户人设设定描述并持久化。 */
    @UiIntentObserver(MainUiIntent.ChangeUserDescription::class)
    private fun onChangeUserDescription(intent: MainUiIntent.ChangeUserDescription) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.userDescription = intent.value.trim()
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                identityState = uiState.settingsState.identityState.copy(
                    userDescription = intent.value,
                    userDescriptionPreview = resolveCharacterUserMacros(
                        template = intent.value,
                        characterName = null,
                        userName = uiState.settingsState.identityState.userName.normalizedUserName()
                    )
                )
            )
        ).setup()
    }

    /** 在全屏 Prompt 编辑器中打开用户人设描述。 */
    @UiIntentObserver(MainUiIntent.ShowUserDescriptionEditor::class)
    private fun onShowUserDescriptionEditor() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        if (!uiState.canOpenDialog()) return
        uiState.copy(
            dialogState = MainDialogState.EditUserDescription(
                draftText = uiState.settingsState.identityState.userDescription
            )
        ).setup()
    }

    /** 修改全屏用户人设描述的暂存文本，确认前不写入偏好设置。 */
    @UiIntentObserver(MainUiIntent.ChangeUserDescriptionEditorDraft::class)
    private fun onChangeUserDescriptionEditorDraft(
        intent: MainUiIntent.ChangeUserDescriptionEditorDraft
    ) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? MainDialogState.EditUserDescription ?: return
        uiState.copy(dialogState = dialog.copy(draftText = intent.value)).setup()
    }

    /** 保存全屏编辑器中的用户人设描述并关闭弹窗。 */
    @UiIntentObserver(MainUiIntent.ConfirmUserDescriptionEditor::class)
    private fun onConfirmUserDescriptionEditor() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? MainDialogState.EditUserDescription ?: return
        AppModel.userDescription = dialog.draftText.trim()
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                identityState = uiState.settingsState.identityState.copy(
                    userDescription = dialog.draftText,
                    userDescriptionPreview = resolveCharacterUserMacros(
                        template = dialog.draftText,
                        characterName = null,
                        userName = uiState.settingsState.identityState.userName.normalizedUserName()
                    )
                )
            ),
            dialogState = MainDialogState.None
        ).setup()
    }

    /** 切换全局默认使用的 LLM 供应商。 */
    @UiIntentObserver(MainUiIntent.SelectProvider::class)
    private suspend fun onSelectProvider(intent: MainUiIntent.SelectProvider) {
        if (!isStateOf<MainUiState.Normal>()) return
        // 更新数据库中的当前首选供应商
        mLLMRepository.updateCurrentProvider(intent.providerId)
        val providers = mLLMRepository.getEnabledProviders()
        val selectedProvider = providers.firstOrNull { it.id == intent.providerId } ?: return
        val current = getOrNull<MainUiState.Normal>() ?: return
        val promptBehaviorState = current.settingsState.promptBehaviorState
        // 刷新模型供应商面板及后处理能力状态
        current.copy(
            selectedPage = MainPage.Settings,
            settingsState = current.settingsState.copy(
                providerState = buildProviderSettingsState(providers, selectedProvider),
                promptBehaviorState = promptBehaviorState.copy(
                    providerPostProcessingState = MainProviderPostProcessingState.Available(
                        selectedProvider.postProcessingMode()
                    )
                )
            )
        ).setup()
    }

    /** 切换是否启用长对话自动总结功能。 */
    @UiIntentObserver(MainUiIntent.ToggleAutoSummaryEnabled::class)
    private fun onToggleAutoSummaryEnabled(intent: MainUiIntent.ToggleAutoSummaryEnabled) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.autoSummaryEnabled = intent.enabled
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                summaryState = uiState.settingsState.summaryState.copy(
                    autoSummaryEnabled = intent.enabled
                )
            )
        ).setup()
    }

    /** 选择用于执行会话总结的专用 LLM 供应商（ID 为 0 时继承当前主供应商）。 */
    @UiIntentObserver(MainUiIntent.SelectSummaryProvider::class)
    private fun onSelectSummaryProvider(intent: MainUiIntent.SelectSummaryProvider) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val summaryState = uiState.settingsState.summaryState
        if (intent.providerId != 0L && summaryState.providers.none { it.id == intent.providerId }) {
            return
        }
        AppModel.summaryLLMProvider = intent.providerId
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                summaryState = summaryState.copy(
                    selectedProviderId = intent.providerId,
                    providers = summaryState.providers.filter {
                        it.isEnabled || it.id == intent.providerId
                    }
                )
            )
        ).setup()
    }

    /** 修改自动总结的触发消息轮数阈值。 */
    @UiIntentObserver(MainUiIntent.ChangeSummaryTriggerMessageCount::class)
    private fun onChangeSummaryTriggerMessageCount(intent: MainUiIntent.ChangeSummaryTriggerMessageCount) {
        updateSettingsInt(intent.value, minimum = 1) {
            AppModel.summaryTriggerMessageCount = it
            copy(
                summaryState = summaryState.copy(triggerMessageCount = it)
            )
        }
    }

    /** 修改总结生成的目标字数限制。 */
    @UiIntentObserver(MainUiIntent.ChangeSummaryWordsLimit::class)
    private fun onChangeSummaryWordsLimit(intent: MainUiIntent.ChangeSummaryWordsLimit) {
        updateSettingsInt(intent.value, minimum = 50) {
            AppModel.summaryWordsLimit = it
            copy(
                summaryState = summaryState.copy(wordsLimit = it)
            )
        }
    }

    /** 修改单次总结请求包含的最大历史消息条数。 */
    @UiIntentObserver(MainUiIntent.ChangeSummaryMaxMessagesPerRequest::class)
    private fun onChangeSummaryMaxMessagesPerRequest(intent: MainUiIntent.ChangeSummaryMaxMessagesPerRequest) {
        updateSettingsInt(intent.value, minimum = 0) {
            AppModel.summaryMaxMessagesPerRequest = it
            copy(
                summaryState = summaryState.copy(maxMessagesPerRequest = it)
            )
        }
    }

    /** 修改总结生成请求的最大 Token 限制。 */
    @UiIntentObserver(MainUiIntent.ChangeSummaryResponseTokens::class)
    private fun onChangeSummaryResponseTokens(intent: MainUiIntent.ChangeSummaryResponseTokens) {
        updateSettingsInt(intent.value, minimum = 128) {
            AppModel.summaryResponseTokens = it
            copy(
                summaryState = summaryState.copy(responseTokens = it)
            )
        }
    }

    /** 切换会话总结设置页内的子标签页。 */
    @UiIntentObserver(MainUiIntent.SelectSummarySettingsTab::class)
    private fun onSelectSummarySettingsTab(intent: MainUiIntent.SelectSummarySettingsTab) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                summaryState = uiState.settingsState.summaryState.copy(
                    selectedTab = intent.tab
                )
            )
        ).setup()
    }

    /** 选择总结内容在 Prompt 上下文中的注入位置（系统提示词 / 对话历史中）。 */
    @UiIntentObserver(MainUiIntent.SelectSummaryInjectionPosition::class)
    private fun onSelectSummaryInjectionPosition(
        intent: MainUiIntent.SelectSummaryInjectionPosition
    ) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.summaryInjectionPosition = intent.position.persistedValue
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                summaryState = uiState.settingsState.summaryState.copy(
                    injectionState = buildSummaryInjectionState(intent.position)
                )
            )
        ).setup()
    }

    /** 修改总结在对话历史中注入的倒数消息深度（Depth）。 */
    @UiIntentObserver(MainUiIntent.ChangeSummaryInjectionDepth::class)
    private fun onChangeSummaryInjectionDepth(intent: MainUiIntent.ChangeSummaryInjectionDepth) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val injectionState = uiState.settingsState.summaryState.injectionState
            as? MainSummaryInjectionState.InChat
            ?: return
        val value = intent.value.toIntOrNull()?.coerceAtLeast(0) ?: 0
        AppModel.summaryInjectionDepth = value
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                summaryState = uiState.settingsState.summaryState.copy(
                    injectionState = injectionState.copy(depth = value)
                )
            )
        ).setup()
    }

    /** 选择总结注入为消息时的角色标识（System / User / Assistant）。 */
    @UiIntentObserver(MainUiIntent.SelectSummaryInjectionRole::class)
    private fun onSelectSummaryInjectionRole(intent: MainUiIntent.SelectSummaryInjectionRole) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val injectionState = uiState.settingsState.summaryState.injectionState
            as? MainSummaryInjectionState.InChat
            ?: return
        AppModel.summaryInjectionRole = intent.role.persistedValue
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                summaryState = uiState.settingsState.summaryState.copy(
                    injectionState = injectionState.copy(role = intent.role)
                )
            )
        ).setup()
    }

    /** 切换是否启用全局流式响应输出（SSE Stream）。 */
    @UiIntentObserver(MainUiIntent.ToggleStreamEnabled::class)
    private fun onToggleStreamEnabled(intent: MainUiIntent.ToggleStreamEnabled) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.streamEnabled = intent.enabled
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                promptBehaviorState = uiState.settingsState.promptBehaviorState.copy(
                    streamEnabled = intent.enabled
                )
            )
        ).setup()
    }

    /** 选择模型特定的 Prompt 后处理模式（如 Raw、Claude 强化、DeepSeek 优化等）。 */
    @UiIntentObserver(MainUiIntent.SelectPostProcessingMode::class)
    private suspend fun onSelectPostProcessingMode(intent: MainUiIntent.SelectPostProcessingMode) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val providerState = uiState.settingsState.providerState
            as? MainProviderSettingsState.Available
            ?: return
        // 在 IO 线程加载当前供应商
        val provider = withContext(Dispatchers.IO) {
            mLLMRepository.getProviderById(providerState.selectedProviderId)
        } ?: return
        val updatedProvider = provider.copy(promptPostProcessingMode = intent.mode.ordinal)
        // 保存后处理模式更新
        withContext(Dispatchers.IO) {
            mLLMRepository.saveProvider(updatedProvider)
        }
        val current = getOrNull<MainUiState.Normal>() ?: return
        val currentProviderState = current.settingsState.providerState
            as? MainProviderSettingsState.Available
            ?: return
        if (currentProviderState.selectedProviderId != updatedProvider.id) return
        // 同步刷新后处理状态
        current.copy(
            settingsState = current.settingsState.copy(
                promptBehaviorState = current.settingsState.promptBehaviorState.copy(
                    providerPostProcessingState = MainProviderPostProcessingState.Available(
                        intent.mode
                    )
                )
            )
        ).setup()
    }

    /** 切换历史消息中的思考过程（Think Blocks）是否回传给模型作为上下文。 */
    @UiIntentObserver(MainUiIntent.ToggleIncludeThinkInContext::class)
    private fun onToggleIncludeThinkInContext(intent: MainUiIntent.ToggleIncludeThinkInContext) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.includeThinkInContext = intent.enabled
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                promptBehaviorState = uiState.settingsState.promptBehaviorState.copy(
                    includeThinkInContext = intent.enabled
                )
            )
        ).setup()
    }

    /** 修改世界书占上下文总量的百分比上限预算。 */
    @UiIntentObserver(MainUiIntent.ChangeWorldInfoBudgetPercent::class)
    private fun onChangeWorldInfoBudgetPercent(intent: MainUiIntent.ChangeWorldInfoBudgetPercent) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val percent = intent.value.coerceIn(0, 100)
        AppModel.worldInfoBudgetPercent = percent
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                worldInfoBudgetState = uiState.settingsState.worldInfoBudgetState.copy(
                    budgetPercent = percent
                )
            )
        ).setup()
    }

    /** 修改世界书硬性 Token 上限预算（Cap）。 */
    @UiIntentObserver(MainUiIntent.ChangeWorldInfoBudgetCap::class)
    private fun onChangeWorldInfoBudgetCap(intent: MainUiIntent.ChangeWorldInfoBudgetCap) {
        updateSettingsInt(intent.value, minimum = 0) {
            AppModel.worldInfoBudgetCap = it
            copy(
                worldInfoBudgetState = worldInfoBudgetState.copy(budgetCap = it)
            )
        }
    }

    /** 切换当世界书条目超出预算被裁切时是否弹出 Toast 警报。 */
    @UiIntentObserver(MainUiIntent.ToggleWorldInfoOverflowAlert::class)
    private fun onToggleWorldInfoOverflowAlert(intent: MainUiIntent.ToggleWorldInfoOverflowAlert) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.worldInfoOverflowAlert = intent.enabled
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                worldInfoBudgetState = uiState.settingsState.worldInfoBudgetState.copy(
                    overflowAlert = intent.enabled
                )
            )
        ).setup()
    }

    /** 切换当历史消息因超限被裁切时是否弹出 Toast 警报。 */
    @UiIntentObserver(MainUiIntent.ToggleContextTrimmingAlert::class)
    private fun onToggleContextTrimmingAlert(intent: MainUiIntent.ToggleContextTrimmingAlert) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.contextTrimmingAlert = intent.enabled
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                promptBehaviorState = uiState.settingsState.promptBehaviorState.copy(
                    contextTrimmingAlert = intent.enabled
                )
            )
        ).setup()
    }

    /** 选择示例对话（MES / Example Dialogue）的注入与展示行为。 */
    @UiIntentObserver(MainUiIntent.SelectExampleDialogueBehavior::class)
    private fun onSelectExampleDialogueBehavior(
        intent: MainUiIntent.SelectExampleDialogueBehavior
    ) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.exampleDialogueBehavior = intent.behavior.persistedValue
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                promptBehaviorState = uiState.settingsState.promptBehaviorState.copy(
                    exampleDialogueBehavior = intent.behavior
                )
            )
        ).setup()
    }

    /** 切换是否开启全局 Debug 调试模式（影响日志捕获与详细错误输出）。 */
    @UiIntentObserver(MainUiIntent.ToggleDebugModeEnabled::class)
    private fun onToggleDebugModeEnabled(intent: MainUiIntent.ToggleDebugModeEnabled) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.debugModeEnabled = intent.enabled
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                debugState = uiState.settingsState.debugState.copy(enabled = intent.enabled)
            )
        ).setup()
    }

    /**
     * 在 IO 线程并发装配首页各组件数据。
     *
     * 包括：角色总数、世界书总数、按角色聚合的单聊分组、群聊摘要列表及故事概览列表。
     */
    private suspend fun buildHomeState(): MainHomeState {
        return withContext(Dispatchers.IO) {
            // 并发拉取基础实体与会话概览
            val characters = mCharacterRepository.getAllCharacters()
            val characterMap = characters.associateBy { it.id }
            val sessions = mChatRepository.getSessionOverviews()
            val groupSessions = mGroupChatRepository.getSessionOverviews()
            val storyOverviews = mStoryRepository.getStoryOverviews()
            // 将单聊会话按角色聚合分组
            val sessionItems = sessions.map { session ->
                session.toUiModel(characterMap[session.characterId])
            }
            val sessionGroups = sessionItems.groupBy { it.characterId }.map { (id, items) ->
                MainChatSessionGroup(
                    characterId = id,
                    characterName = items.firstOrNull()?.characterName.orEmpty(),
                    sessions = items
                )
            }
            // 格式化群聊会话列表项
            val groupChatItems = groupSessions.map { session ->
                MainGroupChatSessionItem(
                    id = session.id.toString(),
                    title = session.title,
                    memberNames = session.memberNames,
                    preview = session.latestMessageContent
                        ?.stripThinkBlocks()
                        ?.takeIf { it.isNotBlank() }
                        ?: mContext.getString(R.string.no_messages_yet),
                    messageCount = session.messageCount,
                    updatedAt = session.latestTime.formatTimestamp("MM-dd HH:mm"),
                    latestTime = session.latestTime
                )
            }
            // 格式化故事列表项
            val storyItems = storyOverviews.map { story ->
                MainStoryItem(
                    id = story.id,
                    title = story.title,
                    preview = story.preview.replace(WHITESPACE_REGEX, " ").trim(),
                    contentCharacterCount = story.contentCharacterCount,
                    updatedAt = story.latestTime.formatTimestamp("MM-dd HH:mm"),
                    latestTime = story.latestTime
                )
            }
            // 装配完整的首页状态模型
            MainHomeState(
                resourceState = MainHomeResourceState(
                    totalCharacters = characters.size,
                    totalWorldBooks = mLorebookRepository.getAllLorebooks().size
                ),
                recentChatsState = if (sessionGroups.isEmpty()) {
                    MainRecentChatsState.Empty
                } else {
                    MainRecentChatsState.Content(sessionGroups = sessionGroups)
                },
                recentGroupChatsState = if (groupChatItems.isEmpty()) {
                    MainRecentGroupChatsState.Empty
                } else {
                    MainRecentGroupChatsState.Content(sessions = groupChatItems)
                },
                recentStoriesState = if (storyItems.isEmpty()) {
                    MainRecentStoriesState.Empty
                } else {
                    MainRecentStoriesState.Content(stories = storyItems)
                },
                allRecentItems = mergeAllRecentItems(
                    chats = sessionItems,
                    groupChats = groupChatItems,
                    stories = storyItems
                )
            )
        }
    }

    /** 装配设置页全量子状态（用户身份、供应商模型参数、提示词行为、预算、总结与调试）。 */
    private suspend fun buildSettingsState(
        providers: List<LLMProvider>,
        selectedProvider: LLMProvider?,
        allProviders: List<LLMProvider>
    ): MainSettingsState {
        return MainSettingsState(
            identityState = MainUserIdentityState(
                userName = AppModel.resolvedUserName,
                userDescription = AppModel.userDescription,
                userDescriptionPreview = resolveCharacterUserMacros(
                    template = AppModel.userDescription,
                    characterName = null,
                    userName = AppModel.resolvedUserName
                ),
                avatarState = if (AppModel.userAvatar.isBlank()) {
                    MainUserAvatarState.None
                } else {
                    MainUserAvatarState.Configured(resolveUserAvatarImage())
                }
            ),
            providerState = buildProviderSettingsState(providers, selectedProvider),
            promptBehaviorState = MainPromptBehaviorState(
                providerPostProcessingState = selectedProvider?.let {
                    MainProviderPostProcessingState.Available(it.postProcessingMode())
                } ?: MainProviderPostProcessingState.Unavailable,
                exampleDialogueBehavior = readExampleDialogueBehavior(),
                includeThinkInContext = AppModel.includeThinkInContext,
                contextTrimmingAlert = AppModel.contextTrimmingAlert,
                streamEnabled = AppModel.streamEnabled
            ),
            worldInfoBudgetState = MainWorldInfoBudgetState(
                budgetPercent = AppModel.worldInfoBudgetPercent.coerceIn(0, 100),
                budgetCap = AppModel.worldInfoBudgetCap.coerceAtLeast(0),
                overflowAlert = AppModel.worldInfoOverflowAlert
            ),
            summaryState = MainSummarySettingsState(
                selectedProviderId = AppModel.summaryLLMProvider,
                providers = allProviders
                    .filter { it.isEnabled || it.id == AppModel.summaryLLMProvider }
                    .map { it.toMainProviderItem() },
                autoSummaryEnabled = AppModel.autoSummaryEnabled,
                triggerMessageCount = AppModel.summaryTriggerMessageCount,
                wordsLimit = AppModel.summaryWordsLimit,
                maxMessagesPerRequest = AppModel.summaryMaxMessagesPerRequest,
                responseTokens = AppModel.summaryResponseTokens,
                injectionState = buildSummaryInjectionState(readSummaryInjectionPosition())
            ),
            debugState = MainDebugSettingsState(
                enabled = AppModel.debugModeEnabled
            )
        )
    }

    /** 构建供应商配置子状态，无可用供应商时返回 Empty。 */
    private fun buildProviderSettingsState(
        providers: List<LLMProvider>,
        selectedProvider: LLMProvider?
    ): MainProviderSettingsState {
        if (selectedProvider == null) return MainProviderSettingsState.Empty
        return MainProviderSettingsState.Available(
            selectedProviderId = selectedProvider.id,
            providers = providers.map { it.toMainProviderItem() },
            generationParametersState = selectedProvider.toGenerationParametersState()
        )
    }

    /** 构建总结注入配置子状态（根据注入位置分别构建 System 或 InChat 配置）。 */
    private fun buildSummaryInjectionState(
        position: SummaryInjectionPosition
    ): MainSummaryInjectionState {
        return position.toMainSummaryInjectionState(
            depth = AppModel.summaryInjectionDepth,
            role = readSummaryInjectionRole()
        )
    }

    /** 解析当前供应商生效的 Prompt 后处理模式枚举。 */
    private fun LLMProvider.postProcessingMode(): PromptPostProcessingMode {
        return PromptPostProcessingMode.fromOrdinal(promptPostProcessingMode)
    }

    /** 将 LLMProvider 映射为主页供应商下拉列表展示项。 */
    private fun LLMProvider.toMainProviderItem(): MainProviderItem {
        return MainProviderItem(
            id = id,
            name = name,
            baseUrl = baseUrl,
            model = model,
            isEnabled = isEnabled
        )
    }

    /** 将 Character 实体映射为导入角色绑定弹窗中的条目。 */
    private fun Character.toImportCharacterItem(): MainImportCharacterItem {
        return MainImportCharacterItem(
            id = id,
            name = name,
            details = creator.takeIf { it.isNotBlank() }
                ?: description.lineSequence().firstOrNull().orEmpty().take(80)
        )
    }

    /** 将 LLMProvider 的生成参数映射为可编辑状态模型。 */
    private fun LLMProvider.toGenerationParametersState(): MainGenerationParametersState {
        return MainGenerationParametersState(
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
            contextTokens = contextTokens
        )
    }

    /** 读取持久化的总结注入位置枚举。 */
    private fun readSummaryInjectionPosition(): SummaryInjectionPosition {
        return SummaryInjectionPosition.fromPersistedValue(AppModel.summaryInjectionPosition)
    }

    /** 读取持久化的示例对话行为枚举。 */
    private fun readExampleDialogueBehavior(): ExampleDialogueBehavior {
        return ExampleDialogueBehavior.fromPersistedValue(AppModel.exampleDialogueBehavior)
    }

    /** 读取持久化的总结注入角色枚举。 */
    private fun readSummaryInjectionRole(): SummaryInjectionRole {
        return SummaryInjectionRole.fromPersistedValue(AppModel.summaryInjectionRole)
    }

    /** 异步加载用户自定义头像 Bitmap 并转换为 Compose 的 ImageBitmap。 */
    private suspend fun resolveUserAvatarImage() =
        AppModel.userAvatar
            .takeIf { it.isNotBlank() }
            ?.let {
                withContext(Dispatchers.IO) {
                    mFileRepository.loadAvatarBitmap(it)?.asImageBitmap()
                }
            }

    /** 从当前首页状态中按 ID 查找故事。 */
    private fun MainHomeState.findStory(storyId: Long): MainStoryItem? {
        val content = recentStoriesState as? MainRecentStoriesState.Content ?: return null
        return content.stories.firstOrNull { it.id == storyId }
    }

    /** 从当前首页快照中读取可重命名内容的标题。 */
    private fun MainHomeState.findItemTitle(item: MainHomeItemSelection): String? {
        return when (item.type) {
            MainHomeItemType.Chat -> allRecentItems
                .filterIsInstance<MainChatSessionItem>()
                .firstOrNull { it.id == item.itemId }
                ?.title
            MainHomeItemType.GroupChat -> allRecentItems
                .filterIsInstance<MainGroupChatSessionItem>()
                .firstOrNull { it.id == item.itemId }
                ?.title
            MainHomeItemType.Story -> item.itemId.toLongOrNull()
                ?.let { storyId -> findStory(storyId) }
                ?.title
        }
    }

    /** 辅助函数：安全解析整数设置项并应用更新。 */
    private fun updateSettingsInt(
        value: String,
        minimum: Int,
        update: MainSettingsState.(Int) -> MainSettingsState
    ) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val intValue = value.toIntOrNull()?.coerceAtLeast(minimum) ?: minimum
        uiState.copy(
            settingsState = uiState.settingsState.update(intValue)
        ).setup()
    }

    /** 将 ChatSessionOverview 转换为首页单聊列表项展示模型。 */
    private fun ChatSessionOverview.toUiModel(character: Character?): MainChatSessionItem {
        return MainChatSessionItem(
            id = id.toString(),
            characterId = characterId.toString(),
            characterName = character?.name.orEmpty().ifBlank { mContext.getString(R.string.unknown_character) },
            title = title,
            preview = latestMessageContent?.stripThinkBlocks()?.takeIf { it.isNotBlank() } ?: mContext.getString(R.string.no_messages_yet),
            messageCount = messageCount,
            updatedAt = latestTime.formatTimestamp("MM-dd HH:mm"),
            latestTime = latestTime
        )
    }
}
