package me.kafuuneko.rpclient.ui.dialog

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.activity.addCallback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.view.WindowCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import me.kafuuneko.rpclient.R
import java.util.UUID
import androidx.compose.ui.R as ComposeR

/**
 * 使用独立透明窗口主题承载公共弹窗，避免 Activity 的不透明主题禁用系统背景模糊。
 * - 生命周期和组合上下文仍来自调用页面，业务显示状态始终由调用方管理。
 * - 保留返回、外侧点击、输入法、窗口安全标志及 Dialog 无障碍语义。
 */
@Composable
internal fun AppDialogWindow(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    windowInset: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val parent = LocalView.current
    val composition = rememberCompositionContext()
    val latestContent by rememberUpdatedState(content)
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    val latestProperties by rememberUpdatedState(properties)
    val latestInset by rememberUpdatedState(windowInset)
    val direction = LocalLayoutDirection.current
    val inset = with(LocalDensity.current) { windowInset.roundToPx() }
    val saveableId = rememberSaveable { UUID.randomUUID().toString() }
    // 返回与外侧点击只发送关闭请求，由页面状态决定是否真正销毁弹窗。
    val host = remember(parent) {
        AppearanceDialog(parent.context) { latestDismiss() }.apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCanceledOnTouchOutside(true)
            onBackPressedDispatcher.addCallback(this) {
                if (latestProperties.dismissOnBackPress) latestDismiss()
            }
        }
    }
    // 使用页面的组合与状态恢复宿主，确保主题和草稿变化能更新已打开的窗口。
    val view = remember(host) {
        DialogContentView(host).apply {
            setTag(ComposeR.id.compose_view_saveable_id_tag, "Dialog:$saveableId")
            setParentCompositionContext(composition)
            setViewTreeLifecycleOwner(parent.findViewTreeLifecycleOwner())
            setViewTreeViewModelStoreOwner(parent.findViewTreeViewModelStoreOwner())
            setViewTreeSavedStateRegistryOwner(parent.findViewTreeSavedStateRegistryOwner())
            dialogContent = { DialogWindowContent(latestInset, latestContent) }
        }
    }
    // 窗口主题在创建时即为 translucent；运行中仅更新尺寸、安全和关闭行为。
    SideEffect {
        view.layoutDirection = if (direction == LayoutDirection.Rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        host.setCanceledOnTouchOutside(properties.dismissOnClickOutside)
        val window = requireNotNull(host.window)
        WindowCompat.setDecorFitsSystemWindows(window, properties.decorFitsSystemWindows)
        val parentSecure = (parent.rootView.layoutParams as? WindowManager.LayoutParams)
            ?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE)?.let { value -> value != 0 } ?: false
        val secure = when (properties.securePolicy) {
            SecureFlagPolicy.SecureOn -> true
            SecureFlagPolicy.SecureOff -> false
            SecureFlagPolicy.Inherit -> parentSecure
        }
        window.setFlags(
            if (secure) WindowManager.LayoutParams.FLAG_SECURE else 0,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        window.setLayout(
            if (properties.usePlatformDefaultWidth) ViewGroup.LayoutParams.WRAP_CONTENT
            else (parent.rootView.width - 2 * inset).coerceAtLeast(1),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
    DisposableEffect(host, view) {
        host.setContentView(view)
        host.show()
        onDispose {
            host.dismiss()
            view.disposeComposition()
        }
    }
}

/** 为面板提供真实 Window，同时复用 ComposeView 的测量和输入连接。 */
private class DialogContentView(host: ComponentDialog) : AbstractComposeView(host.context), DialogWindowProvider {
    override val window: Window = requireNotNull(host.window)
    var dialogContent: @Composable () -> Unit = {}
    /** 内容继承调用页面的主题和已提交外观，不使用 Android 默认弹窗背景。 */
    @Composable
    override fun Content() = dialogContent()
}

/** 外侧点击只请求关闭，避免业务状态尚未更新时系统提前销毁窗口。 */
private class AppearanceDialog(context: Context, private val mOnDismissRequest: () -> Unit) :
    ComponentDialog(context, R.style.Theme_RPClient_Dialog) {
    /** 保留受控弹窗语义，真正销毁由组合的释放阶段负责。 */
    override fun cancel() = mOnDismissRequest()
}

/** 直接测量内容以预留窗口外边距，避免子组合缓存旧主题或旧外观参数。 */
@Composable
private fun DialogWindowContent(inset: Dp, content: @Composable () -> Unit) {
    val pixels = with(LocalDensity.current) { inset.roundToPx() * 2 }
    Layout(content = content, modifier = Modifier.semantics { dialog() }) { measurables, constraints ->
        val maximum = if (constraints.hasBoundedHeight) (constraints.maxHeight - pixels).coerceAtLeast(0)
            else constraints.maxHeight
        val childConstraints = constraints.copy(minHeight = 0, maxHeight = maximum)
        val children = measurables.map { measurable -> measurable.measure(childConstraints) }
        layout(
            width = children.maxOfOrNull { placeable -> placeable.width } ?: 0,
            height = children.maxOfOrNull { placeable -> placeable.height } ?: 0,
        ) {
            children.forEach { placeable -> placeable.placeRelative(x = 0, y = 0) }
        }
    }
}
