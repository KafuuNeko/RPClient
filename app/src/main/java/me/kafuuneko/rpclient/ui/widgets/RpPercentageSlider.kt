package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** 使用与 Token 估算预留一致的滑块交互展示百分比设置。 */
@Composable
fun RpPercentageSlider(
    title: String,
    value: Int,
    helper: String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: IntRange = MIN_PERCENT..MAX_PERCENT,
) {
    val minimum = valueRange.first.coerceIn(MIN_PERCENT, MAX_PERCENT)
    val maximum = valueRange.last.coerceIn(minimum, MAX_PERCENT)
    val percent = value.coerceIn(minimum, maximum)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        // 轨道跟随内容区底板，手柄保持清晰可拖动。
        Slider(
            colors = pageSliderColors(),
            value = percent.toFloat(),
            onValueChange = { float -> onValueChange(float.roundToInt()) },
            enabled = enabled,
            valueRange = minimum.toFloat()..maximum.toFloat(),
        )
        Text(
            text = helper,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val MIN_PERCENT = 0
private const val MAX_PERCENT = 100
