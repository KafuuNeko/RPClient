package me.kafuuneko.rpclient.feature.promptpreset.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.promptpreset.model.PromptPresetCategory
import me.kafuuneko.rpclient.feature.promptpreset.model.PromptPresetScope
import me.kafuuneko.rpclient.feature.promptpreset.model.PromptPresetSection
import me.kafuuneko.rpclient.feature.promptpreset.model.promptPresetField
import me.kafuuneko.rpclient.feature.promptpreset.model.promptPresetFields
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetBrowserState
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetDialogState
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetFieldState
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetGroupState
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetLoadState
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetSectionState
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetUiIntent
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetUiState
import me.kafuuneko.rpclient.ui.dialog.AppConfirmDialog
import me.kafuuneko.rpclient.ui.dialog.AppInputDialog
import me.kafuuneko.rpclient.ui.dialog.AppPromptEditorDialog
import me.kafuuneko.rpclient.ui.dialog.DialogBadgeTone
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppPageScaffold
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpSettingsDivider
import me.kafuuneko.rpclient.ui.widgets.RpSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpSettingsTile
import me.kafuuneko.rpclient.ui.widgets.pageCanvasColor
import me.kafuuneko.rpclient.ui.widgets.PageFilterChip as FilterChip

/**
 * 按用途浏览预设，并通过场景筛选定位单聊、群聊和故事共用的字段。
 * @param uiState 包含场景筛选、可见分组与编辑草稿的页面状态。
 * @param emit 将用户操作交给 ViewModel 处理。
 */
@Composable
fun PromptPresetLayout(
    uiState: PromptPresetUiState,
    emit: PromptPresetUiIntent.() -> Unit = {},
) {
    BackHandler(enabled = uiState is PromptPresetUiState.Normal) { PromptPresetUiIntent.Back.emit() }
    // 编辑器与浏览条件分属不同状态节点，关闭编辑器后继续显示原位置。
    when (uiState) {
        PromptPresetUiState.None -> Unit
        is PromptPresetUiState.Finished -> PromptPresetLayout(uiState = uiState.previous, emit = {})
        is PromptPresetUiState.Normal -> {
            NormalView(uiState = uiState, emit = emit)
            DialogSwitch(dialogState = uiState.dialogState, busy = uiState.busy, emit = emit)
        }
    }
}

@Composable
private fun NormalView(
    uiState: PromptPresetUiState.Normal,
    emit: PromptPresetUiIntent.() -> Unit,
) {
    // 详情页继续使用预设名称与主动复制入口，启用流程仍由管理列表承载。
    AppPageScaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(left = 0.dp),
        topBar = {
            AppTopBar(
                title = uiState.name.ifBlank { stringResource(R.string.prompt_preset_title) },
                actions = {
                    IconButton(
                        onClick = { PromptPresetUiIntent.CopyTemplate.emit() },
                        enabled = !uiState.busy && uiState.loadState == PromptPresetLoadState.Ready,
                    ) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            stringResource(R.string.prompt_template_copy)
                        )
                    }
                },
                onBack = { PromptPresetUiIntent.Back.emit() },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .navigationBarsPadding()
                .background(pageCanvasColor()),
        ) {
            if (uiState.loadState == PromptPresetLoadState.Ready) {
                PromptScopeFilters(
                    scope = uiState.browserState.scope,
                    enabled = !uiState.busy,
                    emit = emit
                )
                PromptBrowserGroups(
                    browser = uiState.browserState,
                    busy = uiState.busy,
                    modifier = Modifier.weight(1f),
                    emit = emit,
                )
            } else {
                PromptLoadStatus(loadState = uiState.loadState, emit = emit)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PromptScopeFilters(
    scope: PromptPresetScope,
    enabled: Boolean,
    emit: PromptPresetUiIntent.() -> Unit,
) {
    // 筛选始终可见且允许换行，兼顾长列表、窄屏和较长译文。
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, top = 8.dp, end = 18.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PromptPresetScope.entries.forEach { option ->
            FilterChip(
                selected = scope == option,
                onClick = { PromptPresetUiIntent.SelectScope(option).emit() },
                enabled = enabled,
                label = { Text(stringResource(option.titleRes)) },
            )
        }
    }
}

@Composable
private fun PromptBrowserGroups(
    browser: PromptPresetBrowserState,
    busy: Boolean,
    modifier: Modifier,
    emit: PromptPresetUiIntent.() -> Unit,
) {
    // 分类使用稳定键，编辑保存与展开切换不会重新创建整个列表滚动状态。
    RpLazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 18.dp, top = 4.dp, end = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(items = browser.groups, key = { it.category }) { group ->
            PromptCategoryCard(
                group = group,
                busy = busy,
                emit = emit
            )
        }
    }
}

@Composable
private fun PromptCategoryCard(
    group: PromptPresetGroupState,
    busy: Boolean,
    emit: PromptPresetUiIntent.() -> Unit,
) {
    val countDescription = stringResource(R.string.prompt_catalog_count, group.itemCount)
    // 一个用途分类只使用一个容器，字段行不重复显示正文和装饰图标。
    RpSettingsGroup {
        RpSettingsTile(
            title = stringResource(group.category.titleRes),
            icon = group.category.icon(),
            enabled = !busy,
            onClick = { PromptPresetUiIntent.ToggleCategory(group.category).emit() },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = group.itemCount.toString(),
                        modifier = Modifier.semantics { contentDescription = countDescription },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (group.expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = stringResource(if (group.expanded) R.string.collapse else R.string.expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        if (group.expanded) {
            RpSettingsDivider(startIndent = false)
            group.sections.forEach { section ->
                PromptSectionContent(section = section, busy = busy, emit = emit)
            }
        }
    }
}

@Composable
private fun PromptSectionContent(
    section: PromptPresetSectionState,
    busy: Boolean,
    emit: PromptPresetUiIntent.() -> Unit,
) {
    // 小节名区分生成与注入、角色消息续写与用户草稿续写。
    section.section.titleRes?.let { titleRes ->
        Text(
            text = stringResource(titleRes),
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    section.items.forEachIndexed { index, item ->
        if (index > 0) RpSettingsDivider(startIndent = false)
        RpSettingsTile(
            title = stringResource(item.field.titleRes),
            subtitle = stringResource(item.statusRes),
            enabled = !busy,
            onClick = { PromptPresetUiIntent.EditPromptClick(item.field.type).emit() },
            trailing = {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            },
        )
    }
}

@Composable
private fun PromptLoadStatus(
    loadState: PromptPresetLoadState,
    emit: PromptPresetUiIntent.() -> Unit,
) {
    Column(modifier = Modifier.padding(24.dp)) {
        when (loadState) {
            PromptPresetLoadState.Loading -> CircularProgressIndicator()
            PromptPresetLoadState.Failed -> {
                Text(stringResource(R.string.prompt_template_failed))
                TextButton(onClick = { PromptPresetUiIntent.Retry.emit() }) {
                    Text(stringResource(R.string.retry))
                }
            }

            PromptPresetLoadState.Ready -> Unit
        }
    }
}

private fun PromptPresetCategory.icon(): ImageVector = when (this) {
    PromptPresetCategory.Basic -> Icons.Rounded.Tune
    PromptPresetCategory.Generation -> Icons.Rounded.AutoAwesome
    PromptPresetCategory.Memory -> Icons.Rounded.Compress
    PromptPresetCategory.Format -> Icons.Rounded.DataObject
}

@Composable
private fun DialogSwitch(
    dialogState: PromptPresetDialogState,
    busy: Boolean,
    emit: PromptPresetUiIntent.() -> Unit,
) {
    // 复制、编辑与放弃草稿互斥展示，关闭流程继续由 ViewModel 校验。
    when (dialogState) {
        is PromptPresetDialogState.None -> Unit
        is PromptPresetDialogState.CopyTemplate -> AppInputDialog(
            onDismissRequest = { PromptPresetUiIntent.DismissPromptDialog.emit() },
            title = stringResource(R.string.prompt_template_copy),
            value = dialogState.name,
            label = stringResource(R.string.prompt_template_name),
            onValueChange = { PromptPresetUiIntent.ChangeCopyName(it).emit() },
            enabled = !busy,
            confirmEnabled = !busy && dialogState.name.isNotBlank(),
            isConfirmLoading = busy,
            onConfirm = { PromptPresetUiIntent.ConfirmCopy.emit() },
        )

        is PromptPresetDialogState.Discard -> AppConfirmDialog(
            onDismissRequest = { PromptPresetUiIntent.DismissPromptDialog.emit() },
            title = stringResource(R.string.unsaved_changes_title),
            message = stringResource(R.string.unsaved_changes_message),
            confirmText = stringResource(R.string.discard_changes),
            onConfirm = { PromptPresetUiIntent.ConfirmDiscard.emit() },
        )

        is PromptPresetDialogState.EditPrompt -> EditPromptDialog(
            dialogState = dialogState,
            busy = busy,
            onDismiss = { PromptPresetUiIntent.DismissPromptDialog.emit() },
            onChange = { PromptPresetUiIntent.ChangePromptDraft(it).emit() },
            onCopy = { PromptPresetUiIntent.CopyPromptDraft.emit() },
            onSave = { PromptPresetUiIntent.SavePrompt.emit() },
        )
    }
}

@Composable
private fun EditPromptDialog(
    dialogState: PromptPresetDialogState.EditPrompt,
    busy: Boolean,
    onDismiss: () -> Unit,
    onChange: (String) -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
) {
    // 字段只提供标题与操作，实时字符和 Token 统计由共享编辑器统一展示。
    val field = promptPresetField(dialogState.type)
    AppPromptEditorDialog(
        onDismissRequest = onDismiss,
        title = stringResource(field.titleRes),
        badgeIcon = field.section.category.icon(),
        badgeTone = DialogBadgeTone.Primary,
        value = dialogState.draftText,
        onValueChange = onChange,
        defaultValue = dialogState.defaultText,
        availableMacros = dialogState.availableMacros,
        onCopyRequest = { onCopy() },
        onConfirm = onSave,
        confirmEnabled = !busy,
        isConfirmLoading = busy,
    )
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true, locale = "zh")
@Composable
private fun PromptPresetLayoutPreview() {
    // 预览只构造目录状态，不读取数据库或生成请求。
    val groups = PromptPresetCategory.entries.map { category ->
        PromptPresetGroupState(
            category = category,
            expanded = category == PromptPresetCategory.Basic,
            sections = promptPresetFields.filter { it.section.category == category }
                .groupBy { it.section }
                .map { (section, fields) ->
                    PromptPresetSectionState(
                        section = section,
                        items = fields.map {
                            PromptPresetFieldState(
                                field = it,
                                statusRes = R.string.prompt_status_default
                            )
                        },
                    )
                },
        )
    }
    AppTheme(dynamicColor = false) {
        PromptPresetLayout(
            uiState = PromptPresetUiState.Normal(
                promptValues = emptyMap(),
                browserState = PromptPresetBrowserState(groups = groups),
            ),
        )
    }
}
