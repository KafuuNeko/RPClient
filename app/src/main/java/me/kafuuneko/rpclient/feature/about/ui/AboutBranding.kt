package me.kafuuneko.rpclient.feature.about.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.theme.AccentAmberColor
import me.kafuuneko.rpclient.ui.theme.AccentVioletColor

/** 背景使用原生绘制，星球保持在顶部装饰区域，不引入位图或网络资源。 */
@Composable
internal fun AboutBackdrop() {
    val colors = MaterialTheme.colorScheme
    // 使用当前主题调色，浅色模式也保留轻量的星球轮廓。
    Canvas(
        modifier = Modifier
        .fillMaxSize()
        .background(colors.background),
        onDraw = {
            val radius = size.width.coerceAtMost(680.dp.toPx()) * 0.62f
            val center = Offset(size.width + radius * 0.12f, -radius * 0.46f)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.Transparent,
                    0.88f to colors.primary.copy(alpha = 0.02f),
                    0.96f to colors.primary.copy(alpha = 0.28f),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius * 1.08f,
                ),
                radius = radius * 1.08f,
                center = center,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.surface, colors.primaryContainer, colors.primary.copy(alpha = 0.4f)),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
            drawCircle(
                color = colors.primary.copy(alpha = 0.35f),
                radius = radius,
                center = center,
                style = Stroke(1.dp.toPx()),
            )
            // 稀疏且固定的星点避免重组时闪烁，也不干扰正文阅读。
            listOf(0.12f to 154f, 0.79f to 93f, 0.2f to 268f, 0.85f to 225f).forEach { (x, y) ->
                val point = Offset(size.width * x, y.dp.toPx())
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(colors.primary.copy(alpha = 0.3f), Color.Transparent),
                        center = point,
                        radius = 5.dp.toPx(),
                    ),
                    radius = 5.dp.toPx(),
                    center = point,
                )
                drawCircle(color = colors.primary.copy(alpha = 0.55f), radius = 0.7.dp.toPx(), center = point)
            }
        },
    )
}

/** 透明顶栏让星球延伸至状态栏，并使用标准的返回触摸区域。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutTopBar(onBack: () -> Unit) {
    // 保留 Material 顶栏的系统安全区及无障碍按钮尺寸。
    TopAppBar(
        title = { Text(text = stringResource(R.string.about), fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painter = painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}

/** 居中展示应用标识，开发版徽标由安装包状态决定。 */
@Composable
internal fun AboutHeroHeader(isDevelopmentBuild: Boolean) {
    // 品牌名资源独立于启动器名称，避免开发包重复出现 Dev 标记。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AboutLogo()
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.about_brand_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            if (isDevelopmentBuild) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary) {
                    Text(
                        text = stringResource(R.string.about_dev_badge),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.about_app_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.about_brand_keywords),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AboutLogo() {
    // 保留现有应用标识，以外层柔光和描边呼应设计稿的玻璃底座。
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(96.dp)
            .background(brush = Brush.radialGradient(listOf(primary.copy(alpha = 0.25f), Color.Transparent))),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(23.dp),
            color = primary.copy(alpha = 0.1f),
            border = BorderStroke(width = 1.dp, color = primary.copy(alpha = 0.25f)),
        ) {
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier
                    .padding(7.dp)
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp)),
            )
        }
    }
}

/** 渐变评分卡在宽屏横向展开，手机上将说明与行动分行以保留可读性。 */
@Composable
internal fun AboutRatingCard(onRateApp: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // 金色到蓝色的边框呼应评分与品牌；内部颜色继续跟随主题。
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(AccentAmberColor.copy(alpha = 0.6f), colors.primary.copy(alpha = 0.45f)),
            ),
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(AccentAmberColor.copy(alpha = 0.1f), colors.primary.copy(alpha = 0.12f)),
                    ),
                )
                .padding(16.dp),
        ) {
            val wide = maxWidth / LocalDensity.current.fontScale >= 520.dp
            if (wide) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AboutStars()
                    AboutRatingCopy(Modifier.weight(1f))
                    AboutRateButton(onRateApp)
                }
            } else if (maxWidth / LocalDensity.current.fontScale >= 300.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AboutStars()
                        AboutRatingCopy()
                    }
                    AboutRateButton(onRateApp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AboutStars()
                    AboutRatingCopy()
                    AboutRateButton(onRateApp, Modifier.align(Alignment.End))
                }
            }
        }
    }
}

@Composable
private fun AboutStars() {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(times = 5) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = AccentAmberColor,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun AboutRatingCopy(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.rate_card_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.rate_card_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutRateButton(onRateApp: () -> Unit, modifier: Modifier = Modifier) {
    // 背景独立绘制渐变，仍使用标准 Button 提供触摸尺寸、焦点和点击反馈。
    Button(
        onClick = onRateApp,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(brush = Brush.horizontalGradient(listOf(AccentVioletColor, MaterialTheme.colorScheme.primary))),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
    ) {
        Text(text = stringResource(R.string.about_rate_action), fontWeight = FontWeight.SemiBold)
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            modifier = Modifier
                .padding(start = 6.dp)
                .size(18.dp),
        )
    }
}
