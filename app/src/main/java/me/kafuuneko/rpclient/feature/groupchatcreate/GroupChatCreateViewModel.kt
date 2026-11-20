package me.kafuuneko.rpclient.feature.groupchatcreate

import android.os.Bundle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.groupchat.GroupChatActivity
import me.kafuuneko.rpclient.feature.groupchat.model.filterForQuery
import me.kafuuneko.rpclient.feature.groupchat.model.withEnabledIds
import me.kafuuneko.rpclient.feature.groupchatcreate.model.reconcile
import me.kafuuneko.rpclient.feature.groupchatcreate.model.toGroupChatCreateCharacterItem
import me.kafuuneko.rpclient.feature.groupchatcreate.model.toGroupChatCreateLorebookGroups
import me.kafuuneko.rpclient.feature.groupchatcreate.model.toSelection
import me.kafuuneko.rpclient.feature.groupchatcreate.model.visibleFor
import me.kafuuneko.rpclient.feature.groupchatcreate.presentation.GroupChatCreateDialogState
import me.kafuuneko.rpclient.feature.groupchatcreate.presentation.GroupChatCreateLoadState
import me.kafuuneko.rpclient.feature.groupchatcreate.presentation.GroupChatCreateUiIntent
import me.kafuuneko.rpclient.feature.groupchatcreate.presentation.GroupChatCreateUiState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.groupchat.GroupChatGreetingCandidate
import me.kafuuneko.rpclient.libs.groupchat.GroupChatGreetingPlanner
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatLorebookGroupItem
import me.kafuuneko.rpclient.libs.groupchat.model.toEntity
import me.kafuuneko.rpclient.libs.media.CharacterAvatarLoader
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehaviorProvider
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.libs.room.repository.UserPersonaRepository
import me.kafuuneko.rpclient.ui.model.selectedWorldBookSummaries
import me.kafuuneko.rpclient.utils.matchesCharacterQuery
import me.kafuuneko.rpclient.utils.toDefaultChatTitle
import me.kafuuneko.rpclient.utils.toggle
import me.kafuuneko.rpclient.utils.toggleAll
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 新建群聊页状态持有者。
 *
 * 核心职责：
 * - 异步加载所有候选角色与世界书条目分组；
 * - 维护群聊参演成员多选状态（最少选择 2 个角色），并联动专属世界书授权；
 * - 管理群聊轮转激活策略（自然发言 Natural / 顺序轮询 RoundRobin）及连续发言权限；
 * - 管理群聊开场白生成模式（每个角色随机首条 Random / 手动指定角色与条目 Manual / 自定义内容 Custom / 无开场白 None）及成员变动时的状态收敛；
 * - 驱动开场白规划器（GreetingPlanner）生成初始快照并原子事务创建群聊会话。
 */
class GroupChatCreateViewModel :
    CoreViewModelWithEvent<GroupChatCreateUiIntent, GroupChatCreateUiState>(
        GroupChatCreateUiState.None
    ), KoinComponent {
    private var mCharacterSearchJob: Job? = null
    private val mPersonas by inject<UserPersonaRepository>()
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mFileRepository by inject<FileRepository>()
    private val mGroupChatRepository by inject<GroupChatRepository>()
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mGreetingPlanner by inject<GroupChatGreetingPlanner>()
    private val mExampleDialogueBehaviorProvider by inject<ExampleDialogueBehaviorProvider>()
    private val mAvatarLoader by lazy {
        CharacterAvatarLoader(
            mFileRepository = mFileRepository,
            mScope = viewModelScope,
            mOnAvatarsUpdated = ::publishCharacterAvatars,
        )
    }

    /** 切换创建草稿的人设，并重新计算含用户名宏的候选角色。 */
    @UiIntentObserver(GroupChatCreateUiIntent.SelectPersona::class)
    private suspend fun onSelectPersona(intent: GroupChatCreateUiIntent.SelectPersona) {
        val state = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        val selection = state.persona.copy(selectedId = intent.id)
        val name = selection.selectedName ?: return
        mCharacterSearchJob?.cancel()
        // 保留已选群成员，按新身份重建描述和开场白预览
        val selected = state.selectedCharacters.mapTo(mutableSetOf()) { it.id }
        val loadedCharacters = withContext(Dispatchers.IO) {
            mCharacterRepository.getAllCharacters()
        }
        // 人设切换需重新解析全部简介与开场白，批量投影留在后台执行。
        val characters = withContext(Dispatchers.Default) {
            loadedCharacters.map { character ->
                character.toGroupChatCreateCharacterItem(name)
                    .copy(selected = character.id in selected)
            }
        }
        val current = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        val dialogState = when (val dialog = current.dialogState) {
            is GroupChatCreateDialogState.CharacterPicker -> dialog.copy(
                visibleCharacters = characters.visibleFor(
                    query = dialog.query,
                    selectedIds = dialog.selectedCharacterIds.takeIf { dialog.showSelectedOnly },
                ),
            )

            else -> dialog
        }
        current.copy(
            persona = selection,
            characters = characters,
            selectedCharacters = characters.filter { it.selected },
            greetingState = current.greetingState.reconcile(characters),
            dialogState = dialogState,
        ).setup()
        mAvatarLoader.registerCharacters(loadedCharacters.associate { it.id to it.avatar })
    }

    /** 初始化页面，拉取候选角色列表与世界书条目列表。 */
    @UiIntentObserver(GroupChatCreateUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<GroupChatCreateUiState.None>()) return
        GroupChatCreateUiState.Normal(loadState = GroupChatCreateLoadState.Loading).setup()
        val selection = mPersonas.chatSelection()
        val userName = requireNotNull(selection.selectedName)
        // 在 IO 线程查询候选角色与带条目的世界书分组。
        val data = withContext(Dispatchers.IO) {
            val rawCharacters = mCharacterRepository.getAllCharacters()
            val lorebookGroups = mLorebookRepository.getAllLorebooksWithEntries()
                .toGroupChatCreateLorebookGroups()
            rawCharacters to lorebookGroups
        }
        mAvatarLoader.registerCharacters(data.first.associate { it.id to it.avatar })
        // 简介与多条开场白均需完整宏展开，后台投影避免阻塞首屏及加载动画。
        val characters = withContext(Dispatchers.Default) {
            data.first.map { character ->
                character.toGroupChatCreateCharacterItem(userName)
            }
        }
        // 渲染就绪状态
        GroupChatCreateUiState.Normal(
            persona = selection,
            characters = characters,
            showCharacterExamplesSwitch =
                mExampleDialogueBehaviorProvider.current() != ExampleDialogueBehavior.Disabled,
            lorebookGroups = data.second,
        ).setup()
    }

    /** 处理返回操作，迁移至 Finished 状态。 */
    @UiIntentObserver(GroupChatCreateUiIntent.Back::class)
    private fun onBack() {
        if (isStateOf<GroupChatCreateUiState.Finished>()) return
        GroupChatCreateUiState.finished(uiStateFlow.value).setup()
    }

    /** 修改群聊自定义标题。 */
    @UiIntentObserver(GroupChatCreateUiIntent.ChangeTitle::class)
    private fun onChangeTitle(intent: GroupChatCreateUiIntent.ChangeTitle) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        uiState.copy(title = intent.value).setup()
    }

    /** 打开群成员多选检索弹窗，并以当前已选成员初始化草稿集合。 */
    @UiIntentObserver(GroupChatCreateUiIntent.ShowCharacterPicker::class)
    private fun onShowCharacterPicker() {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        if (uiState.loadState != GroupChatCreateLoadState.None || uiState.characters.isEmpty()) return
        mCharacterSearchJob?.cancel()
        // 弹窗以已提交的成员为初始草稿，取消时保留原选择。
        val selectedIds = uiState.selectedCharacters.map { it.id }.toSet()
        uiState.copy(
            dialogState = GroupChatCreateDialogState.CharacterPicker(
                query = "",
                showSelectedOnly = false,
                visibleCharacters = uiState.characters,
                selectedCharacterIds = selectedIds,
            ),
        ).setup()
    }

    /** 根据群成员选择器弹窗当前可见窗口按需加载头像缩略图。 */
    @UiIntentObserver(GroupChatCreateUiIntent.VisiblePickerCharactersChanged::class)
    private fun onVisiblePickerCharactersChanged(
        intent: GroupChatCreateUiIntent.VisiblePickerCharactersChanged,
    ) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        if (uiState.dialogState !is GroupChatCreateDialogState.CharacterPicker) return
        mAvatarLoader.updateVisibleCharacters(
            characterIds = intent.characterIds,
            targetSizePx = intent.targetSizePx,
        )
    }

    /** 在群成员选择器弹窗内后台筛选角色，取消旧任务并保留最新勾选状态。 */
    @UiIntentObserver(GroupChatCreateUiIntent.ChangeSearchQuery::class)
    private fun onChangeSearchQuery(intent: GroupChatCreateUiIntent.ChangeSearchQuery) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        val picker = uiState.dialogState as? GroupChatCreateDialogState.CharacterPicker ?: return
        mCharacterSearchJob?.cancel()
        uiState.copy(dialogState = picker.copy(query = intent.value)).setup()
        // 后台计算命中的角色 ID，回写时结合最新勾选集合与筛选态恢复可见列表
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
            val current = getOrNull<GroupChatCreateUiState.Normal>() ?: return@launch
            val currentPicker = current.dialogState as? GroupChatCreateDialogState.CharacterPicker
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

    /** 切换群成员选择器弹窗中的“仅看已选”筛选状态。 */
    @UiIntentObserver(GroupChatCreateUiIntent.ToggleCharacterPickerSelectedOnly::class)
    private fun onToggleCharacterPickerSelectedOnly(
        intent: GroupChatCreateUiIntent.ToggleCharacterPickerSelectedOnly,
    ) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        val picker = uiState.dialogState as? GroupChatCreateDialogState.CharacterPicker ?: return
        mCharacterSearchJob?.cancel()
        uiState.copy(
            dialogState = picker.copy(
                showSelectedOnly = intent.selectedOnly,
                visibleCharacters = uiState.characters.visibleFor(
                    query = picker.query,
                    selectedIds = picker.selectedCharacterIds.takeIf { intent.selectedOnly },
                ),
            ),
        ).setup()
    }

    /** 切换群成员选择器弹窗草稿中单个角色的选中状态。 */
    @UiIntentObserver(GroupChatCreateUiIntent.ToggleCharacterPickerItem::class)
    private fun onToggleCharacterPickerItem(
        intent: GroupChatCreateUiIntent.ToggleCharacterPickerItem,
    ) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        val picker = uiState.dialogState as? GroupChatCreateDialogState.CharacterPicker ?: return
        if (uiState.characters.none { character -> character.id == intent.characterId }) return
        val nextSelectedIds = picker.selectedCharacterIds.toggle(intent.characterId)
        // 仅看已选视图下同步刷新列表，保证可见成员与选中计数实时一致
        uiState.copy(
            dialogState = picker.copy(
                selectedCharacterIds = nextSelectedIds,
                visibleCharacters = if (picker.showSelectedOnly) {
                    uiState.characters.visibleFor(
                        query = picker.query,
                        selectedIds = nextSelectedIds,
                    )
                } else {
                    picker.visibleCharacters
                },
            ),
        ).setup()
    }

    /**
     * 确认群成员选择器草稿，对新加入角色自动联动其专属世界书并收敛开场白状态。
     */
    @UiIntentObserver(GroupChatCreateUiIntent.ConfirmCharacterPicker::class)
    private fun onConfirmCharacterPicker() {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        val picker = uiState.dialogState as? GroupChatCreateDialogState.CharacterPicker ?: return
        mCharacterSearchJob?.cancel()
        updateMembers(
            state = uiState.copy(dialogState = GroupChatCreateDialogState.None),
            selectedIds = picker.selectedCharacterIds,
        )
        mAvatarLoader.clearVisibleCharacters()
    }

    /**
     * 直接切换群聊成员选中状态（用于主表单已选标签快捷移除），同步收敛开场白候选。
     *
     * @param intent 包含目标角色 ID 的意图
     */
    @UiIntentObserver(GroupChatCreateUiIntent.ToggleCharacter::class)
    private fun onToggleCharacter(intent: GroupChatCreateUiIntent.ToggleCharacter) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        if (uiState.characters.none { it.id == intent.characterId }) return
        val selectedIds = uiState.selectedCharacters.map { it.id }.toSet()
        updateMembers(state = uiState, selectedIds = selectedIds.toggle(intent.characterId))
    }

    /** 同步成员、默认世界书及开场白，摘要与持久化使用同一份选择。 */
    private fun updateMembers(state: GroupChatCreateUiState.Normal, selectedIds: Set<Long>) {
        // 识别本次新勾选的角色，仅对新增成员自动附加专属世界书条目
        val previousSelectedIds = state.selectedCharacters.map { it.id }.toSet()
        val addedIds = selectedIds - previousSelectedIds
        val characters = state.characters.map { character ->
            character.copy(selected = character.id in selectedIds)
        }
        val selectedCharacters = characters.filter { it.selected }
        val addedLorebookIds = selectedCharacters
            .filter { character -> character.id in addedIds && character.characterLorebookId > 0L }
            .mapTo(mutableSetOf()) { character -> character.characterLorebookId }
        val defaultEntryIds = state.lorebookGroups
            .filter { group -> group.lorebookId in addedLorebookIds }
            .flatMap { group -> group.entries }
            .mapTo(mutableSetOf()) { entry -> entry.id }
        val selectedEntryIds = state.selectedLorebookEntryIds + defaultEntryIds
        val lorebookGroups = state.lorebookGroups.withEnabledIds(selectedEntryIds)
        // 联动世界书、开场白与摘要，避免主表单展示与提交内容不一致。
        state.copy(
            characters = characters,
            selectedCharacters = selectedCharacters,
            selectedLorebookEntryIds = selectedEntryIds,
            lorebookGroups = lorebookGroups,
            selectedWorldBookSummaries = lorebookGroups.summarizeSelection(),
            greetingState = state.greetingState.reconcile(characters),
        ).setup()
        mAvatarLoader.updatePinnedCharacters(selectedCharacters.map { it.id }.toSet())
    }

    /** 选择群聊角色轮转激活策略。 */
    @UiIntentObserver(GroupChatCreateUiIntent.SelectStrategy::class)
    private fun onSelectStrategy(intent: GroupChatCreateUiIntent.SelectStrategy) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        uiState.copy(activationStrategy = intent.strategy).setup()
    }

    /** 切换是否允许同一个角色连续发言。 */
    @UiIntentObserver(GroupChatCreateUiIntent.ToggleAllowSelfResponses::class)
    private fun onToggleAllowSelfResponses(
        intent: GroupChatCreateUiIntent.ToggleAllowSelfResponses,
    ) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        uiState.copy(allowSelfResponses = intent.enabled).setup()
    }

    /**
     * 修改待创建群聊的角色卡示例对话注入开关。
     *
     * @param intent 包含开关目标值的用户意图。
     */
    @UiIntentObserver(GroupChatCreateUiIntent.ToggleIncludeCharacterExamples::class)
    private fun onToggleIncludeCharacterExamples(
        intent: GroupChatCreateUiIntent.ToggleIncludeCharacterExamples,
    ) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        uiState.copy(includeCharacterExamples = intent.enabled).setup()
    }

    /** 选择群聊开场白模式。 */
    @UiIntentObserver(GroupChatCreateUiIntent.SelectGreetingMode::class)
    private fun onSelectGreetingMode(
        intent: GroupChatCreateUiIntent.SelectGreetingMode,
    ) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        uiState.copy(
            greetingState = uiState.greetingState
                .copy(mode = intent.mode)
                .reconcile(uiState.characters),
        ).setup()
    }

    /** 选择指定发言的开场白角色。 */
    @UiIntentObserver(GroupChatCreateUiIntent.SelectGreetingCharacter::class)
    private fun onSelectGreetingCharacter(
        intent: GroupChatCreateUiIntent.SelectGreetingCharacter,
    ) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        if (uiState.greetingState.characters.none { it.id == intent.characterId }) return
        uiState.copy(
            greetingState = uiState.greetingState.copy(
                selectedCharacterId = intent.characterId,
                selectedGreetingIndex = 0,
            ),
        ).setup()
    }

    /** 选择指定角色的开场白索引。 */
    @UiIntentObserver(GroupChatCreateUiIntent.SelectGreeting::class)
    private fun onSelectGreeting(intent: GroupChatCreateUiIntent.SelectGreeting) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        val greetings = uiState.greetingState.selectedCharacter?.greetings.orEmpty()
        if (intent.greetingIndex !in greetings.indices) return
        uiState.copy(greetingState = uiState.greetingState.copy(selectedGreetingIndex = intent.greetingIndex))
            .setup()
    }

    /** 修改自定义开场白文本内容。 */
    @UiIntentObserver(GroupChatCreateUiIntent.ChangeCustomGreeting::class)
    private fun onChangeCustomGreeting(
        intent: GroupChatCreateUiIntent.ChangeCustomGreeting,
    ) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        uiState.copy(greetingState = uiState.greetingState.copy(customGreeting = intent.value))
            .setup()
    }

    /** 打开世界书条目选择器弹窗，并以当前表单启用集合初始化草稿。 */
    @UiIntentObserver(GroupChatCreateUiIntent.ShowLorebookPicker::class)
    private fun onShowLorebookPicker() {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        if (uiState.loadState != GroupChatCreateLoadState.None || uiState.lorebookGroups.isEmpty()) return
        uiState.copy(
            dialogState = GroupChatCreateDialogState.LorebookPicker(
                query = "",
                showSelectedOnly = false,
                visibleGroups = uiState.lorebookGroups,
                enabledEntryIds = uiState.selectedLorebookEntryIds,
            ),
        ).setup()
    }

    /** 修改世界书选择器弹窗中的检索关键词，实时过滤分组与条目。 */
    @UiIntentObserver(GroupChatCreateUiIntent.ChangeLorebookQuery::class)
    private fun onChangeLorebookQuery(intent: GroupChatCreateUiIntent.ChangeLorebookQuery) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        val picker = uiState.dialogState as? GroupChatCreateDialogState.LorebookPicker ?: return
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

    /** 切换世界书选择器弹窗中的“仅看已启用”筛选状态。 */
    @UiIntentObserver(GroupChatCreateUiIntent.ToggleLorebookPickerSelectedOnly::class)
    private fun onToggleLorebookPickerSelectedOnly(
        intent: GroupChatCreateUiIntent.ToggleLorebookPickerSelectedOnly,
    ) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        val picker = uiState.dialogState as? GroupChatCreateDialogState.LorebookPicker ?: return
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

    /** 批量切换世界书选择器草稿中某本世界书下全部条目的选中状态。 */
    @UiIntentObserver(GroupChatCreateUiIntent.ToggleLorebook::class)
    private fun onToggleLorebook(intent: GroupChatCreateUiIntent.ToggleLorebook) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        val picker = uiState.dialogState as? GroupChatCreateDialogState.LorebookPicker ?: return
        // 收集该世界书下所有条目 ID
        val entryIds = uiState.lorebookGroups
            .firstOrNull { it.lorebookId == intent.lorebookId }
            ?.entries
            ?.mapTo(mutableSetOf()) { it.id }
            .orEmpty()
        if (entryIds.isEmpty()) return
        // 批量切换草稿选中集合并按筛选态刷新可见列表
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

    /** 切换世界书选择器草稿中单个条目的选中状态。 */
    @UiIntentObserver(GroupChatCreateUiIntent.ToggleLorebookEntry::class)
    private fun onToggleLorebookEntry(
        intent: GroupChatCreateUiIntent.ToggleLorebookEntry,
    ) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        val picker = uiState.dialogState as? GroupChatCreateDialogState.LorebookPicker ?: return
        // 校验目标条目仍存在于当前分组，避免无效 ID 污染草稿
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

    /** 确认并提交世界书选择器草稿到群聊创建表单。 */
    @UiIntentObserver(GroupChatCreateUiIntent.ConfirmLorebookPicker::class)
    private fun onConfirmLorebookPicker() {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        val picker = uiState.dialogState as? GroupChatCreateDialogState.LorebookPicker ?: return
        val selectedIds = picker.enabledEntryIds
        val groups = uiState.lorebookGroups.withEnabledIds(selectedIds)
        uiState.copy(
            selectedLorebookEntryIds = selectedIds,
            lorebookGroups = groups,
            selectedWorldBookSummaries = groups.summarizeSelection(),
            dialogState = GroupChatCreateDialogState.None,
        ).setup()
    }

    /** 关闭当前选择器弹窗并丢弃未提交的临时筛选与勾选草稿。 */
    @UiIntentObserver(GroupChatCreateUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        mCharacterSearchJob?.cancel()
        uiState.copy(dialogState = GroupChatCreateDialogState.None).setup()
        mAvatarLoader.clearVisibleCharacters()
    }

    /** 更新角色头像快照，候选列表与主表单的成员选择保持独立。 */
    private fun publishCharacterAvatars(avatars: Map<Long, ImageBitmap>) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        uiState.copy(characterAvatars = avatars).setup()
    }

    /** 从已提交的条目选择生成主表单展示的世界书摘要。 */
    private fun List<GroupChatLorebookGroupItem>.summarizeSelection() = selectedWorldBookSummaries(
        id = GroupChatLorebookGroupItem::lorebookId,
        name = GroupChatLorebookGroupItem::lorebookName,
        totalCount = GroupChatLorebookGroupItem::totalCount,
        selectedCount = GroupChatLorebookGroupItem::enabledCount,
    )

    /** 随创建页销毁取消头像解码，并释放缓存位图。 */
    override fun onCleared() {
        mAvatarLoader.clear()
        super.onCleared()
    }

    /**
     * 规划群聊开场白并事务创建会话、成员和开场消息。
     *
     * GreetingPlanner 先把随机/手动选择固定为有序快照，Repository 再一次性提交，
     * 防止创建过程中成员顺序或随机候选发生变化。
     */
    @UiIntentObserver(GroupChatCreateUiIntent.Create::class)
    private suspend fun onCreate() {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        if (uiState.loadState != GroupChatCreateLoadState.None) return
        // 校验参演成员数量至少为 2 人
        val characterIds = uiState.selectedCharacters.map { it.id }
        if (characterIds.size < 2) {
            AppViewEvent.PopupToastMessageByResId(R.string.group_chat_select_two_characters)
                .tryEmit()
            return
        }
        // 校验开场白配置完整度
        if (!uiState.greetingState.canCreate) {
            AppViewEvent.PopupToastMessageByResId(R.string.group_chat_greeting_incomplete).tryEmit()
            return
        }
        uiState.copy(loadState = GroupChatCreateLoadState.Creating).setup()
        val createTime = System.currentTimeMillis()
        val selectedPersona = mPersonas.chatPersona(uiState.persona.selectedId)
        val userName = selectedPersona.name
        // 提取候选成员与开场白列表
        val greetingCandidates =
            uiState.greetingState.characters.map { groupChatGreetingCharacterItem ->
                GroupChatGreetingCandidate(
                    characterId = groupChatGreetingCharacterItem.id,
                    characterName = groupChatGreetingCharacterItem.name,
                    greetings = groupChatGreetingCharacterItem.greetings,
                )
            }
        // 生成确定的初始开场消息序列
        val openingMessages = mGreetingPlanner.plan(
            candidates = greetingCandidates,
            selection = uiState.greetingState.toSelection(),
            userName = userName,
        )
        // 在 IO 线程原子创建群聊会话、成员关联与初始开场消息
        val sessionId = withContext(Dispatchers.IO) {
            mGroupChatRepository.createSession(
                title = uiState.title.trim().ifBlank { createTime.toDefaultChatTitle() },
                userPersonaId = uiState.persona.selectedId,
                userName = userName,
                characterIds = characterIds,
                lorebookEntryIds = uiState.selectedLorebookEntryIds.sorted(),
                activationStrategy = uiState.activationStrategy.toEntity(),
                allowSelfResponses = uiState.allowSelfResponses,
                includeCharacterExamples = uiState.includeCharacterExamples,
                openingMessages = openingMessages,
                createTime = createTime,
            )
        }
        // 启动群聊页面并等待返回
        AppViewEvent.StartActivity(
            activity = GroupChatActivity::class.java,
            extras = Bundle().apply {
                putString(GroupChatActivity.EXTRA_SESSION_ID, sessionId.toString())
            },
        ).emitAndAwait()
        GroupChatCreateUiState.finished(uiStateFlow.value).setup()
    }

}
