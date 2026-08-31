package me.kafuuneko.rpclient.feature.tokenusage.presentation

import me.kafuuneko.rpclient.feature.tokenusage.model.TokenUsageGroupItem
import me.kafuuneko.rpclient.feature.tokenusage.model.TokenUsageRecordItem
import me.kafuuneko.rpclient.feature.tokenusage.model.TokenUsageSummaryItem
import me.kafuuneko.rpclient.feature.tokenusage.model.TokenUsageTrendPoint

/** 消耗统计支持的时间范围。 */
enum class TokenUsagePeriod {
    Today,
    LastSevenDays,
    LastThirtyDays,
    AllTime
}

/** 消耗趋势图表的展示形态。 */
enum class TokenUsageChartMode {
    /** 分段柱状统计图。 */
    Bar,
    /** 平滑渐变面积走势图。 */
    Line
}

/** 消耗统计页当前显示的确认对话框。 */
sealed class TokenUsageDialogState {
    data object None : TokenUsageDialogState()
    data object ClearConfirm : TokenUsageDialogState()
}

/** 消耗统计页状态树。 */
sealed class TokenUsageUiState {
    data object None : TokenUsageUiState()

    data class Normal(
        /** Token 用量页当前选中的统计周期。 */
        val selectedPeriod: TokenUsagePeriod,
        /** 当前周期内的全局综合用量摘要。 */
        val summary: TokenUsageSummaryItem,
        /** 当前周期内连续按自然日分布的时序趋势点列表。 */
        val trendPoints: List<TokenUsageTrendPoint> = emptyList(),
        /** 趋势图表中当前用户点击选中的时序点键值；为空时默认展示全局或峰值。 */
        val selectedPointKey: String? = null,
        /** 趋势图表的展示形态（柱状图 / 走势图）。 */
        val chartMode: TokenUsageChartMode = TokenUsageChartMode.Bar,
        /** 当前时间范围内按实际模型与 API Host 聚合的分组列表。 */
        val groups: List<TokenUsageGroupItem>,
        /** Token 用量页最近请求记录列表。 */
        val recentRecords: List<TokenUsageRecordItem>,
        /** 当前页面互斥展示的对话框状态。 */
        val dialogState: TokenUsageDialogState = TokenUsageDialogState.None
    ) : TokenUsageUiState()

    data class Finished(val previous: TokenUsageUiState) : TokenUsageUiState()

    companion object {
        /** 构造页面结束状态，并避免重复嵌套 Finished。 */
        fun finished(previous: TokenUsageUiState): TokenUsageUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

