package me.kafuuneko.rpclient.feature.prompttemplatelist.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.prompttemplatelist.presentation.PromptTemplateContentState
import me.kafuuneko.rpclient.feature.prompttemplatelist.presentation.PromptTemplateDialogState
import me.kafuuneko.rpclient.feature.prompttemplatelist.presentation.PromptTemplateItem
import me.kafuuneko.rpclient.feature.prompttemplatelist.presentation.PromptTemplateListUiIntent
import me.kafuuneko.rpclient.feature.prompttemplatelist.presentation.PromptTemplateListUiState
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.dialog.AppInputDialog
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppCard
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.pageCanvasColor
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField

/** 使用项目统一卡片、动态颜色和弹窗的模板管理页。 */
@Composable
fun PromptTemplateListLayout(
    uiState: PromptTemplateListUiState,
    emit: PromptTemplateListUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is PromptTemplateListUiState.Normal) { PromptTemplateListUiIntent.Back.emit() }
    when (uiState) {
        PromptTemplateListUiState.None -> Unit
        is PromptTemplateListUiState.Finished -> PromptTemplateListLayout(uiState.previous) {}
        is PromptTemplateListUiState.Normal -> {
            NormalView(uiState, emit)
            DialogSwitch(uiState, emit)
        }
    }
}

@Composable
private fun NormalView(
    state: PromptTemplateListUiState.Normal,
    emit: PromptTemplateListUiIntent.() -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(pageCanvasColor())) {
        AppTopBar(
            title = stringResource(R.string.prompt_preset_title),
            onBack = { PromptTemplateListUiIntent.Back.emit() },
            actions = {
                IconButton(
                    onClick = { PromptTemplateListUiIntent.Create.emit() },
                    enabled = !state.busy && state.contentState is PromptTemplateContentState.Ready
                ) {
                    Icon(Icons.Rounded.Add, stringResource(R.string.prompt_template_create))
                }
            },
        )
        // 与模型配置列表保持相同边距、信息密度和背景继承。
        RpLazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (val content = state.contentState) {
                PromptTemplateContentState.Loading -> item {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(
                            20.dp
                        )
                    )
                }

                PromptTemplateContentState.Failed -> item {
                    Text(stringResource(R.string.prompt_template_failed))
                    TextButton(onClick = { PromptTemplateListUiIntent.Retry.emit() }) {
                        Text(
                            stringResource(R.string.retry)
                        )
                    }
                }

                is PromptTemplateContentState.Ready -> {
                    item { ActiveTemplateCard(content.activeName) }
                    item {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = content.query,
                            onValueChange = { PromptTemplateListUiIntent.Search(it).emit() },
                            placeholder = { Text(stringResource(R.string.prompt_template_search)) },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                    item { RpSectionHeader(title = stringResource(R.string.prompt_template_all)) }
                    if (content.items.isEmpty()) item {
                        Text(
                            stringResource(R.string.prompt_template_no_results),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    items(items = content.items, key = { it.id }) { item ->
                        TemplateCard(
                            item,
                            state.busy,
                            emit
                        )
                    }
                    if (content.query.isBlank() && content.items.none { !it.isBuiltin }) item {
                        Text(
                            stringResource(R.string.prompt_template_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveTemplateCard(name: String) {
    // 当前配置单独展示，搜索列表时仍能确认下一次生成使用哪套模板。
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.prompt_template_active),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                stringResource(R.string.prompt_template_scope),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun TemplateCard(
    item: PromptTemplateItem,
    busy: Boolean,
    emit: PromptTemplateListUiIntent.() -> Unit
) {
    AppCard(
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = !busy) { PromptTemplateListUiIntent.Open(item.id).emit() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        // 正文留在详情页；列表展示名称、来源与当前状态，避免长提示词撑高卡片。
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    item.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.isActive) Icon(
                    Icons.Rounded.CheckCircle,
                    stringResource(R.string.prompt_template_active),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            if (item.description.isNotBlank()) Text(
                item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (!item.isBuiltin) Text(
                item.updatedTime,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { PromptTemplateListUiIntent.Activate(item.id).emit() },
                    enabled = !busy && !item.isActive
                ) {
                    Text(stringResource(if (item.isActive) R.string.prompt_template_active else R.string.prompt_template_activate))
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { PromptTemplateListUiIntent.Copy(item.id).emit() },
                    enabled = !busy
                ) { Icon(Icons.Rounded.ContentCopy, stringResource(R.string.prompt_template_copy)) }
                if (!item.isBuiltin) {
                    IconButton(
                        onClick = { PromptTemplateListUiIntent.Rename(item.id).emit() },
                        enabled = !busy
                    ) {
                        Icon(
                            Icons.Rounded.Edit,
                            stringResource(R.string.prompt_template_edit_details)
                        )
                    }
                    IconButton(
                        onClick = { PromptTemplateListUiIntent.Delete(item.id).emit() },
                        enabled = !busy
                    ) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.delete)) }
                }
            }
        }
    }
}

@Composable
private fun DialogSwitch(
    state: PromptTemplateListUiState.Normal,
    emit: PromptTemplateListUiIntent.() -> Unit
) {
    // 删除确认区分当前模板，提前说明原子切回默认配置的结果。
    when (val dialog = state.dialogState) {
        PromptTemplateDialogState.None -> Unit
        is PromptTemplateDialogState.Metadata -> MetadataDialog(dialog, state, emit)
        is PromptTemplateDialogState.Delete -> AppDangerDialog(
            onDismissRequest = { PromptTemplateListUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.prompt_template_delete_title),
            message = stringResource(
                if (dialog.item.isActive) R.string.prompt_template_delete_active else R.string.prompt_template_delete_message,
                dialog.item.name
            ),
            confirmEnabled = !state.busy,
            isConfirmLoading = state.busy,
            onConfirm = { PromptTemplateListUiIntent.ConfirmDelete.emit() },
        )
    }
}

@Composable
private fun MetadataDialog(
    dialog: PromptTemplateDialogState.Metadata,
    state: PromptTemplateListUiState.Normal,
    emit: PromptTemplateListUiIntent.() -> Unit
) {
    AppInputDialog(
        onDismissRequest = { PromptTemplateListUiIntent.DismissDialog.emit() },
        title = stringResource(if (dialog.targetId == null) R.string.prompt_template_create else R.string.prompt_template_edit_details),
        value = dialog.name,
        label = stringResource(R.string.prompt_template_name),
        onValueChange = { PromptTemplateListUiIntent.ChangeName(it).emit() },
        enabled = !state.busy,
        confirmEnabled = !state.busy && dialog.name.isNotBlank(),
        isConfirmLoading = state.busy,
        confirmText = stringResource(R.string.save),
        onConfirm = { PromptTemplateListUiIntent.SaveMetadata.emit() },
        extraContent = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = dialog.description,
                label = { Text(stringResource(R.string.prompt_template_description)) },
                onValueChange = { PromptTemplateListUiIntent.ChangeDescription(it).emit() },
                enabled = !state.busy,
                minLines = 2,
                maxLines = 4,
            )

        },
    )
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun PromptTemplateListPreview() {
    AppTheme(dynamicColor = false) {
        PromptTemplateListLayout(
            uiState = PromptTemplateListUiState.Normal(
                contentState = PromptTemplateContentState.Ready(
                    items = listOf(
                        PromptTemplateItem(
                            -1L,
                            "System default",
                            "",
                            true,
                            false,
                            1,
                            ""
                        ),
                        PromptTemplateItem(
                            2L,
                            "Long-form storytelling",
                            "Character-driven scenes and concise memories.",
                            false,
                            true,
                            1,
                            "2026/11/06"
                        ),
                    ),
                    activeName = "Long-form storytelling",
                    activeId = 2L,
                )
            ),
            emit = {},
        )
    }
}
