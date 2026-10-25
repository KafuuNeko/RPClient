package me.kafuuneko.rpclient.feature.contentsearch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.contentsearch.presentation.ContentSearchResultState
import me.kafuuneko.rpclient.feature.contentsearch.presentation.ContentSearchScopeState
import me.kafuuneko.rpclient.feature.contentsearch.presentation.ContentSearchUiIntent
import me.kafuuneko.rpclient.feature.contentsearch.presentation.ContentSearchUiState
import me.kafuuneko.rpclient.feature.contentsearch.presentation.items
import me.kafuuneko.rpclient.feature.contentsearch.presentation.nextCursor
import me.kafuuneko.rpclient.libs.appearance.PageSurfaceKind
import me.kafuuneko.rpclient.libs.search.SearchHit
import me.kafuuneko.rpclient.libs.search.SearchIndexState
import me.kafuuneko.rpclient.libs.search.SearchKind
import me.kafuuneko.rpclient.libs.search.SearchScope
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppPageScaffold
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.generalContentBackground
import me.kafuuneko.rpclient.ui.widgets.PageFilterChip as FilterChip
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField

/** 搜索页只渲染状态；输入、停止、分页及导航都通过 Intent 交给 ViewModel。 */
@Composable
fun ContentSearchLayout(state: ContentSearchUiState, emit: ContentSearchUiIntent.() -> Unit) {
    BackHandler(onBack = { ContentSearchUiIntent.Back.emit() })
    when (state) {
        ContentSearchUiState.None -> Unit
        is ContentSearchUiState.Normal -> SearchPage(state, emit)

        is ContentSearchUiState.Finished -> ContentSearchLayout(state = state.previous, emit = {})
    }
}

/**
 * 搜索主页面。
 * - 顶部栏融合返回导航、输入框与提交/停止按钮。
 * - 筛选栏单行横向滚动，统一收纳对象限定、内容范围与分类标签。
 * - 搜索结果区域独立滚动，最大化保留可视垂直高度。
 */
@Composable
private fun SearchPage(state: ContentSearchUiState.Normal, emit: ContentSearchUiIntent.() -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var focused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!focused) {
            focus.requestFocus()
            focused = true
        }
    }
    val busy = state.resultState is ContentSearchResultState.Searching
    val submit = {
        keyboard?.hide()
        ContentSearchUiIntent.Submit.emit()
    }

    AppPageScaffold(
        topBar = {
            SearchTopBar(
                query = state.inputState.draft,
                busy = busy,
                canSubmit = state.inputState.draft.isNotBlank() && !state.indexState.busy,
                focus = focus,
                onSubmit = submit,
                emit = emit,
            )
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
            ) {
                ScopeControls(state.scopeState, emit)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                SearchResults(state = state, modifier = Modifier.weight(1f), emit = emit)
            }
        },
    )
}

/** 融合返回导航、输入框与提交/停止按钮的统一顶部搜索栏。 */
@Composable
private fun SearchTopBar(
    query: String,
    busy: Boolean,
    canSubmit: Boolean,
    focus: FocusRequester,
    onSubmit: () -> Unit,
    emit: ContentSearchUiIntent.() -> Unit,
) {
    // 顶部栏使用标准外观底板，统一管理状态栏与横向安全边距。
    AppSurface(
        kind = PageSurfaceKind.TopBar,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .windowInsetsPadding(insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 返回上级页面入口。
            IconButton(
                onClick = { ContentSearchUiIntent.Back.emit() },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = stringResource(R.string.back),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            // 弹性填充剩余宽度的单行搜索输入框。
            OutlinedTextField(
                value = query,
                onValueChange = { text -> ContentSearchUiIntent.ChangeQuery(text).emit() },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp)
                    .focusRequester(focus)
                    .generalContentBackground(RoundedCornerShape(16.dp)),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { ContentSearchUiIntent.ChangeQuery("").emit() }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.clear_search),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                placeholder = {
                    Text(
                        text = stringResource(R.string.content_search_placeholder),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            )
            Spacer(modifier = Modifier.width(4.dp))
            // 搜索中切换为醒目的停止操作，空闲时按输入状态启用搜索。
            if (busy) {
                TextButton(
                    onClick = { ContentSearchUiIntent.Stop.emit() },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.stop),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                TextButton(
                    onClick = onSubmit,
                    enabled = canSubmit,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.content_search_submit),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** 单行横向滚动收纳全部筛选维度：限定对象、内容范围下拉与分类芯片。 */
@Composable
private fun ScopeControls(
    scope: ContentSearchScopeState,
    emit: ContentSearchUiIntent.() -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 当前限定在特定对象内搜索时，首位显示高亮且可一键清除的标签。
        if (scope.parentId != null) {
            FilterChip(
                selected = true,
                onClick = { ContentSearchUiIntent.ClearObjectScope.emit() },
                label = {
                    Text(
                        text = scope.parentTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.content_search_clear_scope),
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
        // 内容范围下拉筛选（全部内容、仅元数据、仅正文）。
        Box {
            FilterChip(
                selected = scope.scope != SearchScope.All,
                onClick = { menuExpanded = true },
                label = { Text(stringResource(scopeLabel(scope.scope))) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                SearchScope.entries.forEach { range ->
                    DropdownMenuItem(
                        text = { Text(stringResource(scopeLabel(range))) },
                        onClick = {
                            menuExpanded = false
                            ContentSearchUiIntent.SelectScope(range).emit()
                        },
                    )
                }
            }
        }
        // 会话与故事类型分类筛选。
        SearchKind.entries.forEach { kind ->
            FilterChip(
                selected = scope.kind == kind,
                onClick = { ContentSearchUiIntent.SelectKind(kind).emit() },
                label = { Text(stringResource(kindLabel(kind.code))) },
            )
        }
    }
}

@Composable
private fun SearchResults(
    state: ContentSearchUiState.Normal,
    modifier: Modifier,
    emit: ContentSearchUiIntent.() -> Unit,
) {
    val result = state.resultState
    // 空态、准备状态、错误及部分结果各自有明确含义，不把未完成显示为无匹配。
    RpLazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (state.indexState.busy) {
            item(key = "index-maintenance") {
                // 索引操作统一在设置页处理，搜索页仅展示暂不可查询的原因。
                val message = when (val index = state.indexState) {
                    is SearchIndexState.Building -> stringResource(
                        R.string.content_search_index_building,
                        index.processed,
                    )

                    SearchIndexState.Cleaning -> stringResource(R.string.content_search_index_cleaning)
                    SearchIndexState.CleanupFailed -> stringResource(R.string.content_search_index_cleanup_failed)
                    else -> stringResource(R.string.loading)
                }
                Text(text = message, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { ContentSearchUiIntent.OpenSearchSettings.emit() }) {
                    Text(stringResource(R.string.settings))
                }
            }
        }
        if (state.indexState == SearchIndexState.Updating && state.scopeState.scope != SearchScope.Metadata) {
            item(key = "index-state") {
                Text(
                    text = stringResource(R.string.content_search_preparing),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item(key = "query-status") {
            val message = when (result) {
                ContentSearchResultState.Idle -> R.string.content_search_idle
                is ContentSearchResultState.Searching -> R.string.loading
                is ContentSearchResultState.Error -> result.message
                is ContentSearchResultState.Stopped -> R.string.content_search_stopped
                is ContentSearchResultState.Partial -> R.string.content_search_partial
                is ContentSearchResultState.Complete ->
                    if (result.items.isEmpty()) R.string.content_search_empty else null
            }
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                if (message != null) Text(
                    text = stringResource(message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val canOfferDirectSearch =
                    result is ContentSearchResultState.Error && result.message == R.string.content_search_error
                        && state.indexState.enabled && state.scopeState.scope != SearchScope.Metadata
                if (canOfferDirectSearch) {
                    TextButton(onClick = { ContentSearchUiIntent.SearchDirectly.emit() }) {
                        Text(stringResource(R.string.content_search_slow))
                    }
                    TextButton(onClick = { ContentSearchUiIntent.OpenSearchSettings.emit() }) {
                        Text(stringResource(R.string.settings))
                    }
                }
                if (result is ContentSearchResultState.Error
                    || result is ContentSearchResultState.Partial
                    || result is ContentSearchResultState.Stopped
                ) {
                    TextButton(onClick = { ContentSearchUiIntent.Submit.emit() }) {
                        Text(stringResource(R.string.content_search_refresh))
                    }
                }
            }
        }
        items(
            items = result.items(),
            key = { hit -> if (state.scopeState.parentId == null) hit.key else "${hit.key}:${hit.sourceId}" },
            contentType = { "search-hit" },
        ) { hit ->
            ResultCard(hit = hit, showWithin = state.scopeState.parentId == null, emit = emit)
        }
        if (result.nextCursor() != null) item(key = "more") {
            TextButton(
                onClick = { ContentSearchUiIntent.More.emit() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.content_search_more)) }
        }
    }
}

@Composable
private fun ResultCard(
    hit: SearchHit,
    showWithin: Boolean,
    emit: ContentSearchUiIntent.() -> Unit,
) {
    val highlight = MaterialTheme.colorScheme.primaryContainer
    val foreground = MaterialTheme.colorScheme.onPrimaryContainer
    val snippet = remember(hit.snippet, hit.matchStart, hit.matchEnd, highlight, foreground) {
        buildAnnotatedString {
            append(hit.snippet)
            if (hit.matchStart >= 0 && hit.matchEnd <= length) addStyle(
                style = SpanStyle(
                    background = highlight,
                    color = foreground,
                    fontWeight = FontWeight.Bold,
                ),
                start = hit.matchStart,
                end = hit.matchEnd,
            )
        }
    }
    // 标题与片段是两个清晰入口，片段才执行历史定位。
    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(kindLabel(hit.kind)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = hit.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = {
                        ContentSearchUiIntent.Open(hit = hit, locate = false).emit()
                    })
                    .heightIn(min = 48.dp)
                    .padding(vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = hit.sourceLabel.ifBlank { hit.names.replace('\n', ' ') }
            if (detail.isNotBlank()) Text(
                text = detail,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (hit.snippet.isNotEmpty()) Text(
                text = snippet,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = {
                        ContentSearchUiIntent.Open(hit = hit, locate = true).emit()
                    })
                    .heightIn(min = 48.dp)
                    .padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            else if (hit.sourceId > 0) TextButton(
                onClick = {
                    ContentSearchUiIntent.Open(hit = hit, locate = true).emit()
                },
            ) { Text(stringResource(R.string.content_search_open_match)) }
            if (showWithin) TextButton(
                onClick = {
                    ContentSearchUiIntent.SearchWithin(hit).emit()
                },
            ) { Text(stringResource(R.string.content_search_within)) }
        }
    }
}

private fun kindLabel(kind: Int): Int = when (kind) {
    1 -> R.string.home_session_filter_single
    2 -> R.string.home_session_filter_group
    3 -> R.string.home_content_filter_story
    else -> R.string.home_session_filter_all
}

private fun scopeLabel(scope: SearchScope): Int = when (scope) {
    SearchScope.All -> R.string.content_search_all
    SearchScope.Metadata -> R.string.content_search_metadata
    SearchScope.Body -> R.string.content_search_body
}

@Preview
@Composable
private fun ContentSearchPreview() {
    AppTheme(dynamicColor = false) {
        ContentSearchLayout(
            state = ContentSearchUiState.Normal(),
            emit = {})
    }
}
