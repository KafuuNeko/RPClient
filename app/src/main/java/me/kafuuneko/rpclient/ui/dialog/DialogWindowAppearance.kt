package me.kafuuneko.rpclient.ui.dialog

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import me.kafuuneko.rpclient.libs.appearance.DialogAppearance
import java.util.function.Consumer
import kotlin.math.roundToInt

/** 观察设备与省电模式的实时窗口模糊能力，预览和弹窗使用同一降级判断。 */
@Composable
internal fun rememberDialogBlurAvailable(): Boolean {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val manager = remember(context) { context.getSystemService(WindowManager::class.java) }
    var available by remember(manager) {
        mutableStateOf(!inspection && Build.VERSION.SDK_INT >= 31 && manager.isCrossWindowBlurEnabled)
    }
    // 设备可以在弹窗显示期间关闭模糊，监听必须随组合释放，不能持有已关闭的窗口。
    DisposableEffect(manager, inspection) {
        if (!inspection && Build.VERSION.SDK_INT >= 31) {
            val listener = Consumer<Boolean> { enabled -> available = enabled }
            manager.addCrossWindowBlurEnabledListener(context.mainExecutor, listener)
            onDispose { manager.removeCrossWindowBlurEnabledListener(listener) }
        } else onDispose { }
    }
    return available
}

/** 为与面板同尺寸的原生窗口设置后景模糊，以统一圆角限制模糊范围。 */
@Composable
internal fun Modifier.dialogWindowAppearance(settings: DialogAppearance): Modifier {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return this
    val density = LocalDensity.current
    val radius = with(density) { 28.dp.toPx() }
    val outline = remember(window, radius) {
        GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = radius
        }
    }
    // 窗口背景只提供模糊轮廓，实际颜色由 Compose 面板绘制，避免双层底色。
    DisposableEffect(window, outline) {
        val previousBackground = window.decorView.background
        val previousAttributes = WindowManager.LayoutParams().apply { copyFrom(window.attributes) }
        window.setBackgroundDrawable(outline)
        onDispose {
            if (Build.VERSION.SDK_INT >= 31) window.setBackgroundBlurRadius(0)
            window.attributes = previousAttributes
            window.setBackgroundDrawable(previousBackground)
        }
    }
    SideEffect {
        updateDialogWindow(window, settings, density.density)
    }
    return this
}

/** 仅修改弹窗窗口，0 强度明确清除旧半径；全屏后景模糊不受面板透明度影响。 */
private fun updateDialogWindow(window: Window, settings: DialogAppearance, density: Float) {
    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    window.setDimAmount(settings.dim / 100f)
    if (Build.VERSION.SDK_INT < 31) return
    // 强度按密度转换为物理像素，并限制半径，避免高密度设备产生过大 GPU 开销。
    fun radius(strength: Int) = (strength * 0.4f * density).roundToInt().coerceIn(minimumValue = 0, maximumValue = 150)
    window.setBackgroundBlurRadius(if (settings.transparency > 0) radius(settings.blur) else 0)
    val attributes = window.attributes
    attributes.blurBehindRadius = radius(settings.behindBlur)
    attributes.flags = if (settings.behindBlur > 0) {
        attributes.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
    } else attributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
    window.attributes = attributes
}
