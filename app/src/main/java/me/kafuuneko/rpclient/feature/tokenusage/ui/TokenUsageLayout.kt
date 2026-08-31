package me.kafuuneko.rpclient.feature.tokenusage.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Stream
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.tokenusage.model.TokenUsageGroupItem
import me.kafuuneko.rpclient.feature.tokenusage.model.TokenUsageRecordItem
import me.kafuuneko.rpclient.feature.tokenusage.model.TokenUsageSummaryItem
import me.kafuuneko.rpclient.feature.tokenusage.model.TokenUsageTrendPoint
import me.kafuuneko.rpclient.feature.tokenusage.presentation.TokenUsageChartMode
import me.kafuuneko.rpclient.feature.tokenusage.presentation.TokenUsageDialogState
import me.kafuuneko.rpclient.feature.tokenusage.presentation.TokenUsagePeriod
import me.kafuuneko.rpclient.feature.tokenusage.presentation.TokenUsageUiIntent
import me.kafuuneko.rpclient.feature.tokenusage.presentation.TokenUsageUiState
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.room.entity.LLMTokenUsageSource
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.RankBronzeBadgeColor
import me.kafuuneko.rpclient.ui.theme.RankBronzeTextColor
import me.kafuuneko.rpclient.ui.theme.RankGoldBadgeColor
import me.kafuuneko.rpclient.ui.theme.RankGoldTextColor
import me.kafuuneko.rpclient.ui.theme.RankSilverBadgeColor
import me.kafuuneko.rpclient.ui.theme.RankSilverTextColor
import me.kafuuneko.rpclient.ui.theme.TokenUsageCachedColor
import me.kafuuneko.rpclient.ui.theme.TokenUsageInputColor
import me.kafuuneko.rpclient.ui.theme.TokenUsageLatencyColor
import me.kafuuneko.rpclient.ui.theme.TokenUsageOutputColor
import me.kafuuneko.rpclient.ui.theme.TokenUsageReasoningColor
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpChartDataPoint
import me.kafuuneko.rpclient.ui.widgets.RpChartMode
import me.kafuuneko.rpclient.ui.widgets.RpChartSegment
import me.kafuuneko.rpclient.ui.widgets.RpInfoCard
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpStackedRatioBar
import me.kafuuneko.rpclient.ui.widgets.RpTrendChartCard

/**
 * Token 消耗总览、时序图表、模型排行榜与近期请求明细的 Compose 布局入口。
 *
 * 视觉结构与调度机制：
 * - 顶部 Hero 仪表卡片集中展示消耗总量与分段比例。
 * - 趋势分析卡片复用应用级通用图表组件 RpTrendChartCard。
 * - 模型聚合区升级为带排名徽章与占比进度条的纵向排行榜。
 * - 明细列表采用三段式高信噪比设计，支持平滑展开底层技术调试元数据。
 */
@Composable
fun TokenUsageLayout(
    uiState: TokenUsageUiState,
    emit: TokenUsageUiIntent.() -> Unit = {}
) {
    BackHandler(enabled = uiState is TokenUsageUiState.Normal) { TokenUsageUiIntent.Back.emit() }
    when (uiState) {
        TokenUsageUiState.None -> Unit
        is TokenUsageUiState.Finished -> TokenUsageLayout(uiState.previous) {}
        is TokenUsageUiState.Normal -> {
            NormalView(uiState, emit)
            DialogSwitch(uiState.dialogState, emit)
        }
    }
}

@Composable
private fun NormalView(
    uiState: TokenUsageUiState.Normal,
    emit: TokenUsageUiIntent.() -> Unit
) {
    val hasRecords = uiState.summary.requestCount > 0L
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.token_usage),
                onBack = { TokenUsageUiIntent.Back.emit() },
                actions = {
                    if (hasRecords) {
                        IconButton(onClick = { TokenUsageUiIntent.ShowClearConfirmDialog.emit() }) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.clear_token_usage)
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .navigationBarsPadding()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(top = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                RpPageTitle(
                    title = stringResource(R.string.token_usage),
                    subtitle = stringResource(R.string.token_usage_subtitle)
                )
            }
            item { PeriodFilters(uiState.selectedPeriod, emit) }
            item { HeroSummaryCard(uiState.summary) }

            if (hasRecords) {
                if (uiState.trendPoints.size > 1) {
                    item {
                        val chartPoints = remember(uiState.trendPoints) {
                            uiState.trendPoints.map { it.toChartDataPoint() }
                        }
                        val peakPoint = uiState.trendPoints.maxByOrNull { it.totalTokens }
                        val peakText = if (peakPoint != null && peakPoint.totalTokens > 0L) {
                            stringResource(
                                R.string.token_usage_peak_day,
                                peakPoint.label,
                                peakPoint.totalTokens.formatted()
                            )
                        } else null

                        RpTrendChartCard(
                            title = stringResource(R.string.token_usage_trend),
                            points = chartPoints,
                            selectedKey = uiState.selectedPointKey,
                            chartMode = when (uiState.chartMode) {
                                TokenUsageChartMode.Bar -> RpChartMode.Bar
                                TokenUsageChartMode.Line -> RpChartMode.Line
                            },
                            onSelectPoint = { key -> TokenUsageUiIntent.SelectTrendPoint(key).emit() },
                            onSelectChartMode = { mode ->
                                val target = when (mode) {
                                    RpChartMode.Bar -> TokenUsageChartMode.Bar
                                    RpChartMode.Line -> TokenUsageChartMode.Line
                                }
                                TokenUsageUiIntent.SelectChartMode(target).emit()
                            },
                            barLabel = stringResource(R.string.token_usage_chart_mode_bar),
                            lineLabel = stringResource(R.string.token_usage_chart_mode_line),
                            peakText = peakText,
                            autoScrollKey = uiState.selectedPeriod,
                            tooltipContent = { selectedDataPoint ->
                                val selectedPoint = uiState.trendPoints.firstOrNull { it.key == selectedDataPoint.key }
                                if (selectedPoint != null) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${selectedPoint.key} · ${stringResource(R.string.token_usage_request_count, selectedPoint.requestCount)}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "${stringResource(R.string.token_usage_total)} ${selectedPoint.totalTokens.formatted()}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
                item { RpSectionHeader(stringResource(R.string.token_usage_model_ranking)) }
                itemsIndexed(uiState.groups, key = { _, item -> "${item.model}|${item.endpoint}" }) { index, group ->
                    UsageGroupRankCard(index = index + 1, item = group)
                }
                item { RpSectionHeader(stringResource(R.string.token_usage_recent_requests)) }
                items(uiState.recentRecords, key = { it.id }) { record ->
                    RecentRequestCard(record)
                }
            } else {
                item {
                    RpInfoCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.Numbers,
                        title = stringResource(R.string.token_usage_no_data),
                        subtitle = stringResource(R.string.token_usage_no_data_desc)
                    )
                }
            }

            item {
                RpInfoCard(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Info,
                    title = stringResource(R.string.token_usage_notice_title),
                    subtitle = stringResource(R.string.token_usage_notice_desc)
                )
            }
        }
    }
}

@Composable
private fun PeriodFilters(
    selectedPeriod: TokenUsagePeriod,
    emit: TokenUsageUiIntent.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TokenUsagePeriod.entries.forEach { period ->
            FilterChip(
                selected = period == selectedPeriod,
                onClick = { TokenUsageUiIntent.SelectPeriod(period).emit() },
                label = { Text(stringResource(period.titleRes())) }
            )
        }
    }
}

@Composable
private fun HeroSummaryCard(summary: TokenUsageSummaryItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.token_usage_total),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = summary.totalTokens.formatted(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.token_usage_request_count, summary.requestCount),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (summary.totalTokens > 0L) {
                RpStackedRatioBar(
                    segments = listOf(
                        RpChartSegment(
                            value = summary.inputTokens.toFloat(),
                            color = TokenUsageInputColor,
                            label = "${stringResource(R.string.token_usage_input)} ${(summary.inputRatio * 100).toInt()}%"
                        ),
                        RpChartSegment(
                            value = summary.outputTokens.toFloat(),
                            color = TokenUsageOutputColor,
                            label = "${stringResource(R.string.token_usage_output)} ${(summary.outputRatio * 100).toInt()}%"
                        )
                    )
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryMetricCell(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Storage,
                    title = stringResource(R.string.token_usage_input),
                    value = summary.inputTokens.formatted(),
                    accentColor = TokenUsageInputColor
                )
                SummaryMetricCell(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Stream,
                    title = stringResource(R.string.token_usage_output),
                    value = summary.outputTokens.formatted(),
                    accentColor = TokenUsageOutputColor
                )
            }

            if (summary.cachedInputTokens > 0L || summary.reasoningTokens > 0L) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (summary.cachedInputTokens > 0L) {
                        SummaryMetricCell(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Savings,
                            title = stringResource(R.string.token_usage_cached_input, "").trim(),
                            value = summary.cachedInputTokens.formatted(),
                            accentColor = TokenUsageCachedColor
                        )
                    }
                    if (summary.reasoningTokens > 0L) {
                        SummaryMetricCell(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.AutoGraph,
                            title = stringResource(R.string.token_usage_reasoning, "").trim(),
                            value = summary.reasoningTokens.formatted(),
                            accentColor = TokenUsageReasoningColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricCell(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    accentColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
        border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UsageGroupRankCard(index: Int, item: TokenUsageGroupItem) {
    val rankBadgeBg = when (index) {
        1 -> RankGoldBadgeColor.copy(alpha = 0.18f)
        2 -> RankSilverBadgeColor.copy(alpha = 0.22f)
        3 -> RankBronzeBadgeColor.copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    val rankBadgeText = when (index) {
        1 -> RankGoldTextColor
        2 -> RankSilverTextColor
        3 -> RankBronzeTextColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        )
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = CircleShape,
                    color = rankBadgeBg
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = index.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = rankBadgeText
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.model,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.endpoint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = item.totalTokens.formatted(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${(item.ratio * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(item.ratio.coerceIn(0.01f, 1.0f))
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.token_usage_request_count, item.requestCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${stringResource(R.string.token_usage_input)} ${item.inputTokens.formatted()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TokenUsageInputColor
                    )
                    Text(
                        text = "${stringResource(R.string.token_usage_output)} ${item.outputTokens.formatted()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TokenUsageOutputColor
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentRequestCard(item: TokenUsageRecordItem) {
    var isExpanded by remember { mutableStateOf(false) }
    val mode = stringResource(
        if (item.isStreaming) R.string.token_usage_stream else R.string.token_usage_once
    )
    val inputSource = stringResource(item.inputSource.titleRes())
    val outputSource = stringResource(item.outputSource.titleRes())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        )
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.model,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f)
                    ) {
                        Text(
                            text = item.protocol.shortName(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f))
                ) {
                    Text(
                        text = "+${item.totalTokens.formatted()} Tk",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.createTimeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(text = "·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(
                        Icons.Rounded.ElectricBolt,
                        contentDescription = null,
                        tint = TokenUsageLatencyColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = stringResource(R.string.token_usage_duration, item.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = TokenUsageLatencyColor,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
                Text(text = "·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = mode,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                if (item.cachedInputTokens != null && item.cachedInputTokens > 0L) {
                    Text(text = "·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "💾 ${item.cachedInputTokens.formatted()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TokenUsageCachedColor,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${stringResource(R.string.token_usage_input)} ${item.inputTokens.formatted()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TokenUsageInputColor
                    )
                    Text(
                        text = "${stringResource(R.string.token_usage_output)} ${item.outputTokens.formatted()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TokenUsageOutputColor
                    )
                    if (item.reasoningTokens != null && item.reasoningTokens > 0L) {
                        Text(
                            text = "🧠 ${item.reasoningTokens.formatted()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TokenUsageReasoningColor
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f))
                    Text(
                        text = "Host: ${item.endpoint} (${item.providerName})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                    Text(
                        text = stringResource(R.string.token_usage_source_format, inputSource, outputSource),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                    if (!item.tokenizerName.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.token_usage_tokenizer, item.tokenizerName),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogSwitch(
    dialogState: TokenUsageDialogState,
    emit: TokenUsageUiIntent.() -> Unit
) {
    when (dialogState) {
        TokenUsageDialogState.None -> Unit
        TokenUsageDialogState.ClearConfirm -> AppDangerDialog(
            onDismissRequest = { TokenUsageUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.clear_token_usage),
            message = stringResource(R.string.clear_token_usage_confirm),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { TokenUsageUiIntent.ConfirmClearRecords.emit() }
        )
    }
}

private fun TokenUsagePeriod.titleRes(): Int {
    return when (this) {
        TokenUsagePeriod.Today -> R.string.token_usage_today
        TokenUsagePeriod.LastSevenDays -> R.string.token_usage_last_7_days
        TokenUsagePeriod.LastThirtyDays -> R.string.token_usage_last_30_days
        TokenUsagePeriod.AllTime -> R.string.token_usage_all_time
    }
}

private fun LLMTokenUsageSource.titleRes(): Int {
    return when (this) {
        LLMTokenUsageSource.ProviderReported -> R.string.token_usage_reported
        LLMTokenUsageSource.ModelAwareEstimate -> R.string.token_usage_model_estimate
        LLMTokenUsageSource.ProxyEstimate -> R.string.token_usage_proxy_estimate
    }
}

private fun LLMProviderProtocol.shortName(): String {
    return when (this) {
        LLMProviderProtocol.OpenAICompatible -> "OpenAI"
        LLMProviderProtocol.Gemini -> "Gemini"
        LLMProviderProtocol.AnthropicMessages -> "Anthropic"
    }
}

private fun Long.formatted(): String {
    if (this < 1_000L) {
        return this.toString()
    }
    val (value, unit) = when {
        this >= 1_000_000_000L -> (this / 1_000_000_000.0) to "B"
        this >= 1_000_000L -> (this / 1_000_000.0) to "M"
        else -> (this / 1_000.0) to "K"
    }
    val formattedNumber = "%.2f".format(java.util.Locale.US, value).trimEnd('0').trimEnd('.')
    return "$formattedNumber$unit"
}

private fun TokenUsageTrendPoint.toChartDataPoint(): RpChartDataPoint {
    return RpChartDataPoint(
        key = key,
        label = label,
        value = totalTokens.toFloat(),
        segments = listOf(
            RpChartSegment(outputTokens.toFloat(), TokenUsageOutputColor),
            RpChartSegment(inputTokens.toFloat(), TokenUsageInputColor)
        ),
        isCurrent = isCurrent
    )
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun TokenUsageLayoutPreview() {
    AppTheme(dynamicColor = false) {
        TokenUsageLayout(
            TokenUsageUiState.Normal(
                selectedPeriod = TokenUsagePeriod.LastSevenDays,
                summary = TokenUsageSummaryItem(
                    requestCount = 42,
                    inputTokens = 124_680,
                    outputTokens = 38_320,
                    cachedInputTokens = 45_000,
                    reasoningTokens = 6_200
                ),
                trendPoints = listOf(
                    TokenUsageTrendPoint("2026-08-25", "08-25", 32000, 4000, 2000, 3),
                    TokenUsageTrendPoint("2026-08-26", "08-26", 24000, 8000, 5000, 6),
                    TokenUsageTrendPoint("2026-08-27", "08-27", 18000, 6000, 4000, 5),
                    TokenUsageTrendPoint("2026-08-28", "08-28", 35000, 10000, 15000, 10),
                    TokenUsageTrendPoint("2026-08-29", "08-29", 15000, 5000, 3000, 4),
                    TokenUsageTrendPoint("2026-08-30", "08-30", 8000, 2500, 1000, 2),
                    TokenUsageTrendPoint("2026-08-31", "08-31", 12680, 2820, 15000, 12, isCurrent = true)
                ),
                selectedPointKey = "2026-08-28",
                chartMode = TokenUsageChartMode.Bar,
                groups = listOf(
                    TokenUsageGroupItem("gpt-4o-mini", "api.openai.com", 28, 84_680, 25_320, 30000, 0, ratio = 0.67f),
                    TokenUsageGroupItem("claude-3-5-sonnet", "api.anthropic.com", 14, 40_000, 13_000, 15000, 6200, ratio = 0.33f)
                ),
                recentRecords = listOf(
                    TokenUsageRecordItem(
                        id = 1,
                        createTimeText = "08-31 12:30:00",
                        providerName = "ChatGPT",
                        protocol = LLMProviderProtocol.OpenAICompatible,
                        model = "gpt-4o-mini",
                        endpoint = "api.openai.com",
                        isStreaming = true,
                        inputTokens = 2_048,
                        outputTokens = 512,
                        inputSource = LLMTokenUsageSource.ProviderReported,
                        outputSource = LLMTokenUsageSource.ProviderReported,
                        tokenizerName = null,
                        cachedInputTokens = 1_024,
                        reasoningTokens = null,
                        durationMs = 2_340
                    )
                )
            )
        )
    }
}
