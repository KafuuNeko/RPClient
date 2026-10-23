package me.kafuuneko.rpclient.feature.groupchat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.flowWithLifecycle
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.appearance.PageBackgroundViewModel
import me.kafuuneko.rpclient.feature.appearance.presentation.PageBackgroundUiState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiIntent
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatViewEvent
import me.kafuuneko.rpclient.feature.groupchat.ui.GroupChatLayout
import me.kafuuneko.rpclient.libs.appearance.BackgroundPage
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent
import me.kafuuneko.rpclient.libs.media.CreateImageDocumentContract
import me.kafuuneko.rpclient.libs.media.MessageImageAction
import me.kafuuneko.rpclient.libs.search.SearchNavigation

/** 群聊页面宿主，绑定群聊会话 ID 与 MVI 事件流。 */
class GroupChatActivity : CoreActivityWithEvent() {
    private val mBackgroundViewModel by viewModels<PageBackgroundViewModel>()
    private val mBackgroundFlow by lazy {
        mBackgroundViewModel.observe(BackgroundPage.Group).flowWithLifecycle(lifecycle)
    }
    private val mViewModel by viewModels<GroupChatViewModel>()
    private val mImagePicker = registerForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4),
    ) { uris -> mViewModel.emit(GroupChatUiIntent.ImageAction(MessageImageAction.Picked(uris))) }
    private val mImageSaver = registerForActivityResult(
        contract = CreateImageDocumentContract(),
    ) { uri ->
        uri?.let { uri ->
            mViewModel.emit(
                GroupChatUiIntent.ImageAction(
                    MessageImageAction.SaveResult(
                        uri
                    )
                )
            )
        }
    }


    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()
        val background by mBackgroundFlow.collectAsState(PageBackgroundUiState())
        LaunchedEffect(uiState) {
            if (uiState is GroupChatUiState.Finished) finish()
        }
        GroupChatLayout(
            uiState = uiState,
            background = background,
            emitIntent = mViewModel::emit,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(
            uiIntent = GroupChatUiIntent.Init(
                sessionId = intent.getStringExtra(EXTRA_SESSION_ID),
                searchMessageId = intent.getLongExtra(SearchNavigation.EXTRA_SOURCE_ID, 0)
                    .takeIf { it > 0 },
            ),
        )
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(GroupChatUiIntent.Resume)
    }

    /**
     * 分发图片选择、保存及其他页面宿主事件。
     *
     * @param viewEvent ViewModel 已准备好参数的一次性系统操作。
     */
    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        when (viewEvent) {
            GroupChatViewEvent.PickImages -> mImagePicker.launch(
                input = PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly),
            )

            is GroupChatViewEvent.SaveImage -> mImageSaver.launch(viewEvent.metadata)
            is GroupChatViewEvent.CopyText -> copyText(viewEvent.text)
            else -> super.onReceivedViewEvent(viewEvent)
        }
    }

    private fun copyText(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.message), text))
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_SESSION_ID = "extra_group_chat_session_id"
    }
}
