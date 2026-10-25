package me.kafuuneko.rpclient.feature.jsonviewer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.jsonviewer.model.JsonViewerEntry
import me.kafuuneko.rpclient.feature.jsonviewer.model.JsonViewerNodeType
import me.kafuuneko.rpclient.feature.jsonviewer.presentation.JsonViewerErrorReason
import me.kafuuneko.rpclient.feature.jsonviewer.presentation.JsonViewerUiIntent
import me.kafuuneko.rpclient.feature.jsonviewer.presentation.JsonViewerUiState
import me.kafuuneko.rpclient.feature.jsonviewer.presentation.JsonViewerTab
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppPageScaffold
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpInfoCard
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpSegmentedTabs
import me.kafuuneko.rpclient.ui.widgets.pageCanvasColor

/** 分层 JSON 查看器 Compose 入口。 */
@Composable
fun JsonViewerLayout(
    uiState: JsonViewerUiState,
    emit: JsonViewerUiIntent.() -> Unit = {},
) {
    BackHandler(
        enabled = uiState is JsonViewerUiState.Loading
            || uiState is JsonViewerUiState.Normal
            || uiState is JsonViewerUiState.Error,
        onBack = {
            JsonViewerUiIntent.Back.emit()
        },
    )
    when (uiState) {
        JsonViewerUiState.None -> Unit
        is JsonViewerUiState.Loading -> LoadingView(uiState = uiState, emit = emit)
        is JsonViewerUiState.Finished -> JsonViewerLayout(uiState = uiState.previous, emit = {})
        is JsonViewerUiState.Normal -> NormalView(uiState = uiState, emit = emit)
        is JsonViewerUiState.Error -> ErrorView(uiState = uiState, emit = emit)
    }
}

@Composable
private fun LoadingView(
    uiState: JsonViewerUiState.Loading,
    emit: JsonViewerUiIntent.() -> Unit,
) {
    AppPageScaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(left = 0.dp),
        topBar = {
            AppTopBar(
                title = uiState.title.ifBlank { stringResource(R.string.json_viewer) },
                onBack = { JsonViewerUiIntent.Back.emit() },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(pageCanvasColor()),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun NormalView(
    uiState: JsonViewerUiState.Normal,
    emit: JsonViewerUiIntent.() -> Unit,
) {
    val treeScrollState = rememberLazyListState()
    val sourceScrollState = rememberLazyListState()
    LaunchedEffect(uiState.path) { treeScrollState.scrollToItem(0) }
    AppPageScaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(left = 0.dp),
        topBar = {
            AppTopBar(
                title = uiState.title.ifBlank { stringResource(R.string.json_viewer) },
                onBack = { JsonViewerUiIntent.Back.emit() },
            )
        },
    ) { paddingValues ->
        // 滚动状态保留在 Tab 外，切换展示不会丢失当前位置。
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(pageCanvasColor())
        ) {
            JsonViewTabs(selectedTab = uiState.selectedTab, emit = emit)
            if (uiState.selectedTab == JsonViewerTab.Tree) {
                TreeView(uiState = uiState, scrollState = treeScrollState, emit = emit)
            } else {
                JsonSourceView(state = uiState.sourceState, scrollState = sourceScrollState)
            }
        }
    }
}

/** 可视化与源码共用统一的圆角分段 Tab 样式。 */
@Composable
private fun JsonViewTabs(
    selectedTab: JsonViewerTab,
    emit: JsonViewerUiIntent.() -> Unit,
) {
    // 只替换切换栏的展示，源码加载与两种视图的滚动状态仍由原有入口维护。
    RpSegmentedTabs(
        tabs = JsonViewerTab.entries,
        selectedTab = selectedTab,
        onTabSelected = { tab -> JsonViewerUiIntent.SelectTab(tab).emit() },
        tabLabel = { tab ->
            stringResource(
                when (tab) {
                    JsonViewerTab.Tree -> R.string.json_tab_tree
                    JsonViewerTab.Source -> R.string.json_tab_source
                },
            )
        },
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

/** 当前层的条目和文本块共享外层懒列表，长字符串不会变成一个巨大的布局项目。 */
@Composable
private fun TreeView(
    uiState: JsonViewerUiState.Normal,
    scrollState: LazyListState,
    emit: JsonViewerUiIntent.() -> Unit,
) {
    RpLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        state = scrollState,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    ) {
        // 路径与当前层摘要共用一行，将首屏空间留给实际字段。
        item {
            JsonLocationBar(
                path = uiState.path,
                currentType = uiState.currentType,
                childCount = uiState.childCount,
            )
            Spacer(Modifier.height(12.dp))
        }
        if (uiState.treeError != null) {
            item {
                RpInfoCard(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.DataObject,
                    title = stringResource(R.string.json_viewer),
                    subtitle = stringResource(if (uiState.treeError == JsonViewerErrorReason.TooLarge) R.string.json_preview_too_large else R.string.json_invalid_desc),
                )
            }
            return@RpLazyColumn
        }
        if (uiState.entries.isEmpty()) {
            item {
                RpInfoCard(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.DataObject,
                    title = stringResource(R.string.json_empty_node),
                    subtitle = stringResource(R.string.json_empty_node_desc),
                )
            }
        }
        uiState.entries.forEach { entry -> jsonEntryItems(entry, emit) }
        if (uiState.canLoadMore) {
            item(key = "more") {
                TextButton(onClick = { JsonViewerUiIntent.LoadMore.emit() }) {
                    Text(stringResource(R.string.content_search_more))
                }
            }
        }
    }
}

/** 文本仍按有界项目懒加载，但相邻块拼成同一字段表面，不暴露内部渲染分块。 */
private fun LazyListScope.jsonEntryItems(
    entry: JsonViewerEntry,
    emit: JsonViewerUiIntent.() -> Unit
) {
    item(key = "entry-${entry.id}") {
        if (entry.hasChildren) {
            JsonContainerRow(
                entry = entry,
                onClick = { JsonViewerUiIntent.EntrySelected(entry.id).emit() })
        } else {
            JsonScalarStart(entry = entry)
        }
    }
    // 首块与字段标题放在一起，后续块无间距衔接，仅末块保留底部圆角。
    if (!entry.hasChildren) {
        items(
            count = (entry.valueBlocks.size - 1).coerceAtLeast(0),
            key = { index -> "value-${entry.id}-${index + 1}" }) { index ->
            val isLast = index + 1 == entry.valueBlocks.lastIndex
            JsonScalarContinuation(
                text = entry.valueBlocks[index + 1],
                isLast = isLast,
                truncatedLength = if (isLast && entry.isTruncated) entry.originalLength else null,
            )
        }
    }
    item(key = "spacing-${entry.id}") { Spacer(Modifier.height(12.dp)) }
}

/** 短字段只使用一张卡片，长字段的首块与标题保持连接。 */
@Composable
private fun JsonScalarStart(entry: JsonViewerEntry) {
    val isLast = entry.valueBlocks.size <= 1
    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = if (isLast) RoundedCornerShape(16.dp) else RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp
        ),
    ) {
        // 与后续惰性块共用背景和水平内边距，滚动时不出现独立正文卡片。
        Column(
            Modifier.padding(
                start = 14.dp,
                top = 12.dp,
                end = 14.dp,
                bottom = if (isLast) 14.dp else 0.dp
            )
        ) {
            JsonFieldHeading(entry = entry)
            Spacer(Modifier.height(8.dp))
            JsonValueText(text = entry.valueBlocks.firstOrNull().orEmpty())
            if (isLast && entry.isTruncated) JsonTruncationNote(length = entry.originalLength)
        }
    }
}

/** 中间块不使用圆角或额外留白，完整字段的截断提示固定在末尾。 */
@Composable
private fun JsonScalarContinuation(text: String, isLast: Boolean, truncatedLength: Int?) {
    val shape: Shape =
        if (isLast) RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp) else RectangleShape
    AppSurface(modifier = Modifier.fillMaxWidth(), shape = shape) {
        // 单块文本大小受映射阶段限制，不能在这里合并成长段 Text。
        Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = if (isLast) 14.dp else 0.dp)) {
            JsonValueText(text = text)
            if (truncatedLength != null) JsonTruncationNote(length = truncatedLength)
        }
    }
}

@Composable
private fun JsonValueText(text: String) {
    SelectionContainer {
        Text(
            text = text.ifEmpty { "\"\"" },
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun JsonTruncationNote(length: Int) {
    Text(
        text = stringResource(R.string.json_value_truncated, length),
        modifier = Modifier.padding(top = 10.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ErrorView(
    uiState: JsonViewerUiState.Error,
    emit: JsonViewerUiIntent.() -> Unit,
) {
    AppPageScaffold(
        modifier = Modifier
            .fillMaxSize(),
        contentWindowInsets = WindowInsets(left = 0.dp),
        topBar = {
            AppTopBar(
                title = uiState.title.ifBlank { stringResource(R.string.json_viewer) },
                onBack = { JsonViewerUiIntent.Back.emit() },
            )
        },
        content = { paddingValues ->
            RpLazyColumn(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .background(pageCanvasColor()),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    top = 18.dp,
                    end = 18.dp,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    val errorMessageResId = when (uiState.reason) {
                        JsonViewerErrorReason.PayloadUnavailable -> R.string.json_payload_unavailable
                        JsonViewerErrorReason.InvalidJson -> R.string.json_invalid_desc
                        JsonViewerErrorReason.TooLarge -> R.string.json_preview_too_large
                    }
                    RpInfoCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.DataObject,
                        title = stringResource(R.string.json_invalid_title),
                        subtitle = stringResource(id = errorMessageResId),
                    )
                }
                if (uiState.rawPreview.isNotBlank()) {
                    item {
                        JsonRawPreview(rawPreview = uiState.rawPreview)
                    }
                }
            }
        },
    )
}

@Composable
private fun JsonLocationBar(path: List<String>, currentType: JsonViewerNodeType, childCount: Int) {
    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        // 路径可独立横向滚动，长路径不会挤掉当前层的数量。
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(path) { index, item ->
                    Text(
                        text = item,
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (index == path.lastIndex) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                    if (index < path.lastIndex) Text(
                        "/",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            Text(text = currentType.labelText(), style = MaterialTheme.typography.labelSmall)
            Text(text = "·", style = MaterialTheme.typography.labelSmall)
            Text(
                text = currentType.childCountText(childCount),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun JsonContainerRow(
    entry: JsonViewerEntry,
    onClick: () -> Unit,
) {
    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
    ) {
        // 导航条目的类型与数量收在副标题，字段名保持主要视觉层级。
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = entry.type.labelText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = entry.type.childCountText(entry.childCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun JsonRawPreview(rawPreview: String) {
    AppSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        SelectionContainer {
            Text(
                text = rawPreview,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}

@Composable
private fun JsonFieldHeading(entry: JsonViewerEntry) {
    // 类型降为辅助标签，避免每个字符串前出现占据整行的大色块。
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = entry.type.labelText(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun JsonViewerNodeType.labelText(): String {
    return when (this) {
        JsonViewerNodeType.Object -> stringResource(R.string.json_type_object)
        JsonViewerNodeType.Array -> stringResource(R.string.json_type_array)
        JsonViewerNodeType.String -> stringResource(R.string.json_type_string)
        JsonViewerNodeType.Number -> stringResource(R.string.json_type_number)
        JsonViewerNodeType.Boolean -> stringResource(R.string.json_type_boolean)
        JsonViewerNodeType.Null -> stringResource(R.string.json_type_null)
    }
}

@Composable
private fun JsonViewerNodeType.childCountText(count: Int): String {
    return when (this) {
        JsonViewerNodeType.Object -> stringResource(R.string.json_object_children, count)
        JsonViewerNodeType.Array -> stringResource(R.string.json_array_children, count)
        else -> stringResource(R.string.json_scalar_value)
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun JsonViewerLayoutPreview() {
    AppTheme(dynamicColor = false) {
        JsonViewerLayout(
            uiState = JsonViewerUiState.Normal(
                title = "Request JSON",
                path = listOf("Root", "messages", "[0]"),
                currentType = JsonViewerNodeType.Object,
                childCount = 3,
                entries = listOf(
                    JsonViewerEntry(
                        id = 0,
                        name = "role",
                        type = JsonViewerNodeType.String,
                        preview = "\"user\"",
                        childCount = 0,
                    ),
                    JsonViewerEntry(
                        id = 1,
                        name = "content",
                        type = JsonViewerNodeType.Array,
                        preview = "[]",
                        childCount = 2,
                        sourceKey = "content",
                    ),
                ),
                canNavigateUp = true,
            ),
        )
    }
}
