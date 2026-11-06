package me.kafuuneko.rpclient.feature.prompttemplatelist

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.prompttemplatelist.presentation.PromptTemplateListUiIntent
import me.kafuuneko.rpclient.feature.prompttemplatelist.presentation.PromptTemplateListUiState
import me.kafuuneko.rpclient.feature.prompttemplatelist.ui.PromptTemplateListLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent

/** 提示词模板管理页宿主，只负责状态收集与导航事件。 */
class PromptTemplateListActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<PromptTemplateListViewModel>()

    /** 由基类消费跳转与一次性提示。 */
    override fun getViewEventFlow() = mViewModel.viewEventFlow

    /** 初始化列表订阅，配置变化时复用 ViewModel。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(PromptTemplateListUiIntent.Init)
    }

    /** 展示项目统一页面，并保留结束前的最后一帧。 */
    @Composable
    override fun ViewContent() {
        val state by mViewModel.uiStateFlow.collectAsState()
        LaunchedEffect(state) { if (state is PromptTemplateListUiState.Finished) finish() }
        PromptTemplateListLayout(uiState = state, emit = { mViewModel.emit(this) })
    }
}
