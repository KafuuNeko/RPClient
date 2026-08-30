package me.kafuuneko.rpclient.feature.tokenusage

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.tokenusage.presentation.TokenUsageUiIntent
import me.kafuuneko.rpclient.feature.tokenusage.presentation.TokenUsageUiState
import me.kafuuneko.rpclient.feature.tokenusage.ui.TokenUsageLayout
import me.kafuuneko.rpclient.libs.core.CoreActivity

/** 消耗统计页面宿主，负责绑定 MVI 状态并完成页面退出。 */
class TokenUsageActivity : CoreActivity() {
    private val mViewModel by viewModels<TokenUsageViewModel>()

    /** 收集统计页状态并渲染 Compose 内容。 */
    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is TokenUsageUiState.Finished) finish()
        }

        TokenUsageLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    /** 初始化页面内容并触发首次统计查询。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(TokenUsageUiIntent.Init)
    }
}
