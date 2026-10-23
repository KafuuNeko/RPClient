package me.kafuuneko.rpclient.feature.worldbooklist

import android.os.Bundle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.worldbookedit.WorldBookEditActivity
import me.kafuuneko.rpclient.feature.worldbooklist.model.WorldBookListItem
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListDialogState
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListLoadState
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListUiIntent
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListUiState
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListViewEvent
import me.kafuuneko.rpclient.libs.character.CharacterBookImport
import me.kafuuneko.rpclient.libs.character.LorebookImportPolicy
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 世界书列表页状态持有者。
 *
 * 核心职责：
 * - 聚合全部世界书实体及其条目数量，驱动列表展示；
 * - 调度新建与编辑世界书导航跳转；
 * - 异步解析并导入世界书文件（JSON），支持低 Token 预算确认弹窗拦截与预算覆盖策略；
 * - 异步导出指定世界书至本地文件。
 */
class WorldBookListViewModel : CoreViewModelWithEvent<WorldBookListUiIntent, WorldBookListUiState>(
    WorldBookListUiState.None
), KoinComponent {
    private val mLorebookRepository by inject<LorebookRepository>()
    private var mTransferJob: Job? = null
    private var mTransferToken: Any? = null
    private var mRefreshGeneration: Long = 0L
    private var mPendingImport: CharacterBookImport? = null

    /** 初始化世界书列表，进入加载中状态并拉取数据库数据。 */
    @UiIntentObserver(WorldBookListUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<WorldBookListUiState.None>()) return
        WorldBookListUiState.Normal(loadState = WorldBookListLoadState.Loading).setup()
        refreshLorebooks()
    }

    /** 页面恢复可见时刷新列表数据（导入或导出传输任务进行中时不打断）。 */
    @UiIntentObserver(WorldBookListUiIntent.Resume::class)
    private suspend fun onResume() {
        if (!isStateOf<WorldBookListUiState.Normal>()) return
        // Activity Result 先于 onResume 交付；导入或导出任务负责结束 Loading
        if (mTransferJob?.isActive == true) return
        refreshLorebooks()
    }

    /** 处理返回操作，取消未完成的传输作业并迁移至 Finished 状态。 */
    @UiIntentObserver(WorldBookListUiIntent.Back::class)
    private fun onBack() {
        if (isStateOf<WorldBookListUiState.Finished>()) return
        mRefreshGeneration++
        mTransferJob?.cancel()
        mPendingImport = null
        WorldBookListUiState.finished(uiStateFlow.value).setup()
    }

    /** 打开新建世界书编辑页面。 */
    @UiIntentObserver(WorldBookListUiIntent.CreateWorldBook::class)
    private fun onCreateWorldBook() {
        if (!isStateOf<WorldBookListUiState.Normal>()) return
        AppViewEvent.StartActivity(WorldBookEditActivity::class.java).tryEmit()
    }

    /**
     * 打开指定世界书的编辑页面。
     *
     * @param intent 包含目标世界书 ID 的意图
     */
    @UiIntentObserver(WorldBookListUiIntent.EditWorldBook::class)
    private fun onEditWorldBook(intent: WorldBookListUiIntent.EditWorldBook) {
        val uiState = getOrNull<WorldBookListUiState.Normal>() ?: return
        if (uiState.lorebooks.none { it.id == intent.lorebookId }) return
        AppViewEvent.StartActivity(
            activity = WorldBookEditActivity::class.java,
            extras = Bundle().apply {
                putLong(WorldBookEditActivity.EXTRA_LOREBOOK_ID, intent.lorebookId)
            },
        ).tryEmit()
    }

    /** 从数据库异步拉取全部世界书及其条目数量，并更新 UI 状态。 */
    private suspend fun refreshLorebooks() {
        if (!isStateOf<WorldBookListUiState.Normal>()) return
        val generation = ++mRefreshGeneration
        // 在 IO 线程一次读取世界书与条目计数，避免按世界书逐项查询。
        val items = withContext(Dispatchers.IO) {
            val lorebooks = mLorebookRepository.getAllLorebooks()
            val entryCounts = mLorebookRepository.getLorebookEntryCounts()
            lorebooks.map { lorebook ->
                WorldBookListItem.from(
                    lorebook = lorebook,
                    entryCount = entryCounts[lorebook.id] ?: 0,
                )
            }
        }
        // 校验请求代数，丢弃过期的异步刷新结果
        if (generation != mRefreshGeneration) return
        val current = getOrNull<WorldBookListUiState.Normal>() ?: return
        // 更新世界书列表并解除加载状态
        current.copy(
            loadState = WorldBookListLoadState.None,
            lorebooks = items,
        ).setup()
    }

    /** 触发系统文件选择器以导入世界书 JSON 文件。 */
    @UiIntentObserver(WorldBookListUiIntent.ImportWorldBookClick::class)
    private fun onImportWorldBookClick() {
        val uiState = getOrNull<WorldBookListUiState.Normal>() ?: return
        if (uiState.dialogState != WorldBookListDialogState.None) return
        WorldBookListViewEvent.OpenWorldBookImporter.tryEmit()
    }

    /**
     * 解析独立世界书，并在低固定预算需要确认时暂存导入草稿。
     *
     * 确认前不写数据库；任务 token 只允许当前导入或导出任务结束 Loading，
     * 避免 Activity Result 与 onResume 的刷新顺序造成状态闪回。
     *
     * @param intent 包含世界书文件 URI 的导入意图
     */
    @UiIntentObserver(WorldBookListUiIntent.ImportWorldBook::class)
    private fun onImportWorldBook(intent: WorldBookListUiIntent.ImportWorldBook) {
        val uiState = getOrNull<WorldBookListUiState.Normal>() ?: return
        if (uiState.loadState != WorldBookListLoadState.None || mTransferJob?.isActive == true) return
        // 生成本次传输的唯一标识 Token
        val token = Any()
        mTransferToken = token
        // 进入加载中状态
        uiState.copy(loadState = WorldBookListLoadState.Loading).setup()
        mTransferJob = viewModelScope.launch {
            try {
                // 在 IO 线程解析 URI 对应的世界书文件
                val parsed = withContext(Dispatchers.IO) {
                    mLorebookRepository.readImportFromUri(intent.uri)
                }
                // 检查是否包含低于阈值的固定 Token 预算，若有则弹出确认对话框
                if (LorebookImportPolicy.requiresLowBudgetConfirmation(parsed)) {
                    mPendingImport = parsed
                    getOrNull<WorldBookListUiState.Normal>()?.copy(
                        loadState = WorldBookListLoadState.None,
                        dialogState = WorldBookListDialogState.LowTokenBudgetConfirm(
                            importedTokenBudget = parsed.lorebook.tokenBudget,
                        ),
                    )?.setup()
                } else {
                    // 直接执行数据库入库
                    saveImport(parsed)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // 导入失败弹出 Toast 提示并刷新列表
                AppViewEvent.PopupToastMessageByResId(R.string.import_world_book_failed).tryEmit()
                refreshLorebooks()
            } finally {
                // 结束传输状态
                finishTransfer(token)
            }
        }
    }

    /** 用户确认将低预算世界书改为跟随全局预算并继续导入。 */
    @UiIntentObserver(WorldBookListUiIntent.ImportWithGlobalBudget::class)
    private fun onImportWithGlobalBudget() {
        continuePendingImport(followGlobal = true)
    }

    /** 用户确认保留原文件的固定预算并继续导入。 */
    @UiIntentObserver(WorldBookListUiIntent.ImportWithOriginalBudget::class)
    private fun onImportWithOriginalBudget() {
        continuePendingImport(followGlobal = false)
    }

    /**
     * 消费一次待确认世界书，并按用户选择保留固定预算或改为跟随全局预算。
     *
     * @param followGlobal 是否改为跟随全局预算
     */
    private fun continuePendingImport(followGlobal: Boolean) {
        val uiState = getOrNull<WorldBookListUiState.Normal>() ?: return
        if (uiState.dialogState !is WorldBookListDialogState.LowTokenBudgetConfirm) return
        val parsed = mPendingImport ?: return
        mPendingImport = null
        val token = Any()
        mTransferToken = token
        // 关闭弹窗并重置为加载中状态
        uiState.copy(
            loadState = WorldBookListLoadState.Loading,
            dialogState = WorldBookListDialogState.None,
        ).setup()
        mTransferJob = viewModelScope.launch {
            try {
                // 根据用户选择应用预算策略并保存入库
                saveImport(LorebookImportPolicy.resolveBudget(parsed, followGlobal))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                AppViewEvent.PopupToastMessageByResId(R.string.import_world_book_failed).tryEmit()
                refreshLorebooks()
            } finally {
                // 结束传输状态
                finishTransfer(token)
            }
        }
    }

    /** 将解析后的世界书及条目数据写入数据库，并弹出成功 Toast。 */
    private suspend fun saveImport(parsed: CharacterBookImport) {
        withContext(Dispatchers.IO) { mLorebookRepository.saveImport(parsed) }
        AppViewEvent.PopupToastMessageByResId(R.string.import_world_book_success).tryEmit()
        refreshLorebooks()
    }

    /** 准备导出指定世界书，并触发文件创建器。 */
    @UiIntentObserver(WorldBookListUiIntent.ExportWorldBookClick::class)
    private fun onExportWorldBookClick(intent: WorldBookListUiIntent.ExportWorldBookClick) {
        val uiState = getOrNull<WorldBookListUiState.Normal>() ?: return
        val lorebook = uiState.lorebooks.firstOrNull { it.id == intent.lorebookId } ?: return
        WorldBookListViewEvent.OpenWorldBookExporter(
            lorebookId = intent.lorebookId,
            fileName = "${lorebook.name.ifBlank { "worldbook" }}.json",
        ).tryEmit()
    }

    /**
     * 将指定世界书导出至目标 URI。
     *
     * @param intent 包含世界书 ID 与写入 URI 的意图
     */
    @UiIntentObserver(WorldBookListUiIntent.ExportWorldBook::class)
    private fun onExportWorldBook(intent: WorldBookListUiIntent.ExportWorldBook) {
        val uiState = getOrNull<WorldBookListUiState.Normal>() ?: return
        if (uiState.loadState != WorldBookListLoadState.None || mTransferJob?.isActive == true) return
        val token = Any()
        mTransferToken = token
        // 进入加载中状态
        uiState.copy(loadState = WorldBookListLoadState.Loading).setup()
        mTransferJob = viewModelScope.launch {
            try {
                // 在 IO 线程将世界书及其条目序列化写入文件
                withContext(Dispatchers.IO) {
                    mLorebookRepository.exportToUri(intent.lorebookId, intent.uri)
                }
                AppViewEvent.PopupToastMessageByResId(R.string.export_world_book_success).tryEmit()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                AppViewEvent.PopupToastMessageByResId(R.string.export_world_book_failed).tryEmit()
            } finally {
                // 结束传输状态
                finishTransfer(token)
            }
        }
    }

    /**
     * 仅由当前传输任务清理 Loading；页面结束后不再发布普通状态。
     *
     * @param token 传输任务创建时绑定的唯一标识 Token
     */
    private fun finishTransfer(token: Any) {
        if (mTransferToken !== token) return
        mTransferToken = null
        mTransferJob = null
        val current = getOrNull<WorldBookListUiState.Normal>() ?: return
        if (current.loadState == WorldBookListLoadState.Loading) {
            current.copy(loadState = WorldBookListLoadState.None).setup()
        }
    }
}
