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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.toolsettings.presentation.HttpToolListItem
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsUiIntent
import me.kafuuneko.rpclient.libs.agent.connection.HttpToolKind
import me.kafuuneko.rpclient.ui.theme.AccentEmeraldColor
import me.kafuuneko.rpclient.ui.theme.AccentIndigoColor
import me.kafuuneko.rpclient.ui.theme.AccentSkyColor
import me.kafuuneko.rpclient.ui.widgets.AppCard
import me.kafuuneko.rpclient.ui.widgets.PageSwitch as Switch
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpMetaPill
import me.kafuuneko.rpclient.ui.widgets.RpTagPill

/** 渲染单个自定义 HTTP 或网页读取工具摘要卡，提供启停、测试、复制与删除入口。 */
@Composable
internal fun HttpToolCard(
    item: HttpToolListItem,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit,
) {
    // 沿用模型配置卡片圆角、边框与禁用态底色层次，点击整卡直接进入编辑。
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = !busy) { ToolSettingsUiIntent.EditTool(item.id).emit() },
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
            HttpToolCardHeading(item = item, busy = busy, emit = emit)

            HttpToolCardStatus(item = item)

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
            )

            HttpToolCardActions(item = item, busy = busy, emit = emit)

        }
    }
}


/** 卡片标题与启停只渲染非敏感摘要，编辑动作由页面处理。 */
@Composable
private fun HttpToolCardHeading(
    item: HttpToolListItem,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit
) {
    // 按工具类型区分主题图标色，禁用时降低强调色透明度以便一眼分辨状态。
    val isReader = item.kind == HttpToolKind.ReadUrl
    val accent = if (isReader) AccentSkyColor else AccentIndigoColor
    val activeAccent = if (item.enabled) accent else accent.copy(alpha = 0.45f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RpIconBubble(
            icon = if (isReader) Icons.Rounded.Language else Icons.Rounded.Public,
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
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.description.isNotBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Switch(
            checked = item.enabled,
            enabled = !busy,
            onCheckedChange = { checked ->
                ToolSettingsUiIntent.ToggleToolEnabled(item.id, checked).emit()
            },
        )
    }
}

/** 配置与认证状态由统一就绪服务提供，控件只选择展示样式。 */
@Composable
private fun HttpToolCardStatus(item: HttpToolListItem) {
    val isReader = item.kind == HttpToolKind.ReadUrl
    // 集中展示类型、绑定连接方法与待配置警告，便于一眼判断可用状态。
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RpMetaPill(
            text = stringResource(
                if (isReader) R.string.tool_settings_kind_read_url else R.string.tool_settings_kind_http,
            ),
        )
        if (!isReader) {
            val connLabel = item.connectionName?.let { name ->
                val method = item.connectionMethod.orEmpty()
                if (method.isNotBlank()) "$method · $name" else name
            } ?: stringResource(R.string.tool_settings_unbound)
            if (item.connectionName != null) {
                RpTagPill(text = connLabel)
            } else {
                StatusPill(
                    text = connLabel,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        when {
            !item.enabled -> RpMetaPill(text = stringResource(R.string.disabled))
            item.needsConfiguration -> StatusPill(
                text = stringResource(R.string.tool_settings_needs_configuration),
                color = MaterialTheme.colorScheme.error,
            )

            else -> StatusPill(
                text = stringResource(R.string.enabled),
                color = AccentEmeraldColor,
            )
        }
    }
}

/** 工具卡的编辑、复制和删除统一发送 Intent，不在控件内写配置。 */
@Composable
private fun HttpToolCardActions(
    item: HttpToolListItem,
    busy: Boolean,
    emit: ToolSettingsUiIntent.() -> Unit
) {
    // 底部左侧放编辑与测试胶囊按钮，右侧放复制和删除图标按钮。
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
            onClick = { ToolSettingsUiIntent.EditTool(item.id).emit() },
        )
        CardActionPill(
            icon = Icons.Rounded.PlayArrow,
            label = stringResource(R.string.test),
            enabled = !busy && item.enabled && !item.needsConfiguration,
            emphasized = false,
            onClick = { ToolSettingsUiIntent.OpenTestDialog(item.id).emit() },
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
            onClick = { ToolSettingsUiIntent.CopyTool(item.id).emit() },
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
            onClick = { ToolSettingsUiIntent.ShowDeleteTool(item.id).emit() },
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
