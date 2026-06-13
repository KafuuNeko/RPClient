package me.kafuuneko.rpclient.feature.main

import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.about.AboutActivity
import me.kafuuneko.rpclient.feature.characterlist.CharacterListActivity
import me.kafuuneko.rpclient.feature.chat.ChatActivity
import me.kafuuneko.rpclient.feature.chatcreate.ChatCreateActivity
import me.kafuuneko.rpclient.feature.groupchat.GroupChatActivity
import me.kafuuneko.rpclient.feature.groupchatcreate.GroupChatCreateActivity
import me.kafuuneko.rpclient.feature.llmproviderlist.LLMProviderListActivity
import me.kafuuneko.rpclient.feature.main.presentation.MainDialogState
import me.kafuuneko.rpclient.feature.main.presentation.MainHomeState
import me.kafuuneko.rpclient.feature.main.model.MainChatSessionItem
import me.kafuuneko.rpclient.feature.main.model.MainGroupChatSessionItem
import me.kafuuneko.rpclient.feature.main.presentation.MainPage
import me.kafuuneko.rpclient.feature.main.presentation.MainSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainUiIntent
import me.kafuuneko.rpclient.feature.main.presentation.MainUiState
import me.kafuuneko.rpclient.feature.promptpreset.PromptPresetActivity
import me.kafuuneko.rpclient.feature.requestlog.RequestLogActivity
import me.kafuuneko.rpclient.feature.regexscript.RegexScriptActivity
import me.kafuuneko.rpclient.feature.worldbooklist.WorldBookListActivity
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.prompt.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.SummaryInjectionPosition
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository
import me.kafuuneko.rpclient.libs.utils.formatTimestamp
import me.kafuuneko.rpclient.libs.utils.stripThinkBlocks
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 主页面状态持有者。
 *
 * 聚合最近会话、角色与群聊入口，并将全局设置和当前模型配置映射为可编辑状态。
 */
class MainViewModel : CoreViewModelWithEvent<MainUiIntent, MainUiState>(
    MainUiState.None
), KoinComponent {
    private val mLLMRepository by inject<LLMRepository>()
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mChatRepository by inject<ChatRepository>()
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mGroupChatRepository by inject<GroupChatRepository>()
    private val mContext by inject<Context>()

    @UiIntentObserver(MainUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<MainUiState.None>()) return
        val providers = mLLMRepository.getEnabledProviders()
        val currentId = AppModel.currentLLMProvider
        val selectedProvider = providers.firstOrNull { it.id == currentId } ?: providers.firstOrNull()
        MainUiState.Normal(
            homeState = buildHomeState(),
            settingsState = buildSettingsState(providers, selectedProvider)
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.Resume::class)
    private suspend fun onResume() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val providers = mLLMRepository.getEnabledProviders()
        val currentId = AppModel.currentLLMProvider
        val selectedProvider = providers.firstOrNull { it.id == currentId } ?: providers.firstOrNull()
        uiState.copy(
            homeState = buildHomeState(),
            settingsState = uiState.settingsState.copy(
                selectedProviderId = selectedProvider?.id?.toString().orEmpty(),
                providers = providers,
                userName = AppModel.userName,
                userDescription = AppModel.userDescription,
                temperature = selectedProvider?.temperature ?: 0f,
                topP = selectedProvider?.topP ?: 0f,
                maxTokens = selectedProvider?.maxTokens ?: 0,
                contextTokens = selectedProvider?.contextTokens ?: 0,
                streamEnabled = AppModel.streamEnabled,
                promptPostProcessingMode = selectedProvider?.postProcessingMode()
                    ?: PromptPostProcessingMode.None,
                includeThinkInContext = AppModel.includeThinkInContext,
                debugModeEnabled = AppModel.debugModeEnabled,
                autoSummaryEnabled = AppModel.autoSummaryEnabled,
                summaryTriggerMessageCount = AppModel.summaryTriggerMessageCount,
                summaryWordsLimit = AppModel.summaryWordsLimit,
                summaryMaxMessagesPerRequest = AppModel.summaryMaxMessagesPerRequest,
                summaryResponseTokens = AppModel.summaryResponseTokens,
                summaryInjectionPosition = readSummaryInjectionPosition()
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.Back::class)
    private fun onBack() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        if (uiState.homeState.multiSelectMode) {
            uiState.copy(
                homeState = uiState.homeState.copy(
                    multiSelectMode = false,
                    selectedSessionIds = emptySet()
                )
            ).setup()
            return
        }
        if (uiState.selectedPage != MainPage.Home) {
            uiState.copy(selectedPage = MainPage.Home).setup()
            return
        }
        MainUiState.Finished.setup()
    }

    @UiIntentObserver(MainUiIntent.EnterMultiSelect::class)
    private fun onEnterMultiSelect(intent: MainUiIntent.EnterMultiSelect) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        if (uiState.homeState.multiSelectMode) return
        uiState.copy(
            homeState = uiState.homeState.copy(
                multiSelectMode = true,
                selectedSessionIds = setOf(intent.sessionId)
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ToggleSessionSelection::class)
    private fun onToggleSessionSelection(intent: MainUiIntent.ToggleSessionSelection) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        if (!uiState.homeState.multiSelectMode) return
        val current = uiState.homeState.selectedSessionIds
        val updated = if (intent.sessionId in current) {
            current - intent.sessionId
        } else {
            current + intent.sessionId
        }
        uiState.copy(
            homeState = uiState.homeState.copy(selectedSessionIds = updated)
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ExitMultiSelect::class)
    private fun onExitMultiSelect() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        uiState.copy(
            homeState = uiState.homeState.copy(
                multiSelectMode = false,
                selectedSessionIds = emptySet()
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ShowDeleteSelectedDialog::class)
    private fun onShowDeleteSelectedDialog() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val count = uiState.homeState.selectedSessionIds.size
        if (count == 0) return
        uiState.copy(
            dialogState = MainDialogState.DeleteSelectedSessions(count = count)
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ConfirmDeleteSelected::class)
    private suspend fun onConfirmDeleteSelected() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? MainDialogState.DeleteSelectedSessions ?: return
        val ids = uiState.homeState.selectedSessionIds
        withContext(Dispatchers.IO) {
            ids.forEach { id ->
                id.toLongOrNull()?.let { mChatRepository.deleteSession(it) }
            }
        }
        uiState.copy(
            dialogState = MainDialogState.None,
            homeState = buildHomeState()
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        uiState.copy(dialogState = MainDialogState.None).setup()
    }

    @UiIntentObserver(MainUiIntent.SelectPage::class)
    private fun onSelectPage(intent: MainUiIntent.SelectPage) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        uiState.copy(selectedPage = intent.page).setup()
    }

    @UiIntentObserver(MainUiIntent.OpenChat::class)
    private fun onOpenChat(intent: MainUiIntent.OpenChat) {
        AppViewEvent.StartActivity(
            activity = ChatActivity::class.java,
            extras = Bundle().apply { putString(ChatActivity.EXTRA_SESSION_ID, intent.sessionId) }
        ).tryEmit()
    }

    @UiIntentObserver(MainUiIntent.OpenCreateChat::class)
    private fun onOpenCreateChat() {
        AppViewEvent.StartActivity(ChatCreateActivity::class.java).tryEmit()
    }

    @UiIntentObserver(MainUiIntent.OpenGroupChat::class)
    private fun onOpenGroupChat(intent: MainUiIntent.OpenGroupChat) {
        AppViewEvent.StartActivity(
            activity = GroupChatActivity::class.java,
            extras = Bundle().apply {
                putString(GroupChatActivity.EXTRA_SESSION_ID, intent.sessionId)
            }
        ).tryEmit()
    }

    @UiIntentObserver(MainUiIntent.OpenCreateGroupChat::class)
    private fun onOpenCreateGroupChat() {
        AppViewEvent.StartActivity(GroupChatCreateActivity::class.java).tryEmit()
    }

    @UiIntentObserver(MainUiIntent.OpenCharacterManager::class)
    private fun onOpenCharacterManager() {
        AppViewEvent.StartActivity(CharacterListActivity::class.java).tryEmit()
    }

    @UiIntentObserver(MainUiIntent.OpenWorldBookManager::class)
    private fun onOpenWorldBookManager() {
        AppViewEvent.StartActivity(WorldBookListActivity::class.java).tryEmit()
    }

    @UiIntentObserver(MainUiIntent.OpenProviderManager::class)
    private fun onOpenProviderManager() {
        AppViewEvent.StartActivity(LLMProviderListActivity::class.java).tryEmit()
    }

    @UiIntentObserver(MainUiIntent.OpenPromptPreset::class)
    private fun onOpenPromptPreset() {
        AppViewEvent.StartActivity(PromptPresetActivity::class.java).tryEmit()
    }

    @UiIntentObserver(MainUiIntent.OpenRegexScripts::class)
    private fun onOpenRegexScripts() {
        AppViewEvent.StartActivity(RegexScriptActivity::class.java).tryEmit()
    }

    @UiIntentObserver(MainUiIntent.OpenRequestLogs::class)
    private fun onOpenRequestLogs() {
        AppViewEvent.StartActivity(RequestLogActivity::class.java).tryEmit()
    }

    @UiIntentObserver(MainUiIntent.OpenAbout::class)
    private fun onOpenAbout() {
        AppViewEvent.StartActivity(AboutActivity::class.java).tryEmit()
    }

    @UiIntentObserver(MainUiIntent.ChangeUserName::class)
    private fun onChangeUserName(intent: MainUiIntent.ChangeUserName) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val value = intent.value.trim()
        AppModel.userName = value.ifBlank { "You" }
        uiState.copy(
            settingsState = uiState.settingsState.copy(userName = intent.value)
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ChangeUserDescription::class)
    private fun onChangeUserDescription(intent: MainUiIntent.ChangeUserDescription) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.userDescription = intent.value.trim()
        uiState.copy(
            settingsState = uiState.settingsState.copy(userDescription = intent.value)
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.SelectProvider::class)
    private suspend fun onSelectProvider(intent: MainUiIntent.SelectProvider) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val providerId = intent.providerId.toLongOrNull() ?: return
        mLLMRepository.updateCurrentProvider(providerId)
        val providers = mLLMRepository.getEnabledProviders()
        val selectedProvider = providers.firstOrNull { it.id == providerId }
        uiState.copy(
            selectedPage = MainPage.Settings,
            settingsState = uiState.settingsState.copy(
                selectedProviderId = intent.providerId,
                providers = providers,
                temperature = selectedProvider?.temperature ?: uiState.settingsState.temperature,
                topP = selectedProvider?.topP ?: uiState.settingsState.topP,
                maxTokens = selectedProvider?.maxTokens ?: uiState.settingsState.maxTokens,
                contextTokens = selectedProvider?.contextTokens ?: uiState.settingsState.contextTokens,
                promptPostProcessingMode = selectedProvider?.postProcessingMode()
                    ?: PromptPostProcessingMode.None
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ToggleAutoSummaryEnabled::class)
    private fun onToggleAutoSummaryEnabled(intent: MainUiIntent.ToggleAutoSummaryEnabled) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.autoSummaryEnabled = intent.enabled
        uiState.copy(
            settingsState = uiState.settingsState.copy(autoSummaryEnabled = intent.enabled)
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ChangeSummaryTriggerMessageCount::class)
    private fun onChangeSummaryTriggerMessageCount(intent: MainUiIntent.ChangeSummaryTriggerMessageCount) {
        updateSummaryInt(intent.value, minimum = 1) {
            AppModel.summaryTriggerMessageCount = it
            copy(summaryTriggerMessageCount = it)
        }
    }

    @UiIntentObserver(MainUiIntent.ChangeSummaryWordsLimit::class)
    private fun onChangeSummaryWordsLimit(intent: MainUiIntent.ChangeSummaryWordsLimit) {
        updateSummaryInt(intent.value, minimum = 50) {
            AppModel.summaryWordsLimit = it
            copy(summaryWordsLimit = it)
        }
    }

    @UiIntentObserver(MainUiIntent.ChangeSummaryMaxMessagesPerRequest::class)
    private fun onChangeSummaryMaxMessagesPerRequest(intent: MainUiIntent.ChangeSummaryMaxMessagesPerRequest) {
        updateSummaryInt(intent.value, minimum = 0) {
            AppModel.summaryMaxMessagesPerRequest = it
            copy(summaryMaxMessagesPerRequest = it)
        }
    }

    @UiIntentObserver(MainUiIntent.ChangeSummaryResponseTokens::class)
    private fun onChangeSummaryResponseTokens(intent: MainUiIntent.ChangeSummaryResponseTokens) {
        updateSummaryInt(intent.value, minimum = 128) {
            AppModel.summaryResponseTokens = it
            copy(summaryResponseTokens = it)
        }
    }

    @UiIntentObserver(MainUiIntent.SelectSummaryInjectionPosition::class)
    private fun onSelectSummaryInjectionPosition(
        intent: MainUiIntent.SelectSummaryInjectionPosition
    ) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.summaryInjectionPosition = intent.position.ordinal
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                summaryInjectionPosition = intent.position
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ToggleStreamEnabled::class)
    private fun onToggleStreamEnabled(intent: MainUiIntent.ToggleStreamEnabled) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.streamEnabled = intent.enabled
        uiState.copy(
            settingsState = uiState.settingsState.copy(streamEnabled = intent.enabled)
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.SelectPostProcessingMode::class)
    private suspend fun onSelectPostProcessingMode(intent: MainUiIntent.SelectPostProcessingMode) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val providerId = uiState.settingsState.selectedProviderId.toLongOrNull() ?: return
        val provider = uiState.settingsState.providers.firstOrNull { it.id == providerId } ?: return
        val updatedProvider = provider.copy(promptPostProcessingMode = intent.mode.ordinal)
        withContext(Dispatchers.IO) {
            mLLMRepository.saveProvider(updatedProvider)
        }
        uiState.copy(
            settingsState = uiState.settingsState.copy(
                providers = uiState.settingsState.providers.map {
                    if (it.id == providerId) updatedProvider else it
                },
                promptPostProcessingMode = intent.mode
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ToggleIncludeThinkInContext::class)
    private fun onToggleIncludeThinkInContext(intent: MainUiIntent.ToggleIncludeThinkInContext) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.includeThinkInContext = intent.enabled
        uiState.copy(
            settingsState = uiState.settingsState.copy(includeThinkInContext = intent.enabled)
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.ToggleDebugModeEnabled::class)
    private fun onToggleDebugModeEnabled(intent: MainUiIntent.ToggleDebugModeEnabled) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        AppModel.debugModeEnabled = intent.enabled
        uiState.copy(
            settingsState = uiState.settingsState.copy(debugModeEnabled = intent.enabled)
        ).setup()
    }

    private suspend fun buildHomeState(): MainHomeState {
        return withContext(Dispatchers.IO) {
            val characters = mCharacterRepository.getAllCharacters()
            val characterMap = characters.associateBy { it.id }
            val sessions = mChatRepository.getAllSessions()
            val groupSessions = mGroupChatRepository.getAllSessions()
            MainHomeState(
                recentSessions = sessions.map { session ->
                    session.toUiModel(characterMap[session.characterId])
                },
                groupChatSessions = groupSessions.map { session ->
                    val data = mGroupChatRepository.getGroupChatData(session.id)
                    MainGroupChatSessionItem(
                        id = session.id.toString(),
                        title = session.title,
                        memberNames = data?.members
                            ?.joinToString(", ") { it.character.name }
                            .orEmpty(),
                        preview = data?.messages?.lastOrNull()?.content
                            ?.stripThinkBlocks()
                            ?.takeIf { it.isNotBlank() }
                            ?: mContext.getString(R.string.no_messages_yet),
                        messageCount = data?.messages?.size ?: 0,
                        updatedAt = session.latestTime.formatTimestamp("MM-dd HH:mm")
                    )
                },
                totalCharacters = characters.size,
                totalWorldBooks = mLorebookRepository.getAllLorebooks().size
            )
        }
    }

    private fun buildSettingsState(
        providers: List<LLMProvider>,
        selectedProvider: LLMProvider?
    ): MainSettingsState {
        return MainSettingsState(
            userName = AppModel.userName,
            userDescription = AppModel.userDescription,
            selectedProviderId = selectedProvider?.id?.toString().orEmpty(),
            providers = providers,
            temperature = selectedProvider?.temperature ?: 0.8f,
            topP = selectedProvider?.topP ?: 1.0f,
            maxTokens = selectedProvider?.maxTokens ?: 1200,
            contextTokens = selectedProvider?.contextTokens ?: 8192,
            localFirstEnabled = true,
            streamEnabled = AppModel.streamEnabled,
            promptPostProcessingMode = selectedProvider?.postProcessingMode()
                ?: PromptPostProcessingMode.None,
            includeThinkInContext = AppModel.includeThinkInContext,
            debugModeEnabled = AppModel.debugModeEnabled,
            autoSummaryEnabled = AppModel.autoSummaryEnabled,
            summaryTriggerMessageCount = AppModel.summaryTriggerMessageCount,
            summaryWordsLimit = AppModel.summaryWordsLimit,
            summaryMaxMessagesPerRequest = AppModel.summaryMaxMessagesPerRequest,
            summaryResponseTokens = AppModel.summaryResponseTokens,
            summaryInjectionPosition = readSummaryInjectionPosition()
        )
    }

    private fun LLMProvider.postProcessingMode(): PromptPostProcessingMode {
        return PromptPostProcessingMode.fromOrdinal(promptPostProcessingMode)
    }

    private fun readSummaryInjectionPosition(): SummaryInjectionPosition {
        return SummaryInjectionPosition.fromOrdinal(AppModel.summaryInjectionPosition)
    }

    private fun updateSummaryInt(
        value: String,
        minimum: Int,
        update: MainSettingsState.(Int) -> MainSettingsState
    ) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val intValue = value.toIntOrNull()?.coerceAtLeast(minimum) ?: minimum
        uiState.copy(
            settingsState = uiState.settingsState.update(intValue)
        ).setup()
    }

    private suspend fun ChatSession.toUiModel(character: Character?): MainChatSessionItem {
        val latestMessage = mChatRepository.getLatestMessageBySessionId(id)
        return MainChatSessionItem(
            id = id.toString(),
            characterId = characterId.toString(),
            characterName = character?.name.orEmpty().ifBlank { mContext.getString(R.string.unknown_character) },
            title = title,
            preview = latestMessage?.content?.stripThinkBlocks()?.takeIf { it.isNotBlank() } ?: mContext.getString(R.string.no_messages_yet),
            messageCount = mChatRepository.getMessageCountBySessionId(id),
            updatedAt = latestTime.formatTimestamp("MM-dd HH:mm")
        )
    }
}
