package me.kafuuneko.rpclient.ui.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspection
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspectionItem
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspectionSegment
import me.kafuuneko.rpclient.libs.prompt.model.PromptOmissionReason
import me.kafuuneko.rpclient.libs.prompt.model.PromptOmittedItem
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.model.PromptSource
import me.kafuuneko.rpclient.libs.prompt.model.PromptSourceKind
import me.kafuuneko.rpclient.libs.prompt.model.PromptTokenizerStrategy
import me.kafuuneko.rpclient.libs.regex.RegexExecutionError
import me.kafuuneko.rpclient.libs.regex.RegexExecutionHit
import me.kafuuneko.rpclient.model.PromptInspectorMessageFilter
import me.kafuuneko.rpclient.model.PromptInspectorTab
import me.kafuuneko.rpclient.model.computeTokenSharePercent
import me.kafuuneko.rpclient.model.formatFullPromptForCopy
import me.kafuuneko.rpclient.model.label
import me.kafuuneko.rpclient.model.summarizeRoleTokens
import me.kafuuneko.rpclient.ui.theme.AccentEmeraldColor
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.TokenUsageOutputColor
import me.kafuuneko.rpclient.ui.widgets.PromptInspectorMessagesTab
import me.kafuuneko.rpclient.ui.widgets.PromptInspectorReaderView
import me.kafuuneko.rpclient.ui.widgets.RpChartSegment
import me.kafuuneko.rpclient.ui.widgets.RpInfoCard
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpMetaPill
import me.kafuuneko.rpclient.ui.widgets.RpMetaRow
import me.kafuuneko.rpclient.ui.widgets.RpSegmentedTabs
import me.kafuuneko.rpclient.ui.widgets.RpStackedRatioBar
import me.kafuuneko.rpclient.ui.widgets.AppCard as Card
import me.kafuuneko.rpclient.ui.widgets.AppSurface as Surface
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField

/**
 * 展示最终 Prompt、来源分段拆解、预算分布、裁剪明细和 Regex 执行记录的调试对话框。
 *
 * 核心交互能力：
 * - 通过分段 Tab 隔离消息正文、预算与裁剪概览以及 Regex 记录，减少首屏噪音；
 * - 消息卡片默认折叠并归组折叠多来源标签，避免多条世界书拼成长段文字墙；
 * - 支持关键词搜索、角色与类型筛选，以及进入独立的长消息分块阅读器按来源拆解查看。
 *
 * @param inspection 本次请求生成的完整 Prompt 检查报告
 * @param onDismissRequest 关闭检查器对话框回调
 * @param onCopyRequest 复制单段、单条或完整 Prompt 文本回调
 * @param onPreviewImages 可选的消息图片预览回调
 */
@Composable
fun PromptInspectorDialog(
    inspection: PromptInspection,
    onDismissRequest: () -> Unit,
    onCopyRequest: (String) -> Unit,
    onPreviewImages: ((List<String>, Int) -> Unit)? = null,
) {
    val hasRegexActivity = inspection.regexExecutions.isNotEmpty()
        || inspection.regexErrors.isNotEmpty()
    val availableTabs = remember(hasRegexActivity) {
        buildList {
            add(PromptInspectorTab.Messages)
            add(PromptInspectorTab.Overview)
            if (hasRegexActivity) add(PromptInspectorTab.Regex)
        }
    }
    var selectedTab by remember(inspection) { mutableStateOf(PromptInspectorTab.Messages) }
    var searchVisible by remember(inspection) { mutableStateOf(false) }
    var searchQuery by remember(inspection) { mutableStateOf("") }
    var messageFilter by remember(inspection) { mutableStateOf(PromptInspectorMessageFilter.All) }
    var expandedItemIndices by remember(inspection) { mutableStateOf(emptySet<Int>()) }
    var expandedSourceIndices by remember(inspection) { mutableStateOf(emptySet<Int>()) }
    var readerTargetIndex by remember(inspection) { mutableStateOf<Int?>(null) }
    var readerInitialSegment by remember(inspection) { mutableStateOf<Int?>(null) }
    val activeReaderItem = remember(inspection.items, readerTargetIndex) {
        readerTargetIndex?.let { target ->
            inspection.items.firstOrNull { item -> item.index == target }
        }
    }
    val totalInputTokens = remember(inspection.items) {
        inspection.items.sumOf { item -> item.tokenCount }.coerceAtLeast(1)
    }

    // 处于单条阅读器内按系统返回键时先退回消息列表，不直接销毁整个检查器
    AppDialogWindow(
        onDismissRequest = {
            if (activeReaderItem != null) {
                readerTargetIndex = null
                readerInitialSegment = null
            } else {
                onDismissRequest()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        windowInset = 12.dp,
    ) {
        DialogPanelSurface(modifier = Modifier.fillMaxSize()) {
            if (activeReaderItem != null) {
                PromptInspectorReaderView(
                    item = activeReaderItem,
                    totalInputTokens = totalInputTokens,
                    initialQuery = searchQuery,
                    initialSegmentIndex = readerInitialSegment,
                    onBack = {
                        readerTargetIndex = null
                        readerInitialSegment = null
                    },
                    onClose = onDismissRequest,
                    onCopyRequest = onCopyRequest,
                    onPreviewImages = onPreviewImages,
                )
            } else {
                PromptInspectorMainView(
                    inspection = inspection,
                    availableTabs = availableTabs,
                    selectedTab = selectedTab,
                    searchVisible = searchVisible,
                    searchQuery = searchQuery,
                    messageFilter = messageFilter,
                    expandedItemIndices = expandedItemIndices,
                    expandedSourceIndices = expandedSourceIndices,
                    onSelectTab = { tab -> selectedTab = tab },
                    onToggleSearch = {
                        if (searchVisible && searchQuery.isNotBlank()) {
                            searchQuery = ""
                        }
                        searchVisible = !searchVisible
                        if (searchVisible) selectedTab = PromptInspectorTab.Messages
                    },
                    onSearchQueryChange = { value -> searchQuery = value },
                    onFilterChange = { value -> messageFilter = value },
                    onToggleItemExpanded = { index ->
                        expandedItemIndices = if (index in expandedItemIndices) {
                            expandedItemIndices - index
                        } else {
                            expandedItemIndices + index
                        }
                    },
                    onToggleSourcesExpanded = { index ->
                        expandedSourceIndices = if (index in expandedSourceIndices) {
                            expandedSourceIndices - index
                        } else {
                            expandedSourceIndices + index
                        }
                    },
                    onToggleExpandAll = { indices, expand ->
                        expandedItemIndices = if (expand) {
                            expandedItemIndices + indices
                        } else {
                            expandedItemIndices - indices.toSet()
                        }
                    },
                    onOpenReader = { itemIndex, segmentIndex ->
                        readerTargetIndex = itemIndex
                        readerInitialSegment = segmentIndex
                    },
                    onCopyAll = { onCopyRequest(formatFullPromptForCopy(inspection)) },
                    onCopyRequest = onCopyRequest,
                    onPreviewImages = onPreviewImages,
                    onDismissRequest = onDismissRequest,
                )
            }
        }
    }
}

/** 组织检查器主视图：紧凑顶栏、分段 Tab、搜索栏与当前激活的内容页签。 */
@Composable
private fun PromptInspectorMainView(
    inspection: PromptInspection,
    availableTabs: List<PromptInspectorTab>,
    selectedTab: PromptInspectorTab,
    searchVisible: Boolean,
    searchQuery: String,
    messageFilter: PromptInspectorMessageFilter,
    expandedItemIndices: Set<Int>,
    expandedSourceIndices: Set<Int>,
    onSelectTab: (PromptInspectorTab) -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (PromptInspectorMessageFilter) -> Unit,
    onToggleItemExpanded: (Int) -> Unit,
    onToggleSourcesExpanded: (Int) -> Unit,
    onToggleExpandAll: (List<Int>, Boolean) -> Unit,
    onOpenReader: (itemIndex: Int, initialSegmentIndex: Int?) -> Unit,
    onCopyAll: () -> Unit,
    onCopyRequest: (String) -> Unit,
    onPreviewImages: ((List<String>, Int) -> Unit)?,
    onDismissRequest: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        InspectorHeader(
            inspection = inspection,
            searchActive = searchVisible || searchQuery.isNotBlank(),
            onToggleSearch = onToggleSearch,
            onCopyAll = onCopyAll,
            onDismissRequest = onDismissRequest,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

        // 复用统一圆角分段 Tab 隔离消息、概览与正则诊断
        RpSegmentedTabs(
            tabs = availableTabs,
            selectedTab = selectedTab,
            onTabSelected = onSelectTab,
            tabLabel = { tab ->
                when (tab) {
                    PromptInspectorTab.Messages -> stringResource(
                        R.string.prompt_inspector_tab_messages,
                        inspection.items.size,
                    )

                    PromptInspectorTab.Overview -> if (inspection.omittedItems.isNotEmpty()) {
                        stringResource(
                            R.string.prompt_inspector_tab_overview_omitted,
                            inspection.omittedItems.size,
                        )
                    } else {
                        stringResource(R.string.prompt_inspector_tab_overview)
                    }

                    PromptInspectorTab.Regex -> stringResource(
                        R.string.prompt_inspector_tab_regex,
                        inspection.regexExecutions.size + inspection.regexErrors.size,
                    )
                }
            },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )

        // 搜索框仅在消息页签开启搜索时占位，保持默认首屏轻量
        if (selectedTab == PromptInspectorTab.Messages && searchVisible) {
            InspectorSearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
            )
        }

        when (selectedTab) {
            PromptInspectorTab.Messages -> PromptInspectorMessagesTab(
                inspection = inspection,
                filter = messageFilter,
                searchQuery = searchQuery,
                expandedItemIndices = expandedItemIndices,
                expandedSourceIndices = expandedSourceIndices,
                onFilterChange = onFilterChange,
                onToggleItemExpanded = onToggleItemExpanded,
                onToggleSourcesExpanded = onToggleSourcesExpanded,
                onToggleExpandAll = onToggleExpandAll,
                onOpenReader = onOpenReader,
                onCopyRequest = onCopyRequest,
                onPreviewImages = onPreviewImages,
            )

            PromptInspectorTab.Overview -> PromptInspectorOverviewTab(inspection = inspection)

            PromptInspectorTab.Regex -> PromptInspectorRegexTab(inspection = inspection)
        }
    }
}

/** 渲染紧凑顶栏，适配半透明弹窗控件不透明度规范。 */
@Composable
private fun InspectorHeader(
    inspection: PromptInspection,
    searchActive: Boolean,
    onToggleSearch: () -> Unit,
    onCopyAll: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 徽标底色经 dialogControlColor 适配，确保弹窗半透明主题下风格一致
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    color = dialogControlColor(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.DataObject,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        // 中央保留单行主标题与 Token 总量概览，右侧提供搜索、复制全部和关闭操作
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.prompt_inspector_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.prompt_inspector_header_stats,
                    inspection.items.size,
                    inspection.finalTokenCount,
                    inspection.promptBudget,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggleSearch) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.prompt_inspector_search_placeholder),
                tint = if (searchActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        IconButton(onClick = onCopyAll) {
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = stringResource(R.string.prompt_inspector_copy_all),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDismissRequest) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.close),
            )
        }
    }
}

/** 渲染消息列表搜索输入框。 */
@Composable
private fun InspectorSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    // 单行搜索框在输入非空时展示一键清空图标，避免遮挡下方消息列表
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 6.dp),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        placeholder = {
            Text(
                text = stringResource(R.string.prompt_inspector_search_placeholder),
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
}

/** 展示概览与预算裁剪页签：上下文配额堆叠条、角色分布、环境胶囊与被移除项列表。 */
@Composable
private fun PromptInspectorOverviewTab(inspection: PromptInspection) {
    // 依次呈现上下文预算仪表盘、角色 Token 分布与预算裁剪明细
    RpLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "budget_summary") {
            InspectionSummaryCard(inspection = inspection)
        }
        if (inspection.items.isNotEmpty()) {
            item(key = "role_breakdown") {
                RoleTokenDistributionCard(items = inspection.items)
            }
        }
        if (inspection.omittedItems.isNotEmpty()) {
            item(key = "omitted_header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(
                            R.string.prompt_inspector_omitted_title,
                            inspection.omittedItems.size,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            items(inspection.omittedItems) { omittedItem ->
                OmittedItemCard(item = omittedItem)
            }
        } else {
            item(key = "no_omitted") {
                RpInfoCard(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.CheckCircleOutline,
                    title = stringResource(R.string.prompt_inspector_omitted_title, 0),
                    subtitle = stringResource(R.string.prompt_inspector_no_omitted),
                )
            }
        }
    }
}

/** 渲染上下文预算仪表盘卡片，使用三段式堆叠比例条区分已用、剩余与回复预留。 */
@Composable
private fun InspectionSummaryCard(inspection: PromptInspection) {
    val budgetUsagePercent = computeTokenSharePercent(
        partTokens = inspection.finalTokenCount,
        totalTokens = inspection.promptBudget.coerceAtLeast(1),
    )
    val remainingBudget = (inspection.promptBudget - inspection.finalTokenCount).coerceAtLeast(0)
    // 输入预算占比超过九成时切换为错误警戒色，提醒用户留意上下文裁剪风险
    val usedColor = if (budgetUsagePercent > 90) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val segments = listOf(
        RpChartSegment(
            value = inspection.finalTokenCount.coerceAtLeast(0).toFloat(),
            color = usedColor,
            label = stringResource(
                R.string.prompt_inspector_budget_used_label,
                inspection.finalTokenCount,
            ),
        ),
        RpChartSegment(
            value = remainingBudget.toFloat(),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            label = stringResource(
                R.string.prompt_inspector_budget_remaining_label,
                remainingBudget,
            ),
        ),
        RpChartSegment(
            value = inspection.responseReserve.coerceAtLeast(0).toFloat(),
            color = TokenUsageOutputColor,
            label = stringResource(
                R.string.prompt_inspector_budget_reserve_label,
                inspection.responseReserve,
            ),
        ),
    )
    val metaItems = listOf(
        stringResource(
            R.string.prompt_inspector_model,
            inspection.model.ifBlank { stringResource(R.string.prompt_unknown_model) },
        ),
        stringResource(
            R.string.prompt_inspector_tokenizer,
            inspection.tokenizerName,
            tokenizerStrategyLabel(inspection),
        ),
        stringResource(
            R.string.prompt_inspector_post_processing,
            inspection.postProcessingMode.name,
        ),
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(
                        R.string.prompt_inspector_token_summary,
                        inspection.finalTokenCount,
                        inspection.promptBudget,
                        inspection.responseReserve,
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = usedColor,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = usedColor.copy(alpha = 0.14f),
                ) {
                    Text(
                        text = "$budgetUsagePercent%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = usedColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            RpStackedRatioBar(
                segments = segments,
                modifier = Modifier.fillMaxWidth(),
                barHeight = 8.dp,
            )
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            )
            RpMetaRow(items = metaItems)
        }
    }
}

/** 渲染按消息角色统计的 Token 占比分布卡片。 */
@Composable
private fun RoleTokenDistributionCard(items: List<PromptInspectionItem>) {
    val stats = remember(items) { summarizeRoleTokens(items) }
    val totalTokens = remember(stats) {
        stats.sumOf { stat -> stat.tokenCount }.coerceAtLeast(1)
    }
    // 将 System、User、Assistant 合计消耗映射为分段比例条与角色明细胶囊
    val segments = stats.map { stat ->
        val roleColor = when (stat.role) {
            LLMMessageRole.System -> MaterialTheme.colorScheme.primary
            LLMMessageRole.User -> AccentEmeraldColor
            LLMMessageRole.Assistant -> TokenUsageOutputColor
        }
        val share = computeTokenSharePercent(stat.tokenCount, totalTokens)
        RpChartSegment(
            value = stat.tokenCount.toFloat(),
            color = roleColor,
            label = "${stat.role.name.uppercase()} $share%",
        )
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.prompt_inspector_role_breakdown_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            RpStackedRatioBar(
                segments = segments,
                modifier = Modifier.fillMaxWidth(),
                barHeight = 8.dp,
            )
            RpMetaRow(
                items = stats.map { stat ->
                    "${stat.role.name.uppercase()} · ${stat.messageCount} · ${stat.tokenCount} Tk"
                },
            )
        }
    }
}

@Composable
private fun tokenizerStrategyLabel(inspection: PromptInspection): String {
    val tokenizerStrategyResId = when (inspection.tokenizerStrategy) {
        PromptTokenizerStrategy.ModelAware -> R.string.prompt_tokenizer_model_aware
        PromptTokenizerStrategy.Estimated -> R.string.prompt_tokenizer_estimated
        PromptTokenizerStrategy.Conservative -> R.string.prompt_tokenizer_conservative
    }
    val strategy = stringResource(id = tokenizerStrategyResId)
    if (inspection.tokenizerReservePercent <= 0) return strategy
    return "$strategy · ${
        stringResource(
            R.string.prompt_tokenizer_reserve,
            inspection.tokenizerReservePercent,
        )
    }"
}

/** 渲染单个被预算裁剪移除的条目卡片。 */
@Composable
private fun OmittedItemCard(item: PromptOmittedItem) {
    // 根据预算或图片约束类型映射对应的本地化移除原因
    val omissionReasonResId = when (item.reason) {
        PromptOmissionReason.WorldInfoBudget -> R.string.prompt_omission_world_info_budget
        PromptOmissionReason.ImageCount -> R.string.image_error_count
        PromptOmissionReason.RequestBytes -> R.string.image_error_large
        else -> R.string.prompt_omission_context_budget
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.32f),
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.20f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = item.source.label(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                ) {
                    Text(
                        text = stringResource(
                            R.string.prompt_inspector_item_tokens,
                            item.tokenCount
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Text(
                text = stringResource(id = omissionReasonResId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/** 展示 Regex 执行与错误记录页签。 */
@Composable
private fun PromptInspectorRegexTab(inspection: PromptInspection) {
    // 错误记录优先置顶展示，便于先排查失效脚本再核对正常命中日志
    RpLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (inspection.regexErrors.isEmpty() && inspection.regexExecutions.isEmpty()) {
            item(key = "regex_empty") {
                RpInfoCard(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Code,
                    title = stringResource(R.string.prompt_inspector_regex_title, 0),
                    subtitle = stringResource(R.string.prompt_inspector_no_regex),
                )
            }
            return@RpLazyColumn
        }
        if (inspection.regexErrors.isNotEmpty()) {
            item(key = "regex_errors_header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(
                            R.string.prompt_inspector_regex_errors,
                            inspection.regexErrors.size,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            items(inspection.regexErrors) { error ->
                RegexErrorCard(error = error)
            }
        }
        if (inspection.regexExecutions.isNotEmpty()) {
            item(key = "regex_hits_header") {
                Text(
                    text = stringResource(
                        R.string.prompt_inspector_regex_title,
                        inspection.regexExecutions.size,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(inspection.regexExecutions) { hit ->
                RegexExecutionCard(hit = hit)
            }
        }
    }
}

/** 渲染单条结构化的 Regex 执行命中卡片。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegexExecutionCard(hit: RegexExecutionHit) {
    // 顶部突出脚本名与是否改写状态，底部以胶囊行拆解作用域、位置与执行模式
    val statusText = stringResource(
        id = if (hit.changed) R.string.regex_changed else R.string.regex_matched,
    )
    val persistenceText = stringResource(
        id = if (hit.persisted) R.string.regex_persisted else R.string.regex_temporary,
    )

    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (hit.changed) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
            },
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = hit.scriptName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (hit.changed) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    },
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (hit.changed) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RpMetaPill(text = hit.scope.name)
                RpMetaPill(text = hit.placement.name)
                RpMetaPill(text = hit.mode.name)
                RpMetaPill(text = persistenceText)
            }
        }
    }
}

/** 渲染单条 Regex 执行错误卡片。 */
@Composable
private fun RegexErrorCard(error: RegexExecutionError) {
    // 使用错误容器底板与边框凸显执行失败的正则脚本名称
    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = error.scriptName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.regex_execution_invalid),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Preview(
    name = "PromptInspectorDialog - Dark",
    widthDp = 390,
    heightDp = 820,
    showBackground = true
)
@Composable
private fun PromptInspectorDialogDarkPreview() {
    AppTheme(darkTheme = true, dynamicColor = false) {
        DialogPanelSurface(modifier = Modifier.fillMaxSize()) {
            PromptInspectorMainView(
                inspection = previewPromptInspection,
                availableTabs = listOf(PromptInspectorTab.Messages, PromptInspectorTab.Overview),
                selectedTab = PromptInspectorTab.Messages,
                searchVisible = false,
                searchQuery = "",
                messageFilter = PromptInspectorMessageFilter.All,
                expandedItemIndices = emptySet(),
                expandedSourceIndices = setOf(1),
                onSelectTab = {},
                onToggleSearch = {},
                onSearchQueryChange = {},
                onFilterChange = {},
                onToggleItemExpanded = {},
                onToggleSourcesExpanded = {},
                onToggleExpandAll = { _, _ -> },
                onOpenReader = { _, _ -> },
                onCopyAll = {},
                onCopyRequest = {},
                onPreviewImages = null,
                onDismissRequest = {},
            )
        }
    }
}

@Preview(
    name = "PromptInspectorReader - Segments",
    widthDp = 390,
    heightDp = 820,
    showBackground = true
)
@Composable
private fun PromptInspectorReaderPreview() {
    AppTheme(darkTheme = true, dynamicColor = false) {
        DialogPanelSurface(modifier = Modifier.fillMaxSize()) {
            PromptInspectorReaderView(
                item = previewPromptInspection.items.first(),
                totalInputTokens = previewPromptInspection.finalTokenCount,
                initialQuery = "",
                initialSegmentIndex = null,
                onBack = {},
                onClose = {},
                onCopyRequest = {},
                onPreviewImages = null,
            )
        }
    }
}

private val previewPromptInspection = PromptInspection(
    model = "claude-sonnet-4",
    tokenizerName = "CL100K proxy",
    tokenizerStrategy = PromptTokenizerStrategy.Estimated,
    tokenizerReservePercent = 15,
    postProcessingMode = PromptPostProcessingMode.SemiStrict,
    contextLimit = 16_384,
    responseReserve = 2_048,
    promptBudget = 14_336,
    finalTokenCount = 6_920,
    items = listOf(
        PromptInspectionItem(
            index = 1,
            role = LLMMessageRole.System,
            sources = listOf(
                PromptSource(PromptSourceKind.MainPrompt),
                PromptSource(PromptSourceKind.Summary),
                PromptSource(PromptSourceKind.WorldInfo, "【常驻-视觉基准】角色外貌与物理状态", 1L),
                PromptSource(PromptSourceKind.WorldInfo, "【全局基调】叙事节奏与自驱引擎", 2L),
                PromptSource(PromptSourceKind.WorldInfo, "【地图-边境都市】上中下三城区设定", 3L),
                PromptSource(PromptSourceKind.UserPersona),
                PromptSource(PromptSourceKind.CharacterDescription),
            ),
            tokenCount = 6_589,
            content = "Write the character's next reply in a fictional chat.\n\n[Summary]\nRecent events.\n\n[World Info]\nDetailed world rules.",
            segments = listOf(
                PromptInspectionSegment(
                    sources = listOf(PromptSource(PromptSourceKind.MainPrompt)),
                    content = "Write the character's next reply in a fictional chat.",
                    tokenCount = 420,
                ),
                PromptInspectionSegment(
                    sources = listOf(PromptSource(PromptSourceKind.Summary)),
                    content = "[Summary]\nRecent events.",
                    tokenCount = 680,
                ),
                PromptInspectionSegment(
                    sources = listOf(
                        PromptSource(
                            PromptSourceKind.WorldInfo,
                            "【常驻-视觉基准】角色外貌与物理状态",
                            1L
                        ),
                    ),
                    content = "Detailed appearance and wardrobe baseline.",
                    tokenCount = 1_850,
                ),
            ),
        ),
        PromptInspectionItem(
            index = 2,
            role = LLMMessageRole.User,
            sources = listOf(PromptSource(PromptSourceKind.ChatHistory)),
            tokenCount = 331,
            content = "The rain begins to fall over the lower district.",
            segments = listOf(
                PromptInspectionSegment(
                    sources = listOf(PromptSource(PromptSourceKind.ChatHistory)),
                    content = "The rain begins to fall over the lower district.",
                    tokenCount = 320,
                ),
            ),
        ),
    ),
    omittedItems = listOf(
        PromptOmittedItem(
            source = PromptSource(PromptSourceKind.ChatHistory, "Old message"),
            tokenCount = 412,
            reason = PromptOmissionReason.ContextBudget,
        ),
    ),
)
