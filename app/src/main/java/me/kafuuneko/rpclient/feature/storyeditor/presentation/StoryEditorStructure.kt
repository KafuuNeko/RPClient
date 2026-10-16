package me.kafuuneko.rpclient.feature.storyeditor.presentation

import me.kafuuneko.rpclient.feature.storyeditor.model.StoryChapterDropPosition
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryChapterDropTarget
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryChapterOutlineItem
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryVolumeOutlineItem
import me.kafuuneko.rpclient.libs.room.repository.StoryEditorData

/** 按未分卷章节、分卷章节的现有顺序汇总大纲。 */
internal fun StoryEditorStructureState.allChapters(): List<StoryChapterOutlineItem> {
    return ungroupedChapters + volumes.flatMap { it.chapters }
}

/** 返回指定分卷中的章节；空分卷 ID 表示未分卷章节。 */
internal fun StoryEditorStructureState.chaptersInVolume(
    volumeId: Long?
): List<StoryChapterOutlineItem> {
    return if (volumeId == null) {
        ungroupedChapters
    } else {
        volumes.firstOrNull { it.id == volumeId }?.chapters.orEmpty()
    }
}

/** 替换指定分卷的章节列表，同时保留其余结构节点不变。 */
private fun StoryEditorStructureState.withChapters(
    volumeId: Long?,
    chapters: List<StoryChapterOutlineItem>
): StoryEditorStructureState {
    return if (volumeId == null) {
        copy(ungroupedChapters = chapters)
    } else {
        copy(
            volumes = volumes.map { volume ->
                if (volume.id == volumeId) volume.copy(chapters = chapters) else volume
            }
        )
    }
}

/**
 * 根据拖放目标生成新的章节结构，非法或无效目标返回空。
 *
 * - 同容器内依据源节点与锚点的相对方向插入，保持拖动经过相邻节点时可交换位置。
 * - 跨容器章节目标表示插入到该章节之前。
 * - 容器目标只表达首尾语义，最终下标由当前结构计算。
 * - 源容器与目标容器都会重新生成连续排序值。
 *
 * @param chapterId 被拖动的章节 ID
 * @param target 手势命中的类型化结构目标
 * @return 完成移动后的结构；章节、分卷或锚点无效时返回空
 */
internal fun StoryEditorStructureState.moveChapterForDrag(
    chapterId: Long,
    target: StoryChapterDropTarget
): StoryEditorStructureState? {
    val sourceChapter = allChapters().firstOrNull { it.id == chapterId } ?: return null
    val targetChapter = (target as? StoryChapterDropTarget.Chapter)?.let { chapterTarget ->
        allChapters().firstOrNull { it.id == chapterTarget.chapterId } ?: return null
    }
    if (targetChapter?.id == sourceChapter.id) return null

    // 先验证目标容器，再从参与排序的列表中排除源章节。
    val targetVolumeId = when (target) {
        is StoryChapterDropTarget.Chapter -> requireNotNull(targetChapter).volumeId
        is StoryChapterDropTarget.Container -> target.volumeId
    }
    if (targetVolumeId != null && volumes.none { it.id == targetVolumeId }) return null
    val originalSourceChapters = chaptersInVolume(sourceChapter.volumeId)
    val sourceChapters = originalSourceChapters
        .filterNot { it.id == sourceChapter.id }
        .normalizeChapterOrder()
    val targetChapters = if (sourceChapter.volumeId == targetVolumeId) {
        sourceChapters
    } else {
        chaptersInVolume(targetVolumeId).filterNot { it.id == sourceChapter.id }
    }

    // 章节锚点插在其前方，容器边界按当前成员数量解析为首尾下标。
    val targetIndex = when (target) {
        is StoryChapterDropTarget.Chapter -> {
            val anchorIndex = targetChapters.indexOfFirst { it.id == target.chapterId }
                .takeIf { it >= 0 }
                ?: return null
            val movesDownInSameContainer = sourceChapter.volumeId == targetVolumeId &&
                    originalSourceChapters.indexOfFirst { it.id == sourceChapter.id } <
                    originalSourceChapters.indexOfFirst { it.id == target.chapterId }
            if (movesDownInSameContainer) anchorIndex + 1 else anchorIndex
        }
        is StoryChapterDropTarget.Container -> when (target.position) {
            StoryChapterDropPosition.Start -> 0
            StoryChapterDropPosition.End -> targetChapters.size
        }
    }
    val reorderedTarget = targetChapters.toMutableList().apply {
        add(targetIndex, sourceChapter.copy(volumeId = targetVolumeId))
    }.normalizeChapterOrder()

    return if (sourceChapter.volumeId == targetVolumeId) {
        withChapters(targetVolumeId, reorderedTarget)
    } else {
        withChapters(sourceChapter.volumeId, sourceChapters)
            .withChapters(targetVolumeId, reorderedTarget)
    }
}

/** 生成容器内从零开始且连续的章节排序值。 */
private fun List<StoryChapterOutlineItem>.normalizeChapterOrder(): List<StoryChapterOutlineItem> {
    return mapIndexed { index, chapter -> chapter.copy(sortOrder = index) }
}

/** 将数据库加载的大纲聚合数据转换为 UI 结构状态。 */
internal fun buildStructureState(editorData: StoryEditorData): StoryEditorStructureState {
    // 将章节数据库实体映射为大纲项
    val chapters = editorData.chapters.map { overview ->
        StoryChapterOutlineItem(
            id = overview.id,
            title = overview.title,
            volumeId = overview.volumeId,
            characterCount = overview.contentCharacterCount,
            sortOrder = overview.sortOrder
        )
    }
    // 解析当前章节所属分卷
    val currentVolume = editorData.currentChapter.volumeId?.let { volumeId ->
        editorData.volumes.firstOrNull { it.id == volumeId }
    }
    // 组装大纲状态对象
    return StoryEditorStructureState(
        currentChapterId = editorData.currentChapter.id,
        currentChapterTitle = editorData.currentChapter.title,
        currentVolumeId = currentVolume?.id,
        currentVolumeTitle = currentVolume?.title,
        ungroupedChapters = chapters.filter { it.volumeId == null },
        volumes = editorData.volumes.map { volume ->
            StoryVolumeOutlineItem(
                id = volume.id,
                title = volume.title,
                sortOrder = volume.sortOrder,
                chapters = chapters.filter { it.volumeId == volume.id }
            )
        }
    )
}
