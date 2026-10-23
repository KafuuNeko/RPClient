package me.kafuuneko.rpclient.feature.appearance

import android.content.Intent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.appearance.presentation.AppearanceUiIntent
import me.kafuuneko.rpclient.feature.appearance.presentation.AppearanceUiState
import me.kafuuneko.rpclient.feature.appearance.presentation.AppearanceViewEvent
import me.kafuuneko.rpclient.feature.appearance.ui.AppearanceLayout
import me.kafuuneko.rpclient.feature.dialogappearance.DialogAppearanceActivity
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 外观页面宿主，仅负责系统选图和 ViewEvent 分发。 */
class AppearanceActivity : CoreActivityWithEvent() {
    /** 本页用于展示应用介绍或主题原色，排除通用背景。 */
    override fun usesGeneralBackground(): Boolean = false

    private val mViewModel by viewModels<AppearanceViewModel>()
    private val mPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { uri -> mViewModel.emit(AppearanceUiIntent.ImageSelected(uri)) }
    }

    /** 提供外观页面的一次性宿主事件。 */
    override fun getViewEventFlow() = mViewModel.viewEventFlow

    /** 绑定状态并在关闭过渡期保留上一帧。 */
    @Composable
    override fun ViewContent() {
        val state by mViewModel.uiStateFlow.collectAsState()
        LaunchedEffect(state) { if (state is AppearanceUiState.Finished) finish() }
        AppearanceLayout(state, mViewModel::emit)
    }

    /** 使用系统 Photo Picker，旧系统由 Activity 库自动回退文档选择器。 */
    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        when (viewEvent) {
            AppearanceViewEvent.OpenDialogAppearance -> startActivity(Intent(this, DialogAppearanceActivity::class.java))
            AppearanceViewEvent.PickImage -> mPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            else -> super.onReceivedViewEvent(viewEvent)
        }
    }
}
