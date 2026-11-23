package me.kafuuneko.rpclient.feature.storycreate

import android.os.Bundle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.storycreate.model.StoryCreateForm
import me.kafuuneko.rpclient.feature.storycreate.model.StoryCreateLorebookGroupItem
import me.kafuuneko.rpclient.feature.storycreate.model.StoryCreateOptions
import me.kafuuneko.rpclient.feature.storycreate.model.filterCharactersForQuery
import me.kafuuneko.rpclient.feature.storycreate.model.filterForQuery
import me.kafuuneko.rpclient.feature.storycreate.model.toStorageValue
import me.kafuuneko.rpclient.feature.storycreate.model.toStoryCreateCharacterItem
import me.kafuuneko.rpclient.feature.storycreate.model.toStoryCreateLorebookGroups
import me.kafuuneko.rpclient.feature.storycreate.presentation.StoryCreateDialogState
import me.kafuuneko.rpclient.feature.storycreate.presentation.StoryCreateLoadState
import me.kafuuneko.rpclient.feature.storycreate.presentation.StoryCreateUiIntent
import me.kafuuneko.rpclient.feature.storycreate.presentation.StoryCreateUiState
import me.kafuuneko.rpclient.feature.storyeditor.StoryEditorActivity
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.media.CharacterAvatarLoader
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.libs.room.repository.StoryCharacterSelection
import me.kafuuneko.rpclient.libs.room.repository.StoryLorebookEntrySelection
import me.kafuuneko.rpclient.libs.room.repository.StoryRepository
import me.kafuuneko.rpclient.libs.room.repository.UserPersonaRepository
import me.kafuuneko.rpclient.ui.model.selectedWorldBookSummaries
import me.kafuuneko.rpclient.utils.matchesCharacterQuery
import me.kafuuneko.rpclient.utils.toDefaultChatTitle
import me.kafuuneko.rpclient.utils.toggle
import me.kafuuneko.rpclient.utils.toggleAll
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 新建 Story 页状态持有者。
 *
 * 核心职责：
 * - 异步加载所有候选角色与带有条目的世界书分组列表；
 * - 维护故事标题输入、参演角色选择以及关联世界书条目的联动勾选；
 * - 支持角色名称、简介、标签以及世界书内容的模糊检索过滤；
 * - 保证故事实体、参演角色配置与世界书条目的原子级事务创建，并在成功后导航至故事编辑器。
 */
class StoryCreateViewModel : CoreViewModelWithEvent<StoryCreateUiIntent, StoryCreateUiState>(
    StoryCreateUiState.None
), KoinComponent {
    private var mCharacterSearchJob: Job? = null
    private val mPersonas by inject<UserPersonaRepository>()
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mFileRepository by inject<FileRepository>()
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mStoryRepository by inject<StoryRepository>()
    private val mAvatarLoader by lazy {
        CharacterAvatarLoader(
            mFileRepository = mFileRepository,
            mScope = viewModelScope,
            mOnAvatarsUpdated = ::publishCharacterAvatars,
        )
    }

    /** 初始化页面，拉取数据库中的角色列表与世界书条目候选数据。 */
    @UiIntentObserver(StoryCreateUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<StoryCreateUiState.None>()) return
        StoryCreateUiState.Normal().setup()
        try {
            // 在 IO 线程拉取角色与世界书选项，避免阻塞页面首帧。
            val data = withContext(Dispatchers.IO) { loadOptions() }
            val current = getOrNull<StoryCreateUiState.Normal>() ?: return
            mAvatarLoader.registerCharacters(data.avatarUuids)
            // 装载候选数据并迁移至 Ready 状态
            current.copy(
                loadState = StoryCreateLoadState.Ready,
                form = current.form.copy(persona = mPersonas.selection(-1L)),
                characters = data.characters,
                lorebookGroups = data.lorebookGroups,
            ).setup()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            val current = getOrNull<StoryCreateUiState.Normal>() ?: return
            current.copy(loadState = StoryCreateLoadState.Ready).setup()
            AppViewEvent.PopupToastMessageByResId(R.string.story_settings_load_failed).tryEmit()
        }
    }

    /** 处理返回操作，创建进行中时禁止退出。 */
    @UiIntentObserver(StoryCreateUiIntent.Back::class)
    private fun onBack() {
        val uiState = getOrNull<StoryCreateUiState.Normal>() ?: return
        if (uiState.loadState == StoryCreateLoadState.Creating) return
        StoryCreateUiState.finished(uiState).setup()
    }

    /** 修改故事标题。 */
    @UiIntentObserver(StoryCreateUiIntent.ChangeTitle::class)
    private fun onChangeTitle(intent: StoryCreateUiIntent.ChangeTitle) {
        updateReadyForm { copy(title = intent.value) }
    }

    /**
     * 设置新故事使用的人设，并刷新角色宏预览。
     * @param intent 用户选择的人设 ID，包含跟随默认人设的选项。
     */
    @UiIntentObserver(StoryCreateUiIntent.SelectPersona::class)
    private suspend fun onSelectPersona(intent: StoryCreateUiIntent.SelectPersona) {
        val state = readyState() ?: return
        mCharacterSearchJob?.cancel()
        val name = mPersonas.get(intent.id).name
        val lorebookNames = state.lorebookGroups.associate { it.lorebookId to it.lorebookName }
        // 在 IO 线程重载角色并按新身份重新展开描述中的用户名宏
        val rawCharacters = withContext(Dispatchers.IO) {
            mCharacterRepository.getAllCharacters()
        }
        val current = readyState() ?: return
        val characters = rawCharacters.map { character ->
            character.toStoryCreateCharacterItem(userName = name, lorebookNames = lorebookNames)
        }
        val dialogState = when (val dialog = current.dialogState) {
            is StoryCreateDialogState.CharacterPicker -> dialog.copy(
                visibleCharacters = characters.filterCharactersForQuery(
                    query = dialog.query,
                    selectedIds = dialog.selectedCharacterIds.takeIf { dialog.showSelectedOnly },
                ),
            )

            else -> dialog
        }
        current.copy(
            form = current.form.copy(persona = current.form.persona.copy(selectedId = intent.id)),
            characters = characters,
            selectedCharacters = characters.filter { it.id in current.form.selectedCharacterIds },
            dialogState = dialogState,
        ).setup()
        mAvatarLoader.registerCharacters(rawCharacters.associate { it.id to it.avatar })
    }

    /** 打开角色参考多选检索弹窗，并以当前已选角色初始化草稿集合。 */
    @UiIntentObserver(StoryCreateUiIntent.ShowCharacterPicker::class)
    private fun onShowCharacterPicker() {
        val uiState = readyState() ?: return
        if (uiState.characters.isEmpty()) return
        mCharacterSearchJob?.cancel()
        uiState.copy(
            dialogState = StoryCreateDialogState.CharacterPicker(
                query = "",
                showSelectedOnly = false,
                visibleCharacters = uiState.characters,
                selectedCharacterOrder = uiState.form.selectedCharacterIds.toList(),
            ),
        ).setup()
    }

    /**
     * 根据角色参考选择器弹窗当前可见窗口按需加载头像缩略图。
     * @param intent 选择器视口内的角色 ID 与头像像素尺寸。
     */
    @UiIntentObserver(StoryCreateUiIntent.VisiblePickerCharactersChanged::class)
    private fun onVisiblePickerCharactersChanged(
        intent: StoryCreateUiIntent.VisiblePickerCharactersChanged,
    ) {
        val uiState = readyState() ?: return
        if (uiState.dialogState !is StoryCreateDialogState.CharacterPicker) return
        mAvatarLoader.updateVisibleCharacters(
            characterIds = intent.characterIds,
            targetSizePx = intent.targetSizePx,
        )
    }

    /**
     * 更新表单视口中的已选角色头像，与选择弹窗的视口独立管理。
     * @param intent 可见角色 ID 和头像显示尺寸。
     */
    @UiIntentObserver(StoryCreateUiIntent.VisibleFormCharactersChanged::class)
    private fun onVisibleFormCharactersChanged(intent: StoryCreateUiIntent.VisibleFormCharactersChanged) {
        val state = getOrNull<StoryCreateUiState.Normal>() ?: return
        mAvatarLoader.updatePinnedCharacters(
            characterIds = intent.characterIds.intersect(state.form.selectedCharacterIds),
            targetSizePx = intent.targetSizePx,
        )
    }

    /**
     * 在角色参考选择器弹窗内后台筛选角色，取消旧任务并保留最新勾选状态。
     * @param intent 用于过滤角色名称和内容的搜索词。
     */
    @UiIntentObserver(StoryCreateUiIntent.ChangeCharacterQuery::class)
    private fun onChangeCharacterQuery(intent: StoryCreateUiIntent.ChangeCharacterQuery) {
        val uiState = readyState() ?: return
        val picker = uiState.dialogState as? StoryCreateDialogState.CharacterPicker ?: return
        mCharacterSearchJob?.cancel()
        uiState.copy(dialogState = picker.copy(query = intent.value)).setup()
        // 后台计算命中的角色 ID，回写时核对最新搜索词并叠加已选筛选
        mCharacterSearchJob = viewModelScope.launch {
            val matchedIds = withContext(Dispatchers.Default) {
                val context = currentCoroutineContext()
                uiState.characters.filter { character ->
                    context.ensureActive()
                    matchesCharacterQuery(
                        name = character.name,
                        description = character.description,
                        tags = character.tags,
                        query = intent.value,
                    )
                }.mapTo(HashSet()) { character -> character.id }
            }
            val current = readyState() ?: return@launch
            val currentPicker = current.dialogState as? StoryCreateDialogState.CharacterPicker
                ?: return@launch
            if (currentPicker.query == intent.value) {
                current.copy(
                    dialogState = currentPicker.copy(
                        visibleCharacters = current.characters
                            .filter { character ->
                                character.id in matchedIds &&
                                    (!currentPicker.showSelectedOnly || character.id in currentPicker.selectedCharacterIds)
                            },
                    ),
                ).setup()
            }
        }
    }

    /**
     * 切换角色选择器弹窗中的“仅看已选”筛选状态。
     * @param intent 是否只显示已勾选的角色。
     */
    @UiIntentObserver(StoryCreateUiIntent.ToggleCharacterPickerSelectedOnly::class)
    private fun onToggleCharacterPickerSelectedOnly(
        intent: StoryCreateUiIntent.ToggleCharacterPickerSelectedOnly,
    ) {
        val uiState = readyState() ?: return
        val picker = uiState.dialogState as? StoryCreateDialogState.CharacterPicker ?: return
        mCharacterSearchJob?.cancel()
        uiState.copy(
            dialogState = picker.copy(
                showSelectedOnly = intent.selectedOnly,
                visibleCharacters = uiState.characters.filterCharactersForQuery(
                    query = picker.query,
                    selectedIds = picker.selectedCharacterIds.takeIf { intent.selectedOnly },
                ),
            ),
        ).setup()
    }

    /**
     * 切换角色选择器草稿中单个角色的选中状态。
     * @param intent 在选择器草稿中切换的角色 ID。
     */
    @UiIntentObserver(StoryCreateUiIntent.ToggleCharacterPickerItem::class)
    private fun onToggleCharacterPickerItem(
        intent: StoryCreateUiIntent.ToggleCharacterPickerItem,
    ) {
        val uiState = readyState() ?: return
        val picker = uiState.dialogState as? StoryCreateDialogState.CharacterPicker ?: return
        if (uiState.characters.none { character -> character.id == intent.characterId }) return
        // 取消后再次勾选会追加到末尾，默认主角按最终的勾选顺序确定。
        val nextOrder = if (intent.characterId in picker.selectedCharacterIds) {
            picker.selectedCharacterOrder - intent.characterId
        } else {
            picker.selectedCharacterOrder + intent.characterId
        }
        // 处于仅看已选筛选时同步更新列表，防止已取消勾选的条目滞留视图
        uiState.copy(
            dialogState = picker.copy(
                selectedCharacterOrder = nextOrder,
                visibleCharacters = if (picker.showSelectedOnly) {
                    uiState.characters.filterCharactersForQuery(
                        query = picker.query,
                        selectedIds = nextOrder.toSet(),
                    )
                } else {
                    picker.visibleCharacters
                },
            ),
        ).setup()
    }

    /**
     * 确认角色选择器草稿，保留既有角色的激活模式，并对新加入角色联动勾选其绑定世界书。
     */
    @UiIntentObserver(StoryCreateUiIntent.ConfirmCharacterPicker::class)
    private fun onConfirmCharacterPicker() {
        val uiState = readyState() ?: return
        val picker = uiState.dialogState as? StoryCreateDialogState.CharacterPicker ?: return
        mCharacterSearchJob?.cancel()
        val availableIds = uiState.characters.map { it.id }.toSet()
        val confirmedOrder = picker.selectedCharacterOrder.filter { it in availableIds }
        // 只为本次新增角色补齐关联世界书，保留既有选择与已选角色的设置。
        val addedIds = confirmedOrder.toSet() - uiState.form.selectedCharacterIds
        val addedLorebookIds = uiState.characters
            .filter { character -> character.id in addedIds && character.linkedLorebookId != null }
            .mapNotNullTo(mutableSetOf()) { character -> character.linkedLorebookId }
        val linkedEntryIds = uiState.lorebookGroups
            .filter { group -> group.lorebookId in addedLorebookIds }
            .flatMap { group -> group.entries }
            .mapTo(mutableSetOf()) { entry -> entry.id }
        val nextForm = uiState.form
            .applyCharacterSelection(selectedIdsInOrder = confirmedOrder)
            .copy(selectedLorebookEntryIds = uiState.form.selectedLorebookEntryIds + linkedEntryIds)
        updateCharacterSelection(
            state = uiState.copy(dialogState = StoryCreateDialogState.None),
            form = nextForm,
        )
        mAvatarLoader.clearVisibleCharacters()
    }

    /**
     * 切换单个角色的勾选状态（用于主表单快捷移除已选角色），若新选中则联动绑定世界书条目。
     *
     * @param intent 包含目标角色 ID 的意图
     */
    @UiIntentObserver(StoryCreateUiIntent.ToggleCharacter::class)
    private fun onToggleCharacter(intent: StoryCreateUiIntent.ToggleCharacter) {
        val uiState = readyState() ?: return
        val character = uiState.characters.firstOrNull { it.id == intent.characterId } ?: return
        val selecting = character.id !in uiState.form.selectedCharacterIds
        // 查找角色专属绑定的世界书条目集合
        val linkedEntryIds = character.linkedLorebookId
            ?.let { lorebookId ->
                uiState.lorebookGroups
                    .firstOrNull { group -> group.lorebookId == lorebookId }
                    ?.entries
                    ?.mapTo(mutableSetOf()) { entry -> entry.id }
            }
            .orEmpty()
        // 切换角色选择状态并在初次选中时追加关联条目
        val nextForm = uiState.form.toggleCharacterSelection(character.id).copy(
            selectedLorebookEntryIds = if (selecting) {
                uiState.form.selectedLorebookEntryIds + linkedEntryIds
            } else {
                uiState.form.selectedLorebookEntryIds
            },
        )
        updateCharacterSelection(state = uiState, form = nextForm)
    }

    /** 联动角色选择与世界书摘要，头像由表单视口按需加载。 */
    private fun updateCharacterSelection(state: StoryCreateUiState.Normal, form: StoryCreateForm) {
        state.copy(
            form = form,
            selectedCharacters = state.characters.filter { it.id in form.selectedCharacterIds },
            selectedWorldBookSummaries = state.lorebookGroups.summarizeSelection(form.selectedLorebookEntryIds),
        ).setup()
    }

    /** 设置已选角色的激活模式，并在设置新主角时自动清除旧主角。 */
    @UiIntentObserver(StoryCreateUiIntent.SetCharacterActivationMode::class)
    private fun onSetCharacterActivationMode(
        intent: StoryCreateUiIntent.SetCharacterActivationMode,
    ) {
        val uiState = readyState() ?: return
        if (intent.characterId !in uiState.form.selectedCharacterIds) return
        updateReadyForm {
            setCharacterActivationMode(intent.characterId, intent.activationMode)
        }
    }

    /** 打开世界书条目选择器弹窗，并以当前表单启用集合初始化草稿。 */
    @UiIntentObserver(StoryCreateUiIntent.ShowLorebookPicker::class)
    private fun onShowLorebookPicker() {
        val uiState = readyState() ?: return
        if (uiState.lorebookGroups.isEmpty()) return
        uiState.copy(
            dialogState = StoryCreateDialogState.LorebookPicker(
                query = "",
                showSelectedOnly = false,
                visibleGroups = uiState.lorebookGroups,
                enabledEntryIds = uiState.form.selectedLorebookEntryIds,
            ),
        ).setup()
    }

    /**
     * 修改世界书选择器弹窗中的检索关键词，实时过滤分组与条目。
     * @param intent 用于匹配世界书名称、关键词或条目正文的搜索词。
     */
    @UiIntentObserver(StoryCreateUiIntent.ChangeLorebookQuery::class)
    private fun onChangeLorebookQuery(intent: StoryCreateUiIntent.ChangeLorebookQuery) {
        val uiState = readyState() ?: return
        val picker = uiState.dialogState as? StoryCreateDialogState.LorebookPicker ?: return
        uiState.copy(
            dialogState = picker.copy(
                query = intent.value,
                visibleGroups = uiState.lorebookGroups.filterForQuery(
                    query = intent.value,
                    selectedEntryIds = picker.enabledEntryIds.takeIf { picker.showSelectedOnly },
                ),
            ),
        ).setup()
    }

    /**
     * 切换世界书选择器弹窗中的“仅看已启用”筛选状态。
     * @param intent 是否将选择器限定在已勾选的条目。
     */
    @UiIntentObserver(StoryCreateUiIntent.ToggleLorebookPickerSelectedOnly::class)
    private fun onToggleLorebookPickerSelectedOnly(
        intent: StoryCreateUiIntent.ToggleLorebookPickerSelectedOnly,
    ) {
        val uiState = readyState() ?: return
        val picker = uiState.dialogState as? StoryCreateDialogState.LorebookPicker ?: return
        uiState.copy(
            dialogState = picker.copy(
                showSelectedOnly = intent.selectedOnly,
                visibleGroups = uiState.lorebookGroups.filterForQuery(
                    query = picker.query,
                    selectedEntryIds = picker.enabledEntryIds.takeIf { intent.selectedOnly },
                ),
            ),
        ).setup()
    }

    /**
     * 批量切换世界书选择器草稿中某本世界书下全部条目的选中状态。
     * @param intent 待批量切换全部条目的世界书 ID。
     */
    @UiIntentObserver(StoryCreateUiIntent.ToggleLorebook::class)
    private fun onToggleLorebook(intent: StoryCreateUiIntent.ToggleLorebook) {
        val uiState = readyState() ?: return
        val picker = uiState.dialogState as? StoryCreateDialogState.LorebookPicker ?: return
        // 整书开关始终按完整世界书条目集合切换，不受当前搜索过滤裁剪影响
        val group = uiState.lorebookGroups
            .firstOrNull { it.lorebookId == intent.lorebookId }
            ?: return
        val entryIds = group.entries.mapTo(mutableSetOf()) { it.id }
        if (entryIds.isEmpty()) return
        val nextEnabledIds = picker.enabledEntryIds.toggleAll(entryIds)
        uiState.copy(
            dialogState = picker.copy(
                enabledEntryIds = nextEnabledIds,
                visibleGroups = if (picker.showSelectedOnly) {
                    uiState.lorebookGroups.filterForQuery(
                        query = picker.query,
                        selectedEntryIds = nextEnabledIds,
                    )
                } else {
                    picker.visibleGroups
                },
            ),
        ).setup()
    }

    /**
     * 切换世界书选择器草稿中单个条目的选中状态。
     * @param intent 待切换的世界书条目 ID。
     */
    @UiIntentObserver(StoryCreateUiIntent.ToggleLorebookEntry::class)
    private fun onToggleLorebookEntry(intent: StoryCreateUiIntent.ToggleLorebookEntry) {
        val uiState = readyState() ?: return
        val picker = uiState.dialogState as? StoryCreateDialogState.LorebookPicker ?: return
        // 校验目标条目存在后再更新草稿，并按筛选态同步可见分组
        val entryExists = uiState.lorebookGroups.any { group ->
            group.entries.any { entry -> entry.id == intent.entryId }
        }
        if (!entryExists) return
        val nextEnabledIds = picker.enabledEntryIds.toggle(intent.entryId)
        uiState.copy(
            dialogState = picker.copy(
                enabledEntryIds = nextEnabledIds,
                visibleGroups = if (picker.showSelectedOnly) {
                    uiState.lorebookGroups.filterForQuery(
                        query = picker.query,
                        selectedEntryIds = nextEnabledIds,
                    )
                } else {
                    picker.visibleGroups
                },
            ),
        ).setup()
    }

    /** 确认并提交世界书选择器草稿到故事创建表单。 */
    @UiIntentObserver(StoryCreateUiIntent.ConfirmLorebookPicker::class)
    private fun onConfirmLorebookPicker() {
        val uiState = readyState() ?: return
        val picker = uiState.dialogState as? StoryCreateDialogState.LorebookPicker ?: return
        uiState.copy(
            form = uiState.form.copy(selectedLorebookEntryIds = picker.enabledEntryIds),
            selectedWorldBookSummaries = uiState.lorebookGroups.summarizeSelection(picker.enabledEntryIds),
            dialogState = StoryCreateDialogState.None,
        ).setup()
    }

    /** 关闭当前选择器弹窗并丢弃未提交的临时筛选与勾选草稿。 */
    @UiIntentObserver(StoryCreateUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = readyState() ?: return
        mCharacterSearchJob?.cancel()
        uiState.copy(dialogState = StoryCreateDialogState.None).setup()
        mAvatarLoader.clearVisibleCharacters()
    }

    /** Story 与初始引用必须在同一事务中创建，避免配置校验失败后留下空 Story。 */
    @UiIntentObserver(StoryCreateUiIntent.CreateStory::class)
    private suspend fun onCreateStory() {
        val uiState = readyState() ?: return
        val createTime = System.currentTimeMillis()
        val title = uiState.form.title.trim().ifBlank { createTime.toDefaultChatTitle() }
        // 进入创建中状态
        uiState.copy(loadState = StoryCreateLoadState.Creating).setup()
        try {
            // 在 IO 线程原子事务创建故事、参演角色与关联世界书条目
            val storyId = withContext(Dispatchers.IO) {
                mStoryRepository.createStoryWithConfiguration(
                    title = title,
                    userPersonaId = uiState.form.persona.selectedId,
                    lorebookSelections = uiState.form.selectedLorebookEntryIds
                        .sorted()
                        .map(::StoryLorebookEntrySelection),
                    characterSelections = uiState.selectedCharacters.map { character ->
                        StoryCharacterSelection(
                            characterId = character.id,
                            activationMode = uiState.form.activationModeOf(character.id)
                                .toStorageValue(),
                        )
                    },
                )
            }
            // 启动故事编辑器页面并等待返回
            AppViewEvent.StartActivity(
                activity = StoryEditorActivity::class.java,
                extras = Bundle().apply {
                    putLong(StoryEditorActivity.EXTRA_STORY_ID, storyId)
                },
            ).emitAndAwait()
            // 退出当前新建页
            StoryCreateUiState.finished(uiStateFlow.value).setup()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // 创建失败恢复就绪状态并提示
            val current = getOrNull<StoryCreateUiState.Normal>() ?: return
            current.copy(loadState = StoryCreateLoadState.Ready).setup()
            AppViewEvent.PopupToastMessageByResId(R.string.story_save_failed).tryEmit()
        }
    }

    /** 从数据库拉取所有世界书及其有效条目，以及全量候选角色。 */
    private suspend fun loadOptions(id: Long = -1L): StoryCreateOptions {
        val userName = mPersonas.get(id).name
        val lorebooks = mLorebookRepository.getAllLorebooksWithEntries()
        val lorebookNames = lorebooks.associate { it.lorebook.id to it.lorebook.name }
        // 构建包含条目列表的世界书分组项
        val groups = lorebooks.toStoryCreateLorebookGroups()
        // 构建候选角色列表项与头像 UUID 索引
        val rawCharacters = mCharacterRepository.getAllCharacters()
        val characters = rawCharacters.map { character ->
            character.toStoryCreateCharacterItem(userName, lorebookNames)
        }
        val avatarUuids = rawCharacters.associate { character -> character.id to character.avatar }
        return StoryCreateOptions(
            characters = characters,
            lorebookGroups = groups,
            avatarUuids = avatarUuids,
        )
    }

    /** 更新角色头像快照，保持候选、已选角色和世界书摘要列表稳定。 */
    private fun publishCharacterAvatars(avatars: Map<Long, ImageBitmap>) {
        val uiState = getOrNull<StoryCreateUiState.Normal>() ?: return
        uiState.copy(characterAvatars = avatars).setup()
    }

    /** 从已提交的条目选择生成主表单展示的世界书摘要。 */
    private fun List<StoryCreateLorebookGroupItem>.summarizeSelection(selectedIds: Set<Long>) =
        selectedWorldBookSummaries(
            id = StoryCreateLorebookGroupItem::lorebookId,
            name = StoryCreateLorebookGroupItem::lorebookName,
            totalCount = StoryCreateLorebookGroupItem::entryCount,
            selectedCount = { group -> group.entries.count { entry -> entry.id in selectedIds } },
        )

    /** 随创建页销毁取消头像解码，并释放缓存位图。 */
    override fun onCleared() {
        mAvatarLoader.clear()
        super.onCleared()
    }

    /** 获取处于 Ready 就绪状态的 UI 状态。 */
    private fun readyState(): StoryCreateUiState.Normal? {
        return getOrNull<StoryCreateUiState.Normal>()
            ?.takeIf { it.loadState == StoryCreateLoadState.Ready }
    }

    /** 辅助方法：在 Ready 状态下以不可变方式更新表单数据。 */
    private fun updateReadyForm(update: StoryCreateForm.() -> StoryCreateForm) {
        val uiState = readyState() ?: return
        uiState.copy(form = uiState.form.update()).setup()
    }

}
