package me.kafuuneko.rpclient.feature.groupchat

import android.content.Context
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.ModelSettingsGuideContent
import me.kafuuneko.rpclient.feature.noProviderModelSettingsGuide
import me.kafuuneko.rpclient.feature.toGenerationFailurePresentation
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatGenerationState
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatAvailableCharacterItem
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatMemberItem
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatMessageItem
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatDialogState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatConversationState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatLoadState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatPage
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatSettingsState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiIntent
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatViewEvent
import me.kafuuneko.rpclient.feature.groupchat.presentation.withSettingsDraft
import me.kafuuneko.rpclient.feature.llmproviderlist.LLMProviderListActivity
import me.kafuuneko.rpclient.feature.worldbooklist.WorldBookListActivity
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.groupchat.GroupChatPromptBuilder
import me.kafuuneko.rpclient.libs.groupchat.GroupChatPromptContext
import me.kafuuneko.rpclient.libs.groupchat.GroupChatGenerationMode
import me.kafuuneko.rpclient.libs.groupchat.GroupChatOutputSanitizer
import me.kafuuneko.rpclient.libs.groupchat.GroupChatSpeakerSelector
import me.kafuuneko.rpclient.libs.groupchat.GroupChatSummaryPromptBuilder
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatActivationStrategy
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatLorebookEntryItem
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatLorebookGroupItem
import me.kafuuneko.rpclient.libs.groupchat.model.toEntity
import me.kafuuneko.rpclient.libs.groupchat.model.toGroupChatActivationStrategy
import me.kafuuneko.rpclient.libs.groupchat.model.toGroupChatCharacterCardMode
import me.kafuuneko.rpclient.libs.groupchat.model.toGroupChatMessageSource
import me.kafuuneko.rpclient.libs.llm.LLMProviderSelectionResolver
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspection
import me.kafuuneko.rpclient.libs.prompt.model.PromptOmissionReason
import me.kafuuneko.rpclient.libs.prompt.summarySafeContent
import me.kafuuneko.rpclient.libs.prompt.resolveCharacterUserMacros
import me.kafuuneko.rpclient.libs.regex.RegexMessageProcessor
import me.kafuuneko.rpclient.libs.regex.RegexMessageSource
import me.kafuuneko.rpclient.libs.regex.RegexScriptRepository
import me.kafuuneko.rpclient.libs.regex.RegexScriptRuntime
import me.kafuuneko.rpclient.libs.regex.ScopedRegexScript
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.repository.GroupChatData
import me.kafuuneko.rpclient.libs.room.repository.GroupChatMemberData
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.utils.formatTimestamp
import me.kafuuneko.rpclient.utils.filterLorebookGroups
import me.kafuuneko.rpclient.utils.toggle
import me.kafuuneko.rpclient.utils.toggleAll
import me.kafuuneko.rpclient.utils.toMessageCopyText
import me.kafuuneko.rpclient.model.toMessageContentParts
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * 多角色群聊页面的 ViewModel（状态持有者与业务控制器）。
 *
 * 核心职责：
 * - 群聊会话管理：初始化加载、多角色成员（Member）管理（增删、静音、排序微调）、群聊设置（设定/提示词覆盖/卡片模式/激活策略）。
 * - 调度与激活策略（Activation Strategy）：
 *   - 手动选择（Manual）：用户点选指定角色发言。
 *   - 自然选择/轮询等自动策略：基于上一轮对话激活文本（Activation Text）与角色匹配规则，自动挑选本轮一位或多位角色顺序发言。
 * - 自动模式（AutoMode）：支持角色自主交谈，单轮生成结束后在延时后继续触发下一位发言者。
 * - 群聊 Prompt 构建与清洗：
 *   - 聚合所有成员角色卡（Join 合并模式或单独模式）、群聊世界书、多成员正则脚本。
 *   - 使用 [GroupChatOutputSanitizer] 清洗大模型可能冒充其他成员发言的多余文本，支持 `trimOtherSpeakers` 截断。
 * - 群聊流式控制与原子持久化：内存更新 UI 状态，收尾时原子写入消息并更新世界书时序快照。
 * - 分段总结（Summary）：自动/手动多角色群聊增量摘要生成与回滚。
 */
class GroupChatViewModel :
    CoreViewModelWithEvent<GroupChatUiIntent, GroupChatUiState>(
        GroupChatUiState.None
    ), KoinComponent {
    // 数据仓库与服务依赖注入
    private val mGroupChatRepository by inject<GroupChatRepository>()
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mLLMRepository by inject<LLMRepository>()
    private val mProviderSelectionResolver by inject<LLMProviderSelectionResolver>()
    private val mPromptBuilder by inject<GroupChatPromptBuilder>()
    private val mSpeakerSelector by inject<GroupChatSpeakerSelector>()
    private val mSummaryPromptBuilder by inject<GroupChatSummaryPromptBuilder>()
    private val mOutputSanitizer by inject<GroupChatOutputSanitizer>()
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mRegexRepository by inject<RegexScriptRepository>()
    private val mRegexProcessor by inject<RegexMessageProcessor>()
    private val mContext by inject<Context>()

    /** 当前页面绑定的群聊会话 ID。 */
    private var mSessionId: Long? = null
    /** 当前正在运行的模型生成任务协程 Job，用于停止生成并防止并发请求。 */
    private var mGenerationJob: Job? = null
    /** 当前流式生成在数据库中创建的占位消息 ID。 */
    private var mStreamingMessageId: Long? = null
    /** 当前流式生成已累计接收到的原始文本内容。 */
    private var mStreamingContent: String = ""
    /** 本次生成固定绑定的正则脚本列表快照。 */
    private var mStreamingRegexScripts: List<ScopedRegexScript> = emptyList()
    /** 本次生成固定绑定的正则宏快照（包含群聊成员名单等宏）。 */
    private var mStreamingRegexMacros: Map<String, String> = emptyMap()
    /** 标记最终持久化的 Source 正则是否已执行，防止在收尾阶段重复执行。 */
    private var mStreamingRegexApplied: Boolean = false
    /** 最近一次实际发送给模型请求的 Prompt 检查报告。 */
    private var mLastPromptInspection: PromptInspection? = null
    /** 一次成员拖动的原始顺序与等待持久化的最终顺序。 */
    private var mPendingMemberOrder: PendingMemberOrder? = null
    /** 当前消息窗口最早记录的稳定分页游标。 */
    private var mOldestLoadedMessageCursor: GroupChatMessageCursor? = null
    /** 分页消息执行 Display Regex 所需的不含消息正文的群聊聚合快照。 */
    private var mMessageDisplayContext: GroupChatData? = null

    /**
     * 初始化群聊会话。
     *
     * @param intent 包含 sessionId 的初始化意图
     */
    @UiIntentObserver(GroupChatUiIntent.Init::class)
    private suspend fun onInit(intent: GroupChatUiIntent.Init) {
        if (!isStateOf<GroupChatUiState.None>()) return
        // 解析会话 ID
        val sessionId = intent.sessionId?.toLongOrNull()
        if (sessionId == null) {
            finishWithToast(R.string.invalid_session_id)
            return
        }
        mSessionId = sessionId
        // 异步从数据库加载群聊聚合状态
        val state = withContext(Dispatchers.IO) {
            loadState(sessionId)
        }
        if (state == null) {
            finishWithToast(R.string.group_chat_not_found)
            return
        }
        // 应用并展示正常群聊 UI 状态
        state.setup()
    }

    /**
     * 页面从后台恢复时的刷新处理，保持当前草稿、选中发言人、生成状态与对话框。
     */
    @UiIntentObserver(GroupChatUiIntent.Resume::class)
    private suspend fun onResume() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        refreshState(
            inputDraft = uiState.conversationState.inputDraft,
            selectedSpeakerId = uiState.conversationState.selectedSpeakerId,
            generationState = uiState.conversationState.generationState,
            editingMessageId = uiState.conversationState.editingMessageId,
            editingMessageDraft = uiState.conversationState.editingMessageDraft,
            dialogState = uiState.dialogState
        )
    }

    /**
     * 用户滚动到群聊消息窗口顶部时向前加载一页历史消息。
     *
     * - 使用创建时间和消息 ID 组成的稳定游标处理同时间消息。
     * - 生成与摘要继续读取完整持久化历史，不依赖当前 UI 消息窗口。
     * - 合并时保留加载期间可能发生的尾部状态更新。
     */
    @UiIntentObserver(GroupChatUiIntent.LoadOlderMessages::class)
    private suspend fun onLoadOlderMessages() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        val cursor = mOldestLoadedMessageCursor ?: return
        val displayContext = mMessageDisplayContext ?: return
        if (!uiState.conversationState.canLoadOlderMessages ||
            uiState.conversationState.isLoadingOlderMessages
        ) return

        // 先发布加载标记，阻止列表顶部连续发出相同请求
        uiState.copy(
            conversationState = uiState.conversationState.copy(
                isLoadingOlderMessages = true
            )
        ).setup()
        val loadedPage = try {
            withContext(Dispatchers.IO) {
                val page = mGroupChatRepository.getMessagePageBefore(
                    sessionId = sessionId,
                    beforeCreateTime = cursor.createTime,
                    beforeMessageId = cursor.messageId,
                    pageSize = MESSAGE_PAGE_SIZE
                )
                LoadedGroupChatMessagePage(
                    items = displayContext.copy(messages = page.messages).toMessageItems(
                        newerMessageCount = uiState.conversationState.messages.size
                    ),
                    cursor = page.messages.firstOrNull()?.toGroupChatMessageCursor(),
                    canLoadOlderMessages = page.canLoadOlderMessages
                )
            }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            val currentState = getOrNull<GroupChatUiState.Normal>() ?: return
            currentState.copy(
                conversationState = currentState.conversationState.copy(
                    isLoadingOlderMessages = false
                )
            ).setup()
            return
        }

        // 使用最新状态合并页面，避免覆盖流式生成的末尾消息内容
        val currentState = getOrNull<GroupChatUiState.Normal>() ?: return
        val existingIds = currentState.conversationState.messages.mapTo(mutableSetOf()) { it.id }
        val olderItems = loadedPage.items.filterNot { it.id in existingIds }
        mOldestLoadedMessageCursor = loadedPage.cursor ?: cursor
        currentState.copy(
            conversationState = currentState.conversationState.copy(
                messages = olderItems + currentState.conversationState.messages,
                canLoadOlderMessages = loadedPage.canLoadOlderMessages,
                isLoadingOlderMessages = false
            )
        ).setup()
    }

    /**
     * 处理返回事件。
     *
     * 设置页切回对话页；若正在生成则取消任务、落库已生成内容并退出页面。
     */
    @UiIntentObserver(GroupChatUiIntent.Back::class)
    private suspend fun onBack() {
        val uiState = getOrNull<GroupChatUiState.Normal>()
        if (uiState?.page == GroupChatPage.Settings) {
            refreshState(page = GroupChatPage.Conversation)
            return
        }
        mGenerationJob?.cancel()
        persistOrDeleteStreamingMessage()
        GroupChatUiState.finished(uiStateFlow.value).setup()
    }

    /**
     * 打开群聊设置页面（[GroupChatPage.Settings]）。
     */
    @UiIntentObserver(GroupChatUiIntent.OpenSettings::class)
    private fun onOpenSettings() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        if (mGenerationJob?.isActive == true) return
        uiState.copy(page = GroupChatPage.Settings).setup()
    }

    /** 打开当前群聊会话的世界书快捷管理对话框。 */
    @UiIntentObserver(GroupChatUiIntent.ShowSessionLoreDialog::class)
    private fun onShowSessionLoreDialog() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        val groups = uiState.settingsState.lorebookGroups
        val enabledEntryIds = groups.flatMap { it.entries }
            .filter { it.enabled }
            .mapTo(mutableSetOf()) { it.id }
        uiState.copy(
            dialogState = GroupChatDialogState.SessionLorebook(
                query = "",
                visibleGroups = groups,
                enabledEntryIds = enabledEntryIds
            )
        ).setup()
    }

    /** 更新群聊快捷管理对话框中的世界书搜索词与过滤结果。 */
    @UiIntentObserver(GroupChatUiIntent.ChangeSessionLorebookDialogQuery::class)
    private fun onChangeSessionLorebookDialogQuery(
        intent: GroupChatUiIntent.ChangeSessionLorebookDialogQuery
    ) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? GroupChatDialogState.SessionLorebook ?: return
        uiState.copy(
            dialogState = dialogState.copy(
                query = intent.value,
                visibleGroups = uiState.settingsState.lorebookGroups.filterForQuery(intent.value)
            )
        ).setup()
    }

    /** 切换群聊快捷管理对话框草稿中的单个条目。 */
    @UiIntentObserver(GroupChatUiIntent.ToggleSessionLorebookDialogEntry::class)
    private fun onToggleSessionLorebookDialogEntry(
        intent: GroupChatUiIntent.ToggleSessionLorebookDialogEntry
    ) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? GroupChatDialogState.SessionLorebook ?: return
        val entryExists = uiState.settingsState.lorebookGroups.any { group ->
            group.entries.any { it.id == intent.entryId }
        }
        if (!entryExists) return
        uiState.copy(
            dialogState = dialogState.copy(
                enabledEntryIds = dialogState.enabledEntryIds.toggle(intent.entryId)
            )
        ).setup()
    }

    /** 切换群聊快捷管理对话框草稿中的整个世界书分组。 */
    @UiIntentObserver(GroupChatUiIntent.ToggleSessionLorebookDialogGroup::class)
    private fun onToggleSessionLorebookDialogGroup(
        intent: GroupChatUiIntent.ToggleSessionLorebookDialogGroup
    ) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? GroupChatDialogState.SessionLorebook ?: return
        // 分组开关始终作用于完整分组，不受当前搜索结果裁剪影响。
        val entryIds = uiState.settingsState.lorebookGroups
            .firstOrNull { it.lorebookId == intent.lorebookId }
            ?.entries
            ?.mapTo(mutableSetOf()) { it.id }
            .orEmpty()
        if (entryIds.isEmpty()) return
        uiState.copy(
            dialogState = dialogState.copy(
                enabledEntryIds = dialogState.enabledEntryIds.toggleAll(entryIds)
            )
        ).setup()
    }

    /** 跳转至全局世界书管理界面，并关闭当前快捷管理对话框。 */
    @UiIntentObserver(GroupChatUiIntent.OpenWorldBookManager::class)
    private fun onOpenWorldBookManager() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        uiState.copy(dialogState = GroupChatDialogState.None).setup()
        AppViewEvent.StartActivity(WorldBookListActivity::class.java).tryEmit()
    }

    /**
     * 打开全局模型服务配置管理界面。
     */
    @UiIntentObserver(GroupChatUiIntent.OpenProviderSettings::class)
    private fun onOpenProviderSettings() {
        val uiState = getOrNull<GroupChatUiState.Normal>()
        uiState?.copy(dialogState = GroupChatDialogState.None)?.setup()
        AppViewEvent.StartActivity(LLMProviderListActivity::class.java).tryEmit()
    }

    /**
     * 打开 Prompt 检查器对话框，展示群聊最近一次请求的 Prompt 组成结构。
     */
    @UiIntentObserver(GroupChatUiIntent.OpenPromptInspector::class)
    private fun onOpenPromptInspector() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        val inspection = mLastPromptInspection
        if (inspection == null) {
            AppViewEvent.PopupToastMessageByResId(R.string.prompt_inspector_unavailable).tryEmit()
            return
        }
        uiState.copy(
            dialogState = GroupChatDialogState.PromptInspector(inspection)
        ).setup()
    }

    /**
     * 复制 Prompt 检查器中的指定文本项到剪贴板。
     *
     * @param intent 包含文本内容的意图
     */
    @UiIntentObserver(GroupChatUiIntent.CopyPromptItem::class)
    private fun onCopyPromptItem(intent: GroupChatUiIntent.CopyPromptItem) {
        if (!isStateOf<GroupChatUiState.Normal>()) return
        GroupChatViewEvent.CopyText(intent.text).tryEmit()
    }

    /**
     * 关闭群聊设置页面，切回对话主页面。
     */
    @UiIntentObserver(GroupChatUiIntent.CloseSettings::class)
    private suspend fun onCloseSettings() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        refreshState(
            page = GroupChatPage.Conversation,
            inputDraft = uiState.conversationState.inputDraft,
            selectedSpeakerId = uiState.conversationState.selectedSpeakerId
        )
    }

    /**
     * 修改群聊输入框草稿文本。
     *
     * @param intent 包含草稿文本的意图
     */
    @UiIntentObserver(GroupChatUiIntent.ChangeInputDraft::class)
    private fun onChangeInputDraft(intent: GroupChatUiIntent.ChangeInputDraft) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        uiState.copy(
            conversationState = uiState.conversationState.copy(inputDraft = intent.value)
        ).setup()
    }

    /**
     * 用户点击选择群聊发言者。
     *
     * 行为分发：
     * - 手动模式（Manual）：在 UI 上高亮选中该角色作为下次发送时的发言者。
     * - 自动/其他策略模式：点击头像视为“强制立即触发该成员发言一轮”。
     *
     * @param intent 包含目标角色 ID 的意图
     */
    @UiIntentObserver(GroupChatUiIntent.SelectSpeaker::class)
    private suspend fun onSelectSpeaker(intent: GroupChatUiIntent.SelectSpeaker) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        val member = uiState.members
            .firstOrNull { it.id == intent.characterId } ?: return
        // 手动模式下仅切换 UI 选中的发言人
        if (uiState.activeActivationStrategy == GroupChatActivationStrategy.Manual) {
            if (member.muted) return
            uiState.copy(
                conversationState = uiState.conversationState.copy(
                    selectedSpeakerId = intent.characterId
                )
            ).setup()
            return
        }
        // 自动或其他策略模式下，点击头像视为强制触发该角色立即生成一轮回复
        if (mGenerationJob?.isActive == true) return
        val data = withContext(Dispatchers.IO) {
            mGroupChatRepository.getGroupChatData(uiState.sessionId)
        } ?: return
        val forcedSpeaker = data.members.firstOrNull {
            it.character.id == intent.characterId
        } ?: return
        launchGeneration(uiState.sessionId, listOf(forcedSpeaker))
    }

    /**
     * 切换群聊成员的静音（禁言）状态。
     *
     * 保护规则：群聊中必须至少保留一个非静音的活跃成员。
     *
     * @param intent 包含目标角色 ID 的意图
     */
    @UiIntentObserver(GroupChatUiIntent.ToggleMemberMuted::class)
    private suspend fun onToggleMemberMuted(intent: GroupChatUiIntent.ToggleMemberMuted) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        if (mGenerationJob?.isActive == true) return
        val member = uiState.members
            .firstOrNull { it.id == intent.characterId } ?: return
        // 保护规则：群聊中必须至少保留一位活跃（未禁言）成员
        if (!member.muted && uiState.members.count { !it.muted } <= 1) {
            AppViewEvent.PopupToastMessageByResId(
                R.string.group_chat_keep_one_active_member
            ).tryEmit()
            return
        }
        // 异步写库更新静音状态
        withContext(Dispatchers.IO) {
            mGroupChatRepository.updateMemberMuted(
                sessionId = uiState.sessionId,
                characterId = member.id,
                muted = !member.muted
            )
        }
        // 刷新状态并清除非法的已选发言人
        refreshState(
            inputDraft = uiState.conversationState.inputDraft,
            selectedSpeakerId = uiState.conversationState.selectedSpeakerId
                ?.takeIf { it != member.id || member.muted }
        )
    }

    /**
     * 校验群聊当前是否存在可用模型服务；若无可用服务则唤起引导弹窗。
     *
     * @return true 表示已就绪，false 表示已拦截并弹出引导
     */
    private suspend fun ensureProviderConfigured(): Boolean {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return false
        if (!uiState.hasAvailableProvider) {
            val guide = noProviderModelSettingsGuide(mContext)
            refreshState(dialogState = guide.toGroupChatDialogState())
            return false
        }
        return true
    }

    /**
     * 发送群聊消息并启动多角色发言流程。
     *
     * 业务流程：
     * - 提取输入并执行用户 Source 正则；
     * - 计算激活文本（若用户有输入则使用用户输入，否则取上一条非系统消息作为激活参考）；
     * - 由 [GroupChatSpeakerSelector] 依激活策略选取本轮发言角色列表（可能为 1 人或多人）；
     * - 若有非空用户输入，持久化用户消息；
     * - 启动 [launchGeneration] 执行多角色发言循环。
     */
    @UiIntentObserver(GroupChatUiIntent.SendMessage::class)
    private suspend fun onSendMessage() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        if (!ensureProviderConfigured()) return
        // 并发拦截
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(
                R.string.generation_already_running
            ).tryEmit()
            return
        }
        val sessionId = mSessionId ?: return
        val rawInput = uiState.conversationState.inputDraft.trim()
        val initialData = withContext(Dispatchers.IO) {
            mGroupChatRepository.getGroupChatData(sessionId)
        } ?: return
        // 执行用户输入端 Source 正则
        val input = applyUserRegex(initialData, rawInput)
        val isUserInput = rawInput.isNotBlank()
        // 确定激活文本（若有用户输入使用用户输入，否则使用上一条消息）
        val activationText = if (isUserInput) {
            input
        } else {
            initialData.messages.lastOrNull {
                it.source != GroupChatMessage.Source.System
            }?.content.orEmpty()
        }
        // 由调度策略选择器选出本轮发言角色列表
        val speakers = mSpeakerSelector.select(
            session = initialData.session,
            members = initialData.members,
            messages = initialData.messages,
            activationText = activationText,
            isUserInput = isUserInput,
            manualCharacterId = uiState.conversationState.selectedSpeakerId
        )
        if (speakers.isEmpty()) {
            AppViewEvent.PopupToastMessageByResId(
                R.string.group_chat_select_speaker
            ).tryEmit()
            return
        }
        // 若有用户输入则持久化一条 User 消息
        if (isUserInput && input.isNotBlank()) {
            withContext(Dispatchers.IO) {
                mGroupChatRepository.createMessage(
                    sessionId = sessionId,
                    source = GroupChatMessage.Source.User,
                    content = input,
                    speakerCharacterId = null,
                    speakerNameSnapshot = initialData.session.userName
                )
            }
        }
        // 切换 UI 为生成中状态并启动多角色生成循环
        refreshState(
            inputDraft = "",
            selectedSpeakerId = uiState.conversationState.selectedSpeakerId,
            generationState = GroupChatGenerationState.Generating(
                speakerName = speakers.first().character.name,
                current = 1,
                total = speakers.size
            )
        )
        launchGeneration(sessionId, speakers)
    }

    /**
     * 停止当前的群聊生成任务。
     *
     * 取消协程、执行 Source 正则、落库已生成部分内容，并恢复 UI 空闲状态。
     */
    @UiIntentObserver(GroupChatUiIntent.StopGeneration::class)
    private suspend fun onStopGeneration() {
        if (!isStateOf<GroupChatUiState.Normal>()) return
        val job = mGenerationJob ?: return
        if (!job.isActive) return
        job.cancel()
        applyStreamingAiRegex()
        persistOrDeleteStreamingMessage()
        refreshState(generationState = GroupChatGenerationState.Idle)
    }

    /**
     * 手动触发立即总结群聊历史。
     */
    @UiIntentObserver(GroupChatUiIntent.SummarizeNow::class)
    private suspend fun onSummarizeNow() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        if (mGenerationJob?.isActive == true) return
        if (!ensureProviderConfigured()) return
        uiState.copy(loadState = GroupChatLoadState.Summarizing).setup()
        summarizeSession(uiState.sessionId, showToast = true)
        refreshState(
            page = uiState.page,
            generationState = GroupChatGenerationState.Idle
        )
    }

    /**
     * 恢复/回滚至上一版群聊摘要。
     */
    @UiIntentObserver(GroupChatUiIntent.RestorePreviousSummary::class)
    private suspend fun onRestorePreviousSummary() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        if (mGenerationJob?.isActive == true) return
        val restored = withContext(Dispatchers.IO) {
            mGroupChatRepository.restorePreviousSummary(uiState.sessionId)
        }
        AppViewEvent.PopupToastMessageByResId(
            if (restored) R.string.summary_restored else R.string.no_previous_summary
        ).tryEmit()
        refreshState(page = GroupChatPage.Settings)
    }

    /**
     * 修改群聊标题草稿。
     *
     * @param intent 包含新标题文本的意图
     */
    @UiIntentObserver(GroupChatUiIntent.ChangeTitle::class)
    private fun onChangeTitle(intent: GroupChatUiIntent.ChangeTitle) {
        if (!isStateOf<GroupChatUiState.Normal>()) return
        updateSettingsState { copy(titleDraft = intent.value) }
    }

    /**
     * 修改群聊场景设定（Scenario）草稿。
     *
     * @param intent 包含场景设定文本的意图
     */
    @UiIntentObserver(GroupChatUiIntent.ChangeScenario::class)
    private fun onChangeScenario(intent: GroupChatUiIntent.ChangeScenario) {
        if (!isStateOf<GroupChatUiState.Normal>()) return
        updateSettingsState { copy(scenarioDraft = intent.value) }
    }

    /**
     * 修改群聊用户备注（User Note）草稿。
     *
     * @param intent 包含用户备注文本的意图
     */
    @UiIntentObserver(GroupChatUiIntent.ChangeUserNote::class)
    private fun onChangeUserNote(intent: GroupChatUiIntent.ChangeUserNote) {
        if (!isStateOf<GroupChatUiState.Normal>()) return
        updateSettingsState { copy(userNoteDraft = intent.value) }
    }

    /**
     * 修改群聊摘要正文草稿。
     *
     * @param intent 包含摘要文本的意图
     */
    @UiIntentObserver(GroupChatUiIntent.ChangeSummary::class)
    private fun onChangeSummary(intent: GroupChatUiIntent.ChangeSummary) {
        if (!isStateOf<GroupChatUiState.Normal>()) return
        updateSettingsState { copy(summaryDraft = intent.value) }
    }

    /**
     * 切换群聊自动总结是否暂停。
     *
     * @param intent 包含暂停标志的意图
     */
    @UiIntentObserver(GroupChatUiIntent.ToggleAutoSummaryPaused::class)
    private fun onToggleAutoSummaryPaused(
        intent: GroupChatUiIntent.ToggleAutoSummaryPaused
    ) {
        if (!isStateOf<GroupChatUiState.Normal>()) return
        updateSettingsState { copy(autoSummaryPaused = intent.paused) }
    }

    /**
     * 修改群聊系统提示词覆盖（System Prompt Override）草稿。
     *
     * @param intent 包含系统提示词文本的意图
     */
    @UiIntentObserver(GroupChatUiIntent.ChangeSystemPrompt::class)
    private fun onChangeSystemPrompt(intent: GroupChatUiIntent.ChangeSystemPrompt) {
        if (!isStateOf<GroupChatUiState.Normal>()) return
        updateSettingsState { copy(systemPromptDraft = intent.value) }
    }

    /**
     * 修改群聊推进提示词（Group Nudge Prompt）草稿。
     *
     * @param intent 包含推进提示词文本的意图
     */
    @UiIntentObserver(GroupChatUiIntent.ChangeGroupNudgePrompt::class)
    private fun onChangeGroupNudgePrompt(
        intent: GroupChatUiIntent.ChangeGroupNudgePrompt
    ) {
        if (!isStateOf<GroupChatUiState.Normal>()) return
        updateSettingsState { copy(groupNudgePromptDraft = intent.value) }
    }

    /**
     * 修改新群聊提示词覆盖（New Group Chat Prompt）草稿。
     *
     * @param intent 包含新群聊提示词文本的意图
     */
    @UiIntentObserver(GroupChatUiIntent.ChangeNewGroupChatPrompt::class)
    private fun onChangeNewGroupChatPrompt(
        intent: GroupChatUiIntent.ChangeNewGroupChatPrompt
    ) {
        if (!isStateOf<GroupChatUiState.Normal>()) return
        updateSettingsState { copy(newGroupChatPromptDraft = intent.value) }
    }

    /**
     * 选择群聊成员激活/发言策略（如 Manual, Natural, RoundRobin 等）。
     *
     * @param intent 包含所选激活策略的意图
     */
    @UiIntentObserver(GroupChatUiIntent.SelectActivationStrategy::class)
    private fun onSelectActivationStrategy(
        intent: GroupChatUiIntent.SelectActivationStrategy
    ) {
        if (!isStateOf<GroupChatUiState.Normal>()) return
        updateSettingsState { copy(activationStrategy = intent.strategy) }
    }

    /**
     * 选择角色卡注入模式（单独注入当前发言者卡 vs 合并注入所有成员角色卡）。
     *
     * @param intent 包含所选角色卡模式的意图
     */
    @UiIntentObserver(GroupChatUiIntent.SelectCharacterCardMode::class)
    private fun onSelectCharacterCardMode(
        intent: GroupChatUiIntent.SelectCharacterCardMode
    ) {
        if (!isStateOf<GroupChatUiState.Normal>()) return
        updateSettingsState { copy(characterCardMode = intent.mode) }
    }

    /**
     * 切换合并卡模式下是否包含被禁言/静音成员的角色卡。
     *
     * @param intent 包含开关标志的意图
     */
    @UiIntentObserver(GroupChatUiIntent.ToggleIncludeMutedCards::class)
    private fun onToggleIncludeMutedCards(
        intent: GroupChatUiIntent.ToggleIncludeMutedCards
    ) {
        if (!isStateOf<GroupChatUiState.Normal>()) return
        updateSettingsState { copy(includeMutedCards = intent.enabled) }
    }

    /**
     * 切换群聊自动模式（AutoMode）。
     *
     * 开启后，在非手动模式下，单轮角色生成完成后将在短暂延时后自动触发下一位角色发言，形成自主群聊。
     *
     * @param intent 包含自动模式开关的意图
     */
    @UiIntentObserver(GroupChatUiIntent.ToggleAutoMode::class)
    private fun onToggleAutoMode(intent: GroupChatUiIntent.ToggleAutoMode) {
        if (!isStateOf<GroupChatUiState.Normal>()) return
        updateSettingsState { copy(autoModeEnabled = intent.enabled) }
    }

    /**
     * 切换是否在生成结果中裁剪其他角色的冒充发言内容。
     *
     * @param intent 包含开关标志的意图
     */
    @UiIntentObserver(GroupChatUiIntent.ToggleTrimOtherSpeakers::class)
    private fun onToggleTrimOtherSpeakers(
        intent: GroupChatUiIntent.ToggleTrimOtherSpeakers
    ) {
        if (!isStateOf<GroupChatUiState.Normal>()) return
        updateSettingsState { copy(trimOtherSpeakers = intent.enabled) }
    }

    /**
     * 切换是否允许同一角色连续多轮发言（Self-Responses）。
     *
     * @param intent 包含开关标志的意图
     */
    @UiIntentObserver(GroupChatUiIntent.ToggleAllowSelfResponses::class)
    private fun onToggleAllowSelfResponses(
        intent: GroupChatUiIntent.ToggleAllowSelfResponses
    ) {
        if (!isStateOf<GroupChatUiState.Normal>()) return
        updateSettingsState { copy(allowSelfResponses = intent.enabled) }
    }

    /**
     * 搜索过滤群聊设置页中的世界书条目列表。
     *
     * @param intent 包含搜索关键字的意图
     */
    @UiIntentObserver(GroupChatUiIntent.ChangeLorebookQuery::class)
    private fun onChangeLorebookQuery(intent: GroupChatUiIntent.ChangeLorebookQuery) {
        if (!isStateOf<GroupChatUiState.Normal>()) return
        updateSettingsState {
            copy(
                lorebookQuery = intent.value,
                visibleLorebookGroups = lorebookGroups.filterForQuery(intent.value)
            )
        }
    }

    /**
     * 保存群聊设置到数据库。
     *
     * 仅在摘要正文确有变动时才调用 updateCurrentSummary 更新覆盖边界，避免重复写入。
     */
    @UiIntentObserver(GroupChatUiIntent.SaveSettings::class)
    private suspend fun onSaveSettings() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        uiState.copy(loadState = GroupChatLoadState.Saving).setup()
        // 异步将设置页表单修改持久化至群聊会话记录
        withContext(Dispatchers.IO) {
            val session = mGroupChatRepository.getSessionById(uiState.sessionId)
                ?: return@withContext
            mGroupChatRepository.updateSession(
                session.copy(
                    title = uiState.settingsState.titleDraft.trim().ifBlank { session.title },
                    scenario = uiState.settingsState.scenarioDraft.trim(),
                    userNote = uiState.settingsState.userNoteDraft.trim(),
                    activationStrategy = uiState.settingsState.activationStrategy.toEntity(),
                    allowSelfResponses = uiState.settingsState.allowSelfResponses,
                    characterCardMode = uiState.settingsState.characterCardMode.toEntity(),
                    includeMutedCards = uiState.settingsState.includeMutedCards,
                    autoModeEnabled = uiState.settingsState.autoModeEnabled,
                    trimOtherSpeakers = uiState.settingsState.trimOtherSpeakers,
                    autoSummaryPaused = uiState.settingsState.autoSummaryPaused,
                    systemPromptOverride = uiState.settingsState.systemPromptDraft.trim(),
                    groupNudgePromptOverride = uiState.settingsState.groupNudgePromptDraft.trim(),
                    newGroupChatPromptOverride = uiState.settingsState.newGroupChatPromptDraft.trim()
                )
            )
            // 若摘要草稿有变更，更新当前摘要正文
            if (uiState.settingsState.summaryDraft != uiState.settingsState.summaryDraft.trim()) {
                mGroupChatRepository.updateCurrentSummary(
                    uiState.sessionId,
                    uiState.settingsState.summaryDraft.trim()
                )
            } else {
                val currentSummary = mGroupChatRepository
                    .getGroupChatData(uiState.sessionId)
                    ?.summary
                    ?.content
                    .orEmpty()
                if (currentSummary != uiState.settingsState.summaryDraft) {
                    mGroupChatRepository.updateCurrentSummary(
                        uiState.sessionId,
                        uiState.settingsState.summaryDraft
                    )
                }
            }
        }
        // 保存完成后重载会话数据并返回群聊对话页
        refreshState(page = GroupChatPage.Conversation)
    }

    /**
     * 切换群聊会话中单个世界书条目的启用/禁用状态。
     *
     * @param intent 包含条目 ID 的意图
     */
    @UiIntentObserver(GroupChatUiIntent.ToggleLorebookEntry::class)
    private suspend fun onToggleLorebookEntry(
        intent: GroupChatUiIntent.ToggleLorebookEntry
    ) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        // 计算切换后的启用条目集合
        val enabledIds = uiState.settingsState.lorebookGroups
            .flatMap { it.entries }
            .filter { it.enabled }
            .map { it.id }
            .toMutableSet()
        if (!enabledIds.add(intent.entryId)) enabledIds.remove(intent.entryId)
        // 异步保存条目配置至数据库
        withContext(Dispatchers.IO) {
            mGroupChatRepository.updateSessionLorebookEntryIds(
                uiState.sessionId,
                enabledIds.toList()
            )
        }
        // 刷新当前页面
        refreshState(page = uiState.page)
    }

    /**
     * 批量切换群聊会话中指定世界书的所有条目启用/禁用状态。
     *
     * @param intent 包含世界书 ID 的意图
     */
    @UiIntentObserver(GroupChatUiIntent.ToggleLorebook::class)
    private suspend fun onToggleLorebook(intent: GroupChatUiIntent.ToggleLorebook) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        val entryIds = uiState.settingsState.lorebookGroups
            .firstOrNull { it.lorebookId == intent.lorebookId }
            ?.entries
            ?.mapTo(mutableSetOf()) { it.id }
            .orEmpty()
        if (entryIds.isEmpty()) return
        // 批量反转目标世界书下全部条目的勾选状态
        val enabledIds = uiState.settingsState.lorebookGroups
            .flatMap { it.entries }
            .filter { it.enabled }
            .mapTo(mutableSetOf()) { it.id }
            .toggleAll(entryIds)
        // 异步保存至数据库
        withContext(Dispatchers.IO) {
            mGroupChatRepository.updateSessionLorebookEntryIds(
                uiState.sessionId,
                enabledIds.toList()
            )
        }
        // 刷新页面
        refreshState(page = uiState.page)
    }

    /**
     * 确认并保存快捷管理对话框中的世界书条目选择。
     */
    @UiIntentObserver(GroupChatUiIntent.ConfirmSessionLorebookSelection::class)
    private suspend fun onConfirmSessionLorebookSelection() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? GroupChatDialogState.SessionLorebook ?: return
        // 提交前剔除已经被删除的条目 ID，再一次性覆盖群聊会话配置。
        val validEntryIds = uiState.settingsState.lorebookGroups
            .flatMap { it.entries }
            .mapTo(mutableSetOf()) { it.id }
        val enabledEntryIds = dialogState.enabledEntryIds.intersect(validEntryIds)
        withContext(Dispatchers.IO) {
            mGroupChatRepository.updateSessionLorebookEntryIds(
                uiState.sessionId,
                enabledEntryIds.toList()
            )
        }
        refreshState(
            page = uiState.page,
            dialogState = GroupChatDialogState.None
        )
    }

    /**
     * 向群聊中添加新成员角色。
     *
     * @param intent 包含待添加角色 ID 的意图
     */
    @UiIntentObserver(GroupChatUiIntent.AddMember::class)
    private suspend fun onAddMember(intent: GroupChatUiIntent.AddMember) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        withContext(Dispatchers.IO) {
            mGroupChatRepository.addMember(uiState.sessionId, intent.characterId)
        }
        refreshState(page = uiState.page)
    }

    /**
     * 从群聊中移除成员角色。
     *
     * 约束：群聊至少需要保留 2 位成员。
     *
     * @param intent 包含待移除角色 ID 的意图
     */
    @UiIntentObserver(GroupChatUiIntent.RemoveMember::class)
    private suspend fun onRemoveMember(intent: GroupChatUiIntent.RemoveMember) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        runCatching {
            withContext(Dispatchers.IO) {
                mGroupChatRepository.removeMember(uiState.sessionId, intent.characterId)
            }
        }.onFailure {
            AppViewEvent.PopupToastMessageByResId(R.string.group_chat_select_two_characters).tryEmit()
        }
        refreshState(page = uiState.page)
    }

    /**
     * 调整群聊成员在列表中的顺序位置。
     *
     * @param intent 包含角色 ID 和相对位移 offset 的意图
     */
    @UiIntentObserver(GroupChatUiIntent.MoveMember::class)
    private suspend fun onMoveMember(intent: GroupChatUiIntent.MoveMember) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        withContext(Dispatchers.IO) {
            mGroupChatRepository.moveMember(
                uiState.sessionId,
                intent.characterId,
                intent.offset
            )
        }
        refreshState(page = uiState.page)
    }

    /** 在内存中即时重排群聊成员，为长按拖动提供连续反馈。 */
    @UiIntentObserver(GroupChatUiIntent.ReorderMember::class)
    private fun onReorderMember(intent: GroupChatUiIntent.ReorderMember) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        if (uiState.page != GroupChatPage.Settings) return
        val fromIndex = uiState.members.indexOfFirst { it.id == intent.fromCharacterId }
        val toIndex = uiState.members.indexOfFirst { it.id == intent.toCharacterId }
        if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return
        val originalIds = mPendingMemberOrder?.originalCharacterIds
            ?: uiState.members.map { it.id }
        // 拖动期间只更新页面状态，避免每次跨越成员都触发数据库刷新
        val reordered = uiState.members.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        mPendingMemberOrder = PendingMemberOrder(
            originalCharacterIds = originalIds,
            orderedCharacterIds = reordered.map { it.id }
        )
        uiState.copy(members = reordered).setup()
    }

    /** 拖动结束后以一次数据库事务提交成员最终顺序。 */
    @UiIntentObserver(GroupChatUiIntent.CommitMemberOrder::class)
    private suspend fun onCommitMemberOrder() {
        val pendingOrder = mPendingMemberOrder ?: return
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        if (uiState.page != GroupChatPage.Settings) return
        mPendingMemberOrder = null
        val committed = runCatching {
            withContext(Dispatchers.IO) {
                mGroupChatRepository.reorderMembers(
                    sessionId = uiState.sessionId,
                    orderedCharacterIds = pendingOrder.orderedCharacterIds
                )
            }
        }.getOrDefault(false)
        if (committed) return
        // 写入失败时恢复手势开始前的顺序，不影响设置页的其他未保存草稿
        val memberById = uiState.members.associateBy { it.id }
        val restored = pendingOrder.originalCharacterIds.mapNotNull(memberById::get)
        if (restored.size == uiState.members.size) {
            uiState.copy(members = restored).setup()
        }
    }

    /**
     * 开始编辑指定的群聊历史消息。
     *
     * @param intent 包含待编辑消息 ID 的意图
     */
    @UiIntentObserver(GroupChatUiIntent.StartEditMessage::class)
    private suspend fun onStartEditMessage(intent: GroupChatUiIntent.StartEditMessage) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        if (mGenerationJob?.isActive == true) return
        val message = uiState.conversationState.messages
            .firstOrNull { it.id == intent.messageId } ?: return
        // 异步从数据库读取未经 Display 正则修改的原始文本
        val rawContent = withContext(Dispatchers.IO) {
            mGroupChatRepository.getMessageById(intent.messageId)
                ?.takeIf { it.sessionId == uiState.sessionId }
                ?.content
        } ?: return
        // 将 UI 切换为编辑模式并填入草稿
        uiState.copy(
            conversationState = uiState.conversationState.copy(
                editingMessageId = message.id,
                editingMessageDraft = rawContent
            )
        ).setup()
    }

    /**
     * 复制群聊消息的展示内容到剪贴板。
     *
     * 全局设置不允许思考块进入上下文时，复制同样排除已保存的思考内容。
     *
     * @param intent 包含目标消息 ID 的意图
     */
    @UiIntentObserver(GroupChatUiIntent.CopyMessage::class)
    private suspend fun onCopyMessage(intent: GroupChatUiIntent.CopyMessage) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        val message = uiState.conversationState.messages
            .firstOrNull { it.id == intent.messageId } ?: return
        val copyText = message.content.toMessageCopyText(
            includeThinkBlocks = runCatching { AppModel.includeThinkInContext }.getOrDefault(false)
        )
        if (copyText.isBlank()) return
        GroupChatViewEvent.CopyText(copyText).emit()
    }

    /**
     * 展开/折叠消息中的思考过程块。
     *
     * @param intent 包含思考块 ID 的意图
     */
    @UiIntentObserver(GroupChatUiIntent.ToggleThinkBlock::class)
    private fun onToggleThinkBlock(intent: GroupChatUiIntent.ToggleThinkBlock) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        val ids = uiState.conversationState.expandedThinkBlockIds
        uiState.copy(
            conversationState = uiState.conversationState.copy(
                expandedThinkBlockIds = if (intent.blockId in ids) {
                    ids - intent.blockId
                } else {
                    ids + intent.blockId
                }
            )
        ).setup()
    }

    /**
     * 更新正在编辑的群聊消息草稿。
     *
     * @param intent 包含草稿文本的意图
     */
    @UiIntentObserver(GroupChatUiIntent.ChangeEditingMessageDraft::class)
    private fun onChangeEditingMessageDraft(
        intent: GroupChatUiIntent.ChangeEditingMessageDraft
    ) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        if (uiState.conversationState.editingMessageId == null) return
        uiState.copy(
            conversationState = uiState.conversationState.copy(
                editingMessageDraft = intent.value
            )
        ).setup()
    }

    /**
     * 保存对单条群聊消息的编辑。
     *
     * 根据消息来源分别应用 User 或 Character 的 Source 正则后持久化写库。
     */
    @UiIntentObserver(GroupChatUiIntent.SaveEditingMessage::class)
    private suspend fun onSaveEditingMessage() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        val messageId = uiState.conversationState.editingMessageId ?: return
        if (uiState.conversationState.editingMessageDraft.isBlank()) return
        // 异步对编辑后文本应用对应 Source 阶段正则规则（isEdit = true）
        withContext(Dispatchers.IO) {
            val data = mMessageDisplayContext ?: return@withContext
            val message = mGroupChatRepository.getMessageById(messageId)
                ?.takeIf { it.sessionId == uiState.sessionId } ?: return@withContext
            val content = when (message.source) {
                GroupChatMessage.Source.User -> applyUserRegex(
                    data,
                    uiState.conversationState.editingMessageDraft.trim(),
                    isEdit = true
                )
                GroupChatMessage.Source.Character -> applyAiRegex(
                    data,
                    uiState.conversationState.editingMessageDraft.trim(),
                    message.speakerNameSnapshot,
                    isEdit = true
                )
                GroupChatMessage.Source.System ->
                    uiState.conversationState.editingMessageDraft.trim()
            }
            // 将更新后的内容写回数据库
            mGroupChatRepository.updateMessageContent(
                messageId,
                content
            )
        }
        // 退出编辑状态并刷新 UI
        refreshState(editingMessageId = null, editingMessageDraft = "")
    }

    /**
     * 取消编辑消息，重置草稿与状态。
     */
    @UiIntentObserver(GroupChatUiIntent.CancelEditingMessage::class)
    private fun onCancelEditingMessage() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        uiState.copy(
            conversationState = uiState.conversationState.copy(
                editingMessageId = null,
                editingMessageDraft = ""
            )
        ).setup()
    }

    /**
     * 点击删除单条群聊消息，弹出确认删除对话框。
     *
     * @param intent 包含待删除消息 ID 的意图
     */
    @UiIntentObserver(GroupChatUiIntent.DeleteMessageClick::class)
    private fun onDeleteMessageClick(intent: GroupChatUiIntent.DeleteMessageClick) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        if (mGenerationJob?.isActive == true) return
        if (uiState.conversationState.messages.none { it.id == intent.messageId }) return
        uiState.copy(
            dialogState = GroupChatDialogState.DeleteMessageConfirm(intent.messageId)
        ).setup()
    }

    /**
     * 确认删除单条群聊消息。
     */
    @UiIntentObserver(GroupChatUiIntent.ConfirmDeleteMessage::class)
    private suspend fun onConfirmDeleteMessage() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? GroupChatDialogState.DeleteMessageConfirm ?: return
        if (mGenerationJob?.isActive == true) return
        withContext(Dispatchers.IO) {
            mGroupChatRepository.deleteMessage(dialog.messageId)
        }
        refreshState(
            editingMessageId = null,
            editingMessageDraft = "",
            dialogState = GroupChatDialogState.None
        )
    }

    /**
     * 从指定角色消息处重生成。
     *
     * 删除从该消息开始的所有后续消息，并由该消息的原发言角色重新生成一条回复。
     *
     * @param intent 包含待重生成消息 ID 的意图
     */
    @UiIntentObserver(GroupChatUiIntent.RegenerateMessage::class)
    private suspend fun onRegenerateMessage(intent: GroupChatUiIntent.RegenerateMessage) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        if (mGenerationJob?.isActive == true) return
        if (!ensureProviderConfigured()) return
        val data = withContext(Dispatchers.IO) {
            mGroupChatRepository.getGroupChatData(uiState.sessionId)
        } ?: return
        val message = data.messages.firstOrNull { it.id == intent.messageId } ?: return
        if (message.source != GroupChatMessage.Source.Character) return
        val speaker = data.members.firstOrNull {
            it.character.id == message.speakerCharacterId
        } ?: return
        // 从数据库中截断删除从该消息开始的所有后续历史记录
        withContext(Dispatchers.IO) {
            mGroupChatRepository.deleteMessagesFrom(message.id)
        }
        // 切换 UI 为生成中状态
        refreshState(
            editingMessageId = null,
            editingMessageDraft = "",
            generationState = GroupChatGenerationState.Generating(
                speakerName = speaker.character.name,
                current = 1,
                total = 1
            )
        )
        // 重新以 Regenerate 模式触发该角色的生成流程
        launchGeneration(
            sessionId = uiState.sessionId,
            speakers = listOf(speaker),
            generationMode = GroupChatGenerationMode.Regenerate
        )
    }

    /**
     * 由最后一条角色消息的原发言者续写下一条回复。
     *
     * 业务流程：
     * - 查找最后一条角色消息及其发言者（Speaker）；
     * - 以 [GroupChatGenerationMode.Continue] 模式调用大模型；
     * - 续写结果依然作为一条新消息保存，避免直接破坏已存在的历史记录。
     */
    @UiIntentObserver(GroupChatUiIntent.ContinueLast::class)
    private suspend fun onContinueLast() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        if (mGenerationJob?.isActive == true) return
        if (!ensureProviderConfigured()) return
        val data = withContext(Dispatchers.IO) {
            mGroupChatRepository.getGroupChatData(uiState.sessionId)
        } ?: return
        // 提取最后一条角色消息及对应角色发言者
        val last = data.messages.lastOrNull {
            it.source == GroupChatMessage.Source.Character
        } ?: return
        val speaker = data.members.firstOrNull {
            it.character.id == last.speakerCharacterId
        } ?: return
        val batchId = UUID.randomUUID().toString()
        // 启动续写任务
        mGenerationJob = viewModelScope.launch {
            runCatching {
                generateSpeakerReply(
                    sessionId = uiState.sessionId,
                    speaker = speaker,
                    batchId = batchId,
                    current = 1,
                    total = 1,
                    generationMode = GroupChatGenerationMode.Continue
                )
                refreshState(generationState = GroupChatGenerationState.Idle)
            }.onFailure { throwable ->
                // 异常处理：收尾未落库内容并报错
                val failure = throwable.toGenerationFailurePresentation(
                    mContext,
                    R.string.continue_generation_failed
                ) ?: return@onFailure
                val guideDialog = failure.modelSettingsGuide?.toGroupChatDialogState()
                persistOrDeleteStreamingMessage()
                refreshState(
                    generationState = GroupChatGenerationState.Failed(failure.message),
                    dialogState = guideDialog ?: GroupChatDialogState.None
                )
                if (guideDialog == null) {
                    AppViewEvent.PopupToastMessage(failure.message).tryEmit()
                }
            }
        }
    }

    /**
     * 点击删除当前群聊会话，弹出二次确认对话框。
     */
    @UiIntentObserver(GroupChatUiIntent.DeleteSessionClick::class)
    private fun onDeleteSessionClick() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        if (mGenerationJob?.isActive == true) return
        uiState.copy(
            dialogState = GroupChatDialogState.DeleteSessionConfirm(uiState.title)
        ).setup()
    }

    /**
     * 确认删除当前群聊会话，从数据库删除并关闭退出当前页面。
     */
    @UiIntentObserver(GroupChatUiIntent.ConfirmDeleteSession::class)
    private suspend fun onConfirmDeleteSession() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        if (uiState.dialogState !is GroupChatDialogState.DeleteSessionConfirm) return
        uiState.copy(
            loadState = GroupChatLoadState.Deleting,
            dialogState = GroupChatDialogState.None
        ).setup()
        withContext(Dispatchers.IO) {
            mGroupChatRepository.deleteSession(uiState.sessionId)
        }
        GroupChatUiState.finished(uiStateFlow.value).setup()
    }

    /**
     * 关闭当前展示的弹窗对话框。
     */
    @UiIntentObserver(GroupChatUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        uiState.copy(dialogState = GroupChatDialogState.None).setup()
    }

    /**
     * 启动一轮或自动多轮的群聊多角色生成循环。
     *
     * 调度架构：
     * - 批次标识（batchId）：本次触发选出的多位角色共享同一个生成批次 ID。
     * - 顺序生成：按选定顺序依次调用 [generateSpeakerReply] 生成回复。
     * - 自动模式循环（AutoMode Loop）：
     *   - 单轮结束后，若开启了 `autoModeEnabled` 且激活策略非手动（Manual），则延迟 [AUTO_MODE_DELAY_MS]（500ms）；
     *   - 从数据库重新加载最新群聊快照，依据上一轮的最后消息重新计算下一轮发言者列表，实现连续交谈；
     *   - 直至无发言者被激活或用户手动停止。
     * - 自动总结监测：全部生成结束后触发 [maybeAutoSummarize]。
     *
     * @param sessionId 会话 ID
     * @param speakers 本轮被选中的发言成员列表
     * @param generationMode 生成模式（Normal, Regenerate, Continue 等）
     */
    private fun launchGeneration(
        sessionId: Long,
        speakers: List<GroupChatMemberData>,
        generationMode: GroupChatGenerationMode = GroupChatGenerationMode.Normal
    ) {
        val batchId = UUID.randomUUID().toString()
        mGenerationJob = viewModelScope.launch {
            runCatching {
                var pendingSpeakers = speakers
                var nextGenerationMode = generationMode
                // 循环处理待发言角色列表（支持单批次及 AutoMode 自动追加的多轮批次）
                while (pendingSpeakers.isNotEmpty()) {
                    pendingSpeakers.forEachIndexed { index, speaker ->
                        currentCoroutineContext().ensureActive()
                        // 顺序生成当前发言角色的回复
                        generateSpeakerReply(
                            sessionId = sessionId,
                            speaker = speaker,
                            batchId = batchId,
                            current = index + 1,
                            total = pendingSpeakers.size,
                            generationMode = nextGenerationMode
                        )
                        // 首位发言者可能使用特殊模式（如 Regenerate/Continue），后续角色重置为 Normal
                        nextGenerationMode = GroupChatGenerationMode.Normal
                    }
                    // 加载最新群聊快照
                    val nextData = withContext(Dispatchers.IO) {
                        mGroupChatRepository.getGroupChatData(sessionId)
                    } ?: break
                    // 自动模式下重新选出下一轮发言角色
                    pendingSpeakers = if (
                        nextData.session.autoModeEnabled &&
                        nextData.session.activationStrategy !=
                        GroupChatSession.ActivationStrategy.Manual
                    ) {
                        delay(AUTO_MODE_DELAY_MS.milliseconds)
                        mSpeakerSelector.select(
                            session = nextData.session,
                            members = nextData.members,
                            messages = nextData.messages,
                            activationText = nextData.messages.lastOrNull {
                                it.source != GroupChatMessage.Source.System
                            }?.content.orEmpty(),
                            isUserInput = false,
                            manualCharacterId = null
                        )
                    } else {
                        emptyList()
                    }
                }
                // 检查是否触发自动总结
                maybeAutoSummarize(sessionId)
                // 恢复 UI 为空闲状态
                refreshState(generationState = GroupChatGenerationState.Idle)
            }.onFailure { throwable ->
                // 异常处理：收尾当前流式消息并更新失败状态
                val failure = throwable.toGenerationFailurePresentation(
                    mContext,
                    R.string.generation_failed
                ) ?: return@onFailure
                val guideDialog = failure.modelSettingsGuide?.toGroupChatDialogState()
                persistOrDeleteStreamingMessage()
                refreshState(
                    generationState = GroupChatGenerationState.Failed(failure.message),
                    dialogState = guideDialog ?: GroupChatDialogState.None
                )
                if (guideDialog == null) {
                    AppViewEvent.PopupToastMessage(failure.message).tryEmit()
                }
            }
        }
    }

    /**
     * 为群聊中指定的单个成员生成回复。
     *
     * 处理时序与安全策略：
     * - 组装上下文：读取群聊聚合数据，加载该角色关联的世界书上下文；
     * - 构建 Prompt：调用 [GroupChatPromptBuilder] 组装多角色 Prompt 请求与预算分配；
     * - 占位与流式生成：在数据库创建占位记录，根据流式开关分发调用模型；
     * - 冒名发言清洗：使用 [GroupChatOutputSanitizer.sanitize] 清洗可能出现的其他成员冒名对话，并应用 `trimOtherSpeakers` 截断；
     * - Source 正则与持久化：执行 AI 正则并提交最终文本，同时原子更新世界书时序快照。
     *
     * @param sessionId 会话 ID
     * @param speaker 当前轮到的发言角色
     * @param batchId 当前生成批次 ID
     * @param current 当前为本批次第几个发言者（1-indexed）
     * @param total 本批次总发言人数
     * @param generationMode 生成模式
     */
    private suspend fun generateSpeakerReply(
        sessionId: Long,
        speaker: GroupChatMemberData,
        batchId: String,
        current: Int,
        total: Int,
        generationMode: GroupChatGenerationMode = GroupChatGenerationMode.Normal
    ) {
        // 加载群聊快照、模型提供商与世界书上下文
        val data = withContext(Dispatchers.IO) {
            mGroupChatRepository.getGroupChatData(sessionId)
        } ?: error(mContext.getString(R.string.group_chat_not_found))
        val provider = withContext(Dispatchers.IO) {
            mProviderSelectionResolver.requireCharacterProvider(speaker.character)
        }
        val lorebookContext = withContext(Dispatchers.IO) {
            loadLorebookContext(data, speaker)
        }
        // 构建多角色群聊 Prompt 请求
        val buildResult = withContext(Dispatchers.Default) {
            mPromptBuilder.buildWithMetadata(
                GroupChatPromptContext(
                    session = data.session,
                    members = data.members,
                    speaker = speaker.character,
                    messages = data.messages,
                    summary = data.summary?.content.orEmpty(),
                    candidateLorebookEntries = lorebookContext.entries,
                    candidateLorebooks = lorebookContext.lorebooks,
                    recursiveScanningLorebookIds = lorebookContext.recursiveLorebookIds,
                    provider = provider,
                    generationMode = generationMode,
                    regexScripts = mRegexRepository.activeScripts(
                        data.members.map { it.character }
                    )
                )
            )
        }
        recordPromptInspection(buildResult.inspection)
        val request = buildResult.request
        // 在数据库中创建本条角色回复的占位记录
        mStreamingMessageId = withContext(Dispatchers.IO) {
            mGroupChatRepository.createMessage(
                sessionId = sessionId,
                source = GroupChatMessage.Source.Character,
                content = "",
                speakerCharacterId = speaker.character.id,
                speakerNameSnapshot = speaker.character.name,
                generationBatchId = batchId
            )
        }
        mStreamingContent = ""
        mStreamingRegexScripts = mRegexRepository.activeScripts(
            data.members.map { it.character }
        )
        mStreamingRegexMacros = groupRegexMacros(data, speaker.character.name)
        mStreamingRegexApplied = false
        // 更新 UI 为当前角色的生成中状态
        refreshState(
            generationState = GroupChatGenerationState.Generating(
                speakerName = speaker.character.name,
                current = current,
                total = total
            )
        )
        // 分发执行流式或非流式大模型调用
        if (AppModel.streamEnabled) {
            collectStreamingResponse(sessionId, provider, request)
        } else {
            val response = withContext(Dispatchers.IO) {
                mLLMRepository.generateWithProvider(
                    provider = provider,
                    request = request,
                    routingSessionKey = "group-chat:$sessionId"
                )
            }
            mStreamingContent = response.content
        }
        // 清洗模型可能冒充其他成员发言的内容并应用截断策略
        val sanitizedPart = mOutputSanitizer.sanitize(
            content = mStreamingContent,
            currentSpeakerName = speaker.character.name,
            otherSpeakerNames = data.members
                .map { it.character.name }
                .filterNot { it == speaker.character.name },
            trimOtherSpeakers = data.session.trimOtherSpeakers
        )
        // 执行持久化前的 Source 阶段正则
        val regexedPart = mRegexProcessor.applyAiResponse(
            input = sanitizedPart,
            scripts = mStreamingRegexScripts,
            macros = mStreamingRegexMacros
        )
        mStreamingContent = regexedPart
        mStreamingRegexApplied = true
        // 持久化最终内容并更新世界书激活快照
        val persisted = persistOrDeleteStreamingMessage()
        if (persisted && regexedPart.isNotBlank()) {
            withContext(Dispatchers.IO) {
                mGroupChatRepository.updateWorldInfoState(
                    sessionId,
                    buildResult.worldInfoStateJson
                )
            }
        }
        // 刷新 UI
        refreshState(
            generationState = GroupChatGenerationState.Generating(
                speakerName = speaker.character.name,
                current = current,
                total = total
            )
        )
    }

    /**
     * 收集大模型流式增量事件并实时更新内存中的群聊 UI 消息。
     *
     * @param sessionId 会话 ID
     * @param provider 模型服务配置
     * @param request 请求参数体
     */
    private suspend fun collectStreamingResponse(
        sessionId: Long,
        provider: LLMProvider,
        request: me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
    ) {
        mLLMRepository.streamGenerateWithProvider(
            provider = provider,
            request = request,
            routingSessionKey = "group-chat:$sessionId"
        ).collect { event ->
            currentCoroutineContext().ensureActive()
            if (event is LLMStreamEvent.Delta) {
                mStreamingContent += event.content
                updateStreamingState(mStreamingContent)
            }
        }
    }

    /**
     * 仅在内存 UI 状态中更新流式文本与 Markdown Display 正则渲染，避免高频写入数据库。
     *
     * @param content 当前累积的原始生成文本
     */
    private fun updateStreamingState(content: String) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        val messageId = mStreamingMessageId ?: return
        val displayContent = mRegexProcessor.applyDisplay(
            input = content,
            source = RegexMessageSource.Character,
            scripts = mStreamingRegexScripts,
            macros = mStreamingRegexMacros
        )
        uiState.copy(
            conversationState = uiState.conversationState.copy(
                messages = uiState.conversationState.messages.map {
                    if (it.id == messageId) {
                        it.copy(
                            content = displayContent,
                            parts = displayContent.toMessageContentParts(it.id.toString()),
                            isStreaming = true
                        )
                    } else {
                        it
                    }
                }
            )
        ).setup()
    }

    /**
     * 在生成结束、取消或出错时进行安全收尾与持久化。
     *
     * 若生成内容非空，则将最终内容更新到数据库；若为空（如尚未收到首字就取消），则删除占位记录。
     *
     * @return 是否成功持久化了非空消息
     */
    private suspend fun persistOrDeleteStreamingMessage(): Boolean {
        val messageId = mStreamingMessageId ?: return false
        // 确保已执行 Source 阶段正则
        applyStreamingAiRegex()
        val content = mStreamingContent
        val persisted = content.isNotBlank()
        // 异步写库：若内容非空则更新正文，否则删除空占位记录
        withContext(Dispatchers.IO) {
            if (!persisted) {
                mGroupChatRepository.deleteMessage(messageId)
            } else {
                mGroupChatRepository.updateMessageContent(messageId, content)
            }
        }
        // 重置流式状态字段
        mStreamingMessageId = null
        mStreamingContent = ""
        mStreamingRegexScripts = emptyList()
        mStreamingRegexMacros = emptyMap()
        mStreamingRegexApplied = false
        return persisted
    }

    /**
     * 检查是否达到群聊自动总结的触发条件，并在满足时执行自动总结。
     *
     * @param sessionId 会话 ID
     */
    private suspend fun maybeAutoSummarize(sessionId: Long) {
        if (!AppModel.autoSummaryEnabled) return
        val session = withContext(Dispatchers.IO) {
            mGroupChatRepository.getSessionById(sessionId)
        } ?: return
        if (session.autoSummaryPaused) return
        val messages = withContext(Dispatchers.IO) {
            mGroupChatRepository.getMessagesAfterLatestSummary(sessionId)
        }
        if (messages.size < AppModel.summaryTriggerMessageCount) return
        summarizeSession(sessionId, showToast = false)
    }

    /**
     * 生成群聊增量摘要并持久化。
     *
     * 摘要尚未覆盖的历史消息，并推进覆盖边界（coveredMessageId）到构建器实际选中的最后一条消息。
     *
     * @param sessionId 会话 ID
     * @param showToast 是否显示 Toast 提示信息
     */
    private suspend fun summarizeSession(sessionId: Long, showToast: Boolean) {
        runCatching {
            // 异步加载群聊数据、总结提供商与未总结消息切片
            val data = withContext(Dispatchers.IO) {
                mGroupChatRepository.getGroupChatData(sessionId)
            } ?: return
            val provider = withContext(Dispatchers.IO) {
                mProviderSelectionResolver.requireSummaryProvider()
            }
            val unsummarized = data.messages.filter {
                it.id > (data.summary?.coveredMessageId ?: 0L)
            }
            // 构建群聊增量摘要 Prompt
            val built = mSummaryPromptBuilder.buildWithSelection(
                session = data.session,
                memberNames = data.members.map { it.character.name },
                existingSummary = data.summary?.content.orEmpty(),
                messages = unsummarized,
                provider = provider
            )
            if (built.selectedMessages.isEmpty()) return
            // 调用模型生成摘要
            val response = withContext(Dispatchers.IO) {
                mLLMRepository.generateWithProvider(
                    provider = provider,
                    request = built.request,
                    routingSessionKey = "group-chat:$sessionId"
                )
            }
            val summaryContent = response.content.summarySafeContent()
            if (summaryContent.isBlank()) {
                error(mContext.getString(R.string.summary_failed))
            }
            // 持久化新摘要与覆盖边界消息 ID
            withContext(Dispatchers.IO) {
                mGroupChatRepository.saveSummary(
                    sessionId = sessionId,
                    content = summaryContent,
                    coveredMessageId = built.selectedMessages.last().id
                )
            }
            if (showToast) {
                AppViewEvent.PopupToastMessageByResId(R.string.summary_updated).tryEmit()
            }
        }.onFailure { throwable ->
            val failure = throwable.toGenerationFailurePresentation(
                mContext,
                R.string.summary_failed
            ) ?: throw throwable
            val guideDialog = failure.modelSettingsGuide?.toGroupChatDialogState()
            if (guideDialog != null) {
                val uiState = getOrNull<GroupChatUiState.Normal>() ?: return@onFailure
                uiState.copy(dialogState = guideDialog).setup()
            } else if (showToast) {
                AppViewEvent.PopupToastMessage(failure.message).tryEmit()
            }
        }
    }

    /**
     * 聚合群聊本轮生成所需的世界书上下文。
     *
     * 规则策略：
     * - 提取当前群聊会话中手动勾选启用的条目 ID；
     * - 依据角色卡模式（Join 合并卡模式提取所有未静音角色的世界书，Single 模式仅提取当前发言者的世界书）；
     * - 收集所有相关世界书及其条目，并计算支持递归扫描的世界书 ID 集合。
     *
     * @param data 群聊聚合数据快照
     * @param speaker 当前发言角色
     * @return 组装完成的世界书上下文 [GroupLorebookContext]
     */
    private suspend fun loadLorebookContext(
        data: GroupChatData,
        speaker: GroupChatMemberData
    ): GroupLorebookContext {
        // 提取会话勾选的条目 ID
        val selectedEntryIds = mGroupChatRepository
            .getSessionLorebookEntryIds(data.session)
            .toSet()
        // 根据角色卡模式确定参与世界书匹配的成员集合
        val cardMembers = if (
            data.session.characterCardMode == GroupChatSession.CharacterCardMode.Join
        ) {
            data.members.filter {
                data.session.includeMutedCards || !it.relation.muted
            }
        } else {
            listOf(speaker)
        }
        val characterLorebookIds = cardMembers
            .map { it.character.characterLorebookId }
            .filter { it > 0L }
            .toSet()
        // 批量读取全部世界书及条目，再过滤当前会话需要的资源。
        val lorebooksWithEntries = mLorebookRepository.getAllLorebooksWithEntries()
        val lorebooks = lorebooksWithEntries.map { it.lorebook }
        val allEntries = lorebooksWithEntries.flatMap { it.entries }
        val entries = allEntries.filter {
            it.id in selectedEntryIds || it.lorebookId in characterLorebookIds
        }
        val activeLorebookIds = entries.map { it.lorebookId }.toSet()
        val activeLorebooks = lorebooks
            .filter { it.id in activeLorebookIds }
            .associateBy { it.id }
        // 组装返回上下文
        return GroupLorebookContext(
            entries = entries,
            lorebooks = activeLorebooks,
            recursiveLorebookIds = activeLorebooks.values
                .filter { it.recursiveScanning }
                .map { it.id }
                .toSet()
        )
    }

    /**
     * 辅助刷新群聊 UI 状态函数，自动从当前状态获取默认值并从数据层重载最新状态。
     */
    private suspend fun refreshState(
        page: GroupChatPage =
            getOrNull<GroupChatUiState.Normal>()?.page ?: GroupChatPage.Conversation,
        inputDraft: String =
            getOrNull<GroupChatUiState.Normal>()?.conversationState?.inputDraft.orEmpty(),
        selectedSpeakerId: Long? =
            getOrNull<GroupChatUiState.Normal>()?.conversationState?.selectedSpeakerId,
        lorebookQuery: String =
            getOrNull<GroupChatUiState.Normal>()?.settingsState?.lorebookQuery.orEmpty(),
        generationState: GroupChatGenerationState =
            getOrNull<GroupChatUiState.Normal>()?.conversationState?.generationState
                ?: GroupChatGenerationState.Idle,
        expandedThinkBlockIds: Set<String> =
            getOrNull<GroupChatUiState.Normal>()?.conversationState?.expandedThinkBlockIds
                ?: emptySet(),
        editingMessageId: Long? =
            getOrNull<GroupChatUiState.Normal>()?.conversationState?.editingMessageId,
        editingMessageDraft: String =
            getOrNull<GroupChatUiState.Normal>()?.conversationState?.editingMessageDraft.orEmpty(),
        dialogState: GroupChatDialogState =
            getOrNull<GroupChatUiState.Normal>()?.dialogState ?: GroupChatDialogState.None,
        messageLimit: Int = getOrNull<GroupChatUiState.Normal>()
            ?.conversationState?.messages?.size
            ?.coerceAtLeast(MESSAGE_PAGE_SIZE)
            ?: MESSAGE_PAGE_SIZE
    ) {
        val sessionId = mSessionId ?: return
        val next = withContext(Dispatchers.IO) {
            loadState(
                sessionId = sessionId,
                page = page,
                inputDraft = inputDraft,
                selectedSpeakerId = selectedSpeakerId,
                lorebookQuery = lorebookQuery,
                generationState = generationState,
                expandedThinkBlockIds = expandedThinkBlockIds,
                editingMessageId = editingMessageId,
                editingMessageDraft = editingMessageDraft,
                dialogState = dialogState,
                messageLimit = messageLimit
            )
        } ?: return
        mPendingMemberOrder = null
        next.setup()
    }

    /**
     * 从持久化数据层加载并构建完整的群聊页面 [GroupChatUiState.Normal] 状态。
     *
     * 关键组装逻辑：
     * - 成员与可用角色列表映射；
     * - 有效发言人计算：依据当前激活策略计算选中的发言人 ID；
     * - 消息列表转换与 Display 正则渲染；
     * - 设置页草稿与世界书条目列表构建。
     *
     * @param sessionId 群聊会话 ID
     * @param messageLimit 从会话末尾保留的消息窗口大小
     * @return 组装好的 [GroupChatUiState.Normal]，若群聊不存在返回 null
     */
    private suspend fun loadState(
        sessionId: Long,
        page: GroupChatPage = GroupChatPage.Conversation,
        inputDraft: String = "",
        selectedSpeakerId: Long? = null,
        lorebookQuery: String = "",
        generationState: GroupChatGenerationState = GroupChatGenerationState.Idle,
        expandedThinkBlockIds: Set<String> = emptySet(),
        editingMessageId: Long? = null,
        editingMessageDraft: String = "",
        dialogState: GroupChatDialogState = GroupChatDialogState.None,
        messageLimit: Int = MESSAGE_PAGE_SIZE
    ): GroupChatUiState.Normal? {
        // 查询群聊会话、成员与最近消息窗口
        val pageData = mGroupChatRepository.getGroupChatPageData(
            sessionId = sessionId,
            pageSize = messageLimit
        ) ?: return null
        val data = pageData.data
        mMessageDisplayContext = data.copy(messages = emptyList())
        mOldestLoadedMessageCursor = data.messages.firstOrNull()?.toGroupChatMessageCursor()
        val members = data.members.map {
            GroupChatMemberItem(
                id = it.character.id,
                name = it.character.name,
                description = resolveCharacterUserMacros(
                    template = it.character.description,
                    characterName = it.character.name,
                    userName = data.session.userName
                ),
                muted = it.relation.muted
            )
        }
        // 计算当前激活策略下的有效选中发言人
        val validSelectedSpeakerId = selectedSpeakerId
            ?.takeIf { id -> members.any { it.id == id && !it.muted } }
        val effectiveSpeakerId = if (
            data.session.activationStrategy ==
            GroupChatSession.ActivationStrategy.Manual
        ) {
            validSelectedSpeakerId
        } else {
            validSelectedSpeakerId ?: members.firstOrNull { !it.muted }?.id
        }
        // 组装可用添加角色列表
        val memberIds = members.map { it.id }.toSet()
        val availableCharacters = mCharacterRepository.getAllCharacters().map {
            GroupChatAvailableCharacterItem(
                id = it.id,
                name = it.name,
                alreadyMember = it.id in memberIds
            )
        }
        // 组装世界书分组与条目启用状态
        val enabledEntryIds = mGroupChatRepository
            .getSessionLorebookEntryIds(data.session)
            .toSet()
        val lorebooksWithEntries = mLorebookRepository.getAllLorebooksWithEntries()
        val lorebookGroups = lorebooksWithEntries.map { lorebookWithEntries ->
            val lorebook = lorebookWithEntries.lorebook
            GroupChatLorebookGroupItem(
                lorebookId = lorebook.id,
                lorebookName = lorebook.name,
                entries = lorebookWithEntries.entries.map {
                    GroupChatLorebookEntryItem(
                        id = it.id,
                        lorebookId = lorebook.id,
                        lorebookName = lorebook.name,
                        name = it.name,
                        content = it.content,
                        keywords = it.getKeywordList(),
                        secondaryKeywords = it.getSecondaryKeywordList(),
                        constant = it.constant,
                        order = it.order,
                        depth = it.depth,
                        enabled = it.id in enabledEntryIds
                    )
                }
            )
        }.filter { it.entries.isNotEmpty() }
        // 检查全局或关联成员是否存在可用的模型服务配置
        val hasAvailableProvider = mLLMRepository.getSelectedProvider() != null || data.members.any {
            mProviderSelectionResolver.getCharacterProviderOrNull(it.character) != null
        }
        // 构建并返回群聊完整 UI 状态
        return GroupChatUiState.Normal(
            sessionId = sessionId,
            title = data.session.title,
            members = members,
            activeActivationStrategy = data.session.activationStrategy
                .toGroupChatActivationStrategy(),
            page = page,
            conversationState = GroupChatConversationState(
                messages = data.toMessageItems(),
                canLoadOlderMessages = pageData.canLoadOlderMessages,
                selectedSpeakerId = effectiveSpeakerId,
                inputDraft = inputDraft,
                generationState = generationState,
                expandedThinkBlockIds = expandedThinkBlockIds,
                editingMessageId = editingMessageId,
                editingMessageDraft = editingMessageDraft
            ),
            settingsState = GroupChatSettingsState(
                activationStrategy = data.session.activationStrategy
                    .toGroupChatActivationStrategy(),
                characterCardMode = data.session.characterCardMode
                    .toGroupChatCharacterCardMode(),
                allowSelfResponses = data.session.allowSelfResponses,
                includeMutedCards = data.session.includeMutedCards,
                autoModeEnabled = data.session.autoModeEnabled,
                trimOtherSpeakers = data.session.trimOtherSpeakers,
                scenarioDraft = data.session.scenario,
                userNoteDraft = data.session.userNote,
                summaryDraft = data.summary?.content.orEmpty(),
                autoSummaryPaused = data.session.autoSummaryPaused,
                systemPromptDraft = data.session.systemPromptOverride,
                groupNudgePromptDraft = data.session.groupNudgePromptOverride,
                newGroupChatPromptDraft = data.session.newGroupChatPromptOverride,
                titleDraft = data.session.title,
                availableCharacters = availableCharacters,
                lorebookGroups = lorebookGroups,
                visibleLorebookGroups = lorebookGroups.filterForQuery(lorebookQuery),
                lorebookQuery = lorebookQuery
            ),
            hasPromptInspection = mLastPromptInspection != null,
            hasAvailableProvider = hasAvailableProvider,
            dialogState = dialogState
        )
    }

    /**
     * 记录 Prompt 检查详情，并在发生世界书预算超限或上下文裁剪时弹出 Toast 告警。
     *
     * @param inspection Prompt 检查报告
     */
    private fun recordPromptInspection(inspection: PromptInspection) {
        mLastPromptInspection = inspection
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        uiState.copy(hasPromptInspection = true).setup()
        val hasWorldInfoOverflow = inspection.omittedItems.any {
            it.reason == PromptOmissionReason.WorldInfoBudget
        }
        val hasContextTrimming = inspection.omittedItems.any {
            it.reason == PromptOmissionReason.ContextBudget
        }
        when {
            AppModel.worldInfoOverflowAlert && hasWorldInfoOverflow -> {
                AppViewEvent.PopupToastMessageByResId(
                    R.string.world_info_budget_overflow_warning
                ).tryEmit()
            }
            AppModel.contextTrimmingAlert && hasContextTrimming -> {
                AppViewEvent.PopupToastMessageByResId(R.string.prompt_trimmed_warning).tryEmit()
            }
        }
    }

    /**
     * 将数据库群聊消息转换为 UI 渲染模型，并执行 Display 阶段正则。
     *
     * @receiver 群聊聚合数据快照
     * @param newerMessageCount 当前片段之后已经加载的消息数量
     * @return 转换并正则渲染后的 [GroupChatMessageItem] 列表
     */
    private suspend fun GroupChatData.toMessageItems(
        newerMessageCount: Int = 0
    ): List<GroupChatMessageItem> {
        val scripts = mRegexRepository.activeScripts(members.map { it.character })
        // 遍历消息并执行针对该消息发言角色的 Display 阶段正则
        return messages.mapIndexed { index, message ->
            val characterName = if (message.source == GroupChatMessage.Source.Character) {
                message.speakerNameSnapshot
            } else {
                members.firstOrNull()?.character?.name.orEmpty()
            }
            val macros = groupRegexMacros(this, characterName)
            val depth = newerMessageCount + messages.lastIndex - index
            val displayContent = when (message.source) {
                GroupChatMessage.Source.User -> mRegexProcessor.applyDisplay(
                    input = message.content,
                    source = RegexMessageSource.User,
                    scripts = scripts,
                    macros = macros,
                    depth = depth
                )
                GroupChatMessage.Source.Character -> mRegexProcessor.applyDisplay(
                    input = message.content,
                    source = RegexMessageSource.Character,
                    scripts = scripts,
                    macros = macros,
                    depth = depth
                )
                GroupChatMessage.Source.System -> message.content
            }
            GroupChatMessageItem(
                id = message.id,
                source = message.source.toGroupChatMessageSource(),
                speakerName = message.speakerNameSnapshot,
                content = displayContent,
                parts = displayContent.toMessageContentParts(message.id.toString()),
                time = message.createTime.formatTimestamp("HH:mm"),
                isStreaming = message.id == mStreamingMessageId
            )
        }
    }

    /**
     * 对群聊用户输入文本执行 Source 阶段正则替换。
     *
     * @param data 群聊数据快照
     * @param input 用户原始输入
     * @param isEdit 是否为编辑已有消息
     * @return 正则替换后文本
     */
    private suspend fun applyUserRegex(
        data: GroupChatData,
        input: String,
        isEdit: Boolean = false
    ): String {
        val scripts = mRegexRepository.activeScripts(data.members.map { it.character })
        val macros = groupRegexMacros(
            data,
            data.members.firstOrNull()?.character?.name.orEmpty()
        )
        return mRegexProcessor.applyUserInput(input, scripts, macros, isEdit)
    }

    /**
     * 对群聊 AI 角色生成文本执行 Source 阶段正则替换。
     *
     * @param data 群聊数据快照
     * @param input AI 原始生成文本
     * @param characterName 发言角色名称
     * @param isEdit 是否为编辑已有消息
     * @return 正则替换后文本
     */
    private suspend fun applyAiRegex(
        data: GroupChatData,
        input: String,
        characterName: String,
        isEdit: Boolean = false
    ): String {
        return mRegexProcessor.applyAiResponse(
            input,
            mRegexRepository.activeScripts(data.members.map { it.character }),
            groupRegexMacros(data, characterName),
            isEdit = isEdit
        )
    }

    /**
     * 对流式生成的累计文本执行最终 Source 正则替换，防止重复执行。
     */
    private fun applyStreamingAiRegex() {
        if (mStreamingRegexApplied || mStreamingContent.isBlank()) return
        val processed = mRegexProcessor.applyAiResponse(
            input = mStreamingContent,
            scripts = mStreamingRegexScripts,
            macros = mStreamingRegexMacros
        )
        mStreamingContent = processed
        mStreamingRegexApplied = true
    }

    /**
     * 生成群聊专用的正则宏字典。
     *
     * 注入变量包括：`{{user}}`, `{{char}}`, `{{description}}`, `{{scenario}}`, `{{group}}`（所有群成员名称列表）。
     *
     * @param data 群聊聚合数据
     * @param characterName 当前角色的名称
     * @return 宏映射 Map
     */
    private fun groupRegexMacros(
        data: GroupChatData,
        characterName: String
    ): Map<String, String> {
        return RegexScriptRuntime.macros(
            userName = data.session.userName,
            characterName = characterName,
            userDescription = data.session.userDescription,
            scenario = data.session.scenario,
            groupNames = data.members.joinToString(", ") { it.character.name }
        )
    }

    /**
     * 弹出 Toast 提示并退出当前群聊页面。
     *
     * @param messageResId 字符串资源 ID
     */
    private fun finishWithToast(messageResId: Int) {
        AppViewEvent.PopupToastMessageByResId(messageResId).tryEmit()
        GroupChatUiState.finished(uiStateFlow.value).setup()
    }

    /** 将持久化群聊消息转换为向前分页使用的稳定游标。 */
    private fun GroupChatMessage.toGroupChatMessageCursor(): GroupChatMessageCursor {
        return GroupChatMessageCursor(createTime = createTime, messageId = id)
    }

    /**
     * 仅在设置页更新表单草稿，保持页面状态边界明确。
     *
     * @param transform 表单草稿转换逻辑
     */
    private fun updateSettingsState(
        transform: GroupChatSettingsState.() -> GroupChatSettingsState
    ) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        if (uiState.page != GroupChatPage.Settings) return
        uiState.withSettingsDraft(transform).setup()
    }

    /**
     * 依据搜索词过滤世界书分组及条目列表。
     *
     * @param query 搜索关键词
     * @return 过滤后的世界书分组列表
     */
    private fun List<GroupChatLorebookGroupItem>.filterForQuery(
        query: String
    ): List<GroupChatLorebookGroupItem> = filterLorebookGroups(
        query = query,
        groupName = { it.lorebookName },
        entries = { it.entries },
        entrySearchFields = { entry ->
            sequenceOf(entry.lorebookName, entry.name, entry.content) +
                entry.keywords.asSequence() +
                entry.secondaryKeywords.asSequence()
        },
        copyWithEntries = { group, entries -> group.copy(entries = entries) }
    )

    /**
     * 群聊世界书上下文数据模型。
     *
     * @property entries 候选世界书条目列表
     * @property lorebooks 关联的世界书实体映射
     * @property recursiveLorebookIds 开启了递归扫描的世界书 ID 集合
     */
    private data class GroupLorebookContext(
        /** 当前分组、请求或结果包含的条目列表。 */
        val entries: List<me.kafuuneko.rpclient.libs.room.entity.LorebookEntry>,
        /** 当前页面或流程可使用的世界书列表。 */
        val lorebooks: Map<Long, me.kafuuneko.rpclient.libs.room.entity.Lorebook>,
        /** 本轮实际参与递归扫描的世界书 ID 集合。 */
        val recursiveLorebookIds: Set<Long>
    )

    /** 一次成员拖动的原始顺序与最终顺序快照。 */
    private data class PendingMemberOrder(
        /** 应用排序或筛选前的角色 ID 顺序。 */
        val originalCharacterIds: List<Long>,
        /** 应用业务规则后得到的角色 ID 顺序。 */
        val orderedCharacterIds: List<Long>
    )

    /** 群聊消息由创建时间与 ID 组成的稳定分页游标。 */
    private data class GroupChatMessageCursor(
        val createTime: Long,
        val messageId: Long
    )

    /** 已完成展示转换、可直接合并进 UiState 的一页群聊消息。 */
    private data class LoadedGroupChatMessagePage(
        val items: List<GroupChatMessageItem>,
        val cursor: GroupChatMessageCursor?,
        val canLoadOlderMessages: Boolean
    )

    private companion object {
        /** 自动群聊模式下，两轮生成之间的短暂缓冲延时（毫秒）。 */
        const val AUTO_MODE_DELAY_MS = 500L
        /** 群聊页面首次和后续向前加载的单页消息数量。 */
        const val MESSAGE_PAGE_SIZE = 50
    }
}

/** 将公共模型配置引导转换为群聊页面的对话框状态。 */
private fun ModelSettingsGuideContent.toGroupChatDialogState(): GroupChatDialogState.ModelSettingsGuide {
    return GroupChatDialogState.ModelSettingsGuide(title = title, message = message)
}
