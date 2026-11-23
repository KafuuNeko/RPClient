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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.flowWithLifecycle
import kotlinx.coroutines.flow.merge
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiIntent
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatViewEvent
import me.kafuuneko.rpclient.feature.groupchat.ui.GroupChatLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent
import me.kafuuneko.rpclient.libs.core.deferFirstDrawUntil
import me.kafuuneko.rpclient.libs.media.CreateImageDocumentContract
import me.kafuuneko.rpclient.libs.media.MessageImageAction
import me.kafuuneko.rpclient.libs.search.SearchNavigation

/** 群聊页面宿主，绑定群聊会话 ID 与 MVI 事件流。 */
class GroupChatActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<GroupChatViewModel>()
    private val mBackgroundFlow by lazy { mViewModel.backgroundFlow.flowWithLifecycle(lifecycle) }
    private var mIsFirstFrameReady = false
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


    /**
     * 收集普通事件及可跨 Activity 重建恢复的分支导航。
     * @return 仍由同一个包装器保证最多消费一次的事件流。
     */
    override fun getViewEventFlow() =
        merge(mViewModel.viewEventFlow, mViewModel.branchNavigationFlow)

    /** 加载页和群聊页共用已解析的所属背景，背景及时就绪时随首帧显示。 */
    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()
        val background by mBackgroundFlow.collectAsState(initial = null)
        // 就绪标记随本次界面提交，不能仅凭后台资源读取完成就开始绘制。
        val canDraw = background != null
            && (uiState is GroupChatUiState.Loading || uiState is GroupChatUiState.Normal)
        SideEffect { mIsFirstFrameReady = canDraw }
        LaunchedEffect(uiState) {
            if (uiState is GroupChatUiState.Finished) finish()
        }
        GroupChatLayout(
            uiState = uiState,
            background = background,
            emitIntent = mViewModel::emit,
        )
    }

    /**
     * 短暂等待首帧背景并初始化群聊。
     * @param savedInstanceState 系统保存的 Activity 状态。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deferFirstDrawUntil { mIsFirstFrameReady }
        mViewModel.emit(
            uiIntent = GroupChatUiIntent.Init(
                sessionId = intent.getStringExtra(EXTRA_SESSION_ID),
                searchMessageId = intent.getLongExtra(SearchNavigation.EXTRA_SOURCE_ID, 0)
                    .takeIf { it > 0 },
            ),
        )
    }

    /**
     * 页面自行绘制群聊背景，避免额外读取通用背景。
     * @return 不启用宿主的通用背景层。
     */
    override fun usesGeneralBackground(): Boolean = false

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
