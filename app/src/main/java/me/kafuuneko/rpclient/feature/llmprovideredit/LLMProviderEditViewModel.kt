package me.kafuuneko.rpclient.feature.llmprovideredit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.llmprovideredit.model.LLMProviderEditForm
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditLoadState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditMode
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditTestState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditUiIntent
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditUiState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LLMProviderEditViewModel : CoreViewModelWithEvent<LLMProviderEditUiIntent, LLMProviderEditUiState>(
    LLMProviderEditUiState.None
), KoinComponent {
    private val mLLMRepository by inject<LLMRepository>()

    @UiIntentObserver(LLMProviderEditUiIntent.Init::class)
    private suspend fun onInit(intent: LLMProviderEditUiIntent.Init) {
        if (!isStateOf<LLMProviderEditUiState.None>()) return
        val provider = intent.providerId?.let { mLLMRepository.getProviderById(it) }
        LLMProviderEditUiState.Normal(
            mode = if (provider == null) LLMProviderEditMode.Create else LLMProviderEditMode.Edit,
            form = provider?.toForm() ?: LLMProviderEditForm()
        ).setup()
    }

    @UiIntentObserver(LLMProviderEditUiIntent.Back::class)
    private fun onBack() {
        LLMProviderEditUiState.Finished.setup()
    }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeName::class)
    private fun onChangeName(intent: LLMProviderEditUiIntent.ChangeName) = updateForm { copy(name = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeProviderType::class)
    private fun onChangeProviderType(intent: LLMProviderEditUiIntent.ChangeProviderType) = updateForm { copy(providerType = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeProtocol::class)
    private fun onChangeProtocol(intent: LLMProviderEditUiIntent.ChangeProtocol) = updateForm { copy(protocol = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeBaseUrl::class)
    private fun onChangeBaseUrl(intent: LLMProviderEditUiIntent.ChangeBaseUrl) = updateForm { copy(baseUrl = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeApiKey::class)
    private fun onChangeApiKey(intent: LLMProviderEditUiIntent.ChangeApiKey) = updateForm { copy(apiKey = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeModel::class)
    private fun onChangeModel(intent: LLMProviderEditUiIntent.ChangeModel) = updateForm { copy(model = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeCustomHeadersJson::class)
    private fun onChangeCustomHeadersJson(intent: LLMProviderEditUiIntent.ChangeCustomHeadersJson) = updateForm { copy(customHeadersJson = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeTemperature::class)
    private fun onChangeTemperature(intent: LLMProviderEditUiIntent.ChangeTemperature) = updateForm { copy(temperature = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeMaxTokens::class)
    private fun onChangeMaxTokens(intent: LLMProviderEditUiIntent.ChangeMaxTokens) = updateForm { copy(maxTokens = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ChangeContextTokens::class)
    private fun onChangeContextTokens(intent: LLMProviderEditUiIntent.ChangeContextTokens) = updateForm { copy(contextTokens = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.ToggleEnabled::class)
    private fun onToggleEnabled(intent: LLMProviderEditUiIntent.ToggleEnabled) = updateForm { copy(isEnabled = intent.value) }

    @UiIntentObserver(LLMProviderEditUiIntent.SaveClick::class)
    private suspend fun onSaveClick() {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        val provider = uiState.form.toProviderOrNull() ?: return
        uiState.copy(loadState = LLMProviderEditLoadState.Saving).setup()
        withContext(Dispatchers.IO) { mLLMRepository.saveProvider(provider) }
        AppViewEvent.PopupToastMessageByResId(
            if (uiState.mode == LLMProviderEditMode.Create) R.string.model_created else R.string.model_saved
        ).tryEmit()
        LLMProviderEditUiState.Finished.setup()
    }

    @UiIntentObserver(LLMProviderEditUiIntent.TestClick::class)
    private suspend fun onTestClick() {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        val provider = uiState.form.toProviderOrNull() ?: return
        uiState.copy(testState = LLMProviderEditTestState.Testing).setup()
        val result = runCatching {
            withContext(Dispatchers.IO) {
                mLLMRepository.generateWithProvider(
                    provider = provider,
                    request = LLMGenerationRequest(
                        messages = listOf(
                            LLMMessage(
                                role = LLMMessageRole.User,
                                content = "Please reply with a short English sentence: Model test successful."
                            )
                        )
                    )
                )
            }
        }
        val latestState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        latestState.copy(
            testState = result.fold(
                onSuccess = { LLMProviderEditTestState.Success(it.content.ifBlank { "Model test successful" }) },
                onFailure = { LLMProviderEditTestState.Failed(it.message ?: "Test failed") }
            )
        ).setup()
    }

    /**
     * 统一更新表单字段，并清理测试结果。
     */
    private fun updateForm(block: LLMProviderEditForm.() -> LLMProviderEditForm) {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        uiState.copy(
            form = uiState.form.block(),
            testState = LLMProviderEditTestState.None
        ).setup()
    }

    /**
     * 将实体转换为表单状态。
     */
    private fun LLMProvider.toForm() = LLMProviderEditForm(
        id = id,
        createTime = createTime,
        name = name,
        providerType = providerType,
        protocol = protocol,
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
        customHeadersJson = customHeadersJson,
        temperature = temperature.toString(),
        maxTokens = maxTokens.toString(),
        contextTokens = contextTokens.toString(),
        isEnabled = isEnabled
    )

    /**
     * 校验并将表单转换为数据库实体。
     */
    private fun LLMProviderEditForm.toProviderOrNull(): LLMProvider? {
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
        val parsedTemperature = temperature.toFloatOrNull()
        val parsedMaxTokens = maxTokens.toIntOrNull()
        val parsedContextTokens = contextTokens.toIntOrNull()
        if (parsedTemperature == null || parsedMaxTokens == null || parsedContextTokens == null) {
            AppViewEvent.PopupToastMessageByResId(R.string.generation_params_invalid).tryEmit()
            return null
        }
        return LLMProvider(
            id = id,
            name = name.trim(),
            providerType = providerType,
            protocol = protocol,
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
            model = model.trim(),
            customHeadersJson = customHeadersJson.trim(),
            temperature = parsedTemperature,
            maxTokens = parsedMaxTokens,
            contextTokens = parsedContextTokens,
            isEnabled = isEnabled,
            createTime = createTime
        )
    }
}
