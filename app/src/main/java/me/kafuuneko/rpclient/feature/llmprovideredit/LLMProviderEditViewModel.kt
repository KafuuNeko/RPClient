package me.kafuuneko.rpclient.feature.llmprovideredit

import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.llmprovideredit.model.CredentialEditMode
import me.kafuuneko.rpclient.feature.llmprovideredit.model.LLMProviderCredentialResolver
import me.kafuuneko.rpclient.feature.llmprovideredit.model.LLMProviderEditForm
import me.kafuuneko.rpclient.feature.llmprovideredit.model.ProviderPreset
import me.kafuuneko.rpclient.feature.llmprovideredit.model.hasUnsavedChangesFrom
import me.kafuuneko.rpclient.feature.llmprovideredit.model.toEditForm
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditDialogState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditLoadState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditMode
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditModelCatalogState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditRequestExtensionsState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditTestState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditUiIntent
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditUiState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.llm.LLMClientFactory
import me.kafuuneko.rpclient.libs.llm.adapter.hasValidOpenRouterRoutingPreferences
import me.kafuuneko.rpclient.libs.llm.adapter.protectedRequestBodyPaths
import me.kafuuneko.rpclient.libs.llm.adapter.readOpenRouterRoutingPreferences
import me.kafuuneko.rpclient.libs.llm.adapter.validateRequestBodyPatch
import me.kafuuneko.rpclient.libs.llm.adapter.withOpenRouterFallbacks
import me.kafuuneko.rpclient.libs.llm.adapter.withOpenRouterPreferredProvider
import me.kafuuneko.rpclient.libs.llm.adapter.withOpenRouterPreferredProviderEnabled
import me.kafuuneko.rpclient.libs.llm.catalog.LLMModelCatalogRepository
import me.kafuuneko.rpclient.libs.llm.catalog.classifyModelCatalogFailure
import me.kafuuneko.rpclient.libs.llm.catalog.model.LLMAvailableModel
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderCapabilities
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.MAX_TOKEN_ESTIMATE_RESERVE_PERCENT
import me.kafuuneko.rpclient.libs.room.entity.MIN_TOKEN_ESTIMATE_RESERVE_PERCENT
import me.kafuuneko.rpclient.libs.room.entity.toConfig
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.utils.formatJsonPretty
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 模型配置编辑页状态持有者。
 *
 * 核心职责：
 * - 管理模型提供商全量配置（基础信息、协议类型、Base URL、认证凭据、模型名称、生成参数、Token 预算等）；
 * - 维护预设模板（Provider Preset）的一键套用与能力参数（Capabilities）自动适配；
 * - 调度在线可用模型列表（Model Catalog）的异步查询、分类错误提示与模糊搜索弹窗；
 * - 提供即时连通性测试（Test Connectivity）及结果渲染；
 * - 管理请求头与请求体补丁（RequestBodyPatch）JSON 格式校验与 OpenRouter 路由偏好快捷配置；
 * - 安全管理 API Key 临时替换态与敏感内存清理。
 */
class LLMProviderEditViewModel :
    CoreViewModelWithEvent<LLMProviderEditUiIntent, LLMProviderEditUiState>(
        LLMProviderEditUiState.None
    ), KoinComponent {
    private val mLLMRepository by inject<LLMRepository>()
    private val mLLMClientFactory by inject<LLMClientFactory>()
    private val mModelCatalogRepository by inject<LLMModelCatalogRepository>()

    /** 当前连接测试任务；重复测试或离开页面时用于取消旧请求。 */
    private var mTestJob: Job? = null

    /** 模型目录查询与生成测试互不替代，因此使用独立任务管理取消。 */
    private var mModelCatalogJob: Job? = null
    private var mApiKeyReplacement: String? = null
    private var mInitialApiKey = ""
    private var mInitialCustomHeaders = ""

    /** 初始化编辑页，依据传入的提供商 ID 读取配置或展示空白创建表单。 */
    @UiIntentObserver(LLMProviderEditUiIntent.Init::class)
    private suspend fun onInit(intent: LLMProviderEditUiIntent.Init) {
        if (!isStateOf<LLMProviderEditUiState.None>()) return
        val provider = intent.providerId?.let { mLLMRepository.getProviderById(it) }
        mInitialApiKey = provider?.apiKey.orEmpty()
        mInitialCustomHeaders = provider?.customHeadersJson.orEmpty()
        val form = provider?.toEditForm() ?: LLMProviderEditForm()
        LLMProviderEditUiState.Normal(
            mode = if (provider == null) LLMProviderEditMode.Create else LLMProviderEditMode.Edit,
            form = form,
            requestExtensionsState = form.toRequestExtensionsState()
        ).setup()
    }

    /** 处理返回操作，检查未保存修改并弹出二次确认弹窗。 */
    @UiIntentObserver(LLMProviderEditUiIntent.Back::class)
    private fun onBack() {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        if (uiState.loadState is LLMProviderEditLoadState.Saving) return
        // 取消正在进行的网络测试与目录拉取任务
        cancelNetworkTasks()
        // 存在未保存修改时弹出防误退确认弹窗
        if (uiState.form.hasUnsavedChangesFrom(uiState.initialForm)) {
            uiState.copy(
                testState = LLMProviderEditTestState.None,
                modelCatalogState = if (
                    uiState.modelCatalogState is LLMProviderEditModelCatalogState.Loading
                ) {
                    LLMProviderEditModelCatalogState.Idle
                } else {
                    uiState.modelCatalogState
                },
                dialogState = LLMProviderEditDialogState.UnsavedChangesConfirm
            ).setup()
            return
        }
        finishPage()
    }

    /** 套用内置服务商预设模板（如 OpenAI、Claude、Gemini、OpenRouter 等）。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ApplyPresetTemplate::class)
    private fun onApplyPresetTemplate(intent: LLMProviderEditUiIntent.ApplyPresetTemplate) {
        val preset = intent.preset
        // 获取协议对应的默认能力开关（如是否默认发送 temperature/top_p）
        val capabilities = LLMProviderCapabilities.forProtocol(preset.protocol)
        val formattedPatch = formatJsonPretty(preset.defaultRequestBodyPatchJson).ifBlank { "{}" }
        // 更新表单字段并重置模型目录状态
        updateForm(invalidateModelCatalog = true) {
            copy(
                name = if (name.isBlank() || ProviderPreset.entries.any { it.displayName == name }) preset.displayName else name,
                providerType = preset.providerType,
                protocol = preset.protocol,
                baseUrl = preset.baseUrl,
                model = preset.defaultModel,
                requestBodyPatchJson = formattedPatch,
                sendTemperature = capabilities.defaultSendTemperature,
                sendTopP = capabilities.defaultSendTopP,
                useServerReportedUsage = preset.defaultUseServerReportedUsage
            )
        }
    }

    /** 修改模型配置显示名称。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ChangeName::class)
    private fun onChangeName(intent: LLMProviderEditUiIntent.ChangeName) =
        updateForm { copy(name = intent.value) }

    /** 修改服务提供商类型（Provider Type）。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ChangeProviderType::class)
    private fun onChangeProviderType(intent: LLMProviderEditUiIntent.ChangeProviderType) =
        updateForm(invalidateModelCatalog = true) {
            copy(providerType = intent.value)
        }

    /** 修改通信协议（Protocol）。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ChangeProtocol::class)
    private fun onChangeProtocol(intent: LLMProviderEditUiIntent.ChangeProtocol) =
        updateForm(invalidateModelCatalog = true) {
            val capabilities = LLMProviderCapabilities.forProtocol(intent.value)
            copy(
                protocol = intent.value,
                sendTemperature = capabilities.defaultSendTemperature,
                sendTopP = capabilities.defaultSendTopP
            )
        }

    /** 修改接口基础地址（Base URL）。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ChangeBaseUrl::class)
    private fun onChangeBaseUrl(intent: LLMProviderEditUiIntent.ChangeBaseUrl) =
        updateForm(invalidateModelCatalog = true) {
            copy(baseUrl = intent.value)
        }

    /** 弹出 API Key 凭据编辑弹窗。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ShowApiKeyEditor::class)
    private fun onShowApiKeyEditor() = showDialog(LLMProviderEditDialogState.ApiKeyEditor)

    /** 确认替换为新的 API Key。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ConfirmApiKeyReplacement::class)
    private fun onConfirmApiKeyReplacement(
        intent: LLMProviderEditUiIntent.ConfirmApiKeyReplacement
    ) {
        if (intent.value.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.api_key_required).tryEmit()
            return
        }
        mApiKeyReplacement = intent.value
        updateForm(invalidateModelCatalog = true) {
            copy(apiKeyEditMode = CredentialEditMode.Replace)
        }
        closeDialog()
    }

    /** 清空当前已配置的 API Key。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ClearApiKey::class)
    private fun onClearApiKey() {
        mApiKeyReplacement = null
        updateForm(invalidateModelCatalog = true) {
            copy(apiKeyEditMode = CredentialEditMode.Clear)
        }
    }

    /** 保持已持久化的原 API Key 不变。 */
    @UiIntentObserver(LLMProviderEditUiIntent.KeepExistingApiKey::class)
    private fun onKeepExistingApiKey() {
        mApiKeyReplacement = null
        updateForm(invalidateModelCatalog = true) {
            copy(apiKeyEditMode = CredentialEditMode.KeepExisting)
        }
    }

    /** 手动修改目标模型标识名称（Model）。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ChangeModel::class)
    private fun onChangeModel(intent: LLMProviderEditUiIntent.ChangeModel) =
        updateForm { copy(model = intent.value) }

    /** 在线拉取服务商支持的全部可用模型目录。 */
    @UiIntentObserver(LLMProviderEditUiIntent.QueryModels::class)
    private fun onQueryModels() {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        if (mModelCatalogJob?.isActive == true) return
        // 校验 Base URL 与认证参数
        val provider = uiState.form.toCatalogConfigOrNullWithToast() ?: return
        // 进入模型目录拉取中状态
        uiState.copy(
            modelCatalogState = LLMProviderEditModelCatalogState.Loading
        ).setup()
        mModelCatalogJob = viewModelScope.launch {
            val runningJob = currentCoroutineContext()[Job]
            try {
                // 在 IO 线程请求接口获取模型列表
                val models = withContext(Dispatchers.IO) {
                    mModelCatalogRepository.listModels(provider)
                }
                val latestState =
                    getOrNull<LLMProviderEditUiState.Normal>() ?: return@launch
                // 更新加载成功的模型列表
                latestState.copy(
                    modelCatalogState = LLMProviderEditModelCatalogState.Loaded(
                        models = models
                    )
                ).setup()
            } catch (_: CancellationException) {
                // 用户主动取消或修改连接配置时，不应显示查询失败
            } catch (throwable: Throwable) {
                // 分类错误类型并展示对应的失败状态
                val failure = classifyModelCatalogFailure(throwable)
                    ?: return@launch
                val latestState =
                    getOrNull<LLMProviderEditUiState.Normal>() ?: return@launch
                latestState.copy(
                    modelCatalogState = LLMProviderEditModelCatalogState.Failed(
                        failure = failure
                    )
                ).setup()
            } finally {
                if (mModelCatalogJob === runningJob) mModelCatalogJob = null
            }
        }
    }

    /** 取消当前正在执行的模型目录查询任务。 */
    @UiIntentObserver(LLMProviderEditUiIntent.CancelModelQuery::class)
    private fun onCancelModelQuery() {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        cancelModelCatalogQuery()
        if (uiState.modelCatalogState is LLMProviderEditModelCatalogState.Loading) {
            uiState.copy(
                modelCatalogState = LLMProviderEditModelCatalogState.Idle
            ).setup()
        }
    }

    /** 打开可用模型选择弹窗。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ShowModelPicker::class)
    private fun onShowModelPicker() {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        val catalogState = uiState.modelCatalogState
                as? LLMProviderEditModelCatalogState.Loaded
            ?: return
        if (catalogState.models.isEmpty()) return
        uiState.copy(
            dialogState = LLMProviderEditDialogState.ModelPicker(
                searchQuery = "",
                items = catalogState.models
            )
        ).setup()
    }

    /** 在模型选择弹窗中根据关键词过滤模型。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ChangeModelSearch::class)
    private fun onChangeModelSearch(intent: LLMProviderEditUiIntent.ChangeModelSearch) {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        val dialogState = uiState.dialogState
                as? LLMProviderEditDialogState.ModelPicker
            ?: return
        val models = (
                uiState.modelCatalogState as? LLMProviderEditModelCatalogState.Loaded
                )?.models ?: return
        uiState.copy(
            dialogState = dialogState.copy(
                searchQuery = intent.value,
                items = models.filterForSearch(intent.value)
            )
        ).setup()
    }

    /** 在弹窗中选中某个模型并填入表单。 */
    @UiIntentObserver(LLMProviderEditUiIntent.SelectAvailableModel::class)
    private fun onSelectAvailableModel(intent: LLMProviderEditUiIntent.SelectAvailableModel) {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        if (uiState.dialogState !is LLMProviderEditDialogState.ModelPicker) return
        updateForm { copy(model = intent.modelId) }
        closeDialog()
    }

    /** 打开自定义 HTTP Header 编辑弹窗。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ShowCustomHeadersEditor::class)
    private fun onShowCustomHeadersEditor() {
        val form = getOrNull<LLMProviderEditUiState.Normal>()?.form ?: return
        showDialog(LLMProviderEditDialogState.CustomHeadersEditor(form.customHeadersJson))
    }

    /** 确认替换自定义 Header JSON 内容。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ConfirmCustomHeadersReplacement::class)
    private fun onConfirmCustomHeadersReplacement(
        intent: LLMProviderEditUiIntent.ConfirmCustomHeadersReplacement
    ) {
        val isObject = runCatching {
            JsonParser.parseString(intent.value).isJsonObject
        }.getOrDefault(false)
        if (!isObject) {
            AppViewEvent.PopupToastMessageByResId(R.string.custom_headers_json_invalid).tryEmit()
            return
        }
        val formatted = formatJsonPretty(intent.value)
        updateForm(invalidateModelCatalog = true) {
            copy(
                customHeadersEditMode = CredentialEditMode.Replace,
                customHeadersJson = formatted
            )
        }
        closeDialog()
    }

    /** 清空自定义 Header。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ClearCustomHeaders::class)
    private fun onClearCustomHeaders() {
        updateForm(invalidateModelCatalog = true) {
            copy(
                customHeadersEditMode = CredentialEditMode.Clear,
                customHeadersJson = ""
            )
        }
    }

    /** 保留已持久化的自定义 Header 不变。 */
    @UiIntentObserver(LLMProviderEditUiIntent.KeepExistingCustomHeaders::class)
    private fun onKeepExistingCustomHeaders() {
        val formatted = formatJsonPretty(mInitialCustomHeaders)
        updateForm(invalidateModelCatalog = true) {
            copy(
                customHeadersEditMode = CredentialEditMode.KeepExisting,
                customHeadersJson = formatted
            )
        }
    }

    /** 打开请求体补丁（RequestBodyPatch）编辑弹窗。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ShowRequestBodyPatchDialog::class)
    private fun onShowRequestBodyPatchDialog() {
        val form = getOrNull<LLMProviderEditUiState.Normal>()?.form ?: return
        showDialog(LLMProviderEditDialogState.RequestBodyPatchEditor(form.requestBodyPatchJson))
    }

    /** 校验并确认回写请求体补丁 JSON 字符串。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ConfirmRequestBodyPatch::class)
    private fun onConfirmRequestBodyPatch(intent: LLMProviderEditUiIntent.ConfirmRequestBodyPatch) {
        val form = getOrNull<LLMProviderEditUiState.Normal>()?.form ?: return
        val value = intent.value.trim().ifBlank { "{}" }
        // 校验补丁是否覆盖受保护的协议保留字段
        if (validateRequestBodyPatch(
                value,
                protectedRequestBodyPaths(form.protocol)
            ).isFailure
        ) {
            AppViewEvent.PopupToastMessageByResId(R.string.request_body_patch_invalid).tryEmit()
            return
        }
        // 校验 OpenRouter 专属路由配置合法性
        if (form.providerType == LLMProviderType.OpenRouter &&
            !value.hasValidOpenRouterRoutingPreferences()
        ) {
            AppViewEvent.PopupToastMessageByResId(R.string.request_body_patch_invalid).tryEmit()
            return
        }
        val formatted = formatJsonPretty(value).ifBlank { "{}" }
        updateForm {
            copy(requestBodyPatchJson = formatted)
        }
        closeDialog()
    }

    /** 切换 OpenRouter 优先提供商（Preferred Provider）开关。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ToggleOpenRouterPreferredProvider::class)
    private fun onToggleOpenRouterPreferredProvider(
        intent: LLMProviderEditUiIntent.ToggleOpenRouterPreferredProvider
    ) = updateForm {
        val updated = formatJsonPretty(
            requestBodyPatchJson.withOpenRouterPreferredProviderEnabled(intent.value)
        ).ifBlank { "{}" }
        copy(requestBodyPatchJson = updated)
    }

    /** 修改 OpenRouter 优先提供商名称。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ChangeOpenRouterPreferredProvider::class)
    private fun onChangeOpenRouterPreferredProvider(
        intent: LLMProviderEditUiIntent.ChangeOpenRouterPreferredProvider
    ) = updateForm {
        val updated = formatJsonPretty(
            requestBodyPatchJson.withOpenRouterPreferredProvider(intent.value)
        ).ifBlank { "{}" }
        copy(requestBodyPatchJson = updated)
    }

    /** 切换 OpenRouter 允许回退（Allow Fallbacks）开关。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ToggleOpenRouterFallbacks::class)
    private fun onToggleOpenRouterFallbacks(
        intent: LLMProviderEditUiIntent.ToggleOpenRouterFallbacks
    ) = updateForm {
        val updated = formatJsonPretty(
            requestBodyPatchJson.withOpenRouterFallbacks(intent.value)
        ).ifBlank { "{}" }
        copy(requestBodyPatchJson = updated)
    }

    /** 修改采样温度（Temperature）。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ChangeTemperature::class)
    private fun onChangeTemperature(intent: LLMProviderEditUiIntent.ChangeTemperature) =
        updateForm { copy(temperature = intent.value) }

    /** 修改核采样概率（Top P）。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ChangeTopP::class)
    private fun onChangeTopP(intent: LLMProviderEditUiIntent.ChangeTopP) =
        updateForm { copy(topP = intent.value) }

    /** 修改单次最大生成 Token 数（Max Tokens）。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ChangeMaxTokens::class)
    private fun onChangeMaxTokens(intent: LLMProviderEditUiIntent.ChangeMaxTokens) =
        updateForm { copy(maxTokens = intent.value) }

    /** 修改模型上下文总窗口大小（Context Tokens）。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ChangeContextTokens::class)
    private fun onChangeContextTokens(intent: LLMProviderEditUiIntent.ChangeContextTokens) =
        updateForm { copy(contextTokens = intent.value) }

    /** 修改 Token 估算预留比例（Token Estimate Reserve Percent）。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ChangeTokenEstimateReservePercent::class)
    private fun onChangeTokenEstimateReservePercent(
        intent: LLMProviderEditUiIntent.ChangeTokenEstimateReservePercent
    ) = updateForm { copy(tokenEstimateReservePercent = intent.value) }

    /** 切换是否在请求中显式携带 Temperature 参数。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ToggleSendTemperature::class)
    private fun onToggleSendTemperature(intent: LLMProviderEditUiIntent.ToggleSendTemperature) =
        updateForm { copy(sendTemperature = intent.value) }

    /** 切换是否在请求中显式携带 Top P 参数。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ToggleSendTopP::class)
    private fun onToggleSendTopP(intent: LLMProviderEditUiIntent.ToggleSendTopP) =
        updateForm { copy(sendTopP = intent.value) }

    /** 切换是否优先采用服务端上报的 Token 用量。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ToggleUseServerReportedUsage::class)
    private fun onToggleUseServerReportedUsage(
        intent: LLMProviderEditUiIntent.ToggleUseServerReportedUsage
    ) = updateForm { copy(useServerReportedUsage = intent.value) }

    /** 选择 Prompt 提示词后处理模式（如角色名转换、格式剥离等）。 */
    @UiIntentObserver(LLMProviderEditUiIntent.SelectPostProcessingMode::class)
    private fun onSelectPostProcessingMode(
        intent: LLMProviderEditUiIntent.SelectPostProcessingMode
    ) = updateForm { copy(promptPostProcessingMode = intent.value) }

    /** 切换全局启用/停用状态。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ToggleEnabled::class)
    private fun onToggleEnabled(intent: LLMProviderEditUiIntent.ToggleEnabled) =
        updateForm { copy(isEnabled = intent.value) }

    /** 校验完整表单并保存配置至数据库。 */
    @UiIntentObserver(LLMProviderEditUiIntent.SaveClick::class)
    private suspend fun onSaveClick() {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        val provider = uiState.form.toProviderOrNullWithToast() ?: return
        cancelNetworkTasks()
        uiState.copy(loadState = LLMProviderEditLoadState.Saving).setup()
        withContext(Dispatchers.IO) { mLLMRepository.saveProvider(provider) }
        AppViewEvent.PopupToastMessageByResId(
            if (uiState.mode == LLMProviderEditMode.Create) R.string.model_created else R.string.model_saved
        ).tryEmit()
        finishPage()
    }

    /** 发起即时连通性测试，发送短提示词验证模型接口连通性与凭据正确性。 */
    @UiIntentObserver(LLMProviderEditUiIntent.TestClick::class)
    private fun onTestClick() {
        if (!isStateOf<LLMProviderEditUiState.Normal>()) return
        if (mTestJob?.isActive == true) return
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        // 校验表单基本连接参数
        val provider = uiState.form.toProviderOrNullWithToast() ?: return
        uiState.copy(testState = LLMProviderEditTestState.Testing).setup()
        mTestJob = viewModelScope.launch {
            val runningJob = currentCoroutineContext()[Job]
            try {
                // 在 IO 线程创建 Client 发送测试探针
                val response = withContext(Dispatchers.IO) {
                    mLLMClientFactory.create(provider.toConfig()).generate(
                        "Please reply with a short English sentence: Model test successful."
                    )
                }
                val latestState = getOrNull<LLMProviderEditUiState.Normal>() ?: return@launch
                // 测试成功，展示模型响应内容
                latestState.copy(
                    testState = LLMProviderEditTestState.Success(
                        response.content.takeIf { it.isNotBlank() }
                    )
                ).setup()
            } catch (_: CancellationException) {
                // 用户主动取消测试属于正常操作，不展示失败
            } catch (_: Throwable) {
                val latestState = getOrNull<LLMProviderEditUiState.Normal>() ?: return@launch
                // 测试失败，展示错误状态
                latestState.copy(
                    testState = LLMProviderEditTestState.Failed
                ).setup()
            } finally {
                if (mTestJob === runningJob) mTestJob = null
            }
        }
    }

    /** 取消当前正在执行的连通性测试。 */
    @UiIntentObserver(LLMProviderEditUiIntent.CancelTest::class)
    private fun onCancelTest() {
        if (!isStateOf<LLMProviderEditUiState.Normal>()) return
        cancelTest()
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        if (uiState.testState is LLMProviderEditTestState.Testing) {
            uiState.copy(testState = LLMProviderEditTestState.None).setup()
        }
    }

    /** 取消连接测试协程。 */
    private fun cancelTest() {
        mTestJob?.cancel()
        mTestJob = null
    }

    override fun onCleared() {
        cancelNetworkTasks()
        clearSensitiveDrafts()
        super.onCleared()
    }

    /** 确认放弃未保存的修改并退出。 */
    @UiIntentObserver(LLMProviderEditUiIntent.ConfirmDiscardChanges::class)
    private fun onConfirmDiscardChanges() {
        if (!isStateOf<LLMProviderEditUiState.Normal>()) return
        cancelNetworkTasks()
        finishPage()
    }

    /** 关闭当前弹窗。 */
    @UiIntentObserver(LLMProviderEditUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        closeDialog()
    }

    /**
     * 统一更新表单字段，并清理测试结果。
     *
     * @param invalidateModelCatalog 是否同时使模型目录状态失效
     * @param block 表单更新闭包
     */
    private fun updateForm(
        invalidateModelCatalog: Boolean = false,
        block: LLMProviderEditForm.() -> LLMProviderEditForm
    ) {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        cancelTest()
        if (invalidateModelCatalog) cancelModelCatalogQuery()
        val updatedForm = uiState.form.block()
        uiState.copy(
            form = updatedForm,
            requestExtensionsState = updatedForm.toRequestExtensionsState(),
            testState = LLMProviderEditTestState.None,
            modelCatalogState = if (invalidateModelCatalog) {
                LLMProviderEditModelCatalogState.Idle
            } else {
                uiState.modelCatalogState
            }
        ).setup()
    }

    /** 将表单转换为用于模型目录查询的临时 Config 对象。 */
    private fun LLMProviderEditForm.toCatalogConfigOrNullWithToast(): LLMProviderConfig? {
        if (baseUrl.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.base_url_empty).tryEmit()
            return null
        }
        val apiKey = resolveApiKey() ?: return null
        return LLMProviderConfig(
            name = name.trim(),
            providerType = providerType,
            protocol = protocol,
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
            model = model.trim(),
            customHeadersJson = customHeadersJson.trim(),
            requestBodyPatchJson = requestBodyPatchJson.trim().ifBlank { "{}" }
        )
    }

    /**
     * 校验表单并转换为数据库实体，失败时给出对应提示。
     *
     * 校验规则：
     * - 名称、Base URL、Model 不能为空；
     * - Max Tokens 不能大于等于 Context Tokens；
     * - Token 估算预留比例需在合法范围；
     * - RequestBodyPatch 需为合法 JSON 且不能覆盖协议保留字段；
     * - 凭据需能正确解析。
     */
    private fun LLMProviderEditForm.toProviderOrNullWithToast(): LLMProvider? {
        // 校验基础必填项
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
        // 校验 Token 预算合理性
        val parsedMaxTokens = maxTokens.toIntOrNull()
        val parsedContextTokens = contextTokens.toIntOrNull()
        if (parsedMaxTokens != null &&
            parsedContextTokens != null &&
            parsedMaxTokens >= parsedContextTokens
        ) {
            AppViewEvent.PopupToastMessageByResId(
                R.string.max_tokens_must_be_less_than_context
            ).tryEmit()
            return null
        }
        // 校验预留百分比
        if (tokenEstimateReservePercent !in
            MIN_TOKEN_ESTIMATE_RESERVE_PERCENT..MAX_TOKEN_ESTIMATE_RESERVE_PERCENT
        ) {
            AppViewEvent.PopupToastMessageByResId(
                R.string.token_estimate_reserve_invalid
            ).tryEmit()
            return null
        }
        // 校验请求体补丁 JSON 格式与协议保护字段
        if (validateRequestBodyPatch(
                requestBodyPatchJson,
                protectedRequestBodyPaths(protocol)
            ).isFailure
        ) {
            AppViewEvent.PopupToastMessageByResId(R.string.request_body_patch_invalid).tryEmit()
            return null
        }
        // 校验 OpenRouter 专用路由偏好配置
        if (providerType == LLMProviderType.OpenRouter &&
            !requestBodyPatchJson.hasValidOpenRouterRoutingPreferences()
        ) {
            AppViewEvent.PopupToastMessageByResId(R.string.request_body_patch_invalid).tryEmit()
            return null
        }
        // 解析 API Key 凭据
        val apiKey = resolveApiKey() ?: return null
        val provider = toProviderOrNull(apiKey = apiKey)
        if (provider == null) {
            AppViewEvent.PopupToastMessageByResId(R.string.generation_params_invalid).tryEmit()
        }
        return provider
    }

    /** 依据当前的凭据编辑模式解析最终的 API Key 字符串。 */
    private fun LLMProviderEditForm.resolveApiKey() = LLMProviderCredentialResolver.resolveApiKey(
        form = this,
        initialApiKey = mInitialApiKey,
        apiKeyReplacement = mApiKeyReplacement
    )

    /** 提取 OpenRouter 专属路由配置状态。 */
    private fun LLMProviderEditForm.toRequestExtensionsState():
            LLMProviderEditRequestExtensionsState {
        val routing = requestBodyPatchJson.readOpenRouterRoutingPreferences()
        return LLMProviderEditRequestExtensionsState(
            isOpenRouter = providerType == LLMProviderType.OpenRouter,
            usesPreferredProvider = routing.usesPreferredProvider,
            preferredProvider = routing.preferredProvider,
            allowFallbacks = routing.allowFallbacks
        )
    }

    /** 在模型列表中按 ID 或显示名称进行大小写不敏感的搜索过滤。 */
    private fun List<LLMAvailableModel>.filterForSearch(
        query: String
    ): List<LLMAvailableModel> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return this
        return filter { model ->
            model.id.contains(normalizedQuery, ignoreCase = true) ||
                    model.displayName.contains(normalizedQuery, ignoreCase = true)
        }
    }

    /** 取消模型目录查询协程任务。 */
    private fun cancelModelCatalogQuery() {
        mModelCatalogJob?.cancel()
        mModelCatalogJob = null
    }

    /** 取消所有网络异步任务（连通性测试与模型目录查询）。 */
    private fun cancelNetworkTasks() {
        cancelTest()
        cancelModelCatalogQuery()
    }

    /** 显示指定弹窗。 */
    private fun showDialog(dialogState: LLMProviderEditDialogState) {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        uiState.copy(dialogState = dialogState).setup()
    }

    /** 关闭当前弹窗。 */
    private fun closeDialog() {
        val uiState = getOrNull<LLMProviderEditUiState.Normal>() ?: return
        uiState.copy(dialogState = LLMProviderEditDialogState.None).setup()
    }

    /** 结束编辑页并清理敏感凭据内存。 */
    private fun finishPage() {
        clearSensitiveDrafts()
        LLMProviderEditUiState.finished(uiStateFlow.value).setup()
    }

    /** 清理内存中暂存的敏感明文 API Key 与 Custom Headers。 */
    private fun clearSensitiveDrafts() {
        mApiKeyReplacement = null
        mInitialApiKey = ""
        mInitialCustomHeaders = ""
    }

}
