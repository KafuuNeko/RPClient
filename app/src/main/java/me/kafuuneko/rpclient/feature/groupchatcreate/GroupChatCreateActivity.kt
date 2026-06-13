package me.kafuuneko.rpclient.feature.groupchatcreate

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.groupchatcreate.presentation.GroupChatCreateUiIntent
import me.kafuuneko.rpclient.feature.groupchatcreate.presentation.GroupChatCreateUiState
import me.kafuuneko.rpclient.feature.groupchatcreate.ui.GroupChatCreateLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent

/** 新建群聊页面宿主。 */
class GroupChatCreateActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<GroupChatCreateViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()
        LaunchedEffect(uiState) {
            if (uiState is GroupChatCreateUiState.Finished) finish()
        }
        GroupChatCreateLayout(
            uiState = uiState,
            emitIntent = mViewModel::emit
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(GroupChatCreateUiIntent.Init)
    }
}
