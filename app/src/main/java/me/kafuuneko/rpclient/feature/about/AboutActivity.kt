package me.kafuuneko.rpclient.feature.about

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import me.kafuuneko.rpclient.feature.about.presentation.AboutUiState
import me.kafuuneko.rpclient.feature.about.ui.AboutLayout
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.CoreActivity

/** 关于页面宿主，负责提供版本和项目联系信息。 */
class AboutActivity : CoreActivity() {
    @Composable
    override fun ViewContent() {
        val uiState = remember {
            AboutUiState(
                githubRepoUrl = AppModel.GITHUB_REPO,
                githubRepoName = "KafuuNeko/RPClient",
                developerEmail = AppModel.EMAIL
            )
        }
        Surface(modifier = Modifier.fillMaxSize()) {
            AboutLayout(
                uiState = uiState,
                onBack = { finish() }
            )
        }
    }
}
