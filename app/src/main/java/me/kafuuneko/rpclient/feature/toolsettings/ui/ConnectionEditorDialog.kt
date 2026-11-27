@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package me.kafuuneko.rpclient.feature.toolsettings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsDialogState
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsEditorMode
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsUiIntent
import me.kafuuneko.rpclient.libs.agent.connection.ToolAuthMode
import me.kafuuneko.rpclient.libs.agent.connection.ToolHttpMethod
import me.kafuuneko.rpclient.ui.dialog.AppDialogScaffold
import me.kafuuneko.rpclient.ui.theme.AccentEmeraldColor
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.PageSwitch as Switch


/** 渲染 HTTP 连接的新建、编辑与复制结构化表单，密钥正文始终与表单隔离。 */
@Composable
internal fun ConnectionEditorDialog(
    dialog: ToolSettingsDialogState.ConnectionEditor,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    // 根据编辑模式匹配标题，分段组合身份、认证、安全与高级键值表单。
    val titleRes = when (dialog.mode) {
        ToolSettingsEditorMode.Create -> R.string.tool_settings_create_connection_title
        ToolSettingsEditorMode.Edit -> R.string.tool_settings_edit_connection_title
        ToolSettingsEditorMode.Copy -> R.string.tool_settings_copy_connection_title
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
            ConnectionIdentityFields(dialog = dialog, busy = busy, emit = emit)

            ConnectionAuthenticationFields(dialog = dialog, busy = busy, emit = emit)

            ConnectionSafetyFields(dialog = dialog, busy = busy, emit = emit)

            ConnectionAdvancedFields(dialog = dialog, busy = busy, emit = emit)

            dialog.errorRes?.let { resId ->
                ErrorMessageText(resId = resId)
            }
        }
    }
}


/** 连接身份与方法按表单草稿渲染，变更只发送 Intent。 */
@Composable
private fun ConnectionIdentityFields(
    dialog: ToolSettingsDialogState.ConnectionEditor,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit
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
        ToolHttpMethod.all.forEach { method ->
            OptionChip(
                label = method,
                selected = dialog.method == method,
                enabled = !busy,
                onClick = { ToolSettingsUiIntent.SelectConnectionMethod(method).emit() },
            )
        }
    }
}

/** 认证表单不接触密钥正文，只展示绑定状态。 */
@Composable
private fun ConnectionAuthenticationFields(
    dialog: ToolSettingsDialogState.ConnectionEditor,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit
) {
    // 认证方式与认证头/查询参数名；密钥本身独立加密存储。
    DialogFieldLabel(text = stringResource(R.string.tool_settings_auth_mode))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ToolAuthMode.all.forEach { mode ->
            OptionChip(
                label = authModeLabel(mode),
                selected = dialog.authMode == mode,
                enabled = !busy,
                onClick = { ToolSettingsUiIntent.SelectConnectionAuthMode(mode).emit() },
            )
        }
    }
    if (dialog.authMode != ToolAuthMode.None) {
        OutlinedTextField(
            value = dialog.authHeader,
            onValueChange = { ToolSettingsUiIntent.ChangeConnectionAuthHeader(it).emit() },
            label = {
                Text(
                    stringResource(
                        if (dialog.authMode == ToolAuthMode.Query) {
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
}

/** 网络安全限制保留独立状态，局域网授权由显式开关决定。 */
@Composable
private fun ConnectionSafetyFields(
    dialog: ToolSettingsDialogState.ConnectionEditor,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit
) {
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
}

/** 高级字段折叠只影响显示，不清除静态 Query 与请求头草稿。 */
@Composable
private fun ConnectionAdvancedFields(
    dialog: ToolSettingsDialogState.ConnectionEditor,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit
) {
    var showAdvanced by remember(dialog.id) {
        mutableStateOf(dialog.queryParams.isNotEmpty() || dialog.headers.isNotEmpty())
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
}
