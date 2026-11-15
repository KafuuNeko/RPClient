package me.kafuuneko.rpclient.feature.llmprovideredit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditImageQualityState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditUiIntent
import me.kafuuneko.rpclient.libs.llm.model.ImageQuality

/** 渲染通用画质选项，只消费状态和发布用户意图，不判断服务商协议。 */
@Composable
internal fun ImageQualityControl(
    state: LLMProviderEditImageQualityState,
    emit: LLMProviderEditUiIntent.() -> Unit,
) {
    if (state.options.isEmpty()) return
    // 可选范围已由领域策略收敛；界面只负责布局和本地化文案。
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.image_quality),
            style = MaterialTheme.typography.titleSmall
        )
        EnumChipRow(
            values = state.options,
            selected = state.selected,
            label = { qualityLabel(it) },
            onSelect = { LLMProviderEditUiIntent.SelectImageQuality(it).emit() },
        )
        Text(
            text = stringResource(R.string.image_quality_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun qualityLabel(quality: ImageQuality): String = stringResource(
    when (quality) {
        ImageQuality.Auto -> R.string.local_token_estimator_automatic
        ImageQuality.Low -> R.string.image_quality_low
        ImageQuality.Medium -> R.string.image_quality_medium
        ImageQuality.High -> R.string.image_quality_high
        ImageQuality.UltraHigh -> R.string.image_quality_ultra
        ImageQuality.Original -> R.string.image_quality_original
    }
)
