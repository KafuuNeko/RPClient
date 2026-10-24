package me.kafuuneko.rpclient.feature.dialogappearance

import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.dialogappearance.presentation.DialogAppearanceUiState
import me.kafuuneko.rpclient.feature.dialogappearance.ui.DialogAppearanceLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent

/** 对话框外观编辑宿主，草稿和保存由 ViewModel 处理。 */
class DialogAppearanceActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<DialogAppearanceViewModel>()

    /** 编辑控件保持稳定底色，草稿只作用于预览和显式打开的示例。 */
    override fun usesGeneralBackground(): Boolean = false

    /** 提供统一的一次性事件流。 */
    override fun getViewEventFlow() = mViewModel.viewEventFlow

    /** 收集草稿并保留退出时的最后一帧。 */
    @Composable
    override fun ViewContent() {
        val state by mViewModel.uiStateFlow.collectAsState()
        LaunchedEffect(state) { if (state is DialogAppearanceUiState.Finished) finish() }
        DialogAppearanceLayout(state, mViewModel::emit)
    }
}
