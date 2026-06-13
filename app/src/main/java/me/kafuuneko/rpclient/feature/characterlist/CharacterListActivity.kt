package me.kafuuneko.rpclient.feature.characterlist

import android.content.Intent
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
    private var pendingExportCharacterId: Long? = null

    private val importCharacterCardLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        mViewModel.emit(CharacterListUiIntent.ImportCharacterCard(uri))
    }

    private val exportCharacterJsonLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val characterId = pendingExportCharacterId ?: return@registerForActivityResult
        pendingExportCharacterId = null
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
            emit = { mViewModel.emit(this) }
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
                importCharacterCardLauncher.launch(
                    arrayOf("application/json", "text/*", "image/png", "image/*")
                )
            }

            is CharacterListViewEvent.OpenCharacterCardJsonExporter -> {
                pendingExportCharacterId = viewEvent.characterId
                exportCharacterJsonLauncher.launch(viewEvent.fileName)
            }

            else -> super.onReceivedViewEvent(viewEvent)
        }
    }
}
