@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package me.kafuuneko.rpclient.feature.toolsettings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolCodeEditorTarget
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolConnectionListItem
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsDialogState
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsEditorMode
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsUiIntent
import me.kafuuneko.rpclient.libs.agent.connection.HttpToolKind
import me.kafuuneko.rpclient.ui.dialog.AppCodeEditorDialog
import me.kafuuneko.rpclient.ui.dialog.AppDialogScaffold
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField


/** 渲染 HTTP 或公网读取工具的结构化编辑弹窗，复杂 JSON 由独立子编辑器承接。 */
@Composable
internal fun ToolEditorDialog(
    dialog: ToolSettingsDialogState.ToolEditor,
    connections: List<ToolConnectionListItem>,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    val titleRes = when (dialog.mode) {
        ToolSettingsEditorMode.Create -> R.string.tool_settings_create_tool_title
        ToolSettingsEditorMode.Edit -> R.string.tool_settings_edit_tool_title
        ToolSettingsEditorMode.Copy -> R.string.tool_settings_copy_tool_title
    }
    AppDialogScaffold(
        onDismissRequest = { ToolSettingsUiIntent.DismissDialog.emit() },
        title = stringResource(titleRes),
        badgeIcon = Icons.Rounded.Build,
        compactHeader = true,
        scrollableContent = true,
        confirmText = stringResource(R.string.save),
        dismissText = stringResource(R.string.cancel),
        confirmEnabled = !busy,
        isConfirmLoading = busy,
        onConfirm = { ToolSettingsUiIntent.SaveDialog.emit() },
        onDismiss = { ToolSettingsUiIntent.DismissDialog.emit() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .widthIn(max = 520.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HttpToolIdentityFields(
                dialog = dialog,
                busy = busy,
                emit = emit,
                connections = connections
            )

            // 参数 Schema 摘要卡片与独立全高 JSON 编辑器入口。
            JsonPreviewActionCard(
                title = stringResource(R.string.tool_settings_schema),
                chips = dialog.parameterSummary,
                emptyLabel = stringResource(R.string.tool_settings_schema_empty),
                enabled = !busy,
                onEditClick = {
                    ToolSettingsUiIntent.OpenCodeEditor(ToolCodeEditorTarget.Parameters).emit()
                },
            )

            HttpToolMappingFields(dialog = dialog, busy = busy, emit = emit)

            dialog.errorRes?.let { resId ->
                ErrorMessageText(resId = resId)
            }
        }
    }
}


/** 独立全高 JSON 代码编辑弹窗，用于编辑参数 Schema 或静态请求体模板。 */
@Composable
internal fun ToolJsonSubEditorDialog(
    target: ToolCodeEditorTarget,
    initialValue: String,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    // 随编辑目标重建文本缓冲，确认后通过意图回写父表单草稿。
    val state = remember(target, initialValue) { TextFieldState(initialValue) }
    val titleRes = if (target == ToolCodeEditorTarget.Parameters) {
        R.string.tool_settings_schema
    } else {
        R.string.tool_settings_request_body
    }
    AppCodeEditorDialog(
        onDismissRequest = { ToolSettingsUiIntent.DismissCodeEditor.emit() },
        title = stringResource(titleRes),
        state = state,
        editorHeight = 280.dp,
        onConfirm = {
            ToolSettingsUiIntent.ApplyCodeEditor(target, state.text.toString()).emit()
        },
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 540.dp),
    )
}

/** 高级映射展开状态只控制展示，JSON 草稿继续由 ViewModel 保存。 */
@Composable
private fun AdvancedHttpMappingFields(
    dialog: ToolSettingsDialogState.ToolEditor,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit
) {
    // 已存在错误路径、游标路径或非空静态请求体时默认展开，避免隐藏生效配置。
    var showAdvancedMapping by remember(dialog.id) {
        mutableStateOf(
            dialog.errorPath.isNotBlank() ||
                dialog.nextCursorPath.isNotBlank() ||
                dialog.bodyJson.trim() != "{}",
        )
    }

    CollapsibleSectionHeader(
        title = stringResource(R.string.tool_settings_advanced_mapping),
        expanded = showAdvancedMapping,
        onToggle = { showAdvancedMapping = !showAdvancedMapping },
    )
    AnimatedVisibility(visible = showAdvancedMapping) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = dialog.errorPath,
                onValueChange = { ToolSettingsUiIntent.ChangeToolErrorPath(it).emit() },
                label = { Text(stringResource(R.string.tool_settings_error_path)) },
                placeholder = { Text("/error") },
                singleLine = true,
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = dialog.nextCursorPath,
                onValueChange = {
                    ToolSettingsUiIntent.ChangeToolNextCursorPath(it).emit()
                },
                label = { Text(stringResource(R.string.tool_settings_next_cursor_path)) },
                placeholder = { Text("/next_cursor") },
                singleLine = true,
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            JsonPreviewActionCard(
                title = stringResource(R.string.tool_settings_request_body),
                chips = emptyList(),
                emptyLabel = dialog.bodyJson.lineSequence().firstOrNull().orEmpty()
                    .ifBlank { "{}" },
                enabled = !busy,
                onEditClick = {
                    ToolSettingsUiIntent.OpenCodeEditor(ToolCodeEditorTarget.RequestBody)
                        .emit()
                },
            )
        }
    }
}

/** 函数身份和连接绑定独立展示，公网读取工具不提供连接选择。 */
@Composable
private fun HttpToolIdentityFields(
    dialog: ToolSettingsDialogState.ToolEditor,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit,
    connections: List<ToolConnectionListItem>
) {
    val isReader = dialog.kind == HttpToolKind.ReadUrl
    OutlinedTextField(
        value = dialog.name,
        onValueChange = { ToolSettingsUiIntent.ChangeToolName(it).emit() },
        label = { Text(stringResource(R.string.tool_settings_function_name_label)) },
        supportingText = { Text(stringResource(R.string.tool_settings_function_name_hint)) },
        singleLine = true,
        enabled = !busy,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = dialog.description,
        onValueChange = { ToolSettingsUiIntent.ChangeToolDescription(it).emit() },
        label = { Text(stringResource(R.string.tool_settings_description)) },
        minLines = 2,
        maxLines = 4,
        enabled = !busy,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )

    // 非网页读取工具通过标准下拉框选择连接，缺少连接时给出明确引导。
    if (!isReader) {
        ConnectionDropdownField(
            connections = connections,
            selectedId = dialog.connectionId,
            enabled = !busy,
            onSelect = { id -> ToolSettingsUiIntent.SelectToolConnection(id).emit() },
        )
        if (connections.isEmpty()) {
            Text(
                text = stringResource(R.string.tool_settings_no_connection_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

/** 请求绑定与响应映射共享同一草稿，控件不执行网络或配置校验。 */
@Composable
private fun HttpToolMappingFields(
    dialog: ToolSettingsDialogState.ToolEditor,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit
) {
    val isReader = dialog.kind == HttpToolKind.ReadUrl
    // 结构化参数绑定、响应路径与字段投影表单。
    if (!isReader) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
        )
        BindingsSection(
            bindings = dialog.bindings,
            enabled = !busy,
            emit = emit,
        )
        OutlinedTextField(
            value = dialog.responsePath,
            onValueChange = { ToolSettingsUiIntent.ChangeToolResponsePath(it).emit() },
            label = { Text(stringResource(R.string.tool_settings_response_path)) },
            placeholder = { Text("/results") },
            singleLine = true,
            enabled = !busy,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        KeyValueListSection(
            title = stringResource(R.string.tool_settings_fields_title),
            rows = dialog.fields,
            keyLabel = stringResource(R.string.tool_settings_field_name),
            valueLabel = stringResource(R.string.tool_settings_field_pointer),
            enabled = !busy,
            onAdd = { ToolSettingsUiIntent.AddToolField.emit() },
            onUpdate = { idx, key, value ->
                ToolSettingsUiIntent.UpdateToolField(idx, key, value).emit()
            },
            onRemove = { idx -> ToolSettingsUiIntent.RemoveToolField(idx).emit() },
        )
        AdvancedHttpMappingFields(dialog = dialog, busy = busy, emit = emit)

    }
}
