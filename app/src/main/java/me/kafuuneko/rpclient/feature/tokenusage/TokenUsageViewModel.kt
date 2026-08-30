package me.kafuuneko.rpclient.feature.tokenusage

import java.time.Instant
import java.time.ZoneId
import me.kafuuneko.rpclient.feature.tokenusage.model.TokenUsageGroupItem
import me.kafuuneko.rpclient.feature.tokenusage.model.TokenUsageRecordItem
import me.kafuuneko.rpclient.feature.tokenusage.model.TokenUsageSummaryItem
import me.kafuuneko.rpclient.feature.tokenusage.presentation.TokenUsageDialogState
import me.kafuuneko.rpclient.feature.tokenusage.presentation.TokenUsagePeriod
import me.kafuuneko.rpclient.feature.tokenusage.presentation.TokenUsageUiIntent
import me.kafuuneko.rpclient.feature.tokenusage.presentation.TokenUsageUiState
import me.kafuuneko.rpclient.libs.core.CoreViewModel
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.entity.LLMTokenUsageRecord
import me.kafuuneko.rpclient.libs.room.model.LLMTokenUsageGroup
import me.kafuuneko.rpclient.libs.room.repository.LLMTokenUsageRepository
import me.kafuuneko.rpclient.utils.formatTimestamp
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 消耗统计页状态持有者。
 *
 * - 根据本地时区计算今天、近七天和近三十天的查询边界。
 * - 通过 Repository 读取汇总、模型与 Host 聚合以及近期请求明细。
 * - 驱动时间筛选、清空确认和页面结束状态。
 */
class TokenUsageViewModel : CoreViewModel<TokenUsageUiIntent, TokenUsageUiState>(
    TokenUsageUiState.None
), KoinComponent {
    private val mRepository by inject<LLMTokenUsageRepository>()

    /** 初始化页面并默认展示近七天统计。 */
    @UiIntentObserver(TokenUsageUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<TokenUsageUiState.None>()) return
        loadDashboard(TokenUsagePeriod.LastSevenDays)
    }

    /** 切换时间范围并重新查询聚合结果。 */
    @UiIntentObserver(TokenUsageUiIntent.SelectPeriod::class)
    private suspend fun onSelectPeriod(intent: TokenUsageUiIntent.SelectPeriod) {
        val uiState = getOrNull<TokenUsageUiState.Normal>() ?: return
        if (uiState.selectedPeriod == intent.period) return
        loadDashboard(intent.period)
    }

    /** 处理返回操作，保留最后一个可渲染状态。 */
    @UiIntentObserver(TokenUsageUiIntent.Back::class)
    private fun onBack() {
        if (isStateOf<TokenUsageUiState.Finished>()) return
        TokenUsageUiState.finished(uiStateFlow.value).setup()
    }

    /** 显示清空全部统计的二次确认。 */
    @UiIntentObserver(TokenUsageUiIntent.ShowClearConfirmDialog::class)
    private fun onShowClearConfirmDialog() {
        val uiState = getOrNull<TokenUsageUiState.Normal>() ?: return
        uiState.copy(dialogState = TokenUsageDialogState.ClearConfirm).setup()
    }

    /** 清空全部统计记录，并刷新当前时间范围。 */
    @UiIntentObserver(TokenUsageUiIntent.ConfirmClearRecords::class)
    private suspend fun onConfirmClearRecords() {
        val uiState = getOrNull<TokenUsageUiState.Normal>() ?: return
        mRepository.deleteAll()
        loadDashboard(uiState.selectedPeriod)
    }

    /** 关闭当前确认对话框。 */
    @UiIntentObserver(TokenUsageUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<TokenUsageUiState.Normal>() ?: return
        uiState.copy(dialogState = TokenUsageDialogState.None).setup()
    }

    /** 查询指定时间范围，并把持久化模型转换为页面状态树。 */
    private suspend fun loadDashboard(period: TokenUsagePeriod) {
        val dashboard = mRepository.getDashboard(period.startTimeMillis())
        // 聚合查询只返回领域数据，页面展示所需的时间与 endpoint 文本在此统一转换
        TokenUsageUiState.Normal(
            selectedPeriod = period,
            summary = TokenUsageSummaryItem(
                requestCount = dashboard.summary.requestCount,
                inputTokens = dashboard.summary.inputTokens,
                outputTokens = dashboard.summary.outputTokens
            ),
            groups = dashboard.groups.map { it.toUiModel() },
            recentRecords = dashboard.recentRecords.map { it.toUiModel() }
        ).setup()
    }

    private fun LLMTokenUsageGroup.toUiModel(): TokenUsageGroupItem {
        return TokenUsageGroupItem(
            model = effectiveModel,
            endpoint = formatEndpoint(apiHost, apiPort),
            requestCount = requestCount,
            inputTokens = inputTokens,
            outputTokens = outputTokens
        )
    }

    private fun LLMTokenUsageRecord.toUiModel(): TokenUsageRecordItem {
        // 明细 UI 模型继续只携带脱敏字段，不下传请求地址或正文
        return TokenUsageRecordItem(
            id = id,
            createTimeText = createTime.formatTimestamp("MM-dd HH:mm:ss"),
            providerName = providerName,
            protocol = protocol,
            model = effectiveModel,
            endpoint = formatEndpoint(apiHost, apiPort),
            isStreaming = isStreaming,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            inputSource = inputTokenSource,
            outputSource = outputTokenSource,
            tokenizerName = tokenizerName,
            cachedInputTokens = cachedInputTokens,
            reasoningTokens = reasoningTokens,
            durationMs = durationMs
        )
    }

    private fun TokenUsagePeriod.startTimeMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        if (this == TokenUsagePeriod.AllTime) return 0L
        // 近几天按本地自然日计算，避免简单减毫秒在夏令时切换日产生边界偏差
        val startOfToday = Instant.ofEpochMilli(nowMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
        val daysToSubtract = when (this) {
            TokenUsagePeriod.Today -> 0L
            TokenUsagePeriod.LastSevenDays -> 6L
            TokenUsagePeriod.LastThirtyDays -> 29L
            TokenUsagePeriod.AllTime -> 0L
        }
        return startOfToday.minusDays(daysToSubtract).toInstant().toEpochMilli()
    }

    private fun formatEndpoint(host: String, port: Int): String {
        if (host.isBlank()) return "—"
        if (port <= 0 || port == HTTP_PORT || port == HTTPS_PORT) return host
        val displayHost = if (':' in host) "[$host]" else host
        return "$displayHost:$port"
    }

    private companion object {
        const val HTTP_PORT = 80
        const val HTTPS_PORT = 443
    }
}
