package me.kafuuneko.rpclient.ui.dialog

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.theme.AppTheme

/**
 * 长任务使用的轻量通透加载对话框。
 *
 * @param title 对话框主标题，用于概括当前正在执行的任务。
 * @param description 详细状态描述，解释当前正在处理的具体步骤。
 * @param modifier 对话框面板修饰符。
 * @param onCancel 可选的取消回调；为 null 时表示不可中断任务，不展示取消按钮。
 * @param cancelOnBackPress 是否将系统返回键映射为取消操作；默认只能通过显式取消按钮停止。
 */
@Composable
fun LoadingDialog(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
    cancelOnBackPress: Boolean = false,
) {
    AppDialogWindow(
        onDismissRequest = { if (cancelOnBackPress) onCancel?.invoke() },
        properties = DialogProperties(
            dismissOnBackPress = cancelOnBackPress && onCancel != null,
            dismissOnClickOutside = false,
        ),
    ) {
        DialogPanelSurface(
            modifier = modifier.widthIn(min = 280.dp, max = 310.dp),
        ) {
            Column(
                modifier = Modifier.padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = 28.dp,
                    bottom = if (onCancel != null) 20.dp else 28.dp,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // 空灵双轨极光微光指示器
                LuminousOrbitIndicator()

                // 核心任务标题与步骤描述
                LoadingContentText(
                    title = title,
                    description = description,
                )

                // 弱化视觉对抗的次级药丸取消动作
                if (onCancel != null) {
                    LoadingCancelButton(onCancel = onCancel)
                }
            }
        }
    }
}

/**
 * 具有空灵流光拖尾与双轨差速纵深感的加载指示动效。
 *
 * 外轨使用平滑渐变拖尾营造流光彗尾感，内轨以更低速逆向缓动形成精密差速层次，背景辅以无硬边缘的径向星云光雾呼吸，彻底消除色块堆叠带来的厚重肉感。
 */
@Composable
private fun LuminousOrbitIndicator(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "LuminousOrbitTransition")

    // 外圈流光彗尾顺时针无级旋转
    val outerRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "OuterRotation",
    )

    // 内圈辅助光轨逆向优雅缓动
    val innerRotation by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "InnerRotation",
    )

    // 背景星云微光呼吸明暗波动
    val ambientGlowAlpha by transition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "AmbientGlowAlpha",
    )

    Canvas(
        modifier = modifier.size(72.dp),
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = 24.dp.toPx()
        val innerRadius = 15.5.dp.toPx()
        val outerStrokeWidth = 2.dp.toPx()
        val innerStrokeWidth = 1.5.dp.toPx()

        // 柔和弥散星云光雾，完全去除硬边缘
        val maxGlowRadius = size.minDimension / 2f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = ambientGlowAlpha),
                    accentColor.copy(alpha = ambientGlowAlpha * 0.35f),
                    Color.Transparent,
                ),
                center = center,
                radius = maxGlowRadius,
            ),
            radius = maxGlowRadius,
            center = center,
        )

        // 外层微弱基准底轨
        drawCircle(
            color = accentColor.copy(alpha = 0.08f),
            radius = outerRadius,
            center = center,
            style = Stroke(width = outerStrokeWidth),
        )

        // 外层流光彗尾弧线，头部为完全高亮圆头，尾部顺滑羽化至透明
        rotate(degrees = outerRotation, pivot = center) {
            val sweepBrush = Brush.sweepGradient(
                0.0f to Color.Transparent,
                0.35f to accentColor.copy(alpha = 0.08f),
                0.55f to accentColor.copy(alpha = 0.40f),
                0.75f to accentColor,
                1.0f to Color.Transparent,
                center = center,
            )
            drawArc(
                brush = sweepBrush,
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                size = Size(outerRadius * 2f, outerRadius * 2f),
                style = Stroke(width = outerStrokeWidth, cap = StrokeCap.Round),
            )
        }

        // 内层反向差速光弧，带来精巧的机械与星轨层次感
        rotate(degrees = innerRotation, pivot = center) {
            drawArc(
                color = accentColor.copy(alpha = 0.30f),
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                size = Size(innerRadius * 2f, innerRadius * 2f),
                style = Stroke(width = innerStrokeWidth, cap = StrokeCap.Round),
            )
        }

        // 中心锚定微星芒，伴随环境光轻微呼吸
        val coreAlpha = (0.55f + ambientGlowAlpha * 2.5f).coerceIn(0f, 1f)
        drawCircle(
            color = accentColor.copy(alpha = coreAlpha),
            radius = 1.8.dp.toPx(),
            center = center,
        )
    }
}

/**
 * 居中排版的状态标题与说明文本。
 */
@Composable
private fun LoadingContentText(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (description.isNotBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )
        }
    }
}

/**
 * 轻量级次级药丸取消按钮。
 *
 * 采用低饱和半透明底色与细腻微描边，弱化视觉对抗，保持主要焦点留在加载状态上。
 */
@Composable
private fun LoadingCancelButton(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onCancel,
        shape = CircleShape,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f),
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = dialogControlColor(
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
            ),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp),
    ) {
        Text(
            text = stringResource(R.string.cancel),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Preview(name = "LoadingDialog - Light with Cancel", showBackground = true)
@Composable
private fun LoadingDialogLightPreview() {
    AppTheme(dynamicColor = false) {
        Box(modifier = Modifier.fillMaxSize()) {
            LoadingDialog(
                title = "正在导出聊天记录",
                description = "正在格式化消息并在后台打包为 JSON 文件...",
                onCancel = {},
            )
        }
    }
}

@Preview(name = "LoadingDialog - Dark Non-cancellable", showBackground = true)
@Composable
private fun LoadingDialogDarkPreview() {
    AppTheme(darkTheme = true, dynamicColor = false) {
        Box(modifier = Modifier.fillMaxSize()) {
            LoadingDialog(
                title = "正在重建搜索索引",
                description = "正在解析并清洗历史消息，该过程无法中断...",
                onCancel = null,
            )
        }
    }
}
