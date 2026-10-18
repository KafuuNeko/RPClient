package me.kafuuneko.rpclient.feature.contentsearch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import me.kafuuneko.rpclient.libs.search.SearchHit
import me.kafuuneko.rpclient.libs.search.SearchIndexState
import me.kafuuneko.rpclient.libs.search.SearchKind
import me.kafuuneko.rpclient.libs.search.SearchScope
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppPageScaffold
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.generalContentBackground
import me.kafuuneko.rpclient.ui.widgets.PageButton as Button
import me.kafuuneko.rpclient.ui.widgets.PageFilterChip as FilterChip
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField

/** 搜索页只渲染状态；输入、停止、分页及导航都通过 Intent 交给 ViewModel。 */
@Composable
fun ContentSearchLayout(state: ContentSearchUiState, emit: ContentSearchUiIntent.() -> Unit) {
    BackHandler { ContentSearchUiIntent.Back.emit() }
    when (state) {
        ContentSearchUiState.None -> Unit
        is ContentSearchUiState.Normal -> SearchPage(state, emit)
        is ContentSearchUiState.Finished -> ContentSearchLayout(state.previous) {}
    }
}

/** 固定输入及范围操作，结果区域独立滚动，慢查询不会改变首页布局。 */
@Composable
private fun SearchPage(state: ContentSearchUiState.Normal, emit: ContentSearchUiIntent.() -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var focused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!focused) { focus.requestFocus(); focused = true } }
    val busy = state.resultState is ContentSearchResultState.Searching
    val submit = { keyboard?.hide(); ContentSearchUiIntent.Submit.emit() }
    // 输入与操作区不随长结果列表离开屏幕。
    AppPageScaffold(topBar = { AppTopBar(title = stringResource(R.string.content_search_title), onBack = { ContentSearchUiIntent.Back.emit() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            OutlinedTextField(
                value = state.inputState.draft,
                onValueChange = { ContentSearchUiIntent.ChangeQuery(it).emit() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).focusRequester(focus)
                    .generalContentBackground(RoundedCornerShape(16.dp)),
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.inputState.draft.isNotEmpty()) IconButton(onClick = { ContentSearchUiIntent.ChangeQuery("").emit() }) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.clear_search))
                    }
                },
                placeholder = { Text(stringResource(R.string.content_search_placeholder), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true, shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() })
            )
            ScopeControls(state.scopeState, busy, state.inputState.draft.isNotBlank(), submit, emit)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            SearchResults(state, Modifier.weight(1f), emit)
        }
    }
}

@Composable
private fun ScopeControls(scope: ContentSearchScopeState, busy: Boolean, canSubmit: Boolean, submit: () -> Unit, emit: ContentSearchUiIntent.() -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    // 分类横向滚动；范围菜单与搜索按钮固定可达，不放进同一滚动条。
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SearchKind.entries.forEach { kind ->
            FilterChip(selected = scope.kind == kind, onClick = { ContentSearchUiIntent.SelectKind(kind).emit() },
                label = { Text(stringResource(kindLabel(kind.code))) })
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            TextButton(onClick = { menuExpanded = true }) {
                Text(stringResource(scopeLabel(scope.scope)), modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Rounded.KeyboardArrowDown, null)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                SearchScope.entries.forEach { range ->
                    DropdownMenuItem(text = { Text(stringResource(scopeLabel(range))) }, onClick = {
                        menuExpanded = false
                        ContentSearchUiIntent.SelectScope(range).emit()
                    })
                }
            }
        }
        if (busy) Button(onClick = { ContentSearchUiIntent.Stop.emit() }) { Text(stringResource(R.string.stop)) }
        else Button(onClick = submit, enabled = canSubmit) { Text(stringResource(R.string.content_search_submit)) }
    }
    if (scope.parentId != null) TextButton(onClick = { ContentSearchUiIntent.ClearObjectScope.emit() }, modifier = Modifier.padding(horizontal = 18.dp)) {
        Text(scope.parentTitle, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Rounded.Close, stringResource(R.string.content_search_clear_scope))
    }
}

@Composable
private fun SearchResults(state: ContentSearchUiState.Normal, modifier: Modifier, emit: ContentSearchUiIntent.() -> Unit) {
    val result = state.resultState
    // 空态、准备状态、错误及部分结果各自有明确含义，不把未完成显示为无匹配。
    RpLazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp, 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (state.indexState != SearchIndexState.Ready && state.scopeState.scope != SearchScope.Metadata) {
            item(key = "index-state") {
                Column {
                    Text(stringResource(if (state.indexState == SearchIndexState.Unavailable) R.string.content_search_unavailable else R.string.content_search_preparing), style = MaterialTheme.typography.bodySmall)
                    if (state.inputState.draft.isNotBlank() && state.resultState !is ContentSearchResultState.Searching) {
                        TextButton(onClick = { ContentSearchUiIntent.SearchDirectly.emit() }) { Text(stringResource(R.string.content_search_slow)) }
                    }
                    Row {
                        TextButton(onClick = { ContentSearchUiIntent.SelectScope(SearchScope.Metadata).emit() }) { Text(stringResource(R.string.content_search_metadata)) }
                        if (state.indexState == SearchIndexState.Unavailable) TextButton(onClick = { ContentSearchUiIntent.RetryIndex.emit() }) { Text(stringResource(R.string.retry)) }
                    }
                }
            }
        }
        item(key = "query-status") {
            val message = when (result) {
                ContentSearchResultState.Idle -> R.string.content_search_idle
                is ContentSearchResultState.Searching -> R.string.loading
                is ContentSearchResultState.Error -> result.message
                is ContentSearchResultState.Content -> when {
                    result.stopped -> R.string.content_search_stopped
                    !result.complete -> R.string.content_search_partial
                    result.items.isEmpty() -> R.string.content_search_empty
                    else -> null
                }
            }
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                if (message != null) Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (result is ContentSearchResultState.Error && result.message == R.string.content_search_error &&
                    state.indexState != SearchIndexState.Unavailable && state.scopeState.scope != SearchScope.Metadata) {
                    TextButton(onClick = { ContentSearchUiIntent.SearchDirectly.emit() }) { Text(stringResource(R.string.content_search_slow)) }
                    TextButton(onClick = { ContentSearchUiIntent.RetryIndex.emit() }) { Text(stringResource(R.string.retry)) }
                }
                if (result is ContentSearchResultState.Error || result is ContentSearchResultState.Content && !result.complete) {
                    TextButton(onClick = { ContentSearchUiIntent.Submit.emit() }) { Text(stringResource(R.string.content_search_refresh)) }
                }
            }
        }
        items(result.items(), key = { if (state.scopeState.parentId == null) it.key else "${it.key}:${it.sourceId}" }, contentType = { "search-hit" }) {
            ResultCard(it, state.scopeState.parentId == null, emit)
        }
        if (result is ContentSearchResultState.Content && result.next != null) item(key = "more") {
            TextButton(onClick = { ContentSearchUiIntent.More.emit() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.content_search_more)) }
        }
    }
}

@Composable
private fun ResultCard(hit: SearchHit, showWithin: Boolean, emit: ContentSearchUiIntent.() -> Unit) {
    val highlight = MaterialTheme.colorScheme.primaryContainer
    val foreground = MaterialTheme.colorScheme.onPrimaryContainer
    val snippet = remember(hit.snippet, hit.matchStart, hit.matchEnd, highlight, foreground) {
        buildAnnotatedString {
            append(hit.snippet)
            if (hit.matchStart >= 0 && hit.matchEnd <= length) addStyle(SpanStyle(background = highlight, color = foreground, fontWeight = FontWeight.Bold), hit.matchStart, hit.matchEnd)
        }
    }
    // 标题与片段是两个清晰入口，片段才执行历史定位。
    AppSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(kindLabel(hit.kind)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(hit.title, Modifier.fillMaxWidth().clickable { ContentSearchUiIntent.Open(hit, false).emit() }.heightIn(min = 48.dp).padding(vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val detail = hit.sourceLabel.ifBlank { hit.names.replace('\n', ' ') }
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (hit.snippet.isNotEmpty()) Text(snippet, Modifier.fillMaxWidth().clickable { ContentSearchUiIntent.Open(hit, true).emit() }.heightIn(min = 48.dp).padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            else if (hit.sourceId > 0) TextButton(onClick = { ContentSearchUiIntent.Open(hit, true).emit() }) { Text(stringResource(R.string.content_search_open_match)) }
            if (showWithin) TextButton(onClick = { ContentSearchUiIntent.SearchWithin(hit).emit() }) { Text(stringResource(R.string.content_search_within)) }
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
    AppTheme(dynamicColor = false) { ContentSearchLayout(ContentSearchUiState.Normal()) {} }
}
