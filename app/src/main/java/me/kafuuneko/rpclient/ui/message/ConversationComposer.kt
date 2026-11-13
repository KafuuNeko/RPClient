package me.kafuuneko.rpclient.ui.message

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.appearance.PageSurfaceKind
import me.kafuuneko.rpclient.libs.media.MessageImageAction
import me.kafuuneko.rpclient.libs.media.MessageImageState
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.LocalPageBottomBarOpacity
import me.kafuuneko.rpclient.ui.widgets.PageSurface
import me.kafuuneko.rpclient.ui.widgets.RpScrollableOutlinedTextField
import androidx.compose.material.icons.rounded.Image as ImageIcon

/**
 * 单聊与群聊共用的输入区，统一底栏、附件、快捷操作及发送按钮的布局。
 * @param inputEnabled 是否允许编辑，代写期间可由页面保持输入区可用。
 * @param readOnly 是否只读，保留代写内容选择能力。
 * @param beforeActions 快捷操作前的附加面板。
 * @param actions 当前会话支持的快捷操作。
 */
@Composable
internal fun ConversationComposer(
    draft: String,
    placeholder: String,
    imageState: MessageImageState,
    isGenerating: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onImageAction: (MessageImageAction) -> Unit,
    inputEnabled: Boolean = !isGenerating,
    readOnly: Boolean = false,
    beforeActions: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit,
) {
    // 区域透明度由公共底栏传递，内部描边和填充只应用一次。
    PageSurface(
        kind = PageSurfaceKind.BottomBar,
        color = MaterialTheme.colorScheme.surface.copy(alpha = LocalPageBottomBarOpacity.current),
        tonalElevation = 2.dp * LocalPageBottomBarOpacity.current,
        shadowElevation = 3.dp * LocalPageBottomBarOpacity.current,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f))
                    .height(0.5.dp),
            )
            DraftAttachmentTray(
                state = imageState,
                enabled = !isGenerating && !imageState.loadState.isSubmitting,
                emit = onImageAction,
            )
            beforeActions()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
            // 输入框随行数增长，发送按钮始终与整个输入框垂直居中。
            ConversationInputRow(
                draft = draft,
                placeholder = placeholder,
                imageState = imageState,
                isGenerating = isGenerating,
                inputEnabled = inputEnabled,
                readOnly = readOnly,
                onDraftChange = onDraftChange,
                onSend = onSend,
                onStop = onStop,
                onImageAction = onImageAction,
            )
        }
    }
}

/** 共用文字排版、描边和触摸尺寸，保持单行与多行输入时的按钮对齐规则一致。 */
@Composable
private fun ConversationInputRow(
    draft: String,
    placeholder: String,
    imageState: MessageImageState,
    isGenerating: Boolean,
    inputEnabled: Boolean,
    readOnly: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onImageAction: (MessageImageAction) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val canAttach = !isGenerating && !imageState.loadState.isBusy && imageState.canAddDraft
    val sendButtonColor by animateColorAsState(
        targetValue = if (isGenerating) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        label = "sendButtonColor",
    )
    // 两类会话共用相同的尺寸与配色，不依赖各页面额外指定高度。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RpScrollableOutlinedTextField(
            modifier = Modifier.weight(1f),
            value = draft,
            onValueChange = onDraftChange,
            enabled = inputEnabled && !imageState.loadState.isSubmitting,
            readOnly = readOnly,
            minLines = 1,
            maxLines = 5,
            shape = RoundedCornerShape(24.dp),
            leadingIcon = {
                IconButton(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onImageAction(MessageImageAction.Choose(editing = false))
                    },
                    enabled = canAttach,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ImageIcon,
                        contentDescription = stringResource(R.string.attach_images),
                        tint = if (canAttach) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = 0.38f
                        ),
                    )
                }
            },
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = 0.5f
                        )
                    ),
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                disabledBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
            ),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.width(8.dp))
        // 生成期间沿用停止操作，图标在固定尺寸的圆形触摸区域内居中。
        AppSurface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = sendButtonColor,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (isGenerating) onStop() else onSend()
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isGenerating) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                    contentDescription = stringResource(if (isGenerating) R.string.stop else R.string.send),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** 统一会话快捷操作的字号、图标、内边距和禁用反馈。 */
@Composable
internal fun ConversationQuickAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    // 保留禁用态和点击反馈，避免生成期间误触不可用操作。
    val hapticFeedback = LocalHapticFeedback.current
    AppSurface(
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f * LocalPageBottomBarOpacity.current)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f * LocalPageBottomBarOpacity.current)
        },
        modifier = Modifier.clickable(
            enabled = enabled,
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = 0.38f,
                ),
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = 0.38f,
                ),
            )
        }
    }
}
