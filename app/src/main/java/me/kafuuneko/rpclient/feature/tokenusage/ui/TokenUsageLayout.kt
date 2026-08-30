package me.kafuuneko.rpclient.feature.tokenusage.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Numbers
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.tokenusage.model.TokenUsageGroupItem
import me.kafuuneko.rpclient.feature.tokenusage.model.TokenUsageRecordItem
import me.kafuuneko.rpclient.feature.tokenusage.model.TokenUsageSummaryItem
import me.kafuuneko.rpclient.feature.tokenusage.presentation.TokenUsageDialogState
import me.kafuuneko.rpclient.feature.tokenusage.presentation.TokenUsagePeriod
import me.kafuuneko.rpclient.feature.tokenusage.presentation.TokenUsageUiIntent
import me.kafuuneko.rpclient.feature.tokenusage.presentation.TokenUsageUiState
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.room.entity.LLMTokenUsageSource
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpInfoCard
import me.kafuuneko.rpclient.ui.widgets.RpMetaRow
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader

/** Token 消耗总览、模型与 Host 聚合和近期请求明细的 Compose 入口。 */
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
        // 页面按总览、聚合和明细自上而下组织，筛选切换只替换完整 UiState
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
            item { SummaryGrid(uiState.summary) }
            item {
                RpInfoCard(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Info,
                    title = stringResource(R.string.token_usage_notice_title),
                    subtitle = stringResource(R.string.token_usage_notice_desc)
                )
            }
            if (!hasRecords) {
                item {
                    RpInfoCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.Numbers,
                        title = stringResource(R.string.token_usage_no_data),
                        subtitle = stringResource(R.string.token_usage_no_data_desc)
                    )
                }
            } else {
                item { RpSectionHeader(stringResource(R.string.token_usage_by_model_host)) }
                items(uiState.groups, key = { "${it.model}|${it.endpoint}" }) {
                    UsageGroupCard(it)
                }
                item { RpSectionHeader(stringResource(R.string.token_usage_recent_requests)) }
                items(uiState.recentRecords, key = { it.id }) { RecentRequestCard(it) }
            }
        }
    }
}

@Composable
private fun PeriodFilters(
    selectedPeriod: TokenUsagePeriod,
    emit: TokenUsageUiIntent.() -> Unit
) {
    // 时间选项允许横向滚动，避免翻译后标签在窄屏被压缩
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
private fun SummaryGrid(summary: TokenUsageSummaryItem) {
    // 固定双列卡片在窄屏下保持相同宽度和清晰的输入、输出对照
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.token_usage_requests),
                value = summary.requestCount.formatted(),
                icon = Icons.Rounded.Schedule,
                accent = Color(0xFF0EA5E9)
            )
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.token_usage_total),
                value = summary.totalTokens.formatted(),
                icon = Icons.Rounded.Numbers,
                accent = Color(0xFF8B5CF6)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.token_usage_input),
                value = summary.inputTokens.formatted(),
                icon = Icons.Rounded.Storage,
                accent = Color(0xFF10B981)
            )
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.token_usage_output),
                value = summary.outputTokens.formatted(),
                icon = Icons.Rounded.Stream,
                accent = Color(0xFFF59E0B)
            )
        }
    }
}

@Composable
private fun SummaryCard(
    modifier: Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color
) {
    // 图标颜色只用于区分指标，不承载状态或告警语义
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, accent.copy(alpha = 0.24f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UsageGroupCard(item: TokenUsageGroupItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        )
    ) {
        // 聚合卡片突出实际模型，Host 和三类用量保持为次级信息
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.model,
                        style = MaterialTheme.typography.titleMedium,
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
                Text(
                    stringResource(R.string.token_usage_request_count, item.requestCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
            RpMetaRow(
                listOf(
                    stringResource(R.string.token_usage_input_value, item.inputTokens.formatted()),
                    stringResource(R.string.token_usage_output_value, item.outputTokens.formatted()),
                    stringResource(R.string.token_usage_total_value, item.totalTokens.formatted())
                )
            )
        }
    }
}

@Composable
private fun RecentRequestCard(item: TokenUsageRecordItem) {
    val mode = stringResource(
        if (item.isStreaming) R.string.token_usage_stream else R.string.token_usage_once
    )
    val inputSource = stringResource(item.inputSource.titleRes())
    val outputSource = stringResource(item.outputSource.titleRes())
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        )
    ) {
        // 明细不包含请求正文和 URL 路径，仅展示排查统计来源所需的脱敏元数据
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
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
            RpMetaRow(
                listOf(
                    item.createTimeText,
                    item.providerName,
                    item.protocol.name,
                    mode,
                    stringResource(R.string.token_usage_duration, item.durationMs)
                )
            )
            RpMetaRow(
                buildList {
                    add(stringResource(R.string.token_usage_input_value, item.inputTokens.formatted()))
                    add(stringResource(R.string.token_usage_output_value, item.outputTokens.formatted()))
                    add(stringResource(R.string.token_usage_total_value, item.totalTokens.formatted()))
                    item.cachedInputTokens?.let {
                        add(stringResource(R.string.token_usage_cached_input, it.formatted()))
                    }
                    item.reasoningTokens?.let {
                        add(stringResource(R.string.token_usage_reasoning, it.formatted()))
                    }
                }
            )
            Text(
                text = stringResource(
                    R.string.token_usage_source_format,
                    inputSource,
                    outputSource
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
            )
            item.tokenizerName?.let { tokenizerName ->
                Text(
                    text = stringResource(R.string.token_usage_tokenizer, tokenizerName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
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

private fun Long.formatted(): String = NumberFormat.getIntegerInstance().format(this)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun TokenUsageLayoutPreview() {
    AppTheme(dynamicColor = false) {
        // 预览同时覆盖汇总、聚合以及带服务端明细的单次请求
        TokenUsageLayout(
            TokenUsageUiState.Normal(
                selectedPeriod = TokenUsagePeriod.LastSevenDays,
                summary = TokenUsageSummaryItem(12, 24_680, 8_320),
                groups = listOf(
                    TokenUsageGroupItem("gpt-4o-mini", "api.openai.com", 12, 24_680, 8_320)
                ),
                recentRecords = listOf(
                    TokenUsageRecordItem(
                        id = 1,
                        createTimeText = "08-30 12:30:00",
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
