package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.appearance.PageSurfaceKind
import me.kafuuneko.rpclient.libs.appearance.RenderedPageBackground
import me.kafuuneko.rpclient.ui.theme.AppTheme

/**
 * 首次读取期间展示页面级进度，沿用页面背景并提供返回入口。
 *
 * @param title 页面顶栏标题。
 * @param background 当前页面背景，未就绪时显示主题底色。
 * @param onBack 用户请求返回时的回调，由页面转换为 Intent。
 */
@Composable
fun AppLoadingPage(
    title: String,
    background: RenderedPageBackground? = null,
    onBack: () -> Unit,
) {
    // 透明画布保留页面背景，读取任务和取消行为由调用方管理。
    PageBackdrop(background = background, useContainerAppearance = false) {
        AppPageScaffold(
            containerColor = Color.Transparent,
            topBar = {
                // 顶栏采用所属页面的栏位参数，内层只提供标题与返回交互。
                val opacity = LocalPageTopBarOpacity.current
                PageSurface(
                    kind = PageSurfaceKind.TopBar,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = opacity),
                    tonalElevation = 1.dp * opacity,
                ) {
                    AppTopBar(
                        title = title,
                        onBack = onBack,
                        containerColor = Color.Transparent,
                    )
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(
                    space = 16.dp,
                    alignment = Alignment.CenterVertically,
                ),
            ) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.loading),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AppLoadingPagePreview() {
    AppTheme(darkTheme = false, dynamicColor = false) {
        AppLoadingPage(title = stringResource(R.string.session), onBack = {})
    }
}
