package me.kafuuneko.rpclient.feature.groupchat

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiIntent
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiState
import me.kafuuneko.rpclient.feature.groupchat.ui.GroupChatLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent

class GroupChatActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<GroupChatViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()
        LaunchedEffect(uiState) {
            if (uiState is GroupChatUiState.Finished) finish()
        }
        GroupChatLayout(
            uiState = uiState,
            emitIntent = mViewModel::emit
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(
            GroupChatUiIntent.Init(
                sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
            )
        )
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(GroupChatUiIntent.Resume)
    }

    companion object {
        // 启动群聊页面时传递会话 ID 的参数名。
        const val EXTRA_SESSION_ID = "extra_group_chat_session_id"
    }
}
