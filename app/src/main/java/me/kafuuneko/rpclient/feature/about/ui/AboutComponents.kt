package me.kafuuneko.rpclient.feature.about.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** 以主题表面色和微弱渐变承载关于页分组，避免影响其他设置页面。 */
@Composable
internal fun AboutSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    // 分组标题与容器使用同一间距节奏，边框在浅色和深色主题下均可辨识。
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 2.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.035f),
                                Color.Transparent
                            ),
                        ),
                    ),
                content = content,
            )
        }
    }
}

/** 分割线与卡片内边距对齐，形成连续分组而非独立按钮。 */
@Composable
internal fun AboutDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** 可点击信息行；窄屏或大字体时操作尾标下移，为完整正文让出空间。 */
@Composable
internal fun AboutActionRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    action: String,
    onClick: () -> Unit,
    actionIcon: ImageVector? = null,
    pillAction: Boolean = false,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
    ) {
        // 以有效文字宽度判断布局，避免翻译和系统字号放大后互相挤压。
        val stacked = maxWidth / LocalDensity.current.fontScale < 300.dp
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AboutIconBubble(icon, accent)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (stacked) AboutActionLabel(text = action, icon = actionIcon, pill = pillAction)
            }
            if (!stacked) AboutActionLabel(text = action, icon = actionIcon, pill = pillAction)
        }
    }
}

@Composable
private fun AboutActionLabel(text: String, icon: ImageVector?, pill: Boolean) {
    // 整行是唯一点击与无障碍节点；胶囊仅作操作提示，避免嵌套点击。
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (pill) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        border = if (pill) BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        ) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (pill) 10.dp else 0.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pill && icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = if (pill) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
            )
            if (!pill) {
                Icon(
                    imageVector = icon ?: Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** 版本与构建号作为只读信息显示，大字体下改为上下排布。 */
@Composable
internal fun AboutInfoRow(icon: ImageVector, label: String, value: String) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        // 长版本号允许换行，不截断用于反馈问题的安装包标识。
        val stacked = maxWidth / LocalDensity.current.fontScale < 300.dp
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AboutIconBubble(
                icon = icon,
                accent = MaterialTheme.colorScheme.secondary,
                compact = true
            )
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = label, style = MaterialTheme.typography.bodySmall)
                    Text(text = value, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = value,
                    modifier = Modifier.weight(1.5f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** 彩色图标底座复用主题表面，保持装饰与文字的对比度独立。 */
@Composable
internal fun AboutIconBubble(icon: ImageVector, accent: Color, compact: Boolean = false) {
    // 渐变只作用于图标底座，图标保持实色以避免小尺寸模糊。
    Surface(
        shape = RoundedCornerShape(if (compact) 10.dp else 12.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(width = 0.5.dp, color = accent.copy(alpha = 0.25f)),
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 30.dp else 42.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(if (compact) 19.dp else 24.dp),
            )
        }
    }
}
