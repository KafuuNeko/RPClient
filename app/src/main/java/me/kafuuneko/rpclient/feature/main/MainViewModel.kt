package me.kafuuneko.rpclient.feature.main

import android.os.Bundle
import me.kafuuneko.rpclient.feature.characterlist.CharacterListActivity
import me.kafuuneko.rpclient.feature.chat.ChatActivity
import me.kafuuneko.rpclient.feature.llmproviderlist.LLMProviderListActivity
import me.kafuuneko.rpclient.feature.promptpreset.PromptPresetActivity
import me.kafuuneko.rpclient.feature.main.presentation.MainHomeState
import me.kafuuneko.rpclient.feature.main.presentation.MainPage
import me.kafuuneko.rpclient.feature.main.presentation.MainSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainUiIntent
import me.kafuuneko.rpclient.feature.main.presentation.MainUiState
import me.kafuuneko.rpclient.feature.worldbooklist.WorldBookListActivity
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.model.ChatSessionUiModel
import me.kafuuneko.rpclient.libs.model.RpCharacterUiModel
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.libs.AppModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MainViewModel : CoreViewModelWithEvent<MainUiIntent, MainUiState>(
    MainUiState.None
), KoinComponent {
    private val mLLMRepository by inject<LLMRepository>()
    private val mLorebookRepository by inject<LorebookRepository>()

    @UiIntentObserver(MainUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<MainUiState.None>()) return
        val providers = mLLMRepository.getEnabledProviders()
        val totalWorldBooks = mLorebookRepository.getAllLorebooks().size
        val currentId = AppModel.currentLLMProvider
        val selectedProvider = providers.firstOrNull { it.id == currentId } ?: providers.firstOrNull()
        MainUiState.Normal(
            homeState = MainHomeState(
                activeCharacter = previewCharacters().first(),
                recentSessions = previewSessions(),
                totalCharacters = 24,
                totalWorldBooks = totalWorldBooks
            ),
            settingsState = MainSettingsState(
                selectedProviderId = selectedProvider?.id?.toString().orEmpty(),
                providers = providers,
                temperature = selectedProvider?.temperature ?: 0.8f,
                topP = selectedProvider?.topP ?: 1.0f,
                maxTokens = selectedProvider?.maxTokens ?: 1200,
                contextTokens = selectedProvider?.contextTokens ?: 8192,
                localFirstEnabled = true,
                streamEnabled = true
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.Resume::class)
    private suspend fun onResume() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        val providers = mLLMRepository.getEnabledProviders()
        val totalWorldBooks = mLorebookRepository.getAllLorebooks().size
        val currentId = AppModel.currentLLMProvider
        val selectedProvider = providers.firstOrNull { it.id == currentId } ?: providers.firstOrNull()
        uiState.copy(
            homeState = uiState.homeState.copy(totalWorldBooks = totalWorldBooks),
            settingsState = uiState.settingsState.copy(
                selectedProviderId = selectedProvider?.id?.toString().orEmpty(),
                providers = providers,
                temperature = selectedProvider?.temperature ?: 0f,
                topP = selectedProvider?.topP ?: 0f,
                maxTokens = selectedProvider?.maxTokens ?: 0,
                contextTokens = selectedProvider?.contextTokens ?: 0
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.Back::class)
    private fun onBack() {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        if (uiState.selectedPage != MainPage.Home) {
            uiState.copy(selectedPage = MainPage.Home).setup()
            return
        }
        MainUiState.Finished.setup()
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
                contextTokens = selectedProvider?.contextTokens ?: uiState.settingsState.contextTokens
            )
        ).setup()
    }

    private fun previewCharacters() = listOf(
        RpCharacterUiModel(
            id = "lyra",
            name = "Lyra",
            subtitle = "雾港档案管理员",
            description = "擅长从旧报纸、失踪人口记录和城市传说里找出线索。",
            avatarText = "L",
            tags = listOf("悬疑", "慢热", "现代奇幻"),
            sessions = 12,
            updatedAt = "18 分钟前",
            accentColor = 0xFF315EFD
        )
    )

    private fun previewSessions() = listOf(
        ChatSessionUiModel(
            id = "session-rain",
            characterName = "Lyra",
            title = "雨夜里的第七份卷宗",
            preview = "你把湿透的外套搭在椅背上，她已经把案卷翻到了失踪名单那一页。",
            messageCount = 186,
            branchCount = 3,
            updatedAt = "刚刚"
        ),
        ChatSessionUiModel(
            id = "session-clinic",
            characterName = "Noah",
            title = "越过白盐荒原",
            preview = "移动诊所的灯在风里摇晃，远处的车队只剩一个模糊的红点。",
            messageCount = 74,
            branchCount = 1,
            updatedAt = "昨天"
        )
    )

}
