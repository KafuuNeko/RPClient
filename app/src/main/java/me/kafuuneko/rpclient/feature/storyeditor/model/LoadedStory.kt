package me.kafuuneko.rpclient.feature.storyeditor.model

import me.kafuuneko.rpclient.libs.room.repository.StoryEditorData
import me.kafuuneko.rpclient.libs.room.repository.StoryLorebookRuntimeState

/** 编辑器初始化时一次性读取的 Story 与关联状态。 */
internal data class LoadedStory(
    val editorData: StoryEditorData,
    val characterCount: Int,
    val worldInfoStates: List<StoryLorebookRuntimeState>
)
