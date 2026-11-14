package me.kafuuneko.rpclient.ui.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.model.ChatWritingPhase
import me.kafuuneko.rpclient.model.ChatWritingState
import me.kafuuneko.rpclient.ui.theme.AppTheme

/** 生成期间只显示单行状态与思考入口，详情按需展开。 */
@Composable
internal fun ChatWritingPanel(
    state: ChatWritingState,
    onToggleReasoning: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 状态与思考入口保持紧凑，长详情在独立区域滚动，不挤走输入框。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Text(
                text = stringResource(state.phase.labelRes()),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
            )
            if (state.hasReasoning) {
                TextButton(onClick = onToggleReasoning) {
                    Text(stringResource(R.string.thought_process))
                    Icon(
                        imageVector = if (state.reasoningExpanded) Icons.Rounded.KeyboardArrowUp
                        else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(if (state.reasoningExpanded) R.string.hide else R.string.show),
                    )
                }
            }
        }
        ChatWritingDetails(state)
    }
}

/** 详情不自动滚动，用户展开后可以稳定阅读已经返回的思考内容。 */
@Composable
private fun ChatWritingDetails(state: ChatWritingState) {
    // 默认折叠以保持单栏高度，展开后限制详情高度，避免挤占输入框。
    if (state.reasoningExpanded && state.hasReasoning) {
        SelectionContainer {
            Text(
                text = state.reasoningDetail,
                modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp)
                    .verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun ChatWritingPhase.labelRes(): Int = when (this) {
    ChatWritingPhase.Requesting -> R.string.requesting_model
    ChatWritingPhase.Thinking -> R.string.thinking
    ChatWritingPhase.Writing -> R.string.generating
}

@Preview(widthDp = 360, showBackground = true)
@Composable
private fun ChatWritingPanelPreview() {
    AppTheme(dynamicColor = false) {
        ChatWritingPanel(
            state = ChatWritingState(
                phase = ChatWritingPhase.Thinking,
                hasReasoning = true,
            ),
            onToggleReasoning = {},
        )
    }
}
