package me.kafuuneko.rpclient.feature.prompttemplatelist

import android.os.Bundle
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.prompttemplatelist.presentation.PromptTemplateListUiIntent
import me.kafuuneko.rpclient.feature.prompttemplatelist.presentation.PromptTemplateListUiState
import me.kafuuneko.rpclient.feature.prompttemplatelist.ui.PromptTemplateListLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.GetContentWithMimeTypes
import me.kafuuneko.rpclient.libs.core.IViewEvent
import me.kafuuneko.rpclient.feature.prompttemplatelist.presentation.PromptTemplateListViewEvent

/** 提示词模板管理页宿主，只负责状态收集与导航事件。 */
class PromptTemplateListActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<PromptTemplateListViewModel>()
    private val mImporter = registerForActivityResult(GetContentWithMimeTypes()) { uri ->
        mViewModel.emit(PromptTemplateListUiIntent.ImportResult(uri))
    }
    private val mExporter =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            mViewModel.emit(PromptTemplateListUiIntent.ExportResult(uri))
        }

    /** 仅打开系统文档选择器，取消结果也回传以清理待导出快照。 */
    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        when (viewEvent) {
            PromptTemplateListViewEvent.OpenImporter -> mImporter.launch(
                arrayOf(
                    "application/json",
                    "text/*"
                )
            )

            PromptTemplateListViewEvent.OpenExporter -> mExporter.launch("prompt-preset.json")
            else -> super.onReceivedViewEvent(viewEvent)
        }
    }

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
