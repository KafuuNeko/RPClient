package me.kafuuneko.rpclient.feature.characterlist

import android.net.Uri
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
import me.kafuuneko.rpclient.libs.character.CharacterCardExportFormat
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 角色列表页面宿主，桥接角色卡导入导出文件选择器。 */
class CharacterListActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<CharacterListViewModel>()

    /** 记录系统导出选择器对应的角色；结果交付或取消后立即清空，避免串到下一次导出。 */
    private var mPendingExportCharacterId: Long? = null
    private var mPendingArchiveCharacterIds: List<Long>? = null
    private var mPendingArchiveFormat: CharacterCardExportFormat = CharacterCardExportFormat.Json

    /** 把一次性选择的一组角色卡 URI 交给 ViewModel 执行解析和独立事务导入。 */
    private val mImportCharacterCardLauncher = registerForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        mViewModel.emit(CharacterListUiIntent.ImportCharacterCards(uris))
    }

    /** 将 JSON 导出目的 URI 与发起选择时的角色 ID 配对。 */
    private val mExportCharacterJsonLauncher = registerForActivityResult(
        contract = ActivityResultContracts.CreateDocument(CharacterCardExportFormat.Json.mimeType),
    ) { uri ->
        deliverCharacterExportResult(uri, CharacterCardExportFormat.Json)
    }

    private val mExportCharacterPngLauncher = registerForActivityResult(
        contract = ActivityResultContracts.CreateDocument(CharacterCardExportFormat.Png.mimeType),
    ) { uri ->
        deliverCharacterExportResult(uri, CharacterCardExportFormat.Png)
    }

    /** 结果格式由对应 launcher 固定，取消后同样清空目标，避免串到下一次导出。 */
    private fun deliverCharacterExportResult(uri: Uri?, format: CharacterCardExportFormat) {
        val characterId = mPendingExportCharacterId ?: return
        mPendingExportCharacterId = null
        uri ?: return
        mViewModel.emit(CharacterListUiIntent.ExportCharacter(characterId, format, uri))
    }

    private val mExportCharacterArchiveLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val characterIds = mPendingArchiveCharacterIds ?: return@registerForActivityResult
        val format = mPendingArchiveFormat
        mPendingArchiveCharacterIds = null
        mPendingArchiveFormat = CharacterCardExportFormat.Json
        uri ?: return@registerForActivityResult
        mViewModel.emit(CharacterListUiIntent.ExportSelectedCharacters(characterIds, format, uri))
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

    /**
     * 恢复尚未交付系统结果的导出目标，再加载角色列表。
     * @param savedInstanceState 上次创建文档时冻结的导出目标。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 系统可能重建进程，必须在启动阶段的结果回调之前恢复导出目标。
        mPendingExportCharacterId =
            savedInstanceState?.takeIf { it.containsKey(KEY_EXPORT_ID) }?.getLong(KEY_EXPORT_ID)
        mPendingArchiveCharacterIds = savedInstanceState?.getLongArray(KEY_ARCHIVE_IDS)?.toList()
        mPendingArchiveFormat = CharacterCardExportFormat.entries.firstOrNull {
            it.name == savedInstanceState?.getString(KEY_ARCHIVE_FORMAT)
        } ?: CharacterCardExportFormat.Json
        mViewModel.emit(CharacterListUiIntent.Init)
    }

    /** 只保存发起选择时的标识，用户取消或交付结果后即清空。 */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mPendingExportCharacterId?.let { outState.putLong(KEY_EXPORT_ID, it) }
        mPendingArchiveCharacterIds?.let {
            outState.putString(KEY_ARCHIVE_FORMAT, mPendingArchiveFormat.name)
            outState.putLongArray(
                KEY_ARCHIVE_IDS,
                it.toLongArray()
            )
        }
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(CharacterListUiIntent.Resume)
    }

    /** 仅桥接系统选择器，卡片编码与头像回退均交给 ViewModel 和 Repository。 */
    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        // 不在宿主重新判断头像，沿用用户已经选择的格式与冻结目标。
        when (viewEvent) {
            CharacterListViewEvent.OpenCharacterCardImporter -> {
                mImportCharacterCardLauncher.launch(
                    arrayOf(
                        "application/json",
                        "text/*",
                        "image/png",
                        "image/*",
                        "application/zip",
                        "application/x-zip-compressed"
                    )
                )
            }

            is CharacterListViewEvent.OpenCharacterCardExporter -> {
                mPendingExportCharacterId = viewEvent.characterId
                when (viewEvent.format) {
                    CharacterCardExportFormat.Png -> mExportCharacterPngLauncher.launch(viewEvent.fileName)
                    CharacterCardExportFormat.Json -> mExportCharacterJsonLauncher.launch(viewEvent.fileName)
                }
            }

            is CharacterListViewEvent.OpenCharacterArchiveExporter -> {
                mPendingArchiveCharacterIds = viewEvent.characterIds
                mPendingArchiveFormat = viewEvent.format
                mExportCharacterArchiveLauncher.launch(viewEvent.fileName)
            }

            else -> super.onReceivedViewEvent(viewEvent)
        }
    }

    private companion object {
        const val KEY_EXPORT_ID = "pendingExportCharacterId"
        const val KEY_ARCHIVE_FORMAT = "pendingArchiveFormat"
        const val KEY_ARCHIVE_IDS = "pendingArchiveCharacterIds"
    }
}
