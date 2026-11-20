package me.kafuuneko.rpclient.feature.memorycenter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.memorycenter.presentation.MemoryCenterContentState
import me.kafuuneko.rpclient.feature.memorycenter.presentation.MemoryCenterDialogState
import me.kafuuneko.rpclient.feature.memorycenter.presentation.MemoryCenterSettingsState
import me.kafuuneko.rpclient.feature.memorycenter.presentation.MemoryCenterUiIntent
import me.kafuuneko.rpclient.feature.memorycenter.presentation.MemoryCenterUiState
import me.kafuuneko.rpclient.feature.memorycenter.presentation.MemoryCharacterItem
import me.kafuuneko.rpclient.feature.memorycenter.presentation.MemoryRecallLimitField
import me.kafuuneko.rpclient.ui.dialog.NumericEditDialog
import me.kafuuneko.rpclient.ui.dialog.NumericEditQuickOption
import me.kafuuneko.rpclient.ui.theme.AccentEmeraldColor
import me.kafuuneko.rpclient.ui.theme.AccentIndigoColor
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.CharacterAccentColors
import me.kafuuneko.rpclient.ui.widgets.AppCard
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpMetaPill
import me.kafuuneko.rpclient.ui.widgets.RpPanel
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpSettingsDivider
import me.kafuuneko.rpclient.ui.widgets.RpSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpSettingsValueTile
import me.kafuuneko.rpclient.ui.widgets.RpTagPill
import me.kafuuneko.rpclient.ui.widgets.pageCanvasColor
import me.kafuuneko.rpclient.ui.widgets.PageButton as Button

/** 角色记忆目录页 Compose 入口，展示召回额度设置与全部角色记忆入口。 */
@Composable
fun MemoryCenterLayout(
    state: MemoryCenterUiState,
    emit: MemoryCenterUiIntent.() -> Unit,
) {
    BackHandler(enabled = state is MemoryCenterUiState.Normal) {
        MemoryCenterUiIntent.Back.emit()
    }
    when (state) {
        MemoryCenterUiState.None -> Unit
        is MemoryCenterUiState.Finished -> MemoryCenterLayout(state = state.previous, emit = {})
        is MemoryCenterUiState.Normal -> {
            NormalView(state = state, emit = emit)
            DialogSwitch(dialog = state.dialog, emit = emit)
        }
    }
}

@Composable
private fun NormalView(
    state: MemoryCenterUiState.Normal,
    emit: MemoryCenterUiIntent.() -> Unit,
) {
    val listState = rememberLazyListState()
    // 监听视口内角色卡片，按需解码缩略图。
    LaunchedEffect(key1 = listState, key2 = state.content.items) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .mapNotNull { itemInfo -> itemInfo.key as? Long }
                .toSet()
        }
            .distinctUntilChanged()
            .collect { visibleIds ->
                MemoryCenterUiIntent.VisibleCharactersChanged(visibleIds).emit()
            }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageCanvasColor()),
    ) {
        AppTopBar(
            title = stringResource(R.string.memory_center),
            onBack = { MemoryCenterUiIntent.Back.emit() },
        )
        RpLazyColumn(
            state = listState,
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
            // 普通回复的全局召回额度分组。
            item(key = "limits_header") {
                RpSectionHeader(title = stringResource(R.string.memory_limits))
            }
            item(key = "limits_panel") {
                RecallLimitsPanel(settings = state.settings, emit = emit)
            }
            // 全部角色目录分组。
            item(key = "characters_header") {
                RpSectionHeader(title = stringResource(R.string.all_characters))
            }
            if (state.content.loading) {
                item(key = "loading") { LoadingPanel() }
            } else if (state.content.failed) {
                item(key = "failed") { FailedPanel(onRetry = { MemoryCenterUiIntent.Retry.emit() }) }
            } else if (state.content.items.isEmpty()) {
                item(key = "empty") { EmptyCharactersPanel() }
            } else {
                items(
                    items = state.content.items,
                    key = { item -> item.id },
                ) { item ->
                    CharacterMemoryCard(
                        item = item,
                        onClick = { MemoryCenterUiIntent.OpenCharacter(item.id).emit() },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecallLimitsPanel(
    settings: MemoryCenterSettingsState,
    emit: MemoryCenterUiIntent.() -> Unit,
) {
    // 沿用主设置参数面板的分组值行样式，点击后以独立数值弹窗编辑。
    RpSettingsGroup {
        // 渲染单次普通回复的召回条数上限配置行。
        RpSettingsValueTile(
            icon = Icons.Rounded.Numbers,
            iconColor = AccentIndigoColor,
            iconContainerColor = AccentIndigoColor.copy(alpha = 0.14f),
            title = stringResource(R.string.memory_max_items),
            subtitle = stringResource(R.string.memory_max_items_helper),
            value = stringResource(R.string.memory_count, settings.maxItems),
            onClick = {
                MemoryCenterUiIntent.EditLimitClick(MemoryRecallLimitField.MaxItems).emit()
            },
        )
        RpSettingsDivider()
        // 渲染普通回复的独立记忆 Token 额度配置行。
        RpSettingsValueTile(
            icon = Icons.Rounded.Memory,
            iconColor = AccentEmeraldColor,
            iconContainerColor = AccentEmeraldColor.copy(alpha = 0.14f),
            title = stringResource(R.string.memory_max_tokens),
            subtitle = stringResource(R.string.memory_max_tokens_helper),
            value = stringResource(R.string.prompt_inspector_item_tokens, settings.maxTokens),
            onClick = {
                MemoryCenterUiIntent.EditLimitClick(MemoryRecallLimitField.MaxTokens).emit()
            },
        )
    }
}

@Composable
private fun CharacterMemoryCard(
    item: MemoryCharacterItem,
    onClick: () -> Unit,
) {
    // 卡片外观与角色列表页保持一致，使用柔和底板、微边框和角色强调色头像。
    AppCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧展示角色头像或首字占位徽标。
            RpAvatar(
                text = item.name.take(1).ifBlank { "?" },
                color = characterAccentColor(item.id),
                modifier = Modifier.size(50.dp),
                shape = RoundedCornerShape(15.dp),
                image = item.image,
            )
            Spacer(modifier = Modifier.width(14.dp))
            // 中部展示角色名称及当前已保存通用记忆条数标签。
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val countLabel = stringResource(R.string.memory_count, item.count)
                if (item.count > 0) {
                    RpTagPill(text = countLabel)
                } else {
                    RpMetaPill(text = countLabel)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            // 右侧引导箭头表示点击进入该角色的记忆编辑器。
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                modifier = Modifier.size(20.dp),
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
    // 展示目录读取失败状态卡片，避免把异常误渲染为空目录。
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
            // 错误图标与本地化失败说明。
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
            // 右下角重试按钮重新触发目录订阅。
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
private fun EmptyCharactersPanel() {
    // 无角色卡时展示空目录引导卡片。
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 角色占位图标与空状态提示文案。
            RpIconBubble(icon = Icons.Rounded.Person)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.no_character_cards),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.memory_no_characters),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DialogSwitch(
    dialog: MemoryCenterDialogState,
    emit: MemoryCenterUiIntent.() -> Unit,
) {
    when (dialog) {
        MemoryCenterDialogState.None -> Unit
        is MemoryCenterDialogState.EditLimit -> {
            // 按当前编辑字段准备标题、副标题与快捷选项。
            val title = when (dialog.field) {
                MemoryRecallLimitField.MaxItems -> stringResource(R.string.memory_max_items)
                MemoryRecallLimitField.MaxTokens -> stringResource(R.string.memory_max_tokens)
            }
            val subtitle = when (dialog.field) {
                MemoryRecallLimitField.MaxItems -> stringResource(R.string.memory_max_items_helper)
                MemoryRecallLimitField.MaxTokens -> stringResource(R.string.memory_max_tokens_helper)
            }
            val quickOptions = when (dialog.field) {
                MemoryRecallLimitField.MaxItems -> listOf(
                    NumericEditQuickOption("0", "0"),
                    NumericEditQuickOption("3", "3"),
                    NumericEditQuickOption("6", "6"),
                    NumericEditQuickOption("12", "12"),
                    NumericEditQuickOption("20", "20"),
                )

                MemoryRecallLimitField.MaxTokens -> listOf(
                    NumericEditQuickOption("0", "0"),
                    NumericEditQuickOption("256", "256"),
                    NumericEditQuickOption("512", "512"),
                    NumericEditQuickOption("1024", "1024"),
                    NumericEditQuickOption("2048", "2048"),
                )
            }
            // 复用公共整数编辑弹窗处理输入变更、确认与取消意图。
            NumericEditDialog(
                title = title,
                subtitle = subtitle,
                value = dialog.draft,
                decimalInput = false,
                quickOptions = quickOptions,
                onValueChange = { value -> MemoryCenterUiIntent.ChangeLimitDraft(value).emit() },
                onConfirm = { MemoryCenterUiIntent.ConfirmLimit.emit() },
                onDismiss = { MemoryCenterUiIntent.DismissDialog.emit() },
            )
        }
    }
}

private fun characterAccentColor(characterId: Long): Color {
    val index = Math.floorMod(characterId, CharacterAccentColors.size)
    return CharacterAccentColors[index]
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true, locale = "zh")
@Composable
private fun MemoryCenterLayoutPreview() {
    // 构造静态预览状态，展示有记忆与零记忆角色卡片样式。
    val previewState = MemoryCenterUiState.Normal(
        content = MemoryCenterContentState(
            loading = false,
            items = listOf(
                MemoryCharacterItem(id = 1L, name = "Lyra", count = 12, avatarUuid = ""),
                MemoryCharacterItem(id = 2L, name = "Mira", count = 0, avatarUuid = ""),
            ),
        ),
        settings = MemoryCenterSettingsState(maxItems = 6, maxTokens = 512),
    )
    // 使用固定主题渲染目录页预览。
    AppTheme(dynamicColor = false) {
        MemoryCenterLayout(state = previewState, emit = {})
    }
}
