package me.kafuuneko.rpclient.ui.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import me.kafuuneko.rpclient.ui.widgets.AppSurface as Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.theme.AppTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** 操作项定义。 */
data class AppActionItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String? = null,
    val enabled: Boolean = true,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * 列表动作/选项选择对话框。
 *
 * 适用于包含多个动作入口的操作面板（如导入导出、多格式操作集合等）。
 */
@Composable
fun AppActionListDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badgeIcon: ImageVector = Icons.Rounded.FolderOpen,
    badgeTone: DialogBadgeTone = DialogBadgeTone.Primary,
    actions: List<AppActionItem>,
    dismissText: String = stringResource(R.string.close)
) {
    AppDialogScaffold(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = title,
        subtitle = subtitle,
        badgeIcon = badgeIcon,
        badgeTone = badgeTone,
        compactHeader = true,
        confirmText = "",
        onConfirm = null,
        dismissText = dismissText,
        onDismiss = onDismissRequest,
        scrollableContent = true
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            actions.forEach { action ->
                ActionItemRow(action = action, onDismissRequest = onDismissRequest)
            }
        }
    }
}

@Composable
private fun ActionItemRow(
    action: AppActionItem,
    onDismissRequest: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val containerColor = if (action.isDestructive) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }

    val contentColor = if (!action.enabled) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    } else if (action.isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val iconContainerColor = if (action.isDestructive) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    }

    val iconColor = if (!action.enabled) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    } else if (action.isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = action.enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                action.onClick()
                onDismissRequest()
            },
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(
            1.dp,
            if (action.isDestructive) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标衬底圆角容器
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(dialogControlColor(iconContainerColor), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                if (!action.subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = action.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Preview(name = "AppActionListDialog - Light", showBackground = true)
@Composable
private fun AppActionListDialogPreview() {
    AppTheme(dynamicColor = false) {
        Box(modifier = Modifier.fillMaxSize()) {
            AppActionListDialog(
                onDismissRequest = {},
                title = "故事文件操作",
                subtitle = "请选择导入或导出的目标格式",
                actions = listOf(
                    AppActionItem(
                        icon = Icons.Rounded.FileDownload,
                        title = "导入纯文本 (.txt)",
                        subtitle = "将文本内容追加到当前故事末尾",
                        onClick = {}
                    ),
                    AppActionItem(
                        icon = Icons.Rounded.FileUpload,
                        title = "导出 Markdown (.md)",
                        subtitle = "包含格式化元数据并分享",
                        onClick = {}
                    )
                )
            )
        }
    }
}
