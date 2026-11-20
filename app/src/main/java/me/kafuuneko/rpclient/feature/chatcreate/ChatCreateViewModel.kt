package me.kafuuneko.rpclient.feature.chatcreate

import android.os.Bundle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.chat.ChatActivity
import me.kafuuneko.rpclient.feature.chatcreate.model.ChatCreateForm
import me.kafuuneko.rpclient.feature.chatcreate.model.FirstMessageSelection
import me.kafuuneko.rpclient.feature.chatcreate.model.defaultLorebookEntryIds
import me.kafuuneko.rpclient.feature.chatcreate.model.filterForQuery
import me.kafuuneko.rpclient.feature.chatcreate.model.normalizedTitle
import me.kafuuneko.rpclient.feature.chatcreate.model.toChatCreateCharacterItem
import me.kafuuneko.rpclient.feature.chatcreate.model.toChatCreateLorebookGroups
import me.kafuuneko.rpclient.feature.chatcreate.presentation.ChatCreateDialogState
import me.kafuuneko.rpclient.feature.chatcreate.presentation.ChatCreateLoadState
import me.kafuuneko.rpclient.feature.chatcreate.presentation.ChatCreateUiIntent
import me.kafuuneko.rpclient.feature.chatcreate.presentation.ChatCreateUiState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.llm.LLMProviderSelectionResolver
import me.kafuuneko.rpclient.libs.media.CharacterAvatarLoader
import me.kafuuneko.rpclient.libs.prompt.macro.PromptMacroResolver
import me.kafuuneko.rpclient.libs.macro.MacroExpansionException
import me.kafuuneko.rpclient.libs.prompt.macro.resolveCharacterUserMacros
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehaviorProvider
import me.kafuuneko.rpclient.libs.prompt.model.PromptBuildContext
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.libs.room.repository.UserPersonaRepository
import me.kafuuneko.rpclient.utils.matchesCharacterQuery
import me.kafuuneko.rpclient.utils.toggle
import me.kafuuneko.rpclient.utils.toggleAll
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 新建单角色会话页状态持有者。
 *
 * 核心职责：
 * - 异步加载所有候选角色与世界书条目分组；
 * - 驱动角色选择及其多开场白（First Messages）切换；
 * - 自动联动角色专属世界书条目的默认授权与切换同步；
 * - 支持按条目名称、内容、主次关键词和分类进行多字段世界书过滤；
 * - 结合用户画像与开场白 Prompt 宏（如 `{{user}}`, `{{char}}`）预解析初始消息；
 * - 保证单聊会话与首条消息的原子事务创建，并导航跳转至聊天页。
 */
class ChatCreateViewModel : CoreViewModelWithEvent<ChatCreateUiIntent, ChatCreateUiState>(
    ChatCreateUiState.None
), KoinComponent {
    private var mCharacterSearchJob: Job? = null
    private val mPersonas by inject<UserPersonaRepository>()
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mChatRepository by inject<ChatRepository>()
    private val mFileRepository by inject<FileRepository>()
    private val mProviderSelectionResolver by inject<LLMProviderSelectionResolver>()
    private val mMacroResolver by inject<PromptMacroResolver>()
    private val mExampleDialogueBehaviorProvider by inject<ExampleDialogueBehaviorProvider>()
    private var mCharactersById: Map<Long, Character> = emptyMap()
    private val mAvatarLoader by lazy {
        CharacterAvatarLoader(
            mFileRepository = mFileRepository,
            mScope = viewModelScope,
            mOnAvatarsUpdated = ::publishLoadedAvatars,
        )
    }

    /** 切换创建草稿的人设，并重新计算含用户名宏的候选角色。 */
    @UiIntentObserver(ChatCreateUiIntent.SelectPersona::class)
    private suspend fun onSelectPersona(intent: ChatCreateUiIntent.SelectPersona) {
        val state = getOrNull<ChatCreateUiState.Normal>() ?: return
        val selection = state.persona.copy(selectedId = intent.id)
        val name = selection.selectedName ?: return
        // 从原始角色内容重新展开用户名，保持候选项与开场白采用同一身份
        val characters = mCharactersById.values.map { character ->
            character.toChatCreateCharacterItem(name)
                .copy(avatarImage = mAvatarLoader.avatarOf(character.id))
        }
        val dialogState = when (val dialog = state.dialogState) {
            is ChatCreateDialogState.CharacterPicker -> dialog.copy(
                visibleCharacters = characters.filter { character ->
                    matchesCharacterQuery(
                        name = character.name,
                        description = character.description,
                        tags = character.tags,
                        query = dialog.query,
                    )
                },
            )

            else -> dialog
        }
        mCharacterSearchJob?.cancel()
        state.copy(
            persona = selection,
            characters = characters,
            selectedCharacterFirstMessages = state.selectedCharacter()?.openingPreviews(name)
                .orEmpty(),
            dialogState = dialogState,
        ).setup()
    }

    /**
     * 初始化页面，读取全量角色与世界书条目。
     *
     * @param intent 包含定向创建时的预选角色 ID。
     */
    @UiIntentObserver(ChatCreateUiIntent.Init::class)
    private suspend fun onInit(intent: ChatCreateUiIntent.Init) {
        if (!isStateOf<ChatCreateUiState.None>()) return
        ChatCreateUiState.Normal(loadState = ChatCreateLoadState.Loading).setup()
        // 在 IO 线程查询角色与带条目的世界书分组
        val data = withContext(Dispatchers.IO) {
            val characters = mCharacterRepository.getAllCharacters()
            val lorebooks = mLorebookRepository.getAllLorebooksWithEntries()
            val groups = lorebooks.toChatCreateLorebookGroups()
            characters to groups
        }
        mCharactersById = data.first.associateBy { it.id }
        // 注册全部角色头像 UUID，并优先固定加载当前已预选角色摘要卡头像
        mAvatarLoader.registerCharacters(data.first.associate { it.id to it.avatar })
        // 定向入口仅预选指定角色；已删除时保持未选择，避免误用其他角色开聊
        val selectedCharacter = if (intent.characterId == null) {
            data.first.firstOrNull()
        } else {
            mCharactersById[intent.characterId]
        }
        val selectedLorebookEntryIds = selectedCharacter
            ?.defaultLorebookEntryIds(data.second)
            .orEmpty()
        // 渲染初始就绪状态
        val selection = mPersonas.chatSelection()
        val userName = requireNotNull(selection.selectedName)
        val selectedCharacterFirstMessages = selectedCharacter?.openingPreviews(userName).orEmpty()
        ChatCreateUiState.Normal(
            persona = selection,
            loadState = ChatCreateLoadState.None,
            showCharacterExamplesSwitch =
                mExampleDialogueBehaviorProvider.current() != ExampleDialogueBehavior.Disabled,
            form = (getOrNull<ChatCreateUiState.Normal>()?.form ?: ChatCreateForm())
                .selectCharacter(
                    characterId = selectedCharacter?.id,
                    hasFirstMessage = selectedCharacterFirstMessages.isNotEmpty(),
                    linkedLorebookEntryIds = selectedLorebookEntryIds,
                ),
            characters = data.first.map { character ->
                character.toChatCreateCharacterItem(userName)
                    .copy(avatarImage = mAvatarLoader.avatarOf(character.id))
            },
            selectedCharacterFirstMessages = selectedCharacterFirstMessages,
            lorebookGroups = data.second,
        ).setup()
        mAvatarLoader.updatePinnedCharacters(
            selectedCharacter?.id?.let { id -> setOf(id) } ?: emptySet(),
        )
    }

    /** 打开角色检索选择器弹窗。 */
    @UiIntentObserver(ChatCreateUiIntent.ShowCharacterPicker::class)
    private fun onShowCharacterPicker() {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        if (uiState.loadState != ChatCreateLoadState.None || uiState.characters.isEmpty()) return
        mCharacterSearchJob?.cancel()
        val hydrated = uiState.characters.map { character ->
            character.copy(avatarImage = mAvatarLoader.avatarOf(character.id))
        }
        uiState.copy(
            characters = hydrated,
            dialogState = ChatCreateDialogState.CharacterPicker(
                query = "",
                visibleCharacters = hydrated,
            ),
        ).setup()
    }

    /** 根据角色选择器弹窗当前可见窗口按需加载头像缩略图。 */
    @UiIntentObserver(ChatCreateUiIntent.VisiblePickerCharactersChanged::class)
    private fun onVisiblePickerCharactersChanged(
        intent: ChatCreateUiIntent.VisiblePickerCharactersChanged,
    ) {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        if (uiState.dialogState !is ChatCreateDialogState.CharacterPicker) return
        mAvatarLoader.updateVisibleCharacters(
            characterIds = intent.characterIds,
            targetSizePx = intent.targetSizePx,
        )
    }

    /** 在角色选择器弹窗内后台筛选角色，取消旧任务并避免覆盖最新弹窗状态。 */
    @UiIntentObserver(ChatCreateUiIntent.ChangeCharacterQuery::class)
    private fun onChangeCharacterQuery(intent: ChatCreateUiIntent.ChangeCharacterQuery) {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        val picker = uiState.dialogState as? ChatCreateDialogState.CharacterPicker ?: return
        mCharacterSearchJob?.cancel()
        uiState.copy(dialogState = picker.copy(query = intent.value)).setup()
        // 后台计算仅返回命中的角色 ID，回写时核对弹窗仍处于同一搜索词
        mCharacterSearchJob = viewModelScope.launch {
            val visibleIds = withContext(Dispatchers.Default) {
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
            val current = getOrNull<ChatCreateUiState.Normal>() ?: return@launch
            val currentPicker = current.dialogState as? ChatCreateDialogState.CharacterPicker
                ?: return@launch
            if (currentPicker.query == intent.value) {
                current.copy(
                    dialogState = currentPicker.copy(
                        visibleCharacters = current.characters
                            .filter { character -> character.id in visibleIds }
                            .map { character ->
                                character.copy(avatarImage = mAvatarLoader.avatarOf(character.id))
                            },
                    ),
                ).setup()
            }
        }
    }

    /** 处理返回操作，迁移至 Finished 状态。 */
    @UiIntentObserver(ChatCreateUiIntent.Back::class)
    private fun onBack() {
        if (isStateOf<ChatCreateUiState.Finished>()) return
        ChatCreateUiState.finished(uiStateFlow.value).setup()
    }

    /**
     * 选中目标角色，刷新其可用开场白与默认关联世界书，并关闭角色选择器弹窗。
     *
     * @param intent 包含目标角色 ID 的意图
     */
    @UiIntentObserver(ChatCreateUiIntent.SelectCharacter::class)
    private fun onSelectCharacter(intent: ChatCreateUiIntent.SelectCharacter) {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        val character = mCharactersById[intent.characterId] ?: return
        mCharacterSearchJob?.cancel()
        mAvatarLoader.clearVisibleCharacters()
        // 重复点击当前已选角色时仅收起选择器，不重置已调整的开场白与世界书
        if (uiState.form.selectedCharacterId == character.id) {
            uiState.copy(dialogState = ChatCreateDialogState.None).setup()
            return
        }
        // 记录上一个角色绑定的条目集合用于差量剔除
        val previousLinkedLorebookEntryIds = uiState.selectedCharacter()
            ?.defaultLorebookEntryIds(uiState.lorebookGroups)
            .orEmpty()
        val selectedCharacterFirstMessages = character.openingPreviews(uiState.persona.selectedName)
        // 更新表单角色选择、世界书勾选集合并关闭弹窗
        uiState.copy(
            form = uiState.form.selectCharacter(
                characterId = character.id,
                hasFirstMessage = selectedCharacterFirstMessages.isNotEmpty(),
                previousLinkedLorebookEntryIds = previousLinkedLorebookEntryIds,
                linkedLorebookEntryIds = character.defaultLorebookEntryIds(uiState.lorebookGroups),
            ),
            selectedCharacterFirstMessages = selectedCharacterFirstMessages,
            dialogState = ChatCreateDialogState.None,
        ).setup()
        mAvatarLoader.updatePinnedCharacters(setOf(character.id))
    }

    /** 选择角色预置的多条开场白中的某一条。 */
    @UiIntentObserver(ChatCreateUiIntent.SelectFirstMessage::class)
    private fun onSelectFirstMessage(intent: ChatCreateUiIntent.SelectFirstMessage) {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        if (intent.index !in uiState.selectedCharacterFirstMessages.indices) return
        updateForm { copy(selectedFirstMessageIndex = intent.index) }
    }

    /** 修改会话自定义标题。 */
    @UiIntentObserver(ChatCreateUiIntent.ChangeTitle::class)
    private fun onChangeTitle(intent: ChatCreateUiIntent.ChangeTitle) {
        if (!isStateOf<ChatCreateUiState.Normal>()) return
        updateForm { copy(title = intent.value) }
    }

    /** 修改会话级 User Note 附加提示词。 */
    @UiIntentObserver(ChatCreateUiIntent.ChangeUserNote::class)
    private fun onChangeUserNote(intent: ChatCreateUiIntent.ChangeUserNote) {
        if (!isStateOf<ChatCreateUiState.Normal>()) return
        updateForm { copy(userNote = intent.value) }
    }

    /**
     * 修改待创建会话的角色卡示例对话注入开关。
     *
     * @param intent 包含开关目标值的用户意图。
     */
    @UiIntentObserver(ChatCreateUiIntent.ToggleIncludeCharacterExamples::class)
    private fun onToggleIncludeCharacterExamples(
        intent: ChatCreateUiIntent.ToggleIncludeCharacterExamples,
    ) {
        if (!isStateOf<ChatCreateUiState.Normal>()) return
        updateForm { copy(includeCharacterExamples = intent.enabled) }
    }

    /** 打开世界书条目选择器弹窗，并以当前表单启用集合初始化草稿。 */
    @UiIntentObserver(ChatCreateUiIntent.ShowLorebookPicker::class)
    private fun onShowLorebookPicker() {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        if (uiState.loadState != ChatCreateLoadState.None || uiState.lorebookGroups.isEmpty()) return
        uiState.copy(
            dialogState = ChatCreateDialogState.LorebookPicker(
                query = "",
                showSelectedOnly = false,
                visibleGroups = uiState.lorebookGroups,
                enabledEntryIds = uiState.form.selectedLorebookEntryIds,
            ),
        ).setup()
    }

    /** 修改世界书选择器弹窗中的检索关键词，实时过滤分组与条目。 */
    @UiIntentObserver(ChatCreateUiIntent.ChangeLorebookQuery::class)
    private fun onChangeLorebookQuery(intent: ChatCreateUiIntent.ChangeLorebookQuery) {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        val picker = uiState.dialogState as? ChatCreateDialogState.LorebookPicker ?: return
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
    @UiIntentObserver(ChatCreateUiIntent.ToggleLorebookPickerSelectedOnly::class)
    private fun onToggleLorebookPickerSelectedOnly(
        intent: ChatCreateUiIntent.ToggleLorebookPickerSelectedOnly,
    ) {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        val picker = uiState.dialogState as? ChatCreateDialogState.LorebookPicker ?: return
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

    /** 切换世界书选择器草稿中单个条目的启用状态。 */
    @UiIntentObserver(ChatCreateUiIntent.ToggleLorebookEntry::class)
    private fun onToggleLorebookEntry(intent: ChatCreateUiIntent.ToggleLorebookEntry) {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        val picker = uiState.dialogState as? ChatCreateDialogState.LorebookPicker ?: return
        // 仅允许切换当前已加载分组中存在的有效条目
        val entryExists = uiState.lorebookGroups.any { group ->
            group.entries.any { entry -> entry.id == intent.entryId }
        }
        if (!entryExists) return
        val nextEnabledIds = picker.enabledEntryIds.toggle(intent.entryId)
        // 处于仅看已启用筛选时同步刷新可见分组，确保展示与计数一致
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

    /** 批量切换世界书选择器草稿中某本世界书下全部条目的启用状态。 */
    @UiIntentObserver(ChatCreateUiIntent.ToggleLorebook::class)
    private fun onToggleLorebook(intent: ChatCreateUiIntent.ToggleLorebook) {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        val picker = uiState.dialogState as? ChatCreateDialogState.LorebookPicker ?: return
        // 整书开关始终作用于完整分组，不受当前搜索过滤裁剪影响
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

    /** 确认并提交世界书选择器弹窗中的条目选择草稿。 */
    @UiIntentObserver(ChatCreateUiIntent.ConfirmLorebookPicker::class)
    private fun onConfirmLorebookPicker() {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        val picker = uiState.dialogState as? ChatCreateDialogState.LorebookPicker ?: return
        uiState.copy(
            form = uiState.form.copy(selectedLorebookEntryIds = picker.enabledEntryIds),
            dialogState = ChatCreateDialogState.None,
        ).setup()
    }

    /** 关闭当前打开的选择器弹窗，丢弃未提交的临时草稿与搜索词。 */
    @UiIntentObserver(ChatCreateUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        mCharacterSearchJob?.cancel()
        mAvatarLoader.clearVisibleCharacters()
        uiState.copy(dialogState = ChatCreateDialogState.None).setup()
    }

    /** 将最新解码完成的头像位图同步回主列表与角色选择器弹窗状态。 */
    private fun publishLoadedAvatars() {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        val updatedCharacters = uiState.characters.map { character ->
            val image = mAvatarLoader.avatarOf(character.id)
            if (character.avatarImage === image) character else character.copy(avatarImage = image)
        }
        val updatedDialog = when (val dialog = uiState.dialogState) {
            is ChatCreateDialogState.CharacterPicker -> dialog.copy(
                visibleCharacters = dialog.visibleCharacters.map { character ->
                    val image = mAvatarLoader.avatarOf(character.id)
                    if (character.avatarImage === image) {
                        character
                    } else {
                        character.copy(avatarImage = image)
                    }
                },
            )

            else -> dialog
        }
        // 仅在位图引用实际变化时触发状态发布，避免多余重组
        if (updatedCharacters != uiState.characters || updatedDialog != uiState.dialogState) {
            uiState.copy(
                characters = updatedCharacters,
                dialogState = updatedDialog,
            ).setup()
        }
    }

    override fun onCleared() {
        mAvatarLoader.clear()
        super.onCleared()
    }

    /**
     * 将创建表单转换为会话及可选开场消息，然后在事务成功后进入聊天页。
     *
     * 开场白宏使用与新会话完全相同的用户和角色快照解析；会话与首条消息由 Repository
     * 原子创建，页面不会观察到只有会话而缺少开场白的中间状态。
     */
    @UiIntentObserver(ChatCreateUiIntent.CreateChat::class)
    private suspend fun onCreateChat() {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        if (uiState.loadState != ChatCreateLoadState.None) return
        // 校验选中的角色
        val character = uiState.selectedCharacter() ?: run {
            AppViewEvent.PopupToastMessageByResId(R.string.no_character_selected).tryEmit()
            return
        }
        // 校验开场白选择
        val firstMessageSelection = uiState.resolveFirstMessageSelection() ?: return
        uiState.copy(loadState = ChatCreateLoadState.Creating).setup()
        val selectedPersona = mPersonas.chatPersona(uiState.persona.selectedId)
        val userName = selectedPersona.name
        val userDescription = selectedPersona.description
        val createTime = System.currentTimeMillis()
        val sessionTitle = uiState.form.normalizedTitle(createTime)

        // 预解析开场白中的 Prompt 宏变量
        val firstMessageContent = firstMessageSelection.value?.let { rawFirstMessage ->
            val session = ChatSession(
                id = 0L,
                characterId = character.id,
                createTime = createTime,
                latestTime = createTime,
                lorebookEntrySet = "",
                title = sessionTitle,
                userNote = uiState.form.userNote.trim(),
                userName = userName,
                userDescription = userDescription,
                creatorNotes = null,
                includeCharacterExamples = uiState.form.includeCharacterExamples,
            )
            val context = PromptBuildContext(
                userName = userName,
                userDescription = userDescription,
                character = character,
                session = session,
                summary = "",
                messages = emptyList(),
                currentUserMessage = null,
                candidateLorebookEntries = emptyList(),
                provider = mProviderSelectionResolver.getCharacterProviderOrNull(character),
                maxContextTokens = 0,
                maxResponseTokens = 0,
            )
            try {
                mMacroResolver.resolve(rawFirstMessage, context)
            } catch (_: MacroExpansionException) {
                // 模板错误发生在入库前，保留表单供用户修正后重试。
                uiState.copy(loadState = ChatCreateLoadState.None).setup()
                AppViewEvent.PopupToastMessageByResId(R.string.generation_failed).tryEmit()
                return
            }
        }

        // 在 IO 线程原子入库单聊会话与首条角色消息
        val sessionId = withContext(Dispatchers.IO) {
            mChatRepository.createSessionWithFirstMessage(
                characterId = character.id,
                title = sessionTitle,
                userNote = uiState.form.userNote.trim(),
                userPersonaId = uiState.persona.selectedId,
                userName = userName,
                lorebookEntryIds = uiState.form.selectedLorebookEntryIds.sorted(),
                firstMessageContent = firstMessageContent,
                createTime = createTime,
                includeCharacterExamples = uiState.form.includeCharacterExamples,
            )
        }
        // 导航至聊天页并等待返回
        AppViewEvent.StartActivity(
            activity = ChatActivity::class.java,
            extras = Bundle().apply {
                putString(ChatActivity.EXTRA_SESSION_ID, sessionId.toString())
            },
        ).emitAndAwait()
        ChatCreateUiState.finished(uiStateFlow.value).setup()
    }

    /** 辅助方法：以不可变方式更新表单数据。 */
    private fun updateForm(block: ChatCreateForm.() -> ChatCreateForm) {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        uiState.copy(form = uiState.form.block()).setup()
    }

    /** 展示时只替换角色和用户名，创建时仍用原文执行完整的宏解析。 */
    private fun Character.openingPreviews(userName: String?) = getChatFirstMessageList().map {
        resolveCharacterUserMacros(it, name, userName)
    }

    /** 读取当前选中的角色实体。 */
    private fun ChatCreateUiState.Normal.selectedCharacter(): Character? {
        val characterId = form.selectedCharacterId ?: return null
        return mCharactersById[characterId]
    }

    /** 解析当前开场白选择；若角色提供开场白但用户未选则给出 Toast 提示。 */
    private fun ChatCreateUiState.Normal.resolveFirstMessageSelection(): FirstMessageSelection? {
        if (selectedCharacterFirstMessages.isEmpty()) return FirstMessageSelection(value = null)
        val selectedIndex = form.selectedFirstMessageIndex
        if (selectedIndex == null || selectedIndex !in selectedCharacterFirstMessages.indices) {
            AppViewEvent.PopupToastMessageByResId(R.string.first_message_required).tryEmit()
            return null
        }
        return FirstMessageSelection(
            selectedCharacter()?.getChatFirstMessageList()?.get(selectedIndex)
        )
    }

}
