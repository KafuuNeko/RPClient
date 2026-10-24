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
import me.kafuuneko.rpclient.feature.chatcreate.presentation.ChatCreateLoadState
import me.kafuuneko.rpclient.feature.chatcreate.presentation.ChatCreateUiIntent
import me.kafuuneko.rpclient.feature.chatcreate.presentation.ChatCreateUiState
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.llm.LLMProviderSelectionResolver
import me.kafuuneko.rpclient.libs.prompt.PromptMacroResolver
import me.kafuuneko.rpclient.libs.prompt.model.PromptBuildContext
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
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
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mChatRepository by inject<ChatRepository>()
    private val mProviderSelectionResolver by inject<LLMProviderSelectionResolver>()
    private val mMacroResolver by inject<PromptMacroResolver>()
    private var mCharactersById: Map<Long, Character> = emptyMap()

    /** 初始化页面，并发拉取数据库中的全量角色与世界书条目。 */
    @UiIntentObserver(ChatCreateUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<ChatCreateUiState.None>()) return
        ChatCreateUiState.Normal(loadState = ChatCreateLoadState.Loading).setup()
        // 在 IO 线程并发查询角色与带条目的世界书分组
        val data = withContext(Dispatchers.IO) {
            val characters = mCharacterRepository.getAllCharacters()
            val lorebooks = mLorebookRepository.getAllLorebooksWithEntries()
            val groups = lorebooks.toChatCreateLorebookGroups()
            characters to groups
        }
        mCharactersById = data.first.associateBy { it.id }
        // 默认选中第一个可用角色
        val selectedCharacter = data.first.firstOrNull()
        val selectedCharacterFirstMessages = selectedCharacter?.getChatFirstMessageList().orEmpty()
        val selectedLorebookEntryIds = selectedCharacter
            ?.defaultLorebookEntryIds(data.second)
            .orEmpty()
        // 渲染初始就绪状态
        val userName = AppModel.resolvedUserName
        ChatCreateUiState.Normal(
            loadState = ChatCreateLoadState.None,
            form = (getOrNull<ChatCreateUiState.Normal>()?.form ?: ChatCreateForm())
                .selectCharacter(
                    characterId = selectedCharacter?.id,
                    hasFirstMessage = selectedCharacterFirstMessages.isNotEmpty(),
                    linkedLorebookEntryIds = selectedLorebookEntryIds
                ),
            characters = data.first.map { character ->
                character.toChatCreateCharacterItem(userName)
            },
            selectedCharacterFirstMessages = selectedCharacterFirstMessages,
            lorebookGroups = data.second,
            visibleLorebookGroups = data.second
        ).setup()
    }

    /** 后台筛选角色；取消旧请求并保留同时发生的表单与选择修改。 */
    @UiIntentObserver(ChatCreateUiIntent.ChangeCharacterQuery::class)
    private fun onChangeCharacterQuery(intent: ChatCreateUiIntent.ChangeCharacterQuery) {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        mCharacterSearchJob?.cancel()
        uiState.copy(characterQuery = intent.value).setup()
        // 后台计算仅返回 ID，回写时从最新列表恢复选择状态。
        mCharacterSearchJob = viewModelScope.launch {
            val visibleIds = withContext(Dispatchers.Default) {
                val context = currentCoroutineContext()
                uiState.characters.filter {
                    context.ensureActive()
                    matchesCharacterQuery(it.name, it.description, it.tags, intent.value)
                }.mapTo(HashSet()) { it.id }
            }
            val current = getOrNull<ChatCreateUiState.Normal>() ?: return@launch
            if (current.characterQuery == intent.value) {
                current.copy(visibleCharacters = current.characters.filter { it.id in visibleIds }).setup()
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
     * 选中不同角色，刷新其可用开场白列表并同步更新世界书联动勾选项。
     *
     * @param intent 包含目标角色 ID 的意图
     */
    @UiIntentObserver(ChatCreateUiIntent.SelectCharacter::class)
    private fun onSelectCharacter(intent: ChatCreateUiIntent.SelectCharacter) {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        val character = mCharactersById[intent.characterId] ?: return
        if (uiState.form.selectedCharacterId == character.id) return
        // 记录上一个角色绑定的条目集合用于差量剔除
        val previousLinkedLorebookEntryIds = uiState.selectedCharacter()
            ?.defaultLorebookEntryIds(uiState.lorebookGroups)
            .orEmpty()
        val selectedCharacterFirstMessages = character.getChatFirstMessageList()
        // 更新表单角色选择与世界书勾选集合
        uiState.copy(
            form = uiState.form.selectCharacter(
                characterId = character.id,
                hasFirstMessage = selectedCharacterFirstMessages.isNotEmpty(),
                previousLinkedLorebookEntryIds = previousLinkedLorebookEntryIds,
                linkedLorebookEntryIds = character.defaultLorebookEntryIds(uiState.lorebookGroups)
            ),
            selectedCharacterFirstMessages = selectedCharacterFirstMessages
        ).setup()
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

    /** 修改世界书检索关键词，过滤展示的分组与条目。 */
    @UiIntentObserver(ChatCreateUiIntent.ChangeLorebookQuery::class)
    private fun onChangeLorebookQuery(intent: ChatCreateUiIntent.ChangeLorebookQuery) {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        uiState.copy(
            lorebookQuery = intent.value,
            visibleLorebookGroups = uiState.lorebookGroups.filterForQuery(intent.value)
        ).setup()
    }

    /** 切换单个世界书条目的授权选中状态。 */
    @UiIntentObserver(ChatCreateUiIntent.ToggleLorebookEntry::class)
    private fun onToggleLorebookEntry(intent: ChatCreateUiIntent.ToggleLorebookEntry) {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        if (uiState.lorebookGroups.none { group -> group.entries.any { it.id == intent.entryId } }) return
        val selectedIds = uiState.form.selectedLorebookEntryIds
        updateForm {
            copy(
                selectedLorebookEntryIds = selectedIds.toggle(intent.entryId)
            )
        }
    }

    /** 批量切换某一本世界书下所有条目的授权选中状态。 */
    @UiIntentObserver(ChatCreateUiIntent.ToggleLorebook::class)
    private fun onToggleLorebook(intent: ChatCreateUiIntent.ToggleLorebook) {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        val group = uiState.lorebookGroups.firstOrNull { it.lorebookId == intent.lorebookId } ?: return
        val entryIds = group.entries.map { it.id }.toSet()
        if (entryIds.isEmpty()) return
        val selectedIds = uiState.form.selectedLorebookEntryIds
        updateForm {
            copy(
                selectedLorebookEntryIds = selectedIds.toggleAll(entryIds)
            )
        }
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
        val userName = AppModel.resolvedUserName
        val userDescription = AppModel.userDescription.trim()
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
                creatorNotes = null
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
                maxResponseTokens = 0
            )
            mMacroResolver.resolve(rawFirstMessage, context)
        }

        // 在 IO 线程原子入库单聊会话与首条角色消息
        val sessionId = withContext(Dispatchers.IO) {
            mChatRepository.createSessionWithFirstMessage(
                characterId = character.id,
                title = sessionTitle,
                userNote = uiState.form.userNote.trim(),
                userName = userName,
                userDescription = userDescription,
                lorebookEntryIds = uiState.form.selectedLorebookEntryIds.sorted(),
                firstMessageContent = firstMessageContent,
                createTime = createTime
            )
        }
        // 导航至聊天页并等待返回
        AppViewEvent.StartActivity(
            activity = ChatActivity::class.java,
            extras = Bundle().apply {
                putString(ChatActivity.EXTRA_SESSION_ID, sessionId.toString())
            }
        ).emitAndAwait()
        ChatCreateUiState.finished(uiStateFlow.value).setup()
    }

    /** 辅助方法：以不可变方式更新表单数据。 */
    private fun updateForm(block: ChatCreateForm.() -> ChatCreateForm) {
        val uiState = getOrNull<ChatCreateUiState.Normal>() ?: return
        uiState.copy(form = uiState.form.block()).setup()
    }

    /** 读取当前选中的角色实体。 */
    private fun ChatCreateUiState.Normal.selectedCharacter(): Character? {
        val characterId = form.selectedCharacterId ?: return null
        return mCharactersById[characterId]
    }

    /** 解析当前开场白选择；若角色提供开场白但用户未选则给出 Toast 提示。 */
    private fun ChatCreateUiState.Normal.resolveFirstMessageSelection(): FirstMessageSelection? {
        if (selectedCharacterFirstMessages.isEmpty()) return FirstMessageSelection(null)
        val selectedIndex = form.selectedFirstMessageIndex
        if (selectedIndex == null || selectedIndex !in selectedCharacterFirstMessages.indices) {
            AppViewEvent.PopupToastMessageByResId(R.string.first_message_required).tryEmit()
            return null
        }
        return FirstMessageSelection(selectedCharacterFirstMessages[selectedIndex])
    }

}
