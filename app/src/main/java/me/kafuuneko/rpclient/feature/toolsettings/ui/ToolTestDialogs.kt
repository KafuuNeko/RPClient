@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package me.kafuuneko.rpclient.feature.toolsettings.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsDialogState
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsTab
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsUiIntent
import me.kafuuneko.rpclient.libs.agent.connection.HttpToolKind
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.dialog.AppDialogScaffold
import me.kafuuneko.rpclient.ui.dialog.AppInputDialog
import me.kafuuneko.rpclient.ui.theme.AccentEmeraldColor
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.RpMetaPill
import me.kafuuneko.rpclient.ui.widgets.RpScrollableOutlinedTextField


/** 渲染连接加密凭据绑定弹窗，密码输入仅停留在本地控件瞬态并随提交立即清空。 */
@Composable
internal fun CredentialDialog(
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
    val subtitle =
        "${dialog.connectionName} · ${authModeLabel(dialog.authMode)} (${dialog.authHeader})"
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


/** 渲染工具真机测试弹窗，要求用户显式点击发送并支持随时取消进行中的请求。 */
@Composable
internal fun TestToolDialog(
    dialog: ToolSettingsDialogState.Test,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    // 展示目标摘要、参数提示与 JSON 输入区，运行中将次按钮切换为取消测试。
    val destinationText = when {
        dialog.destination.isNotBlank() -> dialog.destination
        dialog.kind == HttpToolKind.ReadUrl -> stringResource(R.string.tool_settings_read_url_destination)
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

            ToolTestResult(dialog = dialog)

        }
    }
}


/** 渲染工具或连接删除危险确认弹窗，删除连接时同步提示受影响的绑定工具数。 */
@Composable
internal fun DeleteConfigDialog(
    dialog: ToolSettingsDialogState.Delete,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    // 按删除目标和受影响工具数量拼装具体提示文案，复用公共危险确认弹窗。
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


/** 只展示有界脱敏测试结果，确认或错误不引用底层异常原文。 */
@Composable
private fun ToolTestResult(dialog: ToolSettingsDialogState.Test) {
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
