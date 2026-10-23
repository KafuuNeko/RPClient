package me.kafuuneko.rpclient.ui.message

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.media.MessageImageAction
import me.kafuuneko.rpclient.libs.media.MessageImageState

/**
 * 在消息内绘制自适应图片网格，普通态和编辑态使用相同的尺寸与裁切规则。
 *
 * @param ids 当前消息或编辑草稿的图片标识，顺序由页面状态提供
 * @param state 缩略图及处理状态
 * @param editing 是否显示编辑控件；添加入口由消息标题承载
 * @param emit 图片用户行为回调
 */
@Composable
fun MessageImageGallery(
    ids: List<String>,
    state: MessageImageState,
    editing: Boolean,
    emit: (MessageImageAction) -> Unit,
) {
    if (ids.isEmpty()) return
    var expanded by remember(editing) { mutableStateOf(false) }
    // 多图默认收为四格；编辑时可展开剩余图片，避免隐藏附件无法删除或调整顺序。
    val visibleIds = if (expanded) ids else ids.take(4)
    val columns = if (ids.size >= 4) 2 else ids.size
    val aspectRatio = if (ids.size == 1 || ids.size >= 4) 1.6f else 1f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        visibleIds.chunked(columns).forEachIndexed { rowIndex, rowIds ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowIds.forEachIndexed { columnIndex, uuid ->
                    val index = rowIndex * columns + columnIndex
                    val remaining = if (!expanded && index == 3 && ids.size > 4) ids.size - 3 else 0
                    key(uuid) {
                        MessageImageTile(
                            uuid = uuid,
                            state = state,
                            editing = editing,
                            canMove = index > 0,
                            remaining = remaining,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(aspectRatio),
                            onOpen = {
                                if (editing && remaining > 0) expanded = true
                                else emit(MessageImageAction.Preview(ids, index))
                            },
                            emit = emit,
                        )
                    }
                }
                // 展开后的末行仍沿用双列宽度，不因只剩一张图片而突然放大。
                repeat(columns - rowIds.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MessageImageTile(
    uuid: String,
    state: MessageImageState,
    editing: Boolean,
    canMove: Boolean,
    remaining: Int,
    modifier: Modifier,
    onOpen: () -> Unit,
    emit: (MessageImageAction) -> Unit,
) {
    val currentEmit by rememberUpdatedState(emit)
    var showMenu by remember(editing) { mutableStateOf(false) }
    // 只注册实际显示的缩略图，重排或折叠后释放离开组合的图片引用。
    DisposableEffect(uuid) {
        currentEmit(MessageImageAction.RegisterDisplay(uuid))
        onDispose { currentEmit(MessageImageAction.ReleaseDisplay(uuid)) }
    }
    val canMoveImageEarlier = editing && canMove && !state.processing && remaining == 0
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(LocalContentColor.current.copy(alpha = 0.10f))
            .combinedClickable(
                onClick = onOpen,
                onLongClickLabel = stringResource(R.string.image_move_earlier),
                onLongClick = if (canMoveImageEarlier) {
                    { showMenu = true }
                } else null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = state.thumbnails[uuid]
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.message_image),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = stringResource(
                    id = if (state.thumbnails.containsKey(uuid)) R.string.image_missing else R.string.image_loading,
                ),
                modifier = Modifier.padding(6.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        MessageImageOverlay(
            remaining = remaining,
            editable = editing,
            processing = state.processing,
            onRemove = { emit(MessageImageAction.Remove(uuid = uuid, editing = true)) },
        )
        // 将原有前移操作收进长按菜单，避免每张图下方的文字按钮撑高编辑区。
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.image_move_earlier)) },
                enabled = !state.processing,
                onClick = {
                    showMenu = false
                    emit(MessageImageAction.Move(uuid = uuid, editing = true))
                },
            )
        }
    }
}

@Composable
private fun MessageImageOverlay(
    remaining: Int,
    editable: Boolean,
    processing: Boolean,
    onRemove: () -> Unit,
) {
    // 编辑控件覆盖在图片内，不参与网格测量，切换状态时图片位置保持不变。
    Box(Modifier.fillMaxSize()) {
        if (remaining > 0) {
            val description = stringResource(R.string.image_more_count, remaining)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) { Text(text = "+$remaining", color = Color.White, style = MaterialTheme.typography.titleMedium) }
        } else if (editable) {
            IconButton(
                onClick = onRemove,
                enabled = !processing,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.delete),
                    tint = Color.White.copy(alpha = if (processing) 0.38f else 1f),
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.58f))
                        .border(width = 0.5.dp, color = Color.White.copy(alpha = 0.45f), shape = CircleShape)
                        .padding(3.dp),
                )
            }
        }
    }
}

/**
 * 在消息标题右侧提供添加入口；处理期间同位置展示可取消的进度指示。
 *
 * @param state 图片编辑与处理状态
 * @param emit 图片用户行为回调
 */
@Composable
fun MessageImageEditButton(state: MessageImageState, emit: (MessageImageAction) -> Unit) {
    val contentColor = LocalContentColor.current
    // 处理进度替换添加图标，不额外插入整行控件，以保持标题和图片的位置稳定。
    IconButton(
        onClick = {
            emit(if (state.processing) MessageImageAction.CancelProcessing else MessageImageAction.Choose(editing = true))
        },
        enabled = !state.submitting && (state.processing || state.canAddEditing),
        modifier = Modifier.size(32.dp),
    ) {
        if (state.processing) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    color = contentColor,
                    strokeWidth = 1.5.dp,
                )
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.cancel),
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.attach_images),
                modifier = Modifier
                    .size(24.dp)
                    .border(
                        border = BorderStroke(width = 1.dp, color = LocalContentColor.current.copy(alpha = 0.72f)),
                        shape = CircleShape,
                    )
                    .padding(2.dp),
            )
        }
    }
}
