@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package me.kafuuneko.rpclient.feature.toolsettings.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolCodeEditorTarget
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsDialogState
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsUiIntent
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsUiState


/** 统一分发工具管理页的互斥业务弹窗，全部采用项目宽弹窗与紧凑头部规范。 */
@Composable
internal fun DialogSwitch(
    state: ToolSettingsUiState.Normal,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    // 按当前弹窗状态互斥分发至对应编辑、凭据、测试或删除确认弹窗。
    val dismiss = { ToolSettingsUiIntent.DismissDialog.emit() }
    when (val dialog = state.dialog) {
        ToolSettingsDialogState.None -> Unit
        is ToolSettingsDialogState.ConnectionEditor -> ConnectionEditorDialog(
            dialog = dialog,
            busy = state.busy,
            emit = emit,
        )

        is ToolSettingsDialogState.ToolEditor -> {
            ToolEditorDialog(
                dialog = dialog,
                connections = state.content.connections,
                busy = state.busy,
                emit = emit,
            )
            // 复杂 JSON Schema 与请求体由独立全高代码编辑器承载，彻底消除表单内双层滚动打架。
            dialog.activeCodeEditor?.let { target ->
                ToolJsonSubEditorDialog(
                    target = target,
                    initialValue = if (target == ToolCodeEditorTarget.Parameters) {
                        dialog.parametersJson
                    } else {
                        dialog.bodyJson
                    },
                    emit = emit,
                )
            }
        }

        is ToolSettingsDialogState.BuiltinEditor -> BuiltinEditorDialog(
            dialog = dialog,
            busy = state.busy,
            emit = emit,
        )

        is ToolSettingsDialogState.Credential -> CredentialDialog(
            dialog = dialog,
            busy = state.busy,
            emit = emit,
        )

        is ToolSettingsDialogState.Test -> TestToolDialog(
            dialog = dialog,
            emit = emit,
        )

        is ToolSettingsDialogState.Delete -> DeleteConfigDialog(
            dialog = dialog,
            busy = state.busy,
            onDismiss = dismiss,
            onConfirm = { ToolSettingsUiIntent.ConfirmDelete.emit() },
        )
    }
}
