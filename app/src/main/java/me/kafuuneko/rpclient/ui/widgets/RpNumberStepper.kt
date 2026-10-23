package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.dialog.AppInputDialog

/**
 * 现代轻量数值步进器组件。
 *
 * 支持点击 +/- 微调步进，或轻触数字快速弹出键盘输入精确数值。
 */
@Composable
fun RpNumberStepper(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    min: Int = 0,
    max: Int = 999999,
    step: Int = 1,
    enabled: Boolean = true,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    var showDialog by remember { mutableStateOf(false) }
    val currentValue = value.toIntOrNull() ?: min

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.38f
                    ),
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }

            // 步进操作区 [-] [ 数值 ] [+]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = {
                        val next = (currentValue - step).coerceAtLeast(min)
                        onValueChange(next.toString())
                    },
                    enabled = enabled && currentValue > min,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = stringResource(R.string.decrease),
                        tint = if (enabled && currentValue > min) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = 0.3f
                        ),
                        modifier = Modifier.size(18.dp),
                    )
                }

                Surface(
                    modifier = Modifier
                        .widthIn(min = 52.dp)
                        .clickable(enabled = enabled, onClick = { showDialog = true }),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    ),
                ) {
                    Text(
                        text = value.ifBlank { "0" },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(
                            alpha = 0.38f
                        ),
                    )
                }

                IconButton(
                    onClick = {
                        val next = (currentValue + step).coerceAtMost(max)
                        onValueChange(next.toString())
                    },
                    enabled = enabled && currentValue < max,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.increase),
                        tint = if (enabled && currentValue < max) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = 0.3f
                        ),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }

    var dialogDraft by remember(showDialog) { mutableStateOf(value) }

    if (showDialog) {
        AppInputDialog(
            onDismissRequest = { showDialog = false },
            title = title,
            value = dialogDraft,
            onValueChange = { dialogDraft = it },
            singleLine = true,
            keyboardType = KeyboardType.Number,
            confirmText = stringResource(R.string.save),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                val num = dialogDraft.trim().toIntOrNull()
                if (num != null) {
                    onValueChange(num.coerceIn(min, max).toString())
                } else if (dialogDraft.isBlank()) {
                    onValueChange(min.toString())
                }
                showDialog = false
            },
        )
    }
}
