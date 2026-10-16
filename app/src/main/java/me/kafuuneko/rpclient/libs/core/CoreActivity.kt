package me.kafuuneko.rpclient.libs.core

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.flowWithLifecycle
import me.kafuuneko.rpclient.feature.appearance.PageBackgroundViewModel
import me.kafuuneko.rpclient.feature.appearance.presentation.PageBackgroundUiState
import me.kafuuneko.rpclient.libs.appearance.DialogAppearanceManager
import me.kafuuneko.rpclient.ui.theme.LocalDialogAppearance
import me.kafuuneko.rpclient.libs.appearance.BackgroundPage
import me.kafuuneko.rpclient.libs.theme.AppThemeManager
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.PageBackdrop
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Compose Activity 基类。
 *
 * 统一应用主题和 Edge-to-Edge 配置，具体页面只实现 [ViewContent]。
 */
abstract class CoreActivity : ComponentActivity(), KoinComponent {
    private val mDialogAppearance by inject<DialogAppearanceManager>()
    private val mAppThemeManager by inject<AppThemeManager>()

    private val mPageBackgroundViewModel by viewModels<PageBackgroundViewModel>()
    private val mGeneralBackground by lazy {
        mPageBackgroundViewModel.observe(BackgroundPage.General).flowWithLifecycle(lifecycle)
    }

    /** 关于和配色页面可保留完整主题底色，避免外观设置遮蔽其展示内容。 */
    protected open fun usesGeneralBackground(): Boolean = true

    /** 子类可关闭默认的 Edge-to-Edge。 */
    protected open fun isEnableEdgeToEdge(): Boolean = true

    /** 页面 Compose 内容入口。 */
    @Composable
    protected abstract fun ViewContent()

    /** 创建统一主题与背景宿主，业务页面仍由子类提供。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initView()
    }

    private fun initView() {
        if (isEnableEdgeToEdge()) {
            enableEdgeToEdge()
        }
        setContent {
            val themeMode by mAppThemeManager.themeModeFlow.collectAsState()
            val palette by mAppThemeManager.paletteFlow.collectAsState()
            val darkTheme = themeMode.resolveDarkTheme(isSystemInDarkTheme())
            ApplySystemBarTheme(darkTheme)
            val dialogAppearance by mDialogAppearance.settings.collectAsState()
            CompositionLocalProvider(LocalDialogAppearance provides dialogAppearance) {
                AppTheme(darkTheme = darkTheme, palette = palette, content = getContent())
            }
        }
    }

    /** 背景订阅与 Activity 可见生命周期绑定，排除页面不启动图像加载。 */
    private fun getContent(): @Composable () -> Unit = {
        if (usesGeneralBackground()) {
            val background by mGeneralBackground.collectAsState(PageBackgroundUiState())
            PageBackdrop(background) { ViewContent() }
        } else {
            ViewContent()
        }
    }

    /** 让 Edge-to-Edge 系统栏图标与强制亮色或暗色主题保持可读对比度。 */
    @Composable
    private fun ApplySystemBarTheme(darkTheme: Boolean) {
        if (!isEnableEdgeToEdge()) return
        SideEffect {
            val style = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
                detectDarkMode = { darkTheme }
            )
            enableEdgeToEdge(
                statusBarStyle = style,
                navigationBarStyle = style
            )
        }
    }
}

/** 为项目 Compose 组件提供与真实 Activity 一致的主题预览环境。 */
@Composable
fun ActivityPreview(darkTheme: Boolean, content: @Composable () -> Unit) {
    AppTheme(darkTheme = darkTheme, content = content)
}
