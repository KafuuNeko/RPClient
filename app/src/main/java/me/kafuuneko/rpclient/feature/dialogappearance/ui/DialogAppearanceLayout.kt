package me.kafuuneko.rpclient.feature.dialogappearance.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.dialogappearance.presentation.DialogAdjustment
import me.kafuuneko.rpclient.feature.dialogappearance.presentation.DialogAppearanceDialog
import me.kafuuneko.rpclient.feature.dialogappearance.presentation.DialogAppearanceUiIntent
import me.kafuuneko.rpclient.feature.dialogappearance.presentation.DialogAppearanceUiState
import me.kafuuneko.rpclient.ui.dialog.AppActionItem
import me.kafuuneko.rpclient.ui.dialog.AppActionListDialog
import me.kafuuneko.rpclient.ui.dialog.AppDialogScaffold
import me.kafuuneko.rpclient.ui.dialog.rememberDialogBlurAvailable
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.LocalDialogAppearance
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import kotlin.math.roundToInt

/** 独立外观编辑页，预览固定在竖屏顶部或横屏左侧，参数列表单独滚动。 */
@Composable
fun DialogAppearanceLayout(state: DialogAppearanceUiState, emit: (DialogAppearanceUiIntent) -> Unit) {
    if (state is DialogAppearanceUiState.Finished) {
        DialogAppearanceLayout(state = state.previous, emit = {})
        return
    }
    state as DialogAppearanceUiState.Normal
    BackHandler(onBack = { emit(DialogAppearanceUiIntent.Back) })
    val blurAvailable = rememberDialogBlurAvailable()
    // 真实示例和静态预览只覆盖本页草稿，保存确认使用已经提交的全局外观。
    Scaffold(
        topBar = {
        AppTopBar(
            title = stringResource(R.string.dialog_appearance_title),
            onBack = { emit(DialogAppearanceUiIntent.Back) },
            actions = { TextButton(onClick = { emit(DialogAppearanceUiIntent.Save) }) { Text(stringResource(R.string.save)) } },
        )
    },
        content = { padding ->
            val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            if (landscape) {
                Row(
                    modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Box(
                        modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    ) { DialogAppearancePreview(
                        settings = state.settings,
                        blurAvailable = blurAvailable,
                        modifier = Modifier.fillMaxSize(),
                    ) }
                    DialogAdjustments(
                        state = state,
                        blurAvailable = blurAvailable,
                        modifier = Modifier.weight(1f),
                        emit = emit,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                ) {
                    DialogAppearancePreview(
                        settings = state.settings,
                        blurAvailable = blurAvailable,
                        modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    )
                    DialogAdjustments(
                        state = state,
                        blurAvailable = blurAvailable,
                        modifier = Modifier.weight(1f),
                        emit = emit,
                    )
                }
            }
        },
    )
    DialogEditorOverlay(state, emit)
}

/** 参数显示与单位集中映射，面板不透明时禁用无效的局部模糊但保留其草稿值。 */
@Composable
private fun DialogAdjustments(
    state: DialogAppearanceUiState.Normal,
    blurAvailable: Boolean,
    modifier: Modifier,
    emit: (DialogAppearanceUiIntent) -> Unit,
) {
    val settings = state.settings
    Column(
        modifier = modifier
        .verticalScroll(rememberScrollState())
        .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 模糊不可用时明确说明实际降级效果，而不是让滑块悄悄失效。
        if (!blurAvailable) Text(
            text = stringResource(R.string.dialog_appearance_blur_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DialogSlider(
            title = R.string.dialog_appearance_transparency,
            value = settings.transparency,
            maximum = 100,
            field = DialogAdjustment.Transparency,
            emit = emit,
        )
        DialogSlider(
            title = R.string.dialog_appearance_blur,
            value = settings.blur,
            maximum = 100,
            field = DialogAdjustment.Blur,
            emit = emit,
            enabled = settings.transparency > 0,
        )
        DialogSlider(
            title = R.string.dialog_appearance_controls,
            value = settings.controlTransparency,
            maximum = 90,
            field = DialogAdjustment.ControlTransparency,
            emit = emit,
        )
        DialogSlider(
            title = R.string.dialog_appearance_dim,
            value = settings.dim,
            maximum = 80,
            field = DialogAdjustment.Dim,
            emit = emit,
        )
        DialogSlider(
            title = R.string.dialog_appearance_behind_blur,
            value = settings.behindBlur,
            maximum = 100,
            field = DialogAdjustment.BehindBlur,
            emit = emit,
        )
        Button(onClick = { emit(DialogAppearanceUiIntent.Preview) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.dialog_appearance_open_preview))
        }
        TextButton(onClick = { emit(DialogAppearanceUiIntent.Reset) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.prompt_editor_restore_default))
        }
    }
}

@Composable
private fun DialogSlider(
    @StringRes title: Int,
    value: Int,
    maximum: Int,
    field: DialogAdjustment,
    emit: (DialogAppearanceUiIntent) -> Unit,
    enabled: Boolean = true,
) {
    val label = stringResource(title)
    val blur = field == DialogAdjustment.Blur || field == DialogAdjustment.BehindBlur
    Column {
        Row {
            Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text(if (blur) value.toString() else stringResource(R.string.appearance_percent, value))
        }
        // 对外只有强度和百分比，物理像素换算留在窗口渲染层。
        Slider(
            value = value.toFloat(),
            onValueChange = { float -> emit(
                DialogAppearanceUiIntent.Adjust(
                    field = field,
                    value = float.roundToInt(),
                ),
            ) },
            valueRange = 0f..maximum.toFloat(),
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

/** 未保存确认不使用未提交的极端透明草稿，始终保留清晰的保存和放弃入口。 */
@Composable
private fun DialogEditorOverlay(state: DialogAppearanceUiState.Normal, emit: (DialogAppearanceUiIntent) -> Unit) {
    when (state.dialog) {
        DialogAppearanceDialog.None -> Unit
        DialogAppearanceDialog.Preview -> CompositionLocalProvider(LocalDialogAppearance provides state.settings) {
            AppDialogScaffold(
                onDismissRequest = { emit(DialogAppearanceUiIntent.DismissDialog) },
                title = stringResource(R.string.appearance_preview),
                compactHeader = true,
                scrollableContent = true,
                onConfirm = { emit(DialogAppearanceUiIntent.DismissDialog) },
            ) {
                DialogPreviewControls()
            }
        }
        // 与页面背景编辑器复用相同的保存、放弃、取消样式。
        DialogAppearanceDialog.UnsavedChanges -> AppActionListDialog(
            onDismissRequest = { emit(DialogAppearanceUiIntent.DismissDialog) },
            title = stringResource(R.string.appearance_save_changes_title),
            subtitle = stringResource(R.string.appearance_save_changes_message),
            badgeIcon = Icons.Rounded.Save,
            dismissText = stringResource(R.string.cancel),
            actions = listOf(
                AppActionItem(
                    icon = Icons.Rounded.Save,
                    title = stringResource(R.string.save),
                    onClick = { emit(DialogAppearanceUiIntent.Save) },
                ),
                AppActionItem(
                    icon = Icons.Rounded.DeleteOutline,
                    title = stringResource(R.string.discard_changes),
                    isDestructive = true,
                    onClick = { emit(DialogAppearanceUiIntent.Discard) },
                ),
            ),
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Preview(widthDp = 844, heightDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DialogAppearanceLayoutPreview() {
    AppTheme(dynamicColor = false) { DialogAppearanceLayout(state = DialogAppearanceUiState.Normal(), emit = {}) }
}
