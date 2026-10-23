package me.kafuuneko.rpclient.feature.dialogappearance.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.appearance.DialogAppearance
import me.kafuuneko.rpclient.ui.dialog.DialogControlAppearance
import me.kafuuneko.rpclient.ui.dialog.dialogButtonColors
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.PageSwitch
import kotlin.math.sqrt

/** 用固定示例后景演示两个模糊范围，真实系统窗口可通过预览按钮验证。 */
@Composable
internal fun DialogAppearancePreview(
    settings: DialogAppearance,
    blurAvailable: Boolean,
    modifier: Modifier
) {
    val resolved = settings.resolved(blurAvailable)
    val panelBlur = if (resolved.transparency > 0) resolved.blur * 0.4f else 0f
    val behindBlur = resolved.behindBlur * 0.4f
    val combinedBlur = sqrt(panelBlur * panelBlur + behindBlur * behindBlur)
    Box(
        modifier = modifier
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp)),
    ) {
        // 两层示例采用同一画布坐标，只在面板范围增加模糊，文字和控件最后绘制。
        PreviewScene(
            modifier = Modifier
                .fillMaxSize()
                .blur(behindBlur.dp),
        )
        PreviewScene(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val inset = 16.dp.toPx()
                    val path = Path().apply {
                        addRoundRect(
                            roundRect = RoundRect(
                                left = inset,
                                top = inset,
                                right = size.width - inset,
                                bottom = size.height - inset,
                                cornerRadius = CornerRadius(28.dp.toPx()),
                            ),
                        )
                    }
                    clipPath(path) { this@drawWithContent.drawContent() }
                }
                .blur(combinedBlur.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = resolved.dim / 100f)),
        )
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 1f - resolved.transparency / 100f),
        ) {
            DialogControlAppearance(1f - resolved.controlTransparency / 100f) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.appearance_preview),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    AppSurface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = stringResource(R.string.appearance_preview_message),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                        )
                    }
                    Button(
                        onClick = {},
                        modifier = Modifier.align(Alignment.End),
                        colors = dialogButtonColors(
                            ButtonDefaults.buttonColors(),
                            MaterialTheme.colorScheme.primary
                        ),
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            }
        }
    }
}

/** 示例只使用主题色块，不读取或复制用户页面内容与背景图片。 */
@Composable
private fun PreviewScene(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Canvas(
        modifier = modifier.background(colors.surfaceContainerLow),
        onDraw = {
            // 明暗交替的条目提供清晰边缘，以便判断局部与全屏模糊的差别。
            repeat(times = 7) { index ->
                val top = index * size.height / 7
                drawRoundRect(
                    color = if (index % 2 == 0) colors.primaryContainer else colors.secondaryContainer,
                    topLeft = Offset(size.width * 0.08f, top),
                    size = Size(size.width * 0.84f, size.height / 9),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                )
                drawCircle(
                    color = colors.primary.copy(alpha = 0.7f),
                    radius = 8.dp.toPx(),
                    center = Offset(
                        x = size.width * 0.16f,
                        y = top + size.height / 18,
                    ),
                )
            }
        },
    )
}

/** 真实预览包含表项、输入边框和开关，用于检查内部控件透明度及前景可读性。 */
@Composable
internal fun DialogPreviewControls() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppSurface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = stringResource(R.string.appearance_preview_message),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            )
        }
        PageOutlinedTextField(
            value = stringResource(R.string.appearance_preview_reply),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.appearance_preview_content)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.appearance_preview_content), Modifier.weight(1f))
            PageSwitch(checked = true, onCheckedChange = {})
        }
    }
}
