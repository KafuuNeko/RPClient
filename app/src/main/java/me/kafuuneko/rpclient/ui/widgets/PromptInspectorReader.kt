package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspectionItem
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspectionSegment
import me.kafuuneko.rpclient.libs.prompt.model.PromptSourceKind
import me.kafuuneko.rpclient.model.PromptReaderMode
import me.kafuuneko.rpclient.model.baseLabel
import me.kafuuneko.rpclient.model.buildCompactPreview
import me.kafuuneko.rpclient.model.buildHighlightedInspectorText
import me.kafuuneko.rpclient.model.computeTokenSharePercent
import me.kafuuneko.rpclient.model.countInspectorMatches
import me.kafuuneko.rpclient.model.label
import me.kafuuneko.rpclient.model.resolveRoleColors
import me.kafuuneko.rpclient.model.splitInspectorTextChunks
import me.kafuuneko.rpclient.ui.dialog.dialogControlColor
import me.kafuuneko.rpclient.ui.widgets.AppSurface as Surface
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField

/**
 * 超长消息专注阅读器：支持「按来源拆解」与「最终原文」双视图、页内搜索高亮及分块虚拟化渲染。
 *
 * @param item 当前检视的最终消息项
 * @param totalInputTokens 整份 Prompt 的输入 Token 总数，用于展示占比
 * @param initialQuery 从主列表带入的初始搜索关键词
 * @param initialSegmentIndex 可选的目标分段下标，点击来源明细进入时自动展开并定位
 * @param onBack 返回消息列表回调
 * @param onClose 关闭整个 Prompt 检查器回调
 * @param onCopyRequest 复制单段或整条消息文本回调
 * @param onPreviewImages 图片预览回调
 */
@Composable
internal fun PromptInspectorReaderView(
    item: PromptInspectionItem,
    totalInputTokens: Int,
    initialQuery: String,
    initialSegmentIndex: Int?,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onCopyRequest: (String) -> Unit,
    onPreviewImages: ((List<String>, Int) -> Unit)?,
) {
    val hasMultipleSegments = item.segments.size > 1
    var readerMode by remember(item.index, hasMultipleSegments) {
        mutableStateOf(if (hasMultipleSegments) PromptReaderMode.Segments else PromptReaderMode.Raw)
    }
    var searchVisible by remember(item.index) { mutableStateOf(initialQuery.isNotBlank()) }
    var searchQuery by remember(item.index) { mutableStateOf(initialQuery) }
    var monospaceFont by remember { mutableStateOf(false) }
    var expandedSegments by remember(item.index, initialSegmentIndex) {
        mutableStateOf(
            when {
                initialSegmentIndex != null -> setOf(initialSegmentIndex)
                item.segments.size <= 3 -> item.segments.indices.toSet()
                else -> setOf(0)
            },
        )
    }
    val segmentsListState = rememberLazyListState()
    val rawListState = rememberLazyListState()
    val tokenShare = remember(item.tokenCount, totalInputTokens) {
        computeTokenSharePercent(item.tokenCount, totalInputTokens)
    }
    val totalMatches = remember(item.content, searchQuery) {
        countInspectorMatches(item.content, searchQuery)
    }

    // 从来源明细跳转进入时自动滚到目标分段，减少长列表手动查找
    LaunchedEffect(item.index, initialSegmentIndex) {
        if (initialSegmentIndex != null && hasMultipleSegments) {
            readerMode = PromptReaderMode.Segments
            val clampedSegment = initialSegmentIndex.coerceIn(0, item.segments.lastIndex)
            val imageOffset = if (item.images.isNotEmpty()) 1 else 0
            // 初始仅展开目标分段，因此目标前每个折叠分段占标题和间距两项
            segmentsListState.scrollToItem(index = imageOffset + clampedSegment * 2)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ReaderHeader(
            item = item,
            tokenShare = tokenShare,
            searchActive = searchVisible || searchQuery.isNotBlank(),
            monospaceFont = monospaceFont,
            onBack = onBack,
            onToggleSearch = {
                if (searchVisible && searchQuery.isNotBlank()) {
                    searchQuery = ""
                }
                searchVisible = !searchVisible
            },
            onToggleFont = { monospaceFont = !monospaceFont },
            onCopyAll = { onCopyRequest(item.content) },
            onClose = onClose,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

        // 存在多来源分段时展示「按来源拆解 / 最终原文」双视角切换
        if (hasMultipleSegments) {
            RpSegmentedTabs(
                tabs = PromptReaderMode.entries,
                selectedTab = readerMode,
                onTabSelected = { mode -> readerMode = mode },
                tabLabel = { mode ->
                    when (mode) {
                        PromptReaderMode.Segments -> stringResource(
                            R.string.prompt_inspector_reader_tab_segments,
                            item.segments.size,
                        )
                        PromptReaderMode.Raw -> stringResource(R.string.prompt_inspector_reader_tab_raw)
                    }
                },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        // 页内关键词搜索栏与实时命中计数
        if (searchVisible) {
            ReaderSearchBar(
                query = searchQuery,
                matchCount = totalMatches,
                onQueryChange = { value -> searchQuery = value },
            )
        }

        if (readerMode == PromptReaderMode.Segments && hasMultipleSegments) {
            ReaderSegmentsContent(
                item = item,
                searchQuery = searchQuery,
                monospaceFont = monospaceFont,
                expandedSegments = expandedSegments,
                listState = segmentsListState,
                onToggleSegment = { segmentIndex ->
                    expandedSegments = if (segmentIndex in expandedSegments) {
                        expandedSegments - segmentIndex
                    } else {
                        expandedSegments + segmentIndex
                    }
                },
                onToggleAllSegments = { expandAll ->
                    expandedSegments = if (expandAll) item.segments.indices.toSet() else emptySet()
                },
                onCopySegment = onCopyRequest,
                onPreviewImages = onPreviewImages,
            )
        } else {
            ReaderRawContent(
                item = item,
                searchQuery = searchQuery,
                monospaceFont = monospaceFont,
                listState = rawListState,
                onPreviewImages = onPreviewImages,
            )
        }
    }
}

/** 阅读器顶部操作栏：返回列表、消息标识、搜索、字体切换、整条复制与关闭。 */
@Composable
private fun ReaderHeader(
    item: PromptInspectionItem,
    tokenShare: Int,
    searchActive: Boolean,
    monospaceFont: Boolean,
    onBack: () -> Unit,
    onToggleSearch: () -> Unit,
    onToggleFont: () -> Unit,
    onCopyAll: () -> Unit,
    onClose: () -> Unit,
) {
    val roleColors = resolveRoleColors(item.role)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧返回列表并展示当前消息序号、角色与 Token 占比
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "#${item.index}",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = roleColors.container,
                ) {
                    Text(
                        text = item.role.name.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = roleColors.content,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            val tokenStats = if (tokenShare > 0) {
                "${item.tokenCount} Tk · $tokenShare%"
            } else {
                "${item.tokenCount} Tk"
            }
            Text(
                text = tokenStats,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 右侧集中放置页内搜索、字体切换、整条复制与直接关闭操作
        IconButton(onClick = onToggleSearch) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.prompt_inspector_reader_search_placeholder),
                tint = if (searchActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        IconButton(onClick = onToggleFont) {
            Icon(
                imageVector = if (monospaceFont) Icons.Rounded.Code else Icons.Rounded.Description,
                contentDescription = stringResource(R.string.prompt_inspector_reader_toggle_font),
                tint = if (monospaceFont) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        IconButton(onClick = onCopyAll) {
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = stringResource(R.string.copy),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.close),
            )
        }
    }
}

/** 阅读器页内搜索框，实时展示当前消息内的关键词匹配数量。 */
@Composable
private fun ReaderSearchBar(
    query: String,
    matchCount: Int,
    onQueryChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 搜索框与右侧匹配数徽章同行排布，有输入时即时反馈当前消息内的命中总数
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            placeholder = {
                Text(
                    text = stringResource(R.string.prompt_inspector_reader_search_placeholder),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.clear_search),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
        )
        if (query.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (matchCount > 0) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                },
            ) {
                Text(
                    text = stringResource(R.string.prompt_inspector_reader_match_count, matchCount),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (matchCount > 0) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** 按来源拆解视图：逐段展示单源名称、Token 占比条及分块虚拟化的正文内容。 */
@Composable
private fun ReaderSegmentsContent(
    item: PromptInspectionItem,
    searchQuery: String,
    monospaceFont: Boolean,
    expandedSegments: Set<Int>,
    listState: LazyListState,
    onToggleSegment: (Int) -> Unit,
    onToggleAllSegments: (Boolean) -> Unit,
    onCopySegment: (String) -> Unit,
    onPreviewImages: ((List<String>, Int) -> Unit)?,
) {
    val needle = searchQuery.trim()
    val segmentTotalTokens = remember(item.segments) {
        item.segments.sumOf { segment -> segment.tokenCount }.coerceAtLeast(1)
    }
    // 预解析分段来源标签，支持按来源名过滤分段
    val segmentLabels = item.segments.map { segment ->
        segment.sources.map { source -> source.label() }
    }
    val visibleEntries = remember(item.segments, needle, segmentLabels) {
        item.segments.mapIndexedNotNull { index, segment ->
            val matched = needle.isEmpty()
                || segment.content.contains(needle, ignoreCase = true)
                || segmentLabels[index].any { label -> label.contains(needle, ignoreCase = true) }
            if (matched) index to segment else null
        }
    }
    val allExpanded = visibleEntries.isNotEmpty()
        && visibleEntries.all { (index, _) -> index in expandedSegments }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(
                    R.string.prompt_inspector_item_segments_count,
                    visibleEntries.size,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (visibleEntries.isNotEmpty() && needle.isEmpty()) {
                TextButton(
                    onClick = { onToggleAllSegments(!allExpanded) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Icon(
                        imageVector = if (allExpanded) Icons.Rounded.UnfoldLess else Icons.Rounded.UnfoldMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(
                            id = if (allExpanded) {
                                R.string.prompt_inspector_collapse_all
                            } else {
                                R.string.prompt_inspector_expand_all
                            },
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        RpLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 24.dp),
        ) {
            if (item.images.isNotEmpty()) {
                item(key = "reader-images") {
                    MessageImageAttachmentList(
                        images = item.images,
                        imageTokenCounts = item.imageTokenCounts,
                        onPreviewImages = onPreviewImages,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
            if (visibleEntries.isEmpty()) {
                item(key = "reader-segments-empty") {
                    RpInfoCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.SearchOff,
                        title = stringResource(R.string.prompt_inspector_reader_no_matches),
                        subtitle = searchQuery,
                    )
                }
            }
            visibleEntries.forEach { (segmentIndex, segment) ->
                val isExpanded = needle.isNotEmpty() || segmentIndex in expandedSegments
                readerSegmentItems(
                    segmentIndex = segmentIndex,
                    segment = segment,
                    segmentTotalTokens = segmentTotalTokens,
                    searchQuery = searchQuery,
                    monospaceFont = monospaceFont,
                    expanded = isExpanded,
                    onToggleExpanded = { onToggleSegment(segmentIndex) },
                    onCopySegment = { onCopySegment(segment.content) },
                )
            }
        }
    }
}

/**
 * 将单个来源分段及其分块正文注册为连续的 LazyColumn 列表项，兼顾完整卡片视觉与超长文本流畅滚动。
 */
private fun LazyListScope.readerSegmentItems(
    segmentIndex: Int,
    segment: PromptInspectionSegment,
    segmentTotalTokens: Int,
    searchQuery: String,
    monospaceFont: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onCopySegment: () -> Unit,
) {
    val chunks = if (expanded) splitInspectorTextChunks(segment.content) else emptyList()
    val hasContinuationChunks = expanded && chunks.isNotEmpty()
    // 展开正文时标题底边改为直角，与下方分块正文无缝拼合成完整卡片视觉
    val headerShape = if (hasContinuationChunks) {
        RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
    } else {
        RoundedCornerShape(14.dp)
    }

    item(key = "seg-header-$segmentIndex") {
        ReaderSegmentHeaderCard(
            segmentIndex = segmentIndex,
            segment = segment,
            segmentTotalTokens = segmentTotalTokens,
            shape = headerShape,
            expanded = expanded,
            showPreview = !expanded,
            searchQuery = searchQuery,
            onToggleExpanded = onToggleExpanded,
            onCopySegment = onCopySegment,
        )
    }
    if (hasContinuationChunks) {
        itemsIndexed(
            items = chunks,
            key = { chunkIndex, _ -> "seg-$segmentIndex-chunk-$chunkIndex" },
        ) { chunkIndex, chunkText ->
            ReaderChunkSurface(
                text = chunkText,
                searchQuery = searchQuery,
                monospaceFont = monospaceFont,
                isFirst = false,
                isLast = chunkIndex == chunks.lastIndex,
            )
        }
    }
    item(key = "seg-gap-$segmentIndex") {
        Spacer(modifier = Modifier.height(10.dp))
    }
}

/** 渲染单个来源分段的标题头、单段 Token 占比条与折叠摘要。 */
@Composable
private fun ReaderSegmentHeaderCard(
    segmentIndex: Int,
    segment: PromptInspectionSegment,
    segmentTotalTokens: Int,
    shape: RoundedCornerShape,
    expanded: Boolean,
    showPreview: Boolean,
    searchQuery: String,
    onToggleExpanded: () -> Unit,
    onCopySegment: () -> Unit,
) {
    val sharePercent = remember(segment.tokenCount, segmentTotalTokens) {
        computeTokenSharePercent(segment.tokenCount, segmentTotalTokens)
    }
    val shareRatio = (segment.tokenCount.toFloat() / segmentTotalTokens.toFloat()).coerceIn(0f, 1f)
    val primarySource = segment.sources.firstOrNull()
    val categoryLabel = primarySource?.kind?.baseLabel()
        ?: stringResource(R.string.prompt_source_other)
    val detailTitle = when {
        segment.sources.size == 1 -> primarySource?.detail.orEmpty()
        segment.sources.size > 1 -> segment.sources
            .mapNotNull { source -> source.detail.takeIf(String::isNotBlank) }
            .joinToString(" · ")
        else -> ""
    }
    val isWorldInfo = primarySource?.kind == PromptSourceKind.WorldInfo
    val previewText = remember(segment.content) { buildCompactPreview(segment.content) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpanded),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 左侧序号与来源类别胶囊
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = if (isWorldInfo) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                    },
                ) {
                    Text(
                        text = "${segmentIndex + 1} · $categoryLabel",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isWorldInfo) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                // 右侧单段 Token 与占比
                val tokenLabel = if (sharePercent > 0) {
                    "${segment.tokenCount} Tk · $sharePercent%"
                } else {
                    "${segment.tokenCount} Tk"
                }
                Text(
                    text = tokenLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = onCopySegment,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.copy),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 若包含世界书条目名等补充明细，独立成行清晰展示
            if (detailTitle.isNotBlank()) {
                Text(
                    text = detailTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 单段 Token 占比微缩进度条
            LinearProgressIndicator(
                progress = { shareRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape),
                color = if (isWorldInfo) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondary
                },
                trackColor = dialogControlColor(
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                ),
            )
            if (showPreview && previewText.isNotBlank()) {
                val highlightBg = MaterialTheme.colorScheme.primaryContainer
                val highlightFg = MaterialTheme.colorScheme.onPrimaryContainer
                val previewAnnotated = remember(previewText, searchQuery, highlightBg, highlightFg) {
                    buildHighlightedInspectorText(
                        text = previewText,
                        query = searchQuery,
                        highlightBackground = highlightBg,
                        highlightForeground = highlightFg,
                    )
                }
                Text(
                    text = previewAnnotated,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 最终原文视图：将完整消息内容分块虚拟化渲染，支持流畅滚动、搜索高亮和长按划词复制。 */
@Composable
private fun ReaderRawContent(
    item: PromptInspectionItem,
    searchQuery: String,
    monospaceFont: Boolean,
    listState: LazyListState,
    onPreviewImages: ((List<String>, Int) -> Unit)?,
) {
    val chunks = remember(item.content) { splitInspectorTextChunks(item.content) }

    // 原始正文按有界块虚拟化输出，避免单条长系统提示词阻塞首帧测量与滑动
    RpLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
    ) {
        if (item.images.isNotEmpty()) {
            item(key = "raw-images") {
                MessageImageAttachmentList(
                    images = item.images,
                    imageTokenCounts = item.imageTokenCounts,
                    onPreviewImages = onPreviewImages,
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
        if (chunks.isEmpty()) {
            item(key = "raw-empty") {
                Text(
                    text = stringResource(R.string.prompt_inspector_empty_content),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        } else {
            itemsIndexed(
                items = chunks,
                key = { index, _ -> "raw-chunk-$index" },
            ) { index, chunkText ->
                ReaderChunkSurface(
                    text = chunkText,
                    searchQuery = searchQuery,
                    monospaceFont = monospaceFont,
                    isFirst = index == 0,
                    isLast = index == chunks.lastIndex,
                )
            }
        }
    }
}

/** 单个分块正文承托表面：首末块保留圆角，中间块无缝衔接。 */
@Composable
private fun ReaderChunkSurface(
    text: String,
    searchQuery: String,
    monospaceFont: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
) {
    val highlightBg = MaterialTheme.colorScheme.primaryContainer
    val highlightFg = MaterialTheme.colorScheme.onPrimaryContainer
    val annotatedText = remember(text, searchQuery, highlightBg, highlightFg) {
        buildHighlightedInspectorText(
            text = text,
            query = searchQuery,
            highlightBackground = highlightBg,
            highlightForeground = highlightFg,
        )
    }
    // 仅首末块保留圆角和外缘留白，中间块零间距衔接以隐藏内部渲染分块边界
    val shape = when {
        isFirst && isLast -> RoundedCornerShape(14.dp)
        isFirst -> RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
        isLast -> RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
        else -> RectangleShape
    }
    val topPadding = if (isFirst) 12.dp else 4.dp
    val bottomPadding = if (isLast) 12.dp else 4.dp
    val textStyle = if (monospaceFont) {
        MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.5.sp,
            lineHeight = 20.sp,
        )
    } else {
        MaterialTheme.typography.bodyMedium.copy(
            fontSize = 13.5.sp,
            lineHeight = 21.sp,
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
    ) {
        SelectionContainer {
            Text(
                text = annotatedText,
                modifier = Modifier.padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = topPadding,
                    bottom = bottomPadding,
                ),
                style = textStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
