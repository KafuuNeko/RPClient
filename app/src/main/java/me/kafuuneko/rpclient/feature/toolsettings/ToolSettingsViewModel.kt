package me.kafuuneko.rpclient.feature.toolsettings

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.agent.AgentUiCoordinator
import me.kafuuneko.rpclient.feature.agent.model.AgentToolGroup
import me.kafuuneko.rpclient.feature.toolsettings.presentation.BuiltinParamDraft
import me.kafuuneko.rpclient.feature.toolsettings.presentation.BuiltinToolListItem
import me.kafuuneko.rpclient.feature.toolsettings.presentation.HttpBindingDraft
import me.kafuuneko.rpclient.feature.toolsettings.presentation.HttpToolListItem
import me.kafuuneko.rpclient.feature.toolsettings.presentation.KeyValueDraft
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolCodeEditorTarget
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolConnectionListItem
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsContentState
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsDialogState
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsEditorMode
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsTab
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsUiIntent
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsUiState
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsViewEvent
import me.kafuuneko.rpclient.libs.agent.connection.BuiltinToolOverrideCodec
import me.kafuuneko.rpclient.libs.agent.connection.BuiltinToolOverrideData
import me.kafuuneko.rpclient.libs.agent.connection.HttpAgentTools
import me.kafuuneko.rpclient.libs.agent.connection.HttpToolBinding
import me.kafuuneko.rpclient.libs.agent.connection.HttpToolConfigCodec
import me.kafuuneko.rpclient.libs.agent.connection.HttpToolConnection
import me.kafuuneko.rpclient.libs.agent.connection.HttpToolMapping
import me.kafuuneko.rpclient.libs.agent.connection.ToolConfigCodec
import me.kafuuneko.rpclient.libs.agent.connection.ToolConfigTransferRepository
import me.kafuuneko.rpclient.libs.agent.connection.ToolConfigValidationException
import me.kafuuneko.rpclient.libs.agent.connection.ToolCredentialRepository
import me.kafuuneko.rpclient.libs.agent.connection.ToolNetworkPolicy
import me.kafuuneko.rpclient.libs.agent.model.AgentJson
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.ToolSchema
import me.kafuuneko.rpclient.libs.agent.tool.ToolSchemaCodec
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.entity.ToolConnection
import me.kafuuneko.rpclient.libs.room.entity.ToolDefinition
import me.kafuuneko.rpclient.libs.room.repository.ToolConfigRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 工具配置页状态持有者与业务控制器。
 * - 管理 HTTP 连接、HTTP 工具、内置工具覆写、真实测试和 JSON 便携包导入导出。
 * - 密钥仅经一次性保存意图写入 Keystore，不进入页面状态树，复制连接时主动剥离旧凭据引用。
 * - 工具测试使用独立可取消任务，返回键或关闭弹窗会立即中止测试请求而非锁死页面。
 */
class ToolSettingsViewModel :
    CoreViewModelWithEvent<ToolSettingsUiIntent, ToolSettingsUiState>(ToolSettingsUiState.None),
    KoinComponent {
    private val mConfigurations by inject<ToolConfigRepository>()
    private val mCredentials by inject<ToolCredentialRepository>()
    private val mTools by inject<HttpAgentTools>()
    private val mTransfer by inject<ToolConfigTransferRepository>()
    private var mPendingExport: String? = null
    private var mTestJob: Job? = null

    /** 首次进入加载配置快照，屏幕旋转复用已有 ViewModel 状态。 */
    @UiIntentObserver(ToolSettingsUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<ToolSettingsUiState.None>()) return
        ToolSettingsUiState.Normal().setup()
        perform { reload() }
    }

    /** 处理返回：优先关闭子代码编辑器或中止测试并关闭弹窗，顶层返回结束页面。 */
    @UiIntentObserver(ToolSettingsUiIntent.Back::class)
    private fun onBack() {
        val state = getOrNull<ToolSettingsUiState.Normal>() ?: return
        val dialog = state.dialog
        if (dialog is ToolSettingsDialogState.ToolEditor && dialog.activeCodeEditor != null) {
            state.copy(dialog = dialog.copy(activeCodeEditor = null)).setup()
            return
        }
        if (dialog is ToolSettingsDialogState.Test) {
            cancelRunningTest()
            state.copy(dialog = ToolSettingsDialogState.None).setup()
            return
        }
        if (state.busy) return
        if (dialog != ToolSettingsDialogState.None) {
            state.copy(dialog = ToolSettingsDialogState.None).setup()
        } else {
            ToolSettingsUiState.Finished(state).setup()
        }
    }

    /** 切换管理分区。 */
    @UiIntentObserver(ToolSettingsUiIntent.SelectTab::class)
    private fun onSelectTab(intent: ToolSettingsUiIntent.SelectTab) {
        val state = ready() ?: return
        state.copy(content = state.content.copy(tab = intent.tab)).setup()
    }

    /** 打开空白 HTTP 连接新建表单。 */
    @UiIntentObserver(ToolSettingsUiIntent.CreateConnection::class)
    private fun onCreateConnection() {
        val state = ready() ?: return
        val draft = ToolSettingsDialogState.ConnectionEditor(
            id = UUID.randomUUID().toString(),
            mode = ToolSettingsEditorMode.Create,
        )
        state.copy(dialog = draft).setup()
    }

    /** 打开已有连接的结构化编辑表单。 */
    @UiIntentObserver(ToolSettingsUiIntent.EditConnection::class)
    private fun onEditConnection(intent: ToolSettingsUiIntent.EditConnection) = perform {
        val source = mConfigurations.connection(intent.id) ?: return@perform
        val editor = buildConnectionEditor(source, ToolSettingsEditorMode.Edit)
        getOrNull<ToolSettingsUiState.Normal>()?.copy(dialog = editor)?.setup()
    }

    /** 复制连接并清空原凭据引用，避免新目标主机意外继承旧站点密钥。 */
    @UiIntentObserver(ToolSettingsUiIntent.CopyConnection::class)
    private fun onCopyConnection(intent: ToolSettingsUiIntent.CopyConnection) = perform {
        val source = mConfigurations.connection(intent.id) ?: return@perform
        val copied = buildConnectionEditor(source, ToolSettingsEditorMode.Copy).copy(
            id = UUID.randomUUID().toString(),
            name = "${source.name} (2)",
            credentialRef = null,
            hasCredential = false,
        )
        getOrNull<ToolSettingsUiState.Normal>()?.copy(dialog = copied)?.setup()
    }

    /** 立即切换连接全局启停状态。 */
    @UiIntentObserver(ToolSettingsUiIntent.ToggleConnectionEnabled::class)
    private fun onToggleConnectionEnabled(intent: ToolSettingsUiIntent.ToggleConnectionEnabled) =
        perform {
            val connection = mConfigurations.connection(intent.id) ?: return@perform
            mConfigurations.save(connection.copy(enabled = intent.enabled))
            reload()
        }

    /** 打开连接删除确认框，同时提示受影响的绑定工具数量。 */
    @UiIntentObserver(ToolSettingsUiIntent.ShowDeleteConnection::class)
    private fun onShowDeleteConnection(intent: ToolSettingsUiIntent.ShowDeleteConnection) {
        val state = ready() ?: return
        val item = state.content.connections.firstOrNull { it.id == intent.id } ?: return
        state.copy(
            dialog = ToolSettingsDialogState.Delete(
                id = item.id,
                name = item.name,
                tab = ToolSettingsTab.Connections,
                affectedToolCount = item.boundToolCount,
            ),
        ).setup()
    }

    /** 打开连接加密凭据管理弹窗。 */
    @UiIntentObserver(ToolSettingsUiIntent.ShowCredentialDialog::class)
    private fun onShowCredentialDialog(intent: ToolSettingsUiIntent.ShowCredentialDialog) {
        val state = ready() ?: return
        val item = state.content.connections.firstOrNull { it.id == intent.connectionId } ?: return
        state.copy(
            dialog = ToolSettingsDialogState.Credential(
                connectionId = item.id,
                connectionName = item.name,
                authMode = item.authMode,
                authHeader = item.authHeader,
                hasExisting = item.hasCredential,
            ),
        ).setup()
    }

    /** 加密保存凭据并绑定至连接；无认证模式自动切为 Bearer，并清理未被引用的旧密文。 */
    @UiIntentObserver(ToolSettingsUiIntent.SaveCredential::class)
    private fun onSaveCredential(intent: ToolSettingsUiIntent.SaveCredential) = perform(
        onError = ::showDialogError,
    ) {
        val dialog = getOrNull<ToolSettingsUiState.Normal>()?.dialog
            as? ToolSettingsDialogState.Credential ?: return@perform
        val connection = mConfigurations.connection(dialog.connectionId) ?: return@perform
        val config = HttpToolConfigCodec.connection(connection.configJson)
        // 先写入新密文再更新连接引用，写库失败时立即回滚新建密文文件。
        val reference = mCredentials.save(intent.secret)
        try {
            val nextAuthMode = config.authMode.takeUnless { it == "None" } ?: "Bearer"
            val nextAuthHeader = config.authHeader.ifBlank {
                defaultAuthFieldName(nextAuthMode)
            }
            mConfigurations.save(
                connection.copy(
                    configJson = HttpToolConfigCodec.encode(
                        config.copy(
                            credentialRef = reference,
                            authMode = nextAuthMode,
                            authHeader = nextAuthHeader,
                        ),
                    ),
                ),
            )
        } catch (error: Exception) {
            mCredentials.delete(reference)
            throw error
        }
        pruneUnusedCredentials()
        closeDialog()
        reload()
        AppViewEvent.PopupToastMessageByResId(R.string.tool_settings_credential_saved).tryEmit()
    }

    /** 解绑当前连接的凭据并清理本机孤立密文文件。 */
    @UiIntentObserver(ToolSettingsUiIntent.ClearCredential::class)
    private fun onClearCredential() = perform(onError = ::showDialogError) {
        val dialog = getOrNull<ToolSettingsUiState.Normal>()?.dialog
            as? ToolSettingsDialogState.Credential ?: return@perform
        val connection = mConfigurations.connection(dialog.connectionId) ?: return@perform
        val config = HttpToolConfigCodec.connection(connection.configJson)
        mConfigurations.save(
            connection.copy(
                configJson = HttpToolConfigCodec.encode(config.copy(credentialRef = null)),
            ),
        )
        pruneUnusedCredentials()
        closeDialog()
        reload()
        AppViewEvent.PopupToastMessageByResId(R.string.tool_settings_credential_cleared).tryEmit()
    }

    /** 更新连接名称草稿。 */
    @UiIntentObserver(ToolSettingsUiIntent.ChangeConnectionName::class)
    private fun onChangeConnectionName(intent: ToolSettingsUiIntent.ChangeConnectionName) {
        updateConnectionDraft { it.copy(name = intent.value.take(120), errorRes = null) }
    }

    /** 更新连接目标 URL 草稿。 */
    @UiIntentObserver(ToolSettingsUiIntent.ChangeConnectionUrl::class)
    private fun onChangeConnectionUrl(intent: ToolSettingsUiIntent.ChangeConnectionUrl) {
        updateConnectionDraft { it.copy(url = intent.value.take(4096), errorRes = null) }
    }

    /** 切换连接 HTTP 方法。 */
    @UiIntentObserver(ToolSettingsUiIntent.SelectConnectionMethod::class)
    private fun onSelectConnectionMethod(intent: ToolSettingsUiIntent.SelectConnectionMethod) {
        updateConnectionDraft { it.copy(method = intent.method, errorRes = null) }
    }

    /** 切换连接认证方式，并在认证字段仍为默认值时自动适配建议名称。 */
    @UiIntentObserver(ToolSettingsUiIntent.SelectConnectionAuthMode::class)
    private fun onSelectConnectionAuthMode(intent: ToolSettingsUiIntent.SelectConnectionAuthMode) {
        updateConnectionDraft { draft ->
            val previousDefaults = setOf("", "Authorization", "X-API-Key", "api_key")
            val nextHeader = if (draft.authHeader in previousDefaults) {
                defaultAuthFieldName(intent.authMode)
            } else {
                draft.authHeader
            }
            draft.copy(authMode = intent.authMode, authHeader = nextHeader, errorRes = null)
        }
    }

    /** 更新连接认证头或 Query 认证参数名。 */
    @UiIntentObserver(ToolSettingsUiIntent.ChangeConnectionAuthHeader::class)
    private fun onChangeConnectionAuthHeader(intent: ToolSettingsUiIntent.ChangeConnectionAuthHeader) {
        updateConnectionDraft { it.copy(authHeader = intent.value.take(100), errorRes = null) }
    }

    /** 更新连接超时时间草稿（秒）。 */
    @UiIntentObserver(ToolSettingsUiIntent.ChangeConnectionTimeout::class)
    private fun onChangeConnectionTimeout(intent: ToolSettingsUiIntent.ChangeConnectionTimeout) {
        val digits = intent.value.filter { it.isDigit() }.take(3)
        updateConnectionDraft { it.copy(timeoutSeconds = digits, errorRes = null) }
    }

    /** 切换连接局域网/私有地址访问开关。 */
    @UiIntentObserver(ToolSettingsUiIntent.ToggleConnectionAllowPrivate::class)
    private fun onToggleConnectionAllowPrivate(intent: ToolSettingsUiIntent.ToggleConnectionAllowPrivate) {
        updateConnectionDraft { it.copy(allowPrivateAddresses = intent.enabled, errorRes = null) }
    }

    /** 追加一条连接静态 Query 参数草稿。 */
    @UiIntentObserver(ToolSettingsUiIntent.AddConnectionQuery::class)
    private fun onAddConnectionQuery() {
        updateConnectionDraft { it.copy(queryParams = it.queryParams + KeyValueDraft(), errorRes = null) }
    }

    /** 修改指定索引的静态 Query 参数。 */
    @UiIntentObserver(ToolSettingsUiIntent.UpdateConnectionQuery::class)
    private fun onUpdateConnectionQuery(intent: ToolSettingsUiIntent.UpdateConnectionQuery) {
        updateConnectionDraft { draft ->
            if (intent.index !in draft.queryParams.indices) return@updateConnectionDraft draft
            val updated = draft.queryParams.toMutableList()
            updated[intent.index] = KeyValueDraft(intent.key.take(128), intent.value.take(2048))
            draft.copy(queryParams = updated, errorRes = null)
        }
    }

    /** 删除指定索引的静态 Query 参数。 */
    @UiIntentObserver(ToolSettingsUiIntent.RemoveConnectionQuery::class)
    private fun onRemoveConnectionQuery(intent: ToolSettingsUiIntent.RemoveConnectionQuery) {
        updateConnectionDraft { draft ->
            if (intent.index !in draft.queryParams.indices) return@updateConnectionDraft draft
            draft.copy(
                queryParams = draft.queryParams.filterIndexed { idx, _ -> idx != intent.index },
                errorRes = null,
            )
        }
    }

    /** 追加一条连接静态请求头草稿。 */
    @UiIntentObserver(ToolSettingsUiIntent.AddConnectionHeader::class)
    private fun onAddConnectionHeader() {
        updateConnectionDraft { it.copy(headers = it.headers + KeyValueDraft(), errorRes = null) }
    }

    /** 修改指定索引的静态请求头。 */
    @UiIntentObserver(ToolSettingsUiIntent.UpdateConnectionHeader::class)
    private fun onUpdateConnectionHeader(intent: ToolSettingsUiIntent.UpdateConnectionHeader) {
        updateConnectionDraft { draft ->
            if (intent.index !in draft.headers.indices) return@updateConnectionDraft draft
            val updated = draft.headers.toMutableList()
            updated[intent.index] = KeyValueDraft(intent.key.take(128), intent.value.take(2048))
            draft.copy(headers = updated, errorRes = null)
        }
    }

    /** 删除指定索引的静态请求头。 */
    @UiIntentObserver(ToolSettingsUiIntent.RemoveConnectionHeader::class)
    private fun onRemoveConnectionHeader(intent: ToolSettingsUiIntent.RemoveConnectionHeader) {
        updateConnectionDraft { draft ->
            if (intent.index !in draft.headers.indices) return@updateConnectionDraft draft
            draft.copy(
                headers = draft.headers.filterIndexed { idx, _ -> idx != intent.index },
                errorRes = null,
            )
        }
    }

    /** 打开空白自定义 HTTP 工具表单。 */
    @UiIntentObserver(ToolSettingsUiIntent.CreateTool::class)
    private fun onCreateTool() {
        val state = ready() ?: return
        val editor = buildToolEditor(mConfigurations.newTool(), ToolSettingsEditorMode.Create)
        state.copy(dialog = editor).setup()
    }

    /** 打开网页搜索或网页读取模板表单；若默认函数名已存在则自动生成不冲突名称。 */
    @UiIntentObserver(ToolSettingsUiIntent.CreateTemplate::class)
    private fun onCreateTemplate(intent: ToolSettingsUiIntent.CreateTemplate) {
        val state = ready() ?: return
        val template = mConfigurations.template(intent.readUrl)
        val existingNames = state.content.tools.mapTo(HashSet()) { it.name }
        val uniqueName = suggestUniqueToolName(template.name, existingNames)
        val editor = buildToolEditor(
            template.copy(name = uniqueName),
            ToolSettingsEditorMode.Create,
        )
        state.copy(dialog = editor).setup()
    }

    /** 打开已有 HTTP 工具的结构化编辑表单。 */
    @UiIntentObserver(ToolSettingsUiIntent.EditTool::class)
    private fun onEditTool(intent: ToolSettingsUiIntent.EditTool) = perform {
        val source = mConfigurations.tool(intent.id) ?: return@perform
        val editor = buildToolEditor(source, ToolSettingsEditorMode.Edit)
        getOrNull<ToolSettingsUiState.Normal>()?.copy(dialog = editor)?.setup()
    }

    /** 复制 HTTP 工具并分配新稳定身份与不冲突函数名。 */
    @UiIntentObserver(ToolSettingsUiIntent.CopyTool::class)
    private fun onCopyTool(intent: ToolSettingsUiIntent.CopyTool) = perform {
        val source = mConfigurations.tool(intent.id) ?: return@perform
        val existingNames = mConfigurations.tools().mapTo(HashSet()) { it.name }
        val copyName = suggestUniqueToolName(source.name, existingNames)
        val copied = buildToolEditor(
            source.copy(id = UUID.randomUUID().toString(), name = copyName),
            ToolSettingsEditorMode.Copy,
        )
        getOrNull<ToolSettingsUiState.Normal>()?.copy(dialog = copied)?.setup()
    }

    /** 立即切换 HTTP 工具全局启停状态。 */
    @UiIntentObserver(ToolSettingsUiIntent.ToggleToolEnabled::class)
    private fun onToggleToolEnabled(intent: ToolSettingsUiIntent.ToggleToolEnabled) = perform {
        val tool = mConfigurations.tool(intent.id) ?: return@perform
        mConfigurations.save(tool.copy(enabled = intent.enabled))
        reload()
    }

    /** 打开 HTTP 工具删除确认框。 */
    @UiIntentObserver(ToolSettingsUiIntent.ShowDeleteTool::class)
    private fun onShowDeleteTool(intent: ToolSettingsUiIntent.ShowDeleteTool) {
        val state = ready() ?: return
        val item = state.content.tools.firstOrNull { it.id == intent.id } ?: return
        state.copy(
            dialog = ToolSettingsDialogState.Delete(
                id = item.id,
                name = item.name,
                tab = ToolSettingsTab.Tools,
            ),
        ).setup()
    }

    /** 更新 HTTP 工具模型函数名草稿。 */
    @UiIntentObserver(ToolSettingsUiIntent.ChangeToolName::class)
    private fun onChangeToolName(intent: ToolSettingsUiIntent.ChangeToolName) {
        updateToolDraft { it.copy(name = intent.value.take(64), errorRes = null) }
    }

    /** 更新 HTTP 工具面向模型的说明草稿。 */
    @UiIntentObserver(ToolSettingsUiIntent.ChangeToolDescription::class)
    private fun onChangeToolDescription(intent: ToolSettingsUiIntent.ChangeToolDescription) {
        updateToolDraft { it.copy(description = intent.value.take(8000), errorRes = null) }
    }

    /** 选择 HTTP 工具绑定的连接。 */
    @UiIntentObserver(ToolSettingsUiIntent.SelectToolConnection::class)
    private fun onSelectToolConnection(intent: ToolSettingsUiIntent.SelectToolConnection) {
        updateToolDraft { it.copy(connectionId = intent.id, errorRes = null) }
    }

    /** 更新响应数据根路径。 */
    @UiIntentObserver(ToolSettingsUiIntent.ChangeToolResponsePath::class)
    private fun onChangeToolResponsePath(intent: ToolSettingsUiIntent.ChangeToolResponsePath) {
        updateToolDraft { it.copy(responsePath = intent.value.take(1024), errorRes = null) }
    }

    /** 更新业务错误判断路径。 */
    @UiIntentObserver(ToolSettingsUiIntent.ChangeToolErrorPath::class)
    private fun onChangeToolErrorPath(intent: ToolSettingsUiIntent.ChangeToolErrorPath) {
        updateToolDraft { it.copy(errorPath = intent.value.take(1024), errorRes = null) }
    }

    /** 更新远端下一页游标路径。 */
    @UiIntentObserver(ToolSettingsUiIntent.ChangeToolNextCursorPath::class)
    private fun onChangeToolNextCursorPath(intent: ToolSettingsUiIntent.ChangeToolNextCursorPath) {
        updateToolDraft { it.copy(nextCursorPath = intent.value.take(1024), errorRes = null) }
    }

    /** 追加一条工具参数到请求的映射绑定。 */
    @UiIntentObserver(ToolSettingsUiIntent.AddToolBinding::class)
    private fun onAddToolBinding() {
        updateToolDraft { it.copy(bindings = it.bindings + HttpBindingDraft(), errorRes = null) }
    }

    /** 修改指定索引的参数绑定。 */
    @UiIntentObserver(ToolSettingsUiIntent.UpdateToolBinding::class)
    private fun onUpdateToolBinding(intent: ToolSettingsUiIntent.UpdateToolBinding) {
        updateToolDraft { draft ->
            if (intent.index !in draft.bindings.indices) return@updateToolDraft draft
            val updated = draft.bindings.toMutableList()
            updated[intent.index] = HttpBindingDraft(
                argument = intent.argument.take(256),
                target = intent.target,
                path = intent.path.take(256),
            )
            draft.copy(bindings = updated, errorRes = null)
        }
    }

    /** 删除指定索引的参数绑定。 */
    @UiIntentObserver(ToolSettingsUiIntent.RemoveToolBinding::class)
    private fun onRemoveToolBinding(intent: ToolSettingsUiIntent.RemoveToolBinding) {
        updateToolDraft { draft ->
            if (intent.index !in draft.bindings.indices) return@updateToolDraft draft
            draft.copy(
                bindings = draft.bindings.filterIndexed { idx, _ -> idx != intent.index },
                errorRes = null,
            )
        }
    }

    /** 追加一条响应结果字段投影。 */
    @UiIntentObserver(ToolSettingsUiIntent.AddToolField::class)
    private fun onAddToolField() {
        updateToolDraft { it.copy(fields = it.fields + KeyValueDraft(), errorRes = null) }
    }

    /** 修改指定索引的响应字段投影。 */
    @UiIntentObserver(ToolSettingsUiIntent.UpdateToolField::class)
    private fun onUpdateToolField(intent: ToolSettingsUiIntent.UpdateToolField) {
        updateToolDraft { draft ->
            if (intent.index !in draft.fields.indices) return@updateToolDraft draft
            val updated = draft.fields.toMutableList()
            updated[intent.index] = KeyValueDraft(intent.key.take(128), intent.value.take(512))
            draft.copy(fields = updated, errorRes = null)
        }
    }

    /** 删除指定索引的响应字段投影。 */
    @UiIntentObserver(ToolSettingsUiIntent.RemoveToolField::class)
    private fun onRemoveToolField(intent: ToolSettingsUiIntent.RemoveToolField) {
        updateToolDraft { draft ->
            if (intent.index !in draft.fields.indices) return@updateToolDraft draft
            draft.copy(
                fields = draft.fields.filterIndexed { idx, _ -> idx != intent.index },
                errorRes = null,
            )
        }
    }

    /** 在工具表单之上打开独立 JSON 代码编辑器，避免表单内双层滚动冲突。 */
    @UiIntentObserver(ToolSettingsUiIntent.OpenCodeEditor::class)
    private fun onOpenCodeEditor(intent: ToolSettingsUiIntent.OpenCodeEditor) {
        updateToolDraft { it.copy(activeCodeEditor = intent.target, errorRes = null) }
    }

    /** 确认独立 JSON 代码编辑器修改并刷新参数摘要预览。 */
    @UiIntentObserver(ToolSettingsUiIntent.ApplyCodeEditor::class)
    private fun onApplyCodeEditor(intent: ToolSettingsUiIntent.ApplyCodeEditor) {
        val text = intent.value.take(256 * 1024)
        updateToolDraft { draft ->
            when (intent.target) {
                ToolCodeEditorTarget.Parameters -> draft.copy(
                    parametersJson = text,
                    parameterSummary = summarizeSchema(text),
                    activeCodeEditor = null,
                    errorRes = null,
                )

                ToolCodeEditorTarget.RequestBody -> draft.copy(
                    bodyJson = text,
                    activeCodeEditor = null,
                    errorRes = null,
                )
            }
        }
    }

    /** 关闭独立 JSON 代码编辑器并保留原工具表单草稿。 */
    @UiIntentObserver(ToolSettingsUiIntent.DismissCodeEditor::class)
    private fun onDismissCodeEditor() {
        updateToolDraft { it.copy(activeCodeEditor = null) }
    }

    /** 打开内置工具的结构化覆写编辑器，展示原始说明与真实参数清单。 */
    @UiIntentObserver(ToolSettingsUiIntent.EditBuiltin::class)
    private fun onEditBuiltin(intent: ToolSettingsUiIntent.EditBuiltin) = perform {
        val definition = mTools.builtinDefinitions().firstOrNull { it.toolId == intent.toolId }
            ?: return@perform
        val existingRaw = mConfigurations.overrides().firstOrNull { it.toolId == intent.toolId }
            ?.configJson
        val override = runCatching { BuiltinToolOverrideCodec.decode(existingRaw) }
            .getOrDefault(BuiltinToolOverrideData())
        val defaultLabelRes = AgentUiCoordinator.TOOL_LABELS[definition.toolId]
            ?: R.string.tool_settings_builtins
        // 从权威 Schema 派生真实参数列表与允许调整的分页默认值键。
        val params = definition.parameters.properties.map { (paramName, schema) ->
            BuiltinParamDraft(
                name = paramName,
                typeLabel = schema.type.name.lowercase(),
                required = paramName in definition.parameters.required,
                defaultDescription = schema.description,
                overrideDescription = override.parameterDescriptions[paramName].orEmpty(),
            )
        }
        val editableDefaults = BuiltinToolOverrideCodec.editableDefaultKeys(definition.parameters)
        val editor = ToolSettingsDialogState.BuiltinEditor(
            toolId = definition.toolId,
            defaultLabelRes = defaultLabelRes,
            originalDescription = definition.description,
            displayName = override.displayName,
            description = override.description,
            usageHint = override.usageHint,
            parameters = params,
            editableDefaults = editableDefaults,
            defaultLimit = override.defaults["limit"]?.toString().orEmpty(),
            defaultOffset = override.defaults["offset"]?.toString().orEmpty(),
            customized = existingRaw != null,
        )
        getOrNull<ToolSettingsUiState.Normal>()?.copy(dialog = editor)?.setup()
    }

    /** 恢复单个内置工具的默认说明与默认参数。 */
    @UiIntentObserver(ToolSettingsUiIntent.ResetBuiltinOverride::class)
    private fun onResetBuiltinOverride(intent: ToolSettingsUiIntent.ResetBuiltinOverride) =
        perform {
            mConfigurations.resetOverride(intent.toolId)
            val state = getOrNull<ToolSettingsUiState.Normal>()
            if (state?.dialog is ToolSettingsDialogState.BuiltinEditor &&
                state.dialog.toolId == intent.toolId
            ) {
                closeDialog()
            }
            reload()
            AppViewEvent.PopupToastMessageByResId(R.string.tool_settings_reset_done).tryEmit()
        }

    /** 更新内置工具自定义显示名称草稿。 */
    @UiIntentObserver(ToolSettingsUiIntent.ChangeBuiltinDisplayName::class)
    private fun onChangeBuiltinDisplayName(intent: ToolSettingsUiIntent.ChangeBuiltinDisplayName) {
        updateBuiltinDraft { it.copy(displayName = intent.value.take(120), errorRes = null) }
    }

    /** 更新内置工具说明覆写草稿。 */
    @UiIntentObserver(ToolSettingsUiIntent.ChangeBuiltinDescription::class)
    private fun onChangeBuiltinDescription(intent: ToolSettingsUiIntent.ChangeBuiltinDescription) {
        updateBuiltinDraft { it.copy(description = intent.value.take(8000), errorRes = null) }
    }

    /** 更新内置工具附加提示草稿。 */
    @UiIntentObserver(ToolSettingsUiIntent.ChangeBuiltinUsageHint::class)
    private fun onChangeBuiltinUsageHint(intent: ToolSettingsUiIntent.ChangeBuiltinUsageHint) {
        updateBuiltinDraft { it.copy(usageHint = intent.value.take(8000), errorRes = null) }
    }

    /** 更新内置工具单个参数的说明覆写草稿。 */
    @UiIntentObserver(ToolSettingsUiIntent.ChangeBuiltinParamDescription::class)
    private fun onChangeBuiltinParamDescription(
        intent: ToolSettingsUiIntent.ChangeBuiltinParamDescription,
    ) {
        updateBuiltinDraft { draft ->
            draft.copy(
                parameters = draft.parameters.map { param ->
                    if (param.name == intent.name) {
                        param.copy(overrideDescription = intent.value.take(2000))
                    } else {
                        param
                    }
                },
                errorRes = null,
            )
        }
    }

    /** 更新内置工具可选分页默认值草稿。 */
    @UiIntentObserver(ToolSettingsUiIntent.ChangeBuiltinDefault::class)
    private fun onChangeBuiltinDefault(intent: ToolSettingsUiIntent.ChangeBuiltinDefault) {
        val digits = intent.value.filter { it.isDigit() }.take(6)
        updateBuiltinDraft { draft ->
            when (intent.key) {
                "limit" -> draft.copy(defaultLimit = digits, errorRes = null)
                "offset" -> draft.copy(defaultOffset = digits, errorRes = null)
                else -> draft
            }
        }
    }

    /** 完整校验并保存当前打开的连接、工具或内置覆写弹窗；失败时保留草稿并展示具体错误。 */
    @UiIntentObserver(ToolSettingsUiIntent.SaveDialog::class)
    private fun onSaveDialog() = perform(onError = ::showDialogError) {
        val dialog = getOrNull<ToolSettingsUiState.Normal>()?.dialog ?: return@perform
        when (dialog) {
            is ToolSettingsDialogState.ConnectionEditor -> saveConnectionDraft(dialog)
            is ToolSettingsDialogState.ToolEditor -> saveToolDraft(dialog)
            is ToolSettingsDialogState.BuiltinEditor -> saveBuiltinDraft(dialog)
            else -> return@perform
        }
        closeDialog()
        reload()
        AppViewEvent.PopupToastMessageByResId(R.string.tool_settings_saved).tryEmit()
    }

    /** 确认删除连接或 HTTP 工具，并清理孤立凭据密文。 */
    @UiIntentObserver(ToolSettingsUiIntent.ConfirmDelete::class)
    private fun onConfirmDelete() = perform {
        val dialog = getOrNull<ToolSettingsUiState.Normal>()?.dialog
            as? ToolSettingsDialogState.Delete ?: return@perform
        if (dialog.tab == ToolSettingsTab.Connections) {
            mConfigurations.deleteConnection(dialog.id)
            pruneUnusedCredentials()
        } else {
            mConfigurations.deleteTool(dialog.id)
        }
        closeDialog()
        reload()
        AppViewEvent.PopupToastMessageByResId(R.string.tool_settings_deleted).tryEmit()
    }

    /** 关闭当前弹窗，若正在测试则立即取消测试请求。 */
    @UiIntentObserver(ToolSettingsUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val state = getOrNull<ToolSettingsUiState.Normal>() ?: return
        if (state.dialog is ToolSettingsDialogState.Test) {
            cancelRunningTest()
            state.copy(dialog = ToolSettingsDialogState.None).setup()
            return
        }
        if (state.busy) return
        state.copy(dialog = ToolSettingsDialogState.None).setup()
    }

    /** 打开测试弹窗，并按工具参数 Schema 自动生成参数模板与提示。 */
    @UiIntentObserver(ToolSettingsUiIntent.OpenTestDialog::class)
    private fun onOpenTestDialog(intent: ToolSettingsUiIntent.OpenTestDialog) = perform {
        val tool = mConfigurations.tool(intent.toolId) ?: return@perform
        val connection = tool.connectionId?.let { mConfigurations.connection(it) }
        val config = connection?.let { runCatching { HttpToolConfigCodec.connection(it.configJson) }.getOrNull() }
        val destination = when {
            tool.kind == "ReadUrl" -> ""
            config != null && config.url.isNotBlank() -> {
                val host = runCatching { ToolNetworkPolicy.url(config.url).host }.getOrDefault("")
                if (host.isNotBlank()) "${config.method} $host" else ""
            }

            else -> ""
        }
        val testDialog = ToolSettingsDialogState.Test(
            toolId = tool.id,
            toolName = tool.name,
            kind = tool.kind,
            destination = destination,
            parameterHints = summarizeSchema(tool.parametersJson),
            arguments = defaultTestArguments(tool.parametersJson),
        )
        getOrNull<ToolSettingsUiState.Normal>()?.copy(dialog = testDialog)?.setup()
    }

    /** 更新测试参数 JSON 草稿。 */
    @UiIntentObserver(ToolSettingsUiIntent.ChangeTestArguments::class)
    private fun onChangeTestArguments(intent: ToolSettingsUiIntent.ChangeTestArguments) {
        val state = getOrNull<ToolSettingsUiState.Normal>() ?: return
        val test = state.dialog as? ToolSettingsDialogState.Test ?: return
        if (test.running) return
        state.copy(dialog = test.copy(arguments = intent.value.take(64 * 1024))).setup()
    }

    /** 在独立可取消协程中运行真实测试请求，返回脱敏且有界的映射预览。 */
    @UiIntentObserver(ToolSettingsUiIntent.RunTest::class)
    private fun onRunTest() {
        val state = getOrNull<ToolSettingsUiState.Normal>() ?: return
        val test = state.dialog as? ToolSettingsDialogState.Test ?: return
        if (test.running) return
        // 解析与校验测试参数失败时原地反馈具体错误，避免触发无效网络请求。
        val arguments = try {
            val parsed = AgentJson.parse(test.arguments)
            if (!parsed.isJsonObject) throw ToolConfigValidationException("invalid_json", "Expected JSON object")
            parsed.asJsonObject
        } catch (error: Exception) {
            state.copy(
                dialog = test.copy(
                    passed = false,
                    statusRes = validationErrorResource(error),
                    previewJson = "",
                    truncated = false,
                ),
            ).setup()
            return
        }
        state.copy(
            dialog = test.copy(
                running = true,
                passed = null,
                statusRes = null,
                previewJson = "",
                truncated = false,
            ),
        ).setup()
        mTestJob?.cancel()
        mTestJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { mTools.test(test.toolId, arguments) }
                applyTestResult(test.toolId, result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                applyTestFailure(test.toolId, validationErrorResource(error))
            } finally {
                val latest = getOrNull<ToolSettingsUiState.Normal>()
                val latestTest = latest?.dialog as? ToolSettingsDialogState.Test
                if (latestTest != null && latestTest.toolId == test.toolId && latestTest.running) {
                    latest.copy(dialog = latestTest.copy(running = false)).setup()
                }
            }
        }
    }

    /** 中止正在进行中的工具测试请求。 */
    @UiIntentObserver(ToolSettingsUiIntent.CancelTest::class)
    private fun onCancelTest() {
        cancelRunningTest()
        val state = getOrNull<ToolSettingsUiState.Normal>() ?: return
        val test = state.dialog as? ToolSettingsDialogState.Test ?: return
        state.copy(
            dialog = test.copy(
                running = false,
                passed = false,
                statusRes = R.string.tool_settings_test_cancelled,
            ),
        ).setup()
    }

    /** 打开系统导入器，取消选择不会影响已有配置。 */
    @UiIntentObserver(ToolSettingsUiIntent.ImportClick::class)
    private fun onImportClick() {
        val state = ready() ?: return
        if (state.choosingDocument) return
        state.copy(choosingDocument = true).setup()
        ToolSettingsViewEvent.OpenImporter.tryEmit()
    }

    /** 打开系统导出器前先冻结非敏感配置快照。 */
    @UiIntentObserver(ToolSettingsUiIntent.ExportClick::class)
    private fun onExportClick() = perform {
        mPendingExport = mConfigurations.export()
        getOrNull<ToolSettingsUiState.Normal>()?.copy(choosingDocument = true)?.setup()
        ToolSettingsViewEvent.OpenExporter.tryEmit()
    }

    /** 事务导入便携 JSON 包，校验失败整包回滚并给出具体错误提示。 */
    @UiIntentObserver(ToolSettingsUiIntent.ImportResult::class)
    private fun onImportResult(intent: ToolSettingsUiIntent.ImportResult) {
        getOrNull<ToolSettingsUiState.Normal>()?.copy(choosingDocument = false)?.setup()
        val uri = intent.uri ?: return
        perform {
            mConfigurations.import(mTransfer.read(uri), mTools::validateOverride)
            reload()
            AppViewEvent.PopupToastMessageByResId(R.string.tool_settings_import_success).tryEmit()
        }
    }

    /** 将冻结快照写入用户选择的导出文档。 */
    @UiIntentObserver(ToolSettingsUiIntent.ExportResult::class)
    private fun onExportResult(intent: ToolSettingsUiIntent.ExportResult) {
        getOrNull<ToolSettingsUiState.Normal>()?.copy(choosingDocument = false)?.setup()
        val json = mPendingExport
        mPendingExport = null
        val uri = intent.uri ?: return
        perform {
            mTransfer.write(uri, requireNotNull(json) { "Missing export snapshot" })
            AppViewEvent.PopupToastMessageByResId(R.string.tool_settings_export_success).tryEmit()
        }
    }

    /** 页面销毁时中止尚未结束的测试协程。 */
    override fun onCleared() {
        cancelRunningTest()
        super.onCleared()
    }

    /** 保存结构化连接表单至权威配置仓库。 */
    private suspend fun saveConnectionDraft(draft: ToolSettingsDialogState.ConnectionEditor) {
        val timeoutSeconds = draft.timeoutSeconds.trim().toLongOrNull()
            ?: throw ToolConfigValidationException("invalid_limits", "Invalid timeout")
        val maxResponseKiB = draft.maxResponseKiB.trim().toIntOrNull()
            ?: throw ToolConfigValidationException("invalid_limits", "Invalid max response size")
        // 忽略完全空白的键值对行；填写了键或值其中一项但另一项缺键时明确报错。
        val queryMap = linkedMapOf<String, String>()
        draft.queryParams.forEach { row ->
            val key = row.key.trim()
            if (key.isEmpty() && row.value.isEmpty()) return@forEach
            if (key.isEmpty()) throw ToolConfigValidationException("invalid_mapping", "Empty query key")
            queryMap[key] = row.value
        }
        val headerMap = linkedMapOf<String, String>()
        draft.headers.forEach { row ->
            val key = row.key.trim()
            if (key.isEmpty() && row.value.isEmpty()) return@forEach
            if (key.isEmpty()) throw ToolConfigValidationException("invalid_headers", "Empty header name")
            headerMap[key] = row.value
        }
        val config = HttpToolConnection(
            url = draft.url.trim(),
            method = draft.method,
            query = queryMap,
            headers = headerMap,
            authMode = draft.authMode,
            authHeader = draft.authHeader.trim().ifEmpty { defaultAuthFieldName(draft.authMode) },
            credentialRef = draft.credentialRef,
            timeoutMillis = timeoutSeconds * 1000L,
            maxResponseBytes = maxResponseKiB * 1024,
            allowPrivateAddresses = draft.allowPrivateAddresses,
        )
        mConfigurations.save(
            ToolConnection(
                id = draft.id,
                name = draft.name.trim(),
                configJson = HttpToolConfigCodec.encode(config),
                enabled = draft.enabled,
            ),
        )
    }

    /** 保存结构化 HTTP 工具表单至权威配置仓库。 */
    private suspend fun saveToolDraft(draft: ToolSettingsDialogState.ToolEditor) {
        val mappingJson = if (draft.kind == "ReadUrl") {
            "{}"
        } else {
            val bodyObject = ToolConfigCodec.parse(draft.bodyJson.ifBlank { "{}" })
            // 过滤空白绑定与字段投影，组装结构化映射再交由编解码器强校验。
            val bindings = draft.bindings.mapNotNull { row ->
                val argument = row.argument.trim()
                val path = row.path.trim()
                if (argument.isEmpty() && path.isEmpty()) return@mapNotNull null
                val normalizedArg = if (argument.startsWith('/')) argument else "/$argument"
                val normalizedPath = if (row.target == "body" && path.isNotEmpty() && !path.startsWith('/')) {
                    "/$path"
                } else {
                    path
                }
                HttpToolBinding(argument = normalizedArg, target = row.target, path = normalizedPath)
            }
            val fields = linkedMapOf<String, String>()
            draft.fields.forEach { row ->
                val key = row.key.trim()
                val path = row.value.trim()
                if (key.isEmpty() && path.isEmpty()) return@forEach
                if (key.isEmpty()) throw ToolConfigValidationException("invalid_mapping", "Empty field name")
                fields[key] = if (path.isEmpty() || path.startsWith('/')) path else "/$path"
            }
            val responsePath = draft.responsePath.trim().let { raw ->
                if (raw.isEmpty() || raw.startsWith('/')) raw else "/$raw"
            }
            val errorPath = draft.errorPath.trim().takeIf { it.isNotEmpty() }?.let { raw ->
                if (raw.startsWith('/')) raw else "/$raw"
            }
            val nextCursorPath = draft.nextCursorPath.trim().takeIf { it.isNotEmpty() }?.let { raw ->
                if (raw.startsWith('/')) raw else "/$raw"
            }
            HttpToolConfigCodec.encode(
                HttpToolMapping(
                    body = bodyObject,
                    bindings = bindings,
                    responsePath = responsePath,
                    errorPath = errorPath,
                    nextCursorPath = nextCursorPath,
                    fields = fields,
                ),
            )
        }
        mConfigurations.save(
            ToolDefinition(
                id = draft.id,
                name = draft.name.trim(),
                description = draft.description.trim(),
                parametersJson = draft.parametersJson,
                mappingJson = mappingJson,
                connectionId = if (draft.kind == "ReadUrl") null else draft.connectionId,
                kind = draft.kind,
                enabled = draft.enabled,
            ),
        )
    }

    /** 保存内置工具稀疏覆写表单；全部清空时自动恢复内置默认配置。 */
    private suspend fun saveBuiltinDraft(draft: ToolSettingsDialogState.BuiltinEditor) {
        val defaults = linkedMapOf<String, Int>()
        if ("limit" in draft.editableDefaults && draft.defaultLimit.isNotBlank()) {
            defaults["limit"] = draft.defaultLimit.trim().toIntOrNull()
                ?: throw ToolConfigValidationException("invalid_override", "Invalid default limit")
        }
        if ("offset" in draft.editableDefaults && draft.defaultOffset.isNotBlank()) {
            defaults["offset"] = draft.defaultOffset.trim().toIntOrNull()
                ?: throw ToolConfigValidationException("invalid_override", "Invalid default offset")
        }
        val paramDescriptions = draft.parameters
            .associate { it.name to it.overrideDescription.trim() }
            .filterValues { it.isNotEmpty() }
        val encoded = BuiltinToolOverrideCodec.encode(
            BuiltinToolOverrideData(
                displayName = draft.displayName.trim(),
                description = draft.description.trim(),
                usageHint = draft.usageHint.trim(),
                parameterDescriptions = paramDescriptions,
                defaults = defaults,
            ),
        )
        mTools.validateOverride(draft.toolId, encoded)
        mConfigurations.saveOverride(draft.toolId, encoded)
    }

    /** 从数据库重新构建连接、HTTP 工具与内置工具的非敏感摘要列表。 */
    private suspend fun reload() {
        val rawConnections = mConfigurations.connections()
        val rawTools = mConfigurations.tools()
        val boundCounts = rawTools.mapNotNull { it.connectionId }.groupingBy { it }.eachCount()
        // 逐个检查连接 URL 与本机凭据可用性，不将密钥正文放入 UI 模型。
        val connectionItems = rawConnections.map { connection ->
            val config = runCatching { HttpToolConfigCodec.connection(connection.configJson) }
                .getOrElse { HttpToolConnection("") }
            val hasCredential = config.credentialRef?.let { mCredentials.read(it) != null } == true
            val missing = config.url.isBlank() || (config.authMode != "None" && !hasCredential)
            ToolConnectionListItem(
                id = connection.id,
                name = connection.name,
                url = config.url,
                method = config.method,
                authMode = config.authMode,
                authHeader = config.authHeader,
                hasCredential = hasCredential,
                allowPrivateAddresses = config.allowPrivateAddresses,
                enabled = connection.enabled,
                needsConfiguration = missing,
                boundToolCount = boundCounts[connection.id] ?: 0,
            )
        }
        val connectionMap = connectionItems.associateBy { it.id }
        val toolItems = rawTools.map { tool ->
            val bound = tool.connectionId?.let(connectionMap::get)
            val missing = tool.kind != "ReadUrl" &&
                (bound == null || !bound.enabled || bound.needsConfiguration)
            HttpToolListItem(
                id = tool.id,
                name = tool.name,
                description = tool.description,
                kind = tool.kind,
                enabled = tool.enabled,
                connectionName = bound?.name,
                connectionMethod = bound?.method,
                needsConfiguration = missing,
            )
        }
        // 内置工具按统一顺序展示本地化默认名、自定义名及简要描述。
        val overrides = mConfigurations.overrides().associate { it.toolId to it.configJson }
        val definitionsById = mTools.builtinDefinitions().associateBy { it.toolId }
        val builtinItems = BUILTIN_DISPLAY_ORDER.map { (id, group) ->
            val overrideData = overrides[id]?.let {
                runCatching { BuiltinToolOverrideCodec.decode(it) }.getOrNull()
            }
            val effectiveDescription = overrideData?.description?.takeIf { it.isNotBlank() }
                ?: definitionsById[id]?.description.orEmpty()
            BuiltinToolListItem(
                id = id,
                defaultLabelRes = AgentUiCoordinator.TOOL_LABELS[id] ?: R.string.tool_settings_builtins,
                customDisplayName = overrideData?.displayName?.takeIf { it.isNotBlank() },
                group = group,
                descriptionPreview = effectiveDescription,
                customized = id in overrides,
            )
        }
        val state = getOrNull<ToolSettingsUiState.Normal>() ?: return
        state.copy(
            content = ToolSettingsContentState(
                tab = state.content.tab,
                tools = toolItems,
                connections = connectionItems,
                builtins = builtinItems,
            ),
        ).setup()
    }

    /** 将连接实体解析为结构化表单草稿。 */
    private suspend fun buildConnectionEditor(
        value: ToolConnection,
        mode: ToolSettingsEditorMode,
    ): ToolSettingsDialogState.ConnectionEditor {
        val config = HttpToolConfigCodec.connection(value.configJson)
        val hasCredential = config.credentialRef?.let { mCredentials.read(it) != null } == true
        return ToolSettingsDialogState.ConnectionEditor(
            id = value.id,
            mode = mode,
            name = value.name,
            url = config.url,
            method = config.method,
            authMode = config.authMode,
            authHeader = config.authHeader,
            credentialRef = config.credentialRef,
            hasCredential = hasCredential,
            timeoutSeconds = (config.timeoutMillis / 1000L).toString(),
            maxResponseKiB = (config.maxResponseBytes / 1024).coerceAtLeast(1).toString(),
            allowPrivateAddresses = config.allowPrivateAddresses,
            queryParams = config.query.map { (k, v) -> KeyValueDraft(k, v) },
            headers = config.headers.map { (k, v) -> KeyValueDraft(k, v) },
            enabled = value.enabled,
        )
    }

    /** 将工具实体解析为结构化表单草稿。 */
    private fun buildToolEditor(
        value: ToolDefinition,
        mode: ToolSettingsEditorMode,
    ): ToolSettingsDialogState.ToolEditor {
        val prettyParams = runCatching {
            ToolConfigCodec.pretty(ToolConfigCodec.parse(value.parametersJson))
        }.getOrDefault(value.parametersJson)
        val mapping = runCatching { HttpToolConfigCodec.mapping(value.mappingJson) }
            .getOrDefault(HttpToolMapping())
        val prettyBody = runCatching { ToolConfigCodec.pretty(mapping.body) }.getOrDefault("{}")
        return ToolSettingsDialogState.ToolEditor(
            id = value.id,
            mode = mode,
            kind = value.kind,
            name = value.name,
            description = value.description,
            connectionId = value.connectionId,
            parametersJson = prettyParams,
            parameterSummary = summarizeSchema(prettyParams),
            responsePath = mapping.responsePath,
            errorPath = mapping.errorPath.orEmpty(),
            nextCursorPath = mapping.nextCursorPath.orEmpty(),
            bodyJson = prettyBody,
            bindings = mapping.bindings.map { HttpBindingDraft(it.argument, it.target, it.path) },
            fields = mapping.fields.map { (k, v) -> KeyValueDraft(k, v) },
            enabled = value.enabled,
        )
    }

    /** 清理本机凭据目录中已不被任何连接引用的密文文件。 */
    private suspend fun pruneUnusedCredentials() {
        val activeRefs = mConfigurations.connections().mapNotNull { connection ->
            runCatching { HttpToolConfigCodec.connection(connection.configJson).credentialRef }
                .getOrNull()
        }.toSet()
        mCredentials.prune(activeRefs)
    }

    /** 回填工具测试结果及脱敏 JSON 预览。 */
    private fun applyTestResult(toolId: String, result: ToolResult) {
        val state = getOrNull<ToolSettingsUiState.Normal>() ?: return
        val test = state.dialog as? ToolSettingsDialogState.Test ?: return
        if (test.toolId != toolId) return
        when (result) {
            is ToolResult.Success -> {
                val preview = ToolConfigCodec.pretty(result.data).take(MAX_TEST_PREVIEW_CHARS)
                state.copy(
                    dialog = test.copy(
                        running = false,
                        passed = true,
                        statusRes = R.string.tool_settings_test_passed,
                        previewJson = preview,
                        truncated = result.truncated,
                    ),
                ).setup()
            }

            is ToolResult.RecoverableError -> state.copy(
                dialog = test.copy(
                    running = false,
                    passed = false,
                    statusRes = testErrorResource(result.code),
                    previewJson = "",
                    truncated = false,
                ),
            ).setup()

            else -> state.copy(
                dialog = test.copy(
                    running = false,
                    passed = false,
                    statusRes = R.string.tool_settings_test_failed,
                    previewJson = "",
                    truncated = false,
                ),
            ).setup()
        }
    }

    /** 回填测试失败状态。 */
    private fun applyTestFailure(toolId: String, @StringRes errorRes: Int) {
        val state = getOrNull<ToolSettingsUiState.Normal>() ?: return
        val test = state.dialog as? ToolSettingsDialogState.Test ?: return
        if (test.toolId != toolId) return
        state.copy(
            dialog = test.copy(
                running = false,
                passed = false,
                statusRes = errorRes,
                previewJson = "",
                truncated = false,
            ),
        ).setup()
    }

    private fun cancelRunningTest() {
        mTestJob?.cancel()
        mTestJob = null
    }

    private fun updateConnectionDraft(
        transform: (ToolSettingsDialogState.ConnectionEditor) -> ToolSettingsDialogState.ConnectionEditor,
    ) {
        val state = ready() ?: return
        val dialog = state.dialog as? ToolSettingsDialogState.ConnectionEditor ?: return
        state.copy(dialog = transform(dialog)).setup()
    }

    private fun updateToolDraft(
        transform: (ToolSettingsDialogState.ToolEditor) -> ToolSettingsDialogState.ToolEditor,
    ) {
        val state = ready() ?: return
        val dialog = state.dialog as? ToolSettingsDialogState.ToolEditor ?: return
        state.copy(dialog = transform(dialog)).setup()
    }

    private fun updateBuiltinDraft(
        transform: (ToolSettingsDialogState.BuiltinEditor) -> ToolSettingsDialogState.BuiltinEditor,
    ) {
        val state = ready() ?: return
        val dialog = state.dialog as? ToolSettingsDialogState.BuiltinEditor ?: return
        state.copy(dialog = transform(dialog)).setup()
    }

    private fun showDialogError(@StringRes errorRes: Int) {
        val state = getOrNull<ToolSettingsUiState.Normal>() ?: return
        val nextDialog = when (val dialog = state.dialog) {
            is ToolSettingsDialogState.ConnectionEditor -> dialog.copy(errorRes = errorRes)
            is ToolSettingsDialogState.ToolEditor -> dialog.copy(errorRes = errorRes)
            is ToolSettingsDialogState.BuiltinEditor -> dialog.copy(errorRes = errorRes)
            is ToolSettingsDialogState.Credential -> dialog.copy(errorRes = errorRes)
            else -> {
                AppViewEvent.PopupToastMessageByResId(errorRes).tryEmit()
                dialog
            }
        }
        state.copy(dialog = nextDialog).setup()
    }

    private fun closeDialog() {
        getOrNull<ToolSettingsUiState.Normal>()?.copy(dialog = ToolSettingsDialogState.None)?.setup()
    }

    private fun ready(): ToolSettingsUiState.Normal? =
        getOrNull<ToolSettingsUiState.Normal>()?.takeUnless { it.busy }

    /** 异步执行短时配置 IO，取消信号继续上抛，业务异常映射为具体本地化提示。 */
    private fun perform(
        onError: (Int) -> Unit = { resId ->
            AppViewEvent.PopupToastMessageByResId(resId).tryEmit()
        },
        block: suspend () -> Unit,
    ) {
        val state = ready() ?: return
        state.copy(busy = true).setup()
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onError(validationErrorResource(error))
            } finally {
                getOrNull<ToolSettingsUiState.Normal>()?.copy(busy = false)?.setup()
            }
        }
    }

    /** 根据参数 Schema 生成紧凑参数摘要列表供编辑器与测试框展示。 */
    private fun summarizeSchema(rawSchema: String): List<String> {
        val schema = runCatching { ToolSchemaCodec.decode(rawSchema) }.getOrNull() ?: return emptyList()
        return schema.properties.entries.take(12).map { (name, child) ->
            val mark = if (name in schema.required) "*" else ""
            "$name$mark (${child.type.name.lowercase()})"
        }
    }

    /** 根据工具参数 Schema 预生成可直接填写的测试参数 JSON 模板。 */
    private fun defaultTestArguments(rawSchema: String): String {
        val schema = runCatching { ToolSchemaCodec.decode(rawSchema) }.getOrNull() ?: return "{}"
        val selectedKeys = if (schema.required.isNotEmpty()) {
            schema.properties.keys.filter { it in schema.required }
        } else {
            schema.properties.keys.take(3)
        }
        val sample = JsonObject()
        selectedKeys.forEach { key ->
            val prop = schema.properties[key] ?: return@forEach
            when (prop.type) {
                ToolSchema.Type.String -> sample.addProperty(
                    key,
                    if (key == "url") "https://example.com" else "",
                )

                ToolSchema.Type.Integer -> sample.addProperty(key, prop.minimum?.toLong() ?: 0L)
                ToolSchema.Type.Number -> sample.addProperty(key, prop.minimum ?: 0.0)
                ToolSchema.Type.Boolean -> sample.addProperty(key, false)
                ToolSchema.Type.Array -> sample.add(key, JsonArray())
                ToolSchema.Type.Object -> sample.add(key, JsonObject())
                ToolSchema.Type.Null -> sample.add(key, null)
            }
        }
        return ToolConfigCodec.pretty(sample)
    }

    private fun suggestUniqueToolName(base: String, existing: Set<String>): String {
        if (base !in existing) return base
        var suffix = 2
        while (true) {
            val candidate = "${base.take(56)}_$suffix"
            if (candidate !in existing) return candidate
            suffix++
        }
    }

    private fun defaultAuthFieldName(authMode: String): String = when (authMode) {
        "Query" -> "api_key"
        "ApiKey", "Header" -> "X-API-Key"
        else -> "Authorization"
    }

    @StringRes
    private fun validationErrorResource(error: Exception): Int {
        val code = (error as? ToolConfigValidationException)?.code
        return when (code) {
            "invalid_json" -> R.string.tool_settings_error_invalid_json
            "invalid_name" -> R.string.tool_settings_error_invalid_name
            "duplicate_name" -> R.string.tool_settings_error_duplicate_name
            "invalid_url" -> R.string.tool_settings_error_invalid_url
            "credential_in_plain_config" -> R.string.tool_settings_error_plain_credential
            "invalid_headers", "invalid_auth", "invalid_method", "invalid_limits" ->
                R.string.tool_settings_error_invalid_connection

            "invalid_schema" -> R.string.tool_settings_error_invalid_schema
            "invalid_mapping" -> R.string.tool_settings_error_invalid_mapping
            "connection_in_use_body_binding" -> R.string.tool_settings_error_connection_body_conflict
            "missing_connection" -> R.string.tool_settings_needs_configuration
            "invalid_override" -> R.string.tool_settings_error_invalid_override
            "invalid_package" -> R.string.tool_settings_error_invalid_package
            "invalid_test_arguments" -> R.string.tool_settings_error_invalid_test_args
            "invalid_credential" -> R.string.tool_settings_error_invalid_credential
            else -> R.string.tool_settings_invalid
        }
    }

    @StringRes
    private fun testErrorResource(code: String): Int = when (code) {
        "http_needs_configuration" -> R.string.tool_settings_needs_configuration
        "http_auth_required" -> R.string.tool_settings_test_auth_failed
        "http_rate_limited" -> R.string.tool_settings_test_rate_limited
        "http_timeout" -> R.string.tool_settings_test_timeout
        "http_remote_error" -> R.string.tool_settings_test_remote_error
        "http_address_blocked", "http_unsafe_redirect", "http_invalid_url", "http_redirect_limit" ->
            R.string.tool_settings_blocked

        "http_response_too_large", "http_item_too_large" -> R.string.tool_settings_too_large
        "http_mapping_missing", "http_invalid_mapping", "http_invalid_json", "http_unsupported_content" ->
            R.string.tool_settings_mapping_failed

        else -> R.string.tool_settings_test_failed
    }

    private companion object {
        const val MAX_TEST_PREVIEW_CHARS = 8000

        val BUILTIN_DISPLAY_ORDER = listOf(
            "search_lore" to AgentToolGroup.Lore,
            "read_lore_entry" to AgentToolGroup.Lore,
            "search_chat" to AgentToolGroup.Chat,
            "read_messages" to AgentToolGroup.Chat,
            "search_memory" to AgentToolGroup.Memory,
            "read_memory" to AgentToolGroup.Memory,
            "upsert_memory" to AgentToolGroup.Memory,
            "correct_memory" to AgentToolGroup.Memory,
            "invalidate_memory" to AgentToolGroup.Memory,
            "promote_memory_scope" to AgentToolGroup.Memory,
            "get_scene_state" to AgentToolGroup.Scene,
            "update_scene_state" to AgentToolGroup.Scene,
            "roll_dice" to AgentToolGroup.Utility,
            "calculate" to AgentToolGroup.Utility,
            "ask_user" to AgentToolGroup.Interaction,
        )
    }
}
