package me.kafuuneko.rpclient.feature.tokenusage.presentation

import me.kafuuneko.rpclient.feature.tokenusage.model.TokenUsageGroupItem
import me.kafuuneko.rpclient.feature.tokenusage.model.TokenUsageRecordItem
import me.kafuuneko.rpclient.feature.tokenusage.model.TokenUsageSummaryItem

/** 消耗统计支持的时间范围。 */
enum class TokenUsagePeriod {
    Today,
    LastSevenDays,
    LastThirtyDays,
    AllTime
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
        /** 当前会话或故事使用的摘要内容。 */
        val summary: TokenUsageSummaryItem,
        /** 当前页面或结果包含的分组列表。 */
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
