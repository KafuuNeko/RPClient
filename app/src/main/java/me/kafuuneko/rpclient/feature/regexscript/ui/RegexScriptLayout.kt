package me.kafuuneko.rpclient.feature.regexscript.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import me.kafuuneko.rpclient.ui.widgets.DraggableItem
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpScrollableOutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.dragContainer
import me.kafuuneko.rpclient.ui.widgets.rememberLazyListDragDropState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CodeOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.dialog.AppDialogScaffold
import me.kafuuneko.rpclient.ui.dialog.DialogBadgeTone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.regexscript.presentation.RegexScriptDialogState
import me.kafuuneko.rpclient.feature.regexscript.presentation.RegexScriptUiIntent
import me.kafuuneko.rpclient.feature.regexscript.presentation.RegexScriptUiState
import me.kafuuneko.rpclient.libs.regex.RegexExecutionMode
import me.kafuuneko.rpclient.libs.regex.RegexFindMacroMode
import me.kafuuneko.rpclient.libs.regex.RegexPlacement
import me.kafuuneko.rpclient.libs.regex.RegexScript
import me.kafuuneko.rpclient.libs.regex.RegexScriptScope
import me.kafuuneko.rpclient.ui.theme.AppTheme

/** Regex 管理页 Compose 入口，仅根据状态树渲染并向 ViewModel 发出意图。 */
@Composable
fun RegexScriptLayout(
    uiState: RegexScriptUiState,
    emitIntent: (RegexScriptUiIntent) -> Unit = {}
) {
    BackHandler(enabled = uiState is RegexScriptUiState.Normal) { emitIntent(RegexScriptUiIntent.Back) }
    when (uiState) {
        RegexScriptUiState.None -> Unit
        is RegexScriptUiState.Finished -> RegexScriptLayout(uiState.previous) {}
        is RegexScriptUiState.Normal -> {
            RegexScriptNormal(uiState, emitIntent)
            DialogSwitch(uiState.dialogState, emitIntent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegexScriptNormal(
    state: RegexScriptUiState.Normal,
    emitIntent: (RegexScriptUiIntent) -> Unit
) {
    val canManageScripts =
        state.scope != RegexScriptScope.Character || state.selectedCharacterId != null
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.regex_script_title),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { emitIntent(RegexScriptUiIntent.Back) }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { emitIntent(RegexScriptUiIntent.ImportClick) },
                            enabled = canManageScripts && !state.transferInProgress
                        ) {
                            Icon(Icons.Rounded.FileDownload, contentDescription = null)
                        }
                        IconButton(
                            onClick = { emitIntent(RegexScriptUiIntent.ExportClick) },
                            enabled = !state.transferInProgress
                        ) {
                            Icon(Icons.Rounded.FileUpload, contentDescription = null)
                        }
                        IconButton(
                            onClick = { emitIntent(RegexScriptUiIntent.CreateScript) },
                            enabled = canManageScripts && !state.transferInProgress
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
                if (state.transferInProgress) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    ) { padding ->
        val listState = rememberLazyListState()
        val scriptKeys = remember(state.scripts) { state.scripts.map { it.id }.toSet() }
        val dragDropState = rememberLazyListDragDropState(
            lazyListState = listState,
            isItemDraggable = { key -> key in scriptKeys },
            onMove = { fromKey, toKey ->
                val fromIndex = state.scripts.indexOfFirst { it.id == fromKey }
                val toIndex = state.scripts.indexOfFirst { it.id == toKey }
                if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                    emitIntent(RegexScriptUiIntent.ReorderScript(fromIndex, toIndex))
                }
            },
            onDragEnd = {
                emitIntent(RegexScriptUiIntent.CommitScriptOrder)
            }
        )

        RpLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .dragContainer(dragDropState),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { ScopeSelector(state, emitIntent) }

            if (state.scope == RegexScriptScope.Character) {
                item {
                    AnimatedVisibility(visible = true) {
                        CharacterSelector(state, emitIntent)
                    }
                }
            }

            if (state.scope == RegexScriptScope.Character) {
                item { AuthorizationCard(state, emitIntent) }
            }

            if (state.scripts.isEmpty()) {
                item { EmptyScriptsCard() }
            } else {
                items(state.scripts, key = { it.id }) { script ->
                    DraggableItem(
                        dragDropState = dragDropState,
                        key = script.id
                    ) { isDragging ->
                        ScriptCard(
                            script = script,
                            isDragging = isDragging,
                            emitIntent = emitIntent
                        )
                    }
                }
            }

            item { TestCard(state, emitIntent) }
        }
    }
}

/** 统一高对比度 FilterChip 组件，自带打勾选态图标与高亮边框 */
@Composable
private fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: CornerBasedShape = RoundedCornerShape(10.dp)
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        modifier = modifier,
        leadingIcon = if (selected) {
            {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        } else null,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        },
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            selectedBorderColor = MaterialTheme.colorScheme.primary,
            selectedBorderWidth = 1.5.dp,
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

/** 作用域切换器，改用分段式 Pill 单选组，增强页面层次感。 */
@Composable
private fun ScopeSelector(
    state: RegexScriptUiState.Normal,
    emitIntent: (RegexScriptUiIntent) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RegexScriptScope.entries.forEach { scope ->
                val selected = state.scope == scope
                val backgroundColor = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f)
                }
                val contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(
                    onClick = { emitIntent(RegexScriptUiIntent.SelectScope(scope)) },
                    shape = RoundedCornerShape(12.dp),
                    color = backgroundColor,
                    contentColor = contentColor,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = scope.label(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/** 角色作用域下的角色选择器。包装在柔和背景卡片中。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CharacterSelector(
    state: RegexScriptUiState.Normal,
    emitIntent: (RegexScriptUiIntent) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.regex_scope_character),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.characters.forEach { character ->
                    AppFilterChip(
                        selected = state.selectedCharacterId == character.id,
                        onClick = { emitIntent(RegexScriptUiIntent.SelectCharacter(character.id)) },
                        label = character.name
                    )
                }
            }
        }
    }
}

/** 角色卡脚本的显式授权开关及安全提示。 */
@Composable
private fun AuthorizationCard(
    state: RegexScriptUiState.Normal,
    emitIntent: (RegexScriptUiIntent) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.regex_authorization),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.regex_authorization_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.authorized,
                enabled = state.scope != RegexScriptScope.Character ||
                    state.selectedCharacterId != null,
                onCheckedChange = {
                    emitIntent(RegexScriptUiIntent.ToggleAuthorization(it))
                }
            )
        }
    }
}

/** 列表为空时的精致占位卡片。 */
@Composable
private fun EmptyScriptsCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Rounded.CodeOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = stringResource(R.string.regex_no_scripts),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 单条脚本摘要卡片，重构布局，增加 Placement 视觉标签与 Monospace 代码展示框。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScriptCard(
    script: RegexScript,
    isDragging: Boolean = false,
    emitIntent: (RegexScriptUiIntent) -> Unit
) {
    val cardAlpha = if (script.disabled) 0.6f else 1.0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = cardAlpha
                scaleX = if (isDragging) 1.02f else 1.0f
                scaleY = if (isDragging) 1.02f else 1.0f
            },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 8.dp else 0.dp
        ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (isDragging) 1.5.dp else 1.dp,
            if (isDragging) MaterialTheme.colorScheme.primary
            else if (!script.disabled) MaterialTheme.colorScheme.outlineVariant
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 顶部区域包含拖拽手柄、名称和启用开关。
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.DragHandle,
                    contentDescription = null,
                    tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = script.scriptName.ifBlank { stringResource(R.string.regex_unnamed) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = !script.disabled,
                    onCheckedChange = {
                        emitIntent(RegexScriptUiIntent.ToggleScriptEnabled(script.id))
                    }
                )
            }

            // 触发位置以标签组展示，避免把持久化数值直接暴露给用户。
            val matchedPlacements = RegexPlacement.entries.filter { it.value in script.placement }
            if (matchedPlacements.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    matchedPlacements.forEach { placement ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text(
                                text = placement.label(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // 正则表达式等宽代码展示框
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = script.findRegex,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (script.replaceString.isNotEmpty()) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "➜ ",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = script.replaceString,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 操作按钮栏 (编辑、复制、删除)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { emitIntent(RegexScriptUiIntent.EditScript(script.id)) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.edit),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { emitIntent(RegexScriptUiIntent.CopyScript(script.id)) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { emitIntent(RegexScriptUiIntent.DeleteScriptClick(script.id)) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/** 正则调试/试运行卡片，引入 Header 与代码框。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TestCard(
    state: RegexScriptUiState.Normal,
    emitIntent: (RegexScriptUiIntent) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.Science,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    stringResource(R.string.regex_test_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.regex_placements),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RegexPlacement.entries.forEach { placement ->
                        AppFilterChip(
                            selected = state.testPlacement == placement,
                            onClick = {
                                emitIntent(RegexScriptUiIntent.SelectTestPlacement(placement))
                            },
                            label = placement.label()
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.regex_execution_mode),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RegexExecutionMode.entries.forEach { mode ->
                        AppFilterChip(
                            selected = state.testMode == mode,
                            onClick = { emitIntent(RegexScriptUiIntent.SelectTestMode(mode)) },
                            label = mode.name
                        )
                    }
                }
            }

            RpScrollableOutlinedTextField(
                value = state.testInput,
                onValueChange = { emitIntent(RegexScriptUiIntent.ChangeTestInput(it)) },
                label = { Text(stringResource(R.string.regex_test_input)) },
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8
            )

            Button(
                onClick = { emitIntent(RegexScriptUiIntent.RunTest) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.regex_run_test))
            }

            if (state.testOutput.isNotBlank()) {
                RpScrollableOutlinedTextField(
                    value = state.testOutput,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.regex_test_output)) },
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8
                )
            }
        }
    }
}

/** 根据 UiState 选择脚本编辑器或删除确认框。 */
@Composable
private fun DialogSwitch(
    dialogState: RegexScriptDialogState,
    emitIntent: (RegexScriptUiIntent) -> Unit
) {
    when (dialogState) {
        RegexScriptDialogState.None -> Unit
        is RegexScriptDialogState.Editor -> EditorDialog(dialogState, emitIntent)
        is RegexScriptDialogState.DeleteConfirm -> AppDangerDialog(
            onDismissRequest = { emitIntent(RegexScriptUiIntent.DismissDialog) },
            title = stringResource(R.string.regex_delete_title),
            message = dialogState.scriptName,
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { emitIntent(RegexScriptUiIntent.ConfirmDeleteScript) }
        )
    }
}

/** Regex 脚本编辑器，对长表单进行分块分组，并在正则文本域启用 Monospace 字体。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorDialog(
    state: RegexScriptDialogState.Editor,
    emitIntent: (RegexScriptUiIntent) -> Unit
) {
    val draft = state.draft
    AppDialogScaffold(
        onDismissRequest = { emitIntent(RegexScriptUiIntent.DismissDialog) },
        title = stringResource(R.string.regex_editor_title),
        badgeIcon = Icons.Rounded.Tune,
        badgeTone = DialogBadgeTone.Primary,
        confirmText = stringResource(R.string.save),
        dismissText = stringResource(R.string.cancel),
        confirmEnabled = state.validationError == null,
        onConfirm = { emitIntent(RegexScriptUiIntent.SaveDraft) },
        scrollableContent = true
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 基本信息分组
            DraftGroupHeader(stringResource(R.string.regex_section_basic))
            DraftField(draft.scriptName, R.string.regex_script_name) {
                emitIntent(RegexScriptUiIntent.UpdateDraft(draft.copy(scriptName = it)))
            }

            // 规则与表达式分组
            DraftGroupHeader(stringResource(R.string.regex_section_expressions))
            DraftField(
                value = draft.findRegex,
                labelRes = R.string.regex_find_regex,
                minLines = 2,
                isMonospace = true
            ) {
                emitIntent(RegexScriptUiIntent.UpdateDraft(draft.copy(findRegex = it)))
            }
            DraftField(
                value = draft.replaceString,
                labelRes = R.string.regex_replace_string,
                minLines = 2,
                isMonospace = true
            ) {
                emitIntent(RegexScriptUiIntent.UpdateDraft(draft.copy(replaceString = it)))
            }
            DraftField(
                value = draft.trimStrings,
                labelRes = R.string.regex_trim_strings,
                minLines = 2,
                isMonospace = true
            ) {
                emitIntent(RegexScriptUiIntent.UpdateDraft(draft.copy(trimStrings = it)))
            }

            // 作用位置分组
            DraftGroupHeader(stringResource(R.string.regex_placements))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RegexPlacement.entries.forEach { placement ->
                    val selected = placement.value in draft.placements
                    AppFilterChip(
                        selected = selected,
                        onClick = {
                            val placements = draft.placements.toMutableSet()
                            if (!placements.add(placement.value)) placements.remove(placement.value)
                            emitIntent(
                                RegexScriptUiIntent.UpdateDraft(
                                    draft.copy(placements = placements)
                                )
                            )
                        },
                        label = placement.label()
                    )
                }
            }

            // 功能开关分组
            DraftGroupHeader(stringResource(R.string.regex_section_switches))
            BooleanRow(stringResource(R.string.regex_enabled), !draft.disabled) {
                emitIntent(RegexScriptUiIntent.UpdateDraft(draft.copy(disabled = !it)))
            }
            BooleanRow(stringResource(R.string.regex_markdown_only), draft.markdownOnly) {
                emitIntent(
                    RegexScriptUiIntent.UpdateDraft(
                        draft.copy(
                            markdownOnly = it,
                            promptOnly = if (it) false else draft.promptOnly
                        )
                    )
                )
            }
            BooleanRow(stringResource(R.string.regex_prompt_only), draft.promptOnly) {
                emitIntent(
                    RegexScriptUiIntent.UpdateDraft(
                        draft.copy(
                            promptOnly = it,
                            markdownOnly = if (it) false else draft.markdownOnly
                        )
                    )
                )
            }
            BooleanRow(stringResource(R.string.regex_run_on_edit), draft.runOnEdit) {
                emitIntent(RegexScriptUiIntent.UpdateDraft(draft.copy(runOnEdit = it)))
            }

            // 高阶功能分组
            DraftGroupHeader(stringResource(R.string.regex_section_advanced))
            Text(
                stringResource(R.string.regex_find_macro_mode),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RegexFindMacroMode.entries.forEach { mode ->
                    AppFilterChip(
                        selected = draft.substituteRegex == mode.value,
                        onClick = {
                            emitIntent(
                                RegexScriptUiIntent.UpdateDraft(
                                    draft.copy(substituteRegex = mode.value)
                                )
                            )
                        },
                        label = mode.name
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.minDepth,
                    onValueChange = {
                        emitIntent(RegexScriptUiIntent.UpdateDraft(draft.copy(minDepth = it)))
                    },
                    label = { Text(stringResource(R.string.regex_min_depth)) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = draft.maxDepth,
                    onValueChange = {
                        emitIntent(RegexScriptUiIntent.UpdateDraft(draft.copy(maxDepth = it)))
                    },
                    label = { Text(stringResource(R.string.regex_max_depth)) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                )
            }
            state.validationError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun DraftGroupHeader(title: String) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            thickness = 1.dp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun DraftField(
    value: String,
    labelRes: Int,
    minLines: Int = 1,
    isMonospace: Boolean = false,
    onValueChange: (String) -> Unit
) {
    RpScrollableOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        textStyle = if (isMonospace) {
            LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
        } else {
            LocalTextStyle.current
        },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
        singleLine = minLines == 1,
        minLines = minLines,
        maxLines = if (minLines == 1) 1 else 8
    )
}

@Composable
private fun BooleanRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RegexScriptScope.label(): String = when (this) {
    RegexScriptScope.Global -> stringResource(R.string.regex_scope_global)
    RegexScriptScope.Character -> stringResource(R.string.regex_scope_character)
}

@Composable
private fun RegexPlacement.label(): String = when (this) {
    RegexPlacement.MarkdownDisplay -> stringResource(R.string.regex_placement_display)
    RegexPlacement.UserInput -> stringResource(R.string.regex_placement_user)
    RegexPlacement.AiResponse -> stringResource(R.string.regex_placement_ai)
    RegexPlacement.SlashCommand -> stringResource(R.string.regex_placement_slash)
    RegexPlacement.WorldInfo -> stringResource(R.string.regex_placement_world_info)
    RegexPlacement.Reasoning -> stringResource(R.string.regex_placement_reasoning)
}

@Preview(showBackground = true)
@Composable
private fun RegexScriptLayoutPreview() {
    AppTheme(dynamicColor = false) {
        RegexScriptLayout(
            RegexScriptUiState.Normal(
                scripts = listOf(
                    RegexScript(
                        id = "1",
                        scriptName = "Hide tags",
                        findRegex = "/<tag>.*?<\\/tag>/gis",
                        replaceString = "",
                        placement = listOf(RegexPlacement.AiResponse.value)
                    )
                )
            )
        )
    }
}
