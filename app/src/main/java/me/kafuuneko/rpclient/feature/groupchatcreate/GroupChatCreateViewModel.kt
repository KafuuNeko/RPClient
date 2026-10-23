package me.kafuuneko.rpclient.feature.groupchatcreate

import android.os.Bundle
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
import me.kafuuneko.rpclient.feature.groupchatcreate.presentation.GroupChatCreateLoadState
import me.kafuuneko.rpclient.feature.groupchatcreate.presentation.GroupChatCreateUiIntent
import me.kafuuneko.rpclient.feature.groupchatcreate.presentation.GroupChatCreateUiState
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.groupchat.GroupChatGreetingCandidate
import me.kafuuneko.rpclient.libs.groupchat.GroupChatGreetingPlanner
import me.kafuuneko.rpclient.libs.groupchat.model.toEntity
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
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
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mGroupChatRepository by inject<GroupChatRepository>()
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mGreetingPlanner by inject<GroupChatGreetingPlanner>()

    /** 初始化页面，拉取候选角色列表与世界书条目列表。 */
    @UiIntentObserver(GroupChatCreateUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<GroupChatCreateUiState.None>()) return
        GroupChatCreateUiState.Normal(loadState = GroupChatCreateLoadState.Loading).setup()
        val userName = AppModel.resolvedUserName
        // 在 IO 线程并发查询候选角色与带条目的世界书分组
        val data = withContext(Dispatchers.IO) {
            val characters = mCharacterRepository.getAllCharacters().map { character ->
                character.toGroupChatCreateCharacterItem(userName)
            }
            val lorebookGroups = mLorebookRepository.getAllLorebooksWithEntries()
                .toGroupChatCreateLorebookGroups()
            characters to lorebookGroups
        }
        // 渲染就绪状态
        GroupChatCreateUiState.Normal(
            characters = data.first,
            visibleCharacters = data.first,
            lorebookGroups = data.second,
            visibleLorebookGroups = data.second,
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

    /** 后台筛选角色；取消旧请求并保留同时发生的表单与选择修改。 */
    @UiIntentObserver(GroupChatCreateUiIntent.ChangeSearchQuery::class)
    private fun onChangeSearchQuery(intent: GroupChatCreateUiIntent.ChangeSearchQuery) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        mCharacterSearchJob?.cancel()
        uiState.copy(searchQuery = intent.value).setup()
        // 后台计算仅返回 ID，回写时从最新列表恢复选择状态。
        mCharacterSearchJob = viewModelScope.launch {
            val visibleIds = withContext(Dispatchers.Default) {
                val context = currentCoroutineContext()
                uiState.characters.filter { groupChatCreateCharacterItem ->
                    context.ensureActive()
                    matchesCharacterQuery(
                        name = groupChatCreateCharacterItem.name,
                        description = groupChatCreateCharacterItem.description,
                        tags = groupChatCreateCharacterItem.tags,
                        query = intent.value,
                    )
                }.mapTo(HashSet()) { groupChatCreateCharacterItem -> groupChatCreateCharacterItem.id }
            }
            val current = getOrNull<GroupChatCreateUiState.Normal>() ?: return@launch
            if (current.searchQuery == intent.value) {
                current.copy(visibleCharacters = current.characters.filter { groupChatCreateCharacterItem -> groupChatCreateCharacterItem.id in visibleIds }).setup()
            }
        }
    }

    /** 修改世界书检索关键词，实时过滤展示的分组与条目。 */
    @UiIntentObserver(GroupChatCreateUiIntent.ChangeLorebookQuery::class)
    private fun onChangeLorebookQuery(intent: GroupChatCreateUiIntent.ChangeLorebookQuery) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        uiState.copy(
            lorebookQuery = intent.value,
            visibleLorebookGroups = uiState.lorebookGroups.filterForQuery(intent.value),
        ).setup()
    }

    /**
     * 切换群聊成员选中状态，自动同步专属世界书条目授权并收敛开场白候选。
     *
     * @param intent 包含目标角色 ID 的意图
     */
    @UiIntentObserver(GroupChatCreateUiIntent.ToggleCharacter::class)
    private fun onToggleCharacter(intent: GroupChatCreateUiIntent.ToggleCharacter) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        // 切换目标角色选中态
        val characters = uiState.characters.map { groupChatCreateCharacterItem ->
            if (groupChatCreateCharacterItem.id == intent.characterId) groupChatCreateCharacterItem.copy(selected = !groupChatCreateCharacterItem.selected) else groupChatCreateCharacterItem
        }
        val selectedCharacter = characters.firstOrNull { it.id == intent.characterId }
        // 提取角色专属绑定的世界书条目
        val defaultEntryIds = selectedCharacter
            ?.takeIf { it.selected }
            ?.characterLorebookId
            ?.takeIf { it > 0L }
            ?.let { lorebookId ->
                uiState.lorebookGroups
                    .firstOrNull { groupChatLorebookGroupItem -> groupChatLorebookGroupItem.lorebookId == lorebookId }
                    ?.entries
                    ?.mapTo(mutableSetOf()) { groupChatLorebookEntryItem -> groupChatLorebookEntryItem.id }
            }
            .orEmpty()
        val selectedEntryIds = uiState.selectedLorebookEntryIds + defaultEntryIds
        val lorebookGroups = uiState.lorebookGroups.withEnabledIds(selectedEntryIds)
        // 更新 UI 状态并收敛开场白候选成员
        uiState.copy(
            characters = characters,
            visibleCharacters = characters.visibleFor(uiState.searchQuery),
            selectedLorebookEntryIds = selectedEntryIds,
            lorebookGroups = lorebookGroups,
            visibleLorebookGroups = lorebookGroups.filterForQuery(uiState.lorebookQuery),
            greetingState = uiState.greetingState.reconcile(characters),
        ).setup()
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
        uiState.copy(greetingState = uiState.greetingState.copy(selectedGreetingIndex = intent.greetingIndex)).setup()
    }

    /** 修改自定义开场白文本内容。 */
    @UiIntentObserver(GroupChatCreateUiIntent.ChangeCustomGreeting::class)
    private fun onChangeCustomGreeting(
        intent: GroupChatCreateUiIntent.ChangeCustomGreeting,
    ) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        uiState.copy(greetingState = uiState.greetingState.copy(customGreeting = intent.value)).setup()
    }

    /** 批量切换某一本世界书下所有条目的选中状态。 */
    @UiIntentObserver(GroupChatCreateUiIntent.ToggleLorebook::class)
    private fun onToggleLorebook(intent: GroupChatCreateUiIntent.ToggleLorebook) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        // 收集该世界书下所有条目 ID
        val entryIds = uiState.lorebookGroups
            .firstOrNull { it.lorebookId == intent.lorebookId }
            ?.entries
            ?.mapTo(mutableSetOf()) { it.id }
            .orEmpty()
        if (entryIds.isEmpty()) return
        // 批量切换选中集合
        val selectedIds = uiState.selectedLorebookEntryIds.toggleAll(entryIds)
        val groups = uiState.lorebookGroups.withEnabledIds(selectedIds)
        uiState.copy(
            selectedLorebookEntryIds = selectedIds,
            lorebookGroups = groups,
            visibleLorebookGroups = groups.filterForQuery(uiState.lorebookQuery),
        ).setup()
    }

    /** 切换单个世界书条目的选中状态。 */
    @UiIntentObserver(GroupChatCreateUiIntent.ToggleLorebookEntry::class)
    private fun onToggleLorebookEntry(
        intent: GroupChatCreateUiIntent.ToggleLorebookEntry,
    ) {
        val uiState = getOrNull<GroupChatCreateUiState.Normal>() ?: return
        if (uiState.lorebookGroups.none { group ->
                group.entries.any { groupChatLorebookEntryItem -> groupChatLorebookEntryItem.id == intent.entryId }
            }
        ) return
        val selectedIds = uiState.selectedLorebookEntryIds.toggle(intent.entryId)
        val groups = uiState.lorebookGroups.withEnabledIds(selectedIds)
        uiState.copy(
            selectedLorebookEntryIds = selectedIds,
            lorebookGroups = groups,
            visibleLorebookGroups = groups.filterForQuery(uiState.lorebookQuery),
        ).setup()
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
        val characterIds = uiState.characters.filter { it.selected }.map { it.id }
        if (characterIds.size < 2) {
            AppViewEvent.PopupToastMessageByResId(R.string.group_chat_select_two_characters).tryEmit()
            return
        }
        // 校验开场白配置完整度
        if (!uiState.greetingState.canCreate) {
            AppViewEvent.PopupToastMessageByResId(R.string.group_chat_greeting_incomplete).tryEmit()
            return
        }
        uiState.copy(loadState = GroupChatCreateLoadState.Creating).setup()
        val createTime = System.currentTimeMillis()
        val userName = AppModel.resolvedUserName
        // 提取候选成员与开场白列表
        val greetingCandidates = uiState.greetingState.characters.map { groupChatGreetingCharacterItem ->
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
                userName = userName,
                userDescription = AppModel.userDescription.trim(),
                characterIds = characterIds,
                lorebookEntryIds = uiState.selectedLorebookEntryIds.sorted(),
                activationStrategy = uiState.activationStrategy.toEntity(),
                allowSelfResponses = uiState.allowSelfResponses,
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
