package me.kafuuneko.rpclient.feature.storyeditor.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 编辑器需要 Activity 执行的一次性系统能力。 */
sealed class StoryEditorViewEvent : IViewEvent {
    data class CopyDraft(val content: String) : StoryEditorViewEvent()

    data class CopyGeneratedText(val content: String) : StoryEditorViewEvent()

    data class CopyPromptText(val content: String) : StoryEditorViewEvent()

    data object OpenTextImporter : StoryEditorViewEvent()

    data object OpenStoryImporter : StoryEditorViewEvent()

    data class OpenTextExporter(val fileName: String, val markdown: Boolean) : StoryEditorViewEvent()

    data class OpenStoryExporter(val fileName: String) : StoryEditorViewEvent()

    data class OpenStory(val storyId: Long) : StoryEditorViewEvent()
}
