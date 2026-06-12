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
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatGenerationState
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatAvailableCharacterItem
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatLorebookEntryItem
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatLorebookGroupItem
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatMemberItem
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatMessageItem
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatDialogState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatLoadState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatPage
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiIntent
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiState
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
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.prompt.PromptInspection
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.repository.GroupChatData
import me.kafuuneko.rpclient.libs.room.repository.GroupChatMemberData
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.libs.utils.formatTimestamp
import me.kafuuneko.rpclient.libs.utils.toggleAll
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GroupChatViewModel :
    CoreViewModelWithEvent<GroupChatUiIntent, GroupChatUiState>(
        GroupChatUiState.None
    ), KoinComponent {
    private val mGroupChatRepository by inject<GroupChatRepository>()
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mLLMRepository by inject<LLMRepository>()
    private val mPromptBuilder by inject<GroupChatPromptBuilder>()
    private val mSpeakerSelector by inject<GroupChatSpeakerSelector>()
    private val mSummaryPromptBuilder by inject<GroupChatSummaryPromptBuilder>()
    private val mOutputSanitizer by inject<GroupChatOutputSanitizer>()
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mContext by inject<Context>()

    private var mSessionId: Long? = null
    private var mGenerationJob: Job? = null
    private var mStreamingMessageId: Long? = null
    private var mStreamingContent: String = ""
    private var mLastPromptInspection: PromptInspection? = null

    @UiIntentObserver(GroupChatUiIntent.Init::class)
    private suspend fun onInit(intent: GroupChatUiIntent.Init) {
        if (!isStateOf<GroupChatUiState.None>()) return
        val sessionId = intent.sessionId?.toLongOrNull()
        if (sessionId == null) {
            finishWithToast(R.string.invalid_session_id)
            return
        }
        mSessionId = sessionId
        val state = withContext(Dispatchers.IO) {
            loadState(sessionId)
        }
        if (state == null) {
            finishWithToast(R.string.group_chat_not_found)
            return
        }
        state.setup()
    }

    @UiIntentObserver(GroupChatUiIntent.Resume::class)
    private suspend fun onResume() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        refreshState(
            inputDraft = uiState.inputDraft,
            selectedSpeakerId = uiState.selectedSpeakerId,
            generationState = uiState.generationState,
            editingMessageId = uiState.editingMessageId,
            editingMessageDraft = uiState.editingMessageDraft,
            dialogState = uiState.dialogState
        )
    }

    @UiIntentObserver(GroupChatUiIntent.Back::class)
    private suspend fun onBack() {
        val uiState = getOrNull<GroupChatUiState.Normal>()
        if (uiState?.page == GroupChatPage.Settings) {
            refreshState(page = GroupChatPage.Conversation)
            return
        }
        mGenerationJob?.cancel()
        persistOrDeleteStreamingMessage()
        GroupChatUiState.Finished.setup()
    }

    @UiIntentObserver(GroupChatUiIntent.OpenSettings::class)
    private fun onOpenSettings() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        if (mGenerationJob?.isActive == true) return
        uiState.copy(page = GroupChatPage.Settings).setup()
    }

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

    @UiIntentObserver(GroupChatUiIntent.CloseSettings::class)
    private suspend fun onCloseSettings() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        refreshState(
            page = GroupChatPage.Conversation,
            inputDraft = uiState.inputDraft,
            selectedSpeakerId = uiState.selectedSpeakerId
        )
    }

    @UiIntentObserver(GroupChatUiIntent.ChangeInputDraft::class)
    private fun onChangeInputDraft(intent: GroupChatUiIntent.ChangeInputDraft) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        uiState.copy(inputDraft = intent.value).setup()
    }

    @UiIntentObserver(GroupChatUiIntent.SelectSpeaker::class)
    private suspend fun onSelectSpeaker(intent: GroupChatUiIntent.SelectSpeaker) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        val member = uiState.members.firstOrNull { it.id == intent.characterId } ?: return
        if (uiState.activationStrategy == GroupChatSession.ActivationStrategy.Manual) {
            if (member.muted) return
            uiState.copy(selectedSpeakerId = intent.characterId).setup()
            return
        }
        if (mGenerationJob?.isActive == true) return
        val data = withContext(Dispatchers.IO) {
            mGroupChatRepository.getGroupChatData(uiState.sessionId)
        } ?: return
        val forcedSpeaker = data.members.firstOrNull {
            it.character.id == intent.characterId
        } ?: return
        launchGeneration(uiState.sessionId, listOf(forcedSpeaker))
    }

    @UiIntentObserver(GroupChatUiIntent.ToggleMemberMuted::class)
    private suspend fun onToggleMemberMuted(intent: GroupChatUiIntent.ToggleMemberMuted) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        if (mGenerationJob?.isActive == true) return
        val member = uiState.members.firstOrNull { it.id == intent.characterId } ?: return
        if (!member.muted && uiState.members.count { !it.muted } <= 1) {
            AppViewEvent.PopupToastMessageByResId(
                R.string.group_chat_keep_one_active_member
            ).tryEmit()
            return
        }
        withContext(Dispatchers.IO) {
            mGroupChatRepository.updateMemberMuted(
                sessionId = uiState.sessionId,
                characterId = member.id,
                muted = !member.muted
            )
        }
        refreshState(
            inputDraft = uiState.inputDraft,
            selectedSpeakerId = uiState.selectedSpeakerId
                ?.takeIf { it != member.id || member.muted }
        )
    }

    @UiIntentObserver(GroupChatUiIntent.SendMessage::class)
    private suspend fun onSendMessage() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        if (mGenerationJob?.isActive == true) {
            AppViewEvent.PopupToastMessageByResId(
                R.string.generation_already_running
            ).tryEmit()
            return
        }
        val sessionId = mSessionId ?: return
        val input = uiState.inputDraft.trim()
        val initialData = withContext(Dispatchers.IO) {
            mGroupChatRepository.getGroupChatData(sessionId)
        } ?: return
        val speakers = mSpeakerSelector.select(
            session = initialData.session,
            members = initialData.members,
            messages = initialData.messages,
            userInput = input,
            manualCharacterId = uiState.selectedSpeakerId
        )
        if (speakers.isEmpty()) {
            AppViewEvent.PopupToastMessageByResId(
                R.string.group_chat_select_speaker
            ).tryEmit()
            return
        }
        if (input.isNotBlank()) {
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
        refreshState(
            inputDraft = "",
            selectedSpeakerId = uiState.selectedSpeakerId,
            generationState = GroupChatGenerationState.Generating(
                speakerName = speakers.first().character.name,
                current = 1,
                total = speakers.size
            )
        )
        launchGeneration(sessionId, speakers)
    }

    @UiIntentObserver(GroupChatUiIntent.StopGeneration::class)
    private suspend fun onStopGeneration() {
        val job = mGenerationJob ?: return
        if (!job.isActive) return
        job.cancel()
        persistOrDeleteStreamingMessage()
        refreshState(generationState = GroupChatGenerationState.Idle)
    }

    @UiIntentObserver(GroupChatUiIntent.SummarizeNow::class)
    private suspend fun onSummarizeNow() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        if (mGenerationJob?.isActive == true) return
        uiState.copy(loadState = GroupChatLoadState.Summarizing).setup()
        summarizeSession(uiState.sessionId, showToast = true)
        refreshState(
            page = uiState.page,
            generationState = GroupChatGenerationState.Idle
        )
    }

    @UiIntentObserver(GroupChatUiIntent.ChangeTitle::class)
    private fun onChangeTitle(intent: GroupChatUiIntent.ChangeTitle) {
        updateSettingsState { copy(titleDraft = intent.value) }
    }

    @UiIntentObserver(GroupChatUiIntent.ChangeScenario::class)
    private fun onChangeScenario(intent: GroupChatUiIntent.ChangeScenario) {
        updateSettingsState { copy(scenarioDraft = intent.value) }
    }

    @UiIntentObserver(GroupChatUiIntent.ChangeUserNote::class)
    private fun onChangeUserNote(intent: GroupChatUiIntent.ChangeUserNote) {
        updateSettingsState { copy(userNoteDraft = intent.value) }
    }

    @UiIntentObserver(GroupChatUiIntent.ChangeSummary::class)
    private fun onChangeSummary(intent: GroupChatUiIntent.ChangeSummary) {
        updateSettingsState { copy(summaryDraft = intent.value) }
    }

    @UiIntentObserver(GroupChatUiIntent.ChangeSystemPrompt::class)
    private fun onChangeSystemPrompt(intent: GroupChatUiIntent.ChangeSystemPrompt) {
        updateSettingsState { copy(systemPromptDraft = intent.value) }
    }

    @UiIntentObserver(GroupChatUiIntent.ChangeGroupNudgePrompt::class)
    private fun onChangeGroupNudgePrompt(
        intent: GroupChatUiIntent.ChangeGroupNudgePrompt
    ) {
        updateSettingsState { copy(groupNudgePromptDraft = intent.value) }
    }

    @UiIntentObserver(GroupChatUiIntent.ChangeNewGroupChatPrompt::class)
    private fun onChangeNewGroupChatPrompt(
        intent: GroupChatUiIntent.ChangeNewGroupChatPrompt
    ) {
        updateSettingsState { copy(newGroupChatPromptDraft = intent.value) }
    }

    @UiIntentObserver(GroupChatUiIntent.SelectActivationStrategy::class)
    private fun onSelectActivationStrategy(
        intent: GroupChatUiIntent.SelectActivationStrategy
    ) {
        updateSettingsState { copy(activationStrategy = intent.strategy) }
    }

    @UiIntentObserver(GroupChatUiIntent.SelectCharacterCardMode::class)
    private fun onSelectCharacterCardMode(
        intent: GroupChatUiIntent.SelectCharacterCardMode
    ) {
        updateSettingsState { copy(characterCardMode = intent.mode) }
    }

    @UiIntentObserver(GroupChatUiIntent.ToggleIncludeMutedCards::class)
    private fun onToggleIncludeMutedCards(
        intent: GroupChatUiIntent.ToggleIncludeMutedCards
    ) {
        updateSettingsState { copy(includeMutedCards = intent.enabled) }
    }

    @UiIntentObserver(GroupChatUiIntent.ToggleAutoMode::class)
    private fun onToggleAutoMode(intent: GroupChatUiIntent.ToggleAutoMode) {
        updateSettingsState { copy(autoModeEnabled = intent.enabled) }
    }

    @UiIntentObserver(GroupChatUiIntent.ToggleTrimOtherSpeakers::class)
    private fun onToggleTrimOtherSpeakers(
        intent: GroupChatUiIntent.ToggleTrimOtherSpeakers
    ) {
        updateSettingsState { copy(trimOtherSpeakers = intent.enabled) }
    }

    @UiIntentObserver(GroupChatUiIntent.ToggleAllowSelfResponses::class)
    private fun onToggleAllowSelfResponses(
        intent: GroupChatUiIntent.ToggleAllowSelfResponses
    ) {
        updateSettingsState { copy(allowSelfResponses = intent.enabled) }
    }

    @UiIntentObserver(GroupChatUiIntent.SaveSettings::class)
    private suspend fun onSaveSettings() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        uiState.copy(loadState = GroupChatLoadState.Saving).setup()
        withContext(Dispatchers.IO) {
            val session = mGroupChatRepository.getSessionById(uiState.sessionId)
                ?: return@withContext
            mGroupChatRepository.updateSession(
                session.copy(
                    title = uiState.titleDraft.trim().ifBlank { session.title },
                    scenario = uiState.scenarioDraft.trim(),
                    userNote = uiState.userNoteDraft.trim(),
                    activationStrategy = uiState.activationStrategy,
                    allowSelfResponses = uiState.allowSelfResponses,
                    characterCardMode = uiState.characterCardMode,
                    includeMutedCards = uiState.includeMutedCards,
                    autoModeEnabled = uiState.autoModeEnabled,
                    trimOtherSpeakers = uiState.trimOtherSpeakers,
                    systemPromptOverride = uiState.systemPromptDraft.trim(),
                    groupNudgePromptOverride = uiState.groupNudgePromptDraft.trim(),
                    newGroupChatPromptOverride = uiState.newGroupChatPromptDraft.trim()
                )
            )
            if (uiState.summaryDraft != uiState.summaryDraft.trim()) {
                mGroupChatRepository.updateCurrentSummary(
                    uiState.sessionId,
                    uiState.summaryDraft.trim()
                )
            } else {
                val currentSummary = mGroupChatRepository
                    .getGroupChatData(uiState.sessionId)
                    ?.summary
                    ?.content
                    .orEmpty()
                if (currentSummary != uiState.summaryDraft) {
                    mGroupChatRepository.updateCurrentSummary(
                        uiState.sessionId,
                        uiState.summaryDraft
                    )
                }
            }
        }
        refreshState(page = GroupChatPage.Settings)
    }

    @UiIntentObserver(GroupChatUiIntent.ToggleLorebookEntry::class)
    private suspend fun onToggleLorebookEntry(
        intent: GroupChatUiIntent.ToggleLorebookEntry
    ) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        val enabledIds = uiState.lorebookGroups
            .flatMap { it.entries }
            .filter { it.enabled }
            .map { it.id }
            .toMutableSet()
        if (!enabledIds.add(intent.entryId)) enabledIds.remove(intent.entryId)
        withContext(Dispatchers.IO) {
            mGroupChatRepository.updateSessionLorebookEntryIds(
                uiState.sessionId,
                enabledIds.toList()
            )
        }
        refreshState(page = uiState.page)
    }

    @UiIntentObserver(GroupChatUiIntent.ToggleLorebook::class)
    /** 切换当前群聊会话中一本世界书的全部条目。 */
    private suspend fun onToggleLorebook(intent: GroupChatUiIntent.ToggleLorebook) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        val entryIds = uiState.lorebookGroups
            .firstOrNull { it.lorebookId == intent.lorebookId }
            ?.entries
            ?.mapTo(mutableSetOf()) { it.id }
            .orEmpty()
        if (entryIds.isEmpty()) return
        val enabledIds = uiState.lorebookGroups
            .flatMap { it.entries }
            .filter { it.enabled }
            .mapTo(mutableSetOf()) { it.id }
            .toggleAll(entryIds)
        withContext(Dispatchers.IO) {
            mGroupChatRepository.updateSessionLorebookEntryIds(
                uiState.sessionId,
                enabledIds.toList()
            )
        }
        refreshState(page = uiState.page)
    }

    @UiIntentObserver(GroupChatUiIntent.AddMember::class)
    private suspend fun onAddMember(intent: GroupChatUiIntent.AddMember) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        withContext(Dispatchers.IO) {
            mGroupChatRepository.addMember(uiState.sessionId, intent.characterId)
        }
        refreshState(page = uiState.page)
    }

    @UiIntentObserver(GroupChatUiIntent.RemoveMember::class)
    private suspend fun onRemoveMember(intent: GroupChatUiIntent.RemoveMember) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        runCatching {
            withContext(Dispatchers.IO) {
                mGroupChatRepository.removeMember(uiState.sessionId, intent.characterId)
            }
        }.onFailure {
            AppViewEvent.PopupToastMessage(
                it.message ?: mContext.getString(R.string.group_chat_select_two_characters)
            ).tryEmit()
        }
        refreshState(page = uiState.page)
    }

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

    @UiIntentObserver(GroupChatUiIntent.StartEditMessage::class)
    private fun onStartEditMessage(intent: GroupChatUiIntent.StartEditMessage) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        if (mGenerationJob?.isActive == true) return
        val message = uiState.messages.firstOrNull { it.id == intent.messageId } ?: return
        uiState.copy(
            editingMessageId = message.id,
            editingMessageDraft = message.content
        ).setup()
    }

    @UiIntentObserver(GroupChatUiIntent.ChangeEditingMessageDraft::class)
    private fun onChangeEditingMessageDraft(
        intent: GroupChatUiIntent.ChangeEditingMessageDraft
    ) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        if (uiState.editingMessageId == null) return
        uiState.copy(editingMessageDraft = intent.value).setup()
    }

    @UiIntentObserver(GroupChatUiIntent.SaveEditingMessage::class)
    private suspend fun onSaveEditingMessage() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        val messageId = uiState.editingMessageId ?: return
        if (uiState.editingMessageDraft.isBlank()) return
        withContext(Dispatchers.IO) {
            mGroupChatRepository.updateMessageContent(
                messageId,
                uiState.editingMessageDraft.trim()
            )
        }
        refreshState(editingMessageId = null, editingMessageDraft = "")
    }

    @UiIntentObserver(GroupChatUiIntent.CancelEditingMessage::class)
    private fun onCancelEditingMessage() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        uiState.copy(editingMessageId = null, editingMessageDraft = "").setup()
    }

    @UiIntentObserver(GroupChatUiIntent.DeleteMessage::class)
    private suspend fun onDeleteMessage(intent: GroupChatUiIntent.DeleteMessage) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        if (mGenerationJob?.isActive == true) return
        if (uiState.messages.none { it.id == intent.messageId }) return
        withContext(Dispatchers.IO) {
            mGroupChatRepository.deleteMessage(intent.messageId)
        }
        refreshState(editingMessageId = null, editingMessageDraft = "")
    }

    @UiIntentObserver(GroupChatUiIntent.RegenerateMessage::class)
    private suspend fun onRegenerateMessage(intent: GroupChatUiIntent.RegenerateMessage) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        if (mGenerationJob?.isActive == true) return
        val data = withContext(Dispatchers.IO) {
            mGroupChatRepository.getGroupChatData(uiState.sessionId)
        } ?: return
        val message = data.messages.firstOrNull { it.id == intent.messageId } ?: return
        if (message.source != GroupChatMessage.Source.Character) return
        val speaker = data.members.firstOrNull {
            it.character.id == message.speakerCharacterId
        } ?: return
        withContext(Dispatchers.IO) {
            mGroupChatRepository.deleteMessagesFrom(message.id)
        }
        refreshState(
            editingMessageId = null,
            editingMessageDraft = "",
            generationState = GroupChatGenerationState.Generating(
                speakerName = speaker.character.name,
                current = 1,
                total = 1
            )
        )
        launchGeneration(
            sessionId = uiState.sessionId,
            speakers = listOf(speaker),
            generationMode = GroupChatGenerationMode.Regenerate
        )
    }

    @UiIntentObserver(GroupChatUiIntent.ContinueLast::class)
    private suspend fun onContinueLast() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        if (mGenerationJob?.isActive == true) return
        val data = withContext(Dispatchers.IO) {
            mGroupChatRepository.getGroupChatData(uiState.sessionId)
        } ?: return
        val last = data.messages.lastOrNull {
            it.source == GroupChatMessage.Source.Character
        } ?: return
        val speaker = data.members.firstOrNull {
            it.character.id == last.speakerCharacterId
        } ?: return
        val batchId = UUID.randomUUID().toString()
        mGenerationJob = viewModelScope.launch {
            runCatching {
                generateSpeakerReply(
                    sessionId = uiState.sessionId,
                    speaker = speaker,
                    batchId = batchId,
                    current = 1,
                    total = 1,
                    continueMessage = last,
                    generationMode = GroupChatGenerationMode.Continue
                )
                refreshState(generationState = GroupChatGenerationState.Idle)
            }.onFailure {
                if (it is CancellationException) return@onFailure
                persistOrDeleteStreamingMessage()
                refreshState(
                    generationState = GroupChatGenerationState.Failed(
                        it.message ?: mContext.getString(
                            R.string.continue_generation_failed
                        )
                    )
                )
            }
        }
    }

    @UiIntentObserver(GroupChatUiIntent.DeleteSessionClick::class)
    private fun onDeleteSessionClick() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        if (mGenerationJob?.isActive == true) return
        uiState.copy(
            dialogState = GroupChatDialogState.DeleteSessionConfirm(uiState.title)
        ).setup()
    }

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
        GroupChatUiState.Finished.setup()
    }

    @UiIntentObserver(GroupChatUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        uiState.copy(dialogState = GroupChatDialogState.None).setup()
    }

    /** 启动一轮或自动连续多轮的成员回复生成。 */
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
                while (pendingSpeakers.isNotEmpty()) {
                    pendingSpeakers.forEachIndexed { index, speaker ->
                        currentCoroutineContext().ensureActive()
                        generateSpeakerReply(
                            sessionId = sessionId,
                            speaker = speaker,
                            batchId = batchId,
                            current = index + 1,
                            total = pendingSpeakers.size,
                            generationMode = nextGenerationMode
                        )
                        nextGenerationMode = GroupChatGenerationMode.Normal
                    }
                    val nextData = withContext(Dispatchers.IO) {
                        mGroupChatRepository.getGroupChatData(sessionId)
                    } ?: break
                    pendingSpeakers = if (
                        nextData.session.autoModeEnabled &&
                        nextData.session.activationStrategy !=
                        GroupChatSession.ActivationStrategy.Manual
                    ) {
                        delay(AUTO_MODE_DELAY_MS)
                        mSpeakerSelector.select(
                            session = nextData.session,
                            members = nextData.members,
                            messages = nextData.messages,
                            userInput = "",
                            manualCharacterId = null
                        )
                    } else {
                        emptyList()
                    }
                }
                maybeAutoSummarize(sessionId)
                refreshState(generationState = GroupChatGenerationState.Idle)
            }.onFailure { throwable ->
                if (throwable is CancellationException) return@onFailure
                persistOrDeleteStreamingMessage()
                refreshState(
                    generationState = GroupChatGenerationState.Failed(
                        throwable.message ?: mContext.getString(R.string.generation_failed)
                    )
                )
                AppViewEvent.PopupToastMessage(
                    throwable.message ?: mContext.getString(R.string.generation_failed)
                ).tryEmit()
            }
        }
    }

    /** 为指定成员构建上下文、调用模型并持久化清洗后的回复。 */
    private suspend fun generateSpeakerReply(
        sessionId: Long,
        speaker: GroupChatMemberData,
        batchId: String,
        current: Int,
        total: Int,
        continueMessage: GroupChatMessage? = null,
        generationMode: GroupChatGenerationMode = GroupChatGenerationMode.Normal
    ) {
        val data = withContext(Dispatchers.IO) {
            mGroupChatRepository.getGroupChatData(sessionId)
        } ?: error(mContext.getString(R.string.group_chat_not_found))
        val provider = withContext(Dispatchers.IO) {
            mLLMRepository.getSelectedProvider()
        } ?: error(mContext.getString(R.string.no_enabled_llm_provider_configured))
        val lorebookContext = withContext(Dispatchers.IO) {
            loadLorebookContext(data, speaker)
        }
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
                    generationMode = generationMode
                )
            )
        }
        recordPromptInspection(buildResult.inspection)
        val request = buildResult.request
        mStreamingMessageId = continueMessage?.id ?: withContext(Dispatchers.IO) {
            mGroupChatRepository.createMessage(
                sessionId = sessionId,
                source = GroupChatMessage.Source.Character,
                content = "",
                speakerCharacterId = speaker.character.id,
                speakerNameSnapshot = speaker.character.name,
                generationBatchId = batchId
            )
        }
        val existingContent = continueMessage?.content.orEmpty()
        mStreamingContent = existingContent
        refreshState(
            generationState = GroupChatGenerationState.Generating(
                speakerName = speaker.character.name,
                current = current,
                total = total
            )
        )
        if (AppModel.streamEnabled) {
            collectStreamingResponse(request)
        } else {
            val response = withContext(Dispatchers.IO) {
                mLLMRepository.generateWithSelectedProvider(request)
            }
            mStreamingContent = existingContent + response.content
        }
        val generatedPart = mStreamingContent.removePrefix(existingContent)
        val sanitizedPart = mOutputSanitizer.sanitize(
            content = generatedPart,
            currentSpeakerName = speaker.character.name,
            otherSpeakerNames = data.members
                .map { it.character.name }
                .filterNot { it == speaker.character.name },
            trimOtherSpeakers = data.session.trimOtherSpeakers
        )
        mStreamingContent = existingContent + sanitizedPart
        persistOrDeleteStreamingMessage()
        withContext(Dispatchers.IO) {
            mGroupChatRepository.updateWorldInfoState(
                sessionId,
                buildResult.worldInfoStateJson
            )
        }
        refreshState(
            generationState = GroupChatGenerationState.Generating(
                speakerName = speaker.character.name,
                current = current,
                total = total
            )
        )
    }

    /** 收集流式增量并实时更新当前消息的 UI 状态。 */
    private suspend fun collectStreamingResponse(
        request: me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
    ) {
        mLLMRepository.streamGenerateWithSelectedProvider(request).collect { event ->
            currentCoroutineContext().ensureActive()
            if (event is LLMStreamEvent.Delta) {
                mStreamingContent += event.content
                updateStreamingState(mStreamingContent)
            }
        }
    }

    /** 仅更新内存中的流式消息，避免每个增量都写数据库。 */
    private fun updateStreamingState(content: String) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        val messageId = mStreamingMessageId ?: return
        uiState.copy(
            messages = uiState.messages.map {
                if (it.id == messageId) {
                    it.copy(content = content, isStreaming = true)
                } else {
                    it
                }
            }
        ).setup()
    }

    /** 在生成结束或取消时落库，空回复则删除占位消息。 */
    private suspend fun persistOrDeleteStreamingMessage() {
        val messageId = mStreamingMessageId ?: return
        val content = mStreamingContent
        withContext(Dispatchers.IO) {
            if (content.isBlank()) {
                mGroupChatRepository.deleteMessage(messageId)
            } else {
                mGroupChatRepository.updateMessageContent(messageId, content)
            }
        }
        mStreamingMessageId = null
        mStreamingContent = ""
    }

    /** 达到全局触发阈值后自动更新群聊摘要。 */
    private suspend fun maybeAutoSummarize(sessionId: Long) {
        if (!AppModel.autoSummaryEnabled) return
        val messages = withContext(Dispatchers.IO) {
            mGroupChatRepository.getMessagesAfterLatestSummary(sessionId)
        }
        if (messages.size < AppModel.summaryTriggerMessageCount) return
        summarizeSession(sessionId, showToast = false)
    }

    /** 摘要尚未覆盖的消息，并推进摘要覆盖边界。 */
    private suspend fun summarizeSession(sessionId: Long, showToast: Boolean) {
        runCatching {
            val data = withContext(Dispatchers.IO) {
                mGroupChatRepository.getGroupChatData(sessionId)
            } ?: return
            val provider = withContext(Dispatchers.IO) {
                mLLMRepository.getSelectedProvider()
            } ?: return
            val unsummarized = data.messages.filter {
                it.id > (data.summary?.coveredMessageId ?: 0L)
            }
            val selected = mSummaryPromptBuilder.selectMessagesToSummarize(
                unsummarized,
                provider
            )
            if (selected.isEmpty()) return
            val request = mSummaryPromptBuilder.build(
                session = data.session,
                memberNames = data.members.map { it.character.name },
                existingSummary = data.summary?.content.orEmpty(),
                messages = selected,
                provider = provider
            )
            val response = withContext(Dispatchers.IO) {
                mLLMRepository.generateWithSelectedProvider(request)
            }
            withContext(Dispatchers.IO) {
                mGroupChatRepository.saveSummary(
                    sessionId = sessionId,
                    content = response.content,
                    coveredMessageId = selected.last().id
                )
            }
            if (showToast) {
                AppViewEvent.PopupToastMessageByResId(R.string.summary_updated).tryEmit()
            }
        }.onFailure {
            if (it is CancellationException) throw it
            if (showToast) {
                AppViewEvent.PopupToastMessage(
                    it.message ?: mContext.getString(R.string.summary_failed)
                ).tryEmit()
            }
        }
    }

    /** 合并会话手选条目与本轮角色卡绑定的世界书。 */
    private suspend fun loadLorebookContext(
        data: GroupChatData,
        speaker: GroupChatMemberData
    ): GroupLorebookContext {
        val selectedEntryIds = mGroupChatRepository
            .getSessionLorebookEntryIds(data.session)
            .toSet()
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
        val lorebooks = mLorebookRepository.getAllLorebooks()
        val allEntries = lorebooks.flatMap {
            mLorebookRepository.getEntriesByLorebookId(it.id)
        }
        val entries = allEntries.filter {
            it.id in selectedEntryIds || it.lorebookId in characterLorebookIds
        }
        val activeLorebookIds = entries.map { it.lorebookId }.toSet()
        val activeLorebooks = lorebooks
            .filter { it.id in activeLorebookIds }
            .associateBy { it.id }
        return GroupLorebookContext(
            entries = entries,
            lorebooks = activeLorebooks,
            recursiveLorebookIds = activeLorebooks.values
                .filter { it.recursiveScanning }
                .map { it.id }
                .toSet()
        )
    }

    /** 从数据层重新构造完整页面状态，同时保留临时交互状态。 */
    private suspend fun refreshState(
        page: GroupChatPage =
            getOrNull<GroupChatUiState.Normal>()?.page ?: GroupChatPage.Conversation,
        inputDraft: String =
            getOrNull<GroupChatUiState.Normal>()?.inputDraft.orEmpty(),
        selectedSpeakerId: Long? =
            getOrNull<GroupChatUiState.Normal>()?.selectedSpeakerId,
        generationState: GroupChatGenerationState =
            getOrNull<GroupChatUiState.Normal>()?.generationState ?: GroupChatGenerationState.Idle,
        editingMessageId: Long? =
            getOrNull<GroupChatUiState.Normal>()?.editingMessageId,
        editingMessageDraft: String =
            getOrNull<GroupChatUiState.Normal>()?.editingMessageDraft.orEmpty(),
        dialogState: GroupChatDialogState =
            getOrNull<GroupChatUiState.Normal>()?.dialogState ?: GroupChatDialogState.None
    ) {
        val sessionId = mSessionId ?: return
        val next = withContext(Dispatchers.IO) {
            loadState(
                sessionId = sessionId,
                page = page,
                inputDraft = inputDraft,
                selectedSpeakerId = selectedSpeakerId,
                generationState = generationState,
                editingMessageId = editingMessageId,
                editingMessageDraft = editingMessageDraft,
                dialogState = dialogState
            )
        } ?: return
        next.setup()
    }

    /** 将群聊聚合数据映射为只供页面渲染的 UiState。 */
    private suspend fun loadState(
        sessionId: Long,
        page: GroupChatPage = GroupChatPage.Conversation,
        inputDraft: String = "",
        selectedSpeakerId: Long? = null,
        generationState: GroupChatGenerationState = GroupChatGenerationState.Idle,
        editingMessageId: Long? = null,
        editingMessageDraft: String = "",
        dialogState: GroupChatDialogState = GroupChatDialogState.None
    ): GroupChatUiState.Normal? {
        val data = mGroupChatRepository.getGroupChatData(sessionId) ?: return null
        val members = data.members.map {
            GroupChatMemberItem(
                id = it.character.id,
                name = it.character.name,
                description = it.character.description,
                muted = it.relation.muted
            )
        }
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
        val memberIds = members.map { it.id }.toSet()
        val availableCharacters = mCharacterRepository.getAllCharacters().map {
            GroupChatAvailableCharacterItem(
                id = it.id,
                name = it.name,
                alreadyMember = it.id in memberIds
            )
        }
        val enabledEntryIds = mGroupChatRepository
            .getSessionLorebookEntryIds(data.session)
            .toSet()
        val lorebookGroups = mLorebookRepository.getAllLorebooks().map { lorebook ->
            GroupChatLorebookGroupItem(
                lorebookId = lorebook.id,
                lorebookName = lorebook.name,
                entries = mLorebookRepository.getEntriesByLorebookId(lorebook.id).map {
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
        return GroupChatUiState.Normal(
            sessionId = sessionId,
            title = data.session.title,
            page = page,
            activationStrategy = data.session.activationStrategy,
            characterCardMode = data.session.characterCardMode,
            allowSelfResponses = data.session.allowSelfResponses,
            includeMutedCards = data.session.includeMutedCards,
            autoModeEnabled = data.session.autoModeEnabled,
            trimOtherSpeakers = data.session.trimOtherSpeakers,
            scenarioDraft = data.session.scenario,
            userNoteDraft = data.session.userNote,
            summaryDraft = data.summary?.content.orEmpty(),
            systemPromptDraft = data.session.systemPromptOverride,
            groupNudgePromptDraft = data.session.groupNudgePromptOverride,
            newGroupChatPromptDraft = data.session.newGroupChatPromptOverride,
            titleDraft = data.session.title,
            members = members,
            availableCharacters = availableCharacters,
            lorebookGroups = lorebookGroups,
            messages = data.toMessageItems(),
            selectedSpeakerId = effectiveSpeakerId,
            inputDraft = inputDraft,
            generationState = generationState,
            hasPromptInspection = mLastPromptInspection != null,
            editingMessageId = editingMessageId,
            editingMessageDraft = editingMessageDraft,
            dialogState = dialogState
        )
    }

    private fun recordPromptInspection(inspection: PromptInspection) {
        mLastPromptInspection = inspection
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        uiState.copy(hasPromptInspection = true).setup()
        if (inspection.hasOmissions) {
            AppViewEvent.PopupToastMessageByResId(R.string.prompt_trimmed_warning).tryEmit()
        }
    }

    /** 将数据库消息转换为页面消息，并标记当前流式占位项。 */
    private fun GroupChatData.toMessageItems(): List<GroupChatMessageItem> {
        return messages.map {
            GroupChatMessageItem(
                id = it.id,
                source = it.source,
                speakerName = it.speakerNameSnapshot,
                content = it.content,
                time = it.createTime.formatTimestamp("HH:mm"),
                isStreaming = it.id == mStreamingMessageId
            )
        }
    }

    private fun finishWithToast(messageResId: Int) {
        AppViewEvent.PopupToastMessageByResId(messageResId).tryEmit()
        GroupChatUiState.Finished.setup()
    }

    /** 仅允许在设置页更新表单草稿，保持页面状态边界明确。 */
    private inline fun updateSettingsState(
        transform: GroupChatUiState.Normal.() -> GroupChatUiState.Normal
    ) {
        val uiState = getOrNull<GroupChatUiState.Normal>() ?: return
        if (uiState.page != GroupChatPage.Settings) return
        uiState.transform().setup()
    }

    private data class GroupLorebookContext(
        val entries: List<me.kafuuneko.rpclient.libs.room.entity.LorebookEntry>,
        val lorebooks: Map<Long, me.kafuuneko.rpclient.libs.room.entity.Lorebook>,
        val recursiveLorebookIds: Set<Long>
    )

    private companion object {
        // 自动群聊两轮生成之间的短暂等待时间。
        const val AUTO_MODE_DELAY_MS = 500L
    }
}
