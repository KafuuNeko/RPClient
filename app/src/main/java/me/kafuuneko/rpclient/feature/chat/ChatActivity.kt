package me.kafuuneko.rpclient.feature.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiIntent
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatViewEvent
import me.kafuuneko.rpclient.feature.chat.ui.ChatLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 单角色聊天页面宿主，绑定会话 ID、状态流和一次性事件。 */
class ChatActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<ChatViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is ChatUiState.Finished) finish()
        }

        ChatLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(
            ChatUiIntent.Init(
                sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
            )
        )
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(ChatUiIntent.Resume)
    }

    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        when (viewEvent) {
            is ChatViewEvent.CopyText -> copyText(viewEvent.text)
            is ChatViewEvent.OpenSession -> openSession(viewEvent.sessionId)
            else -> super.onReceivedViewEvent(viewEvent)
        }
    }

    private fun copyText(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.message), text))
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun openSession(sessionId: String) {
        startActivity(
            Intent(this, ChatActivity::class.java).apply {
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
        )
        finish()
    }

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
    }
}
