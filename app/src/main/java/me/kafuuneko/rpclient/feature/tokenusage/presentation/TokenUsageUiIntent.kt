package me.kafuuneko.rpclient.feature.tokenusage.presentation

/** 消耗统计页的用户意图。 */
sealed class TokenUsageUiIntent {
    data object Init : TokenUsageUiIntent()
    data object Back : TokenUsageUiIntent()
    data class SelectPeriod(val period: TokenUsagePeriod) : TokenUsageUiIntent()
    data object ShowClearConfirmDialog : TokenUsageUiIntent()
    data object ConfirmClearRecords : TokenUsageUiIntent()
    data object DismissDialog : TokenUsageUiIntent()
}
