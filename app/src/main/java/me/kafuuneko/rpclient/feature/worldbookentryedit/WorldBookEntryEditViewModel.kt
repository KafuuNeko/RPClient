package me.kafuuneko.rpclient.feature.worldbookentryedit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.worldbookentryedit.model.WorldBookEntryEditForm
import me.kafuuneko.rpclient.feature.worldbookentryedit.model.hasUnsavedChangesFrom
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditDialogState
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditLoadState
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditMode
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditUiIntent
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditUiState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.utils.orSingleBlank
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 世界书条目编辑页状态持有者。
 *
 * 核心职责：
 * - 管理条目基础信息（名称、内容、主关键词、次关键词、分类标签、常驻/禁用状态）；
 * - 管理插入与模型角色参数（位置、顺序 Order、深度 Depth、角色 Role、触发概率 Probability、选择逻辑 SelectiveLogic、忽略预算 IgnoreBudget）；
 * - 管理扫描与递归规则（扫描深度、全词匹配、大小写敏感、防止递归、递归延迟、时序粘性 Sticky、冷却 Cooldown、延迟 Delay、自定义插槽 Outlet）；
 * - 管理扫描匹配目标（用户画像、角色描述/性格/深度提示、群聊场景）及扩展 JSON；
 * - 协调表单脏检查、输入参数合法性校验、条目保存与删除。
 */
class WorldBookEntryEditViewModel :
    CoreViewModelWithEvent<WorldBookEntryEditUiIntent, WorldBookEntryEditUiState>(
        WorldBookEntryEditUiState.None
    ), KoinComponent {
    private val mLorebookRepository by inject<LorebookRepository>()

    /** 初始化条目编辑页，依据是否传入条目 ID 决定创建新条目或加载已有条目。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.Init::class)
    private suspend fun onInit(intent: WorldBookEntryEditUiIntent.Init) {
        if (!isStateOf<WorldBookEntryEditUiState.None>()) return
        // 初始进入加载中状态
        WorldBookEntryEditUiState.Normal(
            mode = if (intent.entryId == null) WorldBookEntryEditMode.Create else WorldBookEntryEditMode.Edit,
            form = WorldBookEntryEditForm(lorebookId = intent.lorebookId),
            loadState = WorldBookEntryEditLoadState.Loading
        ).setup()
        // 在 IO 线程拉取指定条目详情
        val form = intent.entryId?.let { entryId ->
            withContext(Dispatchers.IO) {
                mLorebookRepository.getEntryById(entryId)?.let { WorldBookEntryEditForm.from(it) }
            }
        } ?: WorldBookEntryEditForm(lorebookId = intent.lorebookId)
        // 建立编辑态 UI 并记录初始对比基准
        WorldBookEntryEditUiState.Normal(
            mode = if (form.isNew) WorldBookEntryEditMode.Create else WorldBookEntryEditMode.Edit,
            form = form
        ).setup()
    }

    /** 处理返回操作，若有未保存修改则弹出二次确认弹窗。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.Back::class)
    private fun onBack() {
        val uiState = getOrNull<WorldBookEntryEditUiState.Normal>() ?: return
        if (uiState.loadState != WorldBookEntryEditLoadState.None) return
        if (uiState.form.hasUnsavedChangesFrom(uiState.initialForm)) {
            uiState.copy(dialogState = WorldBookEntryEditDialogState.UnsavedChangesConfirm).setup()
            return
        }
        WorldBookEntryEditUiState.finished(uiStateFlow.value).setup()
    }

    /** 修改条目名称。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeName::class)
    private fun onChangeName(intent: WorldBookEntryEditUiIntent.ChangeName) =
        updateForm { copy(name = intent.value) }

    /** 批量设置主关键词列表。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.SetKeywords::class)
    private fun onSetKeywords(intent: WorldBookEntryEditUiIntent.SetKeywords) =
        updateForm { copy(keywords = intent.keywords.orSingleBlank()) }

    /** 批量设置次关键词列表。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.SetSecondaryKeywords::class)
    private fun onSetSecondaryKeywords(intent: WorldBookEntryEditUiIntent.SetSecondaryKeywords) =
        updateForm { copy(secondaryKeywords = intent.secondaryKeywords.orSingleBlank()) }

    /** 修改是否常驻激活（Constant）。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeConstant::class)
    private fun onChangeConstant(intent: WorldBookEntryEditUiIntent.ChangeConstant) =
        updateForm { copy(constant = intent.value) }

    /** 修改是否禁用（Disabled）。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeDisabled::class)
    private fun onChangeDisabled(intent: WorldBookEntryEditUiIntent.ChangeDisabled) =
        updateForm { copy(disabled = intent.value) }

    /** 批量设置分类标签列表。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.SetCategories::class)
    private fun onSetCategories(intent: WorldBookEntryEditUiIntent.SetCategories) =
        updateForm { copy(category = intent.categories.orSingleBlank()) }

    /** 修改插入排序序号（Order）。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeOrder::class)
    private fun onChangeOrder(intent: WorldBookEntryEditUiIntent.ChangeOrder) =
        updateForm { copy(order = intent.value) }

    /** 修改 At Depth 深度值（Depth）。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeDepth::class)
    private fun onChangeDepth(intent: WorldBookEntryEditUiIntent.ChangeDepth) =
        updateForm { copy(depth = intent.value) }

    /** 修改条目在 Prompt 中的插入位置（Position）。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangePosition::class)
    private fun onChangePosition(intent: WorldBookEntryEditUiIntent.ChangePosition) =
        updateForm { copy(position = intent.value) }

    /** 修改条目使用的消息角色（Role）。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeRole::class)
    private fun onChangeRole(intent: WorldBookEntryEditUiIntent.ChangeRole) =
        updateForm { copy(role = intent.value) }

    /** 修改触发概率（Probability: 0-100）。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeProbability::class)
    private fun onChangeProbability(intent: WorldBookEntryEditUiIntent.ChangeProbability) =
        updateForm { copy(probability = intent.value) }

    /** 修改主次关键词的选择逻辑（Selective Logic: AND / NOT）。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeSelectiveLogic::class)
    private fun onChangeSelectiveLogic(intent: WorldBookEntryEditUiIntent.ChangeSelectiveLogic) =
        updateForm { copy(selectiveLogic = intent.value) }

    /** 修改是否忽略上下文预算限制（Ignore Budget）。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeIgnoreBudget::class)
    private fun onChangeIgnoreBudget(intent: WorldBookEntryEditUiIntent.ChangeIgnoreBudget) =
        updateForm { copy(ignoreBudget = intent.value) }

    /** 修改扫描历史消息的轮数深度（Scan Depth）。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeScanDepth::class)
    private fun onChangeScanDepth(intent: WorldBookEntryEditUiIntent.ChangeScanDepth) =
        updateForm { copy(scanDepth = intent.value) }

    /** 修改是否启用全词匹配（Match Whole Words）。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeMatchWholeWords::class)
    private fun onChangeMatchWholeWords(intent: WorldBookEntryEditUiIntent.ChangeMatchWholeWords) =
        updateForm { copy(matchWholeWords = intent.value) }

    /** 修改是否大小写敏感（Case Sensitive）。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeCaseSensitive::class)
    private fun onChangeCaseSensitive(intent: WorldBookEntryEditUiIntent.ChangeCaseSensitive) =
        updateForm { copy(caseSensitive = intent.value) }

    /** 修改条目所属的互斥包含组，多个组名以逗号分隔。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeGroup::class)
    private fun onChangeGroup(intent: WorldBookEntryEditUiIntent.ChangeGroup) =
        updateForm { copy(group = intent.value) }

    /** 修改条目在组内加权随机选择时使用的权重。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeGroupWeight::class)
    private fun onChangeGroupWeight(intent: WorldBookEntryEditUiIntent.ChangeGroupWeight) =
        updateForm { copy(groupWeight = intent.value) }

    /** 修改是否根据关键词命中数执行组内评分淘汰。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeUseGroupScoring::class)
    private fun onChangeUseGroupScoring(intent: WorldBookEntryEditUiIntent.ChangeUseGroupScoring) =
        updateForm { copy(useGroupScoring = intent.value) }

    /** 修改是否在同组候选中优先选择当前条目。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeGroupOverride::class)
    private fun onChangeGroupOverride(intent: WorldBookEntryEditUiIntent.ChangeGroupOverride) =
        updateForm { copy(groupOverride = intent.value) }

    /** 修改是否防止递归触发（Prevent Recursion）。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangePreventRecursion::class)
    private fun onChangePreventRecursion(intent: WorldBookEntryEditUiIntent.ChangePreventRecursion) =
        updateForm { copy(preventRecursion = intent.value) }

    /** 修改是否延迟至递归阶段扫描（Delay Until Recursion）。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeDelayUntilRecursion::class)
    private fun onChangeDelayUntilRecursion(intent: WorldBookEntryEditUiIntent.ChangeDelayUntilRecursion) =
        updateForm { copy(delayUntilRecursion = intent.value) }

    /** 修改时序粘性轮数（Sticky）。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeSticky::class)
    private fun onChangeSticky(intent: WorldBookEntryEditUiIntent.ChangeSticky) =
        updateForm { copy(sticky = intent.value) }

    /** 修改时序冷却轮数（Cooldown）。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeCooldown::class)
    private fun onChangeCooldown(intent: WorldBookEntryEditUiIntent.ChangeCooldown) =
        updateForm { copy(cooldown = intent.value) }

    /** 修改时序激活延迟轮数（Delay）。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeDelay::class)
    private fun onChangeDelay(intent: WorldBookEntryEditUiIntent.ChangeDelay) =
        updateForm { copy(delay = intent.value) }

    /** 修改自定义插槽名称（Outlet Name）。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeOutletName::class)
    private fun onChangeOutletName(intent: WorldBookEntryEditUiIntent.ChangeOutletName) =
        updateForm { copy(outletName = intent.value) }

    /** 修改是否扫描用户画像描述。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeMatchPersonaDescription::class)
    private fun onChangeMatchPersonaDescription(intent: WorldBookEntryEditUiIntent.ChangeMatchPersonaDescription) =
        updateForm { copy(matchPersonaDescription = intent.value) }

    /** 修改是否扫描角色描述。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeMatchCharacterDescription::class)
    private fun onChangeMatchCharacterDescription(intent: WorldBookEntryEditUiIntent.ChangeMatchCharacterDescription) =
        updateForm { copy(matchCharacterDescription = intent.value) }

    /** 修改是否扫描角色性格。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeMatchCharacterPersonality::class)
    private fun onChangeMatchCharacterPersonality(intent: WorldBookEntryEditUiIntent.ChangeMatchCharacterPersonality) =
        updateForm { copy(matchCharacterPersonality = intent.value) }

    /** 修改是否扫描角色深度提示。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeMatchCharacterDepthPrompt::class)
    private fun onChangeMatchCharacterDepthPrompt(intent: WorldBookEntryEditUiIntent.ChangeMatchCharacterDepthPrompt) =
        updateForm { copy(matchCharacterDepthPrompt = intent.value) }

    /** 修改是否扫描群聊/会话场景设定。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeMatchScenario::class)
    private fun onChangeMatchScenario(intent: WorldBookEntryEditUiIntent.ChangeMatchScenario) =
        updateForm { copy(matchScenario = intent.value) }

    /** 修改是否扫描角色卡创建者备注。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeMatchCreatorNotes::class)
    private fun onChangeMatchCreatorNotes(intent: WorldBookEntryEditUiIntent.ChangeMatchCreatorNotes) =
        updateForm { copy(matchCreatorNotes = intent.value) }

    /** 修改扩展 JSON 字符串。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeExtensionsJson::class)
    private fun onChangeExtensionsJson(intent: WorldBookEntryEditUiIntent.ChangeExtensionsJson) =
        updateForm { copy(extensionsJson = intent.value) }

    /** 修改条目主体内容。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeContent::class)
    private fun onChangeContent(intent: WorldBookEntryEditUiIntent.ChangeContent) =
        updateForm { copy(content = intent.value) }

    /** 校验条目所有数字参数与模型字段合法性，并保存至数据库。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.SaveEntry::class)
    private suspend fun onSaveEntry() {
        val uiState = getOrNull<WorldBookEntryEditUiState.Normal>() ?: return
        // 校验 Order 是否为合法整数
        if (uiState.form.order.trim().toIntOrNull() == null) {
            AppViewEvent.PopupToastMessageByResId(R.string.world_book_entry_order_invalid).tryEmit()
            return
        }
        // 校验 Depth 是否为合法整数
        if (uiState.form.depth.trim().toIntOrNull() == null) {
            AppViewEvent.PopupToastMessageByResId(R.string.world_book_entry_depth_invalid).tryEmit()
            return
        }
        // 校验位置、角色、概率与选择逻辑整数合法性
        if (
            uiState.form.position.trim().toIntOrNull() == null ||
            uiState.form.role.trim().toIntOrNull() == null ||
            uiState.form.probability.trim().toIntOrNull() == null ||
            uiState.form.selectiveLogic.trim().toIntOrNull() == null
        ) {
            AppViewEvent.PopupToastMessageByResId(R.string.generation_params_invalid).tryEmit()
            return
        }
        // 转换表单为数据库实体
        val entry = uiState.form.toLorebookEntryOrNull() ?: return
        uiState.copy(loadState = WorldBookEntryEditLoadState.Saving).setup()
        // 在 IO 线程保存条目
        withContext(Dispatchers.IO) {
            mLorebookRepository.saveEntry(entry)
        }
        // 弹出成功提示并结束页面
        AppViewEvent.PopupToastMessageByResId(
            if (uiState.mode == WorldBookEntryEditMode.Create) R.string.world_book_entry_created else R.string.world_book_entry_saved
        ).tryEmit()
        WorldBookEntryEditUiState.finished(uiStateFlow.value).setup()
    }

    /** 点击删除条目，新建状态直接退出，已有条目弹出确认弹窗。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.DeleteEntryClick::class)
    private fun onDeleteEntryClick() {
        val uiState = getOrNull<WorldBookEntryEditUiState.Normal>() ?: return
        if (uiState.form.isNew) {
            WorldBookEntryEditUiState.finished(uiStateFlow.value).setup()
            return
        }
        uiState.copy(
            dialogState = WorldBookEntryEditDialogState.DeleteConfirm(
                entryName = uiState.form.name
            )
        ).setup()
    }

    /** 用户确认删除条目，从数据库中移除并结束页面。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ConfirmDeleteEntry::class)
    private suspend fun onConfirmDeleteEntry() {
        val uiState = getOrNull<WorldBookEntryEditUiState.Normal>() ?: return
        if (uiState.form.isNew) return
        uiState.copy(
            loadState = WorldBookEntryEditLoadState.Deleting,
            dialogState = WorldBookEntryEditDialogState.None
        ).setup()
        withContext(Dispatchers.IO) {
            mLorebookRepository.deleteEntry(uiState.form.id)
        }
        AppViewEvent.PopupToastMessageByResId(R.string.world_book_entry_deleted).tryEmit()
        WorldBookEntryEditUiState.finished(uiStateFlow.value).setup()
    }

    /** 用户确认放弃未保存修改，直接退出页面。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ConfirmDiscardChanges::class)
    private fun onConfirmDiscardChanges() {
        WorldBookEntryEditUiState.finished(uiStateFlow.value).setup()
    }

    /** 关闭当前显示的任何弹窗。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<WorldBookEntryEditUiState.Normal>() ?: return
        uiState.copy(dialogState = WorldBookEntryEditDialogState.None).setup()
    }

    /** 开启设定正文全屏专注编辑器。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.OpenPromptEditor::class)
    private fun onOpenPromptEditor() {
        val uiState = getOrNull<WorldBookEntryEditUiState.Normal>() ?: return
        uiState.copy(dialogState = WorldBookEntryEditDialogState.PromptEditor(uiState.form.content)).setup()
    }

    /** 更新全屏编辑器内部草稿内容。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangePromptEditorDraft::class)
    private fun onChangePromptEditorDraft(intent: WorldBookEntryEditUiIntent.ChangePromptEditorDraft) {
        val uiState = getOrNull<WorldBookEntryEditUiState.Normal>() ?: return
        if (uiState.dialogState !is WorldBookEntryEditDialogState.PromptEditor) return
        uiState.copy(dialogState = WorldBookEntryEditDialogState.PromptEditor(intent.text)).setup()
    }

    /** 确认全屏编辑器修改并同步回正文表单。 */
    @UiIntentObserver(WorldBookEntryEditUiIntent.ConfirmPromptEditor::class)
    private fun onConfirmPromptEditor() {
        val uiState = getOrNull<WorldBookEntryEditUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? WorldBookEntryEditDialogState.PromptEditor ?: return
        uiState.copy(
            form = uiState.form.copy(content = dialog.draftText),
            dialogState = WorldBookEntryEditDialogState.None
        ).setup()
    }

    /** 辅助方法：以不可变方式更新当前条目表单数据。 */
    private fun updateForm(block: WorldBookEntryEditForm.() -> WorldBookEntryEditForm) {
        val uiState = getOrNull<WorldBookEntryEditUiState.Normal>() ?: return
        uiState.copy(form = uiState.form.block()).setup()
    }

}
