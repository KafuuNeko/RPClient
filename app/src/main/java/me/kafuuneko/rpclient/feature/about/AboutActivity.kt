package me.kafuuneko.rpclient.feature.about

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.kafuuneko.rpclient.feature.about.ui.AboutLayout
import me.kafuuneko.rpclient.libs.core.CoreActivity

class AboutActivity : CoreActivity() {
    @Composable
    override fun ViewContent() {
        Surface(modifier = Modifier.fillMaxSize()) {
            AboutLayout { finish() }
        }
    }
}