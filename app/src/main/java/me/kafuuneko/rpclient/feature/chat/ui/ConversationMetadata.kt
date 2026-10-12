package me.kafuuneko.rpclient.feature.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R

/** 角色介绍卡的状态标签；使用实色底保证照片背景下的对比度，窄屏自动换行。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConversationMetadata(messageCount: Int, enabledLorebookCount: Int, streamEnabled: Boolean) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetadataLabel(stringResource(R.string.messages_count, messageCount))
        MetadataLabel(stringResource(R.string.world_books_enabled, enabledLorebookCount))
        MetadataLabel(stringResource(if (streamEnabled) R.string.streaming_on else R.string.streaming_off))
    }
}

/** 标签属于前景信息，独立于卡片壁纸透明度，不叠加灰色描边或半透明文字。 */
@Composable
private fun MetadataLabel(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
