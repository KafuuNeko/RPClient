package me.kafuuneko.rpclient.feature.promptpreset.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterListOff
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.promptpreset.model.PromptPresetCategory
import me.kafuuneko.rpclient.feature.promptpreset.model.PromptPresetScope
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
import me.kafuuneko.rpclient.ui.theme.AccentEmeraldColor
import me.kafuuneko.rpclient.ui.theme.AccentIndigoColor
import me.kafuuneko.rpclient.ui.theme.AccentSkyColor
import me.kafuuneko.rpclient.ui.theme.AccentVioletColor
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppCard
import me.kafuuneko.rpclient.ui.widgets.AppPageScaffold
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpMetaPill
import me.kafuuneko.rpclient.ui.widgets.RpSegmentedTabs
import me.kafuuneko.rpclient.ui.widgets.RpSettingsDivider
import me.kafuuneko.rpclient.ui.widgets.RpSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpSettingsTile
import me.kafuuneko.rpclient.ui.widgets.RpTagPill
import me.kafuuneko.rpclient.ui.widgets.pageCanvasColor
import me.kafuuneko.rpclient.ui.widgets.pageFilterChipColors
import me.kafuuneko.rpclient.ui.widgets.PageButton as Button
import me.kafuuneko.rpclient.ui.widgets.PageFilterChip as FilterChip

/**
 * 按用途浏览预设，并通过场景切换与自定义筛选定位单聊、群聊和故事共用的字段。
 *
 * @param uiState 包含场景筛选、可见分组与编辑草稿的页面状态。
 * @param emit 将用户操作交给 ViewModel 处理。
 */
@Composable
fun PromptPresetLayout(
    uiState: PromptPresetUiState,
    emit: PromptPresetUiIntent.() -> Unit = {},
) {
    BackHandler(enabled = uiState is PromptPresetUiState.Normal) {
        PromptPresetUiIntent.Back.emit()
    }
    // 编辑器与浏览条件分属不同状态节点，关闭编辑器后继续显示原位置。
    when (uiState) {
        PromptPresetUiState.None -> Unit
        is PromptPresetUiState.Finished -> PromptPresetLayout(
            uiState = uiState.previous,
            emit = {},
        )

        is PromptPresetUiState.Normal -> {
            NormalView(uiState = uiState, emit = emit)
            DialogSwitch(
                dialogState = uiState.dialogState,
                busy = uiState.busy,
                emit = emit,
            )
        }
    }
}

@Composable
private fun NormalView(
    uiState: PromptPresetUiState.Normal,
    emit: PromptPresetUiIntent.() -> Unit,
) {
    val isReady = uiState.loadState == PromptPresetLoadState.Ready
    val hasGroups = uiState.browserState.groups.isNotEmpty()
    // 详情页保留预设名称、全部展开折叠与复制整套入口，启用流程仍由管理列表承载。
    AppPageScaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(left = 0.dp),
        topBar = {
            Column {
                AppTopBar(
                    title = uiState.name.ifBlank { stringResource(R.string.prompt_preset_title) },
                    actions = {
                        if (isReady && hasGroups) {
                            IconButton(
                                onClick = { PromptPresetUiIntent.ToggleExpandAll.emit() },
                                enabled = !uiState.busy,
                            ) {
                                Icon(
                                    imageVector = if (uiState.browserState.allExpanded) {
                                        Icons.Rounded.UnfoldLess
                                    } else {
                                        Icons.Rounded.UnfoldMore
                                    },
                                    contentDescription = stringResource(
                                        if (uiState.browserState.allExpanded) {
                                            R.string.prompt_preset_collapse_all
                                        } else {
                                            R.string.prompt_preset_expand_all
                                        },
                                    ),
                                )
                            }
                        }
                        IconButton(
                            onClick = { PromptPresetUiIntent.CopyTemplate.emit() },
                            enabled = !uiState.busy && isReady,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = stringResource(R.string.prompt_template_copy),
                            )
                        }
                    },
                    onBack = { PromptPresetUiIntent.Back.emit() },
                )
                if (uiState.busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(pageCanvasColor()),
        ) {
            if (isReady) {
                PromptBrowserHeader(
                    browser = uiState.browserState,
                    isBuiltin = uiState.isBuiltin,
                    enabled = !uiState.busy,
                    emit = emit,
                )
                PromptBrowserGroups(
                    browser = uiState.browserState,
                    isBuiltin = uiState.isBuiltin,
                    busy = uiState.busy,
                    modifier = Modifier.weight(1f),
                    emit = emit,
                )
            } else {
                PromptLoadStatus(
                    loadState = uiState.loadState,
                    busy = uiState.busy,
                    emit = emit,
                )
            }
        }
    }
}

@Composable
private fun PromptBrowserHeader(
    browser: PromptPresetBrowserState,
    isBuiltin: Boolean,
    enabled: Boolean,
    emit: PromptPresetUiIntent.() -> Unit,
) {
    // 顶部固定场景分段栏与只读/自定义筛选行，切换时不改变模板生效范围。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RpSegmentedTabs(
            tabs = PromptPresetScope.entries,
            selectedTab = browser.scope,
            onTabSelected = { scope ->
                if (enabled) PromptPresetUiIntent.SelectScope(scope).emit()
            },
            tabLabel = { scope -> stringResource(scope.titleRes) },
        )

        if (isBuiltin) {
            ReadOnlyHintBanner()
        } else {
            CustomFilterRow(
                browser = browser,
                enabled = enabled,
                emit = emit,
            )
        }
    }
}

@Composable
private fun ReadOnlyHintBanner() {
    // 提前告知内置预设可查看与复制，消除点击后无法保存的预期落差。
    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Visibility,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.prompt_preset_read_only_banner),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CustomFilterRow(
    browser: PromptPresetBrowserState,
    enabled: Boolean,
    emit: PromptPresetUiIntent.() -> Unit,
) {
    // 高对比筛选胶囊随当前场景同步展示已改计数，便于快速审计自定义差异。
    val selected = browser.onlyCustomized
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FilterChip(
            selected = selected,
            onClick = { PromptPresetUiIntent.ToggleOnlyCustomized.emit() },
            enabled = enabled,
            shape = RoundedCornerShape(10.dp),
            leadingIcon = if (selected) {
                {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                }
            } else {
                null
            },
            label = {
                Text(
                    text = stringResource(
                        R.string.prompt_preset_only_customized,
                        browser.scopedCustomCount,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                )
            },
            border = FilterChipDefaults.filterChipBorder(
                enabled = enabled,
                selected = selected,
                selectedBorderColor = MaterialTheme.colorScheme.primary,
                selectedBorderWidth = 1.2.dp,
                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f),
            ),
            colors = pageFilterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Text(
            text = pluralStringResource(
                id = R.plurals.prompt_catalog_count,
                count = browser.scopedTotalCount,
                browser.scopedTotalCount,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PromptBrowserGroups(
    browser: PromptPresetBrowserState,
    isBuiltin: Boolean,
    busy: Boolean,
    modifier: Modifier,
    emit: PromptPresetUiIntent.() -> Unit,
) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 分类使用稳定键，编辑保存与展开切换不会重新创建整个列表滚动状态。
    RpLazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 18.dp,
            top = 4.dp,
            end = 18.dp,
            bottom = bottomInset + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (browser.groups.isEmpty() && browser.onlyCustomized) {
            item {
                NoCustomizedPromptsCard(
                    enabled = !busy,
                    onShowAll = { PromptPresetUiIntent.ClearFilters.emit() },
                )
            }
        } else {
            items(
                items = browser.groups,
                key = { group -> group.category },
            ) { group ->
                PromptCategoryCard(
                    group = group,
                    showScopes = browser.scope == PromptPresetScope.All,
                    isBuiltin = isBuiltin,
                    busy = busy,
                    emit = emit,
                )
            }
        }
    }
}

@Composable
private fun NoCustomizedPromptsCard(
    enabled: Boolean,
    onShowAll: () -> Unit,
) {
    // 仅看已改无结果时给出明确空态与一键恢复按钮，避免误以为预设条目丢失。
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
                icon = Icons.Rounded.FilterListOff,
                size = 48.dp,
                contentColor = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.50f),
            )
            Text(
                text = stringResource(R.string.prompt_preset_no_custom_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.prompt_preset_no_custom_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onShowAll,
                enabled = enabled,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(text = stringResource(R.string.prompt_preset_show_all))
            }
        }
    }
}

@Composable
private fun PromptCategoryCard(
    group: PromptPresetGroupState,
    showScopes: Boolean,
    isBuiltin: Boolean,
    busy: Boolean,
    emit: PromptPresetUiIntent.() -> Unit,
) {
    val countDescription = pluralStringResource(
        id = R.plurals.prompt_catalog_count,
        count = group.itemCount,
        group.itemCount,
    )
    val accentColor = group.category.accentColor()
    val arrowRotation by animateFloatAsState(
        targetValue = if (group.expanded) 180f else 0f,
        label = "categoryArrowRotation",
    )

    // 一个用途分类使用独立分组卡片，折叠头同步呈现自定义项进度。
    RpSettingsGroup {
        RpSettingsTile(
            title = stringResource(group.category.titleRes),
            icon = group.category.icon(),
            iconColor = accentColor,
            iconContainerColor = accentColor.copy(alpha = 0.14f),
            enabled = !busy,
            onClick = { PromptPresetUiIntent.ToggleCategory(group.category).emit() },
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (group.customCount > 0) {
                        RpTagPill(
                            text = stringResource(
                                R.string.prompt_preset_group_custom_count,
                                group.customCount,
                                group.totalCount,
                            ),
                        )
                    } else {
                        Text(
                            text = group.itemCount.toString(),
                            modifier = Modifier.semantics { contentDescription = countDescription },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = stringResource(
                            if (group.expanded) R.string.collapse else R.string.expand,
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(arrowRotation),
                    )
                }
            },
        )
        AnimatedVisibility(
            visible = group.expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                RpSettingsDivider(startIndent = false)
                group.sections.forEachIndexed { sectionIndex, section ->
                    if (sectionIndex > 0) {
                        RpSettingsDivider(startIndent = false)
                    }
                    PromptSectionContent(
                        section = section,
                        showScopes = showScopes,
                        isBuiltin = isBuiltin,
                        busy = busy,
                        emit = emit,
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptSectionContent(
    section: PromptPresetSectionState,
    showScopes: Boolean,
    isBuiltin: Boolean,
    busy: Boolean,
    emit: PromptPresetUiIntent.() -> Unit,
) {
    // 小节名区分生成与注入、角色消息续写与用户草稿续写。
    section.section.titleRes?.let { titleRes ->
        Text(
            text = stringResource(titleRes),
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    section.items.forEachIndexed { index, item ->
        if (index > 0) RpSettingsDivider(startIndent = false)
        PromptFieldRow(
            item = item,
            showScopes = showScopes,
            isBuiltin = isBuiltin,
            enabled = !busy,
            onClick = { PromptPresetUiIntent.EditPromptClick(item.field.type).emit() },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PromptFieldRow(
    item: PromptPresetFieldState,
    showScopes: Boolean,
    isBuiltin: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 已自定义条目用左侧强调色条标注，在 31 项列表中可一眼扫视定位。
        if (item.isCustomized) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(item.field.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (item.isCustomized) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f)
                    },
                )
                if (item.isCustomized) {
                    RpTagPill(
                        text = stringResource(
                            if (item.isEmpty) {
                                R.string.prompt_status_custom_empty
                            } else {
                                R.string.prompt_status_custom
                            },
                        ),
                    )
                } else if (item.isEmpty) {
                    RpMetaPill(text = stringResource(R.string.prompt_preset_empty_preview))
                }
            }

            if (item.previewText.isNotEmpty()) {
                Text(
                    text = item.previewText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = stringResource(R.string.prompt_preset_empty_preview),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    maxLines = 1,
                )
            }

            if (showScopes) {
                val scopeLabels = item.field.scopes.map { scope -> stringResource(scope.titleRes) }
                Text(
                    text = scopeLabels.joinToString(separator = " · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = if (isBuiltin) Icons.Rounded.Visibility else Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            modifier = Modifier.size(if (isBuiltin) 18.dp else 20.dp),
        )
    }
}

@Composable
private fun PromptLoadStatus(
    loadState: PromptPresetLoadState,
    busy: Boolean,
    emit: PromptPresetUiIntent.() -> Unit,
) {
    // 加载与失败态居中展示，失败卡片保留主题底板与重试入口。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (loadState) {
            PromptPresetLoadState.Loading -> CircularProgressIndicator()
            PromptPresetLoadState.Failed -> {
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
                            onClick = { PromptPresetUiIntent.Retry.emit() },
                            enabled = !busy,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(text = stringResource(R.string.retry))
                        }
                    }
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
    PromptPresetCategory.Agent -> Icons.Rounded.SmartToy
    PromptPresetCategory.Format -> Icons.Rounded.DataObject
}

private fun PromptPresetCategory.accentColor(): Color = when (this) {
    PromptPresetCategory.Basic -> AccentIndigoColor
    PromptPresetCategory.Generation -> AccentVioletColor
    PromptPresetCategory.Memory -> AccentEmeraldColor
    PromptPresetCategory.Agent -> AccentIndigoColor
    PromptPresetCategory.Format -> AccentSkyColor
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
            onValueChange = { value -> PromptPresetUiIntent.ChangeCopyName(value).emit() },
            enabled = !busy,
            badgeIcon = Icons.Rounded.ContentCopy,
            confirmText = stringResource(R.string.create),
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
            onChange = { value -> PromptPresetUiIntent.ChangePromptDraft(value).emit() },
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
    val field = promptPresetField(dialogState.type)
    val errorMessage =
        if (dialogState.hasInvalidRequiredMacro && dialogState.requiredMacro != null) {
            stringResource(
                R.string.prompt_raw_summary_format_requires_macro,
                dialogState.requiredMacro
            )
        } else {
            null
        }

    // 字段只提供标题、只读态与必填校验，实时字数、全屏切换和撤销由共享编辑器承载。
    AppPromptEditorDialog(
        onDismissRequest = onDismiss,
        title = stringResource(field.titleRes),
        badgeIcon = field.section.category.icon(),
        badgeTone = DialogBadgeTone.Primary,
        value = dialogState.draftText,
        onValueChange = onChange,
        defaultValue = dialogState.defaultText,
        availableMacros = dialogState.availableMacros,
        requiredMacro = dialogState.requiredMacro,
        errorMessage = errorMessage,
        readOnly = dialogState.readOnly,
        onCopyRequest = { onCopy() },
        onConfirm = onSave,
        confirmEnabled = !busy && dialogState.hasChanges && !dialogState.hasInvalidRequiredMacro,
        isConfirmLoading = busy,
        confirmText = stringResource(R.string.save),
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
            totalCount = promptPresetFields.count { it.section.category == category },
            sections = promptPresetFields.filter { field -> field.section.category == category }
                .groupBy { field -> field.section }
                .map { (section, fields) ->
                    PromptPresetSectionState(
                        section = section,
                        items = fields.mapIndexed { index, field ->
                            PromptPresetFieldState(
                                field = field,
                                isCustomized = category == PromptPresetCategory.Basic && index == 0,
                                isEmpty = field.type.defaultValue.isBlank(),
                                previewText = field.type.defaultValue.lineSequence().firstOrNull()
                                    .orEmpty(),
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
                name = "长篇叙事预设",
                browserState = PromptPresetBrowserState(
                    groups = groups,
                    scopedTotalCount = promptPresetFields.size,
                    scopedCustomCount = 1,
                ),
            ),
        )
    }
}
