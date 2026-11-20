package me.kafuuneko.rpclient.feature.memorycenter.presentation

import androidx.compose.ui.graphics.ImageBitmap

/** 目录状态包含轻量角色列表、普通召回额度及互斥数值弹窗。 */
sealed class MemoryCenterUiState {
    data object None : MemoryCenterUiState()

    data class Normal(
        val content: MemoryCenterContentState = MemoryCenterContentState(),
        val settings: MemoryCenterSettingsState = MemoryCenterSettingsState(),
        val dialog: MemoryCenterDialogState = MemoryCenterDialogState.None,
    ) : MemoryCenterUiState()

    data class Finished(val previous: MemoryCenterUiState) : MemoryCenterUiState()
}

/** 角色目录列表加载与展示状态。 */
data class MemoryCenterContentState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val items: List<MemoryCharacterItem> = emptyList(),
)

/** 普通回复的全局记忆召回额度配置。 */
data class MemoryCenterSettingsState(
    val maxItems: Int = 6,
    val maxTokens: Int = 512,
)

/** 普通回复召回额度的可编辑项。 */
enum class MemoryRecallLimitField {
    MaxItems,
    MaxTokens,
}

/** 目录页互斥弹窗状态。 */
sealed class MemoryCenterDialogState {
    data object None : MemoryCenterDialogState()

    data class EditLimit(
        val field: MemoryRecallLimitField,
        val draft: String,
    ) : MemoryCenterDialogState()
}

/** 目录中的角色摘要项。 */
data class MemoryCharacterItem(
    val id: Long,
    val name: String,
    val count: Int,
    val avatarUuid: String,
    val image: ImageBitmap? = null,
)
