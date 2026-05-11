package me.kafuuneko.rpclient.feature.chat

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.feature.chat.model.ChatCharacterItem
import me.kafuuneko.rpclient.feature.chat.model.ChatGenerationState
import me.kafuuneko.rpclient.feature.chat.model.ChatLorebookEntryItem
import me.kafuuneko.rpclient.feature.chat.model.ChatMessageUiModel
import me.kafuuneko.rpclient.feature.chat.model.ChatSessionItem
import me.kafuuneko.rpclient.feature.chat.model.MessageRole
import me.kafuuneko.rpclient.feature.chat.presentation.ChatDialogState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLoadState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatPage
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiIntent
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiState
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.prompt.ChatPromptBuilder
import me.kafuuneko.rpclient.libs.prompt.PromptBuildContext
import me.kafuuneko.rpclient.libs.prompt.SummaryPromptBuilder
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatViewModel : CoreViewModelWithEvent<ChatUiIntent, ChatUiState>(
    ChatUiState.None
), KoinComponent {
    private val mChatRepository by inject<ChatRepository>()
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mLLMRepository by inject<LLMRepository>()
    private val mChatPromptBuilder = ChatPromptBuilder()
    private val mSummaryPromptBuilder = SummaryPromptBuilder()

    private var mSessionId: Long? = null
    private var mGenerationJob: Job? = null
    private var mStreamingMessageId: Long? = null
    private var mStreamingContent: String = ""

    @UiIntentObserver(ChatUiIntent.Init::class)
    private suspend fun onInit(intent: ChatUiIntent.Init) {
        if (!isStateOf<ChatUiState.None>()) return
        val sessionId = intent.sessionId?.toLongOrNull()
        if (sessionId == null) {
            finishWithToast("Invalid session id")
            return
        }
        mSessionId = sessionId
        val loaded = withContext(Dispatchers.IO) {
            loadNormalState(sessionId, firstMessage = intent.firstMessage)
        }
        if (loaded == null) {
            finishWithToast("Session not found")
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
            dialogState = uiState.dialogState,
            generationState = uiState.generationState,
            expandedThinkBlockIds = uiState.expandedThinkBlockIds
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
        if (input.isBlank()) return
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessage("Generation is already running").tryEmit()
            return
        }

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
                    generateStreaming(sessionId, request)
                } else {
                    generateOnce(sessionId, request)
                }
                maybeAutoSummarize(sessionId)
            }.onFailure { throwable ->
                if (throwable is CancellationException) return@onFailure
                refreshUiState(
                    sessionId = sessionId,
                    inputDraft = "",
                    isExpanded = uiState.isSessionLoreExpanded,
                    generationState = ChatGenerationState.Failed(throwable.message ?: "Generation failed")
                )
                AppViewEvent.PopupToastMessage(throwable.message ?: "Generation failed").tryEmit()
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
        withContext(Dispatchers.IO) {
            if (messageId != null && content.isNotBlank()) {
                mChatRepository.updateMessageContent(messageId, content)
            } else if (content.isNotBlank()) {
                mChatRepository.createMessage(sessionId, ChatMessage.Source.Char, content)
            }
        }
        mStreamingMessageId = null
        mStreamingContent = ""
        refreshUiState(sessionId = sessionId, generationState = ChatGenerationState.Idle)
    }

    @UiIntentObserver(ChatUiIntent.RegenerateLast::class)
    private suspend fun onRegenerateLast() {
        val sessionId = mSessionId ?: return
        regenerateLastAssistantMessage(sessionId)
    }

    @UiIntentObserver(ChatUiIntent.RegenerateFromMessage::class)
    private suspend fun onRegenerateFromMessage(intent: ChatUiIntent.RegenerateFromMessage) {
        val sessionId = mSessionId ?: return
        val messageId = intent.messageId.toLongOrNull() ?: return
        val latestAssistantMessage = withContext(Dispatchers.IO) {
            mChatRepository.getMessagesBySessionId(sessionId).lastOrNull { it.source == ChatMessage.Source.Char }
        }
        if (latestAssistantMessage?.id != messageId) {
            AppViewEvent.PopupToastMessage("Only the latest assistant reply can be regenerated for now").tryEmit()
            return
        }
        regenerateLastAssistantMessage(sessionId)
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
        val enabledIds = uiState.session.enabledLorebookEntryIds.toMutableSet()
        if (!enabledIds.add(intent.entryId)) {
            enabledIds.remove(intent.entryId)
        }
        withContext(Dispatchers.IO) {
            mChatRepository.updateSessionLorebookEntryIds(sessionId, enabledIds.toList())
        }
        refreshUiState(
            sessionId = sessionId,
            inputDraft = uiState.inputDraft,
            isExpanded = uiState.isSessionLoreExpanded,
            dialogState = uiState.dialogState,
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

    @UiIntentObserver(ChatUiIntent.EditTitleClick::class)
    private fun onEditTitleClick() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(dialogState = ChatDialogState.EditTitle(uiState.session.title)).setup()
    }

    @UiIntentObserver(ChatUiIntent.EditSummaryClick::class)
    private fun onEditSummaryClick() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(dialogState = ChatDialogState.EditSummary(uiState.session.summarize)).setup()
    }

    @UiIntentObserver(ChatUiIntent.EditUserNoteClick::class)
    private fun onEditUserNoteClick() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(dialogState = ChatDialogState.EditUserNote(uiState.session.userNote)).setup()
    }

    @UiIntentObserver(ChatUiIntent.EditCreatorNotesClick::class)
    private fun onEditCreatorNotesClick() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(dialogState = ChatDialogState.EditCreatorNotes(uiState.session.creatorNotes)).setup()
    }

    @UiIntentObserver(ChatUiIntent.SummarizeNow::class)
    private suspend fun onSummarizeNow() {
        val sessionId = mSessionId ?: return
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessage("Please stop generation before summarizing").tryEmit()
            return
        }
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(loadState = ChatLoadState.Saving, dialogState = ChatDialogState.None).setup()
        summarizeSession(sessionId, showToast = true)
        refreshUiState(
            sessionId = sessionId,
            inputDraft = uiState.inputDraft,
            isExpanded = uiState.isSessionLoreExpanded,
            expandedThinkBlockIds = uiState.expandedThinkBlockIds
        )
    }

    @UiIntentObserver(ChatUiIntent.SaveTitle::class)
    private suspend fun onSaveTitle(intent: ChatUiIntent.SaveTitle) {
        val sessionId = mSessionId ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.updateSessionTitle(sessionId, intent.value.trim().ifBlank { "Untitled chat" })
        }
        refreshUiState(sessionId = sessionId, dialogState = ChatDialogState.None)
    }

    @UiIntentObserver(ChatUiIntent.SaveSummary::class)
    private suspend fun onSaveSummary(intent: ChatUiIntent.SaveSummary) {
        val sessionId = mSessionId ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.updateSessionSummarize(sessionId, intent.value)
        }
        refreshUiState(sessionId = sessionId, dialogState = ChatDialogState.None)
    }

    @UiIntentObserver(ChatUiIntent.SaveUserNote::class)
    private suspend fun onSaveUserNote(intent: ChatUiIntent.SaveUserNote) {
        val sessionId = mSessionId ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.updateSessionUserNote(sessionId, intent.value)
        }
        refreshUiState(sessionId = sessionId, dialogState = ChatDialogState.None)
    }

    @UiIntentObserver(ChatUiIntent.SaveCreatorNotes::class)
    private suspend fun onSaveCreatorNotes(intent: ChatUiIntent.SaveCreatorNotes) {
        val sessionId = mSessionId ?: return
        withContext(Dispatchers.IO) {
            mChatRepository.updateSessionCreatorNotes(sessionId, intent.value)
        }
        refreshUiState(sessionId = sessionId, dialogState = ChatDialogState.None)
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

    @UiIntentObserver(ChatUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        uiState.copy(dialogState = ChatDialogState.None).setup()
    }

    private suspend fun regenerateLastAssistantMessage(sessionId: Long) {
        val uiState = getOrNull<ChatUiState.Normal>() ?: return
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessage("Generation is already running").tryEmit()
            return
        }
        val latestAssistantMessage = withContext(Dispatchers.IO) {
            val messages = mChatRepository.getMessagesBySessionId(sessionId)
            messages.lastOrNull().takeIf { it?.source == ChatMessage.Source.Char }
        }
        if (latestAssistantMessage == null) {
            AppViewEvent.PopupToastMessage("No latest assistant reply to regenerate").tryEmit()
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
                    generateStreaming(sessionId, request)
                } else {
                    generateOnce(sessionId, request)
                }
                maybeAutoSummarize(sessionId)
            }.onFailure { throwable ->
                if (throwable is CancellationException) return@onFailure
                AppViewEvent.PopupToastMessage(throwable.message ?: "Regenerate failed").tryEmit()
                refreshUiState(sessionId = sessionId, generationState = ChatGenerationState.Failed(throwable.message ?: "Regenerate failed"))
            }
        }
    }

    private suspend fun generateOnce(
        sessionId: Long,
        request: me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
    ) {
        val response = withContext(Dispatchers.IO) {
            mLLMRepository.generateWithSelectedProvider(request)
        }
        withContext(Dispatchers.IO) {
            mChatRepository.createMessage(sessionId, ChatMessage.Source.Char, response.content)
        }
        refreshUiState(sessionId = sessionId, generationState = ChatGenerationState.Idle)
    }

    private suspend fun generateStreaming(
        sessionId: Long,
        request: me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
    ) {
        mStreamingMessageId = withContext(Dispatchers.IO) {
            mChatRepository.createMessage(sessionId, ChatMessage.Source.Char, "")
        }
        mStreamingContent = ""
        refreshUiState(
            sessionId = sessionId,
            generationState = ChatGenerationState.Streaming(mStreamingMessageId, "")
        )
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
            }
        }
        mStreamingMessageId = null
        mStreamingContent = ""
        refreshUiState(sessionId = sessionId, generationState = ChatGenerationState.Idle)
    }

    private suspend fun maybeAutoSummarize(sessionId: Long) {
        if (!AppModel.autoSummaryEnabled) return
        summarizeSession(sessionId, showToast = false)
    }

    private suspend fun summarizeSession(sessionId: Long, showToast: Boolean) {
        runCatching {
            val data = withContext(Dispatchers.IO) {
                val session = mChatRepository.getSessionById(sessionId) ?: return@withContext null
                val character = mCharacterRepository.getCharacterById(session.characterId) ?: return@withContext null
                val messages = mChatRepository.getUnsummarizedMessagesBySessionId(sessionId)
                val provider = mLLMRepository.getSelectedProvider()
                AutoSummaryData(session, character, messages, provider)
            } ?: return
            if (data.messages.isEmpty()) {
                if (showToast) AppViewEvent.PopupToastMessage("No unsummarized messages").tryEmit()
                return
            }
            if (!showToast && data.messages.size < AppModel.summaryTriggerMessageCount) return
            val request = mSummaryPromptBuilder.build(
                userName = AppModel.userName,
                character = data.character,
                session = data.session,
                messages = data.messages,
                provider = data.provider
            )
            val summarizedIds = mSummaryPromptBuilder.selectSummarizedMessageIds(data.messages, data.provider)
            if (summarizedIds.isEmpty()) return
            val response = withContext(Dispatchers.IO) {
                mLLMRepository.generateWithSelectedProvider(request)
            }
            withContext(Dispatchers.IO) {
                mChatRepository.saveSessionSummarize(
                    sessionId = sessionId,
                    summarize = response.content,
                    summarizedMessageIds = summarizedIds
                )
            }
            if (showToast) AppViewEvent.PopupToastMessage("Summary updated").tryEmit()
        }.onFailure {
            AppViewEvent.PopupToastMessage(it.message ?: "Summary failed").tryEmit()
        }
    }

    private suspend fun buildGenerationRequest(sessionId: Long): me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest {
        val session = mChatRepository.getSessionById(sessionId) ?: error("Session not found")
        val character = mCharacterRepository.getCharacterById(session.characterId) ?: error("Character not found")
        val messages = mChatRepository.getMessagesBySessionId(sessionId)
        val enabledIds = mChatRepository.getSessionLorebookEntryIds(session).toSet()
        val lorebookEntries = getAllLorebookEntries().entries.filter { it.id in enabledIds }
        val provider = mLLMRepository.getSelectedProvider() ?: error("No enabled LLM provider configured")
        return mChatPromptBuilder.build(
            PromptBuildContext(
                userName = AppModel.userName,
                character = character,
                session = session.copy(creatorNotes = mChatRepository.getSessionCreatorNotes(session)),
                messages = messages,
                currentUserMessage = null,
                candidateLorebookEntries = lorebookEntries,
                provider = provider,
                maxContextTokens = provider.contextTokens,
                maxResponseTokens = provider.maxTokens
            )
        )
    }

    private suspend fun loadNormalState(
        sessionId: Long,
        firstMessage: String? = null,
        inputDraft: String = "",
        page: ChatPage = ChatPage.Conversation,
        isExpanded: Boolean = false,
        loadState: ChatLoadState = ChatLoadState.None,
        dialogState: ChatDialogState = ChatDialogState.None,
        generationState: ChatGenerationState = ChatGenerationState.Idle,
        expandedThinkBlockIds: Set<String> = emptySet()
    ): ChatUiState.Normal? {
        val session = mChatRepository.getSessionById(sessionId) ?: return null
        val character = mCharacterRepository.getCharacterById(session.characterId) ?: return null
        val existingMessages = mChatRepository.getMessagesBySessionId(sessionId)
        if (existingMessages.isEmpty() && !firstMessage.isNullOrBlank()) {
            mChatRepository.createMessage(sessionId, ChatMessage.Source.Char, firstMessage)
        }
        val messages = mChatRepository.getMessagesBySessionId(sessionId)
        val lorebookData = getAllLorebookEntries()
        val enabledIds = mChatRepository.getSessionLorebookEntryIds(session).toSet()
        val effectiveCreatorNotes = mChatRepository.getSessionCreatorNotes(session)
        return ChatUiState.Normal(
            page = page,
            loadState = loadState,
            dialogState = dialogState,
            session = session.toUiModel(
                creatorNotes = effectiveCreatorNotes,
                messageCount = messages.size,
                enabledIds = enabledIds
            ),
            character = character.toUiModel(),
            messages = messages.toUiModels(character.name),
            lorebookEntries = lorebookData.toUiModels(enabledIds),
            isSessionLoreExpanded = isExpanded,
            inputDraft = inputDraft,
            generationState = generationState,
            streamEnabled = AppModel.streamEnabled,
            expandedThinkBlockIds = expandedThinkBlockIds
        )
    }

    private suspend fun refreshUiState(
        sessionId: Long,
        inputDraft: String = getOrNull<ChatUiState.Normal>()?.inputDraft.orEmpty(),
        page: ChatPage = getOrNull<ChatUiState.Normal>()?.page ?: ChatPage.Conversation,
        isExpanded: Boolean = getOrNull<ChatUiState.Normal>()?.isSessionLoreExpanded ?: false,
        loadState: ChatLoadState = ChatLoadState.None,
        dialogState: ChatDialogState = getOrNull<ChatUiState.Normal>()?.dialogState ?: ChatDialogState.None,
        generationState: ChatGenerationState = getOrNull<ChatUiState.Normal>()?.generationState ?: ChatGenerationState.Idle,
        expandedThinkBlockIds: Set<String> = getOrNull<ChatUiState.Normal>()?.expandedThinkBlockIds ?: emptySet()
    ) {
        val nextState = withContext(Dispatchers.IO) {
            loadNormalState(
                sessionId = sessionId,
                inputDraft = inputDraft,
                page = page,
                isExpanded = isExpanded,
                loadState = loadState,
                dialogState = dialogState,
                generationState = generationState,
                expandedThinkBlockIds = expandedThinkBlockIds
            )
        } ?: return
        nextState.setup()
    }

    private suspend fun getAllLorebookEntries(): LorebookEntryData {
        val lorebooks = mLorebookRepository.getAllLorebooks()
        val entries = lorebooks.flatMap { mLorebookRepository.getEntriesByLorebookId(it.id) }
        return LorebookEntryData(lorebooks.associateBy { it.id }, entries)
    }

    private fun ChatSession.toUiModel(
        creatorNotes: String,
        messageCount: Int,
        enabledIds: Set<Long>
    ): ChatSessionItem {
        return ChatSessionItem(
            id = id,
            title = title,
            summarize = summarize,
            userNote = userNote,
            creatorNotes = creatorNotes,
            messageCount = messageCount,
            enabledLorebookEntryIds = enabledIds
        )
    }

    private fun Character.toUiModel(): ChatCharacterItem {
        return ChatCharacterItem(
            id = id,
            name = name,
            description = description,
            personality = personality,
            scenario = scenario,
            examplesOfDialogue = examplesOfDialogue,
            postHistoryInstructions = postHistoryInstructions,
            creatorNotes = creatorNotes,
            avatarText = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            accentColor = 0xFF315EFD
        )
    }

    private fun List<ChatMessage>.toUiModels(characterName: String): List<ChatMessageUiModel> {
        return map { message ->
            val role = when (message.source) {
                ChatMessage.Source.User -> MessageRole.User
                ChatMessage.Source.Char -> MessageRole.Assistant
                ChatMessage.Source.System -> MessageRole.Narrator
            }
            ChatMessageUiModel(
                id = message.id.toString(),
                role = role,
                speaker = when (message.source) {
                    ChatMessage.Source.User -> AppModel.userName
                    ChatMessage.Source.Char -> characterName
                    ChatMessage.Source.System -> "System"
                },
                content = message.content,
                time = message.createTime.toDisplayTime(),
                tokenCount = (message.content.length / 3).coerceAtLeast(1),
                isStreaming = message.id == mStreamingMessageId
            )
        }
    }

    private fun LorebookEntryData.toUiModels(enabledIds: Set<Long>): List<ChatLorebookEntryItem> {
        return entries.sortedWith(compareBy<LorebookEntry> { lorebooks[it.lorebookId]?.name.orEmpty() }.thenBy { it.order })
            .map { entry ->
                ChatLorebookEntryItem(
                    id = entry.id,
                    lorebookId = entry.lorebookId,
                    lorebookName = lorebooks[entry.lorebookId]?.name.orEmpty().ifBlank { "Unknown lorebook" },
                    name = entry.name,
                    keywords = entry.getKeywordList(),
                    secondaryKeywords = entry.getSecondaryKeywordList(),
                    order = entry.order,
                    depth = entry.depth,
                    content = entry.content,
                    enabled = entry.id in enabledIds
                )
            }
    }

    private fun List<ChatMessageUiModel>.replaceStreamingMessage(
        messageId: Long?,
        content: String
    ): List<ChatMessageUiModel> {
        if (messageId == null) return this
        return map {
            if (it.id == messageId.toString()) {
                it.copy(content = content, isStreaming = true)
            } else {
                it
            }
        }
    }

    private fun Long.toDisplayTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(this))
    }

    private fun finishWithToast(message: String) {
        AppViewEvent.PopupToastMessage(message).tryEmit()
        ChatUiState.Finished.setup()
    }

    private data class LorebookEntryData(
        val lorebooks: Map<Long, Lorebook>,
        val entries: List<LorebookEntry>
    )

    private data class AutoSummaryData(
        val session: ChatSession,
        val character: Character,
        val messages: List<ChatMessage>,
        val provider: LLMProvider?
    )
}
