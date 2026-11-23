package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.model.WorldBookSelectionSummaryItem
import me.kafuuneko.rpclient.ui.theme.AppTheme

private const val MAX_PREVIEW_WORLD_BOOKS = 5

/**
 * 创建页与设置页共用的世界书选择摘要卡片，点击后由页面拉起完整检索选择器。
 *
 * - 当本地不存在任何可选世界书条目时，降级为不可点击的空态卡片；
 * - 已启用条目时展示总数、活跃世界书数量以及最多五本已启用世界书的标签概览，超出部分以计数徽章折叠。
 *
 * @param totalGroupCount 当前可供选择的世界书总数
 * @param selectedEntryCount 当前已启用的条目总数
 * @param selectedGroups 至少包含一条启用条目的世界书摘要列表
 * @param enabled 卡片是否响应点击
 * @param onClick 点击卡片打开世界书选择器的回调
 * @param modifier 外部布局修饰符
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorldBookSelectionSummaryCard(
    totalGroupCount: Int,
    selectedEntryCount: Int,
    selectedGroups: List<WorldBookSelectionSummaryItem>,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 本地没有可选世界书时直接展示静态空态提示
    if (totalGroupCount == 0) {
        NoWorldBookEntriesCard(modifier = modifier)
        return
    }

    // 根据是否已有启用条目区分描边强调程度与文案
    val hasSelection = selectedEntryCount > 0
    val titleText = if (hasSelection) {
        stringResource(R.string.world_books_enabled, selectedEntryCount)
    } else {
        stringResource(R.string.world_book_selection_empty)
    }
    val subtitleText = if (selectedGroups.isNotEmpty()) {
        stringResource(
            R.string.world_book_selection_summary_subtitle,
            selectedGroups.size,
            totalGroupCount,
        )
    } else {
        stringResource(R.string.world_book_selection_tap_to_choose, totalGroupCount)
    }
    val actionText = stringResource(if (hasSelection) R.string.manage else R.string.picker_choose)

    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (hasSelection) 1.5.dp else 1.dp,
            color = if (hasSelection) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            },
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WorldBookSelectionSummaryHeader(
                title = titleText,
                subtitle = subtitleText,
                action = actionText,
            )

            // 存在已启用世界书时以紧凑标签预览前几本，超出上限折叠为增量计数
            if (selectedGroups.isNotEmpty()) {
                val overflowCount = (selectedGroups.size - MAX_PREVIEW_WORLD_BOOKS).coerceAtLeast(0)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    selectedGroups.take(MAX_PREVIEW_WORLD_BOOKS).forEach { group ->
                        RpTagPill(
                            text = stringResource(
                                R.string.world_book_summary_group_count,
                                group.name,
                                group.selectedCount,
                                group.totalCount,
                            ),
                        )
                    }
                    if (overflowCount > 0) {
                        RpMetaPill(
                            text = stringResource(
                                R.string.world_book_summary_more_count,
                                overflowCount
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** 当库中无可选条目时展示静态说明。 */
@Composable
private fun NoWorldBookEntriesCard(modifier: Modifier) {
    // 空库仍保留摘要卡片的样式，便于在主表单中找到世界书区域。
    AppCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RpIconBubble(icon = Icons.AutoMirrored.Rounded.MenuBook)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.no_world_book_entries_selectable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 显示世界书条目统计及选择器入口。 */
@Composable
private fun WorldBookSelectionSummaryHeader(title: String, subtitle: String, action: String) {
    // 顶部摘要行展示状态图标、条目统计以及打开选择器的操作引导
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RpIconBubble(icon = Icons.AutoMirrored.Rounded.MenuBook)
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun WorldBookSelectionSummaryCardPreview() {
    // 对比已有选择与等待选择时的摘要内容。
    AppTheme(dynamicColor = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WorldBookSelectionSummaryCard(
                totalGroupCount = 12,
                selectedEntryCount = 9,
                selectedGroups = listOf(
                    WorldBookSelectionSummaryItem(
                        lorebookId = 1L,
                        name = "Fog Harbor",
                        selectedCount = 6,
                        totalCount = 6,
                    ),
                    WorldBookSelectionSummaryItem(
                        lorebookId = 2L,
                        name = "Alchemy Rules",
                        selectedCount = 3,
                        totalCount = 14,
                    ),
                ),
                onClick = {},
            )
            WorldBookSelectionSummaryCard(
                totalGroupCount = 12,
                selectedEntryCount = 0,
                selectedGroups = emptyList(),
                onClick = {},
            )
        }
    }
}
