package me.kafuuneko.rpclient.feature.memoryedit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.memoryedit.model.MemoryEditEntryItem
import me.kafuuneko.rpclient.feature.memoryedit.model.MemoryEditOwnerUiModel
import me.kafuuneko.rpclient.feature.memoryedit.model.MemoryEditSettingsUiModel
import me.kafuuneko.rpclient.feature.memoryedit.presentation.MemoryEditContentState
import me.kafuuneko.rpclient.feature.memoryedit.presentation.MemoryEditTab
import me.kafuuneko.rpclient.feature.memoryedit.presentation.MemoryEditUiIntent
import me.kafuuneko.rpclient.feature.memoryedit.presentation.MemoryEditUiState
import me.kafuuneko.rpclient.libs.memory.MemoryKind
import me.kafuuneko.rpclient.libs.memory.MemoryRef
import me.kafuuneko.rpclient.libs.memory.MemoryStatus
import me.kafuuneko.rpclient.libs.memory.MemoryTable
import me.kafuuneko.rpclient.libs.memory.MemoryTarget
import me.kafuuneko.rpclient.ui.theme.AccentAmberColor
import me.kafuuneko.rpclient.ui.theme.AccentEmeraldColor
import me.kafuuneko.rpclient.ui.theme.AccentIndigoColor
import me.kafuuneko.rpclient.ui.theme.AccentSkyColor
import me.kafuuneko.rpclient.ui.theme.AccentVioletColor
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppCard
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpMetaPill
import me.kafuuneko.rpclient.ui.widgets.RpMetaRow
import me.kafuuneko.rpclient.ui.widgets.RpPanel
import me.kafuuneko.rpclient.ui.widgets.RpSegmentedTabs
import me.kafuuneko.rpclient.ui.widgets.RpSettingsDivider
import me.kafuuneko.rpclient.ui.widgets.RpSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpSettingsSwitchTile
import me.kafuuneko.rpclient.ui.widgets.RpTagPill
import me.kafuuneko.rpclient.ui.widgets.pageCanvasColor
import me.kafuuneko.rpclient.ui.widgets.pageFilterChipColors
import me.kafuuneko.rpclient.ui.widgets.PageButton as Button
import me.kafuuneko.rpclient.ui.widgets.PageFilterChip as FilterChip

/** 三种记忆范围共用的编辑器 Compose 入口，只展示状态并发出意图。 */
@Composable
fun MemoryEditLayout(
    state: MemoryEditUiState,
    emit: MemoryEditUiIntent.() -> Unit,
) {
    BackHandler(enabled = state is MemoryEditUiState.Normal) {
        MemoryEditUiIntent.Back.emit()
    }
    when (state) {
        MemoryEditUiState.None -> Unit
        is MemoryEditUiState.Finished -> MemoryEditLayout(state = state.previous, emit = {})
        is MemoryEditUiState.Normal -> {
            NormalView(state = state, emit = emit)
            DialogSwitch(state = state, emit = emit)
        }
    }
}

@Composable
private fun NormalView(
    state: MemoryEditUiState.Normal,
    emit: MemoryEditUiIntent.() -> Unit,
) {
    // 汇总加载完成态、交互使能态与当前范围分类。
    val ready = !state.content.loading && !state.content.failed
    val enabled = ready && !state.busy && !state.choosingDocument
    val isSessionTarget = state.target !is MemoryTarget.Character
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageCanvasColor()),
    ) {
        // 顶栏提供返回、新建及导入导出更多菜单。
        AppTopBar(
            title = stringResource(R.string.memory_editor),
            onBack = { MemoryEditUiIntent.Back.emit() },
            actions = {
                IconButton(
                    onClick = { MemoryEditUiIntent.Create.emit() },
                    enabled = enabled,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.add),
                    )
                }
                TopBarTransferMenu(enabled = enabled, emit = emit)
            },
        )
        // 所有者身份、会话开关与内容分页自上而下组织，加载完成后筛选栏保持常驻不跳动。
        RpLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = 18.dp,
                top = 12.dp,
                end = 18.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.title.isNotBlank()) {
                item(key = "scope_header") { ScopeHeaderCard(state = state) }
            }
            if (state.content.loading) {
                item(key = "loading") { LoadingPanel() }
            } else if (state.content.failed) {
                item(key = "failed") { FailedPanel(onRetry = { MemoryEditUiIntent.Refresh.emit() }) }
            } else {
                memoryContentItems(
                    state = state,
                    isSessionTarget = isSessionTarget,
                    enabled = enabled,
                    emit = emit,
                )
            }
        }
    }
}

private fun LazyListScope.memoryContentItems(
    state: MemoryEditUiState.Normal,
    isSessionTarget: Boolean,
    enabled: Boolean,
    emit: MemoryEditUiIntent.() -> Unit,
) {
    // 会话范围先渲染继承/召回开关卡片与分段切换条。
    if (isSessionTarget && state.settings != null) {
        item(key = "session_settings") {
            SessionMemorySettingsCard(
                settings = state.settings,
                enabled = enabled,
                emit = emit,
            )
        }
        item(key = "tabs") {
            SessionMemoryTabs(
                content = state.content,
                onSelect = { tab -> MemoryEditUiIntent.SelectTab(tab).emit() },
            )
        }
    }
    // 角色范围直接展示条目列表；会话范围根据当前分段在自有条目与只读继承之间切换。
    if (!isSessionTarget || state.content.tab == MemoryEditTab.Scope) {
        item(key = "filters") {
            MemoryFilterSection(
                state = state,
                enabled = enabled,
                emit = emit,
            )
        }
        if (state.content.entries.isEmpty()) {
            item(key = "empty_scope") {
                EmptyScopePanel(
                    enabled = enabled,
                    onCreate = { MemoryEditUiIntent.Create.emit() },
                )
            }
        } else {
            items(
                items = state.content.entries,
                key = { entry -> entry.uuid },
            ) { entry ->
                EntryCard(
                    entry = entry,
                    state = state,
                    enabled = enabled,
                    emit = emit,
                )
            }
        }
    } else {
        item(key = "inherited_notice") {
            InheritedNoticeBanner(inheritEnabled = state.settings?.inherit != false)
        }
        if (state.content.inherited.isEmpty()) {
            item(key = "empty_inherited") { EmptyInheritedPanel() }
        } else {
            items(
                items = state.content.inherited,
                key = { item -> "inherited/${item.entry.uuid}" },
            ) { item ->
                InheritedEntryCard(
                    owner = item.owner,
                    entry = item.entry,
                    state = state,
                    enabled = enabled,
                    onOverride = { MemoryEditUiIntent.Override(item.entry).emit() },
                )
            }
        }
    }
}

@Composable
private fun TopBarTransferMenu(
    enabled: Boolean,
    emit: MemoryEditUiIntent.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // 导入与导出统一收拢在更多菜单内，图标方向与全应用保持一致。
    Box {
        // 顶栏更多操作入口按钮。
        IconButton(
            onClick = { expanded = true },
            enabled = enabled,
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.more),
            )
        }
        // 下拉菜单包含独立记忆包的导入与导出入口。
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.memory_import)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.FileDownload,
                        contentDescription = null,
                    )
                },
                onClick = {
                    expanded = false
                    MemoryEditUiIntent.ImportClick.emit()
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.memory_export)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.FileUpload,
                        contentDescription = null,
                    )
                },
                onClick = {
                    expanded = false
                    MemoryEditUiIntent.ExportClick.emit()
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScopeHeaderCard(state: MemoryEditUiState.Normal) {
    // 按角色通用、单聊与群聊三种范围映射头部图标、强调色和范围标签。
    val (icon, iconColor, scopeLabelRes) = when (state.target) {
        is MemoryTarget.Character -> Triple(
            Icons.Rounded.Person,
            AccentIndigoColor,
            R.string.memory_scope_character,
        )

        is MemoryTarget.Chat -> Triple(
            Icons.Rounded.ChatBubble,
            AccentSkyColor,
            R.string.prompt_scope_chat,
        )

        is MemoryTarget.Group -> Triple(
            Icons.Rounded.Groups,
            AccentVioletColor,
            R.string.prompt_scope_group,
        )
    }
    // 顶部身份卡展示目标标题、范围分类与总条数。
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RpIconBubble(
                icon = icon,
                size = 44.dp,
                contentColor = iconColor,
                containerColor = iconColor.copy(alpha = 0.14f),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RpMetaPill(text = stringResource(scopeLabelRes))
                    RpTagPill(
                        text = stringResource(
                            R.string.memory_count,
                            state.content.totalCount
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionMemorySettingsCard(
    settings: MemoryEditSettingsUiModel,
    enabled: Boolean,
    emit: MemoryEditUiIntent.() -> Unit,
) {
    // 将会话级记忆继承与普通回复召回开关归拢在同一设置分组卡内。
    RpSettingsGroup {
        // 控制当前会话是否继承角色通用记忆。
        RpSettingsSwitchTile(
            icon = Icons.Rounded.Link,
            iconColor = AccentIndigoColor,
            iconContainerColor = AccentIndigoColor.copy(alpha = 0.14f),
            title = stringResource(R.string.memory_inherit),
            checked = settings.inherit,
            enabled = enabled,
            onCheckedChange = { checked ->
                MemoryEditUiIntent.ChangeSettings(checked, settings.recall).emit()
            },
        )
        RpSettingsDivider()
        // 控制普通回复是否在剩余预算内召回记忆。
        RpSettingsSwitchTile(
            icon = Icons.Rounded.Psychology,
            iconColor = AccentEmeraldColor,
            iconContainerColor = AccentEmeraldColor.copy(alpha = 0.14f),
            title = stringResource(R.string.memory_recall),
            checked = settings.recall,
            enabled = enabled,
            onCheckedChange = { checked ->
                MemoryEditUiIntent.ChangeSettings(settings.inherit, checked).emit()
            },
        )
    }
}

@Composable
private fun SessionMemoryTabs(
    content: MemoryEditContentState,
    onSelect: (MemoryEditTab) -> Unit,
) {
    // 复用应用级分段切换条展示当前范围与只读继承条目数量。
    RpSegmentedTabs(
        tabs = MemoryEditTab.entries,
        selectedTab = content.tab,
        onTabSelected = onSelect,
        tabLabel = { tab ->
            when (tab) {
                MemoryEditTab.Scope -> stringResource(
                    R.string.memory_tab_current,
                    content.totalCount
                )

                MemoryEditTab.Inherited -> stringResource(
                    R.string.memory_tab_inherited,
                    content.inherited.size,
                )
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryFilterSection(
    state: MemoryEditUiState.Normal,
    enabled: Boolean,
    emit: MemoryEditUiIntent.() -> Unit,
) {
    val filter = state.content
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // 固定状态选项使用直接点选的筛选胶囊，减少不必要的二级菜单。
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val statusOptions = listOf<MemoryStatus?>(null) + MemoryStatus.entries
            statusOptions.forEach { option ->
                val selected = filter.status == option
                MemoryFilterChip(
                    selected = selected,
                    enabled = enabled,
                    label = stringResource(
                        if (option == null) R.string.memory_all else statusLabel(option),
                    ),
                    onClick = {
                        MemoryEditUiIntent.Filter(option, filter.persona, filter.audience).emit()
                    },
                )
            }
        }
        // 人设与可见范围按当前范围动态变化，使用带下拉箭头的紧凑筛选胶囊。
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterDropdownChip(
                label = stringResource(R.string.memory_persona),
                selected = filter.persona,
                isDefaultSelection = filter.persona == -1L,
                enabled = enabled,
                options = listOf(
                    -1L to stringResource(R.string.memory_all),
                    0L to stringResource(R.string.memory_any_persona),
                ) + state.personas.map { owner -> owner.id to owner.name },
                onSelect = { personaId ->
                    MemoryEditUiIntent.Filter(filter.status, personaId, filter.audience).emit()
                },
            )
            if (state.target !is MemoryTarget.Character) {
                FilterDropdownChip(
                    label = stringResource(R.string.memory_audience),
                    selected = filter.audience,
                    isDefaultSelection = filter.audience == -1L,
                    enabled = enabled,
                    options = listOf(
                        -1L to stringResource(R.string.memory_all),
                        0L to stringResource(R.string.memory_shared),
                    ) + state.settings?.characters.orEmpty()
                        .map { owner -> owner.id to owner.name },
                    onSelect = { audienceId ->
                        MemoryEditUiIntent.Filter(filter.status, filter.persona, audienceId).emit()
                    },
                )
            }
        }
    }
}

@Composable
private fun <T> FilterDropdownChip(
    label: String,
    selected: T,
    isDefaultSelection: Boolean,
    enabled: Boolean,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    // 维护筛选下拉菜单展开状态并解析当前展示文本。
    var expanded by remember { mutableStateOf(false) }
    val selectedText = options.firstOrNull { it.first == selected }?.second
        ?: stringResource(R.string.memory_all)
    val highlighted = !isDefaultSelection
    // 组合筛选胶囊与下拉选项菜单。
    Box {
        FilterChip(
            selected = highlighted,
            onClick = { expanded = true },
            enabled = enabled,
            shape = RoundedCornerShape(10.dp),
            label = {
                Text(
                    text = stringResource(R.string.memory_filter_value, label, selectedText),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            border = FilterChipDefaults.filterChipBorder(
                enabled = enabled,
                selected = highlighted,
                selectedBorderColor = MaterialTheme.colorScheme.primary,
                selectedBorderWidth = 1.dp,
                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f),
            ),
            colors = pageFilterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        FilterDropdownMenu(
            expanded = expanded,
            selected = selected,
            options = options,
            onDismiss = { expanded = false },
            onSelect = { value ->
                expanded = false
                onSelect(value)
            },
        )
    }
}

@Composable
private fun <T> FilterDropdownMenu(
    expanded: Boolean,
    selected: T,
    options: List<Pair<T, String>>,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    // 渲染单选下拉菜单，并为当前生效项附带勾选图标。
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
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
                onClick = { onSelect(value) },
            )
        }
    }
}

private data class EntryCardVisualStyle(
    val accentBarColor: Color,
    val borderStroke: BorderStroke,
    val containerColor: Color,
    val ambientBrush: Brush,
)

@Composable
private fun rememberEntryCardVisualStyle(entry: MemoryEditEntryItem): EntryCardVisualStyle {
    val isInvalidated = entry.status == MemoryStatus.Invalidated
    val isDisputed = entry.status == MemoryStatus.Disputed
    val isPinned = entry.pinned
    // 用左侧状态条和底板色阶区分生效、置顶、待核实与已失效条目。
    val accentBarColor = when {
        isInvalidated -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
        isDisputed -> AccentAmberColor
        isPinned -> MaterialTheme.colorScheme.primary
        else -> AccentEmeraldColor
    }
    val borderStroke = when {
        isDisputed -> BorderStroke(width = 1.dp, color = AccentAmberColor.copy(alpha = 0.45f))
        isPinned && !isInvalidated -> BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        )

        else -> BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
        )
    }
    val containerColor = if (isInvalidated) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val ambientBrush = remember(accentBarColor, isInvalidated) {
        if (isInvalidated) {
            Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
        } else {
            Brush.horizontalGradient(
                colors = listOf(
                    accentBarColor.copy(alpha = 0.06f),
                    accentBarColor.copy(alpha = 0.015f),
                    Color.Transparent,
                ),
            )
        }
    }
    return EntryCardVisualStyle(
        accentBarColor = accentBarColor,
        borderStroke = borderStroke,
        containerColor = containerColor,
        ambientBrush = ambientBrush,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryCard(
    entry: MemoryEditEntryItem,
    state: MemoryEditUiState.Normal,
    enabled: Boolean,
    emit: MemoryEditUiIntent.() -> Unit,
) {
    val visual = rememberEntryCardVisualStyle(entry = entry)
    val isInvalidated = entry.status == MemoryStatus.Invalidated
    val isDisputed = entry.status == MemoryStatus.Disputed
    // 整卡点击进入编辑弹窗，卡片内部依次展示状态行、正文与底行元数据。
    AppCard(
        onClick = { MemoryEditUiIntent.Edit(entry).emit() },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = visual.containerColor),
        border = visual.borderStroke,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(visual.ambientBrush),
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(visual.accentBarColor),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, top = 10.dp, end = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 顶行展示类型、状态、置顶标记与收拢后的更多操作菜单。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RpTagPill(text = stringResource(kindLabel(entry.kind)))
                        StatusPill(status = entry.status)
                        if (entry.pinned) PinnedBadge()
                    }
                    EntryOverflowMenu(
                        entry = entry,
                        showPromote = state.target !is MemoryTarget.Character,
                        enabled = enabled,
                        emit = emit,
                    )
                }
                // 正文区域保持舒适行高，已失效条目降低对比度以退居次要层级。
                Text(
                    text = entry.body,
                    modifier = Modifier.padding(end = 8.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if (isInvalidated) 0.56f else 1f,
                    ),
                )
                EntryCardMetaFooter(
                    entry = entry,
                    state = state,
                    isDisputed = isDisputed,
                    enabled = enabled,
                    emit = emit,
                )
            }
        }
    }
}

@Composable
private fun EntryCardMetaFooter(
    entry: MemoryEditEntryItem,
    state: MemoryEditUiState.Normal,
    isDisputed: Boolean,
    enabled: Boolean,
    emit: MemoryEditUiIntent.() -> Unit,
) {
    // 汇总重要度、人设、可见范围及局部覆盖标记。
    val metaItems = buildList {
        add(stringResource(R.string.memory_importance, entry.importance))
        add(personaLabel(entry.personaId, state))
        if (state.target !is MemoryTarget.Character) {
            add(audienceLabel(entry.privateCharacterId, state))
        }
        if (entry.overrideCharacterMemoryId != null) {
            add(stringResource(R.string.memory_override_badge))
        }
    }
    // 底行左侧展示元数据，待核实条目右侧额外提供快捷确认操作。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            RpMetaRow(items = metaItems)
        }
        if (isDisputed) {
            QuickConfirmBadge(
                enabled = enabled,
                onClick = { MemoryEditUiIntent.Confirm(entry).emit() },
            )
        }
    }
}

@Composable
private fun EntryOverflowMenu(
    entry: MemoryEditEntryItem,
    showPromote: Boolean,
    enabled: Boolean,
    emit: MemoryEditUiIntent.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // 次级与危险操作统一收纳，保持每张卡片的阅读密度与整洁感。
    Box {
        IconButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.more),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        // 按常规操作、可选跨范围提升和危险删除分段排布菜单项。
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val trigger: (MemoryEditUiIntent) -> Unit = { intent ->
                expanded = false
                intent.emit()
            }
            EntryMenuActionItem(
                label = stringResource(R.string.edit),
                icon = Icons.Rounded.Edit,
                onClick = { trigger(MemoryEditUiIntent.Edit(entry)) },
            )
            EntryMenuActionItem(
                label = stringResource(R.string.confirm),
                icon = Icons.Rounded.CheckCircle,
                onClick = { trigger(MemoryEditUiIntent.Confirm(entry)) },
            )
            EntryMenuActionItem(
                label = stringResource(R.string.memory_history),
                icon = Icons.Rounded.History,
                onClick = { trigger(MemoryEditUiIntent.ShowHistoryDialog(entry)) },
            )
            EntryMenuActionItem(
                label = stringResource(R.string.memory_scope),
                icon = Icons.Rounded.Visibility,
                onClick = { trigger(MemoryEditUiIntent.ShowScopeDialog(entry)) },
            )
            if (showPromote) {
                EntryMenuActionItem(
                    label = stringResource(R.string.memory_promote),
                    icon = Icons.Rounded.NorthEast,
                    onClick = { trigger(MemoryEditUiIntent.ShowPromoteDialog(entry)) },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            EntryMenuActionItem(
                label = stringResource(R.string.delete),
                icon = Icons.Rounded.DeleteOutline,
                tint = MaterialTheme.colorScheme.error,
                onClick = { trigger(MemoryEditUiIntent.ShowDeleteDialog(entry)) },
            )
        }
    }
}

@Composable
private fun EntryMenuActionItem(
    label: String,
    icon: ImageVector,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    // 默认图标沿用次级前景色，危险操作按调用方传入的错误色高亮。
    val iconTint = if (tint == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        tint
    }
    DropdownMenuItem(
        text = { Text(text = label, color = tint) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun PinnedBadge() {
    // 置顶徽章采用主题色浅底微边框呈现。
    AppSurface(
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
        ),
    ) {
        // 图钉图标与简短置顶文案并排显示。
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.PushPin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = stringResource(R.string.memory_pinned),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun QuickConfirmBadge(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // 待核实条目的快捷确认胶囊，使用琥珀强调色提示需人工核对。
    AppSurface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = AccentAmberColor.copy(alpha = 0.14f),
        border = BorderStroke(width = 0.5.dp, color = AccentAmberColor.copy(alpha = 0.35f)),
    ) {
        // 勾选图标与确认文案紧凑排列。
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = AccentAmberColor,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(R.string.confirm),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = AccentAmberColor,
            )
        }
    }
}

@Composable
private fun InheritedNoticeBanner(inheritEnabled: Boolean) {
    // 按会话是否开启继承开关选择提示图标与配色。
    val icon = if (inheritEnabled) Icons.Rounded.Info else Icons.Rounded.Link
    val tint = if (inheritEnabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        AccentAmberColor
    }
    // 顶部提示条说明只读继承或继承已关闭状态。
    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(
                    if (inheritEnabled) {
                        R.string.memory_inherited
                    } else {
                        R.string.memory_inherited_disabled_hint
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InheritedEntryCard(
    owner: MemoryEditOwnerUiModel,
    entry: MemoryEditEntryItem,
    state: MemoryEditUiState.Normal,
    enabled: Boolean,
    onOverride: () -> Unit,
) {
    // 只读继承卡片使用柔和次级表面与角色标签，视觉上清晰区分于本会话可编辑条目。
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        ),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 顶部展示来源角色、记忆类别及置顶标记。
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RpTagPill(text = owner.name)
                RpMetaPill(text = stringResource(kindLabel(entry.kind)))
                if (entry.pinned) {
                    PinnedBadge()
                }
            }
            Text(
                text = entry.body,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            // 底部汇总重要度与人设，并提供创建本会话局部覆盖入口。
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    RpMetaRow(
                        items = listOf(
                            stringResource(R.string.memory_importance, entry.importance),
                            personaLabel(entry.personaId, state),
                        ),
                    )
                }
                OverrideActionPill(enabled = enabled, onClick = onOverride)
            }
        }
    }
}

@Composable
private fun OverrideActionPill(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // 为只读继承条目提供醒目的局部覆盖入口按钮。
    AppSurface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
        ),
    ) {
        // 编辑图标与覆盖文案并排展示。
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(R.string.memory_override),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun LoadingPanel() {
    RpPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
            Text(
                text = stringResource(R.string.loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FailedPanel(onRetry: () -> Unit) {
    // 展示加载失败状态卡片，允许用户重新订阅记忆列表。
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 错误图标与本地化失败原因。
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RpIconBubble(
                    icon = Icons.Rounded.ErrorOutline,
                    contentColor = MaterialTheme.colorScheme.error,
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.error),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.memory_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // 右下角重试操作按钮。
            Button(
                onClick = onRetry,
                modifier = Modifier.align(Alignment.End),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(text = stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun EmptyScopePanel(
    enabled: Boolean,
    onCreate: () -> Unit,
) {
    // 当前范围无匹配条目时展示空状态卡片与快捷新增入口。
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 空状态说明图标与引导文案。
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RpIconBubble(
                    icon = Icons.Rounded.Psychology,
                    contentColor = AccentEmeraldColor,
                    containerColor = AccentEmeraldColor.copy(alpha = 0.14f),
                )
                Text(
                    text = stringResource(R.string.memory_empty),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 右下角快捷添加按钮触发新建弹窗。
            Button(
                onClick = onCreate,
                enabled = enabled,
                modifier = Modifier.align(Alignment.End),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(R.string.add))
            }
        }
    }
}

@Composable
private fun EmptyInheritedPanel() {
    // 当前会话无可用角色继承条目时展示说明卡片。
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
        ),
    ) {
        // 链接图标与本地化无继承条目文案。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RpIconBubble(
                icon = Icons.Rounded.Link,
                contentColor = AccentIndigoColor,
                containerColor = AccentIndigoColor.copy(alpha = 0.14f),
            )
            Text(
                text = stringResource(R.string.memory_inherited_empty),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 列表筛选区使用的单选筛选胶囊。 */
@Composable
private fun MemoryFilterChip(
    selected: Boolean,
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    // 选中态附带勾选图标与主色边框，未选中态使用半透明底板。
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        },
        // 统一边框和填充配色与主页面筛选条保持一致。
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            selectedBorderColor = MaterialTheme.colorScheme.primary,
            selectedBorderWidth = 1.dp,
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f),
        ),
        colors = pageFilterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

/** 带语义圆点的状态徽章，复用模型卡片的状态胶囊视觉范式。 */
@Composable
private fun StatusPill(status: MemoryStatus) {
    // 按记忆生效、待核实与已失效状态映射对应强调色。
    val dotColor = when (status) {
        MemoryStatus.Active -> AccentEmeraldColor
        MemoryStatus.Disputed -> AccentAmberColor
        MemoryStatus.Invalidated -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // 胶囊内左侧展示色点，右侧展示本地化状态文本。
    AppSurface(
        shape = RoundedCornerShape(9.dp),
        color = dotColor.copy(alpha = 0.12f),
        border = BorderStroke(width = 0.5.dp, color = dotColor.copy(alpha = 0.28f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(dotColor, CircleShape),
            )
            Text(
                text = stringResource(statusLabel(status)),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = dotColor,
                maxLines = 1,
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true, locale = "zh")
@Composable
private fun MemoryEditLayoutPreview() {
    // 构造置顶生效与待核实局部覆盖两条预览示例。
    val sampleEntry = MemoryEditEntryItem(
        ref = MemoryRef(table = MemoryTable.Group, id = 1L),
        target = MemoryTarget.Group(1L),
        uuid = "preview-1",
        body = "Alex 喝茶不加糖，平时更喜欢乌龙茶和焙茶。",
        kind = MemoryKind.preference,
        status = MemoryStatus.Active,
        importance = 80,
        pinned = true,
        personaId = null,
        privateCharacterId = null,
        overrideCharacterMemoryId = null,
        currentRevision = 2L,
    )
    val disputedEntry = MemoryEditEntryItem(
        ref = MemoryRef(table = MemoryTable.Group, id = 2L),
        target = MemoryTarget.Group(1L),
        uuid = "preview-2",
        body = "钟楼阁楼的铜钥匙藏在旧书桌最下层的暗格里。",
        kind = MemoryKind.clue,
        status = MemoryStatus.Disputed,
        importance = 65,
        pinned = false,
        personaId = null,
        privateCharacterId = 10L,
        overrideCharacterMemoryId = 5L,
        currentRevision = 1L,
    )
    // 使用固定主题渲染群聊记忆编辑器预览。
    AppTheme(dynamicColor = false) {
        MemoryEditLayout(
            state = MemoryEditUiState.Normal(
                target = MemoryTarget.Group(1L),
                title = "雨夜调查小队",
                settings = MemoryEditSettingsUiModel(
                    title = "雨夜调查小队",
                    characters = listOf(
                        MemoryEditOwnerUiModel(id = 10L, name = "Lyra"),
                        MemoryEditOwnerUiModel(id = 11L, name = "Mira"),
                    ),
                    personaId = null,
                    inherit = true,
                    recall = true,
                ),
                content = MemoryEditContentState(
                    loading = false,
                    totalCount = 2,
                    entries = listOf(sampleEntry, disputedEntry),
                ),
            ),
            emit = {},
        )
    }
}
