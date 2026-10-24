package me.kafuuneko.rpclient.feature.storyeditor.presentation

import me.kafuuneko.rpclient.feature.ModelSettingsGuideContent
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryEditorSnapshot
import me.kafuuneko.rpclient.libs.llm.GenerationFailure as LLMGenerationFailure
import me.kafuuneko.rpclient.libs.llm.classifyGenerationFailure

/** 仅在大纲空闲且无弹窗、无结构更新时允许结构编辑。 */
internal fun StoryEditorUiState.Normal.canEditStructure(): Boolean {
    return pageState == StoryEditorPageState.Outline &&
        dialogState == StoryEditorDialogState.None &&
        !structureState.isUpdating &&
        generationState is StoryGenerationState.Idle
}

/** 将公共模型配置引导转换为故事页面的对话框状态。 */
internal fun ModelSettingsGuideContent.toStoryDialogState(): StoryEditorDialogState.ModelSettingsGuide {
    return StoryEditorDialogState.ModelSettingsGuide(title = title, message = message)
}

/** 将 LLM 异常转换为故事生成专用的失败分类 [StoryGenerationFailure]。 */
internal fun Throwable.toStoryGenerationFailure(): StoryGenerationFailure {
    return when (classifyGenerationFailure(this)) {
        is LLMGenerationFailure.EmptyResponse -> StoryGenerationFailure.EmptyResult
        is LLMGenerationFailure.PromptBudget -> StoryGenerationFailure.ContextBudget
        else -> StoryGenerationFailure.Provider
    }
}

/** 判断输入法组合态结束时，是否需要补发一次被延后的自动保存。 */
internal fun shouldScheduleStoryAutoSaveAfterComposition(
    wasComposing: Boolean,
    snapshot: StoryEditorSnapshot,
    draftContent: String,
    persistedContent: String
): Boolean {
    return wasComposing &&
        !snapshot.isComposing &&
        snapshot.content == draftContent &&
        draftContent != persistedContent
}
