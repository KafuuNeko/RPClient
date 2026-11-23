package me.kafuuneko.rpclient.feature.contentsearch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.contentsearch.presentation.ContentSearchInputState
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
import me.kafuuneko.rpclient.ui.theme.AccentAmberColor
import me.kafuuneko.rpclient.ui.theme.AccentSkyColor
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppPageScaffold
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.LocalPageTopBarOpacity
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.PageFilterChip as FilterChip

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
 * - 顶部栏融合返回导航、紧凑搜索胶囊与提交/停止动作胶囊。
 * - 筛选栏通过视觉分隔区分内容范围与对象分类，单对象限定以独立上下文条呈现。
 * - 搜索进度条浮层覆盖在列表顶部，彻底消除搜索起止时的纵向位移闪烁。
 */
@Composable
private fun SearchPage(state: ContentSearchUiState.Normal, emit: ContentSearchUiIntent.() -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var focused by rememberSaveable { mutableStateOf(false) }
    var lastScrolledEpoch by rememberSaveable { mutableLongStateOf(0L) }
    // 首次进入自动聚焦输入框，页面重建后保持用户当前焦点状态。
    LaunchedEffect(Unit) {
        if (!focused) {
            focus.requestFocus()
            focused = true
        }
    }
    // 仅在全新检索或切换筛选产生新批次时回到顶部，分页和返回恢复保持滚动位置。
    LaunchedEffect(state.resultEpoch) {
        if (state.resultEpoch > 0L && state.resultEpoch != lastScrolledEpoch) {
            lastScrolledEpoch = state.resultEpoch
            listState.scrollToItem(0)
        }
    }
    val searchingState = state.resultState as? ContentSearchResultState.Searching
    val isSearching = searchingState != null
    val isAppending = searchingState?.isAppending == true
    val submit = {
        keyboard?.hide()
        ContentSearchUiIntent.Submit.emit()
    }

    AppPageScaffold(
        topBar = {
            SearchTopBar(
                query = state.inputState.draft,
                busy = isSearching,
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
                ScopeControls(scope = state.scopeState, emit = emit)
                ActiveObjectScopeSection(scope = state.scopeState, emit = emit)
                // 进度条采用叠加层渲染，避免出现或消失时推动列表发生跳动。
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    SearchResults(
                        state = state,
                        listState = listState,
                        modifier = Modifier.fillMaxSize(),
                        emit = emit,
                    )
                    SearchProgressOverlay(
                        visible = isSearching && !isAppending,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        },
    )
}

/** 叠加在结果列表顶缘的细线进度条，淡入淡出时不改变列表纵向布局位置。 */
@Composable
private fun SearchProgressOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    // 进度与列表独立测量，切换状态时保持结果位置。
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(durationMillis = 150)),
        exit = fadeOut(animationSpec = tween(durationMillis = 150)),
    ) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        )
    }
}

/** 融合返回导航、紧凑搜索胶囊与平滑切换提交/停止胶囊的统一顶部栏。 */
@Composable
private fun SearchTopBar(
    query: String,
    busy: Boolean,
    canSubmit: Boolean,
    focus: FocusRequester,
    onSubmit: () -> Unit,
    emit: ContentSearchUiIntent.() -> Unit,
) {
    // 顶部栏沿用标准外观底板，统一适配状态栏安全区与自定义壁纸透明度。
    AppSurface(
        kind = PageSurfaceKind.TopBar,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .windowInsetsPadding(
                        insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                    )
                    .padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = { ContentSearchUiIntent.Back.emit() },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_back),
                        contentDescription = stringResource(R.string.back),
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                SearchInputCapsule(
                    query = query,
                    focus = focus,
                    onQueryChange = { text -> ContentSearchUiIntent.ChangeQuery(text).emit() },
                    onSubmit = onSubmit,
                    modifier = Modifier.weight(1f),
                )
                SearchActionPill(
                    busy = busy,
                    canSubmit = canSubmit,
                    onSubmit = onSubmit,
                    onStop = { ContentSearchUiIntent.Stop.emit() },
                )
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
            )
        }
    }
}

/** 紧凑圆角搜索输入胶囊，带焦点描边过渡与一键清空按钮。 */
@Composable
private fun SearchInputCapsule(
    query: String,
    focus: FocusRequester,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f)
        },
        animationSpec = tween(durationMillis = 150),
        label = "searchInputBorder",
    )
    val leadingIconTint by animateColorAsState(
        targetValue = if (isFocused) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 150),
        label = "searchInputIcon",
    )
    val shape = RoundedCornerShape(16.dp)

    // 描边绘制在 AppSurface 外侧，即使顶栏完全透明时仍保留清晰的输入边界。
    AppSurface(
        modifier = modifier.border(
            width = if (isFocused) 1.dp else 0.5.dp,
            color = borderColor,
            shape = shape,
        ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 42.dp)
                .padding(start = 12.dp, end = if (query.isNotEmpty()) 4.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = leadingIconTint,
            )
            Spacer(modifier = Modifier.width(8.dp))
            SearchQueryTextField(
                query = query,
                focus = focus,
                onQueryChange = onQueryChange,
                onFocusChange = { isFocused = it },
                onSubmit = onSubmit,
                modifier = Modifier.weight(1f),
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.clear_search),
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 保留紧凑占位样式和键盘搜索动作。 */
@Composable
private fun SearchQueryTextField(
    query: String,
    focus: FocusRequester,
    onQueryChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier,
) {
    // 占位文案与真实输入重叠测量，开始输入时高度保持一致。
    Box(
        modifier = modifier.padding(vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (query.isEmpty()) {
            Text(
                text = stringResource(R.string.content_search_placeholder),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            )
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .onFocusChanged { focusState -> onFocusChange(focusState.isFocused) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        )
    }
}

/** 顶部栏右侧的搜索/停止胶囊按钮，保持稳定尺寸与平滑淡入淡出。 */
@Composable
private fun SearchActionPill(
    busy: Boolean,
    canSubmit: Boolean,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
) {
    // 搜索与停止共用固定尺寸，切换时让已退场的按钮失去点击能力。
    AnimatedContent(
        targetState = busy,
        transitionSpec = {
            (
                fadeIn(animationSpec = tween(durationMillis = 140)) togetherWith
                    fadeOut(animationSpec = tween(durationMillis = 100))
                ).using(SizeTransform(clip = false))
        },
        label = "searchActionPill",
    ) { searching ->
        SearchActionButton(
            searching = searching,
            canSubmit = canSubmit,
            isCurrentAction = searching == busy,
            onClick = if (searching) onStop else onSubmit,
        )
    }
}

/** 为搜索与停止动作提供同尺寸的按钮表面。 */
@Composable
private fun SearchActionButton(
    searching: Boolean,
    canSubmit: Boolean,
    isCurrentAction: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val topBarOpacity = LocalPageTopBarOpacity.current
    val containerColor = when {
        searching -> colors.error.copy(alpha = 0.14f)
        canSubmit -> colors.primary
        else -> colors.surfaceVariant.copy(alpha = 0.55f)
    }
    // 壁纸透明度较高时使用表面前景色，保持搜索标签对比度。
    val contentColor = when {
        searching -> colors.error
        !canSubmit -> colors.onSurfaceVariant.copy(alpha = 0.45f)
        topBarOpacity < 0.5f -> colors.onSurface
        else -> colors.onPrimary
    }
    val border = if (searching) {
        BorderStroke(width = 0.5.dp, color = colors.error.copy(alpha = 0.35f))
    } else {
        null
    }
    AppSurface(
        onClick = onClick,
        enabled = isCurrentAction && (searching || canSubmit),
        modifier = Modifier
            .widthIn(min = 60.dp)
            .heightIn(min = 40.dp),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = border,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(if (searching) R.string.stop else R.string.content_search_submit),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** 单行横向滚动筛选条，用竖向细线清晰区分“检索范围”与“内容分类”两个维度。 */
@Composable
private fun ScopeControls(
    scope: ContentSearchScopeState,
    emit: ContentSearchUiIntent.() -> Unit,
) {
    val chipShape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchScopePicker(
            scope = scope.scope,
            shape = chipShape,
            onSelect = { range -> ContentSearchUiIntent.SelectScope(range).emit() },
        )
        // 竖向分隔线将范围维度与内容类型维度清晰拆开。
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(18.dp)
                .background(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                    shape = CircleShape,
                ),
        )
        // 内容类型快捷筛选标签。
        SearchKind.entries.forEach { kind ->
            FilterChip(
                selected = scope.kind == kind,
                onClick = { ContentSearchUiIntent.SelectKind(kind).emit() },
                shape = chipShape,
                label = { Text(stringResource(kindLabel(kind.code))) },
            )
        }
    }
}

/** 通过下拉菜单选择标题与角色、正文或全部检索范围。 */
@Composable
private fun SearchScopePicker(
    scope: SearchScope,
    shape: RoundedCornerShape,
    onSelect: (SearchScope) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    // 检索范围下拉选择器（全部内容、标题与角色、正文）。
    Box {
        FilterChip(
            selected = scope != SearchScope.All,
            onClick = { menuExpanded = true },
            shape = shape,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
            label = { Text(stringResource(scopeLabel(scope))) },
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
                val selected = range == scope
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(scopeLabel(range)),
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    trailingIcon = if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else null,
                    onClick = {
                        menuExpanded = false
                        onSelect(range)
                    },
                )
            }
        }
    }
}

/** 限定在单个会话或故事内检索时的可折叠上下文区域，退场动画期间保留末次标题避免空白闪烁。 */
@Composable
private fun ActiveObjectScopeSection(
    scope: ContentSearchScopeState,
    emit: ContentSearchUiIntent.() -> Unit,
) {
    // 退场时沿用上一对象的名称与类型，避免先清空再播放收起动画。
    var displayTitle by remember { mutableStateOf(scope.parentTitle) }
    var displayKindCode by remember { mutableIntStateOf(scope.kind.code) }
    if (scope.parentId != null) {
        displayTitle = scope.parentTitle
        displayKindCode = scope.kind.code
    }
    AnimatedVisibility(
        visible = scope.parentId != null,
        enter = fadeIn(animationSpec = tween(durationMillis = 150)) + expandVertically(
            animationSpec = tween(
                durationMillis = 180
            )
        ),
        exit = fadeOut(animationSpec = tween(durationMillis = 120)) + shrinkVertically(
            animationSpec = tween(
                durationMillis = 150
            )
        ),
    ) {
        ActiveObjectScopeBanner(
            kindCode = displayKindCode,
            parentTitle = displayTitle,
            emit = emit,
        )
    }
}

/** 限定在单个会话或故事内检索时的顶部上下文条，支持一键退出限定。 */
@Composable
private fun ActiveObjectScopeBanner(
    kindCode: Int,
    parentTitle: String,
    emit: ContentSearchUiIntent.() -> Unit,
) {
    // 独立上下文条始终停留在可见区域顶部，避免随筛选条横向滚动而被挤出视野。
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, bottom = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RpIconBubble(
                icon = kindIcon(kindCode),
                size = 26.dp,
                iconSize = 14.dp,
                shape = RoundedCornerShape(8.dp),
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                contentColor = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = parentTitle,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(
                onClick = { ContentSearchUiIntent.ClearObjectScope.emit() },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.content_search_clear_scope),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/** 搜索结果主体列表，按索引状态、检索结果和分页尾部分区组合渲染。 */
@Composable
private fun SearchResults(
    state: ContentSearchUiState.Normal,
    listState: LazyListState,
    modifier: Modifier,
    emit: ContentSearchUiIntent.() -> Unit,
) {
    val result = state.resultState
    val hitItems = result.items()
    val showPagination = result.nextCursor() != null
        || (result is ContentSearchResultState.Searching && result.isAppending)

    // 空态、准备状态、错误及部分结果各自有明确语义，不把未完成显示为无匹配。
    RpLazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 18.dp, top = 8.dp, end = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        indexStatusItems(state = state, emit = emit)
        resultStatusItem(state = state, emit = emit)
        items(
            items = hitItems,
            // 统一以对象键加源 ID 生成稳定唯一键，避免退出单对象范围过渡期内同对象多条命中键冲突。
            key = { hit -> "${hit.key}:${hit.sourceId}" },
            contentType = { "search-hit" },
        ) { hit ->
            SearchResultCard(
                hit = hit,
                showWithin = state.resultParentId == null,
                enabled = !state.isReplacingResults,
                emit = emit,
            )
        }
        if (showPagination) {
            item(key = "more") {
                SearchPaginationFooter(
                    isLoading = result is ContentSearchResultState.Searching,
                    enabled = !state.isReplacingResults,
                    onLoadMore = { ContentSearchUiIntent.More.emit() },
                )
            }
        }
    }
}

/** 渲染索引构建、清理或后台增量更新提示横幅。 */
private fun LazyListScope.indexStatusItems(
    state: ContentSearchUiState.Normal,
    emit: ContentSearchUiIntent.() -> Unit,
) {
    if (state.indexState.busy) {
        item(key = "index-maintenance") {
            val isFailure = state.indexState == SearchIndexState.CleanupFailed
            // 索引操作统一在设置页处理，搜索页仅展示当前维护原因与跳转入口。
            val message = when (val index = state.indexState) {
                is SearchIndexState.Building -> stringResource(
                    R.string.content_search_index_building,
                    index.processed,
                )

                SearchIndexState.Cleaning -> stringResource(R.string.content_search_index_cleaning)
                SearchIndexState.CleanupFailed -> stringResource(R.string.content_search_index_cleanup_failed)
                else -> stringResource(R.string.loading)
            }
            SearchStatusBanner(
                icon = if (isFailure) Icons.Rounded.ErrorOutline else Icons.Rounded.Sync,
                message = message,
                accentColor = if (isFailure) MaterialTheme.colorScheme.error else AccentSkyColor,
                showSpinner = !isFailure,
                actions = listOf(
                    SearchBannerAction(
                        label = stringResource(R.string.settings),
                        icon = Icons.Rounded.Settings,
                        onClick = { ContentSearchUiIntent.OpenSearchSettings.emit() },
                    ),
                ),
            )
        }
    }
    if (state.indexState == SearchIndexState.Updating && state.scopeState.scope != SearchScope.Metadata) {
        item(key = "index-state") {
            SearchStatusBanner(
                icon = Icons.Rounded.Sync,
                message = stringResource(R.string.content_search_preparing),
                accentColor = AccentSkyColor,
            )
        }
    }
}

/** 按需渲染空闲引导、无结果空态、慢查询首屏等待或异常恢复横幅，完整命中时不占空位。 */
private fun LazyListScope.resultStatusItem(
    state: ContentSearchUiState.Normal,
    emit: ContentSearchUiIntent.() -> Unit,
) {
    when (val result = state.resultState) {
        ContentSearchResultState.Idle -> {
            // 索引维护横幅已占用说明区时，避免叠加双层提示卡片。
            if (state.indexState.busy) return
            item(key = "query-idle") {
                SearchEmptyStateCard(
                    title = stringResource(R.string.content_search_title),
                    subtitle = stringResource(R.string.content_search_idle),
                    icon = Icons.Rounded.Search,
                )
            }
        }

        is ContentSearchResultState.Searching -> {
            if (result.items.isNotEmpty()) return
            item(key = "query-searching") {
                SearchEmptyStateCard(
                    title = stringResource(R.string.loading),
                    isLoading = true,
                )
            }
        }

        is ContentSearchResultState.Complete -> {
            if (result.items.isNotEmpty()) return
            item(key = "query-empty") {
                SearchEmptyStateCard(
                    title = stringResource(R.string.content_search_empty),
                    icon = Icons.Rounded.SearchOff,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    action = if (state.scopeState.parentId != null) {
                        SearchBannerAction(
                            label = stringResource(R.string.content_search_clear_scope),
                            onClick = { ContentSearchUiIntent.ClearObjectScope.emit() },
                        )
                    } else null,
                )
            }
        }

        is ContentSearchResultState.Partial,
        is ContentSearchResultState.Stopped,
        is ContentSearchResultState.Error -> {
            item(key = "query-status") {
                QueryAlertBanner(state = state, result = result, emit = emit)
            }
        }
    }
}

/** 渲染部分结果、已停止或出错时的语义横幅及恢复动作组。 */
@Composable
private fun QueryAlertBanner(
    state: ContentSearchUiState.Normal,
    result: ContentSearchResultState,
    emit: ContentSearchUiIntent.() -> Unit,
) {
    val isHardError = result is ContentSearchResultState.Error
        && result.message != R.string.content_search_changed
    val accentColor = if (isHardError) MaterialTheme.colorScheme.error else AccentAmberColor
    val icon = when (result) {
        is ContentSearchResultState.Stopped -> Icons.Rounded.PauseCircleOutline
        is ContentSearchResultState.Partial -> Icons.Rounded.Info
        is ContentSearchResultState.Error -> if (isHardError) {
            Icons.Rounded.ErrorOutline
        } else {
            Icons.Rounded.Refresh
        }

        else -> Icons.Rounded.Info
    }
    val messageRes = when (result) {
        is ContentSearchResultState.Partial -> R.string.content_search_partial
        is ContentSearchResultState.Stopped -> R.string.content_search_stopped
        is ContentSearchResultState.Error -> result.message
        else -> return
    }
    // 组合当前状态可用的恢复操作，超长校验错误只需修改输入，索引失败额外提供原文扫描与设置入口。
    val canOfferRefresh = result !is ContentSearchResultState.Error
        || result.message != R.string.content_search_too_long
    val canOfferDirectSearch = result is ContentSearchResultState.Error
        && result.message == R.string.content_search_error
        && state.indexState.enabled
        && state.scopeState.scope != SearchScope.Metadata
    val actions = buildList {
        if (canOfferRefresh) {
            add(
                SearchBannerAction(
                    label = stringResource(R.string.content_search_refresh),
                    icon = Icons.Rounded.Refresh,
                    onClick = { ContentSearchUiIntent.Submit.emit() },
                ),
            )
        }
        if (canOfferDirectSearch) {
            add(
                SearchBannerAction(
                    label = stringResource(R.string.content_search_slow),
                    icon = Icons.Rounded.Search,
                    onClick = { ContentSearchUiIntent.SearchDirectly.emit() },
                ),
            )
            add(
                SearchBannerAction(
                    label = stringResource(R.string.settings),
                    icon = Icons.Rounded.Settings,
                    onClick = { ContentSearchUiIntent.OpenSearchSettings.emit() },
                ),
            )
        }
    }
    SearchStatusBanner(
        icon = icon,
        message = stringResource(messageRes),
        accentColor = accentColor,
        actions = actions,
    )
}

@Preview(name = "Search - Idle Dark", widthDp = 390, heightDp = 844)
@Composable
private fun ContentSearchIdlePreview() {
    AppTheme(darkTheme = true, dynamicColor = false) {
        ContentSearchLayout(
            state = ContentSearchUiState.Normal(indexState = SearchIndexState.Ready),
            emit = {},
        )
    }
}

@Preview(name = "Search - Results Dark", widthDp = 390, heightDp = 844)
@Composable
private fun ContentSearchResultsDarkPreview() {
    AppTheme(darkTheme = true, dynamicColor = false) {
        ContentSearchLayout(
            state = previewResultsState(),
            emit = {},
        )
    }
}

@Preview(name = "Search - Results Light", widthDp = 390, heightDp = 844)
@Composable
private fun ContentSearchResultsLightPreview() {
    AppTheme(darkTheme = false, dynamicColor = false) {
        ContentSearchLayout(
            state = previewResultsState(),
            emit = {},
        )
    }
}

/** 预览专用的多类型结果样本数据。 */
private fun previewResultsState(): ContentSearchUiState.Normal = ContentSearchUiState.Normal(
    inputState = ContentSearchInputState(draft = "rain", submitted = "rain"),
    scopeState = ContentSearchScopeState(kind = SearchKind.All, scope = SearchScope.All),
    indexState = SearchIndexState.Ready,
    // 单聊与故事命中使用不同的来源标记，预览同时验证两种布局。
    resultState = ContentSearchResultState.Complete(
        items = listOf(
            SearchHit(
                kind = 1,
                parentId = 1L,
                title = "A rainy evening at the café",
                names = "Chino",
                latestTime = 1_760_000_000_000L,
                sourceId = 42L,
                snippet = "The soft sound of rain tapped gently against the tall cafe window while she brewed another cup.",
                matchStart = 18,
                matchEnd = 22,
                sourceLabel = "Chino",
            ),
            SearchHit(
                kind = 3,
                parentId = 2L,
                title = "Chronicles of the Silver Mist",
                names = "Rusk\nAria",
                latestTime = 1_759_900_000_000L,
                sourceId = 7L,
                snippet = "Through the autumn rain they finally spotted the lantern glowing above the stone harbor gate.",
                matchStart = 19,
                matchEnd = 23,
                sourceLabel = "Chapter 3 · Harbor Gate",
            ),
        ),
        next = null,
    ),
)
