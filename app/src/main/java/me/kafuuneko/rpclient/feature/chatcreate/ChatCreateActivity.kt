package me.kafuuneko.rpclient.feature.chatcreate

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.chatcreate.presentation.ChatCreateUiIntent
import me.kafuuneko.rpclient.feature.chatcreate.presentation.ChatCreateUiState
import me.kafuuneko.rpclient.feature.chatcreate.ui.ChatCreateLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent

/** 新建单角色会话页面宿主。 */
class ChatCreateActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<ChatCreateViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is ChatCreateUiState.Finished) finish()
        }

        ChatCreateLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) },
        )
    }

    /** 从导航参数读取预选角色，表单初始化由 ViewModel 处理。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val characterId = intent.getLongExtra(EXTRA_CHARACTER_ID, 0L).takeIf { it > 0L }
        mViewModel.emit(ChatCreateUiIntent.Init(characterId = characterId))
    }

    companion object {
        /** 角色内容页传入的预选角色，普通创建入口无需指定。 */
        const val EXTRA_CHARACTER_ID = "character_id"
    }
}
