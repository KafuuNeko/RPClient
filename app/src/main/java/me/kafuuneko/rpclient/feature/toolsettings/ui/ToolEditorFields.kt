@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package me.kafuuneko.rpclient.feature.toolsettings.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.toolsettings.presentation.HttpBindingDraft
import me.kafuuneko.rpclient.feature.toolsettings.presentation.KeyValueDraft
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolConnectionListItem
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsUiIntent
import me.kafuuneko.rpclient.libs.agent.connection.ToolBindingTarget
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.PageFilterChip as FilterChip
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.RpMetaPill
import me.kafuuneko.rpclient.ui.widgets.pageFilterChipColors


/** 渲染连接绑定下拉选择器，同时标注已停用或待配置连接的辅助状态。 */
@Composable
internal fun ConnectionDropdownField(
    connections: List<ToolConnectionListItem>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (String?) -> Unit,
) {
    // 仅维护下拉菜单展开瞬态，选中结果通过回调交给表单状态。
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


/** 展示 JSON 配置项的紧凑摘要与独立代码编辑器跳转入口。 */
@Composable
internal fun JsonPreviewActionCard(
    title: String,
    chips: List<String>,
    emptyLabel: String,
    enabled: Boolean,
    onEditClick: () -> Unit,
) {
    // 有摘要标签时以流式胶囊展示，无标签时退回等宽单行预览文本。
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


/** 渲染工具参数到 Query 或 JSON 请求体的动态绑定列表区域。 */
@Composable
internal fun BindingsSection(
    bindings: List<HttpBindingDraft>,
    enabled: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    // 顶部提供新增绑定入口，逐条拆分渲染目标切换与路径输入。
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
                    BindingTargetFields(
                        index = index,
                        binding = binding,
                        enabled = enabled,
                        emit = emit
                    )

                    BindingPathFields(
                        index = index,
                        binding = binding,
                        enabled = enabled,
                        emit = emit
                    )

                }
            }
        }
    }
}


/** 渲染通用的动态键值对编辑区，复用于静态 Query、请求头与响应字段投影。 */
@Composable
internal fun KeyValueListSection(
    title: String,
    rows: List<KeyValueDraft>,
    keyLabel: String,
    valueLabel: String,
    enabled: Boolean,
    onAdd: () -> Unit,
    onUpdate: (Int, String, String) -> Unit,
    onRemove: (Int) -> Unit,
) {
    // 每行保持键、值并排与删除按钮，空白行的裁剪与校验由保存阶段统一处理。
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


/** 渲染可点击展开/收起的高级配置区标题栏。 */
@Composable
internal fun CollapsibleSectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    // 点击整行切换展开态，右侧图标同步指示折叠方向。
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


/** 渲染单行枚举选项胶囊，用于 HTTP 方法、认证方式及绑定目标选择。 */
@Composable
internal fun OptionChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // 禁用文字换行以保持流式胶囊行高整齐。
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


/** 弹窗表单分段小标题。 */
@Composable
internal fun DialogFieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** 弹窗内就地展示本地化校验错误文案。 */
@Composable
internal fun ErrorMessageText(resId: Int) {
    Text(
        text = stringResource(resId),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

/** 映射目标只能在 Query 与 Body 间切换，控件不解释目标地址。 */
@Composable
private fun BindingTargetFields(
    index: Int,
    binding: HttpBindingDraft,
    enabled: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit
) {
    // 左侧切换 Query/Body 目标，右侧放置当前绑定条目删除按钮。
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OptionChip(
            label = stringResource(R.string.tool_settings_target_query),
            selected = binding.target == ToolBindingTarget.Query,
            enabled = enabled,
            onClick = {
                ToolSettingsUiIntent.UpdateToolBinding(
                    index = index,
                    argument = binding.argument,
                    target = ToolBindingTarget.Query,
                    path = binding.path,
                ).emit()
            },
        )
        OptionChip(
            label = stringResource(R.string.tool_settings_target_body),
            selected = binding.target == ToolBindingTarget.Body,
            enabled = enabled,
            onClick = {
                ToolSettingsUiIntent.UpdateToolBinding(
                    index = index,
                    argument = binding.argument,
                    target = ToolBindingTarget.Body,
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
}

/** 参数与目标路径保持独立字段，输入由配置保存边界统一校验。 */
@Composable
private fun BindingPathFields(
    index: Int,
    binding: HttpBindingDraft,
    enabled: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit
) {
    // 按当前绑定目标动态切换路径标签与占位提示。
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
                        if (binding.target == ToolBindingTarget.Body) {
                            R.string.tool_settings_binding_path_body
                        } else {
                            R.string.tool_settings_binding_path_query
                        },
                    ),
                )
            },
            placeholder = {
                Text(if (binding.target == ToolBindingTarget.Body) "/q" else "q")
            },
            singleLine = true,
            enabled = enabled,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f),
        )
    }
}
