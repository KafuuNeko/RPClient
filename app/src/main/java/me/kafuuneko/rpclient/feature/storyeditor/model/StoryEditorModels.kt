package me.kafuuneko.rpclient.feature.storyeditor.model

import me.kafuuneko.rpclient.libs.story.StoryImportDraft
import me.kafuuneko.rpclient.libs.room.repository.StoryLorebookRuntimeState

/** Compose 文本编辑状态与 ViewModel 草稿之间的轻量同步快照。 */
data class StoryEditorDocument(
    /** 当前操作关联的故事 ID。 */
    val storyId: Long,
    /** 当前操作关联的章节 ID。 */
    val chapterId: Long,
    /** 当前对象承载的正文内容。 */
    val content: String,
    /** 用于隔离过期异步结果的同步版本号。 */
    val syncVersion: Long,
    /** 当前编辑器最近一次修改的文本区间。 */
    val latestEditedRange: StoryEditedTextRange? = null
)

/** 当前编辑会话中最近一次正文修改所插入或替换内容的半开字符区间。 */
data class StoryEditedTextRange(
    /** 当前区间的起始位置，包含该位置。 */
    val start: Int,
    /** 当前区间的结束位置，不包含该位置。 */
    val end: Int
) {
    init {
        require(start >= 0) { "Edited text range start cannot be negative" }
        require(end > start) { "Edited text range must not be empty or reversed" }
    }
}

/** 当前正文的文本和 IME composition 快照。 */
data class StoryEditorSnapshot(
    /** 当前操作关联的章节 ID。 */
    val chapterId: Long,
    /** 当前对象承载的正文内容。 */
    val content: String,
    /** 输入法是否仍在组合当前编辑文本。 */
    val isComposing: Boolean
)

/** 章节结构页中的轻量章节项；正文只通过 [StoryEditorDocument] 交给编辑器。 */
data class StoryChapterOutlineItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 供界面展示或持久化的标题。 */
    val title: String,
    /** 当前操作关联的故事卷 ID。 */
    val volumeId: Long?,
    /** 当前分组或统计包含的角色数量。 */
    val characterCount: Int,
    /** 当前对象用于稳定排序的顺序值。 */
    val sortOrder: Int
)

/** 章节结构页中的分卷及其有序章节。 */
data class StoryVolumeOutlineItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 供界面展示或持久化的标题。 */
    val title: String,
    /** 当前对象用于稳定排序的顺序值。 */
    val sortOrder: Int,
    /** 当前故事或卷包含的章节列表。 */
    val chapters: List<StoryChapterOutlineItem>
)

/** 移动章节时可选择的目标分组。 */
sealed class StoryChapterDestination {
    data object Ungrouped : StoryChapterDestination()
    data class Volume(val volumeId: Long) : StoryChapterDestination()
}

/** 章节拖放手势命中的结构目标，不携带最终排序下标。 */
sealed class StoryChapterDropTarget {
    /** 拖到另一章节时，由状态持有者计算目标章节前的插入位置。 */
    data class Chapter(val chapterId: Long) : StoryChapterDropTarget()

    /** 拖到分组边界时，由状态持有者根据 [position] 计算首尾位置。 */
    data class Container(
        /** 当前操作关联的故事卷 ID。 */
        val volumeId: Long?,
        /** 当前对象在所属结构中的位置。 */
        val position: StoryChapterDropPosition
    ) : StoryChapterDropTarget()
}

/** 章节拖到分组边界时表达的相对位置。 */
enum class StoryChapterDropPosition {
    Start,
    End
}

/** 创建或重命名结构节点时由对话框携带的目标。 */
sealed class StoryStructureTitleTarget {
    data object NewVolume : StoryStructureTitleTarget()
    data class NewChapter(val volumeId: Long?) : StoryStructureTitleTarget()
    data class Volume(val volumeId: Long) : StoryStructureTitleTarget()
    data class Chapter(val chapterId: Long) : StoryStructureTitleTarget()
}

/** Story 设置页中的角色卡候选项。 */
data class StoryCharacterOptionItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 供界面展示和业务识别的名称。 */
    val name: String,
    /** 用于说明当前对象的描述文本。 */
    val description: String,
    /** 当前列表项是否已被选中。 */
    val selected: Boolean,
    /** 当前角色或条目的激活模式。 */
    val activationMode: StoryCharacterActivationMode = StoryCharacterActivationMode.Auto,
    /** 当前对象用于稳定排序的顺序值。 */
    val sortOrder: Int = Int.MAX_VALUE,
    /** 通过角色卡或业务关系绑定的世界书 ID。 */
    val linkedLorebookId: Long? = null,
    /** 已绑定世界书的显示名称。 */
    val linkedLorebookName: String? = null
)

/** Story 设置页可选择的角色激活方式，不暴露 Room 的持久化取值。 */
enum class StoryCharacterActivationMode {
    /** 主角：常驻置顶注入，单篇故事仅允许一个主角。 */
    Primary,
    /** 常驻配角：常驻注入。 */
    Always,
    /** 自动匹配：根据正文提及关键词动态激活。 */
    Auto
}

/** Story 设置页中的世界书条目。 */
data class StoryLorebookEntryItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 供界面展示和业务识别的名称。 */
    val name: String,
    /** 经过长度限制、供界面展示的正文预览。 */
    val contentPreview: String,
    /** 用于触发世界书条目的主关键词列表。 */
    val keywords: List<String>,
    /** 是否忽略关键词并始终激活当前世界书条目。 */
    val constant: Boolean,
    /** 当前列表项是否已被选中。 */
    val selected: Boolean
)

/** Story 设置页中的世界书分组。 */
data class StoryLorebookGroupItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 供界面展示和业务识别的名称。 */
    val name: String,
    /** 当前分组、请求或结果包含的条目列表。 */
    val entries: List<StoryLorebookEntryItem>
) {
    val selectedCount: Int
        get() = entries.count { it.selected }

    val isAllSelected: Boolean
        get() = entries.isNotEmpty() && selectedCount == entries.size
}

/** 启用指定世界书的全部条目，用于角色关联世界书的自动联动。 */
fun List<StoryLorebookGroupItem>.enableLorebook(
    lorebookId: Long
): List<StoryLorebookGroupItem> {
    return map { group ->
        if (group.id == lorebookId) {
            group.copy(
                entries = group.entries.map { entry ->
                    if (entry.selected) entry else entry.copy(selected = true)
                }
            )
        } else {
            group
        }
    }
}

/**
 * 按 Story 已持久化的条目 ID 重建世界书选择状态。
 *
 * 角色关联只负责在用户选择角色时提供默认勾选，不能在重新打开设置时覆盖用户显式关闭的结果。
 */
fun List<StoryLorebookGroupItem>.restoreLorebookSelection(
    selectedEntryIds: Set<Long>
): List<StoryLorebookGroupItem> {
    return map { group ->
        group.copy(
            entries = group.entries.map { entry ->
                entry.copy(selected = entry.id in selectedEntryIds)
            }
        )
    }
}

/** 按整本世界书切换条目；部分启用时会补全，全部启用时会关闭。 */
fun List<StoryLorebookGroupItem>.toggleLorebook(
    lorebookId: Long
): List<StoryLorebookGroupItem> {
    val target = firstOrNull { it.id == lorebookId } ?: return this
    if (target.entries.isEmpty()) return this
    val selectAll = !target.isAllSelected
    return map { group ->
        if (group.id == lorebookId) {
            group.copy(
                entries = group.entries.map {
                    it.copy(selected = selectAll)
                }
            )
        } else {
            group
        }
    }
}

/** 用户选择的故事纯文本导出格式。 */
enum class StoryTextExportFormat {
    Text,
    Markdown
}

/** 一次可撤销的正文替换及其前后世界书时序状态。 */
data class StoryUndoEntry(
    /** 当前区间的起始位置，包含该位置。 */
    val start: Int,
    /** 本次编辑操作新插入的文本。 */
    val insertedText: String,
    /** 本次编辑操作替换掉的原文本。 */
    val replacedText: String,
    /** 本轮扫描前的世界书时序状态映射。 */
    val previousWorldInfoStates: List<StoryLorebookRuntimeState>,
    /** 本轮生成开始前的世界书时序轮次。 */
    val previousWorldInfoGenerationStep: Int,
    /** 本轮扫描后需要持久化的世界书时序状态映射。 */
    val nextWorldInfoStates: List<StoryLorebookRuntimeState>,
    /** 本轮生成完成后应持久化的世界书时序轮次。 */
    val nextWorldInfoGenerationStep: Int = previousWorldInfoGenerationStep + 1,
    /** 产生当前数据的来源。 */
    val source: StoryEditSource = StoryEditSource.Ai
)

/** 正文修改的来源，用于区分手工合并和 AI 原子操作。 */
enum class StoryEditSource {
    Ai,
    User
}

/** 导入确认对话框所需的解析结果和标题草稿。 */
data class StoryImportPreview(
    /** 当前页面中尚未持久化的编辑草稿。 */
    val draft: StoryImportDraft,
    /** 供界面展示或持久化的标题。 */
    val title: String,
    /** 当前页面是否正在执行保存操作。 */
    val isSaving: Boolean = false
)
