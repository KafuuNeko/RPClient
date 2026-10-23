package me.kafuuneko.rpclient.ui.message

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.media.MessageImageAction
import me.kafuuneko.rpclient.libs.media.MessageImageState

/**
 * 输入栏待发送图片抽屉托盘组件。
 * 在有附件、处理任务或错误提示时渲染；草稿通过长按菜单调整顺序。
 *
 * @param state 当前页面的图片状态。
 * @param enabled 页面是否允许修改附件。
 * @param modifier 修饰符。
 * @param emit 图片操作的意图回调。
 */
@Composable
fun DraftAttachmentTray(
    state: MessageImageState,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    emit: (MessageImageAction) -> Unit,
) {
    val shouldHideImageDraft = state.draft.isEmpty()
        && (state.submitting
        || !state.processing)
        && state.errorResId == null
    if (shouldHideImageDraft) return
    val currentEmit by rememberUpdatedState(emit)
    var moveMenuUuid by remember(state.draft, enabled, state.processing) { mutableStateOf<String?>(value = null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.draft.forEachIndexed { index, uuid ->
                DisposableEffect(uuid) {
                    currentEmit(MessageImageAction.RegisterDisplay(uuid))
                    onDispose { currentEmit(MessageImageAction.ReleaseDisplay(uuid)) }
                }

                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(
                            border = BorderStroke(
                                width = 0.8.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            ),
                            shape = RoundedCornerShape(12.dp),
                        )
                        .combinedClickable(
                            enabled = !state.processing,
                            onClick = {
                                currentEmit(MessageImageAction.Preview(state.draft, index))
                            },
                            onLongClickLabel = stringResource(R.string.image_move_earlier),
                            onLongClick = if (enabled && index > 0) {
                                { moveMenuUuid = uuid }
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
                            text = stringResource(if (state.thumbnails.containsKey(uuid)) R.string.image_missing else R.string.image_loading),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (state.processing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.38f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                        }
                    } else if (enabled) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.58f))
                                .clickable(
                                    onClick = {
                                        currentEmit(MessageImageAction.Remove(uuid = uuid, editing = false))
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.delete),
                                tint = Color.White,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                    // 菜单绑定图片 UUID，重排后仍操作同一附件，沿用历史编辑的前移交互。
                    DropdownMenu(
                        expanded = moveMenuUuid == uuid && enabled && !state.processing && index > 0,
                        onDismissRequest = { moveMenuUuid = null },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.image_move_earlier)) },
                            enabled = enabled && !state.processing && index > 0,
                            onClick = {
                                moveMenuUuid = null
                                currentEmit(MessageImageAction.Move(uuid = uuid, editing = false))
                            },
                        )
                    }
                }
            }

            // 未达到上限且未在处理中时展示追加图片卡片
            if (enabled && state.canAddDraft && !state.processing) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .border(
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            ),
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable(
                            onClick = {
                                currentEmit(MessageImageAction.Choose(editing = false))
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.attach_images),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        if (state.processing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.image_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(6.dp))
                TextButton(
                    onClick = { currentEmit(MessageImageAction.CancelProcessing) },
                    enabled = !state.submitting,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        state.errorResId?.let { errorId ->
            Text(
                text = stringResource(errorId),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp),
            )
        }
    }
}
