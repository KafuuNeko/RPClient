package me.kafuuneko.rpclient.feature.chat

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.ModelSettingsGuideContent
import me.kafuuneko.rpclient.feature.noProviderModelSettingsGuide
import me.kafuuneko.rpclient.feature.toGenerationFailurePresentation
import me.kafuuneko.rpclient.feature.chat.model.ChatGenerationState
import me.kafuuneko.rpclient.feature.chat.model.ChatLorebookGroupItem
import me.kafuuneko.rpclient.feature.chat.model.ChatMessageUiModel
import me.kafuuneko.rpclient.feature.chat.presentation.ChatDialogState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatConversationState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLorebookState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLoadState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatPage
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiIntent
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatViewEvent
import me.kafuuneko.rpclient.feature.chat.presentation.resolveExportDialogState
import me.kafuuneko.rpclient.feature.chat.utils.ChatLorebookEntryData
import me.kafuuneko.rpclient.feature.chat.utils.replaceStreamingMessage
import me.kafuuneko.rpclient.feature.chat.utils.toChatCharacterItem
import me.kafuuneko.rpclient.feature.chat.utils.toChatLorebookGroupItems
import me.kafuuneko.rpclient.feature.chat.utils.toChatMessageItems
import me.kafuuneko.rpclient.feature.chat.utils.toChatSessionItem
import me.kafuuneko.rpclient.feature.characteredit.CharacterEditActivity
import me.kafuuneko.rpclient.feature.llmproviderlist.LLMProviderListActivity
import me.kafuuneko.rpclient.feature.worldbooklist.WorldBookListActivity
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.chat.ChatArchiveRepository
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.defaults.normalizedUserName
import me.kafuuneko.rpclient.libs.llm.LLMProviderSelectionResolver
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.prompt.ChatPromptBuilder
import me.kafuuneko.rpclient.libs.prompt.model.PromptBuildContext
import me.kafuuneko.rpclient.libs.prompt.model.PromptGenerationMode
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspection
import me.kafuuneko.rpclient.libs.prompt.model.PromptOmissionReason
import me.kafuuneko.rpclient.libs.prompt.SummaryPromptBuilder
import me.kafuuneko.rpclient.libs.prompt.summarySafeContent
import me.kafuuneko.rpclient.libs.regex.RegexMessageProcessor
import me.kafuuneko.rpclient.libs.regex.RegexMessageSource
import me.kafuuneko.rpclient.libs.regex.RegexScriptRepository
import me.kafuuneko.rpclient.libs.regex.RegexScriptRuntime
import me.kafuuneko.rpclient.libs.regex.ScopedRegexScript
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.utils.formatTimestamp
import me.kafuuneko.rpclient.utils.filterLorebookGroups
import me.kafuuneko.rpclient.utils.toggle
import me.kafuuneko.rpclient.utils.toggleAll
import me.kafuuneko.rpclient.utils.toDefaultChatTitle
import me.kafuuneko.rpclient.utils.toMessageCopyText
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 单角色聊天页面的 ViewModel（状态持有者与业务控制器）。
 *
 * 核心职责：
 * - 会话生命周期管理：初始化加载、页面恢复（Resume）、返回拦截（导出保护/取消生成）。
 * - 消息流转与持久化：发送、重生成、续写、模仿用户、单条编辑、消息与会话删除、会话分叉（Branch）。
 * - 大模型调用与流式控制：Prompt 构建、Token 预算裁剪监测、流式增量接收与 UI 实时渲染、NonCancellable 安全落库。
 * - 正则脚本双阶段处理：持久化前的 Source 正则与渲染时的 Display 正则分离。
 * - 世界书（Lorebook）激活与管理：条目开关、搜索过滤、递归扫描世界书计算。
 * - 会话摘要（Summary）管理：手动触发、后台自动分段总结、摘要回滚。
 * - 对话归档导出：导出为 JSONL 文件。
 */
class ChatViewModel : CoreViewModelWithEvent<ChatUiIntent, ChatUiState>(
    ChatUiState.None
), KoinComponent {
    // 数据仓库与领域服务注入
    private val mChatRepository by inject<ChatRepository>()
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mLLMRepository by inject<LLMRepository>()
    private val mProviderSelectionResolver by inject<LLMProviderSelectionResolver>()
    private val mFileRepository by inject<FileRepository>()
    private val mChatPromptBuilder by inject<ChatPromptBuilder>()
    private val mSummaryPromptBuilder by inject<SummaryPromptBuilder>()
    private val mRegexRepository by inject<RegexScriptRepository>()
    private val mRegexProcessor by inject<RegexMessageProcessor>()
    private val mChatArchiveRepository by inject<ChatArchiveRepository>()
    private val mContext by inject<Context>()

    /** 当前页面绑定的会话 ID，初始化成功后在页面生命周期内保持不变。 */
    private var mSessionId: Long? = null
    /** 当前大模型生成任务协程 Job，用于防止并发重复请求及响应用户的停止生成操作。 */
    private var mGenerationJob: Job? = null
    /** 后台自动总结任务协程 Job，与主正文生成分开调度、取消与收尾。 */
    private var mSummaryJob: Job? = null
    /** 用户主动触发的对话归档导出任务 Job；运行期间阻止页面退出以防写入不完整文件。 */
    private var mChatExportJob: Job? = null
    /** 用于互斥串行化摘要任务的启动、替换与取消，防止并发总结导致 UI 状态混乱。 */
    private val mSummaryJobMutex = Mutex()
    /** 仅暴露当前流式生成的快照供 UI 刷新读取；生成协程本身是唯一的写入者和最终收尾提交者。 */
    private var mActiveStreamingGeneration: ActiveStreamingGeneration? = null
    /** 最近一次实际发送给模型请求的 Prompt 检查报告，供调试及 Prompt 检查器对话框读取。 */
    private var mLastPromptInspection: PromptInspection? = null
    /** 当前消息窗口最早记录的稳定分页游标。 */
    private var mOldestLoadedMessageCursor: ChatMessageCursor? = null
    /** 分页消息执行 Display Regex 与 UI 映射所需的会话快照。 */
    private var mMessageDisplayContext: ChatMessageDisplayContext? = null

    /**
     * 初始化会话数据。
     *
     * 处理流程：
     * - 校验传入的会话 ID 有效性，无效时弹出 Toast 并结束页面。
     * - 从数据库加载会话基础信息、角色人设、历史消息及世界书列表。
     * - 成功后将 UI 状态由 [ChatUiState.None] 转换为 [ChatUiState.Normal]。
     *
     * @param intent 包含 sessionId 的初始化意图
     */
    @UiIntentObserver(ChatUiIntent.Init::class)
    private suspend fun onInit(intent: ChatUiIntent.Init) {
        if (!isStateOf<ChatUiState.None>()) return
        val sessionId = intent.sessionId?.toLongOrNull()
        if (sessionId == null) {
            finishWithToast(R.string.invalid_session_id)
            return
        }
        mSessionId = sessionId
        val loaded = withContext(Dispatchers.IO) { loadNormalState(sessionId) }
        if (loaded == null) {
            finishWithToast(R.string.session_not_found)
            return
        }
        loaded.setup()
    }

    /**
     * 页面从后台恢复或重新可见时的刷新处理。
     *
     * 保持当前的输入草稿、当前子页面（对话/设置）、对话框、生成中状态、
     * 正在编辑的消息草稿及已展开的思考块状态，重新从数据库载入最新数据并刷新。
     */
    @UiIntentObserver(ChatUiIntent.Resume::class)
    private suspend fun onResume() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        // 异步从数据库重新加载最新的正常页面状态（保留当前页面的草稿与展开状态）
        val refreshed = withContext(Dispatchers.IO) {
            loadNormalState(
                sessionId = sessionId,
                inputDraft = uiState.conversationState.inputDraft,
                page = uiState.page,
                loadState = uiState.loadState,
                generationState = uiState.conversationState.generationState,
                expandedThinkBlockIds = uiState.conversationState.expandedThinkBlockIds,
                editingMessageId = uiState.conversationState.editingMessageId,
                editingMessageDraft = uiState.conversationState.editingMessageDraft,
                dialogState = uiState.dialogState,
                messageLimit = uiState.conversationState.messages.size.coerceAtLeast(
                    MESSAGE_PAGE_SIZE
                )
            )
        }
        // 若会话已被删除，则取消任务并结束页面
        if (refreshed == null) {
            mGenerationJob?.cancel()
            ChatUiState.finished(uiStateFlow.value).setup()
            return
        }
        // 结合当前导出任务状态更新弹窗状态并刷新 UI
        refreshed.copy(
            dialogState = refreshed.dialogState.resolveExportDialogState(
                isExportActive = mChatExportJob?.isActive == true
            )
        ).setup()
    }

    /**
     * 用户滚动到当前消息窗口顶部时向前加载一页历史消息。
     *
     * - 使用创建时间和消息 ID 组成的稳定游标，避免同时间消息跨页重复。
     * - 新页面只执行自身消息的 Display Regex 与展示模型转换。
     * - 合并时以最新 UiState 为准，避免覆盖流式内容等并发内存更新。
     */
    @UiIntentObserver(ChatUiIntent.LoadOlderMessages::class)
    private suspend fun onLoadOlderMessages() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        val cursor = mOldestLoadedMessageCursor ?: return
        val displayContext = mMessageDisplayContext ?: return
        if (!uiState.conversationState.canLoadOlderMessages ||
            uiState.conversationState.isLoadingOlderMessages
        ) return

        // 先发布加载标记，拦截顶部滚动在同一页内重复触发
        uiState.copy(
            conversationState = uiState.conversationState.copy(
                isLoadingOlderMessages = true
            )
        ).setup()
        val loadedPage = try {
            withContext(Dispatchers.IO) {
                val page = mChatRepository.getMessagePageBefore(
                    sessionId = sessionId,
                    beforeCreateTime = cursor.createTime,
                    beforeMessageId = cursor.messageId,
                    pageSize = MESSAGE_PAGE_SIZE
                )
                LoadedChatMessagePage(
                    items = page.messages.toDisplayMessageItems(
                        context = displayContext,
                        newerMessageCount = uiState.conversationState.messages.size
                    ),
                    cursor = page.messages.firstOrNull()?.toChatMessageCursor(),
                    canLoadOlderMessages = page.canLoadOlderMessages,
                    totalMessageCount = page.totalMessageCount
                )
            }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            val currentState = getOrNull<ChatUiState.Normal>() ?: return
            currentState.copy(
                conversationState = currentState.conversationState.copy(
                    isLoadingOlderMessages = false
                )
            ).setup()
            return
        }

        // 保留加载期间可能更新的尾部消息，并按 ID 防御性去重
        val currentState = getOrNull<ChatUiState.Normal>() ?: return
        val existingIds = currentState.conversationState.messages.mapTo(mutableSetOf()) { it.id }
        val olderItems = loadedPage.items.filterNot { it.id in existingIds }
        mOldestLoadedMessageCursor = loadedPage.cursor ?: cursor
        currentState.copy(
            session = currentState.session.copy(messageCount = loadedPage.totalMessageCount),
            conversationState = currentState.conversationState.copy(
                messages = olderItems + currentState.conversationState.messages,
                canLoadOlderMessages = loadedPage.canLoadOlderMessages,
                isLoadingOlderMessages = false
            )
        ).setup()
    }

    /**
     * 处理返回键事件。
     *
     * 拦截与响应逻辑：
     * - 若当前正在导出聊天记录，拦截退出并提示正在导出。
     * - 若当前处于设置页（[ChatPage.Settings]），则切回对话页（[ChatPage.Conversation]）。
     * - 若正在生成，安全取消生成协程并等待其 NonCancellable 收尾完成后退出页面。
     */
    @UiIntentObserver(ChatUiIntent.Back::class)
    private suspend fun onBack() {
        if (mChatExportJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.chat_export_in_progress).tryEmit()
            return
        }
        val uiState = getOrNull<ChatUiState.Normal>()
        if (uiState?.page == ChatPage.Settings) {
            uiState.copy(page = ChatPage.Conversation).setup()
            return
        }
        cancelActiveGeneration()
        ChatUiState.finished(uiStateFlow.value).setup()
    }

    /**
     * 更新对话输入框中的草稿文本。
     *
     * @param intent 包含最新输入文本的意图
     */
    @UiIntentObserver(ChatUiIntent.ChangeInputDraft::class)
    private suspend fun onChangeInputDraft(intent: ChatUiIntent.ChangeInputDraft) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(
            conversationState = uiState.conversationState.copy(inputDraft = intent.value)
        ).setup()
    }

    /**
     * 更新世界书面板的搜索查询词，并动态过滤匹配的世界书分组与条目列表。
     *
     * @param intent 包含搜索关键字的意图
     */
    @UiIntentObserver(ChatUiIntent.ChangeLorebookQuery::class)
    private fun onChangeLorebookQuery(intent: ChatUiIntent.ChangeLorebookQuery) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(
            lorebookState = uiState.lorebookState.copy(
                query = intent.value,
                visibleGroups = uiState.lorebookState.groups.filterForQuery(intent.value)
            )
        ).setup()
    }

    /**
     * 发送用户消息并触发角色回复生成。
     *
     * 核心业务流程：
     * - 检查输入框：若为空则尝试使用全局配置的空消息替换词；若仍为空则视为“继续/续写上一轮”。
     * - 防止并发：若已有生成任务在运行则拒绝请求并 Toast 提示。
     * - 正则处理：对用户原始输入执行 UserInput 阶段的 Source 正则替换。
     * - 消息入库：以 User 来源将消息持久化至数据库。
     * - 构建 Prompt：收集人设、世界书、摘要、最新上下文等组装模型请求，并记录调试检查报告。
     * - 调用模型：根据全局开关决定采用流式（[generateStreaming]）或非流式（[generateOnce]）生成。
     * - 自动总结：生成完成后检测未总结消息量，达到阈值时自动触发增量总结。
     */
    @UiIntentObserver(ChatUiIntent.SendMessage::class)
    private suspend fun onSendMessage() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        if (!ensureProviderConfigured(sessionId, uiState.character.id)) return
        val rawInput = uiState.conversationState.inputDraft.trim()
            .ifBlank { AppModel.replaceEmptyMessagePrompt.trim() }
        // 若最终输入为空，退化为续写角色消息
        if (rawInput.isBlank()) {
            continueLastAssistantMessage(sessionId)
            return
        }
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.generation_already_running).tryEmit()
            return
        }

        // 发送流程不使用 CoreViewModel 的状态回滚式任务队列，因为流式停止时需要保留 partial 内容。
        mGenerationJob = viewModelScope.launch {
            runCatching {
                // 执行用户输入端 Source 正则
                val input = withContext(Dispatchers.IO) {
                    applyUserRegex(sessionId, rawInput)
                }
                // 将用户消息写入数据库
                withContext(Dispatchers.IO) {
                    mChatRepository.createMessage(sessionId, ChatMessage.Source.User, input)
                }
                // 清空草稿并将 UI 切换至“请求中”状态
                refreshUiState(
                    sessionId = sessionId,
                    inputDraft = "",
                    generationState = ChatGenerationState.Requesting
                )
                // 构建 Prompt 请求并记录检查项
                val built = withContext(Dispatchers.IO) { buildGenerationRequest(sessionId) }
                recordPromptInspection(built.inspection)
                // 分发调用大模型生成角色回复
                if (AppModel.streamEnabled) {
                    generateStreaming(
                        sessionId,
                        built.provider,
                        built.request,
                        GenerationOutput.Create(ChatMessage.Source.Char),
                        built.worldInfoStateJson
                    )
                } else {
                    generateOnce(
                        sessionId,
                        built.provider,
                        built.request,
                        GenerationOutput.Create(ChatMessage.Source.Char),
                        built.worldInfoStateJson
                    )
                }
                // 检查并按需触发自动总结
                maybeAutoSummarize(sessionId)
            }.onFailure { throwable ->
                // 异常处理：解析错误信息并更新 UI 失败状态
                val failure = throwable.toGenerationFailurePresentation(
                    mContext,
                    R.string.generation_failed
                ) ?: return@onFailure
                val guideDialog = failure.modelSettingsGuide?.toChatDialogState()
                refreshUiState(
                    sessionId = sessionId,
                    inputDraft = "",
                    generationState = ChatGenerationState.Failed(failure.message),
                    dialogState = guideDialog ?: ChatDialogState.None
                )
                if (guideDialog == null) {
                    AppViewEvent.PopupToastMessage(failure.message).tryEmit()
                }
            }
        }
    }

    /**
     * 响应用户点击“停止生成”按钮。
     *
     * 取消当前的生成协程，等待其 NonCancellable 收尾块完成部分内容落库，并将 UI 恢复至 Idle 状态。
     */
    @UiIntentObserver(ChatUiIntent.StopGeneration::class)
    private suspend fun onStopGeneration() {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        if (!cancelActiveGeneration()) return
        refreshUiState(sessionId = sessionId, generationState = ChatGenerationState.Idle)
    }

    /**
     * 取消当前模型生成任务并等待其安全收尾。
     *
     * 使用 [Job.cancelAndJoin] 确保生成协程在其 finally 块（包含 NonCancellable）
     * 中完成唯一一次原子持久化提交后，本方法才返回。
     *
     * @return 若有活跃任务被取消返回 true，否则返回 false
     */
    private suspend fun cancelActiveGeneration(): Boolean {
        val job = mGenerationJob ?: return false
        if (!job.isActive) return false
        job.cancelAndJoin()
        return true
    }

    /**
     * 重新生成最后一条角色回复。
     */
    @UiIntentObserver(ChatUiIntent.RegenerateLast::class)
    private suspend fun onRegenerateLast() {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        regenerateLastAssistantMessage(sessionId)
    }

    /**
     * 继续生成（续写）最后一轮对话。
     */
    @UiIntentObserver(ChatUiIntent.ContinueLast::class)
    private suspend fun onContinueLast() {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        continueLastAssistantMessage(sessionId)
    }

    /**
     * 触发“模仿用户发言（Impersonate）”。
     *
     * 让模型以用户的第一人称口吻生成下一条消息，并以 [ChatMessage.Source.User] 保存。
     */
    @UiIntentObserver(ChatUiIntent.ImpersonateUser::class)
    private suspend fun onImpersonateUser() {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        generateUserImpersonation(sessionId)
    }

    /**
     * 从指定消息处触发重生成。
     *
     * 目前仅支持重新生成最新的一条角色回复；若点击的不是最后一条角色消息，将提示用户。
     *
     * @param intent 包含目标消息 ID 的意图
     */
    @UiIntentObserver(ChatUiIntent.RegenerateFromMessage::class)
    private suspend fun onRegenerateFromMessage(intent: ChatUiIntent.RegenerateFromMessage) {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        val messageId = intent.messageId.toLongOrNull() ?: return
        val latestAssistantMessage = withContext(Dispatchers.IO) {
            mChatRepository.getLatestCharacterMessageBySessionId(sessionId)
        }
        if (latestAssistantMessage?.id != messageId) {
            AppViewEvent.PopupToastMessageByResId(R.string.only_latest_assistant_reply_regenerate).tryEmit()
            return
        }
        regenerateLastAssistantMessage(sessionId)
    }

    /**
     * 从指定历史消息处创建独立的分支会话（Branching）。
     *
     * 业务流程：
     * - 拦截并发：生成中禁止分叉。
     * - 在数据库事务中截取截至该消息的历史记录，复制到全新会话中，并保留该边界处有效的摘要。
     * - 当前会话状态保持不变，通过 [ChatViewEvent.OpenSession] 一次性事件导航打开新会话。
     *
     * @param intent 包含分叉截断消息 ID 的意图
     */
    @UiIntentObserver(ChatUiIntent.BranchFromMessage::class)
    private suspend fun onBranchFromMessage(intent: ChatUiIntent.BranchFromMessage) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        val messageId = intent.messageId.toLongOrNull() ?: return
        // 拦截并发生成
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.generation_already_running).tryEmit()
            return
        }
        uiState.copy(loadState = ChatLoadState.Saving).setup()
        val branchCreateTime = System.currentTimeMillis()
        // 异步在数据库中创建分支会话
        val branchId = withContext(Dispatchers.IO) {
            mChatRepository.createBranchSession(
                sourceSessionId = sessionId,
                throughMessageId = messageId,
                title = branchCreateTime.toDefaultChatTitle(),
                createTime = branchCreateTime
            )
        }
        // 处理分叉创建失败
        if (branchId == 0L) {
            AppViewEvent.PopupToastMessageByResId(R.string.branch_create_failed).tryEmit()
            refreshUiState(sessionId = sessionId)
            return
        }
        // 导航打开新分支会话
        ChatViewEvent.OpenSession(branchId.toString()).emit()
    }

    /** 打开当前会话的世界书快捷管理对话框。 */
    @UiIntentObserver(ChatUiIntent.ShowSessionLoreDialog::class)
    private fun onShowSessionLoreDialog() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val groups = uiState.lorebookState.groups
        uiState.copy(
            dialogState = ChatDialogState.SessionLorebook(
                query = "",
                visibleGroups = groups,
                enabledEntryIds = uiState.session.enabledLorebookEntryIds
            )
        ).setup()
    }

    /** 更新单聊快捷管理对话框中的世界书搜索词与过滤结果。 */
    @UiIntentObserver(ChatUiIntent.ChangeSessionLorebookDialogQuery::class)
    private fun onChangeSessionLorebookDialogQuery(
        intent: ChatUiIntent.ChangeSessionLorebookDialogQuery
    ) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? ChatDialogState.SessionLorebook ?: return
        uiState.copy(
            dialogState = dialogState.copy(
                query = intent.value,
                visibleGroups = uiState.lorebookState.groups.filterForQuery(intent.value)
            )
        ).setup()
    }

    /** 切换单聊快捷管理对话框草稿中的单个条目。 */
    @UiIntentObserver(ChatUiIntent.ToggleSessionLorebookDialogEntry::class)
    private fun onToggleSessionLorebookDialogEntry(
        intent: ChatUiIntent.ToggleSessionLorebookDialogEntry
    ) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? ChatDialogState.SessionLorebook ?: return
        val entryExists = uiState.lorebookState.groups.any { group ->
            group.entries.any { it.id == intent.entryId }
        }
        if (!entryExists) return
        uiState.copy(
            dialogState = dialogState.copy(
                enabledEntryIds = dialogState.enabledEntryIds.toggle(intent.entryId)
            )
        ).setup()
    }

    /** 切换单聊快捷管理对话框草稿中的整个世界书分组。 */
    @UiIntentObserver(ChatUiIntent.ToggleSessionLorebookDialogGroup::class)
    private fun onToggleSessionLorebookDialogGroup(
        intent: ChatUiIntent.ToggleSessionLorebookDialogGroup
    ) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? ChatDialogState.SessionLorebook ?: return
        // 分组开关始终作用于完整分组，不受当前搜索结果裁剪影响。
        val entryIds = uiState.lorebookState.groups
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

    /**
     * 切换当前会话中单个世界书条目的启用/禁用状态。
     *
     * @param intent 包含条目 ID 的意图
     */
    @UiIntentObserver(ChatUiIntent.ToggleSessionLoreEntry::class)
    private suspend fun onToggleSessionLoreEntry(intent: ChatUiIntent.ToggleSessionLoreEntry) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        if (uiState.lorebookState.groups.none { group ->
                group.entries.any { it.id == intent.entryId }
            }
        ) return
        val enabledIds = uiState.session.enabledLorebookEntryIds.toggle(intent.entryId)
        saveSessionLorebookEntryIds(sessionId, enabledIds)
        refreshUiState(
            sessionId = sessionId,
            inputDraft = uiState.conversationState.inputDraft,
            generationState = uiState.conversationState.generationState
        )
    }

    /**
     * 批量切换指定世界书分组下所有条目的启用/禁用状态。
     *
     * @param intent 包含世界书 ID 的意图
     */
    @UiIntentObserver(ChatUiIntent.ToggleSessionLorebook::class)
    private suspend fun onToggleSessionLorebook(intent: ChatUiIntent.ToggleSessionLorebook) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        val group = uiState.lorebookState.groups
            .firstOrNull { it.lorebookId == intent.lorebookId } ?: return
        val entryIds = group.entries.map { it.id }.toSet()
        if (entryIds.isEmpty()) return
        val enabledIds = uiState.session.enabledLorebookEntryIds.toggleAll(entryIds)
        saveSessionLorebookEntryIds(sessionId, enabledIds)
        refreshUiState(
            sessionId = sessionId,
            inputDraft = uiState.conversationState.inputDraft,
            generationState = uiState.conversationState.generationState
        )
    }

    /**
     * 确认并保存快捷管理对话框中的世界书条目选择。
     */
    @UiIntentObserver(ChatUiIntent.ConfirmSessionLorebookSelection::class)
    private suspend fun onConfirmSessionLorebookSelection() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? ChatDialogState.SessionLorebook ?: return
        val sessionId = mSessionId ?: return
        // 提交前剔除已经被删除的条目 ID，再一次性覆盖会话配置。
        val validEntryIds = uiState.lorebookState.groups
            .flatMap { it.entries }
            .mapTo(mutableSetOf()) { it.id }
        val enabledEntryIds = dialogState.enabledEntryIds.intersect(validEntryIds)
        saveSessionLorebookEntryIds(sessionId, enabledEntryIds)
        refreshUiState(
            sessionId = sessionId,
            inputDraft = uiState.conversationState.inputDraft,
            generationState = uiState.conversationState.generationState,
            dialogState = ChatDialogState.None
        )
    }

    /**
     * 切换至单聊设置页面（[ChatPage.Settings]）。
     */
    @UiIntentObserver(ChatUiIntent.OpenChatSettings::class)
    private fun onOpenChatSettings() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(page = ChatPage.Settings).setup()
    }

    /**
     * 打开 Prompt 检查器对话框，展示最近一次发送给大模型的完整 Prompt 结构与被裁剪项。
     */
    @UiIntentObserver(ChatUiIntent.OpenPromptInspector::class)
    private fun onOpenPromptInspector() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val inspection = mLastPromptInspection
        if (inspection == null) {
            AppViewEvent.PopupToastMessageByResId(R.string.prompt_inspector_unavailable).tryEmit()
            return
        }
        uiState.copy(dialogState = ChatDialogState.PromptInspector(inspection)).setup()
    }

    /**
     * 关闭设置页面，切回对话主页面（[ChatPage.Conversation]）。
     */
    @UiIntentObserver(ChatUiIntent.CloseChatSettings::class)
    private fun onCloseChatSettings() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(page = ChatPage.Conversation).setup()
    }

    /**
     * 点击“导出聊天记录”按钮。
     *
     * 校验前置条件（非生成中、非总结中），生成默认文件名并触发系统的 SAF 文件保存选择器。
     */
    @UiIntentObserver(ChatUiIntent.ExportChatClick::class)
    private fun onExportChatClick() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        // 校验是否正处于忙碌或已在导出中
        if (uiState.loadState != ChatLoadState.None || mChatExportJob?.isActive == true) return
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(
                R.string.stop_generation_before_exporting
            ).tryEmit()
            return
        }
        if (mSummaryJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(
                R.string.wait_for_summary_before_exporting
            ).tryEmit()
            return
        }
        // 格式化默认文件名并调起系统导出器
        val timestamp = System.currentTimeMillis().formatTimestamp("yyyyMMdd_HHmmss")
        ChatViewEvent.OpenChatExporter(fileName = "chat_$timestamp.jsonl").tryEmit()
    }

    /**
     * 处理系统文件选择器返回的导出目标 URI，异步执行聊天记录的归档导出。
     *
     * @param intent 包含用户选择的文件目标 URI 的意图
     */
    @UiIntentObserver(ChatUiIntent.ExportChatResult::class)
    private fun onExportChatResult(intent: ChatUiIntent.ExportChatResult) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        // 校验前置互斥状态
        if (uiState.loadState != ChatLoadState.None || mChatExportJob?.isActive == true) return
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(
                R.string.stop_generation_before_exporting
            ).tryEmit()
            return
        }
        if (mSummaryJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(
                R.string.wait_for_summary_before_exporting
            ).tryEmit()
            return
        }
        // 切换为导出中弹窗状态
        uiState.copy(dialogState = ChatDialogState.Exporting).setup()
        mChatExportJob = viewModelScope.launch {
            try {
                // 异步向目标 URI 写入导出的 JSONL 聊天归档
                mChatArchiveRepository.exportToUri(sessionId, intent.uri)
                AppViewEvent.PopupToastMessageByResId(R.string.export_chat_success).tryEmit()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                AppViewEvent.PopupToastMessageByResId(R.string.export_chat_failed).tryEmit()
            } finally {
                mChatExportJob = null
                // 恢复弹窗状态
                getOrNull<ChatUiState.Normal>()?.let { current ->
                    current.copy(
                        dialogState = current.dialogState.resolveExportDialogState(
                            isExportActive = false
                        )
                    ).setup()
                }
            }
        }
    }

    /**
     * 手动触发立即总结会话历史。
     */
    @UiIntentObserver(ChatUiIntent.SummarizeNow::class)
    private suspend fun onSummarizeNow() {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.stop_generation_before_summarizing).tryEmit()
            return
        }
        launchSummaryJob(sessionId, showToast = true)
    }

    /**
     * 恢复/回滚至上一版历史摘要。
     */
    @UiIntentObserver(ChatUiIntent.RestorePreviousSummary::class)
    private suspend fun onRestorePreviousSummary() {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        if (mGenerationJob?.isActive == true || mSummaryJob?.isActive == true) return
        val restored = withContext(Dispatchers.IO) {
            mChatRepository.restorePreviousSummary(sessionId)
        }
        AppViewEvent.PopupToastMessageByResId(
            if (restored) R.string.summary_restored else R.string.no_previous_summary
        ).tryEmit()
        if (restored) refreshUiState(sessionId = sessionId)
    }

    /**
     * 切换当前会话是否暂停自动总结功能。
     *
     * @param intent 包含是否暂停标志的意图
     */
    @UiIntentObserver(ChatUiIntent.ToggleAutoSummaryPaused::class)
    private suspend fun onToggleAutoSummaryPaused(
        intent: ChatUiIntent.ToggleAutoSummaryPaused
    ) {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.updateAutoSummaryPaused(sessionId, intent.paused)
        }
        refreshUiState(sessionId = sessionId, page = ChatPage.Settings)
    }

    /**
     * 取消当前正在执行的总结任务。
     */
    @UiIntentObserver(ChatUiIntent.CancelSummary::class)
    private suspend fun onCancelSummary() {
        if (!isStateOf<ChatUiState.Normal>()) return
        mSummaryJobMutex.withLock {
            mSummaryJob?.cancelAndJoin()
        }
    }

    /**
     * 点击“删除会话”，展示确认删除对话框。
     */
    @UiIntentObserver(ChatUiIntent.DeleteSessionClick::class)
    private fun onDeleteSessionClick() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.stop_generation_before_deleting).tryEmit()
            return
        }
        uiState.copy(
            dialogState = ChatDialogState.DeleteSessionConfirm(uiState.session.title)
        ).setup()
    }

    /**
     * 复制 Prompt 检查器中的指定文本内容至系统剪贴板。
     *
     * @param intent 包含待复制文本的意图
     */
    @UiIntentObserver(ChatUiIntent.CopyPromptItem::class)
    private fun onCopyPromptItem(intent: ChatUiIntent.CopyPromptItem) {
        if (!isStateOf<ChatUiState.Normal>()) return
        ChatViewEvent.CopyText(intent.text).tryEmit()
    }

    /**
     * 确认删除当前会话。
     *
     * 从数据库物理删除该会话及其全部消息、总结等关联数据，并关闭页面。
     */
    @UiIntentObserver(ChatUiIntent.ConfirmDeleteSession::class)
    private suspend fun onConfirmDeleteSession() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        // 生成中禁止删除
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.stop_generation_before_deleting).tryEmit()
            uiState.copy(dialogState = ChatDialogState.None).setup()
            return
        }
        // 切换为删除中状态
        uiState.copy(
            loadState = ChatLoadState.Deleting,
            dialogState = ChatDialogState.None
        ).setup()
        // 异步物理删除会话及关联数据
        withContext(Dispatchers.IO) {
            mChatRepository.deleteSession(sessionId)
        }
        AppViewEvent.PopupToastMessageByResId(R.string.chat_deleted).tryEmit()
        // 关闭退出聊天页面
        ChatUiState.finished(uiStateFlow.value).setup()
    }

    /**
     * 点击删除单条消息，展示确认删除对话框。
     *
     * @param intent 包含待删除消息 ID 的意图
     */
    @UiIntentObserver(ChatUiIntent.DeleteMessageClick::class)
    private fun onDeleteMessageClick(intent: ChatUiIntent.DeleteMessageClick) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.stop_generation_before_deleting_message).tryEmit()
            return
        }
        uiState.copy(
            dialogState = ChatDialogState.DeleteMessageConfirm(intent.messageId)
        ).setup()
    }

    /**
     * 确认删除单条消息。
     *
     * @param intent 包含目标消息 ID 的意图
     */
    @UiIntentObserver(ChatUiIntent.ConfirmDeleteMessage::class)
    private suspend fun onConfirmDeleteMessage(intent: ChatUiIntent.ConfirmDeleteMessage) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.stop_generation_before_deleting_message).tryEmit()
            uiState.copy(dialogState = ChatDialogState.None).setup()
            return
        }
        uiState.copy(dialogState = ChatDialogState.None).setup()
        val messageId = intent.messageId.toLongOrNull() ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.deleteMessage(messageId)
        }
        AppViewEvent.PopupToastMessageByResId(R.string.message_deleted).tryEmit()
        refreshUiState(sessionId = sessionId)
    }

    /**
     * 关闭当前显示的任何弹窗/对话框。
     */
    @UiIntentObserver(ChatUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(dialogState = ChatDialogState.None).setup()
    }

    /**
     * 保存编辑后的会话标题。
     *
     * @param intent 包含新标题的意图（空白时回退为默认“未命名会话”）
     */
    @UiIntentObserver(ChatUiIntent.SaveTitle::class)
    private suspend fun onSaveTitle(intent: ChatUiIntent.SaveTitle) {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.updateSessionTitle(sessionId, intent.value.trim().ifBlank { mContext.getString(R.string.untitled_chat) })
        }
        refreshUiState(sessionId = sessionId)
    }

    /**
     * 保存手动编辑的当前会话摘要正文。
     *
     * @param intent 包含摘要新文本的意图
     */
    @UiIntentObserver(ChatUiIntent.SaveSummary::class)
    private suspend fun onSaveSummary(intent: ChatUiIntent.SaveSummary) {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.updateCurrentSummary(sessionId, intent.value)
        }
        refreshUiState(sessionId = sessionId)
    }

    /**
     * 保存当前会话的用户备注（User Note）。
     *
     * @param intent 包含新备注文本的意图
     */
    @UiIntentObserver(ChatUiIntent.SaveUserNote::class)
    private suspend fun onSaveUserNote(intent: ChatUiIntent.SaveUserNote) {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.updateSessionUserNote(sessionId, intent.value)
        }
        refreshUiState(sessionId = sessionId)
    }

    /**
     * 跳转至全局世界书管理界面。
     */
    @UiIntentObserver(ChatUiIntent.OpenWorldBookManager::class)
    private fun onOpenWorldBookManager() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(dialogState = ChatDialogState.None).setup()
        AppViewEvent.StartActivity(WorldBookListActivity::class.java).tryEmit()
    }

    /**
     * 跳转至当前角色的编辑界面。
     */
    @UiIntentObserver(ChatUiIntent.OpenCharacterEditor::class)
    private fun onOpenCharacterEditor() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        AppViewEvent.StartActivity(
            activity = CharacterEditActivity::class.java,
            extras = Bundle().apply {
                putLong(CharacterEditActivity.EXTRA_CHARACTER_ID, uiState.character.id)
            }
        ).tryEmit()
    }

    /**
     * 跳转至全局模型配置管理界面。
     */
    @UiIntentObserver(ChatUiIntent.OpenProviderSettings::class)
    private fun onOpenProviderSettings() {
        val uiState = getOrNull<ChatUiState.Normal>()
        uiState?.copy(dialogState = ChatDialogState.None)?.setup()
        AppViewEvent.StartActivity(LLMProviderListActivity::class.java).tryEmit()
    }

    /**
     * 保存当前会话的用户名称。
     *
     * 空白名称统一保存为默认值 `You`，避免生成 prompt 和消息署名出现空名称。
     */
    @UiIntentObserver(ChatUiIntent.SaveUserName::class)
    private suspend fun onSaveUserName(intent: ChatUiIntent.SaveUserName) {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.updateSessionUserName(sessionId, intent.value.normalizedUserName())
        }
        refreshUiState(sessionId = sessionId)
    }

    /**
     * 保存当前会话的用户描述。
     *
     * 用户描述仅影响当前会话，并在保存前移除首尾空白。
     */
    @UiIntentObserver(ChatUiIntent.SaveUserDescription::class)
    private suspend fun onSaveUserDescription(intent: ChatUiIntent.SaveUserDescription) {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.updateSessionUserDescription(sessionId, intent.value.trim())
        }
        refreshUiState(sessionId = sessionId)
    }

    /**
     * 保存当前会话的作者注释（Creator Notes 覆盖）。
     *
     * @param intent 包含作者注释文本的意图
     */
    @UiIntentObserver(ChatUiIntent.SaveCreatorNotes::class)
    private suspend fun onSaveCreatorNotes(intent: ChatUiIntent.SaveCreatorNotes) {
        if (!isStateOf<ChatUiState.Normal>()) return
        val sessionId = mSessionId ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.updateSessionCreatorNotes(sessionId, intent.value)
        }
        refreshUiState(sessionId = sessionId)
    }

    /**
     * 复制指定消息的展示内容到剪贴板。
     *
     * 全局设置不允许思考块进入上下文时，复制同样排除已保存的思考内容。
     *
     * @param intent 包含消息 ID 的意图
     */
    @UiIntentObserver(ChatUiIntent.CopyMessage::class)
    private suspend fun onCopyMessage(intent: ChatUiIntent.CopyMessage) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val message = uiState.conversationState.messages
            .firstOrNull { it.id == intent.messageId } ?: return
        val copyText = message.content.toMessageCopyText(
            includeThinkBlocks = runCatching { AppModel.includeThinkInContext }.getOrDefault(false)
        )
        if (copyText.isBlank()) return
        ChatViewEvent.CopyText(copyText).emit()
    }

    /**
     * 进入单条消息的编辑模式。
     *
     * 会从数据库拉取该消息未经 Display Regex 替换的原始正文，填入编辑草稿框中。
     *
     * @param intent 包含目标消息 ID 的意图
     */
    @UiIntentObserver(ChatUiIntent.StartEditMessage::class)
    private suspend fun onStartEditMessage(intent: ChatUiIntent.StartEditMessage) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val message = uiState.conversationState.messages
            .firstOrNull { it.id == intent.messageId } ?: return
        val messageId = intent.messageId.toLongOrNull() ?: return
        // 流式生成中的消息禁止编辑
        if (message.isStreaming) return
        // 异步从数据库拉取未经 Display 正则修改的原始文本
        val rawContent = withContext(Dispatchers.IO) {
            val sessionId = mSessionId ?: return@withContext null
            mChatRepository.getMessageById(messageId)
                ?.takeIf { it.sessionId == sessionId }
                ?.content
        } ?: return
        // 将 UI 切换至消息编辑状态并填入原始草稿
        uiState.copy(
            conversationState = uiState.conversationState.copy(
                editingMessageId = message.id,
                editingMessageDraft = rawContent
            )
        ).setup()
    }

    /**
     * 更新正在编辑的消息草稿内容。
     *
     * @param intent 包含草稿文本的意图
     */
    @UiIntentObserver(ChatUiIntent.ChangeEditingMessageDraft::class)
    private fun onChangeEditingMessageDraft(intent: ChatUiIntent.ChangeEditingMessageDraft) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        if (uiState.conversationState.editingMessageId == null) return
        uiState.copy(
            conversationState = uiState.conversationState.copy(
                editingMessageDraft = intent.value
            )
        ).setup()
    }

    /**
     * 保存对单条消息的编辑。
     *
     * 根据消息来源（User/Char）重新应用对应的 Source 正则规则（设置 isEdit = true 以触发 runOnEdit 约束），
     * 并将结果写回数据库，退出编辑状态。
     */
    @UiIntentObserver(ChatUiIntent.SaveEditingMessage::class)
    private suspend fun onSaveEditingMessage() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        val messageId = uiState.conversationState.editingMessageId?.toLongOrNull() ?: return
        // 异步处理消息内容更新与对应 Source 正则规则执行
        withContext(Dispatchers.IO) {
            val message = mChatRepository.getMessageById(messageId)
                ?.takeIf { it.sessionId == sessionId } ?: return@withContext
            // 依据消息来源分别执行对应的编辑期正则（isEdit = true）
            val content = when (message.source) {
                ChatMessage.Source.User -> applyUserRegex(
                    sessionId,
                    uiState.conversationState.editingMessageDraft,
                    isEdit = true
                )
                ChatMessage.Source.Char -> applyAiRegex(
                    sessionId,
                    uiState.conversationState.editingMessageDraft,
                    isEdit = true
                )
                ChatMessage.Source.System,
                ChatMessage.Source.Summary -> uiState.conversationState.editingMessageDraft
            }
            // 将修改后的消息正文持久化回数据库
            mChatRepository.updateMessageContent(messageId, content)
        }
        // 刷新 UI 状态并重置编辑态草稿
        refreshUiState(
            sessionId = sessionId,
            inputDraft = uiState.conversationState.inputDraft,
            generationState = uiState.conversationState.generationState,
            expandedThinkBlockIds = uiState.conversationState.expandedThinkBlockIds,
            editingMessageId = null,
            editingMessageDraft = ""
        )
    }

    /**
     * 取消消息编辑，重置编辑状态与草稿。
     */
    @UiIntentObserver(ChatUiIntent.CancelEditingMessage::class)
    private fun onCancelEditingMessage() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(
            conversationState = uiState.conversationState.copy(
                editingMessageId = null,
                editingMessageDraft = ""
            )
        ).setup()
    }

    /**
     * 切换消息中思考过程块（Thinking/Reasoning Block）的展开与折叠状态。
     *
     * @param intent 包含思考块唯一标识的意图
     */
    @UiIntentObserver(ChatUiIntent.ToggleThinkBlock::class)
    private fun onToggleThinkBlock(intent: ChatUiIntent.ToggleThinkBlock) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val ids = uiState.conversationState.expandedThinkBlockIds.toMutableSet()
        if (!ids.add(intent.blockId)) {
            ids.remove(intent.blockId)
        }
        uiState.copy(
            conversationState = uiState.conversationState.copy(
                expandedThinkBlockIds = ids.toSet()
            )
        ).setup()
    }

    /**
     * 检查当前会话关联角色或全局是否存在已启用的模型服务配置；若未配置则唤起引导弹窗。
     *
     * @param sessionId 会话 ID
     * @param characterId 角色 ID
     * @return true 表示已就绪可调用，false 表示已拦截并弹窗引导
     */
    private suspend fun ensureProviderConfigured(sessionId: Long, characterId: Long): Boolean {
        // 加载角色实体以解析角色绑定或全局默认配置
        val character = withContext(Dispatchers.IO) {
            mCharacterRepository.getCharacterById(characterId)
        }
        if (character == null) {
            val guide = noProviderModelSettingsGuide(mContext)
            refreshUiState(
                sessionId = sessionId,
                dialogState = guide.toChatDialogState()
            )
            return false
        }
        // 通过严格选择保留已停用角色绑定的配置名称，供错误对话框准确定位
        val providerError = runCatching {
            withContext(Dispatchers.IO) {
                mProviderSelectionResolver.requireCharacterProvider(character)
            }
        }.exceptionOrNull() ?: return true
        val failure = providerError.toGenerationFailurePresentation(
            mContext,
            R.string.generation_failed
        ) ?: throw providerError
        // 只拦截具有模型配置修复入口的已知选择错误
        val guide = failure.modelSettingsGuide ?: throw providerError
        refreshUiState(
            sessionId = sessionId,
            dialogState = guide.toChatDialogState()
        )
        return false
    }

    /**
     * 重新生成最后一条角色回复的核心业务逻辑。
     *
     * 规则限制与处理流程：
     * - 限制校验：当前仅允许重生成最后一条角色回复，防止破坏中间对话历史；若仅有开场白则不允许重生成。
     * - 构建 Prompt：以 [PromptGenerationMode.Regenerate] 模式构建，并显式传入待排除的消息 ID。
     * - 结果写回：将生成结果更新覆盖到该消息记录（[GenerationOutput.Update]），而不是创建新消息。
     *
     * @param sessionId 会话 ID
     */
    private suspend fun regenerateLastAssistantMessage(sessionId: Long) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(page = ChatPage.Conversation).setup()
        if (!ensureProviderConfigured(sessionId, uiState.character.id)) return
        // 并发拦截：若已有生成任务在运行则拒绝
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.generation_already_running).tryEmit()
            return
        }
        // 校验历史记录：只允许重生成最后一条角色回复，避免破坏中间历史
        val (latestAssistantMessage, messageCount) = withContext(Dispatchers.IO) {
            mChatRepository.getLatestMessageBySessionId(sessionId) to
                mChatRepository.getMessageCountBySessionId(sessionId)
        }
        if (latestAssistantMessage?.source != ChatMessage.Source.Char) {
            AppViewEvent.PopupToastMessageByResId(R.string.no_latest_assistant_reply_to_regenerate).tryEmit()
            return
        }
        if (messageCount == 1) {
            AppViewEvent.PopupToastMessageByResId(R.string.cannot_regenerate_only_first_message).tryEmit()
            return
        }
        // 启动重生成协程任务
        mGenerationJob = viewModelScope.launch {
            runCatching {
                // 更新 UI 为请求中状态
                refreshUiState(
                    sessionId = sessionId,
                    inputDraft = uiState.conversationState.inputDraft,
                    page = ChatPage.Conversation,
                    generationState = ChatGenerationState.Requesting,
                    expandedThinkBlockIds = uiState.conversationState.expandedThinkBlockIds
                )
                // 构建重生成请求，排除待被替换的消息本身
                val built = withContext(Dispatchers.IO) {
                    buildGenerationRequest(
                        sessionId = sessionId,
                        generationMode = PromptGenerationMode.Regenerate,
                        excludedMessageId = latestAssistantMessage.id
                    )
                }
                recordPromptInspection(built.inspection)
                // 分发调用模型生成，目标形态为 Update 覆盖已有消息
                if (AppModel.streamEnabled) {
                    generateStreaming(
                        sessionId,
                        built.provider,
                        built.request,
                        GenerationOutput.Update(latestAssistantMessage.id),
                        built.worldInfoStateJson
                    )
                } else {
                    generateOnce(
                        sessionId,
                        built.provider,
                        built.request,
                        GenerationOutput.Update(latestAssistantMessage.id),
                        built.worldInfoStateJson
                    )
                }
                // 检查自动总结
                maybeAutoSummarize(sessionId)
            }.onFailure { throwable ->
                // 异常处理：解析错误信息并更新 UI 状态
                val failure = throwable.toGenerationFailurePresentation(
                    mContext,
                    R.string.regenerate_failed
                ) ?: return@onFailure
                val guideDialog = failure.modelSettingsGuide?.toChatDialogState()
                refreshUiState(
                    sessionId = sessionId,
                    generationState = ChatGenerationState.Failed(failure.message),
                    dialogState = guideDialog ?: ChatDialogState.None
                )
                if (guideDialog == null) {
                    AppViewEvent.PopupToastMessage(failure.message).tryEmit()
                }
            }
        }
    }

    /**
     * 继续最后一轮对话（续写回复）。
     *
     * 智能分发逻辑：
     * - 若最后一条是用户消息：退化为普通的回复生成（[PromptGenerationMode.Normal]）。
     * - 若最后一条是角色消息：采用 [PromptGenerationMode.Continue] 任务提示词，并**新建消息**保存续写结果，避免直接覆盖已有历史。
     *
     * @param sessionId 会话 ID
     */
    private suspend fun continueLastAssistantMessage(sessionId: Long) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(page = ChatPage.Conversation).setup()
        if (!ensureProviderConfigured(sessionId, uiState.character.id)) return
        // 并发拦截
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.generation_already_running).tryEmit()
            return
        }
        // 检查最后一条消息及其来源
        val latestMessage = withContext(Dispatchers.IO) {
            mChatRepository.getLatestMessageBySessionId(sessionId)
        }
        if (latestMessage == null || (latestMessage.source != ChatMessage.Source.User && latestMessage.source != ChatMessage.Source.Char)) {
            AppViewEvent.PopupToastMessageByResId(R.string.no_latest_assistant_reply_to_continue).tryEmit()
            return
        }
        val isLastUser = latestMessage.source == ChatMessage.Source.User
        // 启动续写任务
        mGenerationJob = viewModelScope.launch {
            runCatching {
                refreshUiState(
                    sessionId = sessionId,
                    inputDraft = uiState.conversationState.inputDraft,
            generationState = ChatGenerationState.Requesting,
                    expandedThinkBlockIds = uiState.conversationState.expandedThinkBlockIds
                )
                // 依据最后一条消息来源决定生成模式（Normal 或 Continue）
                val generationMode = if (isLastUser) PromptGenerationMode.Normal else PromptGenerationMode.Continue
                val built = withContext(Dispatchers.IO) {
                    buildGenerationRequest(sessionId, generationMode)
                }
                recordPromptInspection(built.inspection)
                // 调用模型生成，续写结果作为新建角色消息保存
                if (AppModel.streamEnabled) {
                    generateStreaming(
                        sessionId,
                        built.provider,
                        built.request,
                        GenerationOutput.Create(ChatMessage.Source.Char),
                        built.worldInfoStateJson
                    )
                } else {
                    generateOnce(
                        sessionId,
                        built.provider,
                        built.request,
                        GenerationOutput.Create(ChatMessage.Source.Char),
                        built.worldInfoStateJson
                    )
                }
                // 检查自动总结
                maybeAutoSummarize(sessionId)
            }.onFailure { throwable ->
                val errorResId = if (isLastUser) R.string.generation_failed else R.string.continue_generation_failed
                val failure = throwable.toGenerationFailurePresentation(
                    mContext,
                    errorResId
                ) ?: return@onFailure
                val guideDialog = failure.modelSettingsGuide?.toChatDialogState()
                refreshUiState(
                    sessionId = sessionId,
                    generationState = ChatGenerationState.Failed(failure.message),
                    dialogState = guideDialog ?: ChatDialogState.None
                )
                if (guideDialog == null) {
                    AppViewEvent.PopupToastMessage(failure.message).tryEmit()
                }
            }
        }
    }

    /**
     * 让模型模仿用户的口吻生成下一条消息。
     *
     * 使用 [PromptGenerationMode.Impersonate] 模式构建 Prompt，生成结果以 [ChatMessage.Source.User] 保存落库。
     *
     * @param sessionId 会话 ID
     */
    private suspend fun generateUserImpersonation(sessionId: Long) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(page = ChatPage.Conversation).setup()
        if (!ensureProviderConfigured(sessionId, uiState.character.id)) return
        // 并发拦截
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.generation_already_running).tryEmit()
            return
        }
        // 启动模仿用户生成任务
        mGenerationJob = viewModelScope.launch {
            runCatching {
                refreshUiState(
                    sessionId = sessionId,
                    inputDraft = uiState.conversationState.inputDraft,
                    page = ChatPage.Conversation,
                    generationState = ChatGenerationState.Requesting,
                    expandedThinkBlockIds = uiState.conversationState.expandedThinkBlockIds
                )
                // 构建 Impersonate 模式 Prompt 请求
                val built = withContext(Dispatchers.IO) {
                    buildGenerationRequest(sessionId, PromptGenerationMode.Impersonate)
                }
                recordPromptInspection(built.inspection)
                // 调用模型生成，结果作为新建用户消息保存
                if (AppModel.streamEnabled) {
                    generateStreaming(
                        sessionId,
                        built.provider,
                        built.request,
                        GenerationOutput.Create(ChatMessage.Source.User),
                        built.worldInfoStateJson
                    )
                } else {
                    generateOnce(
                        sessionId,
                        built.provider,
                        built.request,
                        GenerationOutput.Create(ChatMessage.Source.User),
                        built.worldInfoStateJson
                    )
                }
                // 检查自动总结
                maybeAutoSummarize(sessionId)
            }.onFailure { throwable ->
                val failure = throwable.toGenerationFailurePresentation(
                    mContext,
                    R.string.impersonation_failed
                ) ?: return@onFailure
                val guideDialog = failure.modelSettingsGuide?.toChatDialogState()
                refreshUiState(
                    sessionId = sessionId,
                    generationState = ChatGenerationState.Failed(failure.message),
                    dialogState = guideDialog ?: ChatDialogState.None
                )
                if (guideDialog == null) {
                    AppViewEvent.PopupToastMessage(failure.message).tryEmit()
                }
            }
        }
    }

    /**
     * 非流式单次生成调用及结果提交。
     *
     * 处理流程：
     * - 调用 LLM 服务生成完整文本。
     * - 执行持久化前的 Source 正则替换。
     * - 将生成结果与本次世界书时序状态（worldInfoStateJson）原子提交入库。
     *
     * @param sessionId 会话 ID
     * @param provider LLM 服务提供商配置
     * @param request LLM 请求参数
     * @param output 生成输出目标描述（创建新消息或更新已有消息）
     * @param worldInfoStateJson 世界书时序快照 JSON
     */
    private suspend fun generateOnce(
        sessionId: Long,
        provider: LLMProvider,
        request: LLMGenerationRequest,
        output: GenerationOutput,
        worldInfoStateJson: String
    ) {
        // 异步调用模型生成完整响应
        val response = withContext(Dispatchers.IO) {
            mLLMRepository.generateWithProvider(
                provider = provider,
                request = request,
                routingSessionKey = "chat:$sessionId"
            )
        }
        // 执行持久化前的 Source 阶段正则替换
        val processedContent = withContext(Dispatchers.IO) {
            applyGeneratedRegex(sessionId, response.content, output)
        }
        // 空内容直接重置为空闲状态
        if (processedContent.isBlank()) {
            refreshUiState(sessionId = sessionId, generationState = ChatGenerationState.Idle)
            return
        }
        // 原子提交生成结果至数据库（并更新世界书时序状态）
        withContext(Dispatchers.IO) {
            mChatRepository.commitGenerationResult(
                sessionId = sessionId,
                messageId = (output as? GenerationOutput.Update)?.messageId,
                source = output.source(),
                content = processedContent,
                deleteEmptyPlaceholder = false,
                worldInfoStateJson = worldInfoStateJson
            )
        }
        // 刷新 UI 回到空闲状态
        refreshUiState(sessionId = sessionId, generationState = ChatGenerationState.Idle)
    }

    /**
     * 流式生成响应收集与原子性持久化保障。
     *
     * 关键架构设计：
     * - 占位消息管理：如果是创建新消息，先在数据库创建占位记录，便于 UI 实时展示与流式标记。
     * - 正则时序隔离：
     *   - 流式接收 Delta 期间：仅执行临时 Display 正则供 UI 高频渲染，不写数据库。
     *   - 收尾阶段：在 finally 块中，对完整的原始累计文本执行一次 Source 正则后再落库。
     * - 异常与取消保障：
     *   - 收尾运行在 [NonCancellable] 上下文中，确保用户点击“停止”或退出页面时，已收到的部分内容（partial）绝不丢失，能够被安全持久化。
     *   - 若在首个 Delta 到达前就被取消，占位消息将被干净清理（deleteEmptyPlaceholder = true）。
     *
     * @param sessionId 会话 ID
     * @param provider LLM 服务提供商配置
     * @param request LLM 请求参数
     * @param output 生成输出目标描述
     * @param worldInfoStateJson 世界书时序快照 JSON
     */
    private suspend fun generateStreaming(
        sessionId: Long,
        provider: LLMProvider,
        request: LLMGenerationRequest,
        output: GenerationOutput,
        worldInfoStateJson: String
    ) {
        // 异步加载本次生成绑定的正则脚本与宏快照
        val regexContext = withContext(Dispatchers.IO) {
            val session = mChatRepository.getSessionById(sessionId)
            val character = session?.let {
                mCharacterRepository.getCharacterById(it.characterId)
            }
            if (session != null && character != null) {
                StreamingRegexContext(
                    scripts = mRegexRepository.activeScripts(listOf(character)),
                    macros = RegexScriptRuntime.macros(
                        session.userName,
                        character.name,
                        session.userDescription,
                        character.scenario
                    )
                )
            } else {
                StreamingRegexContext()
            }
        }
        // 构建活跃流式生成状态跟踪对象
        val token = Any()
        var active = ActiveStreamingGeneration(
            token = token,
            sessionId = sessionId,
            output = output,
            messageId = (output as? GenerationOutput.Update)?.messageId,
            createdPlaceholder = false,
            content = "",
            regexScripts = regexContext.scripts,
            regexMacros = regexContext.macros,
            worldInfoStateJson = worldInfoStateJson
        )
        mActiveStreamingGeneration = active
        try {
            // 若为创建新消息，在数据库创建占位记录（支持取消时无痕删除）
            if (output is GenerationOutput.Create) {
                val placeholderId = withContext(NonCancellable + Dispatchers.IO) {
                    mChatRepository.createGenerationPlaceholder(sessionId, output.source)
                }
                active = active.copy(
                    messageId = placeholderId,
                    createdPlaceholder = true
                )
                mActiveStreamingGeneration = active
            }
            // 刷新 UI 为流式接收中状态
            refreshUiState(
                sessionId = sessionId,
                generationState = ChatGenerationState.Streaming(active.messageId, active.content)
            )
            // 收集大模型流式增量事件
            mLLMRepository.streamGenerateWithProvider(
                provider = provider,
                request = request,
                routingSessionKey = "chat:$sessionId"
            ).collect { event ->
                currentCoroutineContext().ensureActive()
                when (event) {
                    is LLMStreamEvent.Delta -> {
                        active = active.copy(content = active.content + event.content)
                        mActiveStreamingGeneration = active
                        // 计算用于 UI 实时展示的正则替换文本（Display 阶段正则）
                        val displayContent = applyStreamingDisplayRegex(active)
                        val uiState = getOrNull<ChatUiState.Normal>() ?: return@collect
                        // 仅在内存中替换当前流式消息内容，避免高频写库
                        uiState.copy(
                            conversationState = uiState.conversationState.copy(
                                generationState = ChatGenerationState.Streaming(
                                    active.messageId,
                                    active.content
                                ),
                                messages = uiState.conversationState.messages.replaceStreamingMessage(
                                    active.messageId,
                                    displayContent
                                )
                            )
                        ).setup()
                    }
                    LLMStreamEvent.Connected,
                    is LLMStreamEvent.ReasoningDelta,
                    is LLMStreamEvent.Finished -> Unit
                }
            }
        } finally {
            // 收尾阶段：在 NonCancellable 上下文下执行唯一一次持久化提交，确保部分生成内容安全落库
            val snapshot = active
            try {
                withContext(NonCancellable + Dispatchers.IO) {
                    val finalContent = snapshot.content.takeIf { it.isNotBlank() }
                        ?.let { applyStreamingGeneratedRegex(snapshot) }
                        .orEmpty()
                    mChatRepository.commitGenerationResult(
                        sessionId = snapshot.sessionId,
                        messageId = snapshot.messageId,
                        source = snapshot.output.source(),
                        content = finalContent,
                        deleteEmptyPlaceholder = snapshot.createdPlaceholder,
                        worldInfoStateJson = snapshot.worldInfoStateJson
                    )
                }
            } finally {
                // 清理全局活跃流式引用
                if (mActiveStreamingGeneration?.token === token) {
                    mActiveStreamingGeneration = null
                }
            }
        }
        // 恢复 UI 为空闲状态
        refreshUiState(sessionId = sessionId, generationState = ChatGenerationState.Idle)
    }

    /**
     * 启动一个摘要任务，并通过 Mutex 保证串行化执行。
     *
     * 互斥锁确保上一任务完成取消和 NonCancellable UI 收尾后，新任务才启动，
     * 避免旧任务的 finally 块意外关闭新任务刚展示的“总结中”弹窗。
     *
     * @param sessionId 会话 ID
     * @param showToast 是否在总结完成或出错时弹出 Toast（手动触发为 true，自动触发为 false）
     * @return 启动的任务 Job
     */
    private suspend fun launchSummaryJob(sessionId: Long, showToast: Boolean): Job {
        return mSummaryJobMutex.withLock {
            // 取消并等待上一个可能正在执行的总结任务
            mSummaryJob?.cancelAndJoin()
            // 启动新的总结协程任务
            viewModelScope.launch {
                try {
                    summarizeSession(sessionId, showToast)
                } finally {
                    // 收尾处理：若弹窗仍处于“总结中”状态，则安全关闭弹窗
                    withContext(NonCancellable) {
                        val currentState = getOrNull<ChatUiState.Normal>()
                        if (currentState != null && currentState.dialogState is ChatDialogState.Summarizing) {
                            refreshUiState(
                                sessionId = sessionId,
                                inputDraft = currentState.conversationState.inputDraft,
                                expandedThinkBlockIds = currentState.conversationState.expandedThinkBlockIds,
                                dialogState = ChatDialogState.None
                            )
                        }
                    }
                }
            }.also { mSummaryJob = it }
        }
    }

    /**
     * 检查是否满足自动总结触发条件，并在满足时执行自动总结。
     *
     * 条件包括：
     * - 全局自动总结开关开启。
     * - 会话未暂停自动总结。
     * - 上次总结之后的新增消息数达到全局设定阈值（[AppModel.summaryTriggerMessageCount]）。
     *
     * @param sessionId 会话 ID
     */
    private suspend fun maybeAutoSummarize(sessionId: Long) {
        if (!AppModel.autoSummaryEnabled) return
        val shouldSummarize = withContext(Dispatchers.IO) {
            val session = mChatRepository.getSessionById(sessionId)
            if (session?.autoSummaryPaused != false) return@withContext false
            val messages = mChatRepository.getMessagesAfterLatestSummary(sessionId)
            messages.isNotEmpty() && messages.size >= AppModel.summaryTriggerMessageCount
        }
        if (shouldSummarize) {
            val job = launchSummaryJob(sessionId, showToast = false)
            job.join()
        }
    }

    /**
     * 生成增量摘要核心执行逻辑。
     *
     * 处理流程：
     * - 读取待总结的消息切片（手动触发时允许重新覆盖最新摘要）。
     * - 使用 [SummaryPromptBuilder] 构建增量总结 Prompt 请求。
     * - 调用模型生成摘要正文并进行安全字符清洗（[summarySafeContent]）。
     * - 将新摘要及其实际覆盖的消息边界 ID（coveredMessageId）保存至数据库。
     *
     * @param sessionId 会话 ID
     * @param showToast 是否显示 Toast 提示
     */
    private suspend fun summarizeSession(sessionId: Long, showToast: Boolean) {
        runCatching {
            // 异步组装总结所需的基础数据（会话、角色、待总结切片、模型提供商）
            val data = withContext(Dispatchers.IO) {
                val session = mChatRepository.getSessionById(sessionId) ?: return@withContext null
                val character = mCharacterRepository.getCharacterById(session.characterId) ?: return@withContext null
                val summaryContext = mChatRepository.getSummaryGenerationContext(
                    sessionId = sessionId,
                    allowRefreshLatest = showToast
                )
                val provider = mProviderSelectionResolver.requireSummaryProvider()
                AutoSummaryData(
                    session = session,
                    character = character,
                    summary = summaryContext.existingSummary,
                    messages = summaryContext.messages,
                    summaryIdToUpdate = summaryContext.summaryToUpdate?.id,
                    provider = provider
                )
            } ?: return
            // 检查待总结消息列表是否为空或未达自动阈值
            if (data.messages.isEmpty()) {
                if (showToast) AppViewEvent.PopupToastMessageByResId(R.string.no_unsummarized_messages).tryEmit()
                return
            }
            if (!showToast && data.messages.size < AppModel.summaryTriggerMessageCount) return

            // 设置 UI 为总结中弹窗状态
            val uiState = getOrNull<ChatUiState.Normal>() ?: return
            uiState.copy(dialogState = ChatDialogState.Summarizing).setup()

            // 构建总结专用 Prompt 请求
            val built = mSummaryPromptBuilder.buildWithSelection(
                userName = data.session.userName,
                userDescription = data.session.userDescription,
                character = data.character,
                session = data.session,
                existingSummary = data.summary,
                messages = data.messages,
                provider = data.provider
            )
            if (built.selectedMessages.isEmpty()) return

            currentCoroutineContext().ensureActive()

            // 调用大模型生成摘要
            val response = withContext(Dispatchers.IO) {
                mLLMRepository.generateWithProvider(
                    provider = data.provider,
                    request = built.request,
                    routingSessionKey = "chat:$sessionId"
                )
            }
            // 清洗摘要文本
            val summaryContent = response.content.summarySafeContent()
            if (summaryContent.isBlank()) {
                error(mContext.getString(R.string.summary_failed))
            }

            currentCoroutineContext().ensureActive()

            // 持久化新摘要与覆盖边界消息 ID
            withContext(Dispatchers.IO) {
                mChatRepository.saveSummary(
                    sessionId = sessionId,
                    content = summaryContent,
                    coveredMessageId = built.selectedMessages.last().id,
                    summaryIdToUpdate = data.summaryIdToUpdate
                )
            }
            if (showToast) AppViewEvent.PopupToastMessageByResId(R.string.summary_updated).tryEmit()
        }.onFailure { throwable ->
            val failure = throwable.toGenerationFailurePresentation(
                mContext,
                R.string.summary_failed
            ) ?: throw throwable
            val guideDialog = failure.modelSettingsGuide?.toChatDialogState()
            if (guideDialog != null) {
                val uiState = getOrNull<ChatUiState.Normal>() ?: return@onFailure
                uiState.copy(dialogState = guideDialog).setup()
            } else {
                AppViewEvent.PopupToastMessage(failure.message).tryEmit()
            }
        }
    }

    /**
     * 收集单聊 Prompt 所需的完整上下文数据并交给 [ChatPromptBuilder] 构建大模型请求。
     *
     * 特殊边界处理：
     * 如果正在执行重生成（Regenerate），且待替换的消息恰好是当前最新摘要的覆盖边界（coveredMessageId），
     * 则需要回退一版摘要重新取上下文，避免请求中包含由待替换消息所生成的旧摘要内容。
     *
     * @param sessionId 会话 ID
     * @param generationMode 生成模式（Normal, Regenerate, Continue, Impersonate）
     * @param excludedMessageId 需要从 Prompt 历史中排除的消息 ID（如重生成时的待替换消息）
     * @return 构建完成的生成请求数据包装对象
     */
    private suspend fun buildGenerationRequest(
        sessionId: Long,
        generationMode: PromptGenerationMode = PromptGenerationMode.Normal,
        excludedMessageId: Long? = null
    ): BuiltGenerationRequest {
        // 加载会话实体与角色人设数据
        val session = mChatRepository.getSessionById(sessionId) ?: error(mContext.getString(R.string.session_not_found))
        val character = mCharacterRepository.getCharacterById(session.characterId) ?: error(mContext.getString(R.string.character_not_found))
        val generationHistory = mChatRepository.getPromptHistoryContext(
            sessionId = sessionId,
            excludedMessageId = excludedMessageId,
            maxHistoryMessages = AppModel.maxPromptHistoryMessages.coerceAtLeast(0)
        )
        // 收集并过滤当前会话已启用的世界书条目与递归扫描设置
        val enabledIds = mChatRepository.getSessionLorebookEntryIds(session).toSet()
        val lorebookData = getAllLorebookEntries()
        val allLorebookEntries = lorebookData.entries
        val lorebookEntries = allLorebookEntries.filter { it.id in enabledIds }
        val activeLorebookIds = lorebookEntries.map { it.lorebookId }.toSet()
        val activeLorebooks = lorebookData.lorebooks
            .filterKeys { it in activeLorebookIds }
        val recursiveLorebookIds = activeLorebooks.values
            .filter { it.recursiveScanning }
            .map { it.id }
            .toSet()
        // 解析角色绑定的模型服务提供商
        val provider = mProviderSelectionResolver.requireCharacterProvider(character)
        // 组装 PromptBuildContext 并调用 Prompt 构建器
        val creatorNotes = mChatRepository.getSessionCreatorNotes(session)
        val regexScripts = mRegexRepository.activeScripts(listOf(character))
        val buildResult = withContext(Dispatchers.Default) {
            mChatPromptBuilder.buildWithMetadata(
                PromptBuildContext(
                    userName = session.userName,
                    userDescription = session.userDescription,
                    character = character,
                    session = session.copy(creatorNotes = creatorNotes),
                    summary = generationHistory.summary,
                    messages = generationHistory.messages,
                    currentUserMessage = null,
                    totalMessageCount = generationHistory.totalMessageCount,
                    candidateLorebookEntries = lorebookEntries,
                    candidateLorebooks = activeLorebooks,
                    recursiveScanningLorebookIds = recursiveLorebookIds,
                    provider = provider,
                    maxContextTokens = provider.contextTokens,
                    maxResponseTokens = provider.maxTokens,
                    generationMode = generationMode,
                    regexScripts = regexScripts
                )
            )
        }
        return BuiltGenerationRequest(
            provider = provider,
            request = buildResult.request,
            inspection = buildResult.inspection,
            worldInfoStateJson = buildResult.worldInfoStateJson
        )
    }

    /**
     * 记录 Prompt 检查详情，并在发生世界书预算超限或上下文裁剪时弹出 Toast 告警。
     *
     * @param inspection Prompt 结构与裁剪详情报告
     */
    private fun recordPromptInspection(inspection: PromptInspection) {
        mLastPromptInspection = inspection
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(hasPromptInspection = true).setup()
        // 检查是否存在世界书超限或上下文被裁剪项
        val hasWorldInfoOverflow = inspection.omittedItems.any {
            it.reason == PromptOmissionReason.WorldInfoBudget
        }
        val hasContextTrimming = inspection.omittedItems.any {
            it.reason == PromptOmissionReason.ContextBudget
        }
        // 根据全局配置按需弹出告警 Toast
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
     * 从持久化数据层加载并重建单聊页面 UI 状态。
     *
     * 展示特性：
     * - Display 正则：历史消息在内存中执行 Display 阶段正则，以便支持 Markdown 替换，而数据库中的原始 Source 正文保持纯净。
     * - 头像解析：将角色头像本地文件路径解码为 [androidx.compose.ui.graphics.ImageBitmap]。
     * - 世界书分组：将条目按所属世界书组织，并应用当前搜索词过滤。
     *
     * @param sessionId 会话 ID
     * @param inputDraft 输入框草稿
     * @param page 当前子页面（对话/设置）
     * @param lorebookQuery 世界书搜索词
     * @param loadState 页面整体加载/保存状态
     * @param generationState 大模型生成状态
     * @param expandedThinkBlockIds 已展开的思考块 ID 集合
     * @param editingMessageId 正在编辑的消息 ID
     * @param editingMessageDraft 正在编辑的消息草稿
     * @param dialogState 当前展示的对话框状态
     * @param messageLimit 从会话末尾保留的消息窗口大小
     * @return 组装完成的 [ChatUiState.Normal]，若会话或角色不存在返回 null
     */
    private suspend fun loadNormalState(
        sessionId: Long,
        inputDraft: String = "",
        page: ChatPage = ChatPage.Conversation,
        lorebookQuery: String = "",
        loadState: ChatLoadState = ChatLoadState.None,
        generationState: ChatGenerationState = ChatGenerationState.Idle,
        expandedThinkBlockIds: Set<String> = emptySet(),
        editingMessageId: String? = null,
        editingMessageDraft: String = "",
        dialogState: ChatDialogState = ChatDialogState.None,
        messageLimit: Int = MESSAGE_PAGE_SIZE
    ): ChatUiState.Normal? {
        // 查询会话基础数据、角色人设及最近消息窗口
        val session = mChatRepository.getSessionById(sessionId) ?: return null
        val character = mCharacterRepository.getCharacterById(session.characterId) ?: return null
        val messagePage = mChatRepository.getLatestMessagePage(sessionId, messageLimit)
        val displayContext = ChatMessageDisplayContext(session, character)
        val displayMessages = messagePage.messages.toDisplayMessageItems(displayContext)
        mMessageDisplayContext = displayContext
        mOldestLoadedMessageCursor = messagePage.messages.firstOrNull()?.toChatMessageCursor()
        // 获取摘要、世界书及角色头像资源
        val summary = mChatRepository.getLatestSummary(sessionId)?.content.orEmpty()
        val lorebookData = getAllLorebookEntries()
        val enabledIds = mChatRepository.getSessionLorebookEntryIds(session).toSet()
        val effectiveCreatorNotes = mChatRepository.getSessionCreatorNotes(session)
        val avatarImage = character.avatar.takeIf { it.isNotBlank() }?.let {
            mFileRepository.loadAvatarBitmap(it)?.asImageBitmap()
        }
        val hasAvailableProvider = mProviderSelectionResolver.getCharacterProviderOrNull(character) != null
        // 组装并返回 Normal UI 状态
        return ChatUiState.Normal(
            page = page,
            loadState = loadState,
            session = session.toChatSessionItem(
                summary = summary,
                creatorNotes = effectiveCreatorNotes,
                messageCount = messagePage.totalMessageCount,
                enabledIds = enabledIds
            ),
            character = character.toChatCharacterItem(
                userName = session.userName,
                avatarImage = avatarImage
            ),
            conversationState = ChatConversationState(
                messages = displayMessages,
                canLoadOlderMessages = messagePage.canLoadOlderMessages,
                inputDraft = inputDraft,
                generationState = generationState,
                expandedThinkBlockIds = expandedThinkBlockIds,
                editingMessageId = editingMessageId,
                editingMessageDraft = editingMessageDraft
            ),
            lorebookState = lorebookData.toChatLorebookGroupItems(
                    enabledIds = enabledIds,
                    unknownLorebookName = mContext.getString(R.string.unknown_lorebook)
                ).let { groups ->
                    ChatLorebookState(
                        groups = groups,
                        visibleGroups = groups.filterForQuery(lorebookQuery),
                        query = lorebookQuery
                    )
                },
            streamEnabled = AppModel.streamEnabled,
            hasPromptInspection = mLastPromptInspection != null,
            hasAvailableProvider = hasAvailableProvider,
            dialogState = dialogState
        )
    }

    /**
     * 将一段连续的数据库消息转换为单聊页面展示模型。
     *
     * [newerMessageCount] 保证分批转换时的 Regex depth 仍以会话最新消息为零点，
     * 与一次性转换完整历史的行为保持一致。
     *
     * @receiver 按创建时间正序排列的连续消息。
     * @param context 当前会话和角色的 Display Regex 上下文。
     * @param newerMessageCount 当前片段之后已经加载的消息数量。
     * @return 已应用 Display Regex 和思考块拆分的展示消息。
     */
    private suspend fun List<ChatMessage>.toDisplayMessageItems(
        context: ChatMessageDisplayContext,
        newerMessageCount: Int = 0
    ): List<ChatMessageUiModel> {
        val regexScripts = mRegexRepository.activeScripts(listOf(context.character))
        val regexMacros = RegexScriptRuntime.macros(
            userName = context.session.userName,
            characterName = context.character.name,
            userDescription = context.session.userDescription,
            scenario = context.character.scenario
        )
        // 每一页只处理自身消息，深度偏移仍覆盖已经加载的较新窗口
        val displayMessages = mapIndexed { index, message ->
            val depth = newerMessageCount + lastIndex - index
            val result = when (message.source) {
                ChatMessage.Source.User -> mRegexProcessor.applyDisplay(
                    input = message.content,
                    source = RegexMessageSource.User,
                    scripts = regexScripts,
                    macros = regexMacros,
                    depth = depth
                )
                ChatMessage.Source.Char -> mRegexProcessor.applyDisplay(
                    input = message.content,
                    source = RegexMessageSource.Character,
                    scripts = regexScripts,
                    macros = regexMacros,
                    depth = depth
                )
                ChatMessage.Source.System,
                ChatMessage.Source.Summary -> null
            }
            if (result == null) message else message.copy(content = result)
        }
        return displayMessages.toChatMessageItems(
            characterName = context.character.name,
            userName = context.session.userName,
            systemSpeaker = mContext.getString(R.string.system_speaker),
            streamingMessageId = mActiveStreamingGeneration?.messageId
        )
    }

    /**
     * 辅助刷新 UI 状态函数，自动从当前状态获取默认值并从数据层重载最新状态。
     */
    private suspend fun refreshUiState(
        sessionId: Long,
        inputDraft: String = getOrNull<ChatUiState.Normal>()
            ?.conversationState?.inputDraft.orEmpty(),
        page: ChatPage = getOrNull<ChatUiState.Normal>()?.page ?: ChatPage.Conversation,
        lorebookQuery: String = getOrNull<ChatUiState.Normal>()?.lorebookState?.query.orEmpty(),
        loadState: ChatLoadState = ChatLoadState.None,
        generationState: ChatGenerationState = getOrNull<ChatUiState.Normal>()
            ?.conversationState?.generationState ?: ChatGenerationState.Idle,
        expandedThinkBlockIds: Set<String> = getOrNull<ChatUiState.Normal>()
            ?.conversationState?.expandedThinkBlockIds ?: emptySet(),
        editingMessageId: String? = getOrNull<ChatUiState.Normal>()
            ?.conversationState?.editingMessageId,
        editingMessageDraft: String = getOrNull<ChatUiState.Normal>()
            ?.conversationState?.editingMessageDraft.orEmpty(),
        dialogState: ChatDialogState = getOrNull<ChatUiState.Normal>()?.dialogState ?: ChatDialogState.None,
        messageLimit: Int = getOrNull<ChatUiState.Normal>()
            ?.conversationState?.messages?.size
            ?.coerceAtLeast(MESSAGE_PAGE_SIZE)
            ?: MESSAGE_PAGE_SIZE
    ) {
        val nextState = withContext(Dispatchers.IO) {
            loadNormalState(
                sessionId = sessionId,
                inputDraft = inputDraft,
                page = page,
                lorebookQuery = lorebookQuery,
                loadState = loadState,
                generationState = generationState,
                expandedThinkBlockIds = expandedThinkBlockIds,
                editingMessageId = editingMessageId,
                editingMessageDraft = editingMessageDraft,
                dialogState = dialogState,
                messageLimit = messageLimit
            )
        } ?: return
        nextState.setup()
    }

    /**
     * 持久化当前会话启用的世界书条目 ID 列表。
     *
     * @param sessionId 会话 ID
     * @param enabledIds 启用的条目 ID 集合
     */
    private suspend fun saveSessionLorebookEntryIds(
        sessionId: Long,
        enabledIds: Set<Long>
    ) {
        withContext(Dispatchers.IO) {
            mChatRepository.updateSessionLorebookEntryIds(sessionId, enabledIds.toList())
        }
    }

    /**
     * 获取数据库中全部世界书及其所有条目的聚合数据。
     */
    private suspend fun getAllLorebookEntries(): ChatLorebookEntryData {
        val lorebooksWithEntries = mLorebookRepository.getAllLorebooksWithEntries()
        return ChatLorebookEntryData(
            lorebooks = lorebooksWithEntries.associate { it.lorebook.id to it.lorebook },
            entries = lorebooksWithEntries.flatMap { it.entries }
        )
    }

    /**
     * 弹出错误 Toast 并结束当前页面。
     *
     * @param messageResId 字符串资源 ID
     */
    private fun finishWithToast(messageResId: Int) {
        AppViewEvent.PopupToastMessageByResId(messageResId).tryEmit()
        ChatUiState.finished(uiStateFlow.value).setup()
    }

    /** 将持久化消息转换为向前分页使用的稳定游标。 */
    private fun ChatMessage.toChatMessageCursor(): ChatMessageCursor {
        return ChatMessageCursor(createTime = createTime, messageId = id)
    }

    private companion object {
        /** 聊天页面首次和后续向前加载的单页消息数量。 */
        const val MESSAGE_PAGE_SIZE = 50
    }

    /** 单聊分页消息执行 Display Regex 所需的持久化上下文。 */
    private data class ChatMessageDisplayContext(
        val session: ChatSession,
        val character: Character
    )

    /** 单聊消息由创建时间与 ID 组成的稳定分页游标。 */
    private data class ChatMessageCursor(
        val createTime: Long,
        val messageId: Long
    )

    /** 已完成展示转换、可直接合并进 UiState 的一页单聊消息。 */
    private data class LoadedChatMessagePage(
        val items: List<ChatMessageUiModel>,
        val cursor: ChatMessageCursor?,
        val canLoadOlderMessages: Boolean,
        val totalMessageCount: Int
    )

    /**
     * 构建好的 LLM 请求及元数据包装。
     *
     * @property provider 使用的 LLM 服务配置
     * @property request 组装好的请求体
     * @property inspection Prompt 检查报告
     * @property worldInfoStateJson 世界书时序激活状态快照 JSON
     */
    private data class BuiltGenerationRequest(
        /** 当前请求关联的模型供应商类型。 */
        val provider: LLMProvider,
        /** 经过业务层组装、准备提交给模型服务的请求。 */
        val request: LLMGenerationRequest,
        /** 与实际请求一致、供 Prompt 检查器展示的构建明细。 */
        val inspection: PromptInspection,
        /** 序列化后的世界书时序状态，需要随会话或故事持久化。 */
        val worldInfoStateJson: String
    )

    /**
     * 在用户输入文本持久化前执行 Source 正则替换。
     *
     * 处理时序：
     * - 若输入以 `/` 开头，先进入 SlashCommand placement 进行指令宏转换。
     * - 随后进入 UserInput placement 执行用户输入正则。
     * - 编辑已有消息时通过 [isEdit] 激活 runOnEdit 约束。
     *
     * @param sessionId 会话 ID
     * @param input 原始用户文本
     * @param isEdit 是否为编辑已有消息
     * @return 经过正则替换后的文本
     */
    private suspend fun applyUserRegex(
        sessionId: Long,
        input: String,
        isEdit: Boolean = false
    ): String {
        val session = mChatRepository.getSessionById(sessionId) ?: return input
        val character = mCharacterRepository.getCharacterById(session.characterId) ?: return input
        val scripts = mRegexRepository.activeScripts(listOf(character))
        val macros = RegexScriptRuntime.macros(
            session.userName,
            character.name,
            session.userDescription,
            character.scenario
        )
        return mRegexProcessor.applyUserInput(input, scripts, macros, isEdit)
    }

    /**
     * 在 AI 生成文本持久化前执行 Source 正则替换。
     *
     * @param sessionId 会话 ID
     * @param input 原始 AI 生成文本
     * @param isEdit 是否为编辑已有消息
     * @return 经过正则替换后的文本
     */
    private suspend fun applyAiRegex(
        sessionId: Long,
        input: String,
        isEdit: Boolean = false
    ): String {
        val session = mChatRepository.getSessionById(sessionId) ?: return input
        val character = mCharacterRepository.getCharacterById(session.characterId) ?: return input
        return mRegexProcessor.applyAiResponse(
            input = input,
            scripts = mRegexRepository.activeScripts(listOf(character)),
            macros = RegexScriptRuntime.macros(
                session.userName,
                character.name,
                session.userDescription,
                character.scenario
            ),
            isEdit = isEdit
        )
    }

    /**
     * 根据生成的输出源类型分发应用用户正则或 AI 正则。
     *
     * @param sessionId 会话 ID
     * @param input 原始生成内容
     * @param output 生成目标描述
     * @return 正则替换后文本
     */
    private suspend fun applyGeneratedRegex(
        sessionId: Long,
        input: String,
        output: GenerationOutput?
    ): String {
        return if (output is GenerationOutput.Create && output.source == ChatMessage.Source.User) {
            applyUserRegex(sessionId, input)
        } else {
            applyAiRegex(sessionId, input)
        }
    }

    /**
     * 使用生成启动时冻结的脚本与宏快照，对流式生成的最终文本执行 Source 正则替换。
     *
     * 关键设计：
     * 收尾可能运行在协程取消后的 NonCancellable 区域，因此直接使用快照中的脚本和宏而不再查询数据库，
     * 避免生成途中用户修改脚本导致持久化规则前后不一致。
     *
     * @param snapshot 活跃流式生成的只读快照
     * @return 最终持久化文本
     */
    private fun applyStreamingGeneratedRegex(snapshot: ActiveStreamingGeneration): String {
        return mRegexProcessor.applyGenerated(
            input = snapshot.content,
            source = snapshot.output.source().toRegexMessageSource(),
            scripts = snapshot.regexScripts,
            macros = snapshot.regexMacros
        )
    }

    /**
     * 对流式生成的增量文本应用 Display 正则替换，仅供 UI 界面实时渲染 Markdown。
     *
     * @param snapshot 活跃流式生成的只读快照
     * @return 用于 UI 展示的替换文本
     */
    private fun applyStreamingDisplayRegex(snapshot: ActiveStreamingGeneration): String {
        return mRegexProcessor.applyDisplay(
            input = snapshot.content,
            source = snapshot.output.source().toRegexMessageSource(),
            scripts = snapshot.regexScripts,
            macros = snapshot.regexMacros
        )
    }

    /**
     * 依据搜索词过滤世界书分组及其内部条目。
     *
     * 匹配范围：世界书名称、条目名称、条目正文、主关键字、次关键字。
     *
     * @param query 搜索关键词
     * @return 过滤后的世界书分组列表
     */
    private fun List<ChatLorebookGroupItem>.filterForQuery(
        query: String
    ): List<ChatLorebookGroupItem> = filterLorebookGroups(
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
     * 自动总结流程所需的数据包装类。
     */
    private data class AutoSummaryData(
        /** 当前页面展示或编辑的会话数据。 */
        val session: ChatSession,
        /** 当前状态或操作关联的角色数据。 */
        val character: Character,
        /** 当前会话或故事使用的摘要内容。 */
        val summary: String,
        /** 当前状态或请求包含的消息列表。 */
        val messages: List<ChatMessage>,
        /** 生成完成后需要覆盖的既有摘要 ID。 */
        val summaryIdToUpdate: Long?,
        /** 当前请求关联的模型供应商类型。 */
        val provider: LLMProvider
    )

    /**
     * 大模型生成结果的目标输出形态。
     */
    private sealed class GenerationOutput {
        /** 创建一条新消息并写入指定 source */
        data class Create(
            /** 产生当前数据的来源。 */
            val source: ChatMessage.Source
        ) : GenerationOutput()
        /** 更新覆盖已有的消息记录（如重新生成） */
        data class Update(
            /** 当前操作关联的消息 ID。 */
            val messageId: Long
        ) : GenerationOutput()
    }

    /**
     * 获取当前生成目标对应的消息来源。
     */
    private fun GenerationOutput.source(): ChatMessage.Source {
        return when (this) {
            is GenerationOutput.Create -> source
            is GenerationOutput.Update -> ChatMessage.Source.Char
        }
    }

    /**
     * 流式生成初始化时绑定的正则脚本与宏快照。
     */
    private data class StreamingRegexContext(
        /** 当前页面或流程可使用的正则脚本列表。 */
        val scripts: List<ScopedRegexScript> = emptyList(),
        /** 当前正则执行允许展开的宏变量映射。 */
        val macros: Map<String, String> = emptyMap()
    )

    /**
     * 当前正在进行的流式生成状态快照。
     *
     * @property token 唯一令牌，用于防止多任务并发时的快照竞态
     * @property sessionId 会话 ID
     * @property output 生成目标形态
     * @property messageId 数据库中对应的占位消息 ID
     * @property createdPlaceholder 是否为本次生成新创建了占位记录
     * @property content 当前已累积接收到的原始文本
     * @property regexScripts 冻结的正则脚本列表
     * @property regexMacros 冻结的正则宏映射
     * @property worldInfoStateJson 世界书时序状态快照
     */
    private data class ActiveStreamingGeneration(
        /** 用于识别并取消当前生成任务的唯一令牌。 */
        val token: Any,
        /** 当前操作关联的会话 ID。 */
        val sessionId: Long,
        /** 当前流式生成累计得到的正文。 */
        val output: GenerationOutput,
        /** 当前操作关联的消息 ID。 */
        val messageId: Long?,
        /** 本次流式生成是否已经创建待写回的占位记录。 */
        val createdPlaceholder: Boolean,
        /** 当前对象承载的正文内容。 */
        val content: String,
        /** 当前对象关联或允许执行的正则脚本列表。 */
        val regexScripts: List<ScopedRegexScript>,
        /** 流式生成完成时执行 Source 正则所需的宏映射。 */
        val regexMacros: Map<String, String>,
        /** 序列化后的世界书时序状态，需要随会话或故事持久化。 */
        val worldInfoStateJson: String
    )
}

/** 将公共模型配置引导转换为单聊页面的对话框状态。 */
private fun ModelSettingsGuideContent.toChatDialogState(): ChatDialogState.ModelSettingsGuide {
    return ChatDialogState.ModelSettingsGuide(title = title, message = message)
}

/** 将单聊消息来源映射为 Regex 流水线需要的有限来源集合。 */
private fun ChatMessage.Source.toRegexMessageSource(): RegexMessageSource {
    return if (this == ChatMessage.Source.User) {
        RegexMessageSource.User
    } else {
        RegexMessageSource.Character
    }
}
