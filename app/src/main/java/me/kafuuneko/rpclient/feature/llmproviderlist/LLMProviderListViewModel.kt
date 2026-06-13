package me.kafuuneko.rpclient.feature.llmproviderlist

import android.os.Bundle
import me.kafuuneko.rpclient.feature.llmprovideredit.LLMProviderEditActivity
import me.kafuuneko.rpclient.feature.llmproviderlist.presentation.LLMProviderListLoadState
import me.kafuuneko.rpclient.feature.llmproviderlist.presentation.LLMProviderListUiIntent
import me.kafuuneko.rpclient.feature.llmproviderlist.presentation.LLMProviderListUiState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 模型供应商列表页状态持有者，负责配置导航和启停状态同步。 */
class LLMProviderListViewModel : CoreViewModelWithEvent<LLMProviderListUiIntent, LLMProviderListUiState>(
    LLMProviderListUiState.None
), KoinComponent {
    private val mLLMRepository by inject<LLMRepository>()

    @UiIntentObserver(LLMProviderListUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<LLMProviderListUiState.None>()) return
        LLMProviderListUiState.Normal(
            providers = emptyList(),
            loadState = LLMProviderListLoadState.Loading
        ).setup()
        refreshProviders()
    }

    @UiIntentObserver(LLMProviderListUiIntent.Resume::class)
    private suspend fun onResume() {
        if (!isStateOf<LLMProviderListUiState.Normal>()) return
        refreshProviders()
    }

    @UiIntentObserver(LLMProviderListUiIntent.Back::class)
    private fun onBack() {
        LLMProviderListUiState.Finished.setup()
    }

    @UiIntentObserver(LLMProviderListUiIntent.CreateProvider::class)
    private fun onCreateProvider() {
        AppViewEvent.StartActivity(LLMProviderEditActivity::class.java).tryEmit()
    }

    @UiIntentObserver(LLMProviderListUiIntent.EditProvider::class)
    private fun onEditProvider(intent: LLMProviderListUiIntent.EditProvider) {
        val providerId = intent.providerId.toLongOrNull() ?: return
        AppViewEvent.StartActivity(
            activity = LLMProviderEditActivity::class.java,
            extras = Bundle().apply { putLong(LLMProviderEditActivity.EXTRA_PROVIDER_ID, providerId) }
        ).tryEmit()
    }

    @UiIntentObserver(LLMProviderListUiIntent.ToggleProviderEnabled::class)
    private suspend fun onToggleProviderEnabled(intent: LLMProviderListUiIntent.ToggleProviderEnabled) {
        val providerId = intent.providerId.toLongOrNull() ?: return
        mLLMRepository.updateProviderEnabled(providerId, intent.isEnabled)
        refreshProviders()
    }

    /**
     * 从数据库刷新完整模型列表。
     */
    private suspend fun refreshProviders() {
        val uiState = getOrNull<LLMProviderListUiState.Normal>() ?: return
        val providers = mLLMRepository.getAllProviders()
        uiState.copy(
            providers = providers,
            loadState = LLMProviderListLoadState.None
        ).setup()
    }
}
