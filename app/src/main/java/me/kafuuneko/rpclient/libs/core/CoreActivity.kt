package me.kafuuneko.rpclient.libs.core

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import me.kafuuneko.rpclient.ui.theme.AppTheme

abstract class CoreActivity : ComponentActivity() {
    protected open fun isEnableEdgeToEdge(): Boolean = true

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

@Composable
fun ActivityPreview(darkTheme: Boolean, content: @Composable () -> Unit) {
    AppTheme(darkTheme = darkTheme, content = content)
}