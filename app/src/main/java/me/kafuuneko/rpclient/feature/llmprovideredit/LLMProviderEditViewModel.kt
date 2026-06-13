package me.kafuuneko.rpclient.feature.llmprovideredit

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.llmprovideredit.model.LLMProviderEditForm
import me.kafuuneko.rpclient.feature.llmprovideredit.model.hasUnsavedChangesFrom
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditDialogState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditLoadState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditMode
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditTestState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditUiIntent
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditUiState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.llm.LLMClientFactory
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderCapabilities
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.toConfig
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 模型供应商编辑页状态持有者，负责表单校验、连接测试与配置持久化。 */
class LLMProviderEditViewModel :
    CoreViewModelWithEvent<LLMProviderEditUiIntent, LLMProviderEditUiState>(
        LLMProviderEditUiState.None
    ), KoinComponent {
    private val mLLMRepository by inject<LLMRepository>()
    private val mLLMClientFactory by inject<LLMClientFactory>()
    /** 当前连接测试任务；重复测试或离开页面时用于取消旧请求。 */
    private var mTestJob: Job? = null

    @UiIntentObserver(LLMProviderEditUiIntent.Init::class)
    private suspend fun onInit(intent: LLMProviderEditUiIntent.Init) {
        if (!isStateOf<LLMProviderEditUiState.None>()) return
        val provider = intent.providerId?.let { mLLMRepository.getProviderById(it) }
        LLMProviderEditUiState.Normal(
            mode = if (provider == null) LLMProviderEditMode.Create else LLMProviderEditMode.Edit,

            form = provider?.let { LLMProviderEditForm.from(it) } ?: LLMProviderEditForm()
        ).setup()
    }

    @UiIntentObserver(LLMProviderEditUiIntent.Back::class)
    private fun onBack() {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        if (uiState.loadState is LLMProviderEditLoadState.Saving) return
        cancelTest()
        if (uiState.form.hasUnsavedChangesFrom(uiState.initialForm)) {
            uiState.copy(
                testState = LLMProviderEditTestState.None,
                dialogState = LLMProviderEditDialogState.UnsavedChangesConfirm
            ).setup()
            return
        }
        LLMProviderEditUiState.Finished.setup()
    }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeName::class)
    private fun onChangeName(intent: LLMProviderEditUiIntent.ChangeName) =
        updateForm { copy(name = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeProviderType::class)
    private fun onChangeProviderType(intent: LLMProviderEditUiIntent.ChangeProviderType) =
        updateForm { copy(providerType = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeProtocol::class)
    private fun onChangeProtocol(intent: LLMProviderEditUiIntent.ChangeProtocol) =
        updateForm {
            val capabilities = LLMProviderCapabilities.forProtocol(intent.value)
            copy(
                protocol = intent.value,
                sendTemperature = capabilities.defaultSendTemperature,
                sendTopP = capabilities.defaultSendTopP
            )
        }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeBaseUrl::class)
    private fun onChangeBaseUrl(intent: LLMProviderEditUiIntent.ChangeBaseUrl) =
        updateForm { copy(baseUrl = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeApiKey::class)
    private fun onChangeApiKey(intent: LLMProviderEditUiIntent.ChangeApiKey) =
        updateForm { copy(apiKey = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeModel::class)
    private fun onChangeModel(intent: LLMProviderEditUiIntent.ChangeModel) =
        updateForm { copy(model = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeCustomHeadersJson::class)
    private fun onChangeCustomHeadersJson(intent: LLMProviderEditUiIntent.ChangeCustomHeadersJson) =
        updateForm { copy(customHeadersJson = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeTemperature::class)
    private fun onChangeTemperature(intent: LLMProviderEditUiIntent.ChangeTemperature) =
        updateForm { copy(temperature = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeTopP::class)
    private fun onChangeTopP(intent: LLMProviderEditUiIntent.ChangeTopP) =
        updateForm { copy(topP = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeMaxTokens::class)
    private fun onChangeMaxTokens(intent: LLMProviderEditUiIntent.ChangeMaxTokens) =
        updateForm { copy(maxTokens = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeContextTokens::class)
    private fun onChangeContextTokens(intent: LLMProviderEditUiIntent.ChangeContextTokens) =
        updateForm { copy(contextTokens = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ToggleSendTemperature::class)
    private fun onToggleSendTemperature(intent: LLMProviderEditUiIntent.ToggleSendTemperature) =
        updateForm { copy(sendTemperature = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ToggleSendTopP::class)
    private fun onToggleSendTopP(intent: LLMProviderEditUiIntent.ToggleSendTopP) =
        updateForm { copy(sendTopP = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.SelectPostProcessingMode::class)
    private fun onSelectPostProcessingMode(
        intent: LLMProviderEditUiIntent.SelectPostProcessingMode
    ) = updateForm { copy(promptPostProcessingMode = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ToggleEnabled::class)
    private fun onToggleEnabled(intent: LLMProviderEditUiIntent.ToggleEnabled) =
        updateForm { copy(isEnabled = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.SaveClick::class)
    private suspend fun onSaveClick() {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        val provider = uiState.form.toProviderOrNullWithToast() ?: return
        cancelTest()
        uiState.copy(loadState = LLMProviderEditLoadState.Saving).setup()
        withContext(Dispatchers.IO) { mLLMRepository.saveProvider(provider) }
        AppViewEvent.PopupToastMessageByResId(
            if (uiState.mode == LLMProviderEditMode.Create) R.string.model_created else R.string.model_saved
        ).tryEmit()
        LLMProviderEditUiState.Finished.setup()
    }

    @UiIntentObserver(LLMProviderEditUiIntent.TestClick::class)
    private fun onTestClick() {
        if (mTestJob?.isActive == true) return
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        val provider = uiState.form.toProviderOrNullWithToast() ?: return
        uiState.copy(testState = LLMProviderEditTestState.Testing).setup()
        mTestJob = viewModelScope.launch {
            val runningJob = currentCoroutineContext()[Job]
            try {
                val response = withContext(Dispatchers.IO) {
                    mLLMClientFactory.create(provider.toConfig()).generate(
                        "Please reply with a short English sentence: Model test successful."
                    )
                }
                val latestState = getOrNull<LLMProviderEditUiState.Normal>() ?: return@launch
                latestState.copy(
                    testState = LLMProviderEditTestState.Success(
                        response.content.ifBlank { "Model test successful" }
                    )
                ).setup()
            } catch (_: CancellationException) {
                // Cancellation is an expected user action and should not be shown as a failure.
            } catch (throwable: Throwable) {
                val latestState = getOrNull<LLMProviderEditUiState.Normal>() ?: return@launch
                latestState.copy(
                    testState = LLMProviderEditTestState.Failed(
                        throwable.message ?: "Test failed"
                    )
                ).setup()
            } finally {
                if (mTestJob === runningJob) mTestJob = null
            }
        }
    }

    @UiIntentObserver(LLMProviderEditUiIntent.CancelTest::class)
    private fun onCancelTest() {
        cancelTest()
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        if (uiState.testState is LLMProviderEditTestState.Testing) {
            uiState.copy(testState = LLMProviderEditTestState.None).setup()
        }
    }

    private fun cancelTest() {
        mTestJob?.cancel()
        mTestJob = null
    }

    override fun onCleared() {
        cancelTest()
        super.onCleared()
    }

    @UiIntentObserver(LLMProviderEditUiIntent.ConfirmDiscardChanges::class)
    private fun onConfirmDiscardChanges() {
        cancelTest()
        LLMProviderEditUiState.Finished.setup()
    }

    @UiIntentObserver(LLMProviderEditUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        uiState.copy(dialogState = LLMProviderEditDialogState.None).setup()
    }

    /**
     * 统一更新表单字段，并清理测试结果。
     */
    private fun updateForm(block: LLMProviderEditForm.() -> LLMProviderEditForm) {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        cancelTest()
        uiState.copy(
            form = uiState.form.block(),
            testState = LLMProviderEditTestState.None
        ).setup()
    }

    /**
     * 校验表单并转换为数据库实体，失败时给出对应提示。
     */
    private fun LLMProviderEditForm.toProviderOrNullWithToast(): LLMProvider? {
        if (name.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.model_name_empty).tryEmit()
            return null
        }
        if (baseUrl.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.base_url_empty).tryEmit()
            return null
        }
        if (model.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.model_name_required).tryEmit()
            return null
        }
        val provider = toProviderOrNull()
        if (provider == null) {
            AppViewEvent.PopupToastMessageByResId(R.string.generation_params_invalid).tryEmit()
        }
        return provider
    }

}
