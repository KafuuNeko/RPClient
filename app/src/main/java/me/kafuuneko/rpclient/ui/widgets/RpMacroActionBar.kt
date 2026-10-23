package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R

/** 提示词通用宏变量列表。 */
val DEFAULT_PROMPT_MACROS = listOf("{{char}}", "{{user}}", "{{original}}", "{{lastMessage}}")

/** 示例对话专用宏与分隔符列表。 */
val DIALOGUE_EXAMPLE_MACROS = listOf("<START>", "{{char}}", "{{user}}")

/**
 * 现代化 Prompt / 文本宏变量快捷插入与全屏工具栏。
 *
 * 紧贴于多行 Prompt 输入框上方或下方，提供一键插入 {{char}} / {{user}} / <START> 等能力。
 */
@Composable
fun RpMacroActionBar(
    onInsertMacro: (String) -> Unit,
    modifier: Modifier = Modifier,
    onFullscreenClick: (() -> Unit)? = null,
    macros: List<String> = DEFAULT_PROMPT_MACROS,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            macros.forEach { macro ->
                Surface(
                    onClick = { onInsertMacro(macro) },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    ),
                ) {
                    Text(
                        text = macro,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (onFullscreenClick != null) {
            IconButton(
                onClick = onFullscreenClick,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Fullscreen,
                    contentDescription = stringResource(R.string.fullscreen_editor),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
