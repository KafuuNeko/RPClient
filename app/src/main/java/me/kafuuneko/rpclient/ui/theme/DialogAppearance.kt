package me.kafuuneko.rpclient.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import me.kafuuneko.rpclient.libs.appearance.DialogAppearance

/** 宿主提供已保存外观，编辑预览可局部覆盖，普通页面不使用此配置绘制背景。 */
val LocalDialogAppearance = staticCompositionLocalOf { DialogAppearance() }

/** 仅在对话框面板内提供内部控件的不透明度；null 表示沿用页面原有逻辑。 */
internal val LocalDialogControlOpacity = staticCompositionLocalOf<Float?> { null }
