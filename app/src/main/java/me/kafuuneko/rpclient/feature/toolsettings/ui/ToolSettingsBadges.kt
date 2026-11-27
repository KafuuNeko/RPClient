@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package me.kafuuneko.rpclient.feature.toolsettings.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.agent.connection.ToolAuthMode
import me.kafuuneko.rpclient.ui.widgets.AppSurface

/** 渲染工具或连接卡片底部的带图标胶囊操作按钮。 */
@Composable
internal fun CardActionPill(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    // 根据启用与主次强调态计算容器、文字和描边配色。
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
        emphasized -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
        emphasized -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    AppSurface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = BorderStroke(
            width = 0.5.dp,
            color = if (emphasized && enabled) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}


/** 渲染带浅色背景与细描边的语义状态标签。 */
@Composable
internal fun StatusPill(
    text: String,
    color: Color,
) {
    // 单行省略展示，避免窄屏或大字号下状态胶囊高度膨胀。
    AppSurface(
        shape = RoundedCornerShape(9.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(width = 0.5.dp, color = color.copy(alpha = 0.28f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 将持久化认证模式编码映射为本地化显示名称。 */
@Composable
internal fun authModeLabel(authMode: String): String = when (authMode) {
    ToolAuthMode.Bearer -> stringResource(R.string.tool_settings_auth_bearer)
    ToolAuthMode.ApiKey -> stringResource(R.string.tool_settings_auth_api_key)
    ToolAuthMode.Header -> stringResource(R.string.tool_settings_auth_header)
    ToolAuthMode.Query -> stringResource(R.string.tool_settings_auth_query)
    else -> stringResource(R.string.tool_settings_auth_none)
}
