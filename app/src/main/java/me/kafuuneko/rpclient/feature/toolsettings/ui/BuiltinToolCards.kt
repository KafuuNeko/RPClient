@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package me.kafuuneko.rpclient.feature.toolsettings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.toolsettings.presentation.BuiltinToolListItem
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsUiIntent
import me.kafuuneko.rpclient.libs.agent.tool.catalog.ToolGroup
import me.kafuuneko.rpclient.ui.agent.titleRes
import me.kafuuneko.rpclient.ui.widgets.RpSettingsDivider
import me.kafuuneko.rpclient.ui.widgets.RpSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpTagPill

/** 按领域分组渲染内置工具设置卡，保持与会话选择弹窗一致的分组标题。 */
@Composable
internal fun BuiltinToolGroupCard(
    group: ToolGroup,
    items: List<BuiltinToolListItem>,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    // 顶部渲染共享分组标题，组内依次排列工具摘要行与分隔线。
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(group.titleRes()),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
        RpSettingsGroup {
            items.forEachIndexed { index, item ->
                if (index > 0) RpSettingsDivider(startIndent = false)
                BuiltinToolRow(
                    item = item,
                    busy = busy,
                    emit = emit,
                )
            }
        }
    }
}

/** 渲染单个内置工具条目行，支持点击编辑与已自定义条目的快捷重置。 */
@Composable
internal fun BuiltinToolRow(
    item: BuiltinToolListItem,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    // 沿用提示词预设列表行设计：左侧主色强调条标示已自定义条目，点击进入结构化编辑器。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy) { ToolSettingsUiIntent.EditBuiltin(item.id).emit() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.customized) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        BuiltinToolRowBody(
            item = item,
            modifier = Modifier.weight(1f),
        )
        // 已自定义条目提供行内重置快捷按钮，操作经 Intent 交由 ViewModel 处理。
        if (item.customized) {
            IconButton(
                onClick = { ToolSettingsUiIntent.ResetBuiltinOverride(item.id).emit() },
                enabled = !busy,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = stringResource(R.string.reset),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 展示内置工具名称、自定义标记、稳定 ID 与生效说明摘要。 */
@Composable
private fun BuiltinToolRowBody(
    item: BuiltinToolListItem,
    modifier: Modifier = Modifier,
) {
    // 优先呈现自定义展示名，稳定 toolId 与生效说明作为只读摘要辅助识别。
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.customDisplayName ?: stringResource(item.defaultLabelRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (item.customized) FontWeight.Bold else FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (item.customized) {
                RpTagPill(text = stringResource(R.string.tool_settings_customized_badge))
            }
        }
        Text(
            text = item.id,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.descriptionPreview.isNotBlank()) {
            Text(
                text = item.descriptionPreview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 按共享领域分组顺序整理内置工具列表并记忆结果，避免滚动期间重复过滤。 */
@Composable
internal fun groupBuiltins(
    items: List<BuiltinToolListItem>,
): List<Pair<ToolGroup, List<BuiltinToolListItem>>> = remember(items) {
    ToolGroup.entries.mapNotNull { group ->
        val matched = items.filter { it.group == group }
        if (matched.isEmpty()) null else group to matched
    }
}
