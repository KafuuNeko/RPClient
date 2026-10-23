package me.kafuuneko.rpclient.feature.appearance.presentation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import me.kafuuneko.rpclient.libs.appearance.PageBackground
import me.kafuuneko.rpclient.libs.appearance.PageSurfaceKind
import me.kafuuneko.rpclient.libs.theme.AppThemeMode
import me.kafuuneko.rpclient.libs.theme.AppThemePalette

/** 外观页按预设选择区与背景编辑区组织状态，关闭时保留最后一帧。 */
sealed interface AppearanceUiState {
    data class Normal(
        val palette: AppThemePalette = AppThemePalette.Classic,
        val mode: AppThemeMode = AppThemeMode.FollowSystem,
        val editor: BackgroundEditorState? = null
    ) : AppearanceUiState

    data class Finished(val previous: Normal) : AppearanceUiState
}

/** 背景编辑草稿，图片凭据由 ViewModel 持有，不把文件或路径暴露给 UI。 */
data class BackgroundEditorState(
    val settings: PageBackground,
    val image: ImageBitmap? = null,
    val working: Boolean = false,
    val rendering: Boolean = false,
    @param:StringRes val error: Int? = null,
    val surfaceImages: Map<PageSurfaceKind, ImageBitmap> = emptyMap(),
    val dialogState: BackgroundEditorDialogState = BackgroundEditorDialogState.None,
    /** 已解析继承的预览配置，只用于显示，保存时仍提交 settings。 */
    val effectiveSettings: PageBackground = settings
)

/** 背景编辑器的返回确认状态，由 ViewModel 统一控制。 */
enum class BackgroundEditorDialogState {
    None,

    UnsavedChanges
}

/** 内容页可观察的背景子状态，图片不可用时使用原有主题表面。 */
data class PageBackgroundUiState(
    val settings: PageBackground? = null,
    val image: ImageBitmap? = null,
    val surfaceImages: Map<PageSurfaceKind, ImageBitmap> = emptyMap()
)
