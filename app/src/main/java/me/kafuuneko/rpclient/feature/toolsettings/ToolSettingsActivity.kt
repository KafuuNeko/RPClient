package me.kafuuneko.rpclient.feature.toolsettings

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsUiIntent
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsUiState
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsViewEvent
import me.kafuuneko.rpclient.feature.toolsettings.ui.ToolSettingsLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.GetContentWithMimeTypes
import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 工具管理宿主，只负责系统文档选择、页面状态收集及结束动作。 */
class ToolSettingsActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<ToolSettingsViewModel>()
    private val mImporter = registerForActivityResult(GetContentWithMimeTypes()) {
        mViewModel.emit(
            ToolSettingsUiIntent.ImportResult(it)
        )
    }
    private val mExporter =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
            mViewModel.emit(ToolSettingsUiIntent.ExportResult(it))
        }

    /** 初始化页面，旋转时复用已有 ViewModel。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(ToolSettingsUiIntent.Init)
    }

    /** 由基类处理公共宿主事件。 */
    override fun getViewEventFlow() = mViewModel.viewEventFlow

    /** 展示状态树并在 Finished 后结束宿主。 */
    @Composable
    override fun ViewContent() {
        val state by mViewModel.uiStateFlow.collectAsState()
        LaunchedEffect(state) { if (state is ToolSettingsUiState.Finished) finish() }
        ToolSettingsLayout(uiState = state, emit = { mViewModel.emit(this) })
    }

    /** 仅承载系统选择器，不解析或写入配置。 */
    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        when (viewEvent) {
            ToolSettingsViewEvent.OpenImporter -> mImporter.launch(
                arrayOf(
                    "application/json",
                    "text/*"
                )
            )

            ToolSettingsViewEvent.OpenExporter -> mExporter.launch("http-tools.json")
            else -> super.onReceivedViewEvent(viewEvent)
        }
    }
}
