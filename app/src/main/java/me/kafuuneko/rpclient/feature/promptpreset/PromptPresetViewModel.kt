package me.kafuuneko.rpclient.feature.promptpreset

import me.kafuuneko.rpclient.feature.promptpreset.model.PromptType
import me.kafuuneko.rpclient.feature.promptpreset.model.availableMacros
import me.kafuuneko.rpclient.feature.promptpreset.model.defaultPrompt
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetDialogState
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetUiIntent
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetUiState
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetViewEvent
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver

/**
 * Prompt 提示词预设页状态持有者。
 *
 * 核心职责：
 * - 集中读取 AppModel 中持久化的全量全局提示词模板（单聊、群聊、故事、总结、世界书格式化等）；
 * - 弹出提示词编辑弹窗，提供系统默认模板对比、可用宏标签（Macro Chips）提示与草稿复制；
 * - 将用户定制的模板覆盖写入 MMKV/SharedPreferences。
 */
class PromptPresetViewModel : CoreViewModelWithEvent<PromptPresetUiIntent, PromptPresetUiState>(
    PromptPresetUiState.None
) {

    /** 初始化页面，拉取当前所有提示词类型的生效文本。 */
    @UiIntentObserver(PromptPresetUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<PromptPresetUiState.None>()) return
        PromptPresetUiState.Normal(promptValues = readPromptValues()).setup()
    }

    /** 处理返回操作，迁移至 Finished 状态。 */
    @UiIntentObserver(PromptPresetUiIntent.Back::class)
    private fun onBack() {
        if (isStateOf<PromptPresetUiState.Finished>()) return
        PromptPresetUiState.finished(uiStateFlow.value).setup()
    }

    /** 点击编辑指定类型的 Prompt 模板，弹出编辑弹窗并装载当前草稿、默认值与可用宏列表。 */
    @UiIntentObserver(PromptPresetUiIntent.EditPromptClick::class)
    private fun onEditPromptClick(intent: PromptPresetUiIntent.EditPromptClick) {
        val uiState = getOrNull<PromptPresetUiState.Normal>() ?: return
        uiState.copy(
            dialogState = PromptPresetDialogState.EditPrompt(
                type = intent.type,
                draftText = uiState.promptValues[intent.type].orEmpty(),
                defaultText = defaultPrompt(intent.type),
                availableMacros = availableMacros(intent.type),
            ),
        ).setup()
    }

    /** 复制当前编辑弹窗中的草稿内容到系统剪贴板。 */
    @UiIntentObserver(PromptPresetUiIntent.CopyPromptDraft::class)
    private fun onCopyPromptDraft() {
        val uiState = getOrNull<PromptPresetUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? PromptPresetDialogState.EditPrompt ?: return
        PromptPresetViewEvent.CopyText(dialog.draftText).tryEmit()
    }

    /** 修改当前编辑弹窗中的草稿文本。 */
    @UiIntentObserver(PromptPresetUiIntent.ChangePromptDraft::class)
    private fun onChangePromptDraft(intent: PromptPresetUiIntent.ChangePromptDraft) {
        val uiState = getOrNull<PromptPresetUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? PromptPresetDialogState.EditPrompt ?: return
        uiState.copy(dialogState = dialog.copy(draftText = intent.value)).setup()
    }

    /** 保存编辑后的提示词模板覆盖值，并更新 UI 展示。 */
    @UiIntentObserver(PromptPresetUiIntent.SavePrompt::class)
    private fun onSavePrompt() {
        val uiState = getOrNull<PromptPresetUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? PromptPresetDialogState.EditPrompt ?: return
        writePrompt(dialog.type, dialog.draftText)
        uiState.copy(
            promptValues = readPromptValues(),
            dialogState = PromptPresetDialogState.None,
        ).setup()
    }

    /** 关闭当前显示的提示词编辑弹窗。 */
    @UiIntentObserver(PromptPresetUiIntent.DismissPromptDialog::class)
    private fun onDismissPromptDialog() {
        val uiState = getOrNull<PromptPresetUiState.Normal>() ?: return
        uiState.copy(dialogState = PromptPresetDialogState.None).setup()
    }

    /** 读取所有枚举类型当前生效的提示词文本映射表。 */
    private fun readPromptValues(): Map<PromptType, String> {
        return PromptType.entries.associateWith { readPrompt(it) }
    }

    /** 从 AppModel 读取当前生效的提示词文本。 */
    private fun readPrompt(type: PromptType): String {
        return when (type) {
            PromptType.Main -> AppModel.mainPrompt
            PromptType.Auxiliary -> AppModel.auxiliaryPrompt
            PromptType.PostHistory -> AppModel.postHistoryInstructions
            PromptType.Summarize -> AppModel.summarizePrompt
            PromptType.SummaryInjection -> AppModel.summaryInjectionTemplate
            PromptType.Impersonation -> AppModel.impersonationPrompt
            PromptType.NewChat -> AppModel.newChatPrompt
            PromptType.NewExampleChat -> AppModel.newExampleChatPrompt
            PromptType.ContinueNudge -> AppModel.continueNudgePrompt
            PromptType.ReplaceEmptyMessage -> AppModel.replaceEmptyMessagePrompt
            PromptType.WorldInfoFormat -> AppModel.worldInfoFormat
            PromptType.ScenarioFormat -> AppModel.scenarioFormat
            PromptType.PersonalityFormat -> AppModel.personalityFormat
            PromptType.UserPersonaFormat -> AppModel.userPersonaFormat
            PromptType.GroupNudge -> AppModel.groupNudgePrompt
            PromptType.NewGroupChat -> AppModel.newGroupChatPrompt
            PromptType.GroupSummarize -> AppModel.groupSummarizePrompt
            PromptType.StoryMain -> AppModel.storyMainPrompt
            PromptType.StoryMemory -> AppModel.storyMemoryTemplate
            PromptType.StorySummary -> AppModel.storySummaryTemplate
            PromptType.StorySummarize -> AppModel.storySummarizePrompt
            PromptType.StoryContinuationGuidance -> AppModel.storyContinuationGuidancePrompt
            PromptType.StoryContinue -> AppModel.storyContinuePrompt
        }
    }

    /** 将提示词修改内容回写至 AppModel 持久化存储。 */
    private fun writePrompt(type: PromptType, text: String) {
        when (type) {
            PromptType.Main -> AppModel.mainPrompt = text
            PromptType.Auxiliary -> AppModel.auxiliaryPrompt = text
            PromptType.PostHistory -> AppModel.postHistoryInstructions = text
            PromptType.Summarize -> AppModel.summarizePrompt = text
            PromptType.SummaryInjection -> AppModel.summaryInjectionTemplate = text
            PromptType.Impersonation -> AppModel.impersonationPrompt = text
            PromptType.NewChat -> AppModel.newChatPrompt = text
            PromptType.NewExampleChat -> AppModel.newExampleChatPrompt = text
            PromptType.ContinueNudge -> AppModel.continueNudgePrompt = text
            PromptType.ReplaceEmptyMessage -> AppModel.replaceEmptyMessagePrompt = text
            PromptType.WorldInfoFormat -> AppModel.worldInfoFormat = text
            PromptType.ScenarioFormat -> AppModel.scenarioFormat = text
            PromptType.PersonalityFormat -> AppModel.personalityFormat = text
            PromptType.UserPersonaFormat -> AppModel.userPersonaFormat = text
            PromptType.GroupNudge -> AppModel.groupNudgePrompt = text
            PromptType.NewGroupChat -> AppModel.newGroupChatPrompt = text
            PromptType.GroupSummarize -> AppModel.groupSummarizePrompt = text
            PromptType.StoryMain -> AppModel.storyMainPrompt = text
            PromptType.StoryMemory -> AppModel.storyMemoryTemplate = text
            PromptType.StorySummary -> AppModel.storySummaryTemplate = text
            PromptType.StorySummarize -> AppModel.storySummarizePrompt = text
            PromptType.StoryContinuationGuidance -> AppModel.storyContinuationGuidancePrompt = text
            PromptType.StoryContinue -> AppModel.storyContinuePrompt = text
        }
    }
}
