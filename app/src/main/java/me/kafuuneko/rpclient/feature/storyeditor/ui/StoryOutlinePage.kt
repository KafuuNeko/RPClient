package me.kafuuneko.rpclient.feature.storyeditor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryChapterDropPosition
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryChapterDropTarget
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryChapterOutlineItem
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryVolumeOutlineItem
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorStructureState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorUiIntent
import me.kafuuneko.rpclient.ui.widgets.AppCard
import me.kafuuneko.rpclient.ui.widgets.AppPageScaffold
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.DraggableItem
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.dragContainer
import me.kafuuneko.rpclient.ui.widgets.rememberLazyListDragDropState

/**
 * 渲染故事章节大纲，保留分卷、章节拖放与菜单的原有行为。
 *
 * @param storyTitle 当前故事标题
 * @param structureState 当前分卷和章节结构
 * @param emit 原有故事编辑意图发送入口
 */
@Composable
internal fun StoryOutlinePage(
    storyTitle: String,
    structureState: StoryEditorStructureState,
    emit: StoryEditorUiIntent.() -> Unit,
) {
    var collapsedVolumeIds by remember { mutableStateOf(emptySet<Long>()) }
    val controlsEnabled = !structureState.isUpdating
    val totalChapterCount = structureState.ungroupedChapters.size +
            structureState.volumes.sumOf { it.chapters.size }
    val totalCharacterCount = remember(structureState) {
        structureState.ungroupedChapters.sumOf { storyChapterOutlineItem -> storyChapterOutlineItem.characterCount } +
                structureState.volumes.sumOf { volume -> volume.chapters.sumOf { storyChapterOutlineItem -> storyChapterOutlineItem.characterCount } }
    }

    // 通过查找表把可保存的稳定 key 转换为类型化手势目标，避免解析字符串约定。
    val listState = rememberLazyListState()
    val dragNodesByKey = remember(structureState) {
        storyOutlineDragNodes(structureState)
    }
    val dragDropState = rememberLazyListDragDropState(
        lazyListState = listState,
        isItemDraggable = { key -> controlsEnabled && dragNodesByKey[key]?.chapterId != null },
        isItemDropTarget = { key ->
            controlsEnabled && dragNodesByKey[key]?.dropTarget != null
        },
        onMove = { fromKey, toKey ->
            val chapterId = dragNodesByKey[fromKey]?.chapterId
                ?: return@rememberLazyListDragDropState
            val target = dragNodesByKey[toKey]?.dropTarget
                ?: return@rememberLazyListDragDropState
            StoryEditorUiIntent.DragStoryChapter(chapterId, target).emit()
        },
        onDragEnd = { StoryEditorUiIntent.CommitStoryChapterOrder.emit() },
    )

    AppPageScaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.story_outline),
                onBack = { StoryEditorUiIntent.CloseStoryOutline.emit() },
                actions = {
                    IconButton(
                        onClick = { StoryEditorUiIntent.ShowCreateVolumeDialog.emit() },
                        enabled = controlsEnabled,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Book,
                            contentDescription = stringResource(R.string.story_add_volume),
                        )
                    }
                    IconButton(
                        onClick = { StoryEditorUiIntent.ShowCreateChapterDialog(volumeId = null).emit() },
                        enabled = controlsEnabled,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.story_add_chapter),
                        )
                    }
                },
            )
        },
        content = { padding ->
            RpLazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .dragContainer(dragDropState),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 全书体量与结构概览卡片
                item(key = OUTLINE_SUMMARY_KEY) {
                    OutlineSummary(
                        storyTitle = storyTitle,
                        volumeCount = structureState.volumes.size,
                        chapterCount = totalChapterCount,
                        totalCharacterCount = totalCharacterCount,
                        updating = structureState.isUpdating,
                    )
                }

                // 未分卷章节分组
                val ungroupedTotalWords = structureState.ungroupedChapters.sumOf { storyChapterOutlineItem -> storyChapterOutlineItem.characterCount }
                item(key = UNGROUPED_HEADER_KEY) {
                    OutlineSectionHeader(
                        title = stringResource(R.string.story_ungrouped_chapters),
                        itemCount = structureState.ungroupedChapters.size,
                        totalCharacterCount = ungroupedTotalWords,
                        enabled = controlsEnabled,
                        onAddChapter = {
                            StoryEditorUiIntent.ShowCreateChapterDialog(volumeId = null).emit()
                        },
                    )
                }
                if (structureState.ungroupedChapters.isEmpty()) {
                    item(key = UNGROUPED_EMPTY_KEY) {
                        OutlineEmptyMessage(
                            text = stringResource(R.string.story_no_ungrouped_chapters),
                            onAction = { StoryEditorUiIntent.ShowCreateChapterDialog(volumeId = null).emit() },
                            actionTextRes = R.string.story_add_ungrouped_chapter_button,
                            enabled = controlsEnabled,
                        )
                    }
                } else {
                    items(
                        items = structureState.ungroupedChapters,
                        key = { storyChapterOutlineItem -> chapterOutlineKey(storyChapterOutlineItem.id) },
                    ) { chapter ->
                        val index =
                            structureState.ungroupedChapters.indexOfFirst { storyChapterOutlineItem -> storyChapterOutlineItem.id == chapter.id }
                        DraggableItem(
                            dragDropState = dragDropState,
                            key = chapterOutlineKey(chapter.id),
                        ) { isDragging ->
                            StoryChapterOutlineRow(
                                index = index,
                                chapter = chapter,
                                selected = chapter.id == structureState.currentChapterId,
                                isDragging = isDragging,
                                enabled = controlsEnabled,
                                canMoveUp = index > 0,
                                canMoveDown = index in 0 until structureState.ungroupedChapters.lastIndex,
                                canDelete = totalChapterCount > 1,
                                emit = emit,
                            )
                        }
                    }
                    item(key = UNGROUPED_ADD_KEY) {
                        OutlineAddChapterButton(
                            textRes = R.string.story_add_ungrouped_chapter_button,
                            enabled = controlsEnabled,
                            onClick = { StoryEditorUiIntent.ShowCreateChapterDialog(volumeId = null).emit() },
                        )
                    }
                }

                // 各分卷及其包含的章节列表
                structureState.volumes.forEachIndexed { volumeIndex, volume ->
                    item(key = volumeHeaderKey(volume.id)) {
                        StoryVolumeOutlineHeader(
                            volume = volume,
                            collapsed = volume.id in collapsedVolumeIds,
                            enabled = controlsEnabled,
                            canMoveUp = volumeIndex > 0,
                            canMoveDown = volumeIndex < structureState.volumes.lastIndex,
                            onToggleCollapsed = {
                                collapsedVolumeIds = if (volume.id in collapsedVolumeIds) {
                                    collapsedVolumeIds - volume.id
                                } else {
                                    collapsedVolumeIds + volume.id
                                }
                            },
                            onAddChapter = {
                                StoryEditorUiIntent.ShowCreateChapterDialog(volume.id).emit()
                            },
                            emit = emit,
                        )
                    }
                    if (volume.id !in collapsedVolumeIds) {
                        if (volume.chapters.isEmpty()) {
                            item(key = volumeEmptyKey(volume.id)) {
                                OutlineEmptyMessage(
                                    text = stringResource(R.string.story_empty_volume_guide),
                                    onAction = { StoryEditorUiIntent.ShowCreateChapterDialog(volume.id).emit() },
                                    actionTextRes = R.string.story_add_chapter_to_volume,
                                    enabled = controlsEnabled,
                                )
                            }
                        } else {
                            items(
                                items = volume.chapters,
                                key = { storyChapterOutlineItem -> chapterOutlineKey(storyChapterOutlineItem.id) },
                            ) { chapter ->
                                val index = volume.chapters.indexOfFirst { storyChapterOutlineItem -> storyChapterOutlineItem.id == chapter.id }
                                DraggableItem(
                                    dragDropState = dragDropState,
                                    key = chapterOutlineKey(chapter.id),
                                ) { isDragging ->
                                    StoryChapterOutlineRow(
                                        index = index,
                                        chapter = chapter,
                                        selected = chapter.id == structureState.currentChapterId,
                                        isDragging = isDragging,
                                        enabled = controlsEnabled,
                                        canMoveUp = index > 0,
                                        canMoveDown = index in 0 until volume.chapters.lastIndex,
                                        canDelete = totalChapterCount > 1,
                                        emit = emit,
                                    )
                                }
                            }
                            item(key = volumeAddKey(volume.id)) {
                                OutlineAddChapterButton(
                                    textRes = R.string.story_add_chapter_to_volume,
                                    enabled = controlsEnabled,
                                    onClick = { StoryEditorUiIntent.ShowCreateChapterDialog(volume.id).emit() },
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun OutlineSummary(
    storyTitle: String,
    volumeCount: Int,
    chapterCount: Int,
    totalCharacterCount: Int,
    updating: Boolean,
) {
    // 汇总卡片采用 Primary Container 柔和主色调与通透圆角
    AppCard(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 图标气泡
            RpIconBubble(Icons.AutoMirrored.Rounded.MenuBook)
            // 标题与聚合统计文案
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = storyTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.story_outline_stats_summary,
                        volumeCount,
                        chapterCount,
                        String.format(LocalLocale.current.platformLocale, "%,d", totalCharacterCount),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (updating) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            }
        }
    }
}

@Composable
private fun OutlineSectionHeader(
    title: String,
    itemCount: Int,
    totalCharacterCount: Int? = null,
    enabled: Boolean,
    onAddChapter: () -> Unit,
) {
    // 分区标题沿用原有数量展示和操作入口。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        if (totalCharacterCount != null) {
            Text(
                text = stringResource(
                    R.string.story_volume_stats,
                    itemCount,
                    String.format(LocalLocale.current.platformLocale, "%,d", totalCharacterCount),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        } else {
            Text(
                text = itemCount.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        IconButton(onClick = onAddChapter, enabled = enabled) {
            Icon(imageVector = Icons.Rounded.Add, contentDescription = stringResource(R.string.story_add_chapter))
        }
    }
}

@Composable
private fun StoryVolumeOutlineHeader(
    volume: StoryVolumeOutlineItem,
    collapsed: Boolean,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggleCollapsed: () -> Unit,
    onAddChapter: () -> Unit,
    emit: StoryEditorUiIntent.() -> Unit,
) {
    val totalWords = remember(volume.chapters) {
        volume.chapters.sumOf { storyChapterOutlineItem -> storyChapterOutlineItem.characterCount }
    }
    // 分卷头部采用容器卡片样式，包含章节数与总字数统计
    AppSurface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onToggleCollapsed)
                .padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 折叠与展开图标
            IconButton(onClick = onToggleCollapsed, enabled = enabled) {
                Icon(
                    imageVector = if (collapsed) {
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight
                    } else {
                        Icons.Rounded.KeyboardArrowDown
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            // 分卷标题与统计指标
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = volume.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.story_volume_stats,
                        volume.chapters.size,
                        String.format(LocalLocale.current.platformLocale, "%,d", totalWords),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 快速为本卷添加章节按钮
            IconButton(onClick = onAddChapter, enabled = enabled) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.story_add_chapter_to_volume),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StoryVolumeMenu(
                volumeId = volume.id,
                enabled = enabled,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                emit = emit,
            )
        }
    }
}

@Composable
private fun StoryChapterOutlineRow(
    index: Int,
    chapter: StoryChapterOutlineItem,
    selected: Boolean,
    isDragging: Boolean = false,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
    emit: StoryEditorUiIntent.() -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    // 章节行卡片容器，包含序号、高亮边框、当前编辑状态徽标与拖拽手柄
    val itemColor = when {
        isDragging -> MaterialTheme.colorScheme.surfaceVariant
        selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surface
    }
    val itemBorderColor = when {
        isDragging -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    }
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    StoryEditorUiIntent.SelectStoryChapter(chapter.id).emit()
                },
            ),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = if (isDragging) 8.dp else 0.dp,
        tonalElevation = if (selected || isDragging) 3.dp else 0.dp,
        color = itemColor,
        border = BorderStroke(
            width = if (selected || isDragging) 1.5.dp else 1.dp,
            color = itemBorderColor,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 章节两位工整序号徽标
            AppSurface(
                shape = RoundedCornerShape(8.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Text(
                    text = String.format(LocalLocale.current.platformLocale, "%02d", index + 1),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
            // 标题与字数/状态行
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = chapter.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(weight = 1f, fill = false),
                    )
                    if (selected) {
                        AppSurface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            contentColor = MaterialTheme.colorScheme.primary,
                        ) {
                            Text(
                                text = stringResource(R.string.story_active_chapter),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.story_character_count,
                            chapter.characterCount,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (chapter.characterCount < 50) {
                        AppSurface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ) {
                            Text(
                                text = stringResource(R.string.story_draft_chapter),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.5.dp),
                            )
                        }
                    }
                }
            }
            // 拖拽手柄视觉提示
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = stringResource(R.string.story_drag_handle),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.size(20.dp),
            )
            StoryChapterMenu(
                chapterId = chapter.id,
                enabled = enabled,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                canDelete = canDelete,
                emit = emit,
            )
        }
    }
}

@Composable
private fun OutlineAddChapterButton(
    textRes: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // 创建操作保留所属分卷的回调，视图不自行选择目标位置。
    val haptic = LocalHapticFeedback.current
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(textRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun OutlineEmptyMessage(
    text: String,
    onAction: (() -> Unit)? = null,
    actionTextRes: Int? = null,
    enabled: Boolean = true,
) {
    // 空分组提示保留原有占位尺寸和样式，避免改变拖放区域。
    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onAction != null && actionTextRes != null) {
                TextButton(onClick = onAction, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(actionTextRes), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun StoryVolumeMenu(
    volumeId: Long,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    emit: StoryEditorUiIntent.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // 新建章节
            OutlineMenuItem(
                textRes = R.string.story_add_chapter,
                icon = Icons.Rounded.Add,
                onClick = {
                    expanded = false
                    StoryEditorUiIntent.ShowCreateChapterDialog(volumeId).emit()
                },
            )
            // 重命名分卷
            OutlineMenuItem(
                textRes = R.string.rename,
                icon = Icons.Rounded.Edit,
                onClick = {
                    expanded = false
                    StoryEditorUiIntent.ShowRenameVolumeDialog(volumeId).emit()
                },
            )
            // 上移分卷
            OutlineMenuItem(
                textRes = R.string.move_up,
                icon = Icons.Rounded.ArrowUpward,
                enabled = canMoveUp,
                onClick = {
                    expanded = false
                    StoryEditorUiIntent.MoveStoryVolume(volumeId = volumeId, offset = -1).emit()
                },
            )
            // 下移分卷
            OutlineMenuItem(
                textRes = R.string.move_down,
                icon = Icons.Rounded.ArrowDownward,
                enabled = canMoveDown,
                onClick = {
                    expanded = false
                    StoryEditorUiIntent.MoveStoryVolume(volumeId = volumeId, offset = 1).emit()
                },
            )
            // 删除分卷
            OutlineMenuItem(
                textRes = R.string.delete,
                icon = Icons.Rounded.Delete,
                onClick = {
                    expanded = false
                    StoryEditorUiIntent.ShowDeleteVolumeDialog(volumeId).emit()
                },
            )
        }
    }
}

@Composable
private fun StoryChapterMenu(
    chapterId: Long,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
    emit: StoryEditorUiIntent.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // 重命名章节
            OutlineMenuItem(
                textRes = R.string.rename,
                icon = Icons.Rounded.Edit,
                onClick = {
                    expanded = false
                    StoryEditorUiIntent.ShowRenameChapterDialog(chapterId).emit()
                },
            )
            // 移动到其他分卷
            OutlineMenuItem(
                textRes = R.string.story_move_chapter,
                icon = Icons.Rounded.FolderOpen,
                onClick = {
                    expanded = false
                    StoryEditorUiIntent.ShowMoveStoryChapterDialog(chapterId).emit()
                },
            )
            // 上移章节
            OutlineMenuItem(
                textRes = R.string.move_up,
                icon = Icons.Rounded.ArrowUpward,
                enabled = canMoveUp,
                onClick = {
                    expanded = false
                    StoryEditorUiIntent.MoveStoryChapter(chapterId = chapterId, offset = -1).emit()
                },
            )
            // 下移章节
            OutlineMenuItem(
                textRes = R.string.move_down,
                icon = Icons.Rounded.ArrowDownward,
                enabled = canMoveDown,
                onClick = {
                    expanded = false
                    StoryEditorUiIntent.MoveStoryChapter(chapterId = chapterId, offset = 1).emit()
                },
            )
            // 删除章节
            OutlineMenuItem(
                textRes = R.string.delete,
                icon = Icons.Rounded.Delete,
                enabled = canDelete,
                onClick = {
                    expanded = false
                    StoryEditorUiIntent.ShowDeleteChapterDialog(chapterId).emit()
                },
            )
        }
    }
}

@Composable
private fun OutlineMenuItem(
    textRes: Int,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(textRes)) },
        leadingIcon = { Icon(imageVector = icon, contentDescription = null) },
        enabled = enabled,
        onClick = onClick,
    )
}

private const val OUTLINE_SUMMARY_KEY = "outline-summary"

private const val UNGROUPED_HEADER_KEY = "ungrouped-header"

private const val UNGROUPED_EMPTY_KEY = "ungrouped-empty"

private const val UNGROUPED_ADD_KEY = "ungrouped-add-button"

/** 大纲列表节点对应的拖动源和类型化投放目标。 */
private data class StoryOutlineDragNode(
    val chapterId: Long? = null,
    val dropTarget: StoryChapterDropTarget? = null
)

/** 为当前大纲建立稳定列表 key 到拖放语义的查找表。 */
private fun storyOutlineDragNodes(
    state: StoryEditorStructureState,
): Map<Any, StoryOutlineDragNode> = buildMap {
    // 分组头部和空状态接收首部投放，追加按钮接收尾部投放。
    put(
        key = UNGROUPED_HEADER_KEY,
        value = StoryOutlineDragNode(
            dropTarget = StoryChapterDropTarget.Container(volumeId = null, position = StoryChapterDropPosition.Start),
        ),
    )
    put(UNGROUPED_EMPTY_KEY, getValue(UNGROUPED_HEADER_KEY))
    put(
        key = UNGROUPED_ADD_KEY,
        value = StoryOutlineDragNode(
            dropTarget = StoryChapterDropTarget.Container(volumeId = null, position = StoryChapterDropPosition.End),
        ),
    )
    state.volumes.forEach { volume ->
        val startNode = StoryOutlineDragNode(
            dropTarget = StoryChapterDropTarget.Container(
                volumeId = volume.id,
                position = StoryChapterDropPosition.Start,
            ),
        )
        put(volumeHeaderKey(volume.id), startNode)
        put(volumeEmptyKey(volume.id), startNode)
        put(
            key = volumeAddKey(volume.id),
            value = StoryOutlineDragNode(
                dropTarget = StoryChapterDropTarget.Container(
                    volumeId = volume.id,
                    position = StoryChapterDropPosition.End,
                ),
            ),
        )
    }
    // 章节节点既是拖动源，也是插入到该章节之前的投放锚点。
    (state.ungroupedChapters + state.volumes.flatMap { storyVolumeOutlineItem -> storyVolumeOutlineItem.chapters }).forEach { chapter ->
        put(
            key = chapterOutlineKey(chapter.id),
            value = StoryOutlineDragNode(
                chapterId = chapter.id,
                dropTarget = StoryChapterDropTarget.Chapter(chapter.id),
            ),
        )
    }
}

private fun chapterOutlineKey(chapterId: Long): String = "chapter-$chapterId"

private fun volumeHeaderKey(volumeId: Long): String = "volume-$volumeId"

private fun volumeEmptyKey(volumeId: Long): String = "volume-$volumeId-empty"

private fun volumeAddKey(volumeId: Long): String = "volume-$volumeId-add-button"
