package me.kafuuneko.rpclient.feature.promptpreset

import android.content.Context
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.promptpreset.model.PromptPresetCategory
import me.kafuuneko.rpclient.feature.promptpreset.model.PromptPresetScope
import me.kafuuneko.rpclient.feature.promptpreset.model.PromptPresetSection
import me.kafuuneko.rpclient.feature.promptpreset.model.availableMacros
import me.kafuuneko.rpclient.feature.promptpreset.model.promptPresetFields
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetBrowserState
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetDialogState
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetFieldState
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetGroupState
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetLoadState
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetSectionState
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetUiIntent
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetUiState
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetViewEvent
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.prompt.preset.PromptTemplateSnapshot
import me.kafuuneko.rpclient.libs.prompt.preset.PromptType
import me.kafuuneko.rpclient.libs.prompt.summary.requiredRawSummaryMacro
import me.kafuuneko.rpclient.libs.prompt.summary.splitRawSummaryFormat
import me.kafuuneko.rpclient.libs.room.repository.PromptPresetConflictException
import me.kafuuneko.rpclient.libs.room.repository.PromptPresetRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val PREVIEW_MAX_LENGTH = 120
private val WHITESPACE_COLLAPSE_REGEX = Regex("\\s+")

/**
 * 单套提示词模板的详情与编辑状态持有者。
 * - 系统默认预设提供只读查看与单条原文复制，自定义预设逐项保存差异。
 * - revision 冲突和保存失败保留草稿，未保存退出需明确放弃。
 * - 按用途、适用场景与自定义状态组织展示，筛选不改变模板的生效范围。
 */
class PromptPresetViewModel :
    CoreViewModelWithEvent<PromptPresetUiIntent, PromptPresetUiState>(PromptPresetUiState.None),
    KoinComponent {
    private val mRepository by inject<PromptPresetRepository>()
    private val mContext by inject<Context>()
    private var mPresetId = PromptTemplateSnapshot.BUILTIN_ID

    /** 页面参数确定编辑目标，不把查看模板视为启用。 */
    @UiIntentObserver(PromptPresetUiIntent.Init::class)
    private fun onInit(intent: PromptPresetUiIntent.Init) {
        if (!isStateOf<PromptPresetUiState.None>()) return
        mPresetId = intent.presetId
        PromptPresetUiState.Normal(
            promptValues = emptyMap(),
            loadState = PromptPresetLoadState.Loading,
        ).setup()
        perform { refresh() }
    }

    /** 读取失败后重试同一模板，不自动退回另一套配置。 */
    @UiIntentObserver(PromptPresetUiIntent.Retry::class)
    private fun onRetry() = perform { refresh() }

    /** 切换显示场景，共用字段仍会出现在相关场景下。 */
    @UiIntentObserver(PromptPresetUiIntent.SelectScope::class)
    private fun onSelectScope(intent: PromptPresetUiIntent.SelectScope) = updateBrowser { browser ->
        browser.copy(scope = intent.scope)
    }

    /** 切换是否仅展示已自定义字段，开启时自动展开所有分类方便扫视。 */
    @UiIntentObserver(PromptPresetUiIntent.ToggleOnlyCustomized::class)
    private fun onToggleOnlyCustomized() = updateBrowser { browser ->
        val nextOnlyCustomized = !browser.onlyCustomized
        browser.copy(
            onlyCustomized = nextOnlyCustomized,
            expandedCategories = if (nextOnlyCustomized) {
                PromptPresetCategory.entries.toSet()
            } else {
                browser.expandedCategories
            },
        )
    }

    /** 清空自定义筛选条件，恢复展示当前场景下全部提示词。 */
    @UiIntentObserver(PromptPresetUiIntent.ClearFilters::class)
    private fun onClearFilters() = updateBrowser { browser ->
        browser.copy(onlyCustomized = false)
    }

    /** 一键展开或折叠当前全部可见分类。 */
    @UiIntentObserver(PromptPresetUiIntent.ToggleExpandAll::class)
    private fun onToggleExpandAll() = updateBrowser { browser ->
        val nextExpanded = if (browser.allExpanded) {
            emptySet()
        } else {
            browser.groups.map { group -> group.category }.toSet()
        }
        browser.copy(expandedCategories = nextExpanded)
    }

    /** 展开或折叠用途分类。 */
    @UiIntentObserver(PromptPresetUiIntent.ToggleCategory::class)
    private fun onToggleCategory(intent: PromptPresetUiIntent.ToggleCategory) =
        updateBrowser { browser ->
            val expanded = browser.expandedCategories
            browser.copy(
                expandedCategories = if (intent.category in expanded) {
                    expanded - intent.category
                } else {
                    expanded + intent.category
                },
            )
        }

    /** 未保存编辑先询问放弃，普通返回保留最后一帧。 */
    @UiIntentObserver(PromptPresetUiIntent.Back::class)
    private fun onBack() {
        val state = getOrNull<PromptPresetUiState.Normal>() ?: return
        if (state.busy) return
        if (state.dialogState != PromptPresetDialogState.None) {
            onDismissPromptDialog()
            return
        }
        PromptPresetUiState.finished(state).setup()
    }

    /** 打开编辑或只读查看弹窗；系统默认预设允许查看与复制原文，禁止修改保存。 */
    @UiIntentObserver(PromptPresetUiIntent.EditPromptClick::class)
    private fun onEditPromptClick(intent: PromptPresetUiIntent.EditPromptClick) {
        val state = getOrNull<PromptPresetUiState.Normal>() ?: return
        if (state.busy || state.loadState != PromptPresetLoadState.Ready) return
        state.copy(dialogState = editor(state = state, type = intent.type)).setup()
    }

    /** 复制系统模板或当前自定义模板，保存名称前不落库。 */
    @UiIntentObserver(PromptPresetUiIntent.CopyTemplate::class)
    private fun onCopyTemplate() = showCopyDialog()

    /** 更新副本名称草稿。 */
    @UiIntentObserver(PromptPresetUiIntent.ChangeCopyName::class)
    private fun onChangeCopyName(intent: PromptPresetUiIntent.ChangeCopyName) {
        val state = getOrNull<PromptPresetUiState.Normal>() ?: return
        val dialog = state.dialogState as? PromptPresetDialogState.CopyTemplate ?: return
        if (!state.busy) state.copy(dialogState = dialog.copy(name = intent.value)).setup()
    }

    /** 创建独立副本并在当前页面编辑，保持全局选择不变并提示用户。 */
    @UiIntentObserver(PromptPresetUiIntent.ConfirmCopy::class)
    private fun onConfirmCopy() {
        val state = getOrNull<PromptPresetUiState.Normal>() ?: return
        val dialog = state.dialogState as? PromptPresetDialogState.CopyTemplate ?: return
        if (dialog.name.isBlank()) return
        perform {
            mPresetId = mRepository.create(mPresetId, dialog.name, state.description)
            refresh()
            AppViewEvent.PopupToastMessageByResId(R.string.prompt_template_copied_not_active)
                .tryEmit()
        }
    }

    /** 剪贴板操作交给宿主，复制内容来自当前草稿或只读原文。 */
    @UiIntentObserver(PromptPresetUiIntent.CopyPromptDraft::class)
    private fun onCopyPromptDraft() {
        val state = getOrNull<PromptPresetUiState.Normal>() ?: return
        val dialog = state.dialogState as? PromptPresetDialogState.EditPrompt ?: return
        PromptPresetViewEvent.CopyText(dialog.draftText).tryEmit()
    }

    /** 更新草稿原文并实时计算必填宏校验状态，不裁剪空白或替换宏。 */
    @UiIntentObserver(PromptPresetUiIntent.ChangePromptDraft::class)
    private fun onChangePromptDraft(intent: PromptPresetUiIntent.ChangePromptDraft) {
        val state = getOrNull<PromptPresetUiState.Normal>() ?: return
        val dialog = state.dialogState as? PromptPresetDialogState.EditPrompt ?: return
        if (state.busy || dialog.readOnly) return
        val invalidRequiredMacro = isMissingRequiredMacro(dialog.type, intent.value)
        state.copy(
            dialogState = dialog.copy(
                draftText = intent.value,
                hasInvalidRequiredMacro = invalidRequiredMacro,
            ),
        ).setup()
    }

    /** 正文和版本在同一事务保存，无改动时跳过写入，成功后关闭编辑器并提示。 */
    @UiIntentObserver(PromptPresetUiIntent.SavePrompt::class)
    private fun onSavePrompt() {
        val state = getOrNull<PromptPresetUiState.Normal>() ?: return
        val dialog = state.dialogState as? PromptPresetDialogState.EditPrompt ?: return
        if (dialog.readOnly || !dialog.hasChanges) return
        // 提前提示无效的素材占位符，避免生成摘要时回退默认格式。
        val contentMacro = dialog.type.requiredRawSummaryMacro()
        if (contentMacro != null && dialog.type.splitRawSummaryFormat(dialog.draftText) == null) {
            PromptPresetViewEvent.ShowInvalidSummaryFormat("{{$contentMacro}}").tryEmit()
            return
        }
        perform {
            mRepository.savePrompt(mPresetId, state.revision, dialog.type, dialog.draftText)
            refresh()
            AppViewEvent.PopupToastMessageByResId(R.string.prompt_template_saved).tryEmit()
        }
    }

    /** 关闭前比较原文；无改动或取消副本弹窗时直接关闭，不再触发多余刷新。 */
    @UiIntentObserver(PromptPresetUiIntent.DismissPromptDialog::class)
    private fun onDismissPromptDialog() {
        val state = getOrNull<PromptPresetUiState.Normal>() ?: return
        if (state.busy) return
        val dialog = state.dialogState
        // 仅在草稿有实质改动时进入放弃确认，其余情况直接收起弹窗避免重复读库。
        when {
            dialog is PromptPresetDialogState.EditPrompt && dialog.hasChanges -> {
                state.copy(dialogState = PromptPresetDialogState.Discard(dialog)).setup()
            }

            dialog is PromptPresetDialogState.Discard -> {
                state.copy(dialogState = dialog.editor).setup()
            }

            else -> {
                state.copy(dialogState = PromptPresetDialogState.None).setup()
            }
        }
    }

    /** 用户明确放弃草稿后重新加载，解决其他页面编辑导致的版本冲突。 */
    @UiIntentObserver(PromptPresetUiIntent.ConfirmDiscard::class)
    private fun onConfirmDiscard() = perform { refresh() }

    /** 读取实体只用于转换，不让页面访问 DAO 或实体。 */
    private suspend fun refresh() {
        // 同时刷新正文与 revision，关闭冲突草稿后才能以新版本继续编辑。
        val detail = mRepository.getDetail(mPresetId)
        val state = getOrNull<PromptPresetUiState.Normal>() ?: return
        val builtin = detail.preset.id == PromptTemplateSnapshot.BUILTIN_ID
        val normalizedBrowser = if (builtin) {
            state.browserState.copy(onlyCustomized = false)
        } else {
            state.browserState
        }
        state.copy(
            promptValues = detail.snapshot.values,
            name = if (builtin) {
                mContext.getString(R.string.prompt_template_default)
            } else {
                detail.preset.name
            },
            description = detail.preset.description,
            revision = detail.preset.revision,
            isBuiltin = builtin,
            loadState = PromptPresetLoadState.Ready,
            browserState = buildBrowser(
                browser = normalizedBrowser,
                values = detail.snapshot.values,
                isBuiltin = builtin,
            ),
            dialogState = PromptPresetDialogState.None,
        ).setup()
    }

    /** 更新展示条件，不重新读取预设或干扰未保存草稿。 */
    private fun updateBrowser(update: (PromptPresetBrowserState) -> PromptPresetBrowserState) {
        val state = getOrNull<PromptPresetUiState.Normal>() ?: return
        if (state.busy || state.loadState != PromptPresetLoadState.Ready) return
        state.copy(
            browserState = buildBrowser(
                browser = update(state.browserState),
                values = state.promptValues,
                isBuiltin = state.isBuiltin,
            ),
        ).setup()
    }

    /** 先按实际作用范围筛选，再统计自定义项并组织用途分组。 */
    private fun buildBrowser(
        browser: PromptPresetBrowserState,
        values: Map<PromptType, String>,
        isBuiltin: Boolean,
    ): PromptPresetBrowserState {
        // 场景归属允许重叠；同一字段只有一个目录项，不创建额外覆盖值。
        val scopedFields = promptPresetFields.filter { field ->
            browser.scope == PromptPresetScope.All || browser.scope in field.scopes
        }
        val scopedStates = scopedFields.map { field ->
            val value = values[field.type] ?: field.type.defaultValue
            PromptPresetFieldState(
                field = field,
                isCustomized = !isBuiltin && value != field.type.defaultValue,
                isEmpty = value.isBlank(),
                previewText = formatPreview(value),
            )
        }
        val scopedCustomCount = scopedStates.count { fieldState -> fieldState.isCustomized }
        // 自定义过滤只影响当前列表可见项，保留同场景总计数以便展示徽标。
        val effectiveOnlyCustomized = !isBuiltin && browser.onlyCustomized
        val visibleBySection = scopedStates
            .filter { fieldState -> !effectiveOnlyCustomized || fieldState.isCustomized }
            .groupBy { fieldState -> fieldState.field.section }
        // 按固定用途顺序组织字段，筛选和保存后保留原有展开选择。
        val groups = PromptPresetCategory.entries.mapNotNull { category ->
            val sections = PromptPresetSection.entries
                .filter { section -> section.category == category }
                .mapNotNull sectionLoop@{ section ->
                    val items = visibleBySection[section] ?: return@sectionLoop null
                    PromptPresetSectionState(section = section, items = items)
                }
            if (sections.isEmpty()) return@mapNotNull null
            PromptPresetGroupState(
                category = category,
                expanded = category in browser.expandedCategories,
                sections = sections,
                totalCount = scopedStates.count { it.field.section.category == category },
            )
        }
        return browser.copy(
            onlyCustomized = effectiveOnlyCustomized,
            groups = groups,
            scopedTotalCount = scopedStates.size,
            scopedCustomCount = scopedCustomCount,
        )
    }

    /** 将多行提示词折叠为紧凑单行摘要，避免列表排版抖动。 */
    private fun formatPreview(content: String): String {
        val collapsed = content.trim().replace(WHITESPACE_COLLAPSE_REGEX, " ")
        if (collapsed.length <= PREVIEW_MAX_LENGTH) return collapsed
        return collapsed.take(PREVIEW_MAX_LENGTH)
    }

    /** 判断草稿是否缺少 Raw 摘要格式要求的唯一占位宏。 */
    private fun isMissingRequiredMacro(type: PromptType, text: String): Boolean {
        val required = type.requiredRawSummaryMacro() ?: return false
        return type.splitRawSummaryFormat(text) == null && required.isNotEmpty()
    }

    /** 用已保存原文建立草稿或只读视图，空串保持为空，默认文本只供显式重置。 */
    private fun editor(
        state: PromptPresetUiState.Normal,
        type: PromptType,
    ): PromptPresetDialogState.EditPrompt {
        // 打开瞬间即计算必填宏校验态，避免等到点击保存才发现格式缺宏。
        val currentText = state.promptValues[type].orEmpty()
        val requiredKey = type.requiredRawSummaryMacro()
        return PromptPresetDialogState.EditPrompt(
            type = type,
            draftText = currentText,
            defaultText = type.defaultValue,
            availableMacros = availableMacros(type),
            readOnly = state.isBuiltin,
            requiredMacro = requiredKey?.let { key -> "{{$key}}" },
            hasInvalidRequiredMacro = !state.isBuiltin && isMissingRequiredMacro(type, currentText),
            originalText = currentText,
        )
    }

    /** 工具栏复制时先确认副本名称。 */
    private fun showCopyDialog() {
        val state = getOrNull<PromptPresetUiState.Normal>() ?: return
        if (state.busy || state.loadState != PromptPresetLoadState.Ready) return
        state.copy(
            dialogState = PromptPresetDialogState.CopyTemplate(
                mContext.getString(R.string.prompt_template_copy_name, state.name),
            ),
        ).setup()
    }

    /** 异步操作统一处理取消和失败，错误不包含用户提示词。 */
    private fun perform(block: suspend () -> Unit) {
        val state = getOrNull<PromptPresetUiState.Normal>() ?: return
        if (state.busy) return
        state.copy(busy = true).setup()
        // 操作异常只转为本地化提示，原文草稿与选择留在当前状态。
        viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val current = getOrNull<PromptPresetUiState.Normal>() ?: return@launch
                if (current.loadState != PromptPresetLoadState.Ready) {
                    current.copy(loadState = PromptPresetLoadState.Failed).setup()
                }
                val messageRes = if (error is PromptPresetConflictException) {
                    R.string.prompt_template_conflict
                } else {
                    R.string.prompt_template_failed
                }
                AppViewEvent.PopupToastMessageByResId(messageRes).tryEmit()
            } finally {
                getOrNull<PromptPresetUiState.Normal>()?.copy(busy = false)?.setup()
            }
        }
    }
}
