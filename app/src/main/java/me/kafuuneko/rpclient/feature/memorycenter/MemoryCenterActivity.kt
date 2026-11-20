package me.kafuuneko.rpclient.feature.memorycenter

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.memorycenter.presentation.MemoryCenterUiIntent
import me.kafuuneko.rpclient.feature.memorycenter.presentation.MemoryCenterUiState
import me.kafuuneko.rpclient.feature.memorycenter.ui.MemoryCenterLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent

/** 角色记忆目录宿主，只消费状态和一次性事件。 */
class MemoryCenterActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<MemoryCenterViewModel>()

    /** 初始化一次订阅，往返编辑器由 Room 自动刷新。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(MemoryCenterUiIntent.Init)
    }

    /** 复用统一导航事件处理。 */
    override fun getViewEventFlow() = mViewModel.viewEventFlow

    /** 收集目录状态并处理正常返回。 */
    @Composable
    override fun ViewContent() {
        val state by mViewModel.uiStateFlow.collectAsState()
        LaunchedEffect(state) {
            if (state is MemoryCenterUiState.Finished) finish()
        }
        MemoryCenterLayout(
            state = state,
            emit = { mViewModel.emit(this) },
        )
    }
}
