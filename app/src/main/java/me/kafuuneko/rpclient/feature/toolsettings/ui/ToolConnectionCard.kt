@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package me.kafuuneko.rpclient.feature.toolsettings.ui

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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolConnectionListItem
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsUiIntent
import me.kafuuneko.rpclient.libs.agent.connection.ToolAuthMode
import me.kafuuneko.rpclient.ui.theme.AccentAmberColor
import me.kafuuneko.rpclient.ui.theme.AccentEmeraldColor
import me.kafuuneko.rpclient.ui.theme.AccentIndigoColor
import me.kafuuneko.rpclient.ui.widgets.AppCard
import me.kafuuneko.rpclient.ui.widgets.PageSwitch as Switch
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpMetaPill
import me.kafuuneko.rpclient.ui.widgets.RpTagPill

/** 渲染单个 HTTP 连接摘要卡，展示目标地址、认证状态与凭据管理入口。 */
@Composable
internal fun ConnectionCard(
    item: ToolConnectionListItem,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    // 沿用配置卡片边框与禁用底色层次，点击整卡直接进入连接编辑器。
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = !busy) { ToolSettingsUiIntent.EditConnection(item.id).emit() },
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = if (item.enabled) 1.dp else 0.8.dp,
            color = when {
                !item.enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                item.needsConfiguration -> MaterialTheme.colorScheme.error.copy(alpha = 0.40f)
                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            },
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (item.enabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ConnectionToolCardHeading(item = item, busy = busy, emit = emit)

            ConnectionToolCardStatus(item = item)

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
            )

            ConnectionToolCardActions(item = item, busy = busy, emit = emit)

        }
    }
}

/** 卡片标题与启停只渲染非敏感摘要，编辑动作由页面处理。 */
@Composable
private fun ConnectionToolCardHeading(
    item: ToolConnectionListItem,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit
) {
    val activeAccent =
        if (item.enabled) AccentIndigoColor else AccentIndigoColor.copy(alpha = 0.45f)
    val displayUrl = item.url
        .removePrefix("https://")
        .removePrefix("http://")
        .ifBlank { stringResource(R.string.tool_settings_no_url) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RpIconBubble(
            icon = Icons.Rounded.Dns,
            size = 40.dp,
            contentColor = activeAccent,
            containerColor = activeAccent.copy(alpha = 0.14f),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = displayUrl,
                style = MaterialTheme.typography.labelSmall,
                color = if (item.url.isBlank()) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = item.enabled,
            enabled = !busy,
            onCheckedChange = { checked ->
                ToolSettingsUiIntent.ToggleConnectionEnabled(item.id, checked).emit()
            },
        )
    }
}

/** 配置与认证状态由统一就绪服务提供，控件只选择展示样式。 */
@Composable
private fun ConnectionToolCardStatus(item: ToolConnectionListItem) {
    // 清晰展示 HTTP 方法、认证凭据状态、局域网权限与已关联工具数。
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RpTagPill(text = item.method)
        val authLabel = authModeLabel(item.authMode)
        if (item.authMode == ToolAuthMode.None) {
            RpMetaPill(text = authLabel)
        } else if (item.hasCredential) {
            StatusPill(
                text = "$authLabel · ${stringResource(R.string.tool_settings_credential_bound)}",
                color = AccentEmeraldColor,
            )
        } else {
            StatusPill(
                text = "$authLabel · ${stringResource(R.string.tool_settings_credential_missing)}",
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (item.allowPrivateAddresses) {
            StatusPill(
                text = stringResource(R.string.tool_settings_allow_private),
                color = AccentAmberColor,
            )
        }
        if (item.boundToolCount > 0) {
            RpMetaPill(
                text = stringResource(
                    R.string.tool_settings_bound_tools_count,
                    item.boundToolCount,
                ),
            )
        }
    }
}

/** 工具卡的编辑、复制和删除统一发送 Intent，不在控件内写配置。 */
@Composable
private fun ConnectionToolCardActions(
    item: ToolConnectionListItem,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit
) {
    // 左侧提供编辑与凭据管理胶囊，右侧排列复制与删除图标按钮。
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CardActionPill(
            icon = Icons.Rounded.Edit,
            label = stringResource(R.string.edit),
            enabled = !busy,
            emphasized = true,
            onClick = { ToolSettingsUiIntent.EditConnection(item.id).emit() },
        )
        CardActionPill(
            icon = Icons.Rounded.Key,
            label = stringResource(
                if (item.hasCredential) {
                    R.string.tool_settings_credential_manage
                } else {
                    R.string.tool_settings_credential
                },
            ),
            enabled = !busy,
            emphasized = false,
            onClick = { ToolSettingsUiIntent.ShowCredentialDialog(item.id).emit() },
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
            onClick = { ToolSettingsUiIntent.CopyConnection(item.id).emit() },
            enabled = !busy,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = stringResource(R.string.copy),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.size(19.dp),
            )
        }
        IconButton(
            onClick = { ToolSettingsUiIntent.ShowDeleteConnection(item.id).emit() },
            enabled = !busy,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
