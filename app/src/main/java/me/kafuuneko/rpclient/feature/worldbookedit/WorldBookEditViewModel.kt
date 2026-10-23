package me.kafuuneko.rpclient.feature.worldbookedit

import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.worldbookedit.model.WorldBookEditForm
import me.kafuuneko.rpclient.feature.worldbookedit.model.hasUnsavedChangesFrom
import me.kafuuneko.rpclient.feature.worldbookedit.model.toComparableForm
import me.kafuuneko.rpclient.feature.worldbookedit.presentation.WorldBookEditDialogState
import me.kafuuneko.rpclient.feature.worldbookedit.presentation.WorldBookEditLoadState
import me.kafuuneko.rpclient.feature.worldbookedit.presentation.WorldBookEditMode
import me.kafuuneko.rpclient.feature.worldbookedit.presentation.WorldBookEditUiIntent
import me.kafuuneko.rpclient.feature.worldbookedit.presentation.WorldBookEditUiState
import me.kafuuneko.rpclient.feature.worldbookedit.presentation.withPersistedEntryDisabled
import me.kafuuneko.rpclient.feature.worldbookentryedit.WorldBookEntryEditActivity
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 世界书元数据编辑页状态持有者。
 *
 * 核心职责：
 * - 管理世界书名称、Token 预算模式（跟随全局 / 固定数值 / 无限制）及条目列表元数据；
 * - 协调条目搜索与状态过滤（全部 / 已启用 / 已禁用 / 常驻 / 递归等）；
 * - 驱动快速启用/禁用条目并在数据库中就地持久化；
 * - 跳转条目新建与编辑页面时自动预存世界书实体；
 * - 支持未保存修改防误退脏检查弹窗与世界书级联删除确认。
 */
class WorldBookEditViewModel : CoreViewModelWithEvent<WorldBookEditUiIntent, WorldBookEditUiState>(
    WorldBookEditUiState.None
), KoinComponent {
    private val mLorebookRepository by inject<LorebookRepository>()

    /** 初始化编辑页，依据是否传入世界书 ID 加载已有数据或开启新建表单。 */
    @UiIntentObserver(WorldBookEditUiIntent.Init::class)
    private suspend fun onInit(intent: WorldBookEditUiIntent.Init) {
        if (!isStateOf<WorldBookEditUiState.None>()) return
        // 初始进入加载中状态
        WorldBookEditUiState.Normal(
            mode = if (intent.lorebookId == null) WorldBookEditMode.Create else WorldBookEditMode.Edit,
            form = WorldBookEditForm(),
            loadState = WorldBookEditLoadState.Loading,
        ).setup()
        // 在 IO 线程拉取指定世界书及其所有条目
        val form = intent.lorebookId?.let { lorebookId ->
            withContext(Dispatchers.IO) {
                val lorebook =
                    mLorebookRepository.getLorebookById(lorebookId) ?: return@withContext null
                val entries = mLorebookRepository.getEntriesByLorebookId(lorebookId)
                WorldBookEditForm.from(lorebook, entries)
            }
        } ?: WorldBookEditForm()
        // 构建正常编辑态并记录初始对比基准
        WorldBookEditUiState.Normal(
            mode = if (form.isNew) WorldBookEditMode.Create else WorldBookEditMode.Edit,
            form = form,
        ).setup()
    }

    /** 页面从条目编辑返回时，若表单无未保存修改则重新拉取最新条目列表。 */
    @UiIntentObserver(WorldBookEditUiIntent.Resume::class)
    private suspend fun onResume() {
        val uiState = getOrNull<WorldBookEditUiState.Normal>() ?: return
        if (uiState.form.isNew) return
        if (uiState.form.hasUnsavedChangesFrom(uiState.initialForm)) return
        refreshEntries(uiState)
    }

    /** 处理返回操作，若有未保存修改则弹出二次确认弹窗。 */
    @UiIntentObserver(WorldBookEditUiIntent.Back::class)
    private fun onBack() {
        val uiState = getOrNull<WorldBookEditUiState.Normal>() ?: return
        if (uiState.loadState != WorldBookEditLoadState.None) return
        if (uiState.form.hasUnsavedChangesFrom(uiState.initialForm)) {
            uiState.copy(dialogState = WorldBookEditDialogState.UnsavedChangesConfirm).setup()
            return
        }
        WorldBookEditUiState.finished(uiStateFlow.value).setup()
    }

    /** 更新表单中的世界书名称。 */
    @UiIntentObserver(WorldBookEditUiIntent.ChangeName::class)
    private fun onChangeName(intent: WorldBookEditUiIntent.ChangeName) =
        updateForm { copy(name = intent.value) }

    /** 选择 Token 预算模式（全局/固定/无限制）。 */
    @UiIntentObserver(WorldBookEditUiIntent.SelectTokenBudgetMode::class)
    private fun onSelectTokenBudgetMode(intent: WorldBookEditUiIntent.SelectTokenBudgetMode) =
        updateForm {
            if (tokenBudgetMode == intent.mode) return@updateForm this
            copy(
                tokenBudgetMode = intent.mode,
                tokenBudgetInput = tokenBudgetInput.ifBlank {
                    DEFAULT_FIXED_TOKEN_BUDGET.toString()
                },
            )
        }

    /** 修改固定模式下的 Token 预算数值（过滤非数字字符）。 */
    @UiIntentObserver(WorldBookEditUiIntent.ChangeTokenBudgetTokens::class)
    private fun onChangeTokenBudgetTokens(intent: WorldBookEditUiIntent.ChangeTokenBudgetTokens) {
        updateForm { copy(tokenBudgetInput = intent.value.filter { char -> char in '0'..'9' }) }
    }

    /** 更改条目搜索关键词并触发条目列表就地重构。 */
    @UiIntentObserver(WorldBookEditUiIntent.ChangeEntrySearchQuery::class)
    private fun onChangeEntrySearchQuery(intent: WorldBookEditUiIntent.ChangeEntrySearchQuery) {
        val uiState = getOrNull<WorldBookEditUiState.Normal>() ?: return
        uiState.copy(
            entryListState = uiState.entryListState
                .copy(query = intent.value)
                .rebuild(uiState.form.entries),
        ).setup()
    }

    /** 更改条目分类过滤项并重新计算可见列表。 */
    @UiIntentObserver(WorldBookEditUiIntent.SelectEntryFilter::class)
    private fun onSelectEntryFilter(intent: WorldBookEditUiIntent.SelectEntryFilter) {
        val uiState = getOrNull<WorldBookEditUiState.Normal>() ?: return
        uiState.copy(
            entryListState = uiState.entryListState
                .copy(filter = intent.filter)
                .rebuild(uiState.form.entries),
        ).setup()
    }

    /** 预保存世界书并打开新建条目编辑页面。 */
    @UiIntentObserver(WorldBookEditUiIntent.AddEntry::class)
    private suspend fun onAddEntry() {
        val lorebookId = saveWorldBookForEntryNavigation() ?: return
        AppViewEvent.StartActivity(
            activity = WorldBookEntryEditActivity::class.java,
            extras = Bundle().apply {
                putLong(WorldBookEntryEditActivity.EXTRA_LOREBOOK_ID, lorebookId)
            },
        ).tryEmit()
    }

    /** 预保存世界书并打开指定条目的编辑页面。 */
    @UiIntentObserver(WorldBookEditUiIntent.EditEntry::class)
    private suspend fun onEditEntry(intent: WorldBookEditUiIntent.EditEntry) {
        val lorebookId = saveWorldBookForEntryNavigation() ?: return
        AppViewEvent.StartActivity(
            activity = WorldBookEntryEditActivity::class.java,
            extras = Bundle().apply {
                putLong(WorldBookEntryEditActivity.EXTRA_LOREBOOK_ID, lorebookId)
                putLong(WorldBookEntryEditActivity.EXTRA_ENTRY_ID, intent.entryId)
            },
        ).tryEmit()
    }

    /** 切换指定条目的禁用状态，并直接异步写入数据库。 */
    @UiIntentObserver(WorldBookEditUiIntent.ToggleEntryDisabled::class)
    private suspend fun onToggleEntryDisabled(intent: WorldBookEditUiIntent.ToggleEntryDisabled) {
        val uiState = getOrNull<WorldBookEditUiState.Normal>() ?: return
        val entry = uiState.form.entries.firstOrNull { it.id == intent.entryId } ?: return
        if (entry.disabled == intent.disabled) return

        // 异步更新数据库中条目的禁用状态
        val persisted = runCatching {
            withContext(Dispatchers.IO) {
                mLorebookRepository.updateEntryDisabled(intent.entryId, intent.disabled)
            }
        }.getOrDefault(defaultValue = false)
        if (!persisted) {
            AppViewEvent.PopupToastMessageByResId(R.string.world_book_entry_update_failed).tryEmit()
            return
        }

        // 同步更新内存状态并重构列表展示
        val latestState = getOrNull<WorldBookEditUiState.Normal>() ?: return
        latestState.withPersistedEntryDisabled(intent.entryId, intent.disabled).setup()
    }

    /** 校验并保存世界书元数据，成功后关闭页面。 */
    @UiIntentObserver(WorldBookEditUiIntent.SaveWorldBook::class)
    private suspend fun onSaveWorldBook() {
        val uiState = getOrNull<WorldBookEditUiState.Normal>() ?: return
        val form = uiState.form
        // 校验世界书名称必填
        if (form.name.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.world_book_name_empty).tryEmit()
            return
        }
        // 校验 Token 预算数值合法性
        if (form.resolvedTokenBudget == null) {
            AppViewEvent.PopupToastMessageByResId(R.string.world_book_budget_tokens_helper)
                .tryEmit()
            return
        }
        // 进入保存中状态并在 IO 线程执行持久化
        uiState.copy(loadState = WorldBookEditLoadState.Saving).setup()
        withContext(Dispatchers.IO) {
            mLorebookRepository.saveLorebook(form.toLorebook())
        }
        // 弹出成功提示并结束页面
        AppViewEvent.PopupToastMessageByResId(
            message = if (uiState.mode == WorldBookEditMode.Create) R.string.world_book_created else R.string.world_book_saved,
        ).tryEmit()
        WorldBookEditUiState.finished(uiStateFlow.value).setup()
    }

    /** 点击删除按钮，新建状态直接退出，已有世界书弹出确认弹窗。 */
    @UiIntentObserver(WorldBookEditUiIntent.DeleteWorldBookClick::class)
    private fun onDeleteWorldBookClick() {
        val uiState = getOrNull<WorldBookEditUiState.Normal>() ?: return
        if (uiState.form.isNew) {
            WorldBookEditUiState.finished(uiStateFlow.value).setup()
            return
        }
        uiState.copy(dialogState = WorldBookEditDialogState.DeleteConfirm(worldBookName = uiState.form.name))
            .setup()
    }

    /** 用户确认删除世界书，级联删除所有条目并结束页面。 */
    @UiIntentObserver(WorldBookEditUiIntent.ConfirmDeleteWorldBook::class)
    private suspend fun onConfirmDeleteWorldBook() {
        val uiState = getOrNull<WorldBookEditUiState.Normal>() ?: return
        if (uiState.form.isNew) return
        uiState.copy(
            loadState = WorldBookEditLoadState.Deleting,
            dialogState = WorldBookEditDialogState.None,
        ).setup()
        withContext(Dispatchers.IO) {
            mLorebookRepository.deleteLorebook(uiState.form.id)
        }
        AppViewEvent.PopupToastMessageByResId(R.string.world_book_deleted).tryEmit()
        WorldBookEditUiState.finished(uiStateFlow.value).setup()
    }

    /** 用户确认放弃未保存的修改，直接退出页面。 */
    @UiIntentObserver(WorldBookEditUiIntent.ConfirmDiscardChanges::class)
    private fun onConfirmDiscardChanges() {
        WorldBookEditUiState.finished(uiStateFlow.value).setup()
    }

    /** 关闭当前显示的任何弹窗。 */
    @UiIntentObserver(WorldBookEditUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<WorldBookEditUiState.Normal>() ?: return
        uiState.copy(dialogState = WorldBookEditDialogState.None).setup()
    }

    /** 辅助方法：以不可变方式更新当前表单数据。 */
    private fun updateForm(block: WorldBookEditForm.() -> WorldBookEditForm) {
        val uiState = getOrNull<WorldBookEditUiState.Normal>() ?: return
        uiState.copy(form = uiState.form.block()).setup()
    }

    /** 跳转条目页面前预存当前世界书，确保新建世界书具有数据库自增主键。 */
    private suspend fun saveWorldBookForEntryNavigation(): Long? {
        val uiState = getOrNull<WorldBookEditUiState.Normal>() ?: return null
        // 校验基本名称必填
        if (uiState.form.name.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.world_book_name_empty).tryEmit()
            return null
        }
        // 校验 Token 预算数值合法性
        if (uiState.form.resolvedTokenBudget == null) {
            AppViewEvent.PopupToastMessageByResId(R.string.world_book_budget_tokens_helper)
                .tryEmit()
            return null
        }
        // 持久化世界书实体并获取最新主键 ID
        val lorebookId = withContext(Dispatchers.IO) {
            mLorebookRepository.saveLorebook(uiState.form.toLorebook())
        }
        val latestForm = uiState.form.copy(id = lorebookId)
        // 切换为编辑模式并更新对比基准
        uiState.copy(
            mode = WorldBookEditMode.Edit,
            form = latestForm,
            initialForm = latestForm.toComparableForm(),
        ).setup()
        return lorebookId
    }

    /** 重新拉取世界书最新数据及其条目列表，并重构搜索过滤结果。 */
    private suspend fun refreshEntries(uiState: WorldBookEditUiState.Normal) {
        val form = withContext(Dispatchers.IO) {
            val lorebook =
                mLorebookRepository.getLorebookById(uiState.form.id) ?: return@withContext null
            val entries = mLorebookRepository.getEntriesByLorebookId(uiState.form.id)
            WorldBookEditForm.from(lorebook, entries)
        } ?: return
        getOrNull<WorldBookEditUiState.Normal>()?.copy(
            form = form,
            initialForm = form,
            entryListState = uiState.entryListState.rebuild(form.entries),
        )?.setup()
    }

}

/** 固定 Token 预算模式下的缺省值（1024 Tokens）。 */
private const val DEFAULT_FIXED_TOKEN_BUDGET = 1024

