@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package me.kafuuneko.rpclient.feature.toolsettings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.toolsettings.presentation.BuiltinToolListItem
import me.kafuuneko.rpclient.feature.toolsettings.presentation.HttpToolListItem
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsContentState
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsTab
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsUiIntent
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsUiState
import me.kafuuneko.rpclient.libs.agent.connection.HttpToolKind
import me.kafuuneko.rpclient.libs.agent.connection.ToolHttpMethod
import me.kafuuneko.rpclient.libs.agent.tool.catalog.ToolGroup
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppPageScaffold
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
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
            DialogSwitch(state = uiState, emit = emit)
        }
    }
}


@Composable
private fun ToolSettingsNormalView(
    state: ToolSettingsUiState.Normal,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    val tab = state.content.tab
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 顶栏集中承载导入、导出与当前分区的新建入口，进度条紧贴顶栏下方避免抖动列表。
    AppPageScaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(left = 0.dp),
        topBar = {
            ToolSettingsTopBar(state = state, emit = emit)
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
                toolSettingsItems(state, groupedBuiltins, emit)
            }
        }
    }
}


/** 返回工具设置分区对应的本地化标签标题。 */
internal fun ToolSettingsTab.titleRes(): Int = when (this) {
    ToolSettingsTab.Tools -> R.string.tool_settings_tools
    ToolSettingsTab.Connections -> R.string.tool_settings_connections
    ToolSettingsTab.Builtins -> R.string.tool_settings_builtins
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ToolSettingsPreview() {
    // 静态预览样例仅用于排版核验，不依赖真实数据库或 Koin 容器。
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
                            kind = HttpToolKind.Http,
                            enabled = true,
                            connectionName = "Search API",
                            connectionMethod = ToolHttpMethod.Get,
                            needsConfiguration = false,
                        ),
                        HttpToolListItem(
                            id = "2",
                            name = "read_url",
                            description = "Read public web text as untrusted reference material.",
                            kind = HttpToolKind.ReadUrl,
                            enabled = true,
                            needsConfiguration = false,
                        ),
                    ),
                ),
            ),
        )
    }
}

/** 顶栏操作随忙碌状态禁用，文档选择仍由 ViewEvent 交给宿主。 */
@Composable
private fun ToolSettingsTopBar(
    state: ToolSettingsUiState.Normal,
    emit: ToolSettingsUiIntent.() -> Unit
) {
    // 导入导出在文件选择器打开或后台忙碌时禁用；内置工具分区不提供新增按钮。
    val tab = state.content.tab
    val actionsEnabled = !state.busy && !state.choosingDocument
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
}

/** 保留稳定条目键，各标签只构建自身的惰性列表项。 */
private fun LazyListScope.toolSettingsItems(
    state: ToolSettingsUiState.Normal,
    groupedBuiltins: List<Pair<ToolGroup, List<BuiltinToolListItem>>>,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    // 按当前分区输出对应惰性列表项与空态占位，使用前缀隔离跨分区项键。
    val tab = state.content.tab
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
