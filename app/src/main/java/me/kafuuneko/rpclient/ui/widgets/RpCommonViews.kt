package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 页面主标题，可选显示辅助说明。 */
@Composable
fun RpPageTitle(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Black
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f)
        )
    }
}

/** 内容区段标题，可选在右侧放置操作按钮。 */
@Composable
fun RpSectionHeader(
    title: String,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (action != null) {
            TextButton(onClick = onAction) {
                Text(action)
            }
        }
    }
}

/** 项目统一的文字占位头像。 */
@Composable
fun RpAvatar(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape
) {
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(shape)
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
    }
}

/** 用于列表项或卡片的圆形图标背景。 */
@Composable
fun RpIconBubble(
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(38.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** 可自动换行的标签集合。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RpTagRow(
    tags: List<String>,
    modifier: Modifier = Modifier,
    maxCount: Int = tags.size
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tags.take(maxCount).forEach { tag ->
            RpTagPill(tag)
        }
    }
}

/** 单个强调标签。 */
@Composable
fun RpTagPill(text: String) {
    Surface(
        modifier = Modifier.widthIn(max = 240.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 可自动换行的弱化元信息集合。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RpMetaRow(items: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { item ->
            RpMetaPill(item)
        }
    }
}

/** 单个弱化元信息标签。 */
@Composable
fun RpMetaPill(text: String) {
    Surface(
        modifier = Modifier.widthIn(max = 240.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 带图标、标题和可选操作区的统一信息卡片。 */
@Composable
fun RpInfoCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RpIconBubble(icon)
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                )
            }
            trailing?.invoke()
        }
    }
}

/** 使用项目统一圆角与背景色的内容面板。 */
@Composable
fun RpPanel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

/** 项目统一的小号垂直间距。 */
@Composable
fun RpSpacerSmall() {
    Spacer(modifier = Modifier.height(8.dp))
}
