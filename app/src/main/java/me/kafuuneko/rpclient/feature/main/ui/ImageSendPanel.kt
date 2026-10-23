package me.kafuuneko.rpclient.feature.main.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.main.model.ImageSendField
import me.kafuuneko.rpclient.feature.main.presentation.MainImageSendState
import me.kafuuneko.rpclient.feature.main.presentation.MainUiIntent
import me.kafuuneko.rpclient.libs.media.ImageSendMode
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.RpSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.generalContentBackground
import me.kafuuneko.rpclient.ui.widgets.PageButton as Button
import me.kafuuneko.rpclient.ui.widgets.PageFilterChip as FilterChip
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField

/**
 * 渲染图片发送高级设置；业务草稿、保存和校验全部交给 ViewModel。
 * @param state 当前模式及自定义输入草稿。
 * @param emit 用户意图接收入口。
 */
@Composable
internal fun ImageSendPanel(state: MainImageSendState, emit: MainUiIntent.() -> Unit) {
    RpSettingsGroup {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.image_send_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.image_send_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 窄屏与较长译文允许换行，避免模式按钮挤压参数输入。
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageSendMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.mode == mode,
                        onClick = { MainUiIntent.SelectImageSendMode(mode).emit() },
                        label = { Text(stringResource(mode.titleRes())) },
                    )
                }
            }
            Text(
                text = stringResource(state.mode.descriptionRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.mode == ImageSendMode.Custom) ImageSendLimits(state, emit)
        }
    }
}

/** 自定义字段纵向排列，错误留在面板内并保留输入以便修正。 */
@Composable
private fun ImageSendLimits(state: MainImageSendState, emit: MainUiIntent.() -> Unit) {
    // 参数只在点击保存后生效，输入过程不改变后台请求。
    ImageLimitField(
        value = state.maxFileKiB,
        label = R.string.image_send_max_file,
        onChange = { MainUiIntent.ChangeImageSendLimit(ImageSendField.FileKiB, it).emit() },
    )
    ImageLimitField(
        value = state.maxWidth,
        label = R.string.image_send_max_width,
        onChange = { MainUiIntent.ChangeImageSendLimit(ImageSendField.Width, it).emit() },
    )
    ImageLimitField(
        value = state.maxHeight,
        label = R.string.image_send_max_height,
        onChange = { MainUiIntent.ChangeImageSendLimit(ImageSendField.Height, it).emit() },
    )
    state.errorResId?.let { Text(text = stringResource(it), color = MaterialTheme.colorScheme.error) }
    Button(onClick = { MainUiIntent.SaveImageSendLimits.emit() }, enabled = state.hasChanges) {
        Text(stringResource(R.string.save))
    }
}

/** 数字键盘仅提供输入便利，合法性由 ViewModel 校验。 */
@Composable
private fun ImageLimitField(value: String, @StringRes label: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .generalContentBackground(shape = OutlinedTextFieldDefaults.shape, hasLabel = true),
    )
}

/** @return 发送模式对应的本地化名称。 */
@StringRes
private fun ImageSendMode.titleRes(): Int = when (this) {
    ImageSendMode.Auto -> R.string.image_send_auto
    ImageSendMode.Original -> R.string.image_send_original
    ImageSendMode.Custom -> R.string.image_send_custom
}

/** @return 当前模式的限制与生效方式说明。 */
@StringRes
private fun ImageSendMode.descriptionRes(): Int = when (this) {
    ImageSendMode.Auto -> R.string.image_send_auto_description
    ImageSendMode.Original -> R.string.image_send_original_description
    ImageSendMode.Custom -> R.string.image_send_custom_description
}

@Preview(widthDp = 360, locale = "zh")
@Composable
private fun ImageSendPanelPreview() {
    AppTheme(dynamicColor = false) { ImageSendPanel(state = MainImageSendState(mode = ImageSendMode.Custom), emit = {}) }
}
