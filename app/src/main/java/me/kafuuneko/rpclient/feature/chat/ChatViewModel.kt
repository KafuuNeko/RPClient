package me.kafuuneko.rpclient.feature.chat

import android.content.Context
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
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.prompt.ChatPromptBuilder
import me.kafuuneko.rpclient.libs.prompt.PromptBuildContext
import me.kafuuneko.rpclient.libs.prompt.PromptGenerationMode
import me.kafuuneko.rpclient.libs.prompt.SummaryPromptBuilder
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
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

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
    private val mContext by inject<Context>()

    private var mSessionId: Long? = null
    private var mGenerationJob: Job? = null
    private var mSummaryJob: Job? = null
    private var mStreamingMessageId: Long? = null
    private var mStreamingContent: String = ""
    private var mStreamingCreatedMessage: Boolean = false

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
        refreshUiState(
            sessionId = sessionId,
            inputDraft = uiState.inputDraft,
            isExpanded = uiState.isSessionLoreExpanded,
            generationState = uiState.generationState,
            expandedThinkBlockIds = uiState.expandedThinkBlockIds,
            editingMessageId = uiState.editingMessageId,
            editingMessageDraft = uiState.editingMessageDraft
        )
    }

    @UiIntentObserver(ChatUiIntent.Back::class)
    private fun onBack() {
        val uiState = getOrNull<ChatUiState.Normal>()
        if (uiState?.page == ChatPage.Settings) {
            uiState.copy(page = ChatPage.Conversation).setup()
            return
        }
        mGenerationJob?.cancel()
        ChatUiState.Finished.setup()
    }

    @UiIntentObserver(ChatUiIntent.ChangeInputDraft::class)
    private suspend fun onChangeInputDraft(intent: ChatUiIntent.ChangeInputDraft) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(inputDraft = intent.value).setup()
    }

    @UiIntentObserver(ChatUiIntent.SendMessage::class)
    private suspend fun onSendMessage() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val sessionId = mSessionId ?: return
        val input = uiState.inputDraft.trim()
            .ifBlank { AppModel.replaceEmptyMessagePrompt.trim() }
        if (input.isBlank()) {
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
                withContext(Dispatchers.IO) {
                    mChatRepository.createMessage(sessionId, ChatMessage.Source.User, input)
                }
                refreshUiState(
                    sessionId = sessionId,
                    inputDraft = "",
                    isExpanded = uiState.isSessionLoreExpanded,
                    generationState = ChatGenerationState.Requesting
                )
                val request = withContext(Dispatchers.IO) { buildGenerationRequest(sessionId) }
                if (AppModel.streamEnabled) {
                    generateStreaming(sessionId, request, GenerationOutput.Create(ChatMessage.Source.Char))
                } else {
                    generateOnce(sessionId, request, GenerationOutput.Create(ChatMessage.Source.Char))
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
        // 用户停止生成时，已经收到的流式片段仍然是有效剧情内容，需要写回当前 assistant 消息。
        withContext(Dispatchers.IO) {
            if (messageId != null && content.isNotBlank()) {
                mChatRepository.updateMessageContent(messageId, content)
            } else if (messageId != null && mStreamingCreatedMessage) {
                mChatRepository.deleteMessage(messageId)
            } else if (content.isNotBlank()) {
                mChatRepository.createMessage(sessionId, ChatMessage.Source.Char, content)
            }
        }
        mStreamingMessageId = null
        mStreamingContent = ""
        mStreamingCreatedMessage = false
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
        val branchId = withContext(Dispatchers.IO) {
            mChatRepository.createBranchSession(
                sourceSessionId = sessionId,
                throughMessageId = messageId,
                title = mContext.getString(R.string.branch_session_title, uiState.session.title.ifBlank { mContext.getString(R.string.untitled_chat) })
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
        ChatUiState.Finished.setup()
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
    private fun onStartEditMessage(intent: ChatUiIntent.StartEditMessage) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        val message = uiState.messages.firstOrNull { it.id == intent.messageId } ?: return
        if (message.isStreaming) return
        uiState.copy(
            editingMessageId = message.id,
            editingMessageDraft = message.content
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
            mChatRepository.updateMessageContent(messageId, uiState.editingMessageDraft)
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
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(R.string.generation_already_running).tryEmit()
            return
        }
        // 当前数据结构尚未支持 swipe/branch，只允许重生成最后一条角色回复，避免破坏中间历史。
        val latestAssistantMessage = withContext(Dispatchers.IO) {
            val messages = mChatRepository.getMessagesBySessionId(sessionId)
            messages.lastOrNull().takeIf { it?.source == ChatMessage.Source.Char }
        }
        if (latestAssistantMessage == null) {
            AppViewEvent.PopupToastMessageByResId(R.string.no_latest_assistant_reply_to_regenerate).tryEmit()
            return
        }
        mGenerationJob = viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    mChatRepository.deleteMessage(latestAssistantMessage.id)
                }
                refreshUiState(
                    sessionId = sessionId,
                    inputDraft = uiState.inputDraft,
                    isExpanded = uiState.isSessionLoreExpanded,
                    generationState = ChatGenerationState.Requesting,
                    expandedThinkBlockIds = uiState.expandedThinkBlockIds
                )
                val request = withContext(Dispatchers.IO) { buildGenerationRequest(sessionId) }
                if (AppModel.streamEnabled) {
                    generateStreaming(sessionId, request, GenerationOutput.Create(ChatMessage.Source.Char))
                } else {
                    generateOnce(sessionId, request, GenerationOutput.Create(ChatMessage.Source.Char))
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
                val request = withContext(Dispatchers.IO) {
                    buildGenerationRequest(sessionId, generationMode)
                }
                if (AppModel.streamEnabled) {
                    generateStreaming(sessionId, request, GenerationOutput.Create(ChatMessage.Source.Char))
                } else {
                    generateOnce(sessionId, request, GenerationOutput.Create(ChatMessage.Source.Char))
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
                val request = withContext(Dispatchers.IO) {
                    buildGenerationRequest(sessionId, PromptGenerationMode.Impersonate)
                }
                if (AppModel.streamEnabled) {
                    generateStreaming(sessionId, request, GenerationOutput.Create(ChatMessage.Source.User))
                } else {
                    generateOnce(sessionId, request, GenerationOutput.Create(ChatMessage.Source.User))
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
        output: GenerationOutput
    ) {
        val response = withContext(Dispatchers.IO) {
            mLLMRepository.generateWithSelectedProvider(request)
        }
        withContext(Dispatchers.IO) {
            when (output) {
                is GenerationOutput.Create -> {
                    mChatRepository.createMessage(sessionId, output.source, response.content)
                }
            }
        }
        refreshUiState(sessionId = sessionId, generationState = ChatGenerationState.Idle)
    }

    private suspend fun generateStreaming(
        sessionId: Long,
        request: LLMGenerationRequest,
        output: GenerationOutput
    ) {
        // 先插入空 assistant 消息作为流式占位，后续 delta 只更新 UI，完成或停止时再持久化完整内容。
        when (output) {
            is GenerationOutput.Create -> {
                mStreamingMessageId = withContext(Dispatchers.IO) {
                    mChatRepository.createMessage(sessionId, output.source, "")
                }
                mStreamingContent = ""
                mStreamingCreatedMessage = true
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
                        val uiState = getOrNull<ChatUiState.Normal>() ?: return@collect
                        uiState.copy(
                            generationState = ChatGenerationState.Streaming(mStreamingMessageId, mStreamingContent),
                            messages = uiState.messages.replaceStreamingMessage(mStreamingMessageId, mStreamingContent)
                        ).setup()
                    }
                    is LLMStreamEvent.Finished -> Unit
                }
            }
            val finalContent = mStreamingContent
            val messageId = mStreamingMessageId
            withContext(Dispatchers.IO) {
                if (messageId != null) {
                    mChatRepository.updateMessageContent(messageId, finalContent)
                    mChatRepository.updateSessionLatestTime(sessionId)
                }
            }
        } catch (e: Exception) {
            val messageId = mStreamingMessageId
            if (messageId != null && mStreamingCreatedMessage) {
                withContext(Dispatchers.IO) {
                    mChatRepository.deleteMessage(messageId)
                }
            }
            throw e
        } finally {
            mStreamingMessageId = null
            mStreamingContent = ""
            mStreamingCreatedMessage = false
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

            val selectedMessages = mSummaryPromptBuilder.selectMessagesToSummarize(data.messages, data.provider)
            if (selectedMessages.isEmpty()) return
            val request = mSummaryPromptBuilder.build(
                userName = AppModel.userName,
                userDescription = AppModel.userDescription,
                character = data.character,
                session = data.session,
                existingSummary = data.summary,
                messages = selectedMessages,
                provider = data.provider
            )

            currentCoroutineContext().ensureActive()

            val response = withContext(Dispatchers.IO) {
                mLLMRepository.generateWithSelectedProvider(request)
            }

            currentCoroutineContext().ensureActive()

            withContext(Dispatchers.IO) {
                mChatRepository.saveSummary(
                    sessionId = sessionId,
                    content = response.content,
                    coveredMessageId = selectedMessages.last().id,
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
        generationMode: PromptGenerationMode = PromptGenerationMode.Normal
    ): LLMGenerationRequest {
        // ViewModel 只收集构建 prompt 所需的领域数据，具体排序、宏替换和预算裁剪交给 libs/prompt。
        val session = mChatRepository.getSessionById(sessionId) ?: error(mContext.getString(R.string.session_not_found))
        val character = mCharacterRepository.getCharacterById(session.characterId) ?: error(mContext.getString(R.string.character_not_found))
        val summaryContext = mChatRepository.getSummaryContext(sessionId)
        val enabledIds = mChatRepository.getSessionLorebookEntryIds(session).toSet()
        val lorebookData = getAllLorebookEntries()
        val allLorebookEntries = lorebookData.entries
        val lorebookEntries = (allLorebookEntries.filter { it.id in enabledIds } +
            allLorebookEntries.filter { character.characterLorebookId != 0L && it.lorebookId == character.characterLorebookId })
            .distinctBy { it.id }
        val activeLorebookIds = lorebookEntries.map { it.lorebookId }.toSet()
        val recursiveLorebookIds = lorebookData.lorebooks.values
            .filter { it.id in activeLorebookIds && it.recursiveScanning }
            .map { it.id }
            .toSet()
        val provider = mLLMRepository.getSelectedProvider() ?: error(mContext.getString(R.string.no_enabled_llm_provider_configured))
        val buildResult = mChatPromptBuilder.buildWithMetadata(
            PromptBuildContext(
                userName = AppModel.userName,
                userDescription = AppModel.userDescription,
                character = character,
                session = session.copy(creatorNotes = mChatRepository.getSessionCreatorNotes(session)),
                summary = summaryContext.summary?.content.orEmpty(),
                messages = summaryContext.messagesAfterSummary,
                currentUserMessage = null,
                totalMessageCount = summaryContext.totalMessageCount,
                candidateLorebookEntries = lorebookEntries,
                recursiveScanningLorebookIds = recursiveLorebookIds,
                provider = provider,
                maxContextTokens = provider.contextTokens,
                maxResponseTokens = provider.maxTokens,
                generationMode = generationMode
            )
        )
        if (buildResult.worldInfoStateJson != session.worldInfoStateJson) {
            mChatRepository.updateSessionWorldInfoState(session.id, buildResult.worldInfoStateJson)
        }
        return buildResult.request
    }

    private suspend fun loadNormalState(
        sessionId: Long,
        inputDraft: String = "",
        page: ChatPage = ChatPage.Conversation,
        isExpanded: Boolean = false,
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
            messages = messages.toChatMessageItems(
                characterName = character.name,
                userName = AppModel.userName,
                systemSpeaker = mContext.getString(R.string.system_speaker),
                streamingMessageId = mStreamingMessageId
            ),
            lorebookGroups = lorebookData.toChatLorebookGroupItems(
                enabledIds = enabledIds,
                unknownLorebookName = mContext.getString(R.string.unknown_lorebook)
            ),
            isSessionLoreExpanded = isExpanded,
            inputDraft = inputDraft,
            generationState = generationState,
            streamEnabled = AppModel.streamEnabled,
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
        ChatUiState.Finished.setup()
    }

    private data class AutoSummaryData(
        val session: ChatSession,
        val character: Character,
        val summary: String,
        val messages: List<ChatMessage>,
        val summaryIdToUpdate: Long?,
        val provider: LLMProvider?
    )

    private sealed class GenerationOutput {
        data class Create(val source: ChatMessage.Source) : GenerationOutput()
    }
}
