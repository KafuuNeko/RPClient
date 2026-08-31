package me.kafuuneko.rpclient.feature.tokenusage.presentation

/** 消耗统计页的用户意图。 */
sealed class TokenUsageUiIntent {
    data object Init : TokenUsageUiIntent()
    data object Back : TokenUsageUiIntent()
    data class SelectPeriod(val period: TokenUsagePeriod) : TokenUsageUiIntent()
    data class SelectTrendPoint(val key: String?) : TokenUsageUiIntent()
    data class SelectChartMode(val mode: TokenUsageChartMode) : TokenUsageUiIntent()
    data object ShowClearConfirmDialog : TokenUsageUiIntent()
    data object ConfirmClearRecords : TokenUsageUiIntent()
    data object DismissDialog : TokenUsageUiIntent()
}
