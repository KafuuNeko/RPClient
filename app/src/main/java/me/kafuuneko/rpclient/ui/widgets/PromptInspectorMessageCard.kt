package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspection
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspectionItem
import me.kafuuneko.rpclient.libs.prompt.model.PromptSource
import me.kafuuneko.rpclient.libs.prompt.model.PromptSourceKind
import me.kafuuneko.rpclient.model.PROMPT_COLLAPSED_SOURCE_GROUP_LIMIT
import me.kafuuneko.rpclient.model.PROMPT_INLINE_EXPAND_CHAR_LIMIT
import me.kafuuneko.rpclient.model.PromptInspectorMessageFilter
import me.kafuuneko.rpclient.model.PromptRoleColors
import me.kafuuneko.rpclient.model.PromptSourceGroup
import me.kafuuneko.rpclient.model.baseLabel
import me.kafuuneko.rpclient.model.buildCompactPreview
import me.kafuuneko.rpclient.model.buildHighlightedInspectorText
import me.kafuuneko.rpclient.model.computeTokenSharePercent
import me.kafuuneko.rpclient.model.countTextLines
import me.kafuuneko.rpclient.model.findSegmentIndexForSource
import me.kafuuneko.rpclient.model.groupPromptSources
import me.kafuuneko.rpclient.model.label
import me.kafuuneko.rpclient.model.matchesMessageFilter
import me.kafuuneko.rpclient.model.resolveRoleColors
import me.kafuuneko.rpclient.ui.widgets.AppCard as Card
import me.kafuuneko.rpclient.ui.widgets.AppSurface as Surface
import me.kafuuneko.rpclient.ui.widgets.PageFilterChip as FilterChip

/**
 * 展示消息列表页签：顶部快速筛选条、全部展开/收起控制以及可折叠消息卡片流。
 */
@Composable
internal fun PromptInspectorMessagesTab(
    inspection: PromptInspection,
    filter: PromptInspectorMessageFilter,
    searchQuery: String,
    expandedItemIndices: Set<Int>,
    expandedSourceIndices: Set<Int>,
    onFilterChange: (PromptInspectorMessageFilter) -> Unit,
    onToggleItemExpanded: (Int) -> Unit,
    onToggleSourcesExpanded: (Int) -> Unit,
    onToggleExpandAll: (List<Int>, Boolean) -> Unit,
    onOpenReader: (itemIndex: Int, initialSegmentIndex: Int?) -> Unit,
    onCopyRequest: (String) -> Unit,
    onPreviewImages: ((List<String>, Int) -> Unit)?,
) {
    // 预先解析所有来源本地化名称以支持按中文/当前语言来源名搜索
    val resolvedLabelsByItem = inspection.items.associate { item ->
        item.index to item.sources.map { source -> source.label() }
    }
    val visibleItems = remember(inspection.items, filter, searchQuery, resolvedLabelsByItem) {
        inspection.items.filter { item ->
            matchesMessageFilter(
                item = item,
                filter = filter,
                query = searchQuery,
                resolvedSourceLabels = resolvedLabelsByItem[item.index].orEmpty(),
            )
        }
    }
    val visibleIndices = remember(visibleItems) { visibleItems.map { item -> item.index } }
    val allVisibleExpanded = visibleIndices.isNotEmpty()
        && visibleIndices.all { index -> index in expandedItemIndices }
    val totalInputTokens = remember(inspection.items) {
        inspection.items.sumOf { item -> item.tokenCount }.coerceAtLeast(1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MessageFilterToolbar(
            items = inspection.items,
            visibleCount = visibleItems.size,
            filter = filter,
            allExpanded = allVisibleExpanded,
            onFilterChange = onFilterChange,
            onToggleExpandAll = { onToggleExpandAll(visibleIndices, !allVisibleExpanded) },
        )
        RpLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (visibleItems.isEmpty()) {
                item(key = "empty_messages") {
                    RpInfoCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.SearchOff,
                        title = stringResource(R.string.prompt_inspector_no_matching_messages),
                        subtitle = stringResource(R.string.prompt_inspector_subtitle),
                    )
                }
            }
            items(items = visibleItems, key = { item -> "msg-${item.index}" }) { item ->
                InspectionMessageCard(
                    item = item,
                    totalInputTokens = totalInputTokens,
                    searchQuery = searchQuery,
                    expanded = item.index in expandedItemIndices,
                    sourcesExpanded = item.index in expandedSourceIndices,
                    onToggleExpanded = { onToggleItemExpanded(item.index) },
                    onToggleSourcesExpanded = { onToggleSourcesExpanded(item.index) },
                    onOpenReader = { segmentIndex -> onOpenReader(item.index, segmentIndex) },
                    onCopyRequest = onCopyRequest,
                    onPreviewImages = onPreviewImages,
                )
            }
        }
    }
}

/** 展示角色/特性筛选芯片条与批量展开控制。 */
@Composable
private fun MessageFilterToolbar(
    items: List<PromptInspectionItem>,
    visibleCount: Int,
    filter: PromptInspectorMessageFilter,
    allExpanded: Boolean,
    onFilterChange: (PromptInspectorMessageFilter) -> Unit,
    onToggleExpandAll: () -> Unit,
) {
    val systemCount = remember(items) { items.count { item -> item.role == LLMMessageRole.System } }
    val userCount = remember(items) { items.count { item -> item.role == LLMMessageRole.User } }
    val assistantCount = remember(items) {
        items.count { item -> item.role == LLMMessageRole.Assistant }
    }
    val worldInfoCount = remember(items) {
        items.count { item -> item.sources.any { source -> source.kind == PromptSourceKind.WorldInfo } }
    }
    val imageCount = remember(items) { items.count { item -> item.images.isNotEmpty() } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        // 横向筛选芯片栏，窄屏下保持单行平滑滚动
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MessageFilterChip(
                selected = filter == PromptInspectorMessageFilter.All,
                label = stringResource(R.string.prompt_inspector_filter_all, items.size),
                onClick = { onFilterChange(PromptInspectorMessageFilter.All) },
            )
            if (systemCount > 0) {
                MessageFilterChip(
                    selected = filter == PromptInspectorMessageFilter.System,
                    label = "SYSTEM ($systemCount)",
                    onClick = { onFilterChange(PromptInspectorMessageFilter.System) },
                )
            }
            if (userCount > 0) {
                MessageFilterChip(
                    selected = filter == PromptInspectorMessageFilter.User,
                    label = "USER ($userCount)",
                    onClick = { onFilterChange(PromptInspectorMessageFilter.User) },
                )
            }
            if (assistantCount > 0) {
                MessageFilterChip(
                    selected = filter == PromptInspectorMessageFilter.Assistant,
                    label = "ASSISTANT ($assistantCount)",
                    onClick = { onFilterChange(PromptInspectorMessageFilter.Assistant) },
                )
            }
            if (worldInfoCount > 0) {
                MessageFilterChip(
                    selected = filter == PromptInspectorMessageFilter.WorldInfo,
                    label = stringResource(R.string.prompt_inspector_filter_world_info, worldInfoCount),
                    onClick = { onFilterChange(PromptInspectorMessageFilter.WorldInfo) },
                )
            }
            if (imageCount > 0) {
                MessageFilterChip(
                    selected = filter == PromptInspectorMessageFilter.Images,
                    label = stringResource(R.string.prompt_inspector_filter_images, imageCount),
                    onClick = { onFilterChange(PromptInspectorMessageFilter.Images) },
                )
            }
        }
        // 工具子栏：左侧显示当前顺序说明，右侧提供一键全展/全收
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${stringResource(R.string.prompt_inspector_final_messages)} ($visibleCount)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (visibleCount > 0) {
                TextButton(
                    onClick = onToggleExpandAll,
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
    }
}

@Composable
private fun MessageFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    // 复用页面透明适配芯片，确保在半透明弹窗下仍保持清晰选态
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        },
        colors = pageFilterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    )
}

/**
 * 展示重构后的单条最终消息卡片：语义角色标识、归组来源胶囊与抽屉、紧凑附件与可折叠正文预览。
 */
@Composable
private fun InspectionMessageCard(
    item: PromptInspectionItem,
    totalInputTokens: Int,
    searchQuery: String,
    expanded: Boolean,
    sourcesExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleSourcesExpanded: () -> Unit,
    onOpenReader: (initialSegmentIndex: Int?) -> Unit,
    onCopyRequest: (String) -> Unit,
    onPreviewImages: ((List<String>, Int) -> Unit)?,
) {
    val roleColors = resolveRoleColors(item.role)
    val tokenShare = remember(item.tokenCount, totalInputTokens) {
        computeTokenSharePercent(item.tokenCount, totalInputTokens)
    }
    val sourceGroups = remember(item.sources) { groupPromptSources(item.sources) }
    val lineCount = remember(item.content) { countTextLines(item.content) }
    val compactPreview = remember(item.content) { buildCompactPreview(item.content) }
    val hasMultipleSegments = item.segments.size > 1
    val isVeryLong = item.content.length > PROMPT_INLINE_EXPAND_CHAR_LIMIT

    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 卡片顶部：角色徽章、Token 占比与快捷操作
            MessageCardHeader(
                item = item,
                roleColors = roleColors,
                tokenShare = tokenShare,
                expanded = expanded,
                onToggleExpanded = onToggleExpanded,
                onCopyRequest = { onCopyRequest(item.content) },
            )
            // 来源胶囊与按需展开的结构化来源抽屉
            if (sourceGroups.isNotEmpty()) {
                GroupedSourceSection(
                    sourceGroups = sourceGroups,
                    expanded = sourcesExpanded,
                    onToggleExpanded = onToggleSourcesExpanded,
                    onSourceClick = { source ->
                        val segmentIndex = findSegmentIndexForSource(item, source)
                        onOpenReader(segmentIndex)
                    },
                )
            }
            // 图片附件紧凑条
            if (item.images.isNotEmpty()) {
                MessageImageAttachmentList(
                    images = item.images,
                    imageTokenCounts = item.imageTokenCounts,
                    onPreviewImages = onPreviewImages,
                )
            }
            // 正文预览/原地展开区
            MessageCardBody(
                content = item.content,
                compactPreview = compactPreview,
                searchQuery = searchQuery,
                expanded = expanded,
                isVeryLong = isVeryLong,
                onClickCollapsed = {
                    if (hasMultipleSegments || isVeryLong) {
                        onOpenReader(null)
                    } else {
                        onToggleExpanded()
                    }
                },
            )
            // 底部元信息与阅读/拆解入口
            MessageCardFooter(
                lineCount = lineCount,
                charCount = item.content.length,
                segmentCount = item.segments.size,
                expanded = expanded,
                isVeryLong = isVeryLong,
                onToggleExpanded = onToggleExpanded,
                onOpenReader = { onOpenReader(null) },
            )
        }
    }
}

/** 渲染消息卡片头部行：左侧色条、序号、角色胶囊、Token 占比、复制和折叠图标。 */
@Composable
private fun MessageCardHeader(
    item: PromptInspectionItem,
    roleColors: PromptRoleColors,
    tokenShare: Int,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onCopyRequest: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpanded),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // 左侧通过语义色条、序号和角色胶囊快速标示发言身份
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .clip(CircleShape)
                    .background(roleColors.accent),
            )
            Text(
                text = "#${item.index}",
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = roleColors.container,
            ) {
                Text(
                    text = item.role.name.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = roleColors.content,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
        // 右侧展示 Token 占比、单条复制按钮和折叠指示箭头
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            ) {
                val tokenText = if (tokenShare > 0) {
                    "${item.tokenCount} Tk · $tokenShare%"
                } else {
                    "${item.tokenCount} Tk"
                }
                Text(
                    text = tokenText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            IconButton(
                onClick = onCopyRequest,
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = stringResource(R.string.copy),
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = stringResource(
                    id = if (expanded) R.string.prompt_inspector_collapse else R.string.prompt_inspector_expand,
                ),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 渲染归组后的来源胶囊与可展开来源抽屉。
 *
 * 折叠态最多显示少量归组胶囊与剩余计数，避免多条世界书拼成长段文字墙；
 * 展开态按类别分层呈现并支持点击单条明细直达阅读器对应分段。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GroupedSourceSection(
    sourceGroups: List<PromptSourceGroup>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSourceClick: ((PromptSource) -> Unit)? = null,
) {
    val hasOverflow = sourceGroups.size > PROMPT_COLLAPSED_SOURCE_GROUP_LIMIT
    val hasAnyDetails = sourceGroups.any { group -> group.hasExpandableDetails }
    val singleDetailedSource = sourceGroups.size == 1
        && sourceGroups.single().sources.size == 1
        && sourceGroups.single().sources.single().detail.isNotBlank()
    val canExpandDrawer = hasOverflow || hasAnyDetails
    val visibleGroups = if (expanded) {
        sourceGroups
    } else {
        sourceGroups.take(PROMPT_COLLAPSED_SOURCE_GROUP_LIMIT)
    }
    val hiddenCount = (sourceGroups.size - visibleGroups.size).coerceAtLeast(0)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // 顶部精简来源胶囊行；仅含单个带明细来源时直接展示紧凑标题，多源场景归组折叠
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            visibleGroups.forEach { group ->
                val isWorldInfo = group.kind == PromptSourceKind.WorldInfo
                val chipLabel = when {
                    group.sources.size > 1 -> stringResource(
                        R.string.prompt_inspector_source_group_count,
                        group.kind.baseLabel(),
                        group.sources.size,
                    )
                    singleDetailedSource -> group.sources.single().label()
                    else -> group.kind.baseLabel()
                }
                SourceGroupChip(
                    text = chipLabel,
                    emphasized = isWorldInfo || group.sources.size > 1,
                    showChevron = group.hasExpandableDetails && !singleDetailedSource,
                    expanded = expanded,
                    clickable = canExpandDrawer,
                    onClick = onToggleExpanded,
                )
            }
            if (!expanded && hiddenCount > 0) {
                SourceGroupChip(
                    text = stringResource(R.string.prompt_inspector_source_more, hiddenCount),
                    emphasized = false,
                    showChevron = true,
                    expanded = false,
                    clickable = true,
                    onClick = onToggleExpanded,
                )
            }
        }
        // 展开后的结构化来源明细抽屉
        AnimatedVisibility(visible = expanded && canExpandDrawer) {
            SourceDetailsDrawer(
                sourceGroups = sourceGroups,
                onSourceClick = onSourceClick,
            )
        }
    }
}

/** 单个来源归组胶囊。 */
@Composable
private fun SourceGroupChip(
    text: String,
    emphasized: Boolean,
    showChevron: Boolean,
    expanded: Boolean,
    clickable: Boolean,
    onClick: () -> Unit,
) {
    // 世界书与多项归组使用主色轻填充突出显示，普通单源保持中性弱化样式
    val containerColor = if (emphasized) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
    }
    val borderColor = if (emphasized) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
    }
    val textColor = if (emphasized) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier.widthIn(max = 220.dp),
        shape = RoundedCornerShape(9.dp),
        color = containerColor,
        border = BorderStroke(width = 0.5.dp, color = borderColor),
        onClick = if (clickable) onClick else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            if (showChevron) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = textColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

/** 结构化来源清单抽屉：简短无明细来源聚合为胶囊行，世界书等含标题来源分层罗列且去重前缀。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceDetailsDrawer(
    sourceGroups: List<PromptSourceGroup>,
    onSourceClick: ((PromptSource) -> Unit)?,
) {
    val totalCount = remember(sourceGroups) {
        sourceGroups.sumOf { group -> group.sources.size }
    }
    val simpleGroups = remember(sourceGroups) {
        sourceGroups.filter { group -> !group.hasExpandableDetails }
    }
    val detailedGroups = remember(sourceGroups) {
        sourceGroups.filter { group -> group.hasExpandableDetails }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.prompt_inspector_sources_drawer_title, totalCount),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 无明细的基础来源保持横向紧凑排布
            if (simpleGroups.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    simpleGroups.forEach { group ->
                        RpMetaPill(text = group.kind.baseLabel())
                    }
                }
            }
            // 多条或带标题的来源按类别分组展示，省略重复的“世界书 · ”前缀
            detailedGroups.forEachIndexed { index, group ->
                if (index > 0 || simpleGroups.isNotEmpty()) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        if (group.kind == PromptSourceKind.WorldInfo) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Text(
                            text = "${group.kind.baseLabel()} (${group.sources.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    group.sources.forEach { source ->
                        val title = source.detail.ifBlank { source.kind.baseLabel() }
                        val rowModifier = if (onSourceClick != null) {
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = { onSourceClick(source) })
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        }
                        Row(
                            modifier = rowModifier,
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
                            )
                            Text(
                                text = title,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (onSourceClick != null) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 渲染紧凑的图片附件条，支持一键打开图片预览器。 */
@Composable
internal fun MessageImageAttachmentList(
    images: List<LLMImageReference>,
    imageTokenCounts: List<Long>,
    onPreviewImages: ((List<String>, Int) -> Unit)?,
) {
    // 将每张图片的参数与预览触发合并为单行紧贴附件条，减少垂直高度占用
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        images.forEachIndexed { index, image ->
            val estimatedTokens = imageTokenCounts.getOrElse(index) { 0L }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                border = BorderStroke(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                ),
                onClick = if (onPreviewImages != null) {
                    {
                        onPreviewImages(
                            images.map { reference -> reference.uuid },
                            index,
                        )
                    }
                } else {
                    null
                },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(
                            R.string.prompt_inspector_image_short,
                            index + 1,
                            image.width,
                            image.height,
                            estimatedTokens,
                        ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (onPreviewImages != null) {
                        Text(
                            text = stringResource(R.string.image_view),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** 渲染卡片正文视口：折叠态为轻量摘要，展开态限制超长文本原地渲染以避免列表卡顿。 */
@Composable
private fun MessageCardBody(
    content: String,
    compactPreview: String,
    searchQuery: String,
    expanded: Boolean,
    isVeryLong: Boolean,
    onClickCollapsed: () -> Unit,
) {
    val highlightBg = MaterialTheme.colorScheme.primaryContainer
    val highlightFg = MaterialTheme.colorScheme.onPrimaryContainer

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                other = if (!expanded) Modifier.clickable(onClick = onClickCollapsed) else Modifier,
            ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f),
        ),
    ) {
        if (content.isBlank()) {
            Text(
                text = stringResource(R.string.prompt_inspector_empty_content),
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Surface
        }
        // 超长正文在卡片内仅截取上限，完整浏览交由分块虚拟化阅读器承载
        val displaySource = when {
            !expanded -> compactPreview
            isVeryLong -> content.take(PROMPT_INLINE_EXPAND_CHAR_LIMIT) + "…"
            else -> content
        }
        val highlightedText = remember(displaySource, searchQuery, highlightBg, highlightFg) {
            buildHighlightedInspectorText(
                text = displaySource,
                query = searchQuery,
                highlightBackground = highlightBg,
                highlightForeground = highlightFg,
            )
        }
        val bodyStyle = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.5.sp,
            lineHeight = 19.sp,
        )
        if (expanded) {
            SelectionContainer {
                Text(
                    text = highlightedText,
                    modifier = Modifier.padding(10.dp),
                    style = bodyStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            Text(
                text = highlightedText,
                modifier = Modifier.padding(10.dp),
                style = bodyStyle,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 渲染卡片底部统计信息与展开/专注阅读入口。 */
@Composable
private fun MessageCardFooter(
    lineCount: Int,
    charCount: Int,
    segmentCount: Int,
    expanded: Boolean,
    isVeryLong: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenReader: () -> Unit,
) {
    val metaText = if (segmentCount > 1) {
        "${stringResource(R.string.prompt_inspector_item_segments_count, segmentCount)} · " +
            stringResource(R.string.prompt_inspector_item_meta_summary, lineCount, charCount)
    } else {
        stringResource(R.string.prompt_inspector_item_meta_summary, lineCount, charCount)
    }
    // 多分段消息突出提示拆解查看入口，单段消息提供简洁阅读入口
    val readerLabel = if (segmentCount > 1) {
        stringResource(R.string.prompt_inspector_open_reader_segments, segmentCount)
    } else {
        stringResource(R.string.prompt_inspector_open_reader)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = metaText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!isVeryLong) {
                TextButton(
                    onClick = onToggleExpanded,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(
                            id = if (expanded) {
                                R.string.prompt_inspector_collapse
                            } else {
                                R.string.prompt_inspector_expand
                            },
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                onClick = onOpenReader,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Fullscreen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = readerLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
