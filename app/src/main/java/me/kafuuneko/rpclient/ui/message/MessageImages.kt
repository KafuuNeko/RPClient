package me.kafuuneko.rpclient.ui.message

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.common.media.MessageImageAction
import me.kafuuneko.rpclient.feature.common.media.MessageImageState

/** 图片条只渲染状态并发出用户行为；缩略图由 ViewModel 按可见项加载。 */
@Composable
fun MessageImageStrip(
    ids: List<String>, state: MessageImageState, editable: Boolean = false,
    editing: Boolean = false, enabled: Boolean = true, emit: (MessageImageAction) -> Unit
) {
    if (ids.isEmpty() && !editable) return
    Column {
        if (editable) Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { emit(MessageImageAction.Choose(editing)) }, enabled = enabled && !state.processing && ids.size < 4) {
                Text(stringResource(R.string.attach_images))
            }
            if (state.processing) {
                CircularProgressIndicator(Modifier.size(20.dp))
                TextButton(onClick = { emit(MessageImageAction.CancelProcessing) }) { Text(stringResource(R.string.cancel)) }
            }
        }
        // 历史图片按实际缩略图宽度占位，不能把纯文字气泡或单张图片气泡撑满整行。
        val stripModifier = if (editable) Modifier.fillMaxWidth() else Modifier
        Row(stripModifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ids.forEachIndexed { index, uuid ->
                LaunchedEffect(uuid) { emit(MessageImageAction.Load(uuid)) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(88.dp).clickable { emit(MessageImageAction.Preview(ids, index)) }, contentAlignment = Alignment.Center) {
                        val bitmap = state.thumbnails[uuid]
                        if (bitmap != null) Image(bitmap, stringResource(R.string.message_image), Modifier.size(88.dp), contentScale = ContentScale.Fit)
                        else Text(stringResource(if (state.thumbnails.containsKey(uuid)) R.string.image_missing else R.string.image_loading), style = MaterialTheme.typography.labelSmall)
                    }
                    if (editable) Row {
                        TextButton(onClick = { emit(MessageImageAction.Move(uuid, editing)) }, enabled = enabled && index > 0 && !state.processing) { Text("←") }
                        TextButton(onClick = { emit(MessageImageAction.Remove(uuid, editing)) }, enabled = enabled && !state.processing) { Text("×") }
                    }
                }
            }
        }
        if (editable) state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

/** 原图查看器支持同消息图片切换以及缩放平移；变换属于局部视图状态。 */
@Composable
fun MessageImageViewer(state: MessageImageState, emit: (MessageImageAction) -> Unit) {
    val preview = state.preview ?: return
    var scale by remember(preview.ids, preview.index, preview.sendVersion) { mutableFloatStateOf(1f) }
    var offset by remember(preview.ids, preview.index, preview.sendVersion) { mutableStateOf(Offset.Zero) }
    AlertDialog(
        onDismissRequest = { emit(MessageImageAction.ClosePreview) },
        title = { Text("${preview.index + 1} / ${preview.ids.size}") },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().height(340.dp).pointerInput(preview.index) {
                    detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 8f); offset += pan }
                }, contentAlignment = Alignment.Center) {
                    preview.bitmap?.let { bitmap ->
                        Image(bitmap, stringResource(R.string.message_image), Modifier.fillMaxWidth().graphicsLayer {
                            scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                        }, contentScale = ContentScale.Fit)
                    } ?: Text(stringResource(if (preview.loading) R.string.image_loading else R.string.image_missing))
                }
                Row {
                    TextButton(onClick = { emit(MessageImageAction.Preview(preview.ids, preview.index - 1, preview.sendVersion)) }, enabled = preview.index > 0) { Text("←") }
                    TextButton(onClick = { emit(MessageImageAction.Preview(preview.ids, preview.index + 1, preview.sendVersion)) }, enabled = preview.index < preview.ids.lastIndex) { Text("→") }
                    TextButton(onClick = { emit(MessageImageAction.Preview(preview.ids, preview.index, !preview.sendVersion)) }) {
                        Text(stringResource(if (preview.sendVersion) R.string.image_original else R.string.image_send_preview))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { emit(MessageImageAction.ClosePreview) }) { Text(stringResource(R.string.image_close)) } },
        dismissButton = { TextButton(onClick = { emit(MessageImageAction.Save) }) { Text(stringResource(R.string.image_save)) } }
    )
}
