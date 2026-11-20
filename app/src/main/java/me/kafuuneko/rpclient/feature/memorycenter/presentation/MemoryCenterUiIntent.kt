package me.kafuuneko.rpclient.feature.memorycenter.presentation

/** 目录点击、额度编辑及可见缩略图请求统一交给 ViewModel。 */
sealed class MemoryCenterUiIntent {
    data object Init : MemoryCenterUiIntent()
    data object Back : MemoryCenterUiIntent()
    data object Retry : MemoryCenterUiIntent()
    data class OpenCharacter(val id: Long) : MemoryCenterUiIntent()
    data class VisibleCharactersChanged(val ids: Set<Long>) : MemoryCenterUiIntent()
    data class EditLimitClick(val field: MemoryRecallLimitField) : MemoryCenterUiIntent()
    data class ChangeLimitDraft(val value: String) : MemoryCenterUiIntent()
    data object ConfirmLimit : MemoryCenterUiIntent()
    data object DismissDialog : MemoryCenterUiIntent()
}
