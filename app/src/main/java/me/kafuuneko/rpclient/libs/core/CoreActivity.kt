package me.kafuuneko.rpclient.libs.core

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.libs.theme.AppThemeManager
import me.kafuuneko.rpclient.ui.theme.AppTheme
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Compose Activity 基类。
 *
 * 统一应用主题和 Edge-to-Edge 配置，具体页面只实现 [ViewContent]。
 */
abstract class CoreActivity : ComponentActivity(), KoinComponent {
    private val mAppThemeManager by inject<AppThemeManager>()

    /** 子类可关闭默认的 Edge-to-Edge。 */
    protected open fun isEnableEdgeToEdge(): Boolean = true

    /** 页面 Compose 内容入口。 */
    @Composable
    protected abstract fun ViewContent()

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
            val darkTheme = themeMode.resolveDarkTheme(isSystemInDarkTheme())
            ApplySystemBarTheme(darkTheme)
            AppTheme(darkTheme = darkTheme, content = getContent())
        }
    }

    private fun getContent(): @Composable () -> Unit = { ViewContent() }

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
