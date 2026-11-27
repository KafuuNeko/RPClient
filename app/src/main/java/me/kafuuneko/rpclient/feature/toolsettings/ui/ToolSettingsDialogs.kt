package me.kafuuneko.rpclient.feature.toolsettings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.toolsettings.presentation.HttpBindingDraft
import me.kafuuneko.rpclient.feature.toolsettings.presentation.KeyValueDraft
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolCodeEditorTarget
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolConnectionListItem
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsDialogState
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsEditorMode
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsTab
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsUiIntent
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsUiState
import me.kafuuneko.rpclient.ui.dialog.AppCodeEditorDialog
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.dialog.AppDialogScaffold
import me.kafuuneko.rpclient.ui.dialog.AppInputDialog
import me.kafuuneko.rpclient.ui.theme.AccentEmeraldColor
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.PageFilterChip as FilterChip
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.PageSwitch as Switch
import me.kafuuneko.rpclient.ui.widgets.RpMetaPill
import me.kafuuneko.rpclient.ui.widgets.RpScrollableOutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.RpTagPill
import me.kafuuneko.rpclient.ui.widgets.pageFilterChipColors

private val HTTP_METHODS = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD")
private val AUTH_MODES = listOf("None", "Bearer", "ApiKey", "Header", "Query")

/** 统一分发工具管理页的互斥业务弹窗，全部采用项目宽弹窗与紧凑头部规范。 */
@Composable
internal fun ToolSettingsDialogHost(
    state: ToolSettingsUiState.Normal,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionEditorDialog(
    dialog: ToolSettingsDialogState.ConnectionEditor,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    val titleRes = when (dialog.mode) {
        ToolSettingsEditorMode.Create -> R.string.tool_settings_create_connection_title
        ToolSettingsEditorMode.Edit -> R.string.tool_settings_edit_connection_title
        ToolSettingsEditorMode.Copy -> R.string.tool_settings_copy_connection_title
    }
    var showAdvanced by remember(dialog.id) {
        mutableStateOf(dialog.queryParams.isNotEmpty() || dialog.headers.isNotEmpty())
    }
    AppDialogScaffold(
        onDismissRequest = { ToolSettingsUiIntent.DismissDialog.emit() },
        title = stringResource(titleRes),
        badgeIcon = Icons.Rounded.Dns,
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
            OutlinedTextField(
                value = dialog.name,
                onValueChange = { ToolSettingsUiIntent.ChangeConnectionName(it).emit() },
                label = { Text(stringResource(R.string.tool_settings_connection_name_label)) },
                singleLine = true,
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = dialog.url,
                onValueChange = { ToolSettingsUiIntent.ChangeConnectionUrl(it).emit() },
                label = { Text(stringResource(R.string.tool_settings_connection_url_label)) },
                placeholder = { Text("https://api.example.com/search") },
                singleLine = true,
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            // HTTP 方法单选胶囊行。
            DialogFieldLabel(text = stringResource(R.string.tool_settings_http_method))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HTTP_METHODS.forEach { method ->
                    OptionChip(
                        label = method,
                        selected = dialog.method == method,
                        enabled = !busy,
                        onClick = { ToolSettingsUiIntent.SelectConnectionMethod(method).emit() },
                    )
                }
            }

            // 认证方式与认证头/查询参数名；密钥本身独立加密存储。
            DialogFieldLabel(text = stringResource(R.string.tool_settings_auth_mode))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AUTH_MODES.forEach { mode ->
                    OptionChip(
                        label = authModeLabel(mode),
                        selected = dialog.authMode == mode,
                        enabled = !busy,
                        onClick = { ToolSettingsUiIntent.SelectConnectionAuthMode(mode).emit() },
                    )
                }
            }
            if (dialog.authMode != "None") {
                OutlinedTextField(
                    value = dialog.authHeader,
                    onValueChange = { ToolSettingsUiIntent.ChangeConnectionAuthHeader(it).emit() },
                    label = {
                        Text(
                            stringResource(
                                if (dialog.authMode == "Query") {
                                    R.string.tool_settings_auth_query_label
                                } else {
                                    R.string.tool_settings_auth_header_label
                                },
                            ),
                        )
                    },
                    singleLine = true,
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(
                        if (dialog.hasCredential) {
                            R.string.tool_settings_credential_bound
                        } else {
                            R.string.tool_settings_auth_bind_after_save
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dialog.hasCredential) {
                        AccentEmeraldColor
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            // 超时与局域网安全访问开关。
            OutlinedTextField(
                value = dialog.timeoutSeconds,
                onValueChange = { ToolSettingsUiIntent.ChangeConnectionTimeout(it).emit() },
                label = { Text(stringResource(R.string.tool_settings_timeout_seconds)) },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            AppSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !busy) {
                            ToolSettingsUiIntent.ToggleConnectionAllowPrivate(!dialog.allowPrivateAddresses)
                                .emit()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.tool_settings_allow_private),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.tool_settings_allow_private_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = dialog.allowPrivateAddresses,
                        enabled = !busy,
                        onCheckedChange = { checked ->
                            ToolSettingsUiIntent.ToggleConnectionAllowPrivate(checked).emit()
                        },
                    )
                }
            }

            // 可折叠静态 Query 与静态请求头键值对表单。
            CollapsibleSectionHeader(
                title = stringResource(R.string.tool_settings_static_query_headers),
                expanded = showAdvanced,
                onToggle = { showAdvanced = !showAdvanced },
            )
            AnimatedVisibility(visible = showAdvanced) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    KeyValueListSection(
                        title = stringResource(R.string.tool_settings_static_query),
                        rows = dialog.queryParams,
                        keyLabel = stringResource(R.string.tool_settings_key_label),
                        valueLabel = stringResource(R.string.tool_settings_value_label),
                        enabled = !busy,
                        onAdd = { ToolSettingsUiIntent.AddConnectionQuery.emit() },
                        onUpdate = { idx, key, value ->
                            ToolSettingsUiIntent.UpdateConnectionQuery(idx, key, value).emit()
                        },
                        onRemove = { idx ->
                            ToolSettingsUiIntent.RemoveConnectionQuery(idx).emit()
                        },
                    )
                    KeyValueListSection(
                        title = stringResource(R.string.tool_settings_static_headers),
                        rows = dialog.headers,
                        keyLabel = stringResource(R.string.tool_settings_key_label),
                        valueLabel = stringResource(R.string.tool_settings_value_label),
                        enabled = !busy,
                        onAdd = { ToolSettingsUiIntent.AddConnectionHeader.emit() },
                        onUpdate = { idx, key, value ->
                            ToolSettingsUiIntent.UpdateConnectionHeader(idx, key, value).emit()
                        },
                        onRemove = { idx ->
                            ToolSettingsUiIntent.RemoveConnectionHeader(idx).emit()
                        },
                    )
                }
            }

            dialog.errorRes?.let { resId ->
                ErrorMessageText(resId = resId)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolEditorDialog(
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
    val isReader = dialog.kind == "ReadUrl"
    var showAdvancedMapping by remember(dialog.id) {
        mutableStateOf(
            dialog.errorPath.isNotBlank() ||
                dialog.nextCursorPath.isNotBlank() ||
                dialog.bodyJson.trim() != "{}",
        )
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

            dialog.errorRes?.let { resId ->
                ErrorMessageText(resId = resId)
            }
        }
    }
}

@Composable
private fun ToolJsonSubEditorDialog(
    target: ToolCodeEditorTarget,
    initialValue: String,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
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

@Composable
private fun BuiltinEditorDialog(
    dialog: ToolSettingsDialogState.BuiltinEditor,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    AppDialogScaffold(
        onDismissRequest = { ToolSettingsUiIntent.DismissDialog.emit() },
        title = stringResource(dialog.defaultLabelRes),
        subtitle = dialog.toolId,
        subtitleMaxLines = 1,
        badgeIcon = Icons.Rounded.Tune,
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
            // 展示内置原始说明作为参考，帮助用户明确覆写目标而非盲填。
            AppSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.tool_settings_original_description),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = dialog.originalDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = dialog.displayName,
                onValueChange = { ToolSettingsUiIntent.ChangeBuiltinDisplayName(it).emit() },
                label = { Text(stringResource(R.string.tool_settings_display_name_label)) },
                placeholder = { Text(stringResource(dialog.defaultLabelRes)) },
                singleLine = true,
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = dialog.description,
                onValueChange = { ToolSettingsUiIntent.ChangeBuiltinDescription(it).emit() },
                label = { Text(stringResource(R.string.tool_settings_description_override_label)) },
                minLines = 2,
                maxLines = 4,
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = dialog.usageHint,
                onValueChange = { ToolSettingsUiIntent.ChangeBuiltinUsageHint(it).emit() },
                label = { Text(stringResource(R.string.tool_settings_usage_hint_label)) },
                minLines = 2,
                maxLines = 3,
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            // 仅列出该内置工具真实存在的参数及其原始说明。
            if (dialog.parameters.isNotEmpty()) {
                DialogFieldLabel(text = stringResource(R.string.tool_settings_param_descriptions_title))
                dialog.parameters.forEach { param ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = param.name,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                fontWeight = FontWeight.SemiBold,
                            )
                            RpMetaPill(text = param.typeLabel)
                            if (param.required) {
                                RpTagPill(text = stringResource(R.string.tool_settings_required_badge))
                            }
                        }
                        OutlinedTextField(
                            value = param.overrideDescription,
                            onValueChange = { value ->
                                ToolSettingsUiIntent.ChangeBuiltinParamDescription(param.name, value)
                                    .emit()
                            },
                            placeholder = param.defaultDescription.takeIf { it.isNotBlank() }?.let {
                                {
                                    Text(
                                        text = it,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            },
                            maxLines = 3,
                            enabled = !busy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // 仅对支持分页默认值的工具显示 limit / offset 配置项。
            if (dialog.editableDefaults.isNotEmpty()) {
                DialogFieldLabel(text = stringResource(R.string.tool_settings_defaults_title))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if ("limit" in dialog.editableDefaults) {
                        OutlinedTextField(
                            value = dialog.defaultLimit,
                            onValueChange = {
                                ToolSettingsUiIntent.ChangeBuiltinDefault("limit", it).emit()
                            },
                            label = { Text(stringResource(R.string.tool_settings_default_limit)) },
                            placeholder = { Text("1 - 20") },
                            singleLine = true,
                            enabled = !busy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if ("offset" in dialog.editableDefaults) {
                        OutlinedTextField(
                            value = dialog.defaultOffset,
                            onValueChange = {
                                ToolSettingsUiIntent.ChangeBuiltinDefault("offset", it).emit()
                            },
                            label = { Text(stringResource(R.string.tool_settings_default_offset)) },
                            placeholder = { Text("0") },
                            singleLine = true,
                            enabled = !busy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (dialog.customized) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = { ToolSettingsUiIntent.ResetBuiltinOverride(dialog.toolId).emit() },
                        enabled = !busy,
                    ) {
                        Text(text = stringResource(R.string.agent_reset_defaults))
                    }
                }
            }

            dialog.errorRes?.let { resId ->
                ErrorMessageText(resId = resId)
            }
        }
    }
}

@Composable
private fun CredentialDialog(
    dialog: ToolSettingsDialogState.Credential,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    // 密码草稿仅存活于当前 Compose 实例，不进入 UiState 或 rememberSaveable。
    var secret by remember(dialog.connectionId) { mutableStateOf("") }
    val statusText = if (dialog.hasExisting) {
        stringResource(R.string.tool_settings_credential_bound)
    } else {
        stringResource(R.string.tool_settings_credential_missing)
    }
    val subtitle = "${dialog.connectionName} · ${authModeLabel(dialog.authMode)} (${dialog.authHeader})"
    AppInputDialog(
        onDismissRequest = { ToolSettingsUiIntent.DismissDialog.emit() },
        title = stringResource(R.string.tool_settings_credential),
        subtitle = subtitle,
        value = secret,
        password = true,
        onValueChange = { secret = it },
        enabled = !busy,
        badgeIcon = Icons.Rounded.Key,
        confirmText = stringResource(R.string.save),
        confirmEnabled = !busy && secret.isNotBlank(),
        isConfirmLoading = busy,
        onConfirm = {
            val value = secret
            secret = ""
            ToolSettingsUiIntent.SaveCredential(value).emit()
        },
        extraContent = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    text = statusText,
                    color = if (dialog.hasExisting) AccentEmeraldColor else MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    text = stringResource(R.string.tool_settings_credential_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (dialog.hasExisting) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = { ToolSettingsUiIntent.ClearCredential.emit() },
                            enabled = !busy,
                        ) {
                            Text(
                                text = stringResource(R.string.tool_settings_credential_clear),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                dialog.errorRes?.let { resId ->
                    ErrorMessageText(resId = resId)
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TestToolDialog(
    dialog: ToolSettingsDialogState.Test,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    val destinationText = when {
        dialog.destination.isNotBlank() -> dialog.destination
        dialog.kind == "ReadUrl" -> stringResource(R.string.tool_settings_read_url_destination)
        else -> stringResource(R.string.tool_settings_needs_configuration)
    }
    AppDialogScaffold(
        onDismissRequest = { ToolSettingsUiIntent.DismissDialog.emit() },
        title = "${stringResource(R.string.test)} · ${dialog.toolName}",
        subtitle = destinationText,
        subtitleMaxLines = 2,
        badgeIcon = Icons.Rounded.PlayArrow,
        compactHeader = true,
        scrollableContent = true,
        confirmText = stringResource(R.string.tool_settings_run_test),
        dismissText = stringResource(
            if (dialog.running) R.string.tool_settings_test_cancel else R.string.close,
        ),
        confirmEnabled = !dialog.running,
        isConfirmLoading = dialog.running,
        onConfirm = { ToolSettingsUiIntent.RunTest.emit() },
        onDismiss = {
            if (dialog.running) {
                ToolSettingsUiIntent.CancelTest.emit()
            } else {
                ToolSettingsUiIntent.DismissDialog.emit()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .widthIn(max = 520.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.tool_settings_test_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (dialog.parameterHints.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    dialog.parameterHints.forEach { hint ->
                        RpMetaPill(text = hint)
                    }
                }
            }
            RpScrollableOutlinedTextField(
                value = dialog.arguments,
                onValueChange = { value -> ToolSettingsUiIntent.ChangeTestArguments(value).emit() },
                label = { Text(stringResource(R.string.tool_settings_arguments)) },
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                shape = RoundedCornerShape(12.dp),
                enabled = !dialog.running,
                minLines = 3,
                maxLines = 7,
                modifier = Modifier.fillMaxWidth(),
            )

            // 测试完成后明确区分成功/失败着色，并展示脱敏后的映射结果 JSON 预览。
            dialog.statusRes?.let { statusRes ->
                val passed = dialog.passed == true
                val tint = if (passed) AccentEmeraldColor else MaterialTheme.colorScheme.error
                AppSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = tint.copy(alpha = 0.12f),
                    border = BorderStroke(width = 0.6.dp, color = tint.copy(alpha = 0.30f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(statusRes),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = tint,
                            modifier = Modifier.weight(1f),
                        )
                        if (passed && dialog.truncated) {
                            RpMetaPill(text = stringResource(R.string.tool_settings_test_truncated))
                        }
                    }
                }
            }
            if (dialog.previewJson.isNotBlank()) {
                Text(
                    text = stringResource(R.string.tool_settings_test_result_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                AppSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                ) {
                    SelectionContainer {
                        Text(
                            text = dialog.previewJson,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 18.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteConfigDialog(
    dialog: ToolSettingsDialogState.Delete,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val message = when {
        dialog.tab == ToolSettingsTab.Tools -> stringResource(
            R.string.tool_settings_delete_tool_message,
            dialog.name,
        )

        dialog.affectedToolCount > 0 -> stringResource(
            R.string.tool_settings_delete_connection_impact_message,
            dialog.name,
            dialog.affectedToolCount,
        )

        else -> stringResource(
            R.string.tool_settings_delete_connection_message,
            dialog.name,
        )
    }
    AppDangerDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.delete),
        message = message,
        confirmEnabled = !busy,
        isConfirmLoading = busy,
        onConfirm = onConfirm,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionDropdownField(
    connections: List<ToolConnectionListItem>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedConnection = connections.firstOrNull { it.id == selectedId }
    val displayText = selectedConnection?.let { "${it.name} (${it.method})" }
        ?: stringResource(R.string.none)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.tool_settings_bind_connection)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = enabled),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.none)) },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            connections.forEach { connection ->
                val statusSuffix = when {
                    !connection.enabled -> " · ${stringResource(R.string.disabled)}"
                    connection.needsConfiguration -> " · ${stringResource(R.string.tool_settings_needs_configuration)}"
                    else -> ""
                }
                DropdownMenuItem(
                    text = { Text("${connection.name} (${connection.method})$statusSuffix") },
                    onClick = {
                        expanded = false
                        onSelect(connection.id)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JsonPreviewActionCard(
    title: String,
    chips: List<String>,
    emptyLabel: String,
    enabled: Boolean,
    onEditClick: () -> Unit,
) {
    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            width = 0.6.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(
                    onClick = onEditClick,
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Code,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.tool_settings_edit_json),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            if (chips.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    chips.forEach { chip -> RpMetaPill(text = chip) }
                }
            } else {
                Text(
                    text = emptyLabel,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BindingsSection(
    bindings: List<HttpBindingDraft>,
    enabled: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DialogFieldLabel(text = stringResource(R.string.tool_settings_bindings_title))
            TextButton(
                onClick = { ToolSettingsUiIntent.AddToolBinding.emit() },
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.tool_settings_add_entry),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        bindings.forEachIndexed { index, binding ->
            AppSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OptionChip(
                            label = stringResource(R.string.tool_settings_target_query),
                            selected = binding.target == "query",
                            enabled = enabled,
                            onClick = {
                                ToolSettingsUiIntent.UpdateToolBinding(
                                    index = index,
                                    argument = binding.argument,
                                    target = "query",
                                    path = binding.path,
                                ).emit()
                            },
                        )
                        OptionChip(
                            label = stringResource(R.string.tool_settings_target_body),
                            selected = binding.target == "body",
                            enabled = enabled,
                            onClick = {
                                ToolSettingsUiIntent.UpdateToolBinding(
                                    index = index,
                                    argument = binding.argument,
                                    target = "body",
                                    path = binding.path,
                                ).emit()
                            },
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { ToolSettingsUiIntent.RemoveToolBinding(index).emit() },
                            enabled = enabled,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.80f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = binding.argument,
                            onValueChange = { value ->
                                ToolSettingsUiIntent.UpdateToolBinding(
                                    index = index,
                                    argument = value,
                                    target = binding.target,
                                    path = binding.path,
                                ).emit()
                            },
                            label = { Text(stringResource(R.string.tool_settings_binding_argument)) },
                            placeholder = { Text("/query") },
                            singleLine = true,
                            enabled = enabled,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = binding.path,
                            onValueChange = { value ->
                                ToolSettingsUiIntent.UpdateToolBinding(
                                    index = index,
                                    argument = binding.argument,
                                    target = binding.target,
                                    path = value,
                                ).emit()
                            },
                            label = {
                                Text(
                                    stringResource(
                                        if (binding.target == "body") {
                                            R.string.tool_settings_binding_path_body
                                        } else {
                                            R.string.tool_settings_binding_path_query
                                        },
                                    ),
                                )
                            },
                            placeholder = {
                                Text(if (binding.target == "body") "/q" else "q")
                            },
                            singleLine = true,
                            enabled = enabled,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyValueListSection(
    title: String,
    rows: List<KeyValueDraft>,
    keyLabel: String,
    valueLabel: String,
    enabled: Boolean,
    onAdd: () -> Unit,
    onUpdate: (Int, String, String) -> Unit,
    onRemove: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DialogFieldLabel(text = title)
            TextButton(
                onClick = onAdd,
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.tool_settings_add_entry),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = row.key,
                    onValueChange = { value -> onUpdate(index, value, row.value) },
                    label = { Text(keyLabel) },
                    singleLine = true,
                    enabled = enabled,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = row.value,
                    onValueChange = { value -> onUpdate(index, row.key, value) },
                    label = { Text(valueLabel) },
                    singleLine = true,
                    enabled = enabled,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onRemove(index) },
                    enabled = enabled,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.80f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsibleSectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun OptionChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
        },
        colors = pageFilterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
private fun DialogFieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun ErrorMessageText(resId: Int) {
    Text(
        text = stringResource(resId),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}
