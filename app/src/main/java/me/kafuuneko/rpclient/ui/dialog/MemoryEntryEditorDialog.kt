package me.kafuuneko.rpclient.ui.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.memory.MemoryDraft
import me.kafuuneko.rpclient.libs.memory.MemoryKind
import me.kafuuneko.rpclient.libs.memory.MemoryStatus
import me.kafuuneko.rpclient.ui.theme.AccentAmberColor
import me.kafuuneko.rpclient.ui.theme.AccentEmeraldColor
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.draggableScrollIndicator
import me.kafuuneko.rpclient.ui.widgets.pageSliderColors
import me.kafuuneko.rpclient.ui.widgets.rememberBoundTextFieldState
import me.kafuuneko.rpclient.ui.widgets.AppSurface as Surface
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.PageSwitch as Switch

/** 记忆编辑弹窗中的通用下拉选项。 */
data class MemoryDialogOption(
    /** 选项标识，为空表示不限或共享范围。 */
    val id: Long?,
    /** 供界面展示的名称。 */
    val label: String,
)

/**
 * 通用记忆条目新建与编辑对话框。
 *
 * 复用应用级弹窗脚手架与内嵌正文视口，将状态、类型、权重、置顶与适用范围按信息层级分区排布，
 * 不直接依赖业务页面的 UiState 或 ViewModel。
 *
 * @param onDismissRequest 请求关闭对话框时调用
 * @param draft 当前表单草稿
 * @param isEditing 是否为编辑已有条目
 * @param allowScopeSelection 是否允许在当前表单中修改人设与可见范围
 * @param showAudienceScope 是否展示会话级可见范围字段
 * @param personaOptions 可选人设列表
 * @param audienceOptions 可选可见范围列表
 * @param fixedPersonaLabel 范围只读时展示的人设名称
 * @param fixedAudienceLabel 范围只读时展示的可见范围名称，角色范围为空
 * @param isSaving 是否处于保存提交中
 * @param onDraftChange 草稿字段变更回调
 * @param onReload 重新读取已保存版本回调，新建模式为空
 * @param onSave 确认保存回调
 * @param modifier 外层弹窗容器修饰符
 */
@Composable
fun MemoryEntryEditorDialog(
    onDismissRequest: () -> Unit,
    draft: MemoryDraft,
    isEditing: Boolean,
    allowScopeSelection: Boolean,
    showAudienceScope: Boolean,
    personaOptions: List<MemoryDialogOption>,
    audienceOptions: List<MemoryDialogOption>,
    fixedPersonaLabel: String,
    fixedAudienceLabel: String?,
    isSaving: Boolean,
    onDraftChange: (MemoryDraft) -> Unit,
    onReload: (() -> Unit)?,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textFieldState = rememberBoundTextFieldState(
        value = draft.body,
        onValueChange = { updatedBody -> onDraftChange(draft.copy(body = updatedBody)) },
    )
    // 首次打开时把光标移至文末，后续外部刷新由绑定状态保留合理选择区。
    LaunchedEffect(textFieldState) {
        textFieldState.edit { selection = TextRange(length) }
    }
    // 计算实时字数与粗略 Token 估算值用于弹窗副标题展示。
    val charCount = draft.body.length
    val estimatedTokens = (charCount / 3.5).toInt().coerceAtLeast(0)
    val formScrollState = rememberScrollState()

    AppDialogScaffold(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .widthIn(max = 520.dp),
        onDismissRequest = onDismissRequest,
        title = stringResource(if (isEditing) R.string.edit else R.string.add),
        subtitle = stringResource(R.string.prompt_editor_char_count, charCount, estimatedTokens),
        subtitleMaxLines = 1,
        badgeIcon = if (isEditing) Icons.Rounded.Edit else Icons.Rounded.Add,
        badgeTone = DialogBadgeTone.Primary,
        compactHeader = true,
        confirmText = stringResource(R.string.save),
        dismissText = stringResource(R.string.cancel),
        confirmEnabled = !isSaving && draft.body.isNotBlank() && charCount <= MAX_MEMORY_BODY_LENGTH,
        isConfirmLoading = isSaving,
        onConfirm = onSave,
        onDismiss = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        scrollableContent = false,
    ) {
        // 表单区自适应弹窗剩余高度，键盘展开时保持头部和底部保存按钮稳定可见。
        MemoryEntryEditorForm(
            textFieldState = textFieldState,
            formScrollState = formScrollState,
            draft = draft,
            allowScopeSelection = allowScopeSelection,
            showAudienceScope = showAudienceScope,
            personaOptions = personaOptions,
            audienceOptions = audienceOptions,
            fixedPersonaLabel = fixedPersonaLabel,
            fixedAudienceLabel = fixedAudienceLabel,
            enabled = !isSaving,
            onDraftChange = onDraftChange,
            onReload = onReload,
        )
    }
}

@Composable
private fun MemoryEntryEditorForm(
    textFieldState: TextFieldState,
    formScrollState: ScrollState,
    draft: MemoryDraft,
    allowScopeSelection: Boolean,
    showAudienceScope: Boolean,
    personaOptions: List<MemoryDialogOption>,
    audienceOptions: List<MemoryDialogOption>,
    fixedPersonaLabel: String,
    fixedAudienceLabel: String?,
    enabled: Boolean,
    onDraftChange: (MemoryDraft) -> Unit,
    onReload: (() -> Unit)?,
) {
    // 按正文、状态、类型、权重置顶与适用范围的视觉层级排布表单分区。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .draggableScrollIndicator(formScrollState)
            .verticalScroll(formScrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 渲染记忆正文输入框及清空、重载快捷工具。
        MemoryBodySection(
            state = textFieldState,
            hasText = draft.body.isNotEmpty(),
            enabled = enabled,
            onClear = { textFieldState.setTextAndPlaceCursorAtEnd("") },
            onReload = onReload,
        )
        // 渲染状态分段选择器与类型三列网格。
        MemoryStatusSegmentedSelector(
            selectedStatus = draft.status,
            enabled = enabled,
            onSelect = { status -> onDraftChange(draft.copy(status = status)) },
        )
        MemoryKindGridSelector(
            selectedKind = draft.kind,
            enabled = enabled,
            onSelect = { kind -> onDraftChange(draft.copy(kind = kind)) },
        )
        // 渲染重要度滑块、置顶开关及适用范围区域。
        MemoryPriorityPanel(
            importance = draft.importance,
            pinned = draft.pinned,
            enabled = enabled,
            onImportanceChange = { value -> onDraftChange(draft.copy(importance = value)) },
            onPinnedChange = { pinned -> onDraftChange(draft.copy(pinned = pinned)) },
        )
        MemoryScopeSection(
            allowScopeSelection = allowScopeSelection,
            showAudienceScope = showAudienceScope,
            personaId = draft.personaId,
            privateCharacterId = draft.privateCharacterId,
            personaOptions = personaOptions,
            audienceOptions = audienceOptions,
            fixedPersonaLabel = fixedPersonaLabel,
            fixedAudienceLabel = fixedAudienceLabel,
            enabled = enabled,
            onPersonaSelect = { personaId -> onDraftChange(draft.copy(personaId = personaId)) },
            onAudienceSelect = { audienceId ->
                onDraftChange(draft.copy(privateCharacterId = audienceId))
            },
        )
    }
}

@Composable
private fun MemoryBodySection(
    state: TextFieldState,
    hasText: Boolean,
    enabled: Boolean,
    onClear: () -> Unit,
    onReload: (() -> Unit)?,
) {
    val bodyScrollState = rememberScrollState()
    // 组合正文标题操作行与内嵌圆角多行编辑视口。
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MemoryBodyHeaderRow(
            hasText = hasText,
            enabled = enabled,
            onClear = onClear,
            onReload = onReload,
        )
        // 正文视口沿用通用提示词编辑器的内嵌圆角外观，避免双层描边输入框抢占滚动。
        val viewportShape = RoundedCornerShape(16.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp, max = 148.dp)
                .clip(viewportShape)
                .background(
                    dialogControlColor(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f),
                    shape = viewportShape,
                )
                .padding(14.dp),
        ) {
            if (state.text.isEmpty()) {
                Text(
                    text = stringResource(R.string.memory_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                )
            }
            BasicTextField(
                state = state,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .draggableScrollIndicator(bodyScrollState),
                scrollState = bodyScrollState,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun MemoryBodyHeaderRow(
    hasText: Boolean,
    enabled: Boolean,
    onClear: () -> Unit,
    onReload: (() -> Unit)?,
) {
    val haptic = LocalHapticFeedback.current
    // 标题行左侧显示字段名，右侧收纳清空与重新读取快捷操作。
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.memory_body),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 有输入文本时提供一键清空图标按钮。
        if (hasText) {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClear()
                },
                enabled = enabled,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.prompt_editor_clear),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        // 编辑模式提供重新读取已保存版本的快捷按钮。
        if (onReload != null) {
            MemoryReloadPill(
                enabled = enabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onReload()
                },
            )
        }
    }
}

@Composable
private fun MemoryReloadPill(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    // 使用轻量圆角胶囊承载重新读取入口。
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        // 图标与文字紧凑水平排列。
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(R.string.memory_reload),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MemoryStatusSegmentedSelector(
    selectedStatus: MemoryStatus,
    enabled: Boolean,
    onSelect: (MemoryStatus) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.memory_status),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 三项互斥状态采用等宽分段切换条，带语义圆点便于快速分辨。
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
            ),
        ) {
            // 均分宽度并挂载单选分组无障碍语义。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MemoryStatus.entries.forEach { status ->
                    MemoryStatusSegmentTab(
                        status = status,
                        isSelected = status == selectedStatus,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (status != selectedStatus) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(status)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryStatusSegmentTab(
    status: MemoryStatus,
    isSelected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // 选中态抬高卡片表面并启用状态色边框，未选中态透明贴合轨道底色。
    val dotColor = memoryStatusAccentColor(status)
    Surface(
        modifier = modifier.semantics {
            selected = isSelected
            role = Role.Tab
        },
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
        border = if (isSelected) {
            BorderStroke(width = 1.dp, color = dotColor.copy(alpha = 0.45f))
        } else {
            null
        },
        shadowElevation = if (isSelected) 1.dp else 0.dp,
    ) {
        // 居中排布状态色点与单行状态文案。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(
                space = 5.dp,
                alignment = Alignment.CenterHorizontally,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(dotColor, CircleShape),
            )
            Text(
                text = stringResource(memoryStatusLabelRes(status)),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MemoryKindGridSelector(
    selectedKind: MemoryKind,
    enabled: Boolean,
    onSelect: (MemoryKind) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val rows = remember { MemoryKind.entries.chunked(3) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.memory_kind),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 六个记忆类别固定按三列两行等宽排布，避免流式胶囊高低错落。
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { rowKinds ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowKinds.forEach { kind ->
                        val isSelected = kind == selectedKind
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .semantics {
                                    selected = isSelected
                                    role = Role.RadioButton
                                },
                            onClick = {
                                if (!isSelected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelect(kind)
                                }
                            },
                            enabled = enabled,
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
                            border = BorderStroke(
                                width = if (isSelected) 1.dp else 0.5.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                },
                            ),
                        ) {
                            // 单行居中显示记忆类别文案，过长时省略号截断。
                            Text(
                                text = stringResource(memoryKindLabelRes(kind)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 9.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryPriorityPanel(
    importance: Int,
    pinned: Boolean,
    enabled: Boolean,
    onImportanceChange: (Int) -> Unit,
    onPinnedChange: (Boolean) -> Unit,
) {
    // 重要度与置顶归拢在同一属性分组卡中，与项目设置页卡片层次一致。
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 上半区提供 0~100 重要度数值展示与滑块调节。
            MemoryImportanceSliderBlock(
                importance = importance,
                enabled = enabled,
                onImportanceChange = onImportanceChange,
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 14.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
            )
            // 下半区提供整行可点的置顶开关。
            MemoryPinnedSwitchRow(
                pinned = pinned,
                enabled = enabled,
                onPinnedChange = onPinnedChange,
            )
        }
    }
}

@Composable
private fun MemoryImportanceSliderBlock(
    importance: Int,
    enabled: Boolean,
    onImportanceChange: (Int) -> Unit,
) {
    // 重要度标题行与滑块上下排布，调节结果约束在 0..100 范围内。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // 左侧星标图标配合本地化重要度文案。
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = AccentAmberColor,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.memory_importance, importance),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        // 使用应用统一配色滑块更新整数重要度。
        Slider(
            value = importance.toFloat(),
            onValueChange = { value ->
                onImportanceChange(value.roundToInt().coerceIn(0, 100))
            },
            valueRange = 0f..100f,
            enabled = enabled,
            colors = pageSliderColors(),
        )
    }
}

@Composable
private fun MemoryPinnedSwitchRow(
    pinned: Boolean,
    enabled: Boolean,
    onPinnedChange: (Boolean) -> Unit,
) {
    // 整行可点击切换置顶状态，图钉图标随状态切换主色。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onPinnedChange(!pinned) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.PushPin,
            contentDescription = null,
            tint = if (pinned) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(18.dp),
        )
        // 中间标题与右侧开关组件。
        Text(
            text = stringResource(R.string.memory_pinned),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Switch(
            checked = pinned,
            onCheckedChange = onPinnedChange,
            enabled = enabled,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryScopeSection(
    allowScopeSelection: Boolean,
    showAudienceScope: Boolean,
    personaId: Long?,
    privateCharacterId: Long?,
    personaOptions: List<MemoryDialogOption>,
    audienceOptions: List<MemoryDialogOption>,
    fixedPersonaLabel: String,
    fixedAudienceLabel: String?,
    enabled: Boolean,
    onPersonaSelect: (Long?) -> Unit,
    onAudienceSelect: (Long?) -> Unit,
) {
    if (allowScopeSelection) {
        // 新建非覆盖条目时允许显式指定适用人设与会话可见范围。
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogScopeDropdown(
                label = stringResource(R.string.memory_persona),
                leadingIcon = Icons.Rounded.Person,
                selectedId = personaId,
                options = personaOptions,
                enabled = enabled,
                onSelect = onPersonaSelect,
            )
            if (showAudienceScope) {
                DialogScopeDropdown(
                    label = stringResource(R.string.memory_audience),
                    leadingIcon = Icons.Rounded.Visibility,
                    selectedId = privateCharacterId,
                    options = audienceOptions,
                    enabled = enabled,
                    onSelect = onAudienceSelect,
                )
            }
        }
    } else {
        // 编辑已有条目或局部覆盖时固定归属，以轻盈信息条提示当前适用范围。
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
            border = BorderStroke(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
            ),
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FixedScopeChip(
                    icon = Icons.Rounded.Person,
                    label = fixedPersonaLabel,
                )
                if (!fixedAudienceLabel.isNullOrBlank()) {
                    FixedScopeChip(
                        icon = Icons.Rounded.Visibility,
                        label = fixedAudienceLabel,
                    )
                }
            }
        }
    }
}

@Composable
private fun FixedScopeChip(
    icon: ImageVector,
    label: String,
) {
    // 图标与单行固定归属标签紧凑排列。
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogScopeDropdown(
    label: String,
    leadingIcon: ImageVector,
    selectedId: Long?,
    options: List<MemoryDialogOption>,
    enabled: Boolean,
    onSelect: (Long?) -> Unit,
) {
    // 管理下拉展开临时状态并解析当前选中选项展示文本。
    var expanded by remember(enabled) { mutableStateOf(false) }
    val displayText = options.firstOrNull { it.id == selectedId }?.label
        ?: stringResource(R.string.memory_choose)
    // 锚定只读外框输入框并展开可选归属列表。
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it && enabled },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            label = { Text(text = label) },
            leadingIcon = {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = enabled,
                ),
        )
        // 遍历渲染下拉菜单项并高亮当前已选项。
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                val isCurrent = option.id == selectedId
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    trailingIcon = if (isCurrent) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(option.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun memoryStatusAccentColor(status: MemoryStatus): Color = when (status) {
    MemoryStatus.Active -> AccentEmeraldColor
    MemoryStatus.Disputed -> AccentAmberColor
    MemoryStatus.Invalidated -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun memoryKindLabelRes(kind: MemoryKind): Int = when (kind) {
    MemoryKind.fact -> R.string.memory_kind_fact
    MemoryKind.event -> R.string.memory_kind_event
    MemoryKind.preference -> R.string.memory_kind_preference
    MemoryKind.promise -> R.string.memory_kind_promise
    MemoryKind.relationship -> R.string.memory_kind_relationship
    MemoryKind.clue -> R.string.memory_kind_clue
}

private fun memoryStatusLabelRes(status: MemoryStatus): Int = when (status) {
    MemoryStatus.Active -> R.string.memory_active
    MemoryStatus.Disputed -> R.string.memory_disputed
    MemoryStatus.Invalidated -> R.string.memory_invalidated
}

private const val MAX_MEMORY_BODY_LENGTH = 32768

@Preview(name = "MemoryEntryEditorDialog - Light", showBackground = true, locale = "zh")
@Composable
private fun MemoryEntryEditorDialogLightPreview() {
    // 浅色主题下预览编辑已有条目的固定范围展示态。
    AppTheme(dynamicColor = false) {
        Box(modifier = Modifier.fillMaxSize()) {
            MemoryEntryEditorDialog(
                onDismissRequest = {},
                draft = MemoryDraft(
                    body = "Alex 喝茶不加糖，平时更喜欢乌龙茶和焙茶。",
                    kind = MemoryKind.preference,
                    status = MemoryStatus.Active,
                    importance = 80,
                    pinned = true,
                ),
                isEditing = true,
                allowScopeSelection = false,
                showAudienceScope = true,
                personaOptions = emptyList(),
                audienceOptions = emptyList(),
                fixedPersonaLabel = "不限人设",
                fixedAudienceLabel = "本会话共享",
                isSaving = false,
                onDraftChange = {},
                onReload = {},
                onSave = {},
            )
        }
    }
}

@Preview(name = "MemoryEntryEditorDialog - Create Dark", showBackground = true, locale = "zh")
@Composable
private fun MemoryEntryEditorDialogDarkPreview() {
    // 深色主题下预览新建条目的可选范围表单态。
    AppTheme(darkTheme = true, dynamicColor = false) {
        Box(modifier = Modifier.fillMaxSize()) {
            MemoryEntryEditorDialog(
                onDismissRequest = {},
                draft = MemoryDraft(
                    body = "",
                    kind = MemoryKind.fact,
                    status = MemoryStatus.Active,
                    importance = 50,
                    pinned = false,
                ),
                isEditing = false,
                allowScopeSelection = true,
                showAudienceScope = true,
                personaOptions = listOf(
                    MemoryDialogOption(id = null, label = "不限人设"),
                    MemoryDialogOption(id = 1L, label = "Alex"),
                ),
                audienceOptions = listOf(
                    MemoryDialogOption(id = null, label = "本会话共享"),
                    MemoryDialogOption(id = 10L, label = "Lyra"),
                ),
                fixedPersonaLabel = "不限人设",
                fixedAudienceLabel = "本会话共享",
                isSaving = false,
                onDraftChange = {},
                onReload = null,
                onSave = {},
            )
        }
    }
}
