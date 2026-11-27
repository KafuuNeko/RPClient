@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package me.kafuuneko.rpclient.feature.toolsettings.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsTab
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsUiIntent
import me.kafuuneko.rpclient.ui.theme.AccentAmberColor
import me.kafuuneko.rpclient.ui.theme.AccentIndigoColor
import me.kafuuneko.rpclient.ui.theme.AccentSkyColor
import me.kafuuneko.rpclient.ui.widgets.AppCard
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.PageButton as Button
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpSegmentedTabs

/** 渲染工具管理页顶部分段标签与对应分区的语境说明条。 */
@Composable
internal fun ToolSettingsHeader(
    tab: ToolSettingsTab,
    enabled: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    // 根据当前标签选择对应说明文案，操作忙碌期间禁止切换分区。
    val tipRes = when (tab) {
        ToolSettingsTab.Tools -> R.string.tool_settings_tools_tip
        ToolSettingsTab.Connections -> R.string.tool_settings_connections_tip
        ToolSettingsTab.Builtins -> R.string.tool_settings_builtins_tip
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RpSegmentedTabs(
            tabs = ToolSettingsTab.entries,
            selectedTab = tab,
            onTabSelected = { selected ->
                if (enabled) ToolSettingsUiIntent.SelectTab(selected).emit()
            },
            tabLabel = { item -> stringResource(item.titleRes()) },
        )
        AppSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
            border = BorderStroke(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(16.dp),
                )
                Text(
                    text = stringResource(tipRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


/** 渲染常用搜索、公网读取模板及自定义工具的快捷创建入口栏。 */
@Composable
internal fun QuickTemplateBar(
    enabled: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    // 使用高辨识度带图标胶囊呈现常用模板与自定义新建入口，与列表内容区分层次。
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RpSectionHeader(title = stringResource(R.string.tool_settings_quick_templates))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TemplateActionPill(
                icon = Icons.Rounded.Search,
                label = stringResource(R.string.tool_settings_add_search),
                accent = AccentIndigoColor,
                enabled = enabled,
                onClick = { ToolSettingsUiIntent.CreateTemplate(readUrl = false).emit() },
            )
            TemplateActionPill(
                icon = Icons.Rounded.Language,
                label = stringResource(R.string.tool_settings_add_reader),
                accent = AccentSkyColor,
                enabled = enabled,
                onClick = { ToolSettingsUiIntent.CreateTemplate(readUrl = true).emit() },
            )
            TemplateActionPill(
                icon = Icons.Rounded.Add,
                label = stringResource(R.string.tool_settings_add_custom),
                accent = AccentAmberColor,
                enabled = enabled,
                onClick = { ToolSettingsUiIntent.CreateTool.emit() },
            )
        }
    }
}


/** 渲染带强调色边框与图标的模板快捷创建胶囊。 */
@Composable
internal fun TemplateActionPill(
    icon: ImageVector,
    label: String,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // 单行展示模板标签，配合 FlowRow 在窄屏下自动折行。
    AppSurface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(width = 0.6.dp, color = accent.copy(alpha = 0.30f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}


/** HTTP 工具列表为空时的引导卡片，提供一键添加公网读取模板入口。 */
@Composable
internal fun EmptyToolsCard(
    enabled: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    // 居中呈现空态说明与无认证网页读取模板创建按钮。
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
        border = BorderStroke(
            width = 0.8.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RpIconBubble(
                icon = Icons.Rounded.Public,
                size = 50.dp,
                contentColor = AccentSkyColor,
                containerColor = AccentSkyColor.copy(alpha = 0.14f),
            )
            Text(
                text = stringResource(R.string.tool_settings_empty_tools_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.tool_settings_empty_tools_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = { ToolSettingsUiIntent.CreateTemplate(readUrl = true).emit() },
                enabled = enabled,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.tool_settings_add_reader))
            }
        }
    }
}


/** HTTP 连接列表为空时的引导卡片，提供新建连接入口。 */
@Composable
internal fun EmptyConnectionsCard(
    enabled: Boolean,
    onCreate: () -> Unit,
) {
    // 居中呈现空态说明并引导用户先建立连接再绑定工具。
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
        border = BorderStroke(
            width = 0.8.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RpIconBubble(
                icon = Icons.Rounded.Dns,
                size = 50.dp,
                contentColor = AccentIndigoColor,
                containerColor = AccentIndigoColor.copy(alpha = 0.14f),
            )
            Text(
                text = stringResource(R.string.tool_settings_empty_connections_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.tool_settings_empty_connections_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onCreate,
                enabled = enabled,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.tool_settings_add_connection))
            }
        }
    }
}
