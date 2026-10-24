package me.kafuuneko.rpclient.feature.tokenusage

import me.kafuuneko.rpclient.feature.tokenusage.model.TokenUsageSummaryItem
import me.kafuuneko.rpclient.feature.tokenusage.model.buildTrendPoints
import me.kafuuneko.rpclient.feature.tokenusage.model.toUiModel
import me.kafuuneko.rpclient.feature.tokenusage.presentation.TokenUsageChartMode
import me.kafuuneko.rpclient.feature.tokenusage.presentation.TokenUsageDialogState
import me.kafuuneko.rpclient.feature.tokenusage.presentation.TokenUsagePeriod
import me.kafuuneko.rpclient.feature.tokenusage.presentation.TokenUsageUiIntent
import me.kafuuneko.rpclient.feature.tokenusage.presentation.TokenUsageUiState
import me.kafuuneko.rpclient.feature.tokenusage.presentation.startTimeMillis
import me.kafuuneko.rpclient.libs.core.CoreViewModel
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.repository.LLMTokenUsageRepository
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

}
