package me.kafuuneko.rpclient.feature.toolsettings.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.agent.model.AgentToolGroup
import me.kafuuneko.rpclient.feature.toolsettings.presentation.BuiltinToolListItem
import me.kafuuneko.rpclient.feature.toolsettings.presentation.HttpToolListItem
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolConnectionListItem
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsContentState
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsTab
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsUiIntent
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsUiState
import me.kafuuneko.rpclient.ui.theme.AccentAmberColor
import me.kafuuneko.rpclient.ui.theme.AccentEmeraldColor
import me.kafuuneko.rpclient.ui.theme.AccentIndigoColor
import me.kafuuneko.rpclient.ui.theme.AccentSkyColor
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppCard
import me.kafuuneko.rpclient.ui.widgets.AppPageScaffold
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.PageButton as Button
import me.kafuuneko.rpclient.ui.widgets.PageSwitch as Switch
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpMetaPill
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpSegmentedTabs
import me.kafuuneko.rpclient.ui.widgets.RpSettingsDivider
import me.kafuuneko.rpclient.ui.widgets.RpSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpTagPill
import me.kafuuneko.rpclient.ui.widgets.pageCanvasColor

/**
 * Agent 工具配置页主入口，遵循项目 Material 3 卡片、动态配色与壁纸透明画布规范。
 *
 * @param uiState 工具管理页状态树。
 * @param emit 将用户交互意图发送至 ViewModel。
 */
@Composable
fun ToolSettingsLayout(
    uiState: ToolSettingsUiState,
    emit: ToolSettingsUiIntent.() -> Unit = {},
) {
    BackHandler(enabled = uiState is ToolSettingsUiState.Normal) {
        ToolSettingsUiIntent.Back.emit()
    }
    // 结束过渡态沿用上一帧渲染并屏蔽交互，防止销毁 Activity 前出现闪白。
    when (uiState) {
        ToolSettingsUiState.None -> Unit
        is ToolSettingsUiState.Finished -> ToolSettingsLayout(
            uiState = uiState.previous,
            emit = {},
        )

        is ToolSettingsUiState.Normal -> {
            ToolSettingsNormalView(state = uiState, emit = emit)
            ToolSettingsDialogHost(state = uiState, emit = emit)
        }
    }
}

@Composable
private fun ToolSettingsNormalView(
    state: ToolSettingsUiState.Normal,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    val tab = state.content.tab
    val actionsEnabled = !state.busy && !state.choosingDocument
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 顶栏集中承载导入、导出与当前分区的新建入口，进度条紧贴顶栏下方避免抖动列表。
    AppPageScaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(left = 0.dp),
        topBar = {
            Column {
                AppTopBar(
                    title = stringResource(R.string.tool_settings_title),
                    onBack = { ToolSettingsUiIntent.Back.emit() },
                    actions = {
                        IconButton(
                            onClick = { ToolSettingsUiIntent.ImportClick.emit() },
                            enabled = actionsEnabled,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FileDownload,
                                contentDescription = stringResource(R.string.tool_settings_import),
                            )
                        }
                        IconButton(
                            onClick = { ToolSettingsUiIntent.ExportClick.emit() },
                            enabled = actionsEnabled,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FileUpload,
                                contentDescription = stringResource(R.string.tool_settings_export),
                            )
                        }
                        if (tab != ToolSettingsTab.Builtins) {
                            IconButton(
                                onClick = {
                                    if (tab == ToolSettingsTab.Connections) {
                                        ToolSettingsUiIntent.CreateConnection.emit()
                                    } else {
                                        ToolSettingsUiIntent.CreateTool.emit()
                                    }
                                },
                                enabled = !state.busy,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = stringResource(R.string.add),
                                )
                            }
                        }
                    },
                )
                if (state.busy) {
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
            // 顶部固定分区切换与语境说明条，切换标签后列表滚动互不干扰。
            ToolSettingsHeader(
                tab = tab,
                enabled = !state.busy,
                emit = emit,
            )
            val groupedBuiltins = groupBuiltins(state.content.builtins)
            RpLazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    top = 6.dp,
                    end = 18.dp,
                    bottom = bottomInset + 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (tab) {
                    ToolSettingsTab.Tools -> {
                        item(key = "quick-templates") {
                            QuickTemplateBar(enabled = !state.busy, emit = emit)
                        }
                        if (state.content.tools.isEmpty()) {
                            item(key = "empty-tools") {
                                EmptyToolsCard(enabled = !state.busy, emit = emit)
                            }
                        } else {
                            items(
                                items = state.content.tools,
                                key = { item -> "tool-${item.id}" },
                            ) { item ->
                                HttpToolCard(
                                    item = item,
                                    busy = state.busy,
                                    emit = emit,
                                )
                            }
                        }
                    }

                    ToolSettingsTab.Connections -> {
                        if (state.content.connections.isEmpty()) {
                            item(key = "empty-connections") {
                                EmptyConnectionsCard(
                                    enabled = !state.busy,
                                    onCreate = { ToolSettingsUiIntent.CreateConnection.emit() },
                                )
                            }
                        } else {
                            items(
                                items = state.content.connections,
                                key = { item -> "conn-${item.id}" },
                            ) { item ->
                                ConnectionCard(
                                    item = item,
                                    busy = state.busy,
                                    emit = emit,
                                )
                            }
                        }
                    }

                    ToolSettingsTab.Builtins -> {
                        groupedBuiltins.forEach { (group, groupItems) ->
                            item(key = "builtin-group-${group.name}") {
                                BuiltinToolGroupCard(
                                    group = group,
                                    items = groupItems,
                                    busy = state.busy,
                                    emit = emit,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolSettingsHeader(
    tab: ToolSettingsTab,
    enabled: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    val tipRes = when (tab) {
        ToolSettingsTab.Tools -> R.string.tool_settings_tools_tip
        ToolSettingsTab.Connections -> R.string.tool_settings_connections_tip
        ToolSettingsTab.Builtins -> R.string.tool_settings_builtins_tip
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RpSegmentedTabs(
            tabs = ToolSettingsTab.entries,
            selectedTab = tab,
            onTabSelected = { selected ->
                if (enabled) ToolSettingsUiIntent.SelectTab(selected).emit()
            },
            tabLabel = { item -> stringResource(item.titleRes()) },
        )
        AppSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
            border = BorderStroke(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(16.dp),
                )
                Text(
                    text = stringResource(tipRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickTemplateBar(
    enabled: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    // 使用高辨识度带图标胶囊呈现常用模板与自定义新建入口，与列表内容区分层次。
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RpSectionHeader(title = stringResource(R.string.tool_settings_quick_templates))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TemplateActionPill(
                icon = Icons.Rounded.Search,
                label = stringResource(R.string.tool_settings_add_search),
                accent = AccentIndigoColor,
                enabled = enabled,
                onClick = { ToolSettingsUiIntent.CreateTemplate(readUrl = false).emit() },
            )
            TemplateActionPill(
                icon = Icons.Rounded.Language,
                label = stringResource(R.string.tool_settings_add_reader),
                accent = AccentSkyColor,
                enabled = enabled,
                onClick = { ToolSettingsUiIntent.CreateTemplate(readUrl = true).emit() },
            )
            TemplateActionPill(
                icon = Icons.Rounded.Add,
                label = stringResource(R.string.tool_settings_add_custom),
                accent = AccentAmberColor,
                enabled = enabled,
                onClick = { ToolSettingsUiIntent.CreateTool.emit() },
            )
        }
    }
}

@Composable
private fun TemplateActionPill(
    icon: ImageVector,
    label: String,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    AppSurface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(width = 0.6.dp, color = accent.copy(alpha = 0.30f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun EmptyToolsCard(
    enabled: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
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
                icon = Icons.Rounded.Public,
                size = 50.dp,
                contentColor = AccentSkyColor,
                containerColor = AccentSkyColor.copy(alpha = 0.14f),
            )
            Text(
                text = stringResource(R.string.tool_settings_empty_tools_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.tool_settings_empty_tools_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = { ToolSettingsUiIntent.CreateTemplate(readUrl = true).emit() },
                enabled = enabled,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.tool_settings_add_reader))
            }
        }
    }
}

@Composable
private fun EmptyConnectionsCard(
    enabled: Boolean,
    onCreate: () -> Unit,
) {
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
                icon = Icons.Rounded.Dns,
                size = 50.dp,
                contentColor = AccentIndigoColor,
                containerColor = AccentIndigoColor.copy(alpha = 0.14f),
            )
            Text(
                text = stringResource(R.string.tool_settings_empty_connections_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.tool_settings_empty_connections_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onCreate,
                enabled = enabled,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.tool_settings_add_connection))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HttpToolCard(
    item: HttpToolListItem,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    val isReader = item.kind == "ReadUrl"
    val accent = if (isReader) AccentSkyColor else AccentIndigoColor
    val activeAccent = if (item.enabled) accent else accent.copy(alpha = 0.45f)
    // 沿用模型配置卡片圆角、边框与禁用态底色层次，点击整卡直接进入编辑。
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = !busy) { ToolSettingsUiIntent.EditTool(item.id).emit() },
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = if (item.enabled) 1.dp else 0.8.dp,
            color = when {
                !item.enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                item.needsConfiguration -> MaterialTheme.colorScheme.error.copy(alpha = 0.40f)
                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            },
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (item.enabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RpIconBubble(
                    icon = if (isReader) Icons.Rounded.Language else Icons.Rounded.Public,
                    size = 40.dp,
                    contentColor = activeAccent,
                    containerColor = activeAccent.copy(alpha = 0.14f),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.description.isNotBlank()) {
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Switch(
                    checked = item.enabled,
                    enabled = !busy,
                    onCheckedChange = { checked ->
                        ToolSettingsUiIntent.ToggleToolEnabled(item.id, checked).emit()
                    },
                )
            }

            // 集中展示类型、绑定连接方法与待配置警告，便于一眼判断可用状态。
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RpMetaPill(
                    text = stringResource(
                        if (isReader) R.string.tool_settings_kind_read_url else R.string.tool_settings_kind_http,
                    ),
                )
                if (!isReader) {
                    val connLabel = item.connectionName?.let { name ->
                        val method = item.connectionMethod.orEmpty()
                        if (method.isNotBlank()) "$method · $name" else name
                    } ?: stringResource(R.string.tool_settings_unbound)
                    if (item.connectionName != null) {
                        RpTagPill(text = connLabel)
                    } else {
                        StatusPill(
                            text = connLabel,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                when {
                    !item.enabled -> RpMetaPill(text = stringResource(R.string.disabled))
                    item.needsConfiguration -> StatusPill(
                        text = stringResource(R.string.tool_settings_needs_configuration),
                        color = MaterialTheme.colorScheme.error,
                    )

                    else -> StatusPill(
                        text = stringResource(R.string.enabled),
                        color = AccentEmeraldColor,
                    )
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
            )

            // 底部左侧放编辑与测试胶囊按钮，右侧放复制和删除图标按钮。
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CardActionPill(
                    icon = Icons.Rounded.Edit,
                    label = stringResource(R.string.edit),
                    enabled = !busy,
                    emphasized = true,
                    onClick = { ToolSettingsUiIntent.EditTool(item.id).emit() },
                )
                CardActionPill(
                    icon = Icons.Rounded.PlayArrow,
                    label = stringResource(R.string.test),
                    enabled = !busy && item.enabled && !item.needsConfiguration,
                    emphasized = false,
                    onClick = { ToolSettingsUiIntent.OpenTestDialog(item.id).emit() },
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { ToolSettingsUiIntent.CopyTool(item.id).emit() },
                    enabled = !busy,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.copy),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        modifier = Modifier.size(19.dp),
                    )
                }
                IconButton(
                    onClick = { ToolSettingsUiIntent.ShowDeleteTool(item.id).emit() },
                    enabled = !busy,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionCard(
    item: ToolConnectionListItem,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    val activeAccent = if (item.enabled) AccentIndigoColor else AccentIndigoColor.copy(alpha = 0.45f)
    val displayUrl = item.url
        .removePrefix("https://")
        .removePrefix("http://")
        .ifBlank { stringResource(R.string.tool_settings_no_url) }
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = !busy) { ToolSettingsUiIntent.EditConnection(item.id).emit() },
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = if (item.enabled) 1.dp else 0.8.dp,
            color = when {
                !item.enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                item.needsConfiguration -> MaterialTheme.colorScheme.error.copy(alpha = 0.40f)
                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            },
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (item.enabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RpIconBubble(
                    icon = Icons.Rounded.Dns,
                    size = 40.dp,
                    contentColor = activeAccent,
                    containerColor = activeAccent.copy(alpha = 0.14f),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = displayUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.url.isBlank()) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(
                    checked = item.enabled,
                    enabled = !busy,
                    onCheckedChange = { checked ->
                        ToolSettingsUiIntent.ToggleConnectionEnabled(item.id, checked).emit()
                    },
                )
            }

            // 清晰展示 HTTP 方法、认证凭据状态、局域网权限与已关联工具数。
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RpTagPill(text = item.method)
                val authLabel = authModeLabel(item.authMode)
                if (item.authMode == "None") {
                    RpMetaPill(text = authLabel)
                } else if (item.hasCredential) {
                    StatusPill(
                        text = "$authLabel · ${stringResource(R.string.tool_settings_credential_bound)}",
                        color = AccentEmeraldColor,
                    )
                } else {
                    StatusPill(
                        text = "$authLabel · ${stringResource(R.string.tool_settings_credential_missing)}",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (item.allowPrivateAddresses) {
                    StatusPill(
                        text = stringResource(R.string.tool_settings_allow_private),
                        color = AccentAmberColor,
                    )
                }
                if (item.boundToolCount > 0) {
                    RpMetaPill(
                        text = stringResource(
                            R.string.tool_settings_bound_tools_count,
                            item.boundToolCount,
                        ),
                    )
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CardActionPill(
                    icon = Icons.Rounded.Edit,
                    label = stringResource(R.string.edit),
                    enabled = !busy,
                    emphasized = true,
                    onClick = { ToolSettingsUiIntent.EditConnection(item.id).emit() },
                )
                CardActionPill(
                    icon = Icons.Rounded.Key,
                    label = stringResource(
                        if (item.hasCredential) {
                            R.string.tool_settings_credential_manage
                        } else {
                            R.string.tool_settings_credential
                        },
                    ),
                    enabled = !busy,
                    emphasized = false,
                    onClick = { ToolSettingsUiIntent.ShowCredentialDialog(item.id).emit() },
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { ToolSettingsUiIntent.CopyConnection(item.id).emit() },
                    enabled = !busy,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.copy),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        modifier = Modifier.size(19.dp),
                    )
                }
                IconButton(
                    onClick = { ToolSettingsUiIntent.ShowDeleteConnection(item.id).emit() },
                    enabled = !busy,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BuiltinToolGroupCard(
    group: AgentToolGroup,
    items: List<BuiltinToolListItem>,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(group.titleRes()),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
        RpSettingsGroup {
            items.forEachIndexed { index, item ->
                if (index > 0) RpSettingsDivider(startIndent = false)
                BuiltinToolRow(
                    item = item,
                    busy = busy,
                    emit = emit,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuiltinToolRow(
    item: BuiltinToolListItem,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    // 沿用提示词预设列表行设计：左侧主色强调条标示已自定义条目，点击进入结构化编辑器。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy) { ToolSettingsUiIntent.EditBuiltin(item.id).emit() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.customized) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(36.dp)
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
                    text = item.customDisplayName ?: stringResource(item.defaultLabelRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (item.customized) FontWeight.Bold else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (item.customized) {
                    RpTagPill(text = stringResource(R.string.tool_settings_customized_badge))
                }
            }
            Text(
                text = item.id,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.descriptionPreview.isNotBlank()) {
                Text(
                    text = item.descriptionPreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (item.customized) {
            IconButton(
                onClick = { ToolSettingsUiIntent.ResetBuiltinOverride(item.id).emit() },
                enabled = !busy,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = stringResource(R.string.reset),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun CardActionPill(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
        emphasized -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
        emphasized -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    AppSurface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = BorderStroke(
            width = 0.5.dp,
            color = if (emphasized && enabled) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
internal fun StatusPill(
    text: String,
    color: Color,
) {
    AppSurface(
        shape = RoundedCornerShape(9.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(width = 0.5.dp, color = color.copy(alpha = 0.28f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun authModeLabel(authMode: String): String = when (authMode) {
    "Bearer" -> stringResource(R.string.tool_settings_auth_bearer)
    "ApiKey" -> stringResource(R.string.tool_settings_auth_api_key)
    "Header" -> stringResource(R.string.tool_settings_auth_header)
    "Query" -> stringResource(R.string.tool_settings_auth_query)
    else -> stringResource(R.string.tool_settings_auth_none)
}

@Composable
private fun groupBuiltins(
    items: List<BuiltinToolListItem>,
): List<Pair<AgentToolGroup, List<BuiltinToolListItem>>> = remember(items) {
    AgentToolGroup.entries.mapNotNull { group ->
        val matched = items.filter { it.group == group }
        if (matched.isEmpty()) null else group to matched
    }
}

private fun ToolSettingsTab.titleRes(): Int = when (this) {
    ToolSettingsTab.Tools -> R.string.tool_settings_tools
    ToolSettingsTab.Connections -> R.string.tool_settings_connections
    ToolSettingsTab.Builtins -> R.string.tool_settings_builtins
}

private fun AgentToolGroup.titleRes(): Int = when (this) {
    AgentToolGroup.Lore -> R.string.agent_tool_group_lore
    AgentToolGroup.Chat -> R.string.agent_tool_group_chat
    AgentToolGroup.Memory -> R.string.agent_tool_group_memory
    AgentToolGroup.Scene -> R.string.agent_tool_group_scene
    AgentToolGroup.Utility -> R.string.agent_tool_group_utility
    AgentToolGroup.Interaction -> R.string.agent_tool_group_interaction
    AgentToolGroup.Network -> R.string.tool_settings_tools
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ToolSettingsPreview() {
    AppTheme(dynamicColor = false) {
        ToolSettingsLayout(
            uiState = ToolSettingsUiState.Normal(
                content = ToolSettingsContentState(
                    tab = ToolSettingsTab.Tools,
                    tools = listOf(
                        HttpToolListItem(
                            id = "1",
                            name = "web_search",
                            description = "Search the web for untrusted reference material.",
                            kind = "Http",
                            enabled = true,
                            connectionName = "Search API",
                            connectionMethod = "GET",
                            needsConfiguration = false,
                        ),
                        HttpToolListItem(
                            id = "2",
                            name = "read_url",
                            description = "Read public web text as untrusted reference material.",
                            kind = "ReadUrl",
                            enabled = true,
                            needsConfiguration = false,
                        ),
                    ),
                ),
            ),
        )
    }
}
