package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.utils.toggle

/**
 * 会话设置页世界书分组中的条目展示模型。
 *
 * @param id 条目唯一标识
 * @param name 条目名称
 * @param content 条目正文内容
 * @param keywords 主触发关键词列表
 * @param constant 是否为常驻激活条目
 * @param enabled 当前会话是否已启用该条目
 */
data class SessionWorldBookEntryItem(
    val id: Long,
    val name: String,
    val content: String,
    val keywords: List<String>,
    val constant: Boolean,
    val enabled: Boolean,
)

/**
 * 会话设置页世界书列表中单本世界书的分组展示模型。
 *
 * @param lorebookId 世界书唯一标识
 * @param lorebookName 世界书显示名称
 * @param enabledCount 当前会话已启用的条目数量
 * @param totalCount 该世界书包含的全部条目数量
 * @param entries 该世界书下的完整条目列表
 */
data class SessionWorldBookGroupItem(
    val lorebookId: Long,
    val lorebookName: String,
    val enabledCount: Int,
    val totalCount: Int,
    val entries: List<SessionWorldBookEntryItem>,
) {
    /** 分组内是否存在条目且全部已为当前会话启用。 */
    val isAllEnabled: Boolean
        get() = totalCount > 0 && enabledCount == totalCount
}

/**
 * 单聊与群聊设置页共用的世界书管理分区。
 *
 * - 顶部依次固定“世界书管理”与“添加世界书”操作入口，与设置页操作行保持统一外观；
 * - 下方直接以可折叠卡片列出当前会话已启用的世界书，支持就地整本开关、展开与逐条启停；
 * - 尚未启用的世界书由调用方在点击添加入口后拉起检索弹窗按需添加。
 *
 * @param totalGroupCount 本地可供选择的世界书总数
 * @param enabledGroups 当前会话已启用的世界书分组列表
 * @param onOpenWorldBookManager 点击“世界书管理”入口的回调
 * @param onAddWorldBooks 点击“添加世界书”入口的回调
 * @param onToggleLorebook 切换整本世界书启用状态的回调
 * @param onToggleEntry 切换单个条目启用状态的回调
 */
@Composable
internal fun SessionWorldBookSettingsSection(
    totalGroupCount: Int,
    enabledGroups: List<SessionWorldBookGroupItem>,
    onOpenWorldBookManager: () -> Unit,
    onAddWorldBooks: () -> Unit,
    onToggleLorebook: (Long) -> Unit,
    onToggleEntry: (Long) -> Unit,
) {
    // 展开状态属于局部视觉折叠状态，与会话持久化开关相互独立
    var expandedLorebookIds by remember { mutableStateOf(emptySet<Long>()) }
    val addableLorebookCount = (totalGroupCount - enabledGroups.size).coerceAtLeast(0)

    SettingsSection(title = stringResource(R.string.world_book)) {
        // 顶部优先呈现全局管理与添加入口，避免已启用列表较长时遮挡高频操作
        SettingsActionRow(
            icon = Icons.AutoMirrored.Rounded.MenuBook,
            title = stringResource(R.string.world_book_manager),
            onClick = onOpenWorldBookManager,
        )
        if (totalGroupCount == 0) {
            Text(
                text = stringResource(R.string.no_world_book_entries_selectable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            if (addableLorebookCount > 0) {
                SettingsActionRow(
                    icon = Icons.Rounded.Add,
                    title = stringResource(
                        R.string.world_book_add_action,
                        addableLorebookCount,
                    ),
                    onClick = onAddWorldBooks,
                )
            } else {
                Text(
                    text = stringResource(R.string.world_book_all_enabled),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 已启用世界书直接展开于设置区，方便用户就地微调或关闭
            enabledGroups.forEach { group ->
                key(group.lorebookId) {
                    EnabledWorldBookGroupCard(
                        group = group,
                        expanded = group.lorebookId in expandedLorebookIds,
                        onExpandedChange = {
                            expandedLorebookIds = expandedLorebookIds.toggle(group.lorebookId)
                        },
                        onToggleLorebook = onToggleLorebook,
                        onToggleEntry = onToggleEntry,
                    )
                }
            }
        }
    }
}

/** 展示可折叠的已启用世界书卡片，支持整本勾选与展开逐条操作。 */
@Composable
private fun EnabledWorldBookGroupCard(
    group: SessionWorldBookGroupItem,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onToggleLorebook: (Long) -> Unit,
    onToggleEntry: (Long) -> Unit,
) {
    // 折叠态整卡点击展开，展开态点击标题行收起，复选徽章独立控制整本开关
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!expanded) Modifier.clickable(onClick = onExpandedChange) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 14.dp,
                vertical = if (expanded) 14.dp else 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp)
                    .then(if (expanded) Modifier.clickable(onClick = onExpandedChange) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RpIconBubble(
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    size = 32.dp,
                    iconSize = 17.dp,
                    shape = RoundedCornerShape(10.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.lorebookName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            R.string.enabled_entries_count,
                            group.enabledCount,
                            group.totalCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                val allSelected = group.isAllEnabled
                val indeterminate = group.enabledCount > 0 && !group.isAllEnabled
                RpCheckBadge(
                    selected = allSelected,
                    indeterminate = indeterminate,
                    onClick = { onToggleLorebook(group.lorebookId) },
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = if (expanded) {
                        Icons.Rounded.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
            }
            if (expanded) {
                // 展开后按原顺序渲染完整条目列表，允许就地关闭或重新启用同书内的其他条目
                group.entries.forEach { entry ->
                    EnabledWorldBookEntryRow(
                        entry = entry,
                        onToggle = { onToggleEntry(entry.id) },
                    )
                }
            }
        }
    }
}

/** 展示单个世界书条目卡片，供用户在设置页内直接启停条目。 */
@Composable
private fun EnabledWorldBookEntryRow(
    entry: SessionWorldBookEntryItem,
    onToggle: () -> Unit,
) {
    val constantLabel = stringResource(R.string.entry_constant)
    // 启用状态由描边宽度、高亮色与右侧复选徽章共同表达，底板沿用页面统一外观
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (entry.enabled) 1.5.dp else 1.dp,
            color = if (entry.enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            },
        ),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = entry.name.ifBlank { stringResource(R.string.unnamed_entry) },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.content.isNotBlank()) {
                    Text(
                        text = entry.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                RpTagRow(
                    tags = buildList {
                        if (entry.constant) add(constantLabel)
                        addAll(entry.keywords)
                    },
                    maxCount = 3,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            RpCheckBadge(selected = entry.enabled)
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun SessionWorldBookSettingsSectionPreview() {
    AppTheme(dynamicColor = false) {
        Column(modifier = Modifier.padding(16.dp)) {
            SessionWorldBookSettingsSection(
                totalGroupCount = 4,
                enabledGroups = listOf(
                    SessionWorldBookGroupItem(
                        lorebookId = 1L,
                        lorebookName = "Fog Harbor",
                        enabledCount = 1,
                        totalCount = 2,
                        entries = listOf(
                            SessionWorldBookEntryItem(
                                id = 11L,
                                name = "Old District",
                                content = "Lanterns stay lit through every coastal storm.",
                                keywords = listOf("harbor", "rain"),
                                constant = true,
                                enabled = true,
                            ),
                            SessionWorldBookEntryItem(
                                id = 12L,
                                name = "Signal Tower",
                                content = "Abandoned lighthouse overlooking the outer pier.",
                                keywords = listOf("tower"),
                                constant = false,
                                enabled = false,
                            ),
                        ),
                    ),
                ),
                onOpenWorldBookManager = {},
                onAddWorldBooks = {},
                onToggleLorebook = {},
                onToggleEntry = {},
            )
        }
    }
}
