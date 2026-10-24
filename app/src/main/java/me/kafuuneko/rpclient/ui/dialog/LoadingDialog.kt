package me.kafuuneko.rpclient.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.theme.AppTheme

/**
 * 长任务使用的统一加载对话框，可选提供显式取消操作。
 * @param cancelOnBackPress 是否将返回键交给取消回调；默认只能通过取消按钮停止任务。
 */
@Composable
fun LoadingDialog(
    title: String,
    description: String,
    onCancel: (() -> Unit)? = null,
    cancelOnBackPress: Boolean = false
) {
    AppDialogWindow(
        onDismissRequest = { if (cancelOnBackPress) onCancel?.invoke() },
        properties = DialogProperties(
            dismissOnBackPress = cancelOnBackPress && onCancel != null,
            dismissOnClickOutside = false
        )
    ) {
        DialogPanelSurface(modifier = Modifier.width(310.dp)) {
            Column(
                modifier = Modifier.padding(vertical = 32.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 加载环外层双层柔光光斑容器
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(76.dp)
                        .background(
                            color = dialogControlColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                            shape = CircleShape
                        )
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                color = dialogControlColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                shape = CircleShape
                            )
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.5.dp,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        )
                    }
                }

                Column(
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
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                if (onCancel != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    FilledTonalButton(
                        colors = dialogButtonColors(ButtonDefaults.filledTonalButtonColors()),
                        onClick = onCancel,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "LoadingDialog - Light", showBackground = true)
@Composable
private fun LoadingDialogPreview() {
    AppTheme(dynamicColor = false) {
        Box(modifier = Modifier.fillMaxSize()) {
            LoadingDialog(
                title = "正在导出聊天记录",
                description = "正在格式化消息并在后台打包为 JSON 文件...",
                onCancel = {}
            )
        }
    }
}
