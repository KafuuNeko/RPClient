package me.kafuuneko.rpclient.feature.main.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Image as ImageIcon
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Stream
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.main.model.MainGenerationParameter
import me.kafuuneko.rpclient.feature.main.model.MainProviderItem
import me.kafuuneko.rpclient.feature.main.presentation.MainAppearanceSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainDebugSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainGenerationParametersState
import me.kafuuneko.rpclient.feature.main.presentation.MainHomeResourceState
import me.kafuuneko.rpclient.feature.main.presentation.MainHomeState
import me.kafuuneko.rpclient.feature.main.presentation.MainPage
import me.kafuuneko.rpclient.feature.main.presentation.MainPromptBehaviorState
import me.kafuuneko.rpclient.feature.main.presentation.MainProviderPostProcessingState
import me.kafuuneko.rpclient.feature.main.presentation.MainProviderSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainRecentChatsState
import me.kafuuneko.rpclient.feature.main.presentation.MainRecentGroupChatsState
import me.kafuuneko.rpclient.feature.main.presentation.MainRecentStoriesState
import me.kafuuneko.rpclient.feature.main.presentation.MainSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainSummaryInjectionState
import me.kafuuneko.rpclient.feature.main.presentation.MainSummarySettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainSummarySettingsTab
import me.kafuuneko.rpclient.feature.main.presentation.MainUiIntent
import me.kafuuneko.rpclient.feature.main.presentation.MainUiState
import me.kafuuneko.rpclient.feature.main.presentation.MainUserAvatarState
import me.kafuuneko.rpclient.feature.main.presentation.MainUserIdentityState
import me.kafuuneko.rpclient.feature.main.presentation.MainWorldInfoBudgetState
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionPosition
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionRole
import me.kafuuneko.rpclient.libs.search.SearchIndexState
import me.kafuuneko.rpclient.libs.theme.AppThemeMode
import me.kafuuneko.rpclient.ui.theme.AccentAmberColor
import me.kafuuneko.rpclient.ui.theme.AccentBlueColor
import me.kafuuneko.rpclient.ui.theme.AccentEmeraldColor
import me.kafuuneko.rpclient.ui.theme.AccentIndigoColor
import me.kafuuneko.rpclient.ui.theme.AccentPinkColor
import me.kafuuneko.rpclient.ui.theme.AccentRedColor
import me.kafuuneko.rpclient.ui.theme.AccentSkyColor
import me.kafuuneko.rpclient.ui.theme.AccentVioletColor
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.LocalAppDarkTheme
import me.kafuuneko.rpclient.ui.theme.ProviderAvailableColor
import me.kafuuneko.rpclient.ui.theme.ProviderDisabledColor
import me.kafuuneko.rpclient.ui.theme.ProviderPendingColor
import me.kafuuneko.rpclient.ui.theme.getMacaronColor
import me.kafuuneko.rpclient.ui.widgets.AppCard
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.PageFilterChip as FilterChip
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpMacroActionBar
import me.kafuuneko.rpclient.ui.widgets.RpPercentageSlider
import me.kafuuneko.rpclient.ui.widgets.RpScrollableOutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpSettingsDivider
import me.kafuuneko.rpclient.ui.widgets.RpSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpSettingsSwitchTile
import me.kafuuneko.rpclient.ui.widgets.RpSettingsTile
import me.kafuuneko.rpclient.ui.widgets.RpSettingsValueTile
import me.kafuuneko.rpclient.ui.widgets.generalContentBackground
import me.kafuuneko.rpclient.ui.widgets.rememberBoundTextFieldState
import me.kafuuneko.rpclient.utils.rememberPromptMacroOutputTransformation

/** 设置输入区与全屏人设编辑弹窗共用的宏入口。 */
internal val USER_PERSONA_MACROS = listOf("{{char}}", "{{user}}")

/** 设置主体，按身份、外观、模型、上下文及系统配置分组展示。 */
@Composable
internal fun MainSettingsPage(
    state: MainSettingsState,
    emit: MainUiIntent.() -> Unit
) {
    RpLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = 18.dp,
            top = 8.dp,
            end = 18.dp,
            bottom = 110.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.statusBarsPadding())
        }

        // 用户身份与人设
        item {
            RpSectionHeader(title = stringResource(R.string.user_identity))
        }
        item { UserIdentityPanel(state.identityState, emit) }

        // 外观
        item {
            RpSectionHeader(title = stringResource(R.string.appearance_title))
        }
        item { AppearancePanel(state.appearanceState, emit) }

        // 模型与推理服务
        item {
            RpSectionHeader(
                title = stringResource(R.string.model_provider),
                action = stringResource(R.string.manage)
            ) { MainUiIntent.OpenProviderManager.emit() }
        }
        when (val providerState = state.providerState) {
            MainProviderSettingsState.Empty -> {
                item { EmptyProviderCard { MainUiIntent.OpenProviderManager.emit() } }
            }

            is MainProviderSettingsState.Available -> {
                items(providerState.providers) { provider ->
                    ProviderCard(
                        provider = provider,
                        selected = provider.id == providerState.selectedProviderId,
                        onClick = { MainUiIntent.SelectProvider(provider.id).emit() }
                    )
                }
                item { ParameterPanel(providerState.generationParametersState, emit) }
            }
        }

        // 提示词与上下文记忆
        item {
            RpSectionHeader(title = stringResource(R.string.prompt_and_memory_section))
        }
        item { PromptPresetEntryCard { MainUiIntent.OpenPromptPreset.emit() } }
        item { PromptBehaviorPanel(state.promptBehaviorState, emit) }
        item { WorldInfoBudgetPanel(state.worldInfoBudgetState, emit) }
        item { SummaryPanel(state.summaryState, emit) }
        item { ImageSendPanel(state.imageSendState, emit) }

        // 数据与系统
        item {
            RpSectionHeader(title = stringResource(R.string.system_and_data_section))
        }
        item { StoryMarkdownRenderingPanel(state.appearanceState, emit) }
        item { ChatDataManagementPanel(state.chatDataManagementState, emit) }
        item { BackupRestoreEntryCard { MainUiIntent.OpenBackupRestore.emit() } }
        item { SearchAccelerationPanel(state.searchIndexState, emit) }
        item { TokenUsageEntryCard { emit(MainUiIntent.OpenTokenUsage) } }
        item { DebugPanel(state.debugState, emit) }
        item { AboutEntryCard { emit(MainUiIntent.OpenAbout) } }
    }
}

@Composable
private fun AppearancePanel(
    state: MainAppearanceSettingsState,
    emit: MainUiIntent.() -> Unit
) {
    RpSettingsGroup {
        RpSettingsTile(
            icon = Icons.Rounded.Palette,
            iconColor = AccentIndigoColor,
            iconContainerColor = AccentIndigoColor.copy(alpha = 0.14f),
            title = stringResource(R.string.appearance_theme),
            subtitle = stringResource(R.string.appearance_entry_desc),
            onClick = { MainUiIntent.OpenAppearance.emit() },
            trailing = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                    modifier = Modifier.size(18.dp)
                )
            }
        )
        RpSettingsDivider(startIndent = false)
        // 使用可换行选项，避免较长译文在小屏设备上被截断。
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppThemeMode.entries.forEach { themeMode ->
                FilterChip(
                    selected = themeMode == state.themeMode,
                    onClick = { MainUiIntent.SelectThemeMode(themeMode).emit() },
                    label = { Text(stringResource(themeMode.titleRes())) },
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }
    }
}

@Composable
private fun StoryMarkdownRenderingPanel(
    state: MainAppearanceSettingsState,
    emit: MainUiIntent.() -> Unit
) {
    RpSettingsGroup {
        RpSettingsSwitchTile(
            icon = Icons.Rounded.Edit,
            iconColor = AccentEmeraldColor,
            iconContainerColor = AccentEmeraldColor.copy(alpha = 0.14f),
            title = stringResource(R.string.story_markdown_rendering),
            subtitle = stringResource(R.string.story_markdown_rendering_desc),
            checked = state.storyMarkdownRenderingEnabled,
            onCheckedChange = { MainUiIntent.ToggleStoryMarkdownRendering(it).emit() }
        )
    }
}

/** 从数据设置进入完整备份与恢复页面。 */
@Composable
private fun BackupRestoreEntryCard(onClick: () -> Unit) {
    RpSettingsGroup {
        RpSettingsTile(
            icon = Icons.Rounded.Storage,
            iconColor = AccentSkyColor,
            iconContainerColor = AccentSkyColor.copy(alpha = 0.14f),
            title = stringResource(R.string.backup_restore_title),
            subtitle = stringResource(R.string.backup_restore_entry_desc),
            onClick = onClick
        )
    }
}

/** 全局搜索加速开关使用共享索引状态，确认与维护进度由主页弹窗宿主承载。 */
@Composable
private fun SearchAccelerationPanel(index: SearchIndexState, emit: MainUiIntent.() -> Unit) {
    // 忙碌和失败状态使用现有本地化说明，避免把尚未完成的构建显示为已开启。
    val subtitle = when (index) {
        is SearchIndexState.Building -> stringResource(R.string.content_search_index_building, index.processed)
        SearchIndexState.Preparing -> stringResource(R.string.loading)
        SearchIndexState.Cleaning -> stringResource(R.string.content_search_index_cleaning)
        SearchIndexState.CleanupFailed -> stringResource(R.string.content_search_index_cleanup_failed)
        SearchIndexState.Unavailable -> stringResource(R.string.content_search_index_failed)
        SearchIndexState.Ready, SearchIndexState.Updating -> stringResource(R.string.content_search_index_enabled)
        SearchIndexState.Disabled -> stringResource(R.string.content_search_index_disabled)
    }
    RpSettingsGroup {
        RpSettingsSwitchTile(
            title = stringResource(R.string.content_search_index_title),
            subtitle = subtitle,
            icon = Icons.Rounded.Search,
            iconColor = AccentSkyColor,
            iconContainerColor = AccentSkyColor.copy(alpha = 0.14f),
            checked = index.enabled,
            enabled = !index.busy,
            onCheckedChange = { MainUiIntent.ToggleSearchAcceleration(it).emit() }
        )
    }
}

@Composable
private fun TokenUsageEntryCard(onClick: () -> Unit) {
    // 消耗统计不依赖调试模式，因此入口始终显示在数据与系统分组
    RpSettingsGroup {
        RpSettingsTile(
            icon = Icons.Rounded.Numbers,
            iconColor = AccentSkyColor,
            iconContainerColor = AccentSkyColor.copy(alpha = 0.14f),
            title = stringResource(R.string.token_usage),
            subtitle = stringResource(R.string.token_usage_entry_subtitle),
            onClick = onClick,
            trailing = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                    modifier = Modifier.size(18.dp)
                )
            }
        )
    }
}

@Composable
private fun WorldInfoBudgetPanel(
    state: MainWorldInfoBudgetState,
    emit: MainUiIntent.() -> Unit
) {
    RpSettingsGroup {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.world_info_budget_section),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.world_info_budget_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
            RpPercentageSlider(
                title = stringResource(R.string.world_info_context_percent),
                value = state.budgetPercent,
                helper = stringResource(R.string.world_info_context_percent_helper),
                onValueChange = { MainUiIntent.ChangeWorldInfoBudgetPercent(it).emit() }
            )
            NumberSettingRow(
                title = stringResource(R.string.world_info_budget_cap),
                value = state.budgetCap.toString(),
                helper = stringResource(R.string.world_info_budget_cap_helper),
                onValueChange = { MainUiIntent.ChangeWorldInfoBudgetCap(it).emit() }
            )
        }
        RpSettingsDivider(startIndent = false)
        RpSettingsSwitchTile(
            icon = Icons.Rounded.Book,
            iconColor = AccentEmeraldColor,
            iconContainerColor = AccentEmeraldColor.copy(alpha = 0.14f),
            title = stringResource(R.string.world_info_overflow_alert),
            subtitle = stringResource(R.string.world_info_overflow_alert_desc),
            checked = state.overflowAlert,
            onCheckedChange = { MainUiIntent.ToggleWorldInfoOverflowAlert(it).emit() }
        )
    }
}

@Composable
private fun UserIdentityPanel(
    state: MainUserIdentityState,
    emit: MainUiIntent.() -> Unit
) {
    RpSettingsGroup {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                UserAvatarPicker(
                    state = state,
                    emit = emit
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = state.userName.ifBlank { stringResource(R.string.user_display_name) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = state.userDescriptionPreview.ifBlank {
                            stringResource(R.string.user_persona_description)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.userName,
                    onValueChange = { MainUiIntent.ChangeUserName(it).emit() },
                    label = { Text(stringResource(R.string.user_display_name)) },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().generalContentBackground(RoundedCornerShape(14.dp), hasLabel = true))
                UserPersonaDescriptionField(
                    value = state.userDescription,
                    onValueChange = { MainUiIntent.ChangeUserDescription(it).emit() },
                    onExpandFullscreen = {
                        MainUiIntent.ShowUserDescriptionEditor.emit()
                    }
                )
                if (state.avatarState is MainUserAvatarState.Configured) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { MainUiIntent.ClearUserAvatar.emit() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.clear_user_avatar),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 带宏高亮、光标插入快捷栏和全屏编辑入口的用户人设描述输入框。 */
@Composable
private fun UserPersonaDescriptionField(
    value: String,
    onValueChange: (String) -> Unit,
    onExpandFullscreen: () -> Unit
) {
    val textFieldState = rememberBoundTextFieldState(value, onValueChange)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RpScrollableOutlinedTextField(
            state = textFieldState,
            label = { Text(stringResource(R.string.user_persona_description)) },
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(14.dp),
            outputTransformation = rememberPromptMacroOutputTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        RpMacroActionBar(
            onInsertMacro = { macro ->
                val start = minOf(
                    textFieldState.selection.start,
                    textFieldState.selection.end
                )
                val end = maxOf(
                    textFieldState.selection.start,
                    textFieldState.selection.end
                )
                textFieldState.edit {
                    replace(start, end, macro)
                    selection = TextRange(start + macro.length)
                }
            },
            onFullscreenClick = onExpandFullscreen,
            macros = USER_PERSONA_MACROS
        )
    }
}

@Composable
private fun UserAvatarPicker(
    state: MainUserIdentityState,
    emit: MainUiIntent.() -> Unit
) {
    val avatarText = state.userName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val avatarColor = remember(state.userName) {
        getMacaronColor(state.userName.ifBlank { "user" })
    }

    Box(
        modifier = Modifier
            .size(78.dp)
            .clickable { MainUiIntent.PickUserAvatarClick.emit() },
        contentAlignment = Alignment.Center
    ) {
        val avatarState = state.avatarState
        val avatarImage = (avatarState as? MainUserAvatarState.Configured)?.image
        AppSurface(
            modifier = Modifier.size(70.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            ),
            shadowElevation = 2.dp
        ) {
            if (avatarImage == null) {
                RpAvatar(
                    text = avatarText,
                    color = avatarColor,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(20.dp)
                )
            } else {
                Image(
                    bitmap = avatarImage,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
        AppSurface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(26.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
            shadowElevation = 3.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.ImageIcon,
                    contentDescription = stringResource(R.string.choose_user_avatar),
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun PromptBehaviorPanel(
    state: MainPromptBehaviorState,
    emit: MainUiIntent.() -> Unit
) {
    RpSettingsGroup {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.prompt_behavior_section),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.prompt_post_processing_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
            val postProcessingState = state.providerPostProcessingState
            val selectedMode =
                (postProcessingState as? MainProviderPostProcessingState.Available)?.mode

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PromptPostProcessingMode.entries.forEach { mode ->
                    PromptPostProcessingModeRow(
                        mode = mode,
                        selected = mode == selectedMode,
                        enabled = postProcessingState is MainProviderPostProcessingState.Available,
                        onClick = { MainUiIntent.SelectPostProcessingMode(mode).emit() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.prompt_example_behavior_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.prompt_example_behavior_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExampleDialogueBehavior.entries.forEach { behavior ->
                    FilterChip(
                        selected = behavior == state.exampleDialogueBehavior,
                        onClick = {
                            MainUiIntent.SelectExampleDialogueBehavior(behavior).emit()
                        },
                        label = { Text(stringResource(behavior.titleRes())) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
            NumberSettingRow(
                title = stringResource(R.string.prompt_max_history_messages),
                value = state.maxHistoryMessages.toString(),
                helper = stringResource(R.string.prompt_max_history_messages_helper),
                onValueChange = { MainUiIntent.ChangeMaxPromptHistoryMessages(it).emit() }
            )
        }
        RpSettingsDivider(startIndent = false)
        RpSettingsSwitchTile(
            icon = Icons.Rounded.Psychology,
            iconColor = AccentIndigoColor,
            iconContainerColor = AccentIndigoColor.copy(alpha = 0.14f),
            title = stringResource(R.string.prompt_include_think_context_title),
            subtitle = stringResource(R.string.prompt_include_think_context_desc),
            checked = state.includeThinkInContext,
            onCheckedChange = { MainUiIntent.ToggleIncludeThinkInContext(it).emit() }
        )
        RpSettingsDivider()
        RpSettingsSwitchTile(
            icon = Icons.Rounded.NotificationsActive,
            iconColor = AccentPinkColor,
            iconContainerColor = AccentPinkColor.copy(alpha = 0.14f),
            title = stringResource(R.string.context_trimming_alert),
            subtitle = stringResource(R.string.context_trimming_alert_desc),
            checked = state.contextTrimmingAlert,
            onCheckedChange = { MainUiIntent.ToggleContextTrimmingAlert(it).emit() }
        )
        RpSettingsDivider()
        RpSettingsSwitchTile(
            icon = Icons.Rounded.Stream,
            iconColor = AccentSkyColor,
            iconContainerColor = AccentSkyColor.copy(alpha = 0.14f),
            title = stringResource(R.string.streaming_response),
            subtitle = stringResource(R.string.streaming_response_desc),
            checked = state.streamEnabled,
            onCheckedChange = { MainUiIntent.ToggleStreamEnabled(it).emit() }
        )
    }
}

@Composable
private fun PromptPostProcessingModeRow(
    mode: PromptPostProcessingMode,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 0.5.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f)
            }
        ),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(mode.titleRes()),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(mode.descriptionRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyProviderCard(
    onClick: () -> Unit
) {
    RpSettingsGroup {
        RpSettingsTile(
            icon = Icons.Rounded.Storage,
            iconColor = AccentAmberColor,
            iconContainerColor = AccentAmberColor.copy(alpha = 0.14f),
            title = stringResource(R.string.no_enabled_model),
            subtitle = stringResource(R.string.go_to_model_manager),
            onClick = onClick,
            trailing = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                    modifier = Modifier.size(18.dp)
                )
            }
        )
    }
}

@Composable
private fun ProviderCard(
    provider: MainProviderItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    AppCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.selected = selected; role = Role.RadioButton },
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 0.5.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(
                alpha = 0.22f
            )
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                if (LocalAppDarkTheme.current) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                modifier = Modifier.size(38.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    provider.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    provider.model,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    provider.baseUrl,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.48f
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val dotColor = when {
                !provider.isEnabled -> ProviderDisabledColor
                !provider.isConfigured -> ProviderPendingColor
                else -> ProviderAvailableColor
            }
            val statusText = when {
                !provider.isEnabled -> stringResource(R.string.not_enabled)
                !provider.isConfigured -> stringResource(R.string.pending_config)
                else -> stringResource(R.string.available)
            }
            AppSurface(
                shape = RoundedCornerShape(8.dp),
                color = dotColor.copy(alpha = 0.12f),
                border = BorderStroke(0.5.dp, dotColor.copy(alpha = 0.28f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(dotColor, CircleShape)
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = dotColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ParameterPanel(
    state: MainGenerationParametersState,
    emit: MainUiIntent.() -> Unit
) {
    RpSettingsGroup {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.generation_parameters),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { MainUiIntent.OpenSelectedProviderEdit.emit() },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    stringResource(R.string.preset),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        RpSettingsDivider(startIndent = false)
        RpSettingsValueTile(
            icon = Icons.Rounded.Thermostat,
            iconColor = AccentAmberColor,
            iconContainerColor = AccentAmberColor.copy(alpha = 0.14f),
            title = stringResource(R.string.temperature),
            value = state.temperature.toString(),
            onClick = {
                MainUiIntent.ShowGenerationParameterDialog(MainGenerationParameter.Temperature)
                    .emit()
            }
        )
        RpSettingsDivider()
        RpSettingsValueTile(
            icon = Icons.Rounded.Tune,
            iconColor = AccentBlueColor,
            iconContainerColor = AccentBlueColor.copy(alpha = 0.14f),
            title = stringResource(R.string.top_p),
            value = state.topP.toString(),
            onClick = {
                MainUiIntent.ShowGenerationParameterDialog(MainGenerationParameter.TopP).emit()
            }
        )
        RpSettingsDivider()
        RpSettingsValueTile(
            icon = Icons.Rounded.Numbers,
            iconColor = AccentVioletColor,
            iconContainerColor = AccentVioletColor.copy(alpha = 0.14f),
            title = stringResource(R.string.max_tokens),
            value = state.maxTokens.toString(),
            onClick = {
                MainUiIntent.ShowGenerationParameterDialog(MainGenerationParameter.MaxTokens).emit()
            }
        )
        RpSettingsDivider()
        RpSettingsValueTile(
            icon = Icons.Rounded.Memory,
            iconColor = AccentEmeraldColor,
            iconContainerColor = AccentEmeraldColor.copy(alpha = 0.14f),
            title = stringResource(R.string.context),
            value = "${state.contextTokens} ${stringResource(R.string.tokens)}",
            onClick = {
                MainUiIntent.ShowGenerationParameterDialog(MainGenerationParameter.ContextTokens)
                    .emit()
            }
        )
    }
}

@Composable
private fun PromptPresetEntryCard(onClick: () -> Unit) {
    RpSettingsGroup {
        RpSettingsTile(
            icon = Icons.Rounded.AutoAwesome,
            iconColor = AccentVioletColor,
            iconContainerColor = AccentVioletColor.copy(alpha = 0.14f),
            title = stringResource(R.string.prompt_preset_title),
            subtitle = stringResource(R.string.prompt_preset_entry_subtitle),
            onClick = onClick,
            trailing = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                    modifier = Modifier.size(18.dp)
                )
            }
        )
    }
}

@Composable
private fun SummaryPanel(
    state: MainSummarySettingsState,
    emit: MainUiIntent.() -> Unit
) {
    // 分段栏和设置内容共用区域外观，切换时只替换对应的表单。
    RpSettingsGroup {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.summary_memory),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            SummarySectionTabs(state.selectedTab, emit)
            when (state.selectedTab) {
                MainSummarySettingsTab.General -> GeneralSummarySettings(state, emit)
                MainSummarySettingsTab.Conversation -> ConversationSummarySettings(state, emit)
            }
        }
    }
}

/** 分段选择与设置卡片使用同一层次，轻量底板和选中标记适应透明背景。 */
@Composable
private fun SummarySectionTabs(selectedTab: MainSummarySettingsTab, emit: MainUiIntent.() -> Unit) {
    AppSurface(shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
        Row(Modifier.fillMaxWidth().selectableGroup().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // 选中状态通过强调色与字重表达，完全透明时仍能辨认。
            MainSummarySettingsTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                AppSurface(
                    modifier = Modifier.weight(1f).semantics { selected = isSelected; role = Role.Tab },
                    onClick = { MainUiIntent.SelectSummarySettingsTab(tab).emit() },
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        Text(stringResource(tab.titleRes()), style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = color)
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneralSummarySettings(
    state: MainSummarySettingsState,
    emit: MainUiIntent.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SummaryProviderSelector(
            selectedProviderId = state.selectedProviderId,
            providers = state.providers,
            onSelect = { MainUiIntent.SelectSummaryProvider(it).emit() }
        )
        NumberSettingRow(
            title = stringResource(R.string.summary_target_words),
            value = state.wordsLimit.toString(),
            onValueChange = { MainUiIntent.ChangeSummaryWordsLimit(it).emit() }
        )
        NumberSettingRow(
            title = stringResource(R.string.summary_response_tokens),
            value = state.responseTokens.toString(),
            onValueChange = { MainUiIntent.ChangeSummaryResponseTokens(it).emit() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummaryProviderSelector(
    selectedProviderId: Long,
    providers: List<MainProviderItem>,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedProvider = providers.firstOrNull { it.id == selectedProviderId }
    val selectedName = selectedProvider?.summaryDisplayName()
        ?: stringResource(R.string.follow_global_model)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.summary_model_config)) },
            supportingText = { Text(stringResource(R.string.summary_model_config_helper)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).generalContentBackground(RoundedCornerShape(12.dp), hasLabel = true),
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.follow_global_model)) },
                onClick = {
                    onSelect(0L)
                    expanded = false
                }
            )
            providers.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.summaryDisplayName()) },
                    onClick = {
                        onSelect(provider.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun MainProviderItem.summaryDisplayName(): String {
    val displayName = name.ifBlank { stringResource(R.string.unnamed_model_config) }
    return if (isEnabled) {
        displayName
    } else {
        stringResource(R.string.disabled_model_config_format, displayName)
    }
}

@Composable
private fun ConversationSummarySettings(
    state: MainSummarySettingsState,
    emit: MainUiIntent.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingSwitchRow(
            Icons.Rounded.AutoAwesome,
            stringResource(R.string.auto_summarize),
            stringResource(R.string.auto_summarize_desc),
            state.autoSummaryEnabled,
            onCheckedChange = { MainUiIntent.ToggleAutoSummaryEnabled(it).emit() }
        )
        NumberSettingRow(
            title = stringResource(R.string.summary_update_every_messages),
            value = state.triggerMessageCount.toString(),
            onValueChange = { MainUiIntent.ChangeSummaryTriggerMessageCount(it).emit() }
        )
        NumberSettingRow(
            title = stringResource(R.string.summary_max_messages_per_request),
            value = state.maxMessagesPerRequest.toString(),
            helper = stringResource(R.string.summary_max_messages_helper),
            onValueChange = { MainUiIntent.ChangeSummaryMaxMessagesPerRequest(it).emit() }
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.summary_injection_position),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryInjectionPosition.entries.forEach { position ->
                    FilterChip(
                        selected = position == state.injectionState.position,
                        onClick = {
                            MainUiIntent.SelectSummaryInjectionPosition(position).emit()
                        },
                        label = { Text(stringResource(position.titleRes())) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }
        val injectionState = state.injectionState
        if (injectionState is MainSummaryInjectionState.InChat) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NumberSettingRow(
                    title = stringResource(R.string.summary_injection_depth),
                    value = injectionState.depth.toString(),
                    onValueChange = {
                        MainUiIntent.ChangeSummaryInjectionDepth(it).emit()
                    }
                )
                Text(
                    text = stringResource(R.string.summary_injection_role),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryInjectionRole.entries.forEach { role ->
                        FilterChip(
                            selected = role == injectionState.role,
                            onClick = {
                                MainUiIntent.SelectSummaryInjectionRole(role).emit()
                            },
                            label = { Text(stringResource(role.titleRes())) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun MainSummarySettingsTab.titleRes(): Int {
    return when (this) {
        MainSummarySettingsTab.General -> R.string.general_summary_memory
        MainSummarySettingsTab.Conversation -> R.string.conversation_summary_memory
    }
}

@Composable
private fun NumberSettingRow(
    title: String,
    value: String,
    helper: String? = null,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            if (!helper.isNullOrBlank()) {
                Text(
                    helper,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
                )
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.width(100.dp).generalContentBackground(RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
private fun DebugPanel(
    state: MainDebugSettingsState,
    emit: MainUiIntent.() -> Unit
) {
    RpSettingsGroup {
        RpSettingsSwitchTile(
            icon = Icons.Rounded.BugReport,
            iconColor = AccentRedColor,
            iconContainerColor = AccentRedColor.copy(alpha = 0.14f),
            title = stringResource(R.string.debug_mode),
            subtitle = stringResource(R.string.debug_mode_desc),
            checked = state.enabled,
            onCheckedChange = { MainUiIntent.ToggleDebugModeEnabled(it).emit() }
        )
        if (state.enabled) {
            RpSettingsDivider()
            RpSettingsTile(
                icon = Icons.Rounded.DataObject,
                iconColor = AccentVioletColor,
                iconContainerColor = AccentVioletColor.copy(alpha = 0.14f),
                title = stringResource(R.string.request_logs),
                subtitle = stringResource(R.string.request_logs_entry_subtitle),
                onClick = { MainUiIntent.OpenRequestLogs.emit() },
                trailing = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun AboutEntryCard(
    onClick: () -> Unit
) {
    RpSettingsGroup {
        RpSettingsTile(
            icon = Icons.Rounded.Info,
            iconColor = MaterialTheme.colorScheme.primary,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            title = stringResource(R.string.about),
            subtitle = stringResource(R.string.about_desc),
            onClick = onClick,
            trailing = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                    modifier = Modifier.size(18.dp)
                )
            }
        )
    }
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    RpSettingsSwitchTile(
        icon = icon,
        title = title,
        subtitle = subtitle,
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}

private fun PromptPostProcessingMode.titleRes(): Int {
    return when (this) {
        PromptPostProcessingMode.None -> R.string.prompt_post_processing_none
        PromptPostProcessingMode.Merge -> R.string.prompt_post_processing_merge
        PromptPostProcessingMode.SemiStrict -> R.string.prompt_post_processing_semi_strict
        PromptPostProcessingMode.Strict -> R.string.prompt_post_processing_strict
        PromptPostProcessingMode.SingleUserMessage -> R.string.prompt_post_processing_single_user
    }
}

private fun PromptPostProcessingMode.descriptionRes(): Int {
    return when (this) {
        PromptPostProcessingMode.None -> R.string.prompt_post_processing_none_desc
        PromptPostProcessingMode.Merge -> R.string.prompt_post_processing_merge_desc
        PromptPostProcessingMode.SemiStrict -> R.string.prompt_post_processing_semi_strict_desc
        PromptPostProcessingMode.Strict -> R.string.prompt_post_processing_strict_desc
        PromptPostProcessingMode.SingleUserMessage -> R.string.prompt_post_processing_single_user_desc
    }
}

private fun ExampleDialogueBehavior.titleRes(): Int {
    return when (this) {
        ExampleDialogueBehavior.Normal -> R.string.prompt_example_behavior_normal
        ExampleDialogueBehavior.Pinned -> R.string.prompt_example_behavior_pinned
        ExampleDialogueBehavior.Disabled -> R.string.prompt_example_behavior_disabled
    }
}

private fun SummaryInjectionPosition.titleRes(): Int {
    return when (this) {
        SummaryInjectionPosition.None -> R.string.summary_position_none
        SummaryInjectionPosition.BeforeMain -> R.string.summary_position_before_main
        SummaryInjectionPosition.AfterMain -> R.string.summary_position_after_main
        SummaryInjectionPosition.InChat -> R.string.summary_position_in_chat
    }
}

private fun SummaryInjectionRole.titleRes(): Int {
    return when (this) {
        SummaryInjectionRole.System -> R.string.summary_role_system
        SummaryInjectionRole.User -> R.string.summary_role_user
        SummaryInjectionRole.Assistant -> R.string.summary_role_assistant
    }
}

private fun AppThemeMode.titleRes(): Int {
    return when (this) {
        AppThemeMode.FollowSystem -> R.string.theme_follow_system
        AppThemeMode.Light -> R.string.theme_light
        AppThemeMode.Dark -> R.string.theme_dark
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun MainSettingsLayoutPreview() {
    AppTheme(dynamicColor = false) {
        MainLayout(
            uiState = MainUiState.Normal(
                selectedPage = MainPage.Settings,
                homeState = MainHomeState(
                    resourceState = MainHomeResourceState(
                        totalCharacters = 24,
                        totalWorldBooks = 7
                    ),
                    recentChatsState = MainRecentChatsState.Empty,
                    recentGroupChatsState = MainRecentGroupChatsState.Empty,
                    recentStoriesState = MainRecentStoriesState.Empty
                ),
                settingsState = MainSettingsState(
                    appearanceState = MainAppearanceSettingsState(
                        themeMode = AppThemeMode.FollowSystem
                    ),
                    identityState = MainUserIdentityState(
                        userName = "KafuuNeko",
                        userDescription = "A traveler exploring AI worlds.",
                        userDescriptionPreview = "A traveler exploring AI worlds.",
                        avatarState = MainUserAvatarState.None
                    ),
                    providerState = MainProviderSettingsState.Available(
                        selectedProviderId = 1L,
                        providers = listOf(
                            MainProviderItem(
                                id = 1L,
                                name = "DeepSeek V3",
                                model = "deepseek-chat",
                                baseUrl = "https://api.deepseek.com/v1",
                                isEnabled = true
                            )
                        ),
                        generationParametersState = MainGenerationParametersState(
                            temperature = 0.8f,
                            topP = 0.95f,
                            maxTokens = 4096,
                            contextTokens = 32768
                        )
                    ),
                    promptBehaviorState = MainPromptBehaviorState(
                        providerPostProcessingState = MainProviderPostProcessingState.Available(
                            mode = PromptPostProcessingMode.Strict
                        ),
                        exampleDialogueBehavior = ExampleDialogueBehavior.Normal,
                        includeThinkInContext = true,
                        maxHistoryMessages = 500,
                        contextTrimmingAlert = true,
                        streamEnabled = true
                    ),
                    worldInfoBudgetState = MainWorldInfoBudgetState(
                        budgetPercent = 25,
                        budgetCap = 2048,
                        overflowAlert = true
                    ),
                    summaryState = MainSummarySettingsState(
                        autoSummaryEnabled = true,
                        triggerMessageCount = 20,
                        wordsLimit = 500,
                        maxMessagesPerRequest = 0,
                        responseTokens = 800,
                        injectionState = MainSummaryInjectionState.InChat(
                            depth = 4,
                            role = SummaryInjectionRole.System
                        )
                    ),
                    debugState = MainDebugSettingsState(enabled = true)
                )
            ),
            emit = {}
        )
    }
}
