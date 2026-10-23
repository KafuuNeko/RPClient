package me.kafuuneko.rpclient.feature.characterlist

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.characterlist.presentation.CharacterListUiIntent
import me.kafuuneko.rpclient.feature.characterlist.presentation.CharacterListUiState
import me.kafuuneko.rpclient.feature.characterlist.presentation.CharacterListViewEvent
import me.kafuuneko.rpclient.feature.characterlist.ui.CharacterListLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 角色列表页面宿主，桥接角色卡导入导出文件选择器。 */
class CharacterListActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<CharacterListViewModel>()

    /** 记录系统导出选择器对应的角色；结果交付或取消后立即清空，避免串到下一次导出。 */
    private var mPendingExportCharacterId: Long? = null

    /** 把一次性选择的一组角色卡 URI 交给 ViewModel 执行解析和独立事务导入。 */
    private val mImportCharacterCardLauncher = registerForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        mViewModel.emit(CharacterListUiIntent.ImportCharacterCards(uris))
    }

    /** 将 JSON 导出目的 URI 与发起选择时的角色 ID 配对。 */
    private val mExportCharacterJsonLauncher = registerForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val characterId = mPendingExportCharacterId ?: return@registerForActivityResult
        mPendingExportCharacterId = null
        uri ?: return@registerForActivityResult
        mViewModel.emit(CharacterListUiIntent.ExportCharacterJson(characterId, uri))
    }

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is CharacterListUiState.Finished) finish()
        }

        CharacterListLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(CharacterListUiIntent.Init)
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(CharacterListUiIntent.Resume)
    }

    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        when (viewEvent) {
            CharacterListViewEvent.OpenCharacterCardImporter -> {
                mImportCharacterCardLauncher.launch(
                    input = arrayOf("application/json", "text/*", "image/png", "image/*"),
                )
            }

            is CharacterListViewEvent.OpenCharacterCardJsonExporter -> {
                mPendingExportCharacterId = viewEvent.characterId
                mExportCharacterJsonLauncher.launch(viewEvent.fileName)
            }

            else -> super.onReceivedViewEvent(viewEvent)
        }
    }
}
