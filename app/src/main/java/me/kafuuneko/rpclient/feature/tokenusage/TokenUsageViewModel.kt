package me.kafuuneko.rpclient.feature.tokenusage

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import me.kafuuneko.rpclient.feature.tokenusage.model.TokenUsageGroupItem
import me.kafuuneko.rpclient.feature.tokenusage.model.TokenUsageRecordItem
import me.kafuuneko.rpclient.feature.tokenusage.model.TokenUsageSummaryItem
import me.kafuuneko.rpclient.feature.tokenusage.model.TokenUsageTrendPoint
import me.kafuuneko.rpclient.feature.tokenusage.presentation.TokenUsageChartMode
import me.kafuuneko.rpclient.feature.tokenusage.presentation.TokenUsageDialogState
import me.kafuuneko.rpclient.feature.tokenusage.presentation.TokenUsagePeriod
import me.kafuuneko.rpclient.feature.tokenusage.presentation.TokenUsageUiIntent
import me.kafuuneko.rpclient.feature.tokenusage.presentation.TokenUsageUiState
import me.kafuuneko.rpclient.libs.core.CoreViewModel
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.entity.LLMTokenUsageRecord
import me.kafuuneko.rpclient.libs.room.model.LLMTokenUsageDailyStat
import me.kafuuneko.rpclient.libs.room.model.LLMTokenUsageGroup
import me.kafuuneko.rpclient.libs.room.repository.LLMTokenUsageRepository
import me.kafuuneko.rpclient.utils.formatTimestamp
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 消耗统计页状态持有者。
 *
 * 核心职责与调度：
 * - 结合系统时区精确计算自然日与多周期统计查询边界。
 * - 通过 Repository 读取用量总览、自然日时序趋势、模型 Host 聚合与近期明细。
 * - 对时序离散数据补齐连续日期轴，计算模型总消耗占比与峰值高亮。
 * - 驱动时间范围切换、时序点交互选择、清空确认与页面结束状态。
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

    /** 处理柱状趋势图中点击选中或取消选中特定时序点。 */
    @UiIntentObserver(TokenUsageUiIntent.SelectTrendPoint::class)
    private fun onSelectTrendPoint(intent: TokenUsageUiIntent.SelectTrendPoint) {
        val uiState = getOrNull<TokenUsageUiState.Normal>() ?: return
        val newKey = if (uiState.selectedPointKey == intent.key) null else intent.key
        uiState.copy(selectedPointKey = newKey).setup()
    }

    /** 切换消耗趋势图表的展示形态（柱状图 / 走势图）。 */
    @UiIntentObserver(TokenUsageUiIntent.SelectChartMode::class)
    private fun onSelectChartMode(intent: TokenUsageUiIntent.SelectChartMode) {
        val uiState = getOrNull<TokenUsageUiState.Normal>() ?: return
        if (uiState.chartMode == intent.mode) return
        uiState.copy(chartMode = intent.mode).setup()
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
        // 从数据库拉取指定时间范围的聚合数据与明细记录
        val dashboard = mRepository.getDashboard(period.startTimeMillis())
        val totalTokens = dashboard.summary.inputTokens + dashboard.summary.outputTokens
        val previousChartMode = getOrNull<TokenUsageUiState.Normal>()?.chartMode ?: TokenUsageChartMode.Bar

        // 构建连续的时序趋势点，补齐无使用记录的空白日期以形成完整柱形图
        val trendPoints = buildTrendPoints(period, dashboard.dailyStats)

        // 计算各模型分组在当前总消耗中的占比，并转换展示端点
        val groupItems = dashboard.groups.map { group ->
            val groupTotal = group.inputTokens + group.outputTokens
            val ratio = if (totalTokens > 0L) {
                (groupTotal.toFloat() / totalTokens).coerceIn(0f, 1f)
            } else {
                0f
            }
            group.toUiModel(ratio)
        }

        // 装配完整页面状态并通知 UI 刷新
        TokenUsageUiState.Normal(
            selectedPeriod = period,
            summary = TokenUsageSummaryItem(
                requestCount = dashboard.summary.requestCount,
                inputTokens = dashboard.summary.inputTokens,
                outputTokens = dashboard.summary.outputTokens,
                cachedInputTokens = dashboard.summary.cachedInputTokens,
                reasoningTokens = dashboard.summary.reasoningTokens
            ),
            trendPoints = trendPoints,
            selectedPointKey = null,
            chartMode = previousChartMode,
            groups = groupItems,
            recentRecords = dashboard.recentRecords.map { it.toUiModel() }
        ).setup()
    }

    /**
     * 根据当前选择的统计周期生成连续的时序数据点。
     *
     * 处理机制与补齐规则：
     * - 今天：若无数据则填充今日单个空点，有数据则直接对应今日指标。
     * - 近 7 天 / 近 30 天：从起始日期按天递增至今天，未匹配到数据库记录的日期自动填 0。
     * - 全部：若数据库返回了时序数据，则直接映射并按日期升序输出。
     *
     * @param period 当前选中的时间统计范围
     * @param rawStats 数据库按日汇总的原始统计结果
     * @return 供图表组件渲染的连续时序点列表
     */
    private fun buildTrendPoints(
        period: TokenUsagePeriod,
        rawStats: List<LLMTokenUsageDailyStat>
    ): List<TokenUsageTrendPoint> {
        val statMap = rawStats.associateBy { it.dateText }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val shortDateFormatter = DateTimeFormatter.ofPattern("MM-dd")

        val daysCount = when (period) {
            TokenUsagePeriod.Today -> 1
            TokenUsagePeriod.LastSevenDays -> 7
            TokenUsagePeriod.LastThirtyDays -> 30
            TokenUsagePeriod.AllTime -> 0
        }

        // 全部周期直接返回数据库已有的非空时序列表
        if (daysCount == 0) {
            return rawStats.map { stat ->
                TokenUsageTrendPoint(
                    key = stat.dateText,
                    label = stat.dateText.takeLast(5),
                    inputTokens = stat.inputTokens,
                    outputTokens = stat.outputTokens,
                    cachedInputTokens = stat.cachedInputTokens,
                    requestCount = stat.requestCount,
                    isCurrent = stat.dateText == today.format(fullDateFormatter)
                )
            }
        }

        // 固定天数周期从过去按天递增补齐完整时间轴
        val startDay = today.minusDays((daysCount - 1).toLong())
        return (0 until daysCount).map { offset ->
            val date = startDay.plusDays(offset.toLong())
            val dateKey = date.format(fullDateFormatter)
            val label = date.format(shortDateFormatter)
            val stat = statMap[dateKey]
            TokenUsageTrendPoint(
                key = dateKey,
                label = label,
                inputTokens = stat?.inputTokens ?: 0L,
                outputTokens = stat?.outputTokens ?: 0L,
                cachedInputTokens = stat?.cachedInputTokens ?: 0L,
                requestCount = stat?.requestCount ?: 0L,
                isCurrent = date == today
            )
        }
    }

    private fun LLMTokenUsageGroup.toUiModel(ratio: Float): TokenUsageGroupItem {
        return TokenUsageGroupItem(
            model = effectiveModel,
            endpoint = formatEndpoint(apiHost, apiPort),
            requestCount = requestCount,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cachedInputTokens = cachedInputTokens,
            reasoningTokens = reasoningTokens,
            ratio = ratio
        )
    }

    private fun LLMTokenUsageRecord.toUiModel(): TokenUsageRecordItem {
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

