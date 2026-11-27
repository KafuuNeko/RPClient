package me.kafuuneko.rpclient.feature.toolsettings.presentation

import androidx.annotation.StringRes
import me.kafuuneko.rpclient.feature.agent.model.AgentToolGroup

/** 工具管理页状态树，凭据正文与底层响应头不进入页面状态。 */
sealed interface ToolSettingsUiState {
    data object None : ToolSettingsUiState

    data class Normal(
        val content: ToolSettingsContentState = ToolSettingsContentState(),
        val dialog: ToolSettingsDialogState = ToolSettingsDialogState.None,
        val busy: Boolean = false,
        val choosingDocument: Boolean = false,
    ) : ToolSettingsUiState

    data class Finished(val previous: ToolSettingsUiState) : ToolSettingsUiState
}

/** 列表分区与结构化展示条目，不直接携带 Room 实体。 */
data class ToolSettingsContentState(
    val tab: ToolSettingsTab = ToolSettingsTab.Tools,
    val tools: List<HttpToolListItem> = emptyList(),
    val connections: List<ToolConnectionListItem> = emptyList(),
    val builtins: List<BuiltinToolListItem> = emptyList(),
)

/** 页面分区。 */
enum class ToolSettingsTab { Tools, Connections, Builtins }

/** 编辑器模式，用于区分新建、编辑与复制的标题及提示。 */
enum class ToolSettingsEditorMode { Create, Edit, Copy }

/** HTTP 工具列表摘要卡片模型。 */
data class HttpToolListItem(
    val id: String,
    val name: String,
    val description: String = "",
    val kind: String = "Http",
    val enabled: Boolean = true,
    val connectionName: String? = null,
    val connectionMethod: String? = null,
    val needsConfiguration: Boolean = false,
)

/** HTTP 连接列表摘要卡片模型，不暴露凭据正文。 */
data class ToolConnectionListItem(
    val id: String,
    val name: String,
    val url: String = "",
    val method: String = "GET",
    val authMode: String = "None",
    val authHeader: String = "Authorization",
    val hasCredential: Boolean = false,
    val allowPrivateAddresses: Boolean = false,
    val enabled: Boolean = true,
    val needsConfiguration: Boolean = false,
    val boundToolCount: Int = 0,
)

/** 内置本地工具列表条目模型。 */
data class BuiltinToolListItem(
    val id: String,
    @param:StringRes val defaultLabelRes: Int,
    val customDisplayName: String? = null,
    val group: AgentToolGroup = AgentToolGroup.Utility,
    val descriptionPreview: String = "",
    val customized: Boolean = false,
)

/** 通用键值表单草稿项，用于静态 Query、请求头及响应字段投影。 */
data class KeyValueDraft(
    val key: String = "",
    val value: String = "",
)

/** 请求参数绑定表单项。 */
data class HttpBindingDraft(
    val argument: String = "",
    val target: String = "query",
    val path: String = "",
)

/** 内置工具单个参数的说明覆写草稿。 */
data class BuiltinParamDraft(
    val name: String,
    val typeLabel: String,
    val required: Boolean,
    val defaultDescription: String,
    val overrideDescription: String,
)

/** 工具编辑器中需要全高语法高亮编辑的 JSON 子块目标。 */
enum class ToolCodeEditorTarget { Parameters, RequestBody }

/** 互斥对话框与结构化编辑草稿。 */
sealed interface ToolSettingsDialogState {
    data object None : ToolSettingsDialogState

    /** 结构化 HTTP 连接表单，凭据引用不暴露为手写 JSON 文本。 */
    data class ConnectionEditor(
        val id: String,
        val mode: ToolSettingsEditorMode,
        val name: String = "",
        val url: String = "",
        val method: String = "GET",
        val authMode: String = "None",
        val authHeader: String = "Authorization",
        val credentialRef: String? = null,
        val hasCredential: Boolean = false,
        val timeoutSeconds: String = "30",
        val maxResponseKiB: String = "2048",
        val allowPrivateAddresses: Boolean = false,
        val queryParams: List<KeyValueDraft> = emptyList(),
        val headers: List<KeyValueDraft> = emptyList(),
        val enabled: Boolean = true,
        @param:StringRes val errorRes: Int? = null,
    ) : ToolSettingsDialogState

    /** 结构化 HTTP 工具表单，复杂 JSON Schema 与静态请求体经独立代码编辑器修改。 */
    data class ToolEditor(
        val id: String,
        val mode: ToolSettingsEditorMode,
        val kind: String = "Http",
        val name: String = "",
        val description: String = "",
        val connectionId: String? = null,
        val parametersJson: String = """{"type":"object","properties":{}}""",
        val parameterSummary: List<String> = emptyList(),
        val responsePath: String = "",
        val errorPath: String = "",
        val nextCursorPath: String = "",
        val bodyJson: String = "{}",
        val bindings: List<HttpBindingDraft> = emptyList(),
        val fields: List<KeyValueDraft> = emptyList(),
        val enabled: Boolean = true,
        val activeCodeEditor: ToolCodeEditorTarget? = null,
        @param:StringRes val errorRes: Int? = null,
    ) : ToolSettingsDialogState

    /** 内置工具结构化覆写表单，展示原始说明、真实参数清单与可选分页默认值。 */
    data class BuiltinEditor(
        val toolId: String,
        @param:StringRes val defaultLabelRes: Int,
        val originalDescription: String,
        val displayName: String = "",
        val description: String = "",
        val usageHint: String = "",
        val parameters: List<BuiltinParamDraft> = emptyList(),
        val editableDefaults: List<String> = emptyList(),
        val defaultLimit: String = "",
        val defaultOffset: String = "",
        val customized: Boolean = false,
        @param:StringRes val errorRes: Int? = null,
    ) : ToolSettingsDialogState

    /** 连接加密凭据管理弹窗；密码输入仅停留在控件瞬态，不进入状态树。 */
    data class Credential(
        val connectionId: String,
        val connectionName: String,
        val authMode: String,
        val authHeader: String,
        val hasExisting: Boolean,
        @param:StringRes val errorRes: Int? = null,
    ) : ToolSettingsDialogState

    /** 工具真机测试弹窗，支持参数提示、取消执行与脱敏结果预览。 */
    data class Test(
        val toolId: String,
        val toolName: String,
        val kind: String = "Http",
        val destination: String = "",
        val parameterHints: List<String> = emptyList(),
        val arguments: String = "{}",
        val running: Boolean = false,
        val passed: Boolean? = null,
        @param:StringRes val statusRes: Int? = null,
        val previewJson: String = "",
        val truncated: Boolean = false,
    ) : ToolSettingsDialogState

    /** 删除确认弹窗，明确告知删除对象及关联工具影响。 */
    data class Delete(
        val id: String,
        val name: String,
        val tab: ToolSettingsTab,
        val affectedToolCount: Int = 0,
    ) : ToolSettingsDialogState
}
