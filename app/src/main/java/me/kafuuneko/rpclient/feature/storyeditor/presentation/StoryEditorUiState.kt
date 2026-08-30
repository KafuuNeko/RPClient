package me.kafuuneko.rpclient.feature.storyeditor.presentation

import me.kafuuneko.rpclient.feature.storyeditor.model.StoryCharacterOptionItem
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryChapterDestination
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryChapterOutlineItem
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryLorebookGroupItem
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryImportPreview
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryStructureTitleTarget
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryVolumeOutlineItem
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspection

/** 章节编辑器页面状态树；当前章节正文由独立文档状态桥接，不复制到此处。 */
sealed class StoryEditorUiState {
    data object None : StoryEditorUiState()

    data class Normal(
        /** 当前操作关联的故事 ID。 */
        val storyId: Long,
        /** 当前页面顶部栏的可渲染状态。 */
        val topBarState: StoryEditorTopBarState,
        /** 当前页面正文区域的可渲染状态。 */
        val contentState: StoryEditorContentState,
        /** 故事卷与章节结构区域的可渲染状态。 */
        val structureState: StoryEditorStructureState,
        /** 角色与世界书引用区域的可渲染状态。 */
        val referenceState: StoryEditorReferenceState,
        /** 故事续写指导输入区域的状态。 */
        val continuationInputState: StoryContinuationInputState = StoryContinuationInputState(),
        /** 当前模型生成任务的生命周期状态。 */
        val generationState: StoryGenerationState = StoryGenerationState.Idle,
        /** 当前编辑历史是否允许撤销。 */
        val canUndoEdit: Boolean = false,
        /** 当前编辑历史是否允许重做。 */
        val canRedoEdit: Boolean = false,
        /** 当前会话是否存在可供查看的 Prompt 明细。 */
        val hasPromptInspection: Boolean = false,
        /** 当前是否存在可用于生成的模型配置。 */
        val hasAvailableProvider: Boolean = true,
        /** 当前页面主体内容的可渲染状态。 */
        val pageState: StoryEditorPageState = StoryEditorPageState.Editor,
        /** 当前页面互斥展示的对话框状态。 */
        val dialogState: StoryEditorDialogState = StoryEditorDialogState.None
    ) : StoryEditorUiState()

    data class Finished(val previous: StoryEditorUiState) : StoryEditorUiState()

    companion object {
        fun finished(previous: StoryEditorUiState): StoryEditorUiState {
            return previous as? Finished ?: Finished(previous)
        }
    }
}

/** 编辑器顶部栏标题与保存状态。 */
data class StoryEditorTopBarState(
    /** 供界面展示或持久化的标题。 */
    val title: String,
    /** 当前页面持久化操作的状态。 */
    val saveState: StorySaveState = StorySaveState.Saved
)

/** 正文区域的轻量渲染状态；正文内容不存入 UiState。 */
data class StoryEditorContentState(
    /** 当前分组或统计包含的角色数量。 */
    val characterCount: Int,
    /** 当前内容是否允许用户编辑。 */
    val editable: Boolean = true
)

/** 当前 Story 的轻量分卷/章节结构与编辑定位，不包含任何章节正文。 */
data class StoryEditorStructureState(
    /** 编辑器当前打开的章节 ID。 */
    val currentChapterId: Long,
    /** 编辑器当前打开章节的标题。 */
    val currentChapterTitle: String,
    /** 编辑器当前章节所属的卷 ID。 */
    val currentVolumeId: Long? = null,
    /** 编辑器当前章节所属卷的标题。 */
    val currentVolumeTitle: String? = null,
    /** 尚未归入任何卷的章节列表。 */
    val ungroupedChapters: List<StoryChapterOutlineItem> = emptyList(),
    /** 当前故事包含的卷结构列表。 */
    val volumes: List<StoryVolumeOutlineItem> = emptyList(),
    /** 当前页面是否正在刷新或提交更新。 */
    val isUpdating: Boolean = false
)

/** 当前故事已配置的上下文来源摘要。 */
data class StoryEditorReferenceState(
    /** 当前故事是否已配置长期记忆。 */
    val hasMemory: Boolean,
    /** 当前故事是否已配置作者注释。 */
    val hasAuthorNote: Boolean,
    /** 当前分组或统计包含的角色数量。 */
    val characterCount: Int,
    /** 当前故事已选择的世界书条目数量。 */
    val lorebookEntryCount: Int
)

/** 编辑器底栏中归属于当前章节并持续用于普通续写的引导草稿。 */
data class StoryContinuationInputState(
    /** 续写指导输入框中尚未提交的草稿。 */
    val guidanceDraft: String = ""
)

/** 正文自动保存状态。冲突草稿只保留在 ViewModel，不进入可重放 UiState。 */
sealed class StorySaveState {
    data object Saved : StorySaveState()
    data object Dirty : StorySaveState()
    data object Saving : StorySaveState()
    data object Failed : StorySaveState()
    data object Conflict : StorySaveState()
}

/** 一轮续写从准备、流式接收到应用结果的互斥状态。 */
sealed class StoryGenerationState {
    data object Idle : StoryGenerationState()
    data object Preparing : StoryGenerationState()
    data class Streaming(
        /** 生成中断时仍可恢复或保存的正文片段。 */
        val partialText: String,
        /** 当前生成任务所处的准备、请求或收尾阶段。 */
        val phase: StoryGenerationPhase,
        /** 生成开始时的单调时钟值，仅用于计算当前任务耗时。 */
        val startedAtElapsedRealtime: Long,
        /** 经过长度限制、供状态区域展示的推理摘要。 */
        val reasoningPreview: String = "",
        /** 当前流式生成累计得到的完整推理文本。 */
        val reasoningDetail: String = "",
        /** 当前生成状态中的推理详情是否展开。 */
        val isReasoningExpanded: Boolean = false
    ) : StoryGenerationState()
    data object Applying : StoryGenerationState()
    data class Failed(
        /** 当前状态或取舍产生的原因。 */
        val reason: StoryGenerationFailure,
        /** 生成失败后仍可由用户决定保存的部分正文。 */
        val recoverablePartial: String = "",
        /** 用于解释当前状态的详细信息。 */
        val detail: String = ""
    ) : StoryGenerationState()
}

/** 流式故事生成中由真实协议事件驱动的当前阶段。 */
enum class StoryGenerationPhase {
    AwaitingResponse,
    Connected,
    Reasoning,
    Writing
}

/** 用户可恢复或重试的续写失败类型。 */
enum class StoryGenerationFailure {
    Setup,
    Provider,
    ApplyResult,
    Conflict,
    EmptyResult,
    ContextBudget
}

/** 故事设置页的可选分区。 */
enum class StorySettingsSection {
    Context,
    Characters,
    Lorebook
}

/** 编辑器与全屏设置之间的页面状态。 */
sealed class StoryEditorPageState {
    data object Editor : StoryEditorPageState()
    data object Outline : StoryEditorPageState()
    data object LoadingSettings : StoryEditorPageState()

    data class Settings(
        /** 移动或编辑操作当前选中的结构区段。 */
        val selectedSection: StorySettingsSection = StorySettingsSection.Context,
        /** 长期参与故事 Prompt 构建的记忆内容。 */
        val memory: String,
        /** 当前会话或故事使用的摘要内容。 */
        val summary: String,
        /** 按指定位置注入故事 Prompt 的作者注释。 */
        val authorNote: String,
        /** 故事 Prompt 是否包含当前用户名称和用户设定。 */
        val includeUserPersona: Boolean,
        /** 当前页面或流程可使用的角色列表。 */
        val characters: List<StoryCharacterOptionItem>,
        /** 按世界书分组后的条目列表。 */
        val lorebookGroups: List<StoryLorebookGroupItem>,
        /** 当前页面是否正在执行保存操作。 */
        val isSaving: Boolean = false
    ) : StoryEditorPageState()
}

/** 编辑器业务对话框状态。 */
sealed class StoryEditorDialogState {
    data object None : StoryEditorDialogState()

    data class ModelSettingsGuide(
        val title: String,
        val message: String
    ) : StoryEditorDialogState()

    data class PromptInspector(val inspection: PromptInspection) : StoryEditorDialogState()

    data object FileActions : StoryEditorDialogState()

    data class ImportPreview(val preview: StoryImportPreview) : StoryEditorDialogState()

    data class StructureTitleEditor(
        val target: StoryStructureTitleTarget,
        val title: String,
        val isSaving: Boolean = false
    ) : StoryEditorDialogState()

    data class DeleteVolume(
        val volumeId: Long,
        val title: String,
        val isSaving: Boolean = false
    ) : StoryEditorDialogState()

    data class DeleteChapter(
        val chapterId: Long,
        val title: String,
        val isSaving: Boolean = false
    ) : StoryEditorDialogState()

    data class MoveChapter(
        val chapterId: Long,
        val title: String,
        val selectedDestination: StoryChapterDestination,
        val isSaving: Boolean = false
    ) : StoryEditorDialogState()

    data object SummarizingStory : StoryEditorDialogState()

    data class StorySummaryPreview(
        val content: String,
        val sourceStoryRevision: Long,
        val sourceChapterId: Long,
        val sourceChapterRevision: Long
    ) : StoryEditorDialogState()
}
