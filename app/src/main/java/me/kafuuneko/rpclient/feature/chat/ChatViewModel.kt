package me.kafuuneko.rpclient.feature.chat

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.chat.model.ChatGenerationState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatDialogState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLoadState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatPage
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiIntent
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatViewEvent
import me.kafuuneko.rpclient.feature.chat.utils.ChatLorebookEntryData
import me.kafuuneko.rpclient.feature.chat.utils.replaceStreamingMessage
import me.kafuuneko.rpclient.feature.chat.utils.toChatCharacterItem
import me.kafuuneko.rpclient.feature.chat.utils.toChatLorebookGroupItems
import me.kafuuneko.rpclient.feature.chat.utils.toChatMessageItems
import me.kafuuneko.rpclient.feature.chat.utils.toChatSessionItem
import me.kafuuneko.rpclient.feature.characteredit.CharacterEditActivity
import me.kafuuneko.rpclient.feature.worldbooklist.WorldBookListActivity
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.prompt.ChatPromptBuilder
import me.kafuuneko.rpclient.libs.prompt.PromptBuildContext
import me.kafuuneko.rpclient.libs.prompt.PromptGenerationMode
import me.kafuuneko.rpclient.libs.prompt.PromptInspection
import me.kafuuneko.rpclient.libs.prompt.SummaryPromptBuilder
import me.kafuuneko.rpclient.libs.prompt.summarySafeContent
import me.kafuuneko.rpclient.libs.regex.RegexExecutionMode
import me.kafuuneko.rpclient.libs.regex.RegexPlacement
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
import me.kafuuneko.rpclient.libs.utils.toggle
import me.kafuuneko.rpclient.libs.utils.toggleAll
import me.kafuuneko.rpclient.libs.utils.toDefaultChatTitle
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 单角色聊天页的状态持有者。
 *
 * 负责会话加载、消息持久化、Prompt 构建、流式生成、Regex 处理和自动总结。
 * 生成期间的可变成员只用于跨流式回调保存一次请求的上下文，结束或取消时必须统一清理。
 */
class ChatViewModel : CoreViewModelWithEvent<ChatUiIntent, ChatUiState>(
    ChatUiState.None
), KoinComponent {
    private val mChatRepository by inject<ChatRepository>()
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mLLMRepository by inject<LLMRepository>()
    private val mFileRepository by inject<FileRepository>()
    private val mChatPromptBuilder by inject<ChatPromptBuilder>()
    private val mSummaryPromptBuilder by inject<SummaryPromptBuilder>()
    private val mRegexRepository by inject<RegexScriptRepository>()
    private val mRegexRuntime by inject<RegexScriptRuntime>()
    private val mContext by inject<Context>()

    /** 当前页面绑定的会话 ID，初始化成功后在页面生命周期内保持不变。 */
    private var mSessionId: Long? = null
    /** 当前模型生成任务，用于阻止并发生成和响应停止操作。 */
    private var mGenerationJob: Job? = null
    /** 后台自动总结任务，与正文生成分开取消和收尾。 */
    private var mSummaryJob: Job? = null
    /** 流式占位消息及已接收内容，用于增量落库和异常恢复。 */
    private var mStreamingMessageId: Long? = null
    private var mStreamingContent: String = ""
    private var mStreamingCreatedMessage: Boolean = false
    /** 当前生成模式的输出目标，例如新消息、续写或重新生成。 */
    private var mStreamingOutput: GenerationOutput? = null
    /** 本次生成固定使用的脚本与宏快照，避免流中途配置变化导致结果不一致。 */
    private var mStreamingRegexScripts: List<ScopedRegexScript> = emptyList()
    private var mStreamingRegexMacros: Map<String, String> = emptyMap()
    /** 最近一次实际发送请求的检查报告，供调试对话框读取。 */
    private var mLastPromptInspection: PromptInspection? = null

    /**
     * 初始化真实会话数据。
     *
     * 新建会话进入 Chat 页时，如果数据库中还没有消息且携带开场白，则在这里将开场白落库为角色消息。
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

    @UiIntentObserver(ChatUiIntent.Resume::class)
    private suspend fun onResume() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        val refreshed = withContext(Dispatchers.IO) {
            loadNormalState(
                sessionId = sessionId,
                inputDraft = uiState.inputDraft,
                page = uiState.page,
                isExpanded = uiState.isSessionLoreExpanded,
                loadState = uiState.loadState,
                generationState = uiState.generationState,
                expandedThinkBlockIds = uiState.expandedThinkBlockIds,
                editingMessageId = uiState.editingMessageId,
                editingMessageDraft = uiState.editingMessageDraft,
                dialogState = uiState.dialogState
            )
        }
        if (refreshed == null) {
            mGenerationJob?.cancel()
            ChatUiState.finished(uiStateFlow.value).setup()
            return
        }
        refreshed.setup()
    }

    @UiIntentObserver(ChatUiIntent.Back::class)
    private fun onBack() {
        val uiState = getOrNull<ChatUiState.Normal>()
        if (uiState?.page == ChatPage.Settings) {
            uiState.copy(page = ChatPage.Conversation).setup()
            return
        }
        mGenerationJob?.cancel()
        ChatUiState.finished(uiStateFlow.value).setup()
    }

    @UiIntentObserver(ChatUiIntent.ChangeInputDraft::class)
    private suspend fun onChangeInputDraft(intent: ChatUiIntent.ChangeInputDraft) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(inputDraft = intent.value).setup()
    }

    @UiIntentObserver(ChatUiIntent.ChangeLorebookQuery::class)
    private fun onChangeLorebookQuery(intent: ChatUiIntent.ChangeLorebookQuery) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(lorebookQuery = intent.value).setup()
    }

    @UiIntentObserver(ChatUiIntent.SendMessage::class)
    private suspend fun onSendMessage() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        val rawInput = uiState.inputDraft.trim()
            .ifBlank { AppModel.replaceEmptyMessagePrompt.trim() }
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
                val input = withContext(Dispatchers.IO) {
                    applyUserRegex(sessionId, rawInput)
                }
                withContext(Dispatchers.IO) {
                    mChatRepository.createMessage(sessionId, ChatMessage.Source.User, input)
                }
                refreshUiState(
                    sessionId = sessionId,
                    inputDraft = "",
                    isExpanded = uiState.isSessionLoreExpanded,
                    generationState = ChatGenerationState.Requesting
                )
                val built = withContext(Dispatchers.IO) { buildGenerationRequest(sessionId) }
                recordPromptInspection(built.inspection)
                if (AppModel.streamEnabled) {
                    generateStreaming(
                        sessionId,
                        built.request,
                        GenerationOutput.Create(ChatMessage.Source.Char),
                        built.worldInfoStateJson
                    )
                } else {
                    generateOnce(
                        sessionId,
                        built.request,
                        GenerationOutput.Create(ChatMessage.Source.Char),
                        built.worldInfoStateJson
                    )
                }
                maybeAutoSummarize(sessionId)
            }.onFailure { throwable ->
                if (throwable is CancellationException) return@onFailure
                refreshUiState(
                    sessionId = sessionId,
                    inputDraft = "",
                    isExpanded = uiState.isSessionLoreExpanded,
                    generationState = ChatGenerationState.Failed(throwable.message ?: mContext.getString(R.string.generation_failed))
                )
                AppViewEvent.PopupToastMessage(throwable.message ?: mContext.getString(R.string.generation_failed)).tryEmit()
            }
        }
    }

    @UiIntentObserver(ChatUiIntent.StopGeneration::class)
    private suspend fun onStopGeneration() {
        val sessionId = mSessionId ?: return
        val job = mGenerationJob ?: return
        if (!job.isActive) return
        job.cancel()
        val messageId = mStreamingMessageId
        val content = mStreamingContent
        val output = mStreamingOutput
        // 用户停止生成时，已经收到的流式片段仍然是有效剧情内容，需要写回当前 assistant 消息。
        withContext(Dispatchers.IO) {
            val processedContent = content.takeIf { it.isNotBlank() }
                ?.let { applyGeneratedRegex(sessionId, it, output) }
                .orEmpty()
            if (messageId != null && processedContent.isNotBlank()) {
                mChatRepository.updateMessageContent(messageId, processedContent)
            } else if (messageId != null && mStreamingCreatedMessage) {
                mChatRepository.deleteMessage(messageId)
            } else if (messageId == null && processedContent.isNotBlank()) {
                mChatRepository.createMessage(
                    sessionId,
                    ChatMessage.Source.Char,
                    processedContent
                )
            }
        }
        mStreamingMessageId = null
        mStreamingContent = ""
        mStreamingCreatedMessage = false
        mStreamingOutput = null
        mStreamingRegexScripts = emptyList()
        mStreamingRegexMacros = emptyMap()
        refreshUiState(sessionId = sessionId, generationState = ChatGenerationState.Idle)
    }

    @UiIntentObserver(ChatUiIntent.RegenerateLast::class)
    private suspend fun onRegenerateLast() {
        val sessionId = mSessionId ?: return
        regenerateLastAssistantMessage(sessionId)
    }

    @UiIntentObserver(ChatUiIntent.ContinueLast::class)
    private suspend fun onContinueLast() {
        val sessionId = mSessionId ?: return
        continueLastAssistantMessage(sessionId)
    }

    @UiIntentObserver(ChatUiIntent.ImpersonateUser::class)
    private suspend fun onImpersonateUser() {
        val sessionId = mSessionId ?: return
        generateUserImpersonation(sessionId)
    }

    @UiIntentObserver(ChatUiIntent.RegenerateFromMessage::class)
    private suspend fun onRegenerateFromMessage(intent: ChatUiIntent.RegenerateFromMessage) {
        val sessionId = mSessionId ?: return
        val messageId = intent.messageId.toLongOrNull() ?: return
        val latestAssistantMessage = withContext(Dispatchers.IO) {
            mChatRepository.getMessagesBySessionId(sessionId).lastOrNull { it.source == ChatMessage.Source.Char }
        }
        if (latestAssistantMessage?.id != messageId) {
            AppViewEvent.PopupToastMessageByResId(R.string.only_latest_assistant_reply_regenerate).tryEmit()
            return
        }
        regenerateLastAssistantMessage(sessionId)
    }

    @UiIntentObserver(ChatUiIntent.BranchFromMessage::class)
    private suspend fun onBranchFromMessage(intent: ChatUiIntent.BranchFromMessage) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        val messageId = intent.messageId.toLongOrNull() ?: return
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.generation_already_running).tryEmit()
            return
        }
        uiState.copy(loadState = ChatLoadState.Saving).setup()
        val branchCreateTime = System.currentTimeMillis()
        val branchId = withContext(Dispatchers.IO) {
            mChatRepository.createBranchSession(
                sourceSessionId = sessionId,
                throughMessageId = messageId,
                title = branchCreateTime.toDefaultChatTitle(),
                createTime = branchCreateTime
            )
        }
        if (branchId == 0L) {
            AppViewEvent.PopupToastMessageByResId(R.string.branch_create_failed).tryEmit()
            refreshUiState(sessionId = sessionId)
            return
        }
        ChatViewEvent.OpenSession(branchId.toString()).emit()
    }


    @UiIntentObserver(ChatUiIntent.OpenSessionLore::class)
    private fun onOpenSessionLore() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(isSessionLoreExpanded = !uiState.isSessionLoreExpanded).setup()
    }

    @UiIntentObserver(ChatUiIntent.ToggleSessionLoreEntry::class)
    private suspend fun onToggleSessionLoreEntry(intent: ChatUiIntent.ToggleSessionLoreEntry) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        if (uiState.lorebookGroups.none { group -> group.entries.any { it.id == intent.entryId } }) return
        val enabledIds = uiState.session.enabledLorebookEntryIds.toggle(intent.entryId)
        saveSessionLorebookEntryIds(sessionId, enabledIds)
        refreshUiState(
            sessionId = sessionId,
            inputDraft = uiState.inputDraft,
            isExpanded = uiState.isSessionLoreExpanded,
            generationState = uiState.generationState
        )
    }

    @UiIntentObserver(ChatUiIntent.ToggleSessionLorebook::class)
    private suspend fun onToggleSessionLorebook(intent: ChatUiIntent.ToggleSessionLorebook) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        val group = uiState.lorebookGroups.firstOrNull { it.lorebookId == intent.lorebookId } ?: return
        val entryIds = group.entries.map { it.id }.toSet()
        if (entryIds.isEmpty()) return
        val enabledIds = uiState.session.enabledLorebookEntryIds.toggleAll(entryIds)
        saveSessionLorebookEntryIds(sessionId, enabledIds)
        refreshUiState(
            sessionId = sessionId,
            inputDraft = uiState.inputDraft,
            isExpanded = uiState.isSessionLoreExpanded,
            generationState = uiState.generationState
        )
    }

    @UiIntentObserver(ChatUiIntent.OpenChatSettings::class)
    private fun onOpenChatSettings() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(page = ChatPage.Settings).setup()
    }

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

    @UiIntentObserver(ChatUiIntent.CloseChatSettings::class)
    private fun onCloseChatSettings() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(page = ChatPage.Conversation).setup()
    }

    @UiIntentObserver(ChatUiIntent.SummarizeNow::class)
    private suspend fun onSummarizeNow() {
        val sessionId = mSessionId ?: return
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.stop_generation_before_summarizing).tryEmit()
            return
        }
        launchSummaryJob(sessionId, showToast = true)
    }

    @UiIntentObserver(ChatUiIntent.RestorePreviousSummary::class)
    private suspend fun onRestorePreviousSummary() {
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

    @UiIntentObserver(ChatUiIntent.ToggleAutoSummaryPaused::class)
    private suspend fun onToggleAutoSummaryPaused(
        intent: ChatUiIntent.ToggleAutoSummaryPaused
    ) {
        val sessionId = mSessionId ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.updateAutoSummaryPaused(sessionId, intent.paused)
        }
        refreshUiState(sessionId = sessionId, page = ChatPage.Settings)
    }

    @UiIntentObserver(ChatUiIntent.CancelSummary::class)
    private fun onCancelSummary() {
        mSummaryJob?.cancel()
    }

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

    @UiIntentObserver(ChatUiIntent.ConfirmDeleteSession::class)
    private suspend fun onConfirmDeleteSession() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.stop_generation_before_deleting).tryEmit()
            uiState.copy(dialogState = ChatDialogState.None).setup()
            return
        }
        uiState.copy(
            loadState = ChatLoadState.Deleting,
            dialogState = ChatDialogState.None
        ).setup()
        withContext(Dispatchers.IO) {
            mChatRepository.deleteSession(sessionId)
        }
        AppViewEvent.PopupToastMessageByResId(R.string.chat_deleted).tryEmit()
        ChatUiState.finished(uiStateFlow.value).setup()
    }

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

    @UiIntentObserver(ChatUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(dialogState = ChatDialogState.None).setup()
    }

    @UiIntentObserver(ChatUiIntent.SaveTitle::class)
    private suspend fun onSaveTitle(intent: ChatUiIntent.SaveTitle) {
        val sessionId = mSessionId ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.updateSessionTitle(sessionId, intent.value.trim().ifBlank { mContext.getString(R.string.untitled_chat) })
        }
        refreshUiState(sessionId = sessionId)
    }

    @UiIntentObserver(ChatUiIntent.SaveSummary::class)
    private suspend fun onSaveSummary(intent: ChatUiIntent.SaveSummary) {
        val sessionId = mSessionId ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.updateCurrentSummary(sessionId, intent.value)
        }
        refreshUiState(sessionId = sessionId)
    }

    @UiIntentObserver(ChatUiIntent.SaveUserNote::class)
    private suspend fun onSaveUserNote(intent: ChatUiIntent.SaveUserNote) {
        val sessionId = mSessionId ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.updateSessionUserNote(sessionId, intent.value)
        }
        refreshUiState(sessionId = sessionId)
    }

    @UiIntentObserver(ChatUiIntent.OpenWorldBookManager::class)
    private fun onOpenWorldBookManager() {
        if (!isStateOf<ChatUiState.Normal>()) return
        AppViewEvent.StartActivity(WorldBookListActivity::class.java).tryEmit()
    }

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
     * 保存当前会话的用户名称。
     *
     * 空白名称统一保存为默认值 `You`，避免生成 prompt 和消息署名出现空名称。
     */
    @UiIntentObserver(ChatUiIntent.SaveUserName::class)
    private suspend fun onSaveUserName(intent: ChatUiIntent.SaveUserName) {
        val sessionId = mSessionId ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.updateSessionUserName(sessionId, intent.value.trim().ifBlank { "You" })
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
        val sessionId = mSessionId ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.updateSessionUserDescription(sessionId, intent.value.trim())
        }
        refreshUiState(sessionId = sessionId)
    }

    @UiIntentObserver(ChatUiIntent.SaveCreatorNotes::class)
    private suspend fun onSaveCreatorNotes(intent: ChatUiIntent.SaveCreatorNotes) {
        val sessionId = mSessionId ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.updateSessionCreatorNotes(sessionId, intent.value)
        }
        refreshUiState(sessionId = sessionId)
    }

    @UiIntentObserver(ChatUiIntent.CopyMessage::class)
    private suspend fun onCopyMessage(intent: ChatUiIntent.CopyMessage) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val message = uiState.messages.firstOrNull { it.id == intent.messageId } ?: return
        if (message.content.isBlank()) return
        ChatViewEvent.CopyText(message.content).emit()
    }

    @UiIntentObserver(ChatUiIntent.StartEditMessage::class)
    private suspend fun onStartEditMessage(intent: ChatUiIntent.StartEditMessage) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val message = uiState.messages.firstOrNull { it.id == intent.messageId } ?: return
        if (message.isStreaming) return
        val rawContent = withContext(Dispatchers.IO) {
            val sessionId = mSessionId ?: return@withContext null
            mChatRepository.getMessagesBySessionId(sessionId)
                .firstOrNull { it.id.toString() == intent.messageId }
                ?.content
        } ?: return
        uiState.copy(
            editingMessageId = message.id,
            editingMessageDraft = rawContent
        ).setup()
    }

    @UiIntentObserver(ChatUiIntent.ChangeEditingMessageDraft::class)
    private fun onChangeEditingMessageDraft(intent: ChatUiIntent.ChangeEditingMessageDraft) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        if (uiState.editingMessageId == null) return
        uiState.copy(editingMessageDraft = intent.value).setup()
    }

    @UiIntentObserver(ChatUiIntent.SaveEditingMessage::class)
    private suspend fun onSaveEditingMessage() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        val messageId = uiState.editingMessageId?.toLongOrNull() ?: return
        withContext(Dispatchers.IO) {
            val message = mChatRepository.getMessagesBySessionId(sessionId)
                .firstOrNull { it.id == messageId } ?: return@withContext
            val content = when (message.source) {
                ChatMessage.Source.User -> applyUserRegex(
                    sessionId,
                    uiState.editingMessageDraft,
                    isEdit = true
                )
                ChatMessage.Source.Char -> applyAiRegex(
                    sessionId,
                    uiState.editingMessageDraft,
                    isEdit = true
                )
                ChatMessage.Source.System,
                ChatMessage.Source.Summary -> uiState.editingMessageDraft
            }
            mChatRepository.updateMessageContent(messageId, content)
        }
        refreshUiState(
            sessionId = sessionId,
            inputDraft = uiState.inputDraft,
            isExpanded = uiState.isSessionLoreExpanded,
            generationState = uiState.generationState,
            expandedThinkBlockIds = uiState.expandedThinkBlockIds,
            editingMessageId = null,
            editingMessageDraft = ""
        )
    }

    @UiIntentObserver(ChatUiIntent.CancelEditingMessage::class)
    private fun onCancelEditingMessage() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(
            editingMessageId = null,
            editingMessageDraft = ""
        ).setup()
    }

    @UiIntentObserver(ChatUiIntent.ToggleThinkBlock::class)
    private fun onToggleThinkBlock(intent: ChatUiIntent.ToggleThinkBlock) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val ids = uiState.expandedThinkBlockIds.toMutableSet()
        if (!ids.add(intent.blockId)) {
            ids.remove(intent.blockId)
        }
        uiState.copy(expandedThinkBlockIds = ids).setup()
    }

    private suspend fun regenerateLastAssistantMessage(sessionId: Long) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(page = ChatPage.Conversation).setup()
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.generation_already_running).tryEmit()
            return
        }
        // 当前数据结构尚未支持 swipe/branch，只允许重生成最后一条角色回复，避免破坏中间历史。
        val messages = withContext(Dispatchers.IO) {
            mChatRepository.getMessagesBySessionId(sessionId)
        }
        val latestAssistantMessage = messages.lastOrNull().takeIf { it?.source == ChatMessage.Source.Char }
        if (latestAssistantMessage == null) {
            AppViewEvent.PopupToastMessageByResId(R.string.no_latest_assistant_reply_to_regenerate).tryEmit()
            return
        }
        if (messages.size == 1) {
            AppViewEvent.PopupToastMessageByResId(R.string.cannot_regenerate_only_first_message).tryEmit()
            return
        }
        mGenerationJob = viewModelScope.launch {
            runCatching {
                refreshUiState(
                    sessionId = sessionId,
                    inputDraft = uiState.inputDraft,
                    page = ChatPage.Conversation,
                    isExpanded = uiState.isSessionLoreExpanded,
                    generationState = ChatGenerationState.Requesting,
                    expandedThinkBlockIds = uiState.expandedThinkBlockIds
                )
                val built = withContext(Dispatchers.IO) {
                    buildGenerationRequest(
                        sessionId = sessionId,
                        generationMode = PromptGenerationMode.Regenerate,
                        excludedMessageId = latestAssistantMessage.id
                    )
                }
                recordPromptInspection(built.inspection)
                if (AppModel.streamEnabled) {
                    generateStreaming(
                        sessionId,
                        built.request,
                        GenerationOutput.Update(latestAssistantMessage.id),
                        built.worldInfoStateJson
                    )
                } else {
                    generateOnce(
                        sessionId,
                        built.request,
                        GenerationOutput.Update(latestAssistantMessage.id),
                        built.worldInfoStateJson
                    )
                }
                maybeAutoSummarize(sessionId)
            }.onFailure { throwable ->
                if (throwable is CancellationException) return@onFailure
                AppViewEvent.PopupToastMessage(throwable.message ?: mContext.getString(R.string.regenerate_failed)).tryEmit()
                refreshUiState(sessionId = sessionId, generationState = ChatGenerationState.Failed(throwable.message ?: mContext.getString(R.string.regenerate_failed)))
            }
        }
    }

    private suspend fun continueLastAssistantMessage(sessionId: Long) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(page = ChatPage.Conversation).setup()
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.generation_already_running).tryEmit()
            return
        }
        val latestMessage = withContext(Dispatchers.IO) {
            mChatRepository.getMessagesBySessionId(sessionId).lastOrNull()
        }
        if (latestMessage == null || (latestMessage.source != ChatMessage.Source.User && latestMessage.source != ChatMessage.Source.Char)) {
            AppViewEvent.PopupToastMessageByResId(R.string.no_latest_assistant_reply_to_continue).tryEmit()
            return
        }
        val isLastUser = latestMessage.source == ChatMessage.Source.User
        mGenerationJob = viewModelScope.launch {
            runCatching {
                refreshUiState(
                    sessionId = sessionId,
                    inputDraft = uiState.inputDraft,
                    isExpanded = uiState.isSessionLoreExpanded,
                    generationState = ChatGenerationState.Requesting,
                    expandedThinkBlockIds = uiState.expandedThinkBlockIds
                )
                val generationMode = if (isLastUser) PromptGenerationMode.Normal else PromptGenerationMode.Continue
                val built = withContext(Dispatchers.IO) {
                    buildGenerationRequest(sessionId, generationMode)
                }
                recordPromptInspection(built.inspection)
                if (AppModel.streamEnabled) {
                    generateStreaming(
                        sessionId,
                        built.request,
                        GenerationOutput.Create(ChatMessage.Source.Char),
                        built.worldInfoStateJson
                    )
                } else {
                    generateOnce(
                        sessionId,
                        built.request,
                        GenerationOutput.Create(ChatMessage.Source.Char),
                        built.worldInfoStateJson
                    )
                }
                maybeAutoSummarize(sessionId)
            }.onFailure { throwable ->
                if (throwable is CancellationException) return@onFailure
                val errorResId = if (isLastUser) R.string.generation_failed else R.string.continue_generation_failed
                AppViewEvent.PopupToastMessage(throwable.message ?: mContext.getString(errorResId)).tryEmit()
                refreshUiState(sessionId = sessionId, generationState = ChatGenerationState.Failed(throwable.message ?: mContext.getString(errorResId)))
            }
        }
    }

    private suspend fun generateUserImpersonation(sessionId: Long) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(page = ChatPage.Conversation).setup()
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.generation_already_running).tryEmit()
            return
        }
        mGenerationJob = viewModelScope.launch {
            runCatching {
                refreshUiState(
                    sessionId = sessionId,
                    inputDraft = uiState.inputDraft,
                    page = ChatPage.Conversation,
                    isExpanded = uiState.isSessionLoreExpanded,
                    generationState = ChatGenerationState.Requesting,
                    expandedThinkBlockIds = uiState.expandedThinkBlockIds
                )
                val built = withContext(Dispatchers.IO) {
                    buildGenerationRequest(sessionId, PromptGenerationMode.Impersonate)
                }
                recordPromptInspection(built.inspection)
                if (AppModel.streamEnabled) {
                    generateStreaming(
                        sessionId,
                        built.request,
                        GenerationOutput.Create(ChatMessage.Source.User),
                        built.worldInfoStateJson
                    )
                } else {
                    generateOnce(
                        sessionId,
                        built.request,
                        GenerationOutput.Create(ChatMessage.Source.User),
                        built.worldInfoStateJson
                    )
                }
                maybeAutoSummarize(sessionId)
            }.onFailure { throwable ->
                if (throwable is CancellationException) return@onFailure
                AppViewEvent.PopupToastMessage(throwable.message ?: mContext.getString(R.string.impersonation_failed)).tryEmit()
                refreshUiState(sessionId = sessionId, generationState = ChatGenerationState.Failed(throwable.message ?: mContext.getString(R.string.impersonation_failed)))
            }
        }
    }

    private suspend fun generateOnce(
        sessionId: Long,
        request: LLMGenerationRequest,
        output: GenerationOutput,
        worldInfoStateJson: String
    ) {
        val response = withContext(Dispatchers.IO) {
            mLLMRepository.generateWithSelectedProvider(request)
        }
        val processedContent = withContext(Dispatchers.IO) {
            applyGeneratedRegex(sessionId, response.content, output)
        }
        if (processedContent.isBlank()) {
            refreshUiState(sessionId = sessionId, generationState = ChatGenerationState.Idle)
            return
        }
        withContext(Dispatchers.IO) {
            when (output) {
                is GenerationOutput.Create -> {
                    mChatRepository.createMessage(sessionId, output.source, processedContent)
                }
                is GenerationOutput.Update -> {
                    mChatRepository.updateMessageContent(output.messageId, processedContent)
                    mChatRepository.updateSessionLatestTime(sessionId)
                }
            }
            mChatRepository.updateSessionWorldInfoState(sessionId, worldInfoStateJson)
        }
        refreshUiState(sessionId = sessionId, generationState = ChatGenerationState.Idle)
    }

    private suspend fun generateStreaming(
        sessionId: Long,
        request: LLMGenerationRequest,
        output: GenerationOutput,
        worldInfoStateJson: String
    ) {
        mStreamingOutput = output
        withContext(Dispatchers.IO) {
            val session = mChatRepository.getSessionById(sessionId)
            val character = session?.let {
                mCharacterRepository.getCharacterById(it.characterId)
            }
            if (session != null && character != null) {
                mStreamingRegexScripts = mRegexRepository.activeScripts(listOf(character))
                mStreamingRegexMacros = RegexScriptRuntime.macros(
                    session.userName,
                    character.name,
                    session.userDescription,
                    character.scenario
                )
            }
        }
        // 先插入空 assistant 消息作为流式占位，后续 delta 只更新 UI，完成或停止时再持久化完整内容。
        when (output) {
            is GenerationOutput.Create -> {
                mStreamingMessageId = withContext(Dispatchers.IO) {
                    mChatRepository.createMessage(sessionId, output.source, "")
                }
                mStreamingContent = ""
                mStreamingCreatedMessage = true
            }
            is GenerationOutput.Update -> {
                mStreamingMessageId = output.messageId
                mStreamingContent = ""
                mStreamingCreatedMessage = false
            }
        }
        refreshUiState(
            sessionId = sessionId,
            generationState = ChatGenerationState.Streaming(mStreamingMessageId, mStreamingContent)
        )
        try {
            mLLMRepository.streamGenerateWithSelectedProvider(request).collect { event ->
                currentCoroutineContext().ensureActive()
                when (event) {
                    is LLMStreamEvent.Delta -> {
                        mStreamingContent += event.content
                        val displayContent = applyStreamingDisplayRegex(
                            mStreamingContent,
                            output
                        )
                        val uiState = getOrNull<ChatUiState.Normal>() ?: return@collect
                        uiState.copy(
                            generationState = ChatGenerationState.Streaming(mStreamingMessageId, mStreamingContent),
                            messages = uiState.messages.replaceStreamingMessage(
                                mStreamingMessageId,
                                displayContent
                            )
                        ).setup()
                    }
                    is LLMStreamEvent.Finished -> Unit
                }
            }
            val finalContent = withContext(Dispatchers.IO) {
                applyGeneratedRegex(sessionId, mStreamingContent, output)
            }
            val messageId = mStreamingMessageId
            withContext(Dispatchers.IO) {
                if (messageId != null && finalContent.isNotBlank()) {
                    mChatRepository.updateMessageContent(messageId, finalContent)
                    mChatRepository.updateSessionLatestTime(sessionId)
                    mChatRepository.updateSessionWorldInfoState(sessionId, worldInfoStateJson)
                } else if (messageId != null && mStreamingCreatedMessage) {
                    mChatRepository.deleteMessage(messageId)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val messageId = mStreamingMessageId
            val partialContent = withContext(Dispatchers.IO) {
                mStreamingContent.takeIf { it.isNotBlank() }
                    ?.let { applyGeneratedRegex(sessionId, it, output) }
                    .orEmpty()
            }
            withContext(Dispatchers.IO) {
                if (messageId != null && partialContent.isNotBlank()) {
                    mChatRepository.updateMessageContent(messageId, partialContent)
                    mChatRepository.updateSessionLatestTime(sessionId)
                } else if (messageId != null && mStreamingCreatedMessage) {
                    mChatRepository.deleteMessage(messageId)
                }
            }
            throw e
        } finally {
            mStreamingMessageId = null
            mStreamingContent = ""
            mStreamingCreatedMessage = false
            mStreamingOutput = null
            mStreamingRegexScripts = emptyList()
            mStreamingRegexMacros = emptyMap()
        }
        refreshUiState(sessionId = sessionId, generationState = ChatGenerationState.Idle)
    }

    private fun launchSummaryJob(sessionId: Long, showToast: Boolean): Job {
        mSummaryJob?.cancel()
        val job = viewModelScope.launch {
            try {
                summarizeSession(sessionId, showToast)
            } finally {
                withContext(NonCancellable) {
                    val currentState = getOrNull<ChatUiState.Normal>()
                    if (currentState != null && currentState.dialogState is ChatDialogState.Summarizing) {
                        refreshUiState(
                            sessionId = sessionId,
                            inputDraft = currentState.inputDraft,
                            isExpanded = currentState.isSessionLoreExpanded,
                            expandedThinkBlockIds = currentState.expandedThinkBlockIds,
                            dialogState = ChatDialogState.None
                        )
                    }
                }
            }
        }
        mSummaryJob = job
        return job
    }

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

    private suspend fun summarizeSession(sessionId: Long, showToast: Boolean) {
        runCatching {
            val data = withContext(Dispatchers.IO) {
                val session = mChatRepository.getSessionById(sessionId) ?: return@withContext null
                val character = mCharacterRepository.getCharacterById(session.characterId) ?: return@withContext null
                val summaryContext = mChatRepository.getSummaryGenerationContext(
                    sessionId = sessionId,
                    allowRefreshLatest = showToast
                )
                val provider = mLLMRepository.getSelectedProvider()
                AutoSummaryData(
                    session = session,
                    character = character,
                    summary = summaryContext.existingSummary,
                    messages = summaryContext.messages,
                    summaryIdToUpdate = summaryContext.summaryToUpdate?.id,
                    provider = provider
                )
            } ?: return
            if (data.messages.isEmpty()) {
                if (showToast) AppViewEvent.PopupToastMessageByResId(R.string.no_unsummarized_messages).tryEmit()
                return
            }
            if (!showToast && data.messages.size < AppModel.summaryTriggerMessageCount) return

            val uiState = getOrNull<ChatUiState.Normal>() ?: return
            uiState.copy(dialogState = ChatDialogState.Summarizing).setup()

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

            val response = withContext(Dispatchers.IO) {
                mLLMRepository.generateWithSelectedProvider(built.request)
            }
            val summaryContent = response.content.summarySafeContent()
            if (summaryContent.isBlank()) {
                error(mContext.getString(R.string.summary_failed))
            }

            currentCoroutineContext().ensureActive()

            withContext(Dispatchers.IO) {
                mChatRepository.saveSummary(
                    sessionId = sessionId,
                    content = summaryContent,
                    coveredMessageId = built.selectedMessages.last().id,
                    summaryIdToUpdate = data.summaryIdToUpdate
                )
            }
            if (showToast) AppViewEvent.PopupToastMessageByResId(R.string.summary_updated).tryEmit()
        }.onFailure {
            if (it is CancellationException) {
                throw it
            }
            AppViewEvent.PopupToastMessage(it.message ?: mContext.getString(R.string.summary_failed)).tryEmit()
        }
    }

    private suspend fun buildGenerationRequest(
        sessionId: Long,
        generationMode: PromptGenerationMode = PromptGenerationMode.Normal,
        excludedMessageId: Long? = null
    ): BuiltGenerationRequest {
        // ViewModel 只收集构建 prompt 所需的领域数据，具体排序、宏替换和预算裁剪交给 libs/prompt。
        val session = mChatRepository.getSessionById(sessionId) ?: error(mContext.getString(R.string.session_not_found))
        val character = mCharacterRepository.getCharacterById(session.characterId) ?: error(mContext.getString(R.string.character_not_found))
        val summaryContext = mChatRepository.getSummaryContext(sessionId)
        val generationHistory = if (
            excludedMessageId != null &&
            summaryContext.summary?.coveredMessageId == excludedMessageId &&
            summaryContext.messagesAfterSummary.isEmpty()
        ) {
            val regenerationContext = mChatRepository.getSummaryGenerationContext(
                sessionId = sessionId,
                allowRefreshLatest = true
            )
            GenerationHistory(
                summary = regenerationContext.existingSummary,
                messages = regenerationContext.messages.filterNot { it.id == excludedMessageId },
                totalMessageCount = (summaryContext.totalMessageCount - 1).coerceAtLeast(0)
            )
        } else {
            GenerationHistory(
                summary = summaryContext.summary?.content.orEmpty(),
                messages = summaryContext.messagesAfterSummary.filterNot { it.id == excludedMessageId },
                totalMessageCount = (
                    summaryContext.totalMessageCount - if (excludedMessageId == null) 0 else 1
                ).coerceAtLeast(0)
            )
        }
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
        val provider = mLLMRepository.getSelectedProvider() ?: error(mContext.getString(R.string.no_enabled_llm_provider_configured))
        val buildResult = mChatPromptBuilder.buildWithMetadata(
            PromptBuildContext(
                userName = session.userName,
                userDescription = session.userDescription,
                character = character,
                session = session.copy(creatorNotes = mChatRepository.getSessionCreatorNotes(session)),
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
                regexScripts = mRegexRepository.activeScripts(listOf(character))
            )
        )
        return BuiltGenerationRequest(
            request = buildResult.request,
            inspection = buildResult.inspection,
            worldInfoStateJson = buildResult.worldInfoStateJson
        )
    }

    private fun recordPromptInspection(inspection: PromptInspection) {
        mLastPromptInspection = inspection
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(hasPromptInspection = true).setup()
        if (inspection.hasOmissions) {
            AppViewEvent.PopupToastMessageByResId(R.string.prompt_trimmed_warning).tryEmit()
        }
    }

    private suspend fun loadNormalState(
        sessionId: Long,
        inputDraft: String = "",
        page: ChatPage = ChatPage.Conversation,
        isExpanded: Boolean = false,
        lorebookQuery: String = "",
        loadState: ChatLoadState = ChatLoadState.None,
        generationState: ChatGenerationState = ChatGenerationState.Idle,
        expandedThinkBlockIds: Set<String> = emptySet(),
        editingMessageId: String? = null,
        editingMessageDraft: String = "",
        dialogState: ChatDialogState = ChatDialogState.None
    ): ChatUiState.Normal? {
        // 所有 UI model 在 ViewModel 中组装，Compose 只负责渲染和发送 intent。
        val session = mChatRepository.getSessionById(sessionId) ?: return null
        val character = mCharacterRepository.getCharacterById(session.characterId) ?: return null
        val messages = mChatRepository.getMessagesBySessionId(sessionId)
        val regexScripts = mRegexRepository.activeScripts(listOf(character))
        val regexMacros = RegexScriptRuntime.macros(
            userName = session.userName,
            characterName = character.name,
            userDescription = session.userDescription,
            scenario = character.scenario
        )
        val displayMessages = messages.mapIndexed { index, message ->
            val depth = messages.lastIndex - index
            val result = when (message.source) {
                ChatMessage.Source.User -> mRegexRuntime.executeDisplayMessage(
                    message.content,
                    regexScripts,
                    regexMacros,
                    depth,
                    RegexPlacement.UserInput
                )
                ChatMessage.Source.Char -> mRegexRuntime.executeDisplayMessage(
                    message.content,
                    regexScripts,
                    regexMacros,
                    depth
                )
                ChatMessage.Source.System,
                ChatMessage.Source.Summary -> null
            }
            if (result == null) message else message.copy(content = result.text)
        }
        val summary = mChatRepository.getLatestSummary(sessionId)?.content.orEmpty()
        val lorebookData = getAllLorebookEntries()
        val enabledIds = mChatRepository.getSessionLorebookEntryIds(session).toSet()
        val effectiveCreatorNotes = mChatRepository.getSessionCreatorNotes(session)
        val avatarFilePath = character.avatar.takeIf { it.isNotBlank() }?.let {
            mFileRepository.getFile(it)?.absolutePath
        }
        return ChatUiState.Normal(
            page = page,
            loadState = loadState,
            session = session.toChatSessionItem(
                summary = summary,
                creatorNotes = effectiveCreatorNotes,
                messageCount = messages.size,
                enabledIds = enabledIds
            ),
            character = character.toChatCharacterItem(avatarFilePath),
            messages = displayMessages.toChatMessageItems(
                characterName = character.name,
                userName = session.userName,
                systemSpeaker = mContext.getString(R.string.system_speaker),
                streamingMessageId = mStreamingMessageId
            ),
            lorebookGroups = lorebookData.toChatLorebookGroupItems(
                enabledIds = enabledIds,
                unknownLorebookName = mContext.getString(R.string.unknown_lorebook)
            ),
            lorebookQuery = lorebookQuery,
            isSessionLoreExpanded = isExpanded,
            inputDraft = inputDraft,
            generationState = generationState,
            streamEnabled = AppModel.streamEnabled,
            hasPromptInspection = mLastPromptInspection != null,
            expandedThinkBlockIds = expandedThinkBlockIds,
            editingMessageId = editingMessageId,
            editingMessageDraft = editingMessageDraft,
            dialogState = dialogState
        )
    }

    private suspend fun refreshUiState(
        sessionId: Long,
        inputDraft: String = getOrNull<ChatUiState.Normal>()?.inputDraft.orEmpty(),
        page: ChatPage = getOrNull<ChatUiState.Normal>()?.page ?: ChatPage.Conversation,
        isExpanded: Boolean = getOrNull<ChatUiState.Normal>()?.isSessionLoreExpanded ?: false,
        lorebookQuery: String = getOrNull<ChatUiState.Normal>()?.lorebookQuery.orEmpty(),
        loadState: ChatLoadState = ChatLoadState.None,
        generationState: ChatGenerationState = getOrNull<ChatUiState.Normal>()?.generationState ?: ChatGenerationState.Idle,
        expandedThinkBlockIds: Set<String> = getOrNull<ChatUiState.Normal>()?.expandedThinkBlockIds ?: emptySet(),
        editingMessageId: String? = getOrNull<ChatUiState.Normal>()?.editingMessageId,
        editingMessageDraft: String = getOrNull<ChatUiState.Normal>()?.editingMessageDraft.orEmpty(),
        dialogState: ChatDialogState = getOrNull<ChatUiState.Normal>()?.dialogState ?: ChatDialogState.None
    ) {
        val nextState = withContext(Dispatchers.IO) {
            loadNormalState(
                sessionId = sessionId,
                inputDraft = inputDraft,
                page = page,
                isExpanded = isExpanded,
                lorebookQuery = lorebookQuery,
                loadState = loadState,
                generationState = generationState,
                expandedThinkBlockIds = expandedThinkBlockIds,
                editingMessageId = editingMessageId,
                editingMessageDraft = editingMessageDraft,
                dialogState = dialogState
            )
        } ?: return
        nextState.setup()
    }

    private suspend fun saveSessionLorebookEntryIds(
        sessionId: Long,
        enabledIds: Set<Long>
    ) {
        withContext(Dispatchers.IO) {
            mChatRepository.updateSessionLorebookEntryIds(sessionId, enabledIds.toList())
        }
    }

    private suspend fun getAllLorebookEntries(): ChatLorebookEntryData {
        val lorebooks = mLorebookRepository.getAllLorebooks()
        val entries = lorebooks.flatMap { mLorebookRepository.getEntriesByLorebookId(it.id) }
        return ChatLorebookEntryData(lorebooks.associateBy { it.id }, entries)
    }

    private fun finishWithToast(messageResId: Int) {
        AppViewEvent.PopupToastMessageByResId(messageResId).tryEmit()
        ChatUiState.finished(uiStateFlow.value).setup()
    }

    private data class BuiltGenerationRequest(
        val request: LLMGenerationRequest,
        val inspection: PromptInspection,
        val worldInfoStateJson: String
    )

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
        val slashProcessed = if (input.startsWith('/')) {
            mRegexRuntime.execute(
                input,
                scripts,
                RegexPlacement.SlashCommand,
                RegexExecutionMode.Source,
                macros,
                isEdit = isEdit
            ).text
        } else {
            input
        }
        return mRegexRuntime.execute(
            slashProcessed,
            scripts,
            RegexPlacement.UserInput,
            RegexExecutionMode.Source,
            macros,
            isEdit = isEdit
        ).text
    }

    private suspend fun applyAiRegex(
        sessionId: Long,
        input: String,
        isEdit: Boolean = false
    ): String {
        val session = mChatRepository.getSessionById(sessionId) ?: return input
        val character = mCharacterRepository.getCharacterById(session.characterId) ?: return input
        return mRegexRuntime.executeAiMessage(
            input = input,
            scripts = mRegexRepository.activeScripts(listOf(character)),
            mode = RegexExecutionMode.Source,
            macros = RegexScriptRuntime.macros(
                session.userName,
                character.name,
                session.userDescription,
                character.scenario
            ),
            isEdit = isEdit
        ).text
    }

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

    private fun applyStreamingDisplayRegex(
        input: String,
        output: GenerationOutput
    ): String {
        return if (output is GenerationOutput.Create && output.source == ChatMessage.Source.User) {
            mRegexRuntime.executeDisplayMessage(
                input,
                mStreamingRegexScripts,
                mStreamingRegexMacros,
                bodyPlacement = RegexPlacement.UserInput
            ).text
        } else {
            mRegexRuntime.executeDisplayMessage(
                input,
                mStreamingRegexScripts,
                mStreamingRegexMacros
            ).text
        }
    }

    private data class AutoSummaryData(
        val session: ChatSession,
        val character: Character,
        val summary: String,
        val messages: List<ChatMessage>,
        val summaryIdToUpdate: Long?,
        val provider: LLMProvider?
    )

    private data class GenerationHistory(
        val summary: String,
        val messages: List<ChatMessage>,
        val totalMessageCount: Int
    )

    private sealed class GenerationOutput {
        data class Create(val source: ChatMessage.Source) : GenerationOutput()
        data class Update(val messageId: Long) : GenerationOutput()
    }
}
