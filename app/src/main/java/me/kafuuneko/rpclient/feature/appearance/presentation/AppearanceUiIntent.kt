package me.kafuuneko.rpclient.feature.appearance.presentation

import android.net.Uri
import me.kafuuneko.rpclient.libs.appearance.BackgroundPage
import me.kafuuneko.rpclient.libs.theme.AppThemeMode
import me.kafuuneko.rpclient.libs.theme.AppThemePalette

/** 外观页的用户动作，后台处理细节不进入 Intent。 */
sealed interface AppearanceUiIntent {
    data object EditDialogAppearance : AppearanceUiIntent

    data object Back : AppearanceUiIntent

    data class SelectPalette(val palette: AppThemePalette) : AppearanceUiIntent

    data class SelectMode(val mode: AppThemeMode) : AppearanceUiIntent

    data class EditBackground(val page: BackgroundPage) : AppearanceUiIntent

    data class SetFollowGeneral(val enabled: Boolean) : AppearanceUiIntent

    data class SetUseCharacterBackground(val enabled: Boolean) : AppearanceUiIntent

    data object PickImage : AppearanceUiIntent

    data class ImageSelected(val uri: Uri) : AppearanceUiIntent

    data class ChangeAdjustment(val field: BackgroundAdjustment, val value: Float) : AppearanceUiIntent

    data object FinishAdjustment : AppearanceUiIntent

    data object ResetBackground : AppearanceUiIntent

    data object ApplyBackground : AppearanceUiIntent

    data object DiscardBackgroundChanges : AppearanceUiIntent

    data object DismissDialog : AppearanceUiIntent
}

/** 可调参数与预览位置对应，滑块数值统一在领域模型边界校验。 */
enum class BackgroundAdjustment {
    Blur,

    TopBarBlur,

    BottomBarBlur,

    BubbleBlur,

    ContentBlur,

    Scrim,

    ContentTransparency,

    TopBarTransparency,

    BottomBarTransparency,

    BubbleTransparency,

    Horizontal,

    Vertical;

    /** 模糊滑块使用无单位整数强度，并触发可取消图像预览。 */
    val isBlur: Boolean get() = this in setOf(Blur, TopBarBlur, BottomBarBlur, BubbleBlur, ContentBlur)

    /** 改变透明度时需要重新判断栏位模糊是否启用。 */
    val isTransparency: Boolean get() = this in setOf(
        ContentTransparency,
        TopBarTransparency,
        BottomBarTransparency,
        BubbleTransparency,
    )
}
