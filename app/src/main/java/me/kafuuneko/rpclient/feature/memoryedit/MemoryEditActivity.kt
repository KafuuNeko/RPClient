package me.kafuuneko.rpclient.feature.memoryedit

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.memoryedit.presentation.MemoryEditUiIntent
import me.kafuuneko.rpclient.feature.memoryedit.presentation.MemoryEditUiState
import me.kafuuneko.rpclient.feature.memoryedit.presentation.MemoryEditViewEvent
import me.kafuuneko.rpclient.feature.memoryedit.ui.MemoryEditLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.GetContentWithMimeTypes
import me.kafuuneko.rpclient.libs.core.IViewEvent
import me.kafuuneko.rpclient.libs.memory.MemoryTarget

/** 独立记忆编辑宿主；导航参数只携带目标类型和真实 ID。 */
class MemoryEditActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<MemoryEditViewModel>()
    private val mImporter = registerForActivityResult(GetContentWithMimeTypes()) { uri ->
        mViewModel.emit(MemoryEditUiIntent.ImportResult(uri))
    }
    private val mExporter =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            mViewModel.emit(MemoryEditUiIntent.ExportResult(uri))
        }

    /** 配置变化后仍使用原 ViewModel，不重置尚未保存的草稿。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(
            MemoryEditUiIntent.Init(
                type = intent.getStringExtra("memory_type"),
                id = intent.getLongExtra("memory_id", 0),
            ),
        )
    }

    /** 交由统一宿主消费导航及本地化提示。 */
    override fun getViewEventFlow() = mViewModel.viewEventFlow

    /** 系统文件授权与业务导入分离，取消也清理冻结的导出快照。 */
    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        when (viewEvent) {
            MemoryEditViewEvent.OpenImporter -> mImporter.launch(
                arrayOf("application/json", "text/*"),
            )

            MemoryEditViewEvent.OpenExporter -> mExporter.launch("memories.json")
            else -> super.onReceivedViewEvent(viewEvent)
        }
    }

    /** 保留结束前画面，避免返回动画闪烁。 */
    @Composable
    override fun ViewContent() {
        val state by mViewModel.uiStateFlow.collectAsState()
        LaunchedEffect(state) {
            if (state is MemoryEditUiState.Finished) finish()
        }
        MemoryEditLayout(
            state = state,
            emit = { mViewModel.emit(this) },
        )
    }

    companion object {
        /**
         * 构造记忆编辑页的导航参数，四个入口使用同一类型编码，不通过角色名称推断目标。
         *
         * @param target 记忆编辑目标（角色通用、单聊或群聊）
         * @return 包含目标类型与主键 ID 的导航 Bundle
         */
        fun extras(target: MemoryTarget): Bundle = Bundle().apply {
            putLong("memory_id", target.id)
            putString(
                "memory_type",
                when (target) {
                    is MemoryTarget.Character -> "Character"
                    is MemoryTarget.Chat -> "Chat"
                    is MemoryTarget.Group -> "Group"
                },
            )
        }
    }
}
