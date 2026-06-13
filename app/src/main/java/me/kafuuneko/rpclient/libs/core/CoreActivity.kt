package me.kafuuneko.rpclient.libs.core

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import me.kafuuneko.rpclient.ui.theme.AppTheme

/**
 * Compose Activity 基类。
 *
 * 统一应用主题和 Edge-to-Edge 配置，具体页面只实现 [ViewContent]。
 */
abstract class CoreActivity : ComponentActivity() {
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
        setContent { AppTheme(content = getContent()) }
    }

    private fun getContent(): @Composable () -> Unit = { ViewContent() }
}

/** 为项目 Compose 组件提供与真实 Activity 一致的主题预览环境。 */
@Composable
fun ActivityPreview(darkTheme: Boolean, content: @Composable () -> Unit) {
    AppTheme(darkTheme = darkTheme, content = content)
}
