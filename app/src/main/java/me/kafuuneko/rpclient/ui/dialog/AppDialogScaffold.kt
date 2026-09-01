package me.kafuuneko.rpclient.ui.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.widgets.draggableScrollIndicator
import me.kafuuneko.rpclient.ui.theme.AppTheme

/** Dialog 顶部状态徽标的色彩风格。 */
enum class DialogBadgeTone {
    Primary,
    Danger,
    Warning,
    Secondary,
    Neutral
}

/**
 * 现代化 Dialog 容器脚手架。
 *
 * 统一处理 28.dp 圆角、柔和微边框、分层 Elevation、Hero 状态徽标与自适应多语言操作按钮排版。
 */
@Composable
fun AppDialogScaffold(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    badgeIcon: ImageVector? = null,
    badgeTone: DialogBadgeTone = DialogBadgeTone.Primary,
    subtitle: String? = null,
    compactHeader: Boolean = false,
    confirmText: String = stringResource(R.string.confirm),
    dismissText: String? = stringResource(R.string.cancel),
    confirmEnabled: Boolean = true,
    isConfirmLoading: Boolean = false,
    confirmIsDestructive: Boolean = false,
    stackButtons: Boolean? = null,
    onConfirm: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = onDismissRequest,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = true),
    scrollableContent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AppDialogHeader(
                    title = title,
                    subtitle = subtitle,
                    badgeIcon = badgeIcon,
                    badgeTone = badgeTone,
                    compact = compactHeader
                )
                AppDialogContent(scrollableContent, content)
                if (onConfirm != null || onDismiss != null) {
                    AppDialogActions(
                        confirmText = confirmText,
                        dismissText = dismissText,
                        confirmEnabled = confirmEnabled,
                        isConfirmLoading = isConfirmLoading,
                        confirmIsDestructive = confirmIsDestructive,
                        stacked = stackButtons ?: shouldStackActionButtons(confirmText, dismissText),
                        onConfirm = onConfirm,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun AppDialogHeader(
    title: String,
    subtitle: String?,
    badgeIcon: ImageVector?,
    badgeTone: DialogBadgeTone,
    compact: Boolean
) {
    if (compact) {
        CompactDialogHeader(title, subtitle, badgeIcon, badgeTone)
    } else {
        HeroDialogHeader(title, subtitle, badgeIcon, badgeTone)
    }
}

@Composable
private fun CompactDialogHeader(
    title: String,
    subtitle: String?,
    badgeIcon: ImageVector?,
    badgeTone: DialogBadgeTone
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (badgeIcon != null) {
            val (containerColor, contentColor) = resolveBadgeColors(badgeTone)
            Box(
                modifier = Modifier.size(42.dp).background(containerColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(badgeIcon, null, Modifier.size(22.dp), contentColor)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HeroDialogHeader(
    title: String,
    subtitle: String?,
    badgeIcon: ImageVector?,
    badgeTone: DialogBadgeTone
) {
    if (badgeIcon != null) {
        val (containerColor, contentColor) = resolveBadgeColors(badgeTone)
        Box(
            modifier = Modifier
                .size(58.dp)
                .background(containerColor.copy(alpha = 0.35f), CircleShape)
                .padding(5.dp)
                .background(containerColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(badgeIcon, null, Modifier.size(26.dp), contentColor)
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AppDialogContent(
    scrollable: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    val modifier = if (scrollable) {
        Modifier
            .fillMaxWidth()
            .heightIn(max = 380.dp)
            .draggableScrollIndicator(scrollState)
            .verticalScroll(scrollState)
    } else {
        Modifier.fillMaxWidth()
    }
    Column(modifier = modifier, content = content)
}

@Composable
private fun AppDialogActions(
    confirmText: String,
    dismissText: String?,
    confirmEnabled: Boolean,
    isConfirmLoading: Boolean,
    confirmIsDestructive: Boolean,
    stacked: Boolean,
    onConfirm: (() -> Unit)?,
    onDismiss: (() -> Unit)?
) {
    val haptic = LocalHapticFeedback.current
    val confirmAction = onConfirm?.let {
        {
            if (confirmIsDestructive) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            it()
        }
    }
    if (stacked) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            confirmAction?.let {
                DialogConfirmButton(
                    text = confirmText,
                    enabled = confirmEnabled,
                    loading = isConfirmLoading,
                    destructive = confirmIsDestructive,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = it
                )
            }
            if (dismissText != null && onDismiss != null) {
                DialogDismissButton(dismissText, Modifier.fillMaxWidth(), onDismiss)
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dismissText != null && onDismiss != null) {
                DialogDismissButton(dismissText, Modifier.weight(1f), onDismiss)
            }
            confirmAction?.let {
                DialogConfirmButton(
                    text = confirmText,
                    enabled = confirmEnabled,
                    loading = isConfirmLoading,
                    destructive = confirmIsDestructive,
                    modifier = Modifier.weight(1f),
                    onClick = it
                )
            }
        }
    }
}

@Composable
private fun DialogConfirmButton(
    text: String,
    enabled: Boolean,
    loading: Boolean,
    destructive: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val colors = if (destructive) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        )
    } else {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    }
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        colors = colors,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.5.dp,
                color = if (destructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DialogDismissButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 估算文本在移动端按钮中的视觉宽度（CJK 字符按 2.0 计算，西文字符按 1.0 计算）。 */
private fun String.estimatedVisualWidth(): Float {
    return fold(0f) { acc, char ->
        if (char.code in 0x4E00..0x9FFF || char.code in 0x3400..0x4DBF || char.code in 0x3000..0x303F || char.code in 0xFF00..0xFFEF) {
            acc + 2.0f
        } else {
            acc + 1.0f
        }
    }
}

/** 智能判定是否需要将按钮转为全宽纵向堆叠模式。 */
private fun shouldStackActionButtons(
    confirmText: String?,
    dismissText: String?
): Boolean {
    if (confirmText == null || dismissText == null) return false
    val confirmWidth = confirmText.estimatedVisualWidth()
    val dismissWidth = dismissText.estimatedVisualWidth()
    return confirmWidth > 9.0f || dismissWidth > 9.0f || (confirmWidth + dismissWidth) > 17.0f
}

@Composable
private fun resolveBadgeColors(tone: DialogBadgeTone): Pair<Color, Color> {
    return when (tone) {
        DialogBadgeTone.Primary -> Pair(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.primary
        )
        DialogBadgeTone.Danger -> Pair(
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.error
        )
        DialogBadgeTone.Warning -> Pair(
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.tertiary
        )
        DialogBadgeTone.Secondary -> Pair(
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.secondary
        )
        DialogBadgeTone.Neutral -> Pair(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(name = "AppDialogScaffold - Primary Light", showBackground = true)
@Composable
private fun AppDialogScaffoldPrimaryPreview() {
    AppTheme(dynamicColor = false) {
        AppDialogScaffold(
            onDismissRequest = {},
            badgeIcon = Icons.Rounded.Info,
            badgeTone = DialogBadgeTone.Primary,
            title = "保存当前修改",
            subtitle = "所有变更将立即生效并同步至本地数据库。",
            confirmText = "保存",
            dismissText = "取消",
            onConfirm = {}
        )
    }
}

@Preview(name = "AppDialogScaffold - Danger Dark", showBackground = true)
@Composable
private fun AppDialogScaffoldDangerPreview() {
    AppTheme(darkTheme = true, dynamicColor = false) {
        Box(modifier = Modifier.fillMaxSize()) {
            AppDialogScaffold(
                onDismissRequest = {},
                badgeIcon = Icons.Rounded.DeleteOutline,
                badgeTone = DialogBadgeTone.Danger,
                title = "彻底删除会话",
                subtitle = "删除后将无法恢复，包含的所有聊天消息将被清空。",
                confirmText = "确认删除",
                dismissText = "取消",
                confirmIsDestructive = true,
                onConfirm = {}
            )
        }
    }
}

@Preview(name = "AppDialogScaffold - Long Text Stacked", showBackground = true)
@Composable
private fun AppDialogScaffoldStackedPreview() {
    AppTheme(darkTheme = true, dynamicColor = false) {
        Box(modifier = Modifier.fillMaxSize()) {
            AppDialogScaffold(
                onDismissRequest = {},
                badgeTone = DialogBadgeTone.Warning,
                title = "世界书预算可能过低",
                subtitle = "该世界书的固定预算为 25 Token，可能不足。是否改为跟随全局预算？",
                confirmText = "跟随全局",
                dismissText = "保留导入预算",
                onConfirm = {}
            )
        }
    }
}
