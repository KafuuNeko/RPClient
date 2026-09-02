package me.kafuuneko.rpclient.feature.llmproviderlist

import android.os.Bundle
import kotlinx.coroutines.CancellationException
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.llmprovideredit.LLMProviderEditActivity
import me.kafuuneko.rpclient.feature.llmproviderlist.model.LLMProviderListItem
import me.kafuuneko.rpclient.feature.llmproviderlist.presentation.LLMProviderListLoadState
import me.kafuuneko.rpclient.feature.llmproviderlist.presentation.LLMProviderListDialogState
import me.kafuuneko.rpclient.feature.llmproviderlist.presentation.LLMProviderListUiIntent
import me.kafuuneko.rpclient.feature.llmproviderlist.presentation.LLMProviderListUiState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 模型配置列表页状态持有者。
 *
 * 核心职责：
 * - 展示所有 LLM 服务提供商配置摘要及启用状态；
 * - 驱动新建与编辑模型配置页面的导航跳转；
 * - 从列表快速克隆完整模型配置；
 * - 支持快速切换提供商的全局启用/停用状态；
 * - 删除提供商前统计关联角色卡数量并弹出确认弹窗。
 */
class LLMProviderListViewModel : CoreViewModelWithEvent<LLMProviderListUiIntent, LLMProviderListUiState>(
    LLMProviderListUiState.None
), KoinComponent {
    private val mLLMRepository by inject<LLMRepository>()

    /** 初始化模型配置列表，进入加载中状态并从数据库拉取全部提供商。 */
    @UiIntentObserver(LLMProviderListUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<LLMProviderListUiState.None>()) return
        LLMProviderListUiState.Normal(
            providers = emptyList(),
            loadState = LLMProviderListLoadState.Loading
        ).setup()
        refreshProviders()
    }

    /** 页面恢复可见时重新拉取最新提供商数据。 */
    @UiIntentObserver(LLMProviderListUiIntent.Resume::class)
    private suspend fun onResume() {
        if (!isStateOf<LLMProviderListUiState.Normal>()) return
        refreshProviders()
    }

    /** 处理返回操作，迁移至 Finished 状态。 */
    @UiIntentObserver(LLMProviderListUiIntent.Back::class)
    private fun onBack() {
        if (isStateOf<LLMProviderListUiState.Finished>()) return
        LLMProviderListUiState.finished(uiStateFlow.value).setup()
    }

    /** 打开新建模型配置页面。 */
    @UiIntentObserver(LLMProviderListUiIntent.CreateProvider::class)
    private fun onCreateProvider() {
        if (!isStateOf<LLMProviderListUiState.Normal>()) return
        AppViewEvent.StartActivity(LLMProviderEditActivity::class.java).tryEmit()
    }

    /**
     * 打开指定模型配置的编辑页面。
     *
     * @param intent 包含目标提供商 ID 的意图
     */
    @UiIntentObserver(LLMProviderListUiIntent.EditProvider::class)
    private fun onEditProvider(intent: LLMProviderListUiIntent.EditProvider) {
        if (!isStateOf<LLMProviderListUiState.Normal>()) return
        val providerId = intent.providerId.toLongOrNull() ?: return
        AppViewEvent.StartActivity(
            activity = LLMProviderEditActivity::class.java,
            extras = Bundle().apply { putLong(LLMProviderEditActivity.EXTRA_PROVIDER_ID, providerId) }
        ).tryEmit()
    }

    /**
     * 克隆指定模型配置并刷新列表。
     *
     * @param intent 包含源模型配置 ID 的意图
     */
    @UiIntentObserver(LLMProviderListUiIntent.CloneProvider::class)
    private suspend fun onCloneProvider(intent: LLMProviderListUiIntent.CloneProvider) {
        if (!isStateOf<LLMProviderListUiState.Normal>()) return
        val providerId = intent.providerId.toLongOrNull() ?: return
        mLLMRepository.cloneProvider(providerId) ?: return
        refreshProviders()
        AppViewEvent.PopupToastMessageByResId(R.string.model_created).tryEmit()
    }

    /**
     * 快速切换指定模型配置的启用/停用状态。
     *
     * @param intent 包含提供商 ID 与目标启用状态的意图
     */
    @UiIntentObserver(LLMProviderListUiIntent.ToggleProviderEnabled::class)
    private suspend fun onToggleProviderEnabled(intent: LLMProviderListUiIntent.ToggleProviderEnabled) {
        if (!isStateOf<LLMProviderListUiState.Normal>()) return
        val providerId = intent.providerId.toLongOrNull() ?: return
        mLLMRepository.updateProviderEnabled(providerId, intent.isEnabled)
        refreshProviders()
    }

    /**
     * 查询关联角色数量并弹出删除模型配置确认弹窗。
     *
     * @param intent 包含目标提供商 ID 的意图
     */
    @UiIntentObserver(LLMProviderListUiIntent.ShowDeleteProviderDialog::class)
    private suspend fun onShowDeleteProviderDialog(
        intent: LLMProviderListUiIntent.ShowDeleteProviderDialog
    ) {
        val uiState = getOrNull<LLMProviderListUiState.Normal>() ?: return
        val providerId = intent.providerId.toLongOrNull() ?: return
        val provider = uiState.providers.firstOrNull { it.id == providerId } ?: return
        val associationCount = mLLMRepository.getCharacterAssociationCount(providerId)
        uiState.copy(
            dialogState = LLMProviderListDialogState.DeleteProvider(
                providerId = providerId,
                providerName = provider.name,
                associatedCharacterCount = associationCount
            )
        ).setup()
    }

    /** 用户确认删除模型配置，执行数据库删除并刷新列表。 */
    @UiIntentObserver(LLMProviderListUiIntent.ConfirmDeleteProvider::class)
    private suspend fun onConfirmDeleteProvider() {
        val uiState = getOrNull<LLMProviderListUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? LLMProviderListDialogState.DeleteProvider
            ?: return
        if (dialogState.isDeleting) return
        // 标记弹窗处于删除中状态，防止重复点击
        uiState.copy(dialogState = dialogState.copy(isDeleting = true)).setup()
        try {
            // 执行数据库物理删除并清空关联外键
            mLLMRepository.deleteProvider(dialogState.providerId)
            val current = getOrNull<LLMProviderListUiState.Normal>() ?: return
            current.copy(dialogState = LLMProviderListDialogState.None).setup()
            refreshProviders()
            AppViewEvent.PopupToastMessageByResId(R.string.model_config_deleted).tryEmit()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // 删除失败恢复弹窗状态并弹出提示
            AppViewEvent.PopupToastMessageByResId(R.string.model_config_delete_failed).tryEmit()
            val current = getOrNull<LLMProviderListUiState.Normal>() ?: return
            val currentDialog = current.dialogState as? LLMProviderListDialogState.DeleteProvider
                ?: return
            current.copy(dialogState = currentDialog.copy(isDeleting = false)).setup()
        }
    }

    /** 关闭当前显示的删除确认弹窗。 */
    @UiIntentObserver(LLMProviderListUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<LLMProviderListUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? LLMProviderListDialogState.DeleteProvider
            ?: return
        if (dialogState.isDeleting) return
        uiState.copy(dialogState = LLMProviderListDialogState.None).setup()
    }

    /** 从数据库拉取全部模型配置，并更新 UI 展示。 */
    private suspend fun refreshProviders() {
        val uiState = getOrNull<LLMProviderListUiState.Normal>() ?: return
        // 查询全部提供商并转换为列表项模型
        val providers = mLLMRepository.getAllProviders().map { provider ->
            LLMProviderListItem(
                id = provider.id,
                name = provider.name,
                providerType = provider.providerType,
                protocol = provider.protocol,
                baseUrl = provider.baseUrl,
                model = provider.model,
                isEnabled = provider.isEnabled
            )
        }
        // 更新列表展示并解除加载中状态
        uiState.copy(
            providers = providers,
            loadState = LLMProviderListLoadState.None
        ).setup()
    }
}
