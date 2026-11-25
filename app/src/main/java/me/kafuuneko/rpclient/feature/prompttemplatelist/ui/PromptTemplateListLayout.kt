package me.kafuuneko.rpclient.feature.prompttemplatelist.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.prompttemplatelist.presentation.PromptTemplateContentState
import me.kafuuneko.rpclient.feature.prompttemplatelist.presentation.PromptTemplateDialogState
import me.kafuuneko.rpclient.feature.prompttemplatelist.presentation.PromptTemplateItem
import me.kafuuneko.rpclient.feature.prompttemplatelist.presentation.PromptTemplateListUiIntent
import me.kafuuneko.rpclient.feature.prompttemplatelist.presentation.PromptTemplateListUiState
import me.kafuuneko.rpclient.feature.prompttemplatelist.presentation.PromptTemplateMetadataMode
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.dialog.AppInputDialog
import me.kafuuneko.rpclient.ui.theme.AccentVioletColor
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppCard
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpMetaPill
import me.kafuuneko.rpclient.ui.widgets.RpScrollableOutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.RpTagPill
import me.kafuuneko.rpclient.ui.widgets.pageCanvasColor
import me.kafuuneko.rpclient.ui.widgets.PageButton as Button

/** 使用项目统一卡片、动态颜色和弹窗的系统提示词模板管理页。 */
@Composable
fun PromptTemplateListLayout(
    uiState: PromptTemplateListUiState,
    emit: PromptTemplateListUiIntent.() -> Unit,
) {
    BackHandler(enabled = uiState is PromptTemplateListUiState.Normal) {
        PromptTemplateListUiIntent.Back.emit()
    }
    // 退出过渡态继续绘制上一帧，避免宿主销毁前画面闪白。
    when (uiState) {
        PromptTemplateListUiState.None -> Unit
        is PromptTemplateListUiState.Finished -> PromptTemplateListLayout(
            uiState = uiState.previous,
            emit = {},
        )

        is PromptTemplateListUiState.Normal -> {
            NormalView(state = uiState, emit = emit)
            DialogSwitch(state = uiState, emit = emit)
        }
    }
}

@Composable
private fun NormalView(
    state: PromptTemplateListUiState.Normal,
    emit: PromptTemplateListUiIntent.() -> Unit,
) {
    val canCreate = !state.busy && state.contentState is PromptTemplateContentState.Ready
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageCanvasColor()),
    ) {
        AppTopBar(
            title = stringResource(R.string.prompt_preset_title),
            onBack = { PromptTemplateListUiIntent.Back.emit() },
            actions = {
                IconButton(
                    onClick = { PromptTemplateListUiIntent.Create.emit() },
                    enabled = canCreate,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.prompt_template_create),
                    )
                }
            },
        )
        if (state.busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        // 顶栏已承载标题与新建入口，列表直接呈现预设单选卡片以减少视觉噪音。
        RpLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                top = 14.dp,
                end = 18.dp,
                bottom = bottomInset + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (val content = state.contentState) {
                PromptTemplateContentState.Loading -> {
                    item { LoadingRow() }
                }

                PromptTemplateContentState.Failed -> {
                    item {
                        FailedCard(
                            enabled = !state.busy,
                            onRetry = { PromptTemplateListUiIntent.Retry.emit() },
                        )
                    }
                }

                is PromptTemplateContentState.Ready -> {
                    items(
                        items = content.items,
                        key = { presetItem -> presetItem.id },
                    ) { item ->
                        TemplateCard(
                            item = item,
                            busy = state.busy,
                            emit = emit,
                        )
                    }
                    if (content.items.none { presetItem -> !presetItem.isBuiltin }) {
                        item {
                            EmptyCustomPresetsCard(
                                enabled = !state.busy,
                                onCreateClick = { PromptTemplateListUiIntent.Create.emit() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun FailedCard(
    enabled: Boolean,
    onRetry: () -> Unit,
) {
    // 卡片容器在透明壁纸下仍保持错误提示与重试按钮的清晰对比度。
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 0.8.dp,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.30f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RpIconBubble(
                icon = Icons.Rounded.ErrorOutline,
                size = 48.dp,
                contentColor = MaterialTheme.colorScheme.error,
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
            )
            Text(
                text = stringResource(R.string.prompt_template_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onRetry,
                enabled = enabled,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(text = stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun EmptyCustomPresetsCard(
    enabled: Boolean,
    onCreateClick: () -> Unit,
) {
    // 仅有内置预设时在底部提供轻量新建引导，沿用模型配置空态视觉语言。
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
        border = BorderStroke(
            width = 0.8.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RpIconBubble(
                icon = Icons.Rounded.AutoAwesome,
                size = 50.dp,
                contentColor = AccentVioletColor,
                containerColor = AccentVioletColor.copy(alpha = 0.14f),
            )
            Text(
                text = stringResource(R.string.prompt_template_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.prompt_template_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Button(
                onClick = onCreateClick,
                enabled = enabled,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.prompt_template_create))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateCard(
    item: PromptTemplateItem,
    busy: Boolean,
    emit: PromptTemplateListUiIntent.() -> Unit,
) {
    val containerColor = if (item.isActive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor = if (item.isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    }
    val titleColor = if (item.isActive) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (item.isActive) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val descriptionText = if (item.isBuiltin) {
        stringResource(R.string.prompt_template_builtin_desc)
    } else {
        item.description
    }

    // 点击未激活卡片直接切换为当前启用预设，整卡复用标准单选交互与圆角水波纹裁剪。
    AppCard(
        onClick = if (item.isActive) null else {
            { PromptTemplateListUiIntent.Activate(item.id).emit() }
        },
        enabled = !busy,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                selected = item.isActive
                role = Role.RadioButton
            },
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = if (item.isActive) 1.5.dp else 0.8.dp,
            color = borderColor,
        ),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RadioButton(
                    selected = item.isActive,
                    onClick = null,
                    enabled = !busy,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = item.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (item.isActive) FontWeight.Bold else FontWeight.SemiBold,
                            color = titleColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (item.isActive) {
                            ActiveStatusBadge()
                        } else if (item.isBuiltin) {
                            RpMetaPill(text = stringResource(R.string.prompt_template_builtin_badge))
                        }
                    }
                    if (descriptionText.isNotBlank()) {
                        Text(
                            text = descriptionText,
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 集中展示覆盖项数、内置属性与更新时间，帮助用户快速判断待切换模板。
            FlowRow(
                modifier = Modifier.padding(start = 34.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (item.isBuiltin) {
                    if (item.isActive) {
                        CardMetaChip(
                            text = stringResource(R.string.prompt_template_builtin_badge),
                            onActiveCard = true,
                        )
                    }
                    CardMetaChip(
                        text = stringResource(R.string.prompt_template_all_default),
                        onActiveCard = item.isActive,
                    )
                } else {
                    if (item.overrideCount > 0) {
                        val customLabel = pluralStringResource(
                            id = R.plurals.prompt_template_custom_count,
                            count = item.overrideCount,
                            item.overrideCount,
                        )
                        if (item.isActive) {
                            CardMetaChip(
                                text = customLabel,
                                onActiveCard = true,
                                emphasized = true,
                            )
                        } else {
                            RpTagPill(text = customLabel)
                        }
                    } else {
                        CardMetaChip(
                            text = stringResource(R.string.prompt_template_all_default),
                            onActiveCard = item.isActive,
                        )
                    }
                    if (item.updatedTime.isNotBlank()) {
                        CardMetaChip(
                            text = item.updatedTime,
                            onActiveCard = item.isActive,
                        )
                    }
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = if (item.isActive) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
                },
            )

            TemplateCardActions(
                item = item,
                busy = busy,
                iconTint = secondaryColor,
                emit = emit,
            )
        }
    }
}

@Composable
private fun ActiveStatusBadge() {
    // 激活态卡片底板已是 primaryContainer，徽章采用实心主色填充确保高对比度。
    AppSurface(
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color = MaterialTheme.colorScheme.onPrimary, shape = CircleShape),
            )
            Text(
                text = stringResource(R.string.prompt_template_active),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CardMetaChip(
    text: String,
    onActiveCard: Boolean,
    emphasized: Boolean = false,
) {
    // 激活态卡片上使用半透明表面底板托底，避免元信息标签与主色容器融为一体。
    if (!onActiveCard) {
        RpMetaPill(text = text)
        return
    }
    AppSurface(
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = if (emphasized) 0.28f else 0.16f),
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            color = if (emphasized) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TemplateCardActions(
    item: PromptTemplateItem,
    busy: Boolean,
    iconTint: Color,
    emit: PromptTemplateListUiIntent.() -> Unit,
) {
    // 菜单仅维护展开状态，所有业务操作继续经由 Intent 上报。
    var menuExpanded by remember(item.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OpenDetailPill(
            isBuiltin = item.isBuiltin,
            onActiveCard = item.isActive,
            enabled = !busy,
            onClick = { PromptTemplateListUiIntent.Open(item.id).emit() },
        )

        Spacer(modifier = Modifier.weight(1f))

        IconButton(
            onClick = { PromptTemplateListUiIntent.Copy(item.id).emit() },
            enabled = !busy,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = stringResource(R.string.prompt_template_copy),
                tint = iconTint,
                modifier = Modifier.size(19.dp),
            )
        }

        if (!item.isBuiltin) {
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    enabled = !busy,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.more),
                        tint = iconTint,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.prompt_template_edit_details)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.EditNote,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            PromptTemplateListUiIntent.Rename(item.id).emit()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            PromptTemplateListUiIntent.Delete(item.id).emit()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OpenDetailPill(
    isBuiltin: Boolean,
    onActiveCard: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // 卡片主体负责单选激活，左下角独立胶囊按钮提供明确的详情编辑/只读查看入口。
    val containerColor = if (onActiveCard) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
    }
    val contentColor = if (onActiveCard) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    AppSurface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = if (isBuiltin) Icons.Rounded.Visibility else Icons.Rounded.Edit,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = stringResource(
                    if (isBuiltin) {
                        R.string.prompt_template_view_prompts
                    } else {
                        R.string.prompt_template_edit_prompts
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun DialogSwitch(
    state: PromptTemplateListUiState.Normal,
    emit: PromptTemplateListUiIntent.() -> Unit,
) {
    // 删除确认区分当前模板，提前说明原子切回默认配置的结果。
    when (val dialog = state.dialogState) {
        PromptTemplateDialogState.None -> Unit
        is PromptTemplateDialogState.Metadata -> MetadataDialog(
            dialog = dialog,
            state = state,
            emit = emit,
        )

        is PromptTemplateDialogState.Delete -> AppDangerDialog(
            onDismissRequest = { PromptTemplateListUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.prompt_template_delete_title),
            message = stringResource(
                if (dialog.item.isActive) {
                    R.string.prompt_template_delete_active
                } else {
                    R.string.prompt_template_delete_message
                },
                dialog.item.name,
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
    emit: PromptTemplateListUiIntent.() -> Unit,
) {
    val titleRes = when (dialog.mode) {
        PromptTemplateMetadataMode.Create -> R.string.prompt_template_create
        PromptTemplateMetadataMode.Copy -> R.string.prompt_template_copy
        PromptTemplateMetadataMode.Edit -> R.string.prompt_template_edit_details
    }
    val badgeIcon = when (dialog.mode) {
        PromptTemplateMetadataMode.Create -> Icons.Rounded.Add
        PromptTemplateMetadataMode.Copy -> Icons.Rounded.ContentCopy
        PromptTemplateMetadataMode.Edit -> Icons.Rounded.Edit
    }
    val subtitle =
        if (dialog.mode == PromptTemplateMetadataMode.Copy && dialog.sourceName.isNotBlank()) {
            stringResource(R.string.prompt_template_copy_source, dialog.sourceName)
        } else {
            null
        }
    val confirmRes = if (dialog.mode == PromptTemplateMetadataMode.Edit) {
        R.string.save
    } else {
        R.string.create
    }

    // 名称输入采用 Next 动作，避免填写名称后回车直接跳过简介表单。
    AppInputDialog(
        onDismissRequest = { PromptTemplateListUiIntent.DismissDialog.emit() },
        title = stringResource(titleRes),
        subtitle = subtitle,
        value = dialog.name,
        label = stringResource(R.string.prompt_template_name),
        onValueChange = { value -> PromptTemplateListUiIntent.ChangeName(value).emit() },
        enabled = !state.busy,
        badgeIcon = badgeIcon,
        imeAction = ImeAction.Next,
        confirmEnabled = !state.busy && dialog.name.isNotBlank(),
        isConfirmLoading = state.busy,
        confirmText = stringResource(confirmRes),
        onConfirm = { PromptTemplateListUiIntent.SaveMetadata.emit() },
        extraContent = {
            RpScrollableOutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = dialog.description,
                label = { Text(text = stringResource(R.string.prompt_template_description)) },
                onValueChange = { value ->
                    PromptTemplateListUiIntent.ChangeDescription(value).emit()
                },
                enabled = !state.busy,
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
            )
        },
    )
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true, locale = "zh")
@Composable
private fun PromptTemplateListPreview() {
    // 预览只构造静态列表状态，不依赖数据库或偏好存储。
    AppTheme(dynamicColor = false) {
        PromptTemplateListLayout(
            uiState = PromptTemplateListUiState.Normal(
                contentState = PromptTemplateContentState.Ready(
                    items = listOf(
                        PromptTemplateItem(
                            id = -1L,
                            name = "系统默认",
                            description = "",
                            isBuiltin = true,
                            isActive = false,
                            revision = 1,
                            updatedTime = "",
                            overrideCount = 0,
                        ),
                        PromptTemplateItem(
                            id = 2L,
                            name = "长篇叙事预设",
                            description = "注重角色描写与克制节奏，强化对话细节与简洁记忆。",
                            isBuiltin = false,
                            isActive = true,
                            revision = 3,
                            updatedTime = "2026/11/06 14:20",
                            overrideCount = 6,
                        ),
                    ),
                ),
            ),
            emit = {},
        )
    }
}
