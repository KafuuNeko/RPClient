package me.kafuuneko.rpclient.feature.main

import android.os.Bundle
import me.kafuuneko.rpclient.feature.character.CharacterActivity
import me.kafuuneko.rpclient.feature.chat.ChatActivity
import me.kafuuneko.rpclient.feature.main.presentation.MainHomeState
import me.kafuuneko.rpclient.feature.main.presentation.MainPage
import me.kafuuneko.rpclient.feature.main.presentation.MainSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainUiIntent
import me.kafuuneko.rpclient.feature.main.presentation.MainUiState
import me.kafuuneko.rpclient.feature.worldbook.WorldBookActivity
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.model.ChatSessionUiModel
import me.kafuuneko.rpclient.libs.model.ProviderUiModel
import me.kafuuneko.rpclient.libs.model.RpCharacterUiModel

class MainViewModel : CoreViewModelWithEvent<MainUiIntent, MainUiState>(
    MainUiState.None
) {
    @UiIntentObserver(MainUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<MainUiState.None>()) return
        MainUiState.Normal(
            homeState = MainHomeState(
                greeting = "继续一段有角色记忆、有世界书约束的剧情。",
                activeCharacter = previewCharacters().first(),
                recentSessions = previewSessions(),
                totalCharacters = 24,
                totalWorldBooks = 7
            ),
            settingsState = MainSettingsState(
                selectedProviderId = "openrouter",
                providers = previewProviders(),
                temperature = 0.82f,
                maxTokens = 1200,
                contextTokens = 8192,
                localFirstEnabled = true,
                streamEnabled = true
            )
        ).setup()
    }

    @UiIntentObserver(MainUiIntent.Resume::class)
    private fun onResume() = Unit

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
        AppViewEvent.StartActivity(CharacterActivity::class.java).tryEmit()
    }

    @UiIntentObserver(MainUiIntent.OpenWorldBookManager::class)
    private fun onOpenWorldBookManager() {
        AppViewEvent.StartActivity(WorldBookActivity::class.java).tryEmit()
    }

    @UiIntentObserver(MainUiIntent.SelectProvider::class)
    private fun onSelectProvider(intent: MainUiIntent.SelectProvider) {
        val uiState = getOrNull<MainUiState.Normal>() ?: return
        uiState.copy(
            selectedPage = MainPage.Settings,
            settingsState = uiState.settingsState.copy(selectedProviderId = intent.providerId)
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

    private fun previewProviders() = listOf(
        ProviderUiModel(
            id = "openrouter",
            name = "OpenRouter",
            type = "OpenAI-compatible",
            endpoint = "https://openrouter.ai/api/v1",
            model = "anthropic/claude-sonnet",
            status = "可用",
            isEnabled = true
        ),
        ProviderUiModel(
            id = "ollama",
            name = "Ollama Local",
            type = "Local",
            endpoint = "http://127.0.0.1:11434",
            model = "qwen3:14b",
            status = "未连接",
            isEnabled = false
        )
    )
}

