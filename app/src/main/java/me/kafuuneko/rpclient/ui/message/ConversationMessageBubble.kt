package me.kafuuneko.rpclient.ui.message

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.appearance.PageSurfaceKind
import me.kafuuneko.rpclient.libs.media.MessageImageAction
import me.kafuuneko.rpclient.libs.media.MessageImageState
import me.kafuuneko.rpclient.model.MessageContentPart
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.DefaultCharacterAccentColor
import me.kafuuneko.rpclient.ui.theme.NarratorAvatarColor
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.LocalPageBubbleOpacity
import me.kafuuneko.rpclient.ui.widgets.MarkdownMessageText
import me.kafuuneko.rpclient.ui.widgets.PageSurface
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.RpScrollableOutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.pageUserBubbleContentColor

private val MESSAGE_AVATAR_SIZE = 34.dp
private val MESSAGE_AVATAR_CORNER_RADIUS = (34 * 0.28).dp
private val USER_BUBBLE_MAX_WIDTH = 310.dp
private val OTHER_BUBBLE_MAX_WIDTH = 295.dp

/** 会话消息在展示层中的视觉角色，用于统一单聊与群聊的气泡样式。 */
internal enum class ConversationMessageRole {
    User,

    Assistant,

    Narrator,
}

/** 非用户消息左侧头像的展示参数。 */
internal data class ConversationMessageAvatar(
    /** 图片不可用时展示的占位字符。 */
    val text: String,
    /** 占位底板、边框与文字的强调色。 */
    val accentColor: Color,
    /** 已解码的头像图片；为空时回退文字占位。 */
    val image: ImageBitmap? = null,
)

/**
 * 单聊与群聊共用的消息气泡展示模型。
 *
 * - 只承载展示所需的只读字段，不耦合各页面的领域实体或意图类型。
 * - 由各会话页面将自有 UI 模型映射至此结构，确保两类会话视觉保持一致。
 */
internal data class ConversationMessageItem(
    /** 当前消息的稳定标识，用于局部操作栏展开态与列表复用。 */
    val id: String,
    /** 当前消息对应的视觉角色。 */
    val role: ConversationMessageRole,
    /** 展示在气泡顶部左侧的发言人名称。 */
    val speaker: String,
    /** 原始消息正文，用于判断流式生成空内容态。 */
    val content: String,
    /** 按正文与推理块拆分后的展示片段。 */
    val parts: List<MessageContentPart>,
    /** 展示在气泡顶部右侧的时间文本。 */
    val time: String,
    /** 消息正文附带的图片文件引用。 */
    val imageUuids: List<String> = emptyList(),
    /** 当前消息是否处于流式生成中。 */
    val isStreaming: Boolean = false,
    /** 非用户消息左侧展示的头像信息；用户消息不展示头像。 */
    val avatar: ConversationMessageAvatar? = null,
)

/**
 * 单聊与群聊共用的消息气泡组件，以单聊样式为统一标准。
 *
 * - 统一头像尺寸与圆角、气泡宽度上限、底角尖角、内嵌标题行、思考块与内联编辑区。
 * - 统一气泡外下方的独立毛玻璃悬浮操作栏，由各页面通过 [actions] 槽位注入各自支持的业务操作。
 *
 * @param message 待渲染的通用消息展示模型
 * @param imageState 当前页面的图片缩略图与编辑状态
 * @param expandedThinkBlockIds 已展开的推理块 ID 集合
 * @param editing 当前消息是否处于内联编辑态
 * @param editingDraft 当前消息编辑草稿文本
 * @param onToggleThinkBlock 切换推理块展开或折叠的回调
 * @param onEditingDraftChange 编辑草稿内容变化的回调
 * @param onSaveEdit 提交编辑结果的回调
 * @param onCancelEdit 取消编辑的回调
 * @param onImageAction 消息图片预览或编辑操作回调
 * @param actions 气泡外下方悬浮操作栏中的业务操作按钮槽位
 * @param modifier 整行消息容器的修饰符
 */
@Composable
internal fun ConversationMessageBubble(
    message: ConversationMessageItem,
    imageState: MessageImageState,
    expandedThinkBlockIds: Set<String>,
    editing: Boolean,
    editingDraft: String,
    onToggleThinkBlock: (String) -> Unit,
    onEditingDraftChange: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onImageAction: (MessageImageAction) -> Unit,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val isUser = message.role == ConversationMessageRole.User
    var showActions by remember(message.id) { mutableStateOf(false) }

    // 整行按发言角色左右对齐，头像与气泡容器在顶边对齐。
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser) {
            message.avatar?.let { avatar ->
                ConversationMessageAvatarView(avatar = avatar)
            }
        }

        // 气泡主体与外置悬浮操作栏垂直堆叠，保持各自独立的毛玻璃底板。
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ConversationBubbleCard(
                message = message,
                imageState = imageState,
                expandedThinkBlockIds = expandedThinkBlockIds,
                editing = editing,
                editingDraft = editingDraft,
                onBubbleClick = { showActions = !showActions },
                onToggleThinkBlock = onToggleThinkBlock,
                onEditingDraftChange = onEditingDraftChange,
                onSaveEdit = onSaveEdit,
                onCancelEdit = onCancelEdit,
                onImageAction = onImageAction,
            )

            // 编辑态和流式生成态均隐藏操作栏，避免对未落库或编辑中消息触发并发操作。
            ConversationMessageActionBar(
                visible = showActions && !editing && !message.isStreaming,
                actions = actions,
            )
        }
    }
}

/**
 * 统一悬浮操作栏中的单个图标按钮，保持单聊与群聊一致的触摸尺寸、图标规格和着色。
 *
 * @param icon 操作图标
 * @param contentDescription 无障碍与语义描述文案
 * @param onClick 点击触发的业务操作回调
 */
@Composable
internal fun ConversationMessageActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    // 操作数量由页面决定，按钮与图标尺寸固定以保持各类会话的操作栏高度一致。
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(30.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(17.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 渲染非用户消息左侧的统一圆角头像。 */
@Composable
private fun ConversationMessageAvatarView(avatar: ConversationMessageAvatar) {
    RpAvatar(
        text = avatar.text,
        color = avatar.accentColor,
        modifier = Modifier
            .padding(end = 8.dp, top = 2.dp)
            .size(MESSAGE_AVATAR_SIZE),
        shape = RoundedCornerShape(MESSAGE_AVATAR_CORNER_RADIUS),
        image = avatar.image,
    )
}

/** 渲染气泡底板、内嵌标题行、图片网格以及正文或内联编辑区。 */
@Composable
private fun ConversationBubbleCard(
    message: ConversationMessageItem,
    imageState: MessageImageState,
    expandedThinkBlockIds: Set<String>,
    editing: Boolean,
    editingDraft: String,
    onBubbleClick: () -> Unit,
    onToggleThinkBlock: (String) -> Unit,
    onEditingDraftChange: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onImageAction: (MessageImageAction) -> Unit,
) {
    val isUser = message.role == ConversationMessageRole.User
    // 供应商对助手图片的支持不一致，暂只向用户消息开放新增入口，保留所有消息既有附件的编辑能力。
    val canAddImages = editing && isUser
    val imageIds = if (editing) imageState.editing else message.imageUuids
    val hasImageHeader = imageIds.isNotEmpty() || message.imageUuids.isNotEmpty() || canAddImages
    val bubbleShape = message.role.bubbleShape()
    val bubbleColor = message.role.bubbleColor()
    val bubbleMaxWidth = if (isUser) USER_BUBBLE_MAX_WIDTH else OTHER_BUBBLE_MAX_WIDTH
    val bubbleShadowElevation = (if (isUser) 1.5.dp else 0.5.dp) * LocalPageBubbleOpacity.current

    // 气泡表面使用独立壁纸模糊参数，编辑中或流式输出中禁用点击切换操作栏。
    PageSurface(
        kind = PageSurfaceKind.Bubble,
        modifier = Modifier
            .widthIn(max = bubbleMaxWidth)
            .clickable(
                enabled = !editing && !message.isStreaming,
                onClick = onBubbleClick,
            ),
        shape = bubbleShape,
        color = bubbleColor,
        shadowElevation = bubbleShadowElevation,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ConversationBubbleHeader(
                speaker = message.speaker,
                time = message.time,
                isUser = isUser,
                hasImageHeader = hasImageHeader,
                canAddImages = canAddImages,
                imageState = imageState,
                onImageAction = onImageAction,
            )

            MessageImageGallery(
                ids = imageIds,
                state = imageState,
                editing = editing,
                emit = onImageAction,
            )

            // 仅在编辑当前消息时展示图片处理错误提示。
            if (editing) {
                imageState.errorResId?.let { errorResId ->
                    Text(
                        text = stringResource(errorResId),
                        color = if (isUser) pageUserBubbleContentColor() else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // 按当前是否编辑切换内联编辑框与富文本正文渲染。
            if (editing) {
                ConversationMessageEditContent(
                    draft = editingDraft,
                    imageState = imageState,
                    isUser = isUser,
                    onDraftChange = onEditingDraftChange,
                    onSave = onSaveEdit,
                    onCancel = onCancelEdit,
                )
            } else {
                ConversationMessageContent(
                    message = message,
                    expandedThinkBlockIds = expandedThinkBlockIds,
                    isUser = isUser,
                    onToggleThinkBlock = onToggleThinkBlock,
                )
            }
        }
    }
}

/** 渲染气泡顶部的发言人名称、时间戳与可选的图片添加按钮。 */
@Composable
private fun ConversationBubbleHeader(
    speaker: String,
    time: String,
    isUser: Boolean,
    hasImageHeader: Boolean,
    canAddImages: Boolean,
    imageState: MessageImageState,
    onImageAction: (MessageImageAction) -> Unit,
) {
    val speakerColor = if (isUser) {
        pageUserBubbleContentColor()
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val timeColor = if (isUser) {
        pageUserBubbleContentColor().copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    }

    // 图片消息为标题行预留固定最小高度，避免添加按钮出现时推动下方图片网格跳动。
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (hasImageHeader) 32.dp else 0.dp),
    ) {
        Text(
            text = speaker,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = speakerColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall,
            color = timeColor,
        )
        if (canAddImages) {
            Spacer(modifier = Modifier.width(4.dp))
            MessageImageEditButton(
                state = imageState,
                emit = onImageAction,
            )
        }
    }
}

/** 渲染位于气泡外下方的独立毛玻璃悬浮操作栏。 */
@Composable
private fun ConversationMessageActionBar(
    visible: Boolean,
    actions: @Composable RowScope.() -> Unit,
) {
    // 展开动画只作用于外置操作栏，避免气泡底板跟随操作区一起变形。
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        // 独立采样气泡区域的壁纸模糊，操作栏与正文共用透明度而不重复叠加底色。
        PageSurface(
            kind = PageSurfaceKind.Bubble,
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = 0.88f * LocalPageBubbleOpacity.current,
            ),
            shadowElevation = 2.dp * LocalPageBubbleOpacity.current,
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

/** 按分段顺序渲染消息正文、可折叠推理块以及空内容的流式等待提示。 */
@Composable
private fun ConversationMessageContent(
    message: ConversationMessageItem,
    expandedThinkBlockIds: Set<String>,
    isUser: Boolean,
    onToggleThinkBlock: (String) -> Unit,
) {
    // 扮演用户流式输出时可能出现主色用户气泡，需要为等待提示切换高对比前景色。
    val waitingTextColor = if (isUser) {
        pageUserBubbleContentColor().copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f)
    }
    val waitingIndicatorColor = if (isUser) {
        pageUserBubbleContentColor()
    } else {
        MaterialTheme.colorScheme.primary
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (message.isStreaming && (message.content.isBlank() || message.parts.isEmpty())) {
            ConversationStreamingStatus(
                text = stringResource(R.string.waiting_for_response),
                color = waitingTextColor,
                indicatorColor = waitingIndicatorColor,
            )
        }
        message.parts.forEach { part ->
            when (part) {
                is MessageContentPart.Text -> {
                    if (part.content.isNotBlank()) {
                        MarkdownMessageText(
                            content = part.content,
                            isUser = isUser,
                        )
                    }
                }

                is MessageContentPart.Think -> ConversationThinkBlock(
                    part = part,
                    expanded = part.id in expandedThinkBlockIds,
                    isThinking = message.isStreaming && !part.isComplete,
                    onToggle = { onToggleThinkBlock(part.id) },
                )
            }
        }
    }
}

/** 在流式消息内部展示轻量进度指示与状态文案。 */
@Composable
private fun ConversationStreamingStatus(
    text: String,
    color: Color,
    indicatorColor: Color,
    modifier: Modifier = Modifier,
) {
    // 等待与思考提示共用排版，前景配色由所属气泡提供以维持对比度。
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = indicatorColor,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

/** 渲染可折叠的模型推理过程块，展开状态由页面状态管理。 */
@Composable
private fun ConversationThinkBlock(
    part: MessageContentPart.Think,
    expanded: Boolean,
    isThinking: Boolean,
    onToggle: () -> Unit,
) {
    val surfaceAlpha = (if (isThinking) 0.55f else 0.38f) * LocalPageBubbleOpacity.current
    val iconTint = if (isThinking) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    // 思考块继承气泡透明度且不重复采样壁纸模糊，点击整块切换展开态。
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = surfaceAlpha),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp),
                )
                if (isThinking) {
                    ConversationStreamingStatus(
                        text = stringResource(R.string.thinking),
                        color = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.thought_process),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(if (expanded) R.string.hide else R.string.show),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Text(
                    text = part.content,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** 渲染消息气泡内的内联编辑框及取消/保存操作行。 */
@Composable
private fun ConversationMessageEditContent(
    draft: String,
    imageState: MessageImageState,
    isUser: Boolean,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val userContentColor = pageUserBubbleContentColor()
    val textColor = if (isUser) userContentColor else MaterialTheme.colorScheme.onSurface
    val accentColor = if (isUser) userContentColor else MaterialTheme.colorScheme.primary

    // 编辑框沿用正文排版，短消息仅占一行，长消息在最大行数内滚动。
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RpScrollableOutlinedTextField(
            enabled = !imageState.loadState.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium,
            value = draft,
            onValueChange = onDraftChange,
            minLines = 1,
            maxLines = 8,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                cursorColor = accentColor,
                focusedContainerColor = if (isUser) userContentColor.copy(alpha = 0.06f) else Color.Transparent,
                unfocusedContainerColor = if (isUser) userContentColor.copy(alpha = 0.04f) else Color.Transparent,
                focusedBorderColor = if (isUser) userContentColor.copy(alpha = 0.72f) else MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = if (isUser) userContentColor.copy(alpha = 0.42f) else MaterialTheme.colorScheme.outline,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = onCancel,
                enabled = !imageState.loadState.isSubmitting,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = if (isUser) userContentColor.copy(alpha = 0.72f) else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Normal,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(
                onClick = onSave,
                enabled = !imageState.loadState.isBusy,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(if (imageState.loadState.isSubmitting) R.string.image_loading else R.string.save),
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                )
            }
        }
    }
}

/** 根据视觉角色返回单聊标准的气泡圆角形状。 */
private fun ConversationMessageRole.bubbleShape(): Shape = when (this) {
    ConversationMessageRole.User -> RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = 18.dp,
        bottomEnd = 4.dp,
    )

    ConversationMessageRole.Assistant -> RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = 4.dp,
        bottomEnd = 18.dp,
    )

    ConversationMessageRole.Narrator -> RoundedCornerShape(14.dp)
}

/** 根据视觉角色返回遵循气泡透明度的填充底色。 */
@Composable
private fun ConversationMessageRole.bubbleColor(): Color = when (this) {
    ConversationMessageRole.User -> MaterialTheme.colorScheme.primary.copy(
        alpha = LocalPageBubbleOpacity.current,
    )

    ConversationMessageRole.Assistant -> MaterialTheme.colorScheme.surface.copy(
        alpha = LocalPageBubbleOpacity.current,
    )

    ConversationMessageRole.Narrator -> MaterialTheme.colorScheme.surfaceVariant.copy(
        alpha = 0.50f * LocalPageBubbleOpacity.current,
    )
}

@Preview(widthDp = 390, showBackground = true)
@Composable
private fun ConversationMessageBubblePreview() {
    // 固定主题配色，便于对照不同角色的气泡底色、圆角与头像布局。
    AppTheme(dynamicColor = false) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 角色回复同时覆盖展开的推理块和富文本正文，检查内容分段后的间距。
            ConversationMessageBubble(
                message = ConversationMessageItem(
                    id = "1",
                    role = ConversationMessageRole.Assistant,
                    speaker = "Lyra",
                    content = "The rain kept tapping on the archive windows.",
                    parts = listOf(
                        MessageContentPart.Think(
                            id = "think-1",
                            content = "Check the lantern before opening the door.",
                            isComplete = true,
                        ),
                        MessageContentPart.Text(
                            content = "The rain kept **tapping** on the archive windows.",
                        ),
                    ),
                    time = "21:04",
                    avatar = ConversationMessageAvatar(
                        text = "L",
                        accentColor = DefaultCharacterAccentColor,
                    ),
                ),
                imageState = MessageImageState(),
                expandedThinkBlockIds = setOf("think-1"),
                editing = false,
                editingDraft = "",
                onToggleThinkBlock = {},
                onEditingDraftChange = {},
                onSaveEdit = {},
                onCancelEdit = {},
                onImageAction = {},
                actions = {
                    ConversationMessageActionButton(
                        icon = Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.copy),
                        onClick = {},
                    )
                    ConversationMessageActionButton(
                        icon = Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.edit),
                        onClick = {},
                    )
                    ConversationMessageActionButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.regenerate),
                        onClick = {},
                    )
                    ConversationMessageActionButton(
                        icon = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.delete),
                        onClick = {},
                    )
                },
            )
            // 用户消息不展示头像，保留右对齐与主色底板的对照样本。
            ConversationMessageBubble(
                message = ConversationMessageItem(
                    id = "2",
                    role = ConversationMessageRole.User,
                    speaker = "You",
                    content = "Let us inspect the upper shelf first.",
                    parts = listOf(MessageContentPart.Text("Let us inspect the upper shelf first.")),
                    time = "21:05",
                ),
                imageState = MessageImageState(),
                expandedThinkBlockIds = emptySet(),
                editing = false,
                editingDraft = "",
                onToggleThinkBlock = {},
                onEditingDraftChange = {},
                onSaveEdit = {},
                onCancelEdit = {},
                onImageAction = {},
                actions = {},
            )
            // 旁白使用独立底色和均匀圆角，避免与普通角色回复混淆。
            ConversationMessageBubble(
                message = ConversationMessageItem(
                    id = "3",
                    role = ConversationMessageRole.Narrator,
                    speaker = "System",
                    content = "A distant bell rings across the harbor.",
                    parts = listOf(MessageContentPart.Text("A distant bell rings across the harbor.")),
                    time = "21:06",
                    avatar = ConversationMessageAvatar(
                        text = "S",
                        accentColor = NarratorAvatarColor,
                    ),
                ),
                imageState = MessageImageState(),
                expandedThinkBlockIds = emptySet(),
                editing = false,
                editingDraft = "",
                onToggleThinkBlock = {},
                onEditingDraftChange = {},
                onSaveEdit = {},
                onCancelEdit = {},
                onImageAction = {},
                actions = {},
            )
        }
    }
}
