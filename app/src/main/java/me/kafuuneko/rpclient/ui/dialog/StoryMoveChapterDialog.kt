package me.kafuuneko.rpclient.ui.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.widgets.draggableScrollIndicator
import me.kafuuneko.rpclient.ui.theme.AppTheme

/**
 * 移动章节弹窗中的目标分卷或未分卷选择项。
 *
 * @property volumeId 目标分卷 ID，为空表示未分卷章节
 * @property title 目标分组显示名称
 * @property chapterCount 目标分组当前章节数
 */
data class StoryChapterDestinationOption(
    val volumeId: Long?,
    val title: String,
    val chapterCount: Int = 0
)

/**
 * 选择章节所属分卷并确认移动的对话框。
 *
 * @param onDismissRequest 请求关闭对话框时调用
 * @param chapterTitle 当前待移动章节标题
 * @param options 可选择的目标分组
 * @param selectedVolumeId 当前选中的目标分卷 ID，为空表示未分卷章节
 * @param isSaving 是否正在保存移动结果
 * @param onDestinationSelected 选择目标分组时调用
 * @param onConfirm 确认移动时调用
 */
@Composable
fun StoryMoveChapterDialog(
    onDismissRequest: () -> Unit,
    chapterTitle: String,
    options: List<StoryChapterDestinationOption>,
    selectedVolumeId: Long?,
    isSaving: Boolean,
    onDestinationSelected: (Long?) -> Unit,
    onConfirm: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    // 内容区独立滚动，目标分卷较多时仍由外层对话框固定确认操作。
    AppConfirmDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.story_move_chapter),
        badgeIcon = Icons.Rounded.FolderOpen,
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        confirmEnabled = !isSaving,
        isConfirmLoading = isSaving,
        onConfirm = onConfirm
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .draggableScrollIndicator(scrollState)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 当前移动章节信息卡片
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = chapterTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.story_move_chapter),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 目标分卷卡片式单选列表
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 选择只更新待确认目标，实际移动由确认动作统一触发。
                options.forEach { option ->
                    val selected = selectedVolumeId == option.volumeId
                    val isUngrouped = option.volumeId == null
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isSaving) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDestinationSelected(option.volumeId)
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 分卷类型图标指示
                            Icon(
                                imageVector = if (isUngrouped) {
                                    Icons.Rounded.FolderOpen
                                } else {
                                    Icons.Rounded.Book
                                },
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            // 标题与章节数统计
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = option.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(
                                        R.string.story_chapter_count,
                                        option.chapterCount
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // 选中态对勾指示器
                            Surface(
                                modifier = Modifier.size(22.dp),
                                shape = CircleShape,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                },
                                border = if (selected) {
                                    null
                                } else {
                                    BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(3.dp)
                                            .size(16.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "StoryMoveChapterDialog", showBackground = true)
@Composable
private fun StoryMoveChapterDialogPreview() {
    AppTheme(dynamicColor = false) {
        StoryMoveChapterDialog(
            onDismissRequest = {},
            chapterTitle = "第一章 宿命之始",
            options = listOf(
                StoryChapterDestinationOption(null, "未分卷章节", 2),
                StoryChapterDestinationOption(1L, "第一卷 启程", 5),
                StoryChapterDestinationOption(2L, "第二卷 远征", 3)
            ),
            selectedVolumeId = 1L,
            isSaving = false,
            onDestinationSelected = {},
            onConfirm = {}
        )
    }
}
