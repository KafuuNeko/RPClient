package me.kafuuneko.rpclient.feature.worldbooklist

import android.net.Uri
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
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
import me.kafuuneko.rpclient.libs.character.LorebookArchiveLimits
import me.kafuuneko.rpclient.libs.character.LorebookArchiveLimitException
import me.kafuuneko.rpclient.libs.character.LorebookImportBatch
import me.kafuuneko.rpclient.libs.character.LorebookImportPolicy
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 世界书列表页状态持有者。
 * - 管理列表、多选及删除确认，统一协调文件选择器与导航。
 * - 批量导入按文件容错，低预算统一确认后逐本事务保存。
 * - 传输任务互斥，过期刷新不能覆盖任务状态。
 */
class WorldBookListViewModel : CoreViewModelWithEvent<WorldBookListUiIntent, WorldBookListUiState>(
    WorldBookListUiState.None
), KoinComponent {
    private val mLorebookRepository by inject<LorebookRepository>()
    private var mTransferJob: Job? = null
    private var mTransferToken: Any? = null
    private var mRefreshGeneration = 0L
    private var mPendingImport: LorebookImportBatch? = null

    /** 初始化页面并读取列表。 */
    @UiIntentObserver(WorldBookListUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<WorldBookListUiState.None>()) return
        WorldBookListUiState.Normal(loadState = WorldBookListLoadState.Loading).setup()
        refreshLorebooks()
    }

    /** 返回页面时刷新列表，传输任务自行负责最终刷新。 */
    @UiIntentObserver(WorldBookListUiIntent.Resume::class)
    private suspend fun onResume() {
        if (!isStateOf<WorldBookListUiState.Normal>() || mTransferJob?.isActive == true) return
        refreshLorebooks()
    }

    /** 返回时先退出选择，持久化期间保持页面稳定。 */
    @UiIntentObserver(WorldBookListUiIntent.Back::class)
    private fun onBack() {
        val state = idleState() ?: return
        if (state.selectedLorebookIds.isNotEmpty()) {
            onClearWorldBookSelection()
            return
        }
        mRefreshGeneration++
        mPendingImport = null
        WorldBookListUiState.finished(state).setup()
    }

    /** 打开新建世界书页面。 */
    @UiIntentObserver(WorldBookListUiIntent.CreateWorldBook::class)
    private fun onCreateWorldBook() {
        val state = idleState() ?: return
        if (state.selectedLorebookIds.isNotEmpty()) return
        AppViewEvent.StartActivity(WorldBookEditActivity::class.java).tryEmit()
    }

    /** 多选时切换选择，普通模式打开世界书。 */
    @UiIntentObserver(WorldBookListUiIntent.EditWorldBook::class)
    private fun onEditWorldBook(intent: WorldBookListUiIntent.EditWorldBook) {
        val state = idleState() ?: return
        // 多选模式复用整卡点击，不触发编辑页的自动保存导航。
        if (state.selectedLorebookIds.isNotEmpty()) {
            onToggleWorldBookSelection(WorldBookListUiIntent.ToggleWorldBookSelection(intent.lorebookId))
            return
        }
        if (state.lorebooks.none { it.id == intent.lorebookId }) return
        AppViewEvent.StartActivity(
            activity = WorldBookEditActivity::class.java,
            extras = Bundle().apply {
                putLong(
                    WorldBookEditActivity.EXTRA_LOREBOOK_ID,
                    intent.lorebookId
                )
            },
        ).tryEmit()
    }

    /** 长按世界书进入多选。 */
    @UiIntentObserver(WorldBookListUiIntent.BeginWorldBookSelection::class)
    private fun onBeginWorldBookSelection(intent: WorldBookListUiIntent.BeginWorldBookSelection) {
        val state = idleState() ?: return
        if (state.lorebooks.none { it.id == intent.lorebookId }) return
        state.copy(selectedLorebookIds = setOf(intent.lorebookId)).setup()
    }

    /** 切换单本世界书的选择。 */
    @UiIntentObserver(WorldBookListUiIntent.ToggleWorldBookSelection::class)
    private fun onToggleWorldBookSelection(intent: WorldBookListUiIntent.ToggleWorldBookSelection) {
        val state = idleState() ?: return
        if (state.lorebooks.none { it.id == intent.lorebookId }) return
        val selected = state.selectedLorebookIds
        state.copy(selectedLorebookIds = if (intent.lorebookId in selected) selected - intent.lorebookId else selected + intent.lorebookId)
            .setup()
    }

    /** 全选列表或清除全选。 */
    @UiIntentObserver(WorldBookListUiIntent.ToggleAllWorldBookSelection::class)
    private fun onToggleAllWorldBookSelection() {
        val state = idleState() ?: return
        val ids = state.lorebooks.mapTo(mutableSetOf()) { it.id }
        state.copy(selectedLorebookIds = if (state.selectedLorebookIds.containsAll(ids)) emptySet() else ids)
            .setup()
    }

    /** 清空选择并退出多选模式。 */
    @UiIntentObserver(WorldBookListUiIntent.ClearWorldBookSelection::class)
    private fun onClearWorldBookSelection() {
        val state = idleState() ?: return
        state.copy(selectedLorebookIds = emptySet()).setup()
    }

    /** 冻结即将删除的世界书集合。 */
    @UiIntentObserver(WorldBookListUiIntent.DeleteSelectedWorldBooksClick::class)
    private fun onDeleteSelectedWorldBooksClick() {
        val state = idleState() ?: return
        if (state.selectedLorebookIds.isEmpty()) return
        state.copy(dialogState = WorldBookListDialogState.DeleteSelectedConfirm(state.selectedLorebookIds))
            .setup()
    }

    /** 事务删除世界书及条目，成功后清理已删除的选择。 */
    @UiIntentObserver(WorldBookListUiIntent.ConfirmDeleteSelectedWorldBooks::class)
    private fun onConfirmDeleteSelectedWorldBooks() {
        val state = getOrNull<WorldBookListUiState.Normal>() ?: return
        val dialog = state.dialogState as? WorldBookListDialogState.DeleteSelectedConfirm ?: return
        launchTransfer(R.string.delete_selected_items_failed) {
            withContext(Dispatchers.IO) { mLorebookRepository.deleteLorebooks(dialog.lorebookIds.toList()) }
            refreshLorebooks()
        }
    }

    /** 请求多文件选择器。 */
    @UiIntentObserver(WorldBookListUiIntent.ImportWorldBookClick::class)
    private fun onImportWorldBookClick() {
        val state = idleState() ?: return
        if (state.selectedLorebookIds.isNotEmpty()) return
        WorldBookListViewEvent.OpenWorldBookImporter.tryEmit()
    }

    /** 顺序解析文档；整批低预算只询问一次，确认前不写库。 */
    @UiIntentObserver(WorldBookListUiIntent.ImportWorldBooks::class)
    private fun onImportWorldBooks(intent: WorldBookListUiIntent.ImportWorldBooks) {
        if (idleState() == null || intent.uris.isEmpty()) return
        launchTransfer(R.string.import_world_book_failed) {
            val batch = readImportBatch(intent.uris.distinct())
            val lowBudgetBooks =
                batch.drafts.filter(LorebookImportPolicy::requiresLowBudgetConfirmation)
            // 草稿只由 ViewModel 持有，弹窗只包含汇总信息。
            if (lowBudgetBooks.isNotEmpty()) {
                mPendingImport = batch
                getOrNull<WorldBookListUiState.Normal>()?.copy(
                    dialogState = WorldBookListDialogState.LowTokenBudgetConfirm(
                        importedTokenBudget = lowBudgetBooks.minOf { it.lorebook.tokenBudget },
                        affectedBookCount = lowBudgetBooks.size,
                    ),
                )?.setup()
            } else {
                saveImportBatch(batch, followGlobal = false)
            }
        }
    }

    /** 对低预算世界书统一应用全局预算。 */
    @UiIntentObserver(WorldBookListUiIntent.ImportWithGlobalBudget::class)
    private fun onImportWithGlobalBudget() = continuePendingImport(followGlobal = true)

    /** 保留原文件预算并继续导入。 */
    @UiIntentObserver(WorldBookListUiIntent.ImportWithOriginalBudget::class)
    private fun onImportWithOriginalBudget() = continuePendingImport(followGlobal = false)

    /** 消费一次待确认批次；其他预算的世界书不受覆盖。 */
    private fun continuePendingImport(followGlobal: Boolean) {
        val state = getOrNull<WorldBookListUiState.Normal>() ?: return
        if (state.dialogState !is WorldBookListDialogState.LowTokenBudgetConfirm) return
        val batch = mPendingImport ?: return
        launchTransfer(R.string.import_world_book_failed) {
            mPendingImport = null
            saveImportBatch(batch, followGlobal)
        }
    }

    /** 汇总文件级失败，并限制整个待确认批次的内存与数量。 */
    private suspend fun readImportBatch(uris: List<Uri>): LorebookImportBatch {
        val drafts = mutableListOf<CharacterBookImport>()
        var failures = 0
        var total = 0
        var bytes = 0L
        val limits = LorebookArchiveLimits()
        for ((index, uri) in uris.withIndex()) {
            updateImportProgress(saving = false, completed = index, total = uris.size)
            try {
                val result = mLorebookRepository.readImportItemsFromUri(uri)
                total += result.totalCount
                // 超出批次预算的文档计入失败，已接受的有效文档仍可继续导入。
                if (bytes + result.decodedBytes > limits.totalJsonBytes || drafts.size + result.drafts.size > limits.entries) {
                    failures += result.totalCount
                    continue
                }
                drafts += result.drafts
                bytes += result.decodedBytes
                failures += result.failureCount
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                failures++
                total++
            }
        }
        return LorebookImportBatch(drafts, failures, total, bytes)
    }

    /** 每本世界书独立提交，失败不回滚其他成功项，最终只刷新一次列表。 */
    private suspend fun saveImportBatch(batch: LorebookImportBatch, followGlobal: Boolean) {
        var success = 0
        var failures = batch.failureCount
        for ((index, draft) in batch.drafts.withIndex()) {
            updateImportProgress(saving = true, completed = index, total = batch.drafts.size)
            try {
                val resolved = if (LorebookImportPolicy.requiresLowBudgetConfirmation(draft)) {
                    LorebookImportPolicy.resolveBudget(draft, followGlobal)
                } else draft
                mLorebookRepository.saveImport(resolved)
                success++
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                failures++
            }
        }
        // 结果进入可恢复状态；单文件继续使用原有的简洁提示。
        refreshLorebooks()
        if (batch.totalCount == 1) {
            AppViewEvent.PopupToastMessageByResId(
                if (success == 1) R.string.import_world_book_success else R.string.import_world_book_failed
            ).tryEmit()
        } else {
            getOrNull<WorldBookListUiState.Normal>()?.copy(
                dialogState = WorldBookListDialogState.BatchImportResult(success, failures)
            )?.setup()
        }
    }

    /** 发布文件读取或保存阶段的可见进度。 */
    private fun updateImportProgress(saving: Boolean, completed: Int, total: Int) {
        getOrNull<WorldBookListUiState.Normal>()?.copy(
            loadState = WorldBookListLoadState.Importing(saving, completed, total)
        )?.setup()
    }

    /** 关闭结果或删除确认弹窗，不丢弃待确认的导入草稿。 */
    @UiIntentObserver(WorldBookListUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val state = getOrNull<WorldBookListUiState.Normal>() ?: return
        if (state.dialogState is WorldBookListDialogState.LowTokenBudgetConfirm) return
        state.copy(dialogState = WorldBookListDialogState.None).setup()
    }

    /** 请求单本 JSON 导出。 */
    @UiIntentObserver(WorldBookListUiIntent.ExportWorldBookClick::class)
    private fun onExportWorldBookClick(intent: WorldBookListUiIntent.ExportWorldBookClick) {
        val state = idleState() ?: return
        val book = state.lorebooks.firstOrNull { it.id == intent.lorebookId } ?: return
        WorldBookListViewEvent.OpenWorldBookExporter(
            book.id,
            "${book.name.ifBlank { "worldbook" }}.json"
        ).tryEmit()
    }

    /** 写入单本 JSON。 */
    @UiIntentObserver(WorldBookListUiIntent.ExportWorldBook::class)
    private fun onExportWorldBook(intent: WorldBookListUiIntent.ExportWorldBook) {
        if (idleState() == null) return
        launchTransfer(R.string.export_world_book_failed) {
            mLorebookRepository.exportToUri(intent.lorebookId, intent.uri)
            AppViewEvent.PopupToastMessageByResId(R.string.export_world_book_success).tryEmit()
        }
    }

    /** 冻结选择并请求 ZIP 导出，数量在启动系统选择器前校验。 */
    @UiIntentObserver(WorldBookListUiIntent.ExportSelectedWorldBooksClick::class)
    private fun onExportSelectedWorldBooksClick() {
        val state = idleState() ?: return
        val ids = state.selectedLorebookIds.toList()
        if (ids.isEmpty()) return
        if (ids.size > LorebookArchiveLimits.MAX_ENTRIES) {
            AppViewEvent.PopupToastMessageByResId(R.string.world_book_archive_limit).tryEmit()
            return
        }
        // 名称使用时间戳，避免把世界书内容暴露在外部文档标题中。
        val timestamp = OffsetDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-'UTC'XX", Locale.ROOT))
        WorldBookListViewEvent.OpenWorldBookArchiveExporter(
            ids,
            "RPClient-WorldBooks-$timestamp.zip"
        ).tryEmit()
    }

    /** 写入所选世界书 ZIP。 */
    @UiIntentObserver(WorldBookListUiIntent.ExportSelectedWorldBooks::class)
    private fun onExportSelectedWorldBooks(intent: WorldBookListUiIntent.ExportSelectedWorldBooks) {
        if (idleState() == null || intent.lorebookIds.isEmpty()) return
        launchTransfer(R.string.export_world_book_failed) {
            mLorebookRepository.exportArchiveToUri(intent.lorebookIds, intent.uri)
            AppViewEvent.PopupToastMessageByResId(R.string.export_world_book_success).tryEmit()
        }
    }

    /** 只有空闲且没有弹窗时允许更改选择或启动外部动作。 */
    private fun idleState(): WorldBookListUiState.Normal? {
        val state = getOrNull<WorldBookListUiState.Normal>() ?: return null
        return state.takeIf {
            it.loadState == WorldBookListLoadState.None &&
                it.dialogState == WorldBookListDialogState.None && mTransferJob?.isActive != true
        }
    }

    /** 统一串行化长任务，令牌只允许当前任务清理加载状态。 */
    private fun launchTransfer(@StringRes failureMessage: Int, block: suspend () -> Unit) {
        val state = getOrNull<WorldBookListUiState.Normal>() ?: return
        if (state.loadState != WorldBookListLoadState.None || mTransferJob?.isActive == true) return
        val token = Any()
        mTransferToken = token
        mRefreshGeneration++
        state.copy(
            loadState = WorldBookListLoadState.Loading,
            dialogState = WorldBookListDialogState.None
        ).setup()
        mTransferJob = viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: LorebookArchiveLimitException) {
                AppViewEvent.PopupToastMessageByResId(R.string.world_book_archive_limit).tryEmit()
            } catch (_: Exception) {
                AppViewEvent.PopupToastMessageByResId(failureMessage).tryEmit()
            } finally {
                // 页面结束后不再写回普通状态，旧任务也不能解除后续任务的锁。
                if (mTransferToken === token) {
                    mTransferToken = null
                    mTransferJob = null
                    getOrNull<WorldBookListUiState.Normal>()?.copy(loadState = WorldBookListLoadState.None)
                        ?.setup()
                }
            }
        }
    }

    /** 读取列表及计数，过滤已被其他页面删除的选择。 */
    private suspend fun refreshLorebooks() {
        if (!isStateOf<WorldBookListUiState.Normal>()) return
        val generation = ++mRefreshGeneration
        val items = withContext(Dispatchers.IO) {
            val counts = mLorebookRepository.getLorebookEntryCounts()
            mLorebookRepository.getAllLorebooks().map { book ->
                WorldBookListItem.from(book, counts[book.id] ?: 0)
            }
        }
        // 仅接受最新刷新，任务中的刷新不提前解除加载状态。
        if (generation != mRefreshGeneration) return
        val state = getOrNull<WorldBookListUiState.Normal>() ?: return
        state.copy(
            loadState = if (mTransferJob?.isActive == true) state.loadState else WorldBookListLoadState.None,
            lorebooks = items,
            selectedLorebookIds = state.selectedLorebookIds.intersect(items.mapTo(mutableSetOf()) { it.id }),
        ).setup()
    }
}
