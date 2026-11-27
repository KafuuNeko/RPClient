@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package me.kafuuneko.rpclient.feature.toolsettings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsDialogState
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsUiIntent
import me.kafuuneko.rpclient.ui.dialog.AppDialogScaffold
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.RpMetaPill
import me.kafuuneko.rpclient.ui.widgets.RpTagPill


/** 渲染内置工具的结构化覆写弹窗，允许修改展示名、模型说明与规格声明的默认参数。 */
@Composable
internal fun BuiltinEditorDialog(
    dialog: ToolSettingsDialogState.BuiltinEditor,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    // 弹窗只展示当前草稿并派发编辑意图，保存前校验与默认回退由编辑器服务统一执行。
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
            BuiltinOriginalDescription(originalDescription = dialog.originalDescription)

            BuiltinDescriptionFields(dialog = dialog, busy = busy, emit = emit)

            BuiltinParameterDescriptions(dialog = dialog, busy = busy, emit = emit)

            BuiltinDefaultFields(dialog = dialog, busy = busy, emit = emit)

            // 仅存在已保存覆写时展示一键重置入口，避免误导未自定义条目。
            if (dialog.customized) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            ToolSettingsUiIntent.ResetBuiltinOverride(dialog.toolId).emit()
                        },
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

/** 内置默认说明独立展示，便于与覆盖后的预览比较。 */
@Composable
private fun BuiltinOriginalDescription(originalDescription: String) {
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
                text = originalDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 展示名、模型说明与附加提示分开编辑，预览来自统一解析器。 */
@Composable
private fun BuiltinDescriptionFields(
    dialog: ToolSettingsDialogState.BuiltinEditor,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit
) {
    // 展示名仅影响界面展示，说明覆写与附加提示会进入生效模型提示词预览。
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

    if (dialog.effectiveDescription.isNotEmpty()) {
        DialogFieldLabel(text = stringResource(R.string.appearance_preview))
        Text(text = dialog.effectiveDescription, style = MaterialTheme.typography.bodySmall)
    }
}

/** 只渲染规格允许的参数说明，表单不能修改参数类型或必填性。 */
@Composable
private fun BuiltinParameterDescriptions(
    dialog: ToolSettingsDialogState.BuiltinEditor,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit
) {
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
}

/** 默认参数行完全由规格驱动，原始默认值作为未覆盖提示。 */
@Composable
private fun BuiltinDefaultFields(
    dialog: ToolSettingsDialogState.BuiltinEditor,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit
) {
    // 参数行由规格派生，新增可编辑默认项不需要修改表单代码。
    if (dialog.editableDefaults.isNotEmpty()) {
        DialogFieldLabel(text = stringResource(R.string.tool_settings_defaults_title))
        dialog.editableDefaults.forEach { field ->
            OutlinedTextField(
                value = field.value,
                onValueChange = { ToolSettingsUiIntent.ChangeBuiltinDefault(field.key, it).emit() },
                label = { Text(stringResource(field.labelRes)) },
                placeholder = { Text(field.originalValue) },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = if (field.numeric) KeyboardType.Number else KeyboardType.Text),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
