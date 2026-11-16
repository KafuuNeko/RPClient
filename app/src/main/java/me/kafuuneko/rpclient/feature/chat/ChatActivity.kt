package me.kafuuneko.rpclient.feature.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiIntent
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatViewEvent
import me.kafuuneko.rpclient.feature.chat.ui.ChatLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent
import me.kafuuneko.rpclient.libs.core.deferFirstDrawUntil
import me.kafuuneko.rpclient.libs.media.CreateImageDocumentContract
import me.kafuuneko.rpclient.libs.media.MessageImageAction
import me.kafuuneko.rpclient.libs.search.SearchNavigation

/** 单角色聊天页面宿主，绑定会话 ID、状态流和一次性事件。 */
class ChatActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<ChatViewModel>()
    private val mBackgroundFlow by lazy { mViewModel.backgroundFlow.flowWithLifecycle(lifecycle) }
    private var mIsFirstFrameReady = false
    private val mImagePicker = registerForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4),
    ) { uris ->
        mViewModel.emit(ChatUiIntent.ImageAction(MessageImageAction.Picked(uris)))
    }
    private val mImageSaver = registerForActivityResult(
        contract = CreateImageDocumentContract(),
    ) { uri ->
        uri?.let { uri -> mViewModel.emit(ChatUiIntent.ImageAction(MessageImageAction.SaveResult(uri))) }
    }


    /** 导出目标由系统文档选择器创建，Activity 只回传 URI。 */
    private val mChatExporterLauncher = registerForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-ndjson"),
    ) { uri ->
        uri?.let { uri -> mViewModel.emit(ChatUiIntent.ExportChatResult(uri)) }
    }

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    /** 加载页和会话页共用已解析的所属背景，首帧在 Compose 应用资源后放行。 */
    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()
        val background by mBackgroundFlow.collectAsState(initial = null)

        // 等待当前页面已经采用背景，避免资源流先就绪但界面仍绘制空底板。
        val canDraw = background != null
            && (uiState is ChatUiState.Loading || uiState is ChatUiState.Normal)
        SideEffect { mIsFirstFrameReady = canDraw }
        LaunchedEffect(uiState) {
            if (uiState is ChatUiState.Finished) finish()
        }

        ChatLayout(
            uiState = uiState,
            background = background,
            emit = { mViewModel.emit(this) },
        )
    }

    /**
     * 建立首帧就绪约束后初始化会话。
     * @param savedInstanceState 系统保存的 Activity 状态。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deferFirstDrawUntil { mIsFirstFrameReady }
        mViewModel.emit(
            uiIntent = ChatUiIntent.Init(
                sessionId = intent.getStringExtra(EXTRA_SESSION_ID),
                searchMessageId = intent.getLongExtra(SearchNavigation.EXTRA_SOURCE_ID, 0)
                    .takeIf { it > 0 },
            ),
        )
    }

    /**
     * 页面自行绘制对话背景，避免额外读取通用背景。
     * @return 不启用宿主的通用背景层。
     */
    override fun usesGeneralBackground(): Boolean = false

    override fun onResume() {
        super.onResume()
        mViewModel.emit(ChatUiIntent.Resume)
    }

    /**
     * 分发图片选择、保存及其他页面宿主事件。
     *
     * @param viewEvent ViewModel 已准备好参数的一次性系统操作。
     */
    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        // 图片保存使用本次原图的格式元数据，其他导出沿用各自的文档合约。
        when (viewEvent) {
            ChatViewEvent.PickImages -> mImagePicker.launch(
                input = PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly),
            )

            is ChatViewEvent.SaveImage -> mImageSaver.launch(viewEvent.metadata)
            is ChatViewEvent.CopyText -> copyText(viewEvent.text)
            is ChatViewEvent.OpenSession -> openSession(viewEvent.sessionId)
            is ChatViewEvent.OpenChatExporter -> {
                mChatExporterLauncher.launch(viewEvent.fileName)
            }

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
            },
        )
        finish()
    }

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
    }
}
