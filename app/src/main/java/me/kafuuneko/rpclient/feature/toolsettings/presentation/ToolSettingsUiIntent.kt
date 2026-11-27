package me.kafuuneko.rpclient.feature.toolsettings.presentation

import android.net.Uri

/** 工具管理用户行为，按连接、工具、内置覆写、测试及导入导出清晰拆分。 */
sealed interface ToolSettingsUiIntent {
    data object Init : ToolSettingsUiIntent
    data object Back : ToolSettingsUiIntent
    data class SelectTab(val tab: ToolSettingsTab) : ToolSettingsUiIntent

    // 连接管理
    data object CreateConnection : ToolSettingsUiIntent
    data class EditConnection(val id: String) : ToolSettingsUiIntent
    data class CopyConnection(val id: String) : ToolSettingsUiIntent
    data class ToggleConnectionEnabled(val id: String, val enabled: Boolean) : ToolSettingsUiIntent
    data class ShowDeleteConnection(val id: String) : ToolSettingsUiIntent
    data class ShowCredentialDialog(val connectionId: String) : ToolSettingsUiIntent
    data class SaveCredential(val secret: String) : ToolSettingsUiIntent
    data object ClearCredential : ToolSettingsUiIntent

    // 连接表单字段
    data class ChangeConnectionName(val value: String) : ToolSettingsUiIntent
    data class ChangeConnectionUrl(val value: String) : ToolSettingsUiIntent
    data class SelectConnectionMethod(val method: String) : ToolSettingsUiIntent
    data class SelectConnectionAuthMode(val authMode: String) : ToolSettingsUiIntent
    data class ChangeConnectionAuthHeader(val value: String) : ToolSettingsUiIntent
    data class ChangeConnectionTimeout(val value: String) : ToolSettingsUiIntent
    data class ToggleConnectionAllowPrivate(val enabled: Boolean) : ToolSettingsUiIntent
    data object AddConnectionQuery : ToolSettingsUiIntent
    data class UpdateConnectionQuery(val index: Int, val key: String, val value: String) :
        ToolSettingsUiIntent

    data class RemoveConnectionQuery(val index: Int) : ToolSettingsUiIntent
    data object AddConnectionHeader : ToolSettingsUiIntent
    data class UpdateConnectionHeader(val index: Int, val key: String, val value: String) :
        ToolSettingsUiIntent

    data class RemoveConnectionHeader(val index: Int) : ToolSettingsUiIntent

    // HTTP 工具管理
    data object CreateTool : ToolSettingsUiIntent
    data class CreateTemplate(val readUrl: Boolean) : ToolSettingsUiIntent
    data class EditTool(val id: String) : ToolSettingsUiIntent
    data class CopyTool(val id: String) : ToolSettingsUiIntent
    data class ToggleToolEnabled(val id: String, val enabled: Boolean) : ToolSettingsUiIntent
    data class ShowDeleteTool(val id: String) : ToolSettingsUiIntent

    // HTTP 工具表单字段
    data class ChangeToolName(val value: String) : ToolSettingsUiIntent
    data class ChangeToolDescription(val value: String) : ToolSettingsUiIntent
    data class SelectToolConnection(val id: String?) : ToolSettingsUiIntent
    data class ChangeToolResponsePath(val value: String) : ToolSettingsUiIntent
    data class ChangeToolErrorPath(val value: String) : ToolSettingsUiIntent
    data class ChangeToolNextCursorPath(val value: String) : ToolSettingsUiIntent
    data object AddToolBinding : ToolSettingsUiIntent
    data class UpdateToolBinding(
        val index: Int,
        val argument: String,
        val target: String,
        val path: String,
    ) : ToolSettingsUiIntent

    data class RemoveToolBinding(val index: Int) : ToolSettingsUiIntent
    data object AddToolField : ToolSettingsUiIntent
    data class UpdateToolField(val index: Int, val key: String, val value: String) :
        ToolSettingsUiIntent

    data class RemoveToolField(val index: Int) : ToolSettingsUiIntent
    data class OpenCodeEditor(val target: ToolCodeEditorTarget) : ToolSettingsUiIntent
    data class ApplyCodeEditor(val target: ToolCodeEditorTarget, val value: String) :
        ToolSettingsUiIntent

    data object DismissCodeEditor : ToolSettingsUiIntent

    // 内置工具覆写
    data class EditBuiltin(val toolId: String) : ToolSettingsUiIntent
    data class ResetBuiltinOverride(val toolId: String) : ToolSettingsUiIntent
    data class ChangeBuiltinDisplayName(val value: String) : ToolSettingsUiIntent
    data class ChangeBuiltinDescription(val value: String) : ToolSettingsUiIntent
    data class ChangeBuiltinUsageHint(val value: String) : ToolSettingsUiIntent
    data class ChangeBuiltinParamDescription(val name: String, val value: String) :
        ToolSettingsUiIntent

    data class ChangeBuiltinDefault(val key: String, val value: String) : ToolSettingsUiIntent

    // 通用弹窗确认与关闭
    data object SaveDialog : ToolSettingsUiIntent
    data object ConfirmDelete : ToolSettingsUiIntent
    data object DismissDialog : ToolSettingsUiIntent

    // 测试与便携包
    data class OpenTestDialog(val toolId: String) : ToolSettingsUiIntent
    data class ChangeTestArguments(val value: String) : ToolSettingsUiIntent
    data object RunTest : ToolSettingsUiIntent
    data object CancelTest : ToolSettingsUiIntent
    data object ImportClick : ToolSettingsUiIntent
    data object ExportClick : ToolSettingsUiIntent
    data class ImportResult(val uri: Uri?) : ToolSettingsUiIntent
    data class ExportResult(val uri: Uri?) : ToolSettingsUiIntent
}
