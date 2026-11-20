package me.kafuuneko.rpclient.feature.memoryedit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.memoryedit.model.MemoryEditRevisionUiModel
import me.kafuuneko.rpclient.feature.memoryedit.model.MemoryEditSourceUiModel
import me.kafuuneko.rpclient.feature.memoryedit.presentation.MemoryEditDialogState
import me.kafuuneko.rpclient.feature.memoryedit.presentation.MemoryEditUiIntent
import me.kafuuneko.rpclient.feature.memoryedit.presentation.MemoryEditUiState
import me.kafuuneko.rpclient.libs.memory.MemoryKind
import me.kafuuneko.rpclient.libs.memory.MemoryStatus
import me.kafuuneko.rpclient.libs.memory.MemoryTarget
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.dialog.AppDialogScaffold
import me.kafuuneko.rpclient.ui.dialog.DialogBadgeTone
import me.kafuuneko.rpclient.ui.dialog.MemoryDialogOption
import me.kafuuneko.rpclient.ui.dialog.MemoryEntryEditorDialog
import me.kafuuneko.rpclient.ui.theme.AccentAmberColor
import me.kafuuneko.rpclient.ui.theme.AccentEmeraldColor
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.RpMetaPill
import me.kafuuneko.rpclient.ui.widgets.RpMetaRow
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField

/** 记忆编辑页互斥弹窗分发入口，复用应用级编辑与确认对话框。 */
@Composable
internal fun DialogSwitch(
    state: MemoryEditUiState.Normal,
    emit: MemoryEditUiIntent.() -> Unit,
) {
    // 危险操作与表单弹窗互斥，提交期间禁用重复确认。
    when (val dialog = state.dialog) {
        MemoryEditDialogState.None -> Unit
        is MemoryEditDialogState.Edit -> EditEntryDialog(
            state = state,
            dialog = dialog,
            emit = emit
        )

        is MemoryEditDialogState.Delete -> AppDangerDialog(
            onDismissRequest = { MemoryEditUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.delete),
            message = stringResource(R.string.memory_delete_confirm, dialog.entry.body),
            confirmEnabled = !state.busy,
            isConfirmLoading = state.busy,
            onConfirm = { MemoryEditUiIntent.ConfirmDelete.emit() },
        )

        is MemoryEditDialogState.Discard -> AppDangerDialog(
            onDismissRequest = { MemoryEditUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.discard_changes),
            message = stringResource(R.string.memory_discard),
            onConfirm = { MemoryEditUiIntent.ConfirmDiscard.emit() },
        )

        is MemoryEditDialogState.History -> HistoryDialog(dialog = dialog, emit = emit)
        is MemoryEditDialogState.Scope -> ScopeDialog(state = state, dialog = dialog, emit = emit)
        is MemoryEditDialogState.Promote -> PromoteDialog(
            state = state,
            dialog = dialog,
            emit = emit
        )

        is MemoryEditDialogState.Import -> ImportDialog(state = state, dialog = dialog, emit = emit)
    }
}

@Composable
private fun EditEntryDialog(
    state: MemoryEditUiState.Normal,
    dialog: MemoryEditDialogState.Edit,
    emit: MemoryEditUiIntent.() -> Unit,
) {
    // 准备新建或编辑模式的范围下拉选项与固定范围文案。
    val isEditing = dialog.entry != null
    val showAudience = state.target !is MemoryTarget.Character
    val personaOptions = listOf(
        MemoryDialogOption(id = null, label = stringResource(R.string.memory_any_persona)),
    ) + state.personas.map { owner -> MemoryDialogOption(id = owner.id, label = owner.name) }
    val audienceOptions = listOf(
        MemoryDialogOption(id = null, label = stringResource(R.string.memory_shared)),
    ) + state.settings?.characters.orEmpty()
        .map { owner -> MemoryDialogOption(id = owner.id, label = owner.name) }
    // 复用公共条目编辑弹窗并将全部交互转为页面意图。
    MemoryEntryEditorDialog(
        onDismissRequest = { MemoryEditUiIntent.DismissDialog.emit() },
        draft = dialog.draft,
        isEditing = isEditing,
        allowScopeSelection = !isEditing && dialog.draft.overrideCharacterMemoryId == null,
        showAudienceScope = showAudience,
        personaOptions = personaOptions,
        audienceOptions = audienceOptions,
        fixedPersonaLabel = personaLabel(dialog.draft.personaId, state),
        fixedAudienceLabel = if (showAudience) {
            audienceLabel(dialog.draft.privateCharacterId, state)
        } else {
            null
        },
        isSaving = state.busy,
        onDraftChange = { draft -> MemoryEditUiIntent.ChangeDraft(draft).emit() },
        onReload = if (isEditing) {
            { MemoryEditUiIntent.Reload.emit() }
        } else {
            null
        },
        onSave = { MemoryEditUiIntent.Save.emit() },
    )
}

@Composable
private fun HistoryDialog(
    dialog: MemoryEditDialogState.History,
    emit: MemoryEditUiIntent.() -> Unit,
) {
    // 展示不可变修订时间线，仅提供关闭按钮避免误操作。
    AppDialogScaffold(
        onDismissRequest = { MemoryEditUiIntent.DismissDialog.emit() },
        title = stringResource(R.string.memory_history),
        badgeIcon = Icons.Rounded.History,
        badgeTone = DialogBadgeTone.Primary,
        compactHeader = true,
        confirmText = stringResource(R.string.close),
        dismissText = null,
        onConfirm = { MemoryEditUiIntent.DismissDialog.emit() },
        scrollableContent = true,
    ) {
        // 按版本号倒序渲染每版快照与证据引用。
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            dialog.revisions.forEach { revision ->
                RevisionCard(revision = revision)
            }
        }
    }
}

@Composable
private fun RevisionCard(revision: MemoryEditRevisionUiModel) {
    // 按修订状态决定语义圆点配色。
    val dotColor = when (revision.status) {
        MemoryStatus.Active -> AccentEmeraldColor
        MemoryStatus.Disputed -> AccentAmberColor
        MemoryStatus.Invalidated -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // 渲染单版修订卡片，包含标题行、正文及关联证据列表。
    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 顶部汇总版本号、状态文本与记忆分类胶囊。
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(dotColor, CircleShape),
                )
                Text(
                    text = stringResource(
                        R.string.memory_revision,
                        revision.revision,
                        stringResource(statusLabel(revision.status)),
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                RpMetaPill(text = stringResource(kindLabel(revision.kind)))
            }
            Text(
                text = revision.body,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            // 逐条渲染该修订记录下的证据来源与摘录。
            revision.sources.forEach { source ->
                RevisionSourceQuote(source = source)
            }
        }
    }
}

@Composable
private fun RevisionSourceQuote(source: MemoryEditSourceUiModel) {
    // 证据以左竖线引用块呈现，清晰区分修订正文与原始出处。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 左侧引用竖线指示来源区块边界。
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        )
        // 右侧展示本地化来源类型及非空原文摘录。
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(sourceKindLabel(source.sourceKind)),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (source.quote.isNotBlank()) {
                Text(
                    text = source.quote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScopeDialog(
    state: MemoryEditUiState.Normal,
    dialog: MemoryEditDialogState.Scope,
    emit: MemoryEditUiIntent.() -> Unit,
) {
    // 独立范围调整弹窗要求显式确认可见性扩大或收缩。
    AppDialogScaffold(
        onDismissRequest = { MemoryEditUiIntent.DismissDialog.emit() },
        title = stringResource(R.string.memory_scope),
        badgeIcon = Icons.Rounded.Visibility,
        badgeTone = DialogBadgeTone.Warning,
        compactHeader = true,
        confirmEnabled = !state.busy,
        isConfirmLoading = state.busy,
        onConfirm = { MemoryEditUiIntent.ConfirmScope.emit() },
        scrollableContent = true,
    ) {
        // 上方展示可见性提示，下方提供人设与受众下拉框。
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DialogNoticeBox(text = stringResource(R.string.memory_scope_confirm))
            ScopeFields(
                state = state,
                personaId = dialog.personaId,
                privateId = dialog.privateId,
                enabled = !state.busy,
                onChange = { persona, privateId ->
                    MemoryEditUiIntent.ChangeScope(persona, privateId).emit()
                },
            )
        }
    }
}

@Composable
private fun PromoteDialog(
    state: MemoryEditUiState.Normal,
    dialog: MemoryEditDialogState.Promote,
    emit: MemoryEditUiIntent.() -> Unit,
) {
    // 可选角色已由 ViewModel 按私有受众预筛，此处直接映射为下拉选项。
    val characterOptions =
        dialog.eligibleCharacters.map { owner -> owner.id as Long? to owner.name }
    // 渲染提升至角色通用范围的确认弹窗。
    AppDialogScaffold(
        onDismissRequest = { MemoryEditUiIntent.DismissDialog.emit() },
        title = stringResource(R.string.memory_promote),
        badgeIcon = Icons.Rounded.NorthEast,
        badgeTone = DialogBadgeTone.Primary,
        compactHeader = true,
        confirmEnabled = !state.busy && dialog.characterId != null,
        isConfirmLoading = state.busy,
        onConfirm = { MemoryEditUiIntent.ConfirmPromotion.emit() },
        scrollableContent = true,
    ) {
        // 展示跨会话影响提示、当前适用人设与目标角色选择器。
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DialogNoticeBox(text = stringResource(R.string.memory_promote_confirm))
            RpMetaRow(
                items = listOf(
                    stringResource(
                        R.string.memory_filter_value,
                        stringResource(R.string.memory_persona),
                        personaLabel(dialog.entry.personaId, state),
                    ),
                ),
            )
            MemoryDropdownField(
                label = stringResource(R.string.memory_target),
                selected = dialog.characterId,
                options = characterOptions,
                enabled = !state.busy,
                onSelect = { characterId ->
                    if (characterId != null) {
                        MemoryEditUiIntent.ChangePromotion(characterId).emit()
                    }
                },
            )
        }
    }
}

@Composable
private fun ImportDialog(
    state: MemoryEditUiState.Normal,
    dialog: MemoryEditDialogState.Import,
    emit: MemoryEditUiIntent.() -> Unit,
) {
    // 仅当全部外部人设和成员完成显式映射后才允许确认导入。
    AppDialogScaffold(
        onDismissRequest = { MemoryEditUiIntent.DismissDialog.emit() },
        title = stringResource(R.string.memory_import),
        badgeIcon = Icons.Rounded.FileDownload,
        badgeTone = DialogBadgeTone.Primary,
        compactHeader = true,
        confirmEnabled = !state.busy && dialog.isComplete,
        isConfirmLoading = state.busy,
        onConfirm = { MemoryEditUiIntent.ConfirmImport.emit() },
        scrollableContent = true,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 提示本次导入目标及待导入条目总数。
            DialogNoticeBox(
                text = stringResource(
                    R.string.memory_import_confirm,
                    state.title,
                    dialog.entryCount,
                ),
            )
            // 外部名称只作为待映射标签，选择值始终是本机真实 ID。
            dialog.requiredPersonas.forEach { mappingItem ->
                MemoryDropdownField(
                    label = stringResource(R.string.memory_map_persona, mappingItem.label),
                    selected = dialog.personas[mappingItem.sourceId],
                    options = state.personas.map { owner -> owner.id as Long? to owner.name },
                    enabled = !state.busy,
                    onSelect = { targetId ->
                        if (targetId != null) {
                            MemoryEditUiIntent.MapPersona(mappingItem.sourceId, targetId).emit()
                        }
                    },
                )
            }
            dialog.requiredMembers.forEach { mappingItem ->
                MemoryDropdownField(
                    label = stringResource(R.string.memory_map_member, mappingItem.label),
                    selected = dialog.members[mappingItem.sourceId],
                    options = state.settings?.characters.orEmpty()
                        .map { owner -> owner.id as Long? to owner.name },
                    enabled = !state.busy,
                    onSelect = { targetId ->
                        if (targetId != null) {
                            MemoryEditUiIntent.MapMember(mappingItem.sourceId, targetId).emit()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DialogNoticeBox(text: String) {
    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScopeFields(
    state: MemoryEditUiState.Normal,
    personaId: Long?,
    privateId: Long?,
    enabled: Boolean,
    onChange: (Long?, Long?) -> Unit,
) {
    // 适用人设下拉字段，空值表示不限人设。
    MemoryDropdownField(
        label = stringResource(R.string.memory_persona),
        selected = personaId,
        options = listOf(null to stringResource(R.string.memory_any_persona)) +
            state.personas.map { owner -> owner.id as Long? to owner.name },
        enabled = enabled,
        onSelect = { chosen -> onChange(chosen, privateId) },
    )
    // 会话范围额外展示共享或成员私有受众选择。
    if (state.target !is MemoryTarget.Character) {
        MemoryDropdownField(
            label = stringResource(R.string.memory_audience),
            selected = privateId,
            options = listOf(null to stringResource(R.string.memory_shared)) +
                state.settings?.characters.orEmpty()
                    .map { owner -> owner.id as Long? to owner.name },
            enabled = enabled,
            onSelect = { chosen -> onChange(personaId, chosen) },
        )
    }
}

/** 与主设置和人设选择器统一的表单下拉字段，展开态仅限临时控件状态。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> MemoryDropdownField(
    label: String,
    selected: T,
    options: List<Pair<T, String>>,
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    // 维护下拉展开临时态并解析当前选中展示文本。
    var expanded by remember(enabled) { mutableStateOf(false) }
    val displayText = options.firstOrNull { it.first == selected }?.second
        ?: stringResource(R.string.memory_choose)
    // 锚定只读文本输入框并在展开时展示可选列表。
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
            shape = RoundedCornerShape(12.dp),
            label = { Text(text = label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = enabled,
                ),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (value, text) ->
                val isCurrent = value == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            text = text,
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
                        onSelect(value)
                    },
                )
            }
        }
    }
}

@Composable
internal fun personaLabel(
    id: Long?,
    state: MemoryEditUiState.Normal,
): String = if (id == null) {
    stringResource(R.string.memory_any_persona)
} else {
    state.personas.firstOrNull { it.id == id }?.name
        ?: stringResource(R.string.memory_target_missing)
}

@Composable
internal fun audienceLabel(
    privateCharacterId: Long?,
    state: MemoryEditUiState.Normal,
): String = if (privateCharacterId == null) {
    stringResource(R.string.memory_shared)
} else {
    state.settings?.characters.orEmpty().firstOrNull { it.id == privateCharacterId }?.name
        ?: stringResource(R.string.memory_target_missing)
}

internal fun kindLabel(kind: MemoryKind): Int = when (kind) {
    MemoryKind.fact -> R.string.memory_kind_fact
    MemoryKind.event -> R.string.memory_kind_event
    MemoryKind.preference -> R.string.memory_kind_preference
    MemoryKind.promise -> R.string.memory_kind_promise
    MemoryKind.relationship -> R.string.memory_kind_relationship
    MemoryKind.clue -> R.string.memory_kind_clue
}

internal fun statusLabel(status: MemoryStatus): Int = when (status) {
    MemoryStatus.Active -> R.string.memory_active
    MemoryStatus.Disputed -> R.string.memory_disputed
    MemoryStatus.Invalidated -> R.string.memory_invalidated
}

private fun sourceKindLabel(sourceKind: String): Int = when (sourceKind) {
    "UserEdit" -> R.string.memory_user_source
    "External" -> R.string.memory_external_source
    "PromotedSnapshot" -> R.string.memory_promoted_snapshot_source
    "ChatSummary", "GroupSummary" -> R.string.memory_summary_source
    else -> R.string.memory_message_source
}
