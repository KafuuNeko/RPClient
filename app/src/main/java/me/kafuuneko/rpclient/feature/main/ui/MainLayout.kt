package me.kafuuneko.rpclient.feature.main.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Stream
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.main.model.MainChatSessionGroup
import me.kafuuneko.rpclient.feature.main.model.items.MainChatSessionItem
import me.kafuuneko.rpclient.feature.main.model.MainGenerationParameter
import me.kafuuneko.rpclient.feature.main.model.items.MainGroupChatSessionItem
import me.kafuuneko.rpclient.feature.main.model.items.MainHomeContentItem
import me.kafuuneko.rpclient.feature.main.model.MainHomeItemSelection
import me.kafuuneko.rpclient.feature.main.model.MainHomeItemType
import me.kafuuneko.rpclient.feature.main.model.MainProviderItem
import me.kafuuneko.rpclient.feature.main.model.items.MainStoryItem
import me.kafuuneko.rpclient.feature.main.presentation.MainDebugSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainDialogState
import me.kafuuneko.rpclient.feature.main.presentation.MainGenerationParametersState
import me.kafuuneko.rpclient.feature.main.presentation.MainHomeContentTab
import me.kafuuneko.rpclient.feature.main.presentation.MainHomeResourceState
import me.kafuuneko.rpclient.feature.main.presentation.MainHomeSelectionState
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
import me.kafuuneko.rpclient.model.TokenPreset
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.dialog.AppInputDialog
import me.kafuuneko.rpclient.ui.dialog.AppPromptEditorDialog
import me.kafuuneko.rpclient.ui.dialog.NumericEditDialog
import me.kafuuneko.rpclient.ui.dialog.NumericEditQuickOption
import me.kafuuneko.rpclient.ui.dialog.SliderConfig
import me.kafuuneko.rpclient.ui.theme.AccentAmberColor
import me.kafuuneko.rpclient.ui.theme.AccentBlueColor
import me.kafuuneko.rpclient.ui.theme.AccentEmeraldColor
import me.kafuuneko.rpclient.ui.theme.AccentIndigoColor
import me.kafuuneko.rpclient.ui.theme.AccentPinkColor
import me.kafuuneko.rpclient.ui.theme.AccentRedColor
import me.kafuuneko.rpclient.ui.theme.AccentSkyColor
import me.kafuuneko.rpclient.ui.theme.AccentVioletColor
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.ProviderAvailableColor
import me.kafuuneko.rpclient.ui.theme.ProviderDisabledColor
import me.kafuuneko.rpclient.ui.theme.ProviderPendingColor
import me.kafuuneko.rpclient.ui.theme.getMacaronColor
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpInfoCard
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpMacroActionBar
import me.kafuuneko.rpclient.ui.widgets.RpScrollableOutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.rememberBoundTextFieldState
import me.kafuuneko.rpclient.ui.widgets.RpMetaRow
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpPercentageSlider
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpSettingsDivider
import me.kafuuneko.rpclient.ui.widgets.RpSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpSettingsSwitchTile
import me.kafuuneko.rpclient.ui.widgets.RpSettingsTile
import me.kafuuneko.rpclient.ui.widgets.RpSettingsValueTile
import me.kafuuneko.rpclient.utils.rememberPromptMacroOutputTransformation
import androidx.compose.material.icons.rounded.Image as ImageIcon

private val USER_PERSONA_MACROS = listOf("{{char}}", "{{user}}")

/** 主页面 Compose 入口，承载首页聊天与故事列表以及全局设置。 */
@Composable
fun MainLayout(
    uiState: MainUiState,
    emit: MainUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is MainUiState.Normal) { MainUiIntent.Back.emit() }
    when (uiState) {
        MainUiState.None -> Unit
        is MainUiState.Finished -> MainLayout(uiState.previous) {}
        is MainUiState.Normal -> MainNormal(uiState, emit)
    }
}

@Composable
private fun MainNormal(
    uiState: MainUiState.Normal,
    emit: MainUiIntent.() -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentWindowInsets = WindowInsets(0.dp)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(MaterialThemeLike.background())
        ) {
            when (uiState.selectedPage) {
                MainPage.Home -> HomePage(uiState.homeState, emit)
                MainPage.Settings -> SettingsPage(uiState.settingsState, emit)
            }

            val selectionState = uiState.homeState.selectionState
                as? MainHomeSelectionState.Selecting
            if (selectionState != null && uiState.selectedPage == MainPage.Home) {
                MultiSelectBottomBar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .navigationBarsPadding(),
                    selectedCount = selectionState.selectedItems.size,
                    emit = emit
                )
            } else {
                MainBottomBar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .navigationBarsPadding(),
                    selectedPage = uiState.selectedPage,
                    emit = emit
                )
            }
        }
    }

    DialogSwitch(uiState.dialogState, emit)
}

@Composable
private fun MainBottomBar(
    modifier: Modifier = Modifier,
    selectedPage: MainPage,
    emit: MainUiIntent.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        ),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainBottomBarItem(
                selected = selectedPage == MainPage.Home,
                onClick = { MainUiIntent.SelectPage(MainPage.Home).emit() },
                icon = Icons.Rounded.Home,
                label = stringResource(R.string.home),
                modifier = Modifier.weight(1f)
            )
            MainBottomBarItem(
                selected = selectedPage == MainPage.Settings,
                onClick = { MainUiIntent.SelectPage(MainPage.Settings).emit() },
                icon = Icons.Rounded.Settings,
                label = stringResource(R.string.settings),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MultiSelectBottomBar(
    modifier: Modifier = Modifier,
    selectedCount: Int,
    emit: MainUiIntent.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        ),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainBottomBarItem(
                selected = false,
                onClick = { MainUiIntent.ExitMultiSelect.emit() },
                icon = Icons.Rounded.Close,
                label = stringResource(R.string.cancel),
                modifier = Modifier.weight(1f)
            )
            MainBottomBarItem(
                selected = false,
                onClick = { MainUiIntent.ShowDeleteSelectedDialog.emit() },
                icon = Icons.Rounded.Delete,
                label = stringResource(R.string.delete),
                modifier = Modifier.weight(1f),
                enabled = selectedCount > 0
            )
        }
    }
}

@Composable
private fun DialogSwitch(
    dialogState: MainDialogState,
    emit: MainUiIntent.() -> Unit
) {
    when (dialogState) {
        MainDialogState.None -> Unit

        is MainDialogState.DeleteSelectedItems -> AppDangerDialog(
            onDismissRequest = { MainUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.delete_selected_items_title),
            message = stringResource(
                R.string.delete_selected_items_message,
                dialogState.count
            ),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            confirmEnabled = !dialogState.isDeleting,
            isConfirmLoading = dialogState.isDeleting,
            onConfirm = { MainUiIntent.ConfirmDeleteSelected.emit() },
        )

        is MainDialogState.RenameItem -> AppInputDialog(
            onDismissRequest = { MainUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.rename),
            value = dialogState.title,
            onValueChange = { MainUiIntent.ChangeItemTitleDraft(it).emit() },
            label = stringResource(R.string.title),
            confirmText = stringResource(R.string.confirm),
            dismissText = stringResource(R.string.cancel),
            confirmEnabled = dialogState.title.isNotBlank() && !dialogState.isSaving,
            isConfirmLoading = dialogState.isSaving,
            onConfirm = { MainUiIntent.ConfirmItemRename.emit() }
        )

        is MainDialogState.EditGenerationParameter -> NumericEditDialog(
            title = stringResource(dialogState.parameter.titleRes()),
            subtitle = stringResource(dialogState.parameter.subtitleRes()),
            value = dialogState.draftValue,
            decimalInput = dialogState.parameter.isDecimalInput(),
            sliderConfig = dialogState.parameter.sliderConfig(),
            quickOptions = dialogState.parameter.quickOptions(),
            onValueChange = { MainUiIntent.ChangeGenerationParameterDraft(it).emit() },
            onConfirm = { MainUiIntent.ConfirmGenerationParameter.emit() },
            onDismiss = { MainUiIntent.DismissDialog.emit() }
        )

        is MainDialogState.EditUserDescription -> AppPromptEditorDialog(
            onDismissRequest = { MainUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.user_persona_description),
            value = dialogState.draftText,
            availableMacros = USER_PERSONA_MACROS,
            onValueChange = {
                MainUiIntent.ChangeUserDescriptionEditorDraft(it).emit()
            },
            onConfirm = { MainUiIntent.ConfirmUserDescriptionEditor.emit() }
        )

        is MainDialogState.ImportChatCharacterSelection -> ImportChatCharacterDialog(
            state = dialogState,
            emit = emit
        )
    }
}

private fun MainGenerationParameter.titleRes(): Int = when (this) {
    MainGenerationParameter.Temperature -> R.string.temperature
    MainGenerationParameter.TopP -> R.string.top_p
    MainGenerationParameter.MaxTokens -> R.string.max_tokens
    MainGenerationParameter.ContextTokens -> R.string.context
}

private fun MainGenerationParameter.subtitleRes(): Int = when (this) {
    MainGenerationParameter.Temperature -> R.string.parameter_temperature_desc
    MainGenerationParameter.TopP -> R.string.parameter_top_p_desc
    MainGenerationParameter.MaxTokens -> R.string.parameter_max_tokens_desc
    MainGenerationParameter.ContextTokens -> R.string.parameter_context_tokens_desc
}

private fun MainGenerationParameter.isDecimalInput(): Boolean = when (this) {
    MainGenerationParameter.Temperature, MainGenerationParameter.TopP -> true
    MainGenerationParameter.MaxTokens, MainGenerationParameter.ContextTokens -> false
}

@Composable
private fun MainGenerationParameter.sliderConfig(): SliderConfig? = when (this) {
    MainGenerationParameter.Temperature -> SliderConfig(
        range = 0.00f..2.00f,
        step = 0.05f,
        minLabel = stringResource(R.string.parameter_temp_min_label),
        maxLabel = stringResource(R.string.parameter_temp_max_label)
    )
    MainGenerationParameter.TopP -> SliderConfig(
        range = 0.00f..1.00f,
        step = 0.05f,
        minLabel = stringResource(R.string.parameter_topp_min_label),
        maxLabel = stringResource(R.string.parameter_topp_max_label)
    )
    MainGenerationParameter.MaxTokens, MainGenerationParameter.ContextTokens -> null
}

@Composable
private fun MainGenerationParameter.quickOptions(): List<NumericEditQuickOption> = when (this) {
    MainGenerationParameter.Temperature -> listOf(
        NumericEditQuickOption(stringResource(R.string.parameter_preset_precise), "0.20"),
        NumericEditQuickOption(stringResource(R.string.parameter_preset_balanced), "0.70"),
        NumericEditQuickOption(stringResource(R.string.parameter_preset_creative), "1.20")
    )
    MainGenerationParameter.TopP -> listOf(
        NumericEditQuickOption(stringResource(R.string.parameter_preset_topp_focused), "0.50"),
        NumericEditQuickOption(stringResource(R.string.parameter_preset_topp_balanced), "0.80"),
        NumericEditQuickOption(stringResource(R.string.parameter_preset_topp_rich), "0.95"),
        NumericEditQuickOption(stringResource(R.string.parameter_preset_topp_full), "1.00")
    )
    MainGenerationParameter.MaxTokens, MainGenerationParameter.ContextTokens -> {
        TokenPreset.entries.map { preset ->
            NumericEditQuickOption(
                label = preset.displayName,
                value = preset.value.toString()
            )
        }
    }
}

@Composable
private fun MainBottomBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val hapticFeedback = LocalHapticFeedback.current
    val targetContentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val targetContainerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        Color.Transparent
    }

    val contentColor by animateColorAsState(targetValue = targetContentColor, label = "bottomBarContentColor")
    val containerColor by animateColorAsState(targetValue = targetContainerColor, label = "bottomBarContainerColor")

    Box(
        modifier = modifier
            .clickable(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            )
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .background(containerColor, CircleShape)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun HeroEntryCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.AddComment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/** 顶部快捷行动双栏卡片（群聊与故事创作）。 */
@Composable
private fun HomeQuickActionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RpIconBubble(icon = icon, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 首页资产快捷管理金刚区（角色卡、世界书、Regex 脚本）。 */
@Composable
private fun HomeAssetDock(
    resourceState: MainHomeResourceState,
    emit: MainUiIntent.() -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HomeAssetCapsule(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.Person,
            title = stringResource(R.string.character),
            count = resourceState.totalCharacters,
            onClick = { MainUiIntent.OpenCharacterManager.emit() }
        )
        HomeAssetCapsule(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.Book,
            title = stringResource(R.string.world_book),
            count = resourceState.totalWorldBooks,
            onClick = { MainUiIntent.OpenWorldBookManager.emit() }
        )
        HomeAssetCapsule(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.DataObject,
            title = stringResource(R.string.regex_script_title),
            count = null,
            onClick = { MainUiIntent.OpenRegexScripts.emit() }
        )
    }
}

/** 单个紧凑资产管理胶囊卡片，带数量 Badge。 */
@Composable
private fun HomeAssetCapsule(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    count: Int? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (count != null) {
                    Surface(
                        modifier = Modifier.offset(x = 6.dp, y = (-4).dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 1.dp
                    ) {
                        Text(
                            text = if (count > 99) "99+" else count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomePage(
    state: MainHomeState,
    emit: MainUiIntent.() -> Unit
) {
    val selectionState = state.selectionState as? MainHomeSelectionState.Selecting
    val multiSelectMode = selectionState != null
    val selectedItems = selectionState?.selectedItems.orEmpty()

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
        if (multiSelectMode) {
            item {
                Text(
                    text = stringResource(R.string.selected_count, selectedItems.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
        } else {
            homeEntryItems(state.resourceState, emit)
        }
        homeContentSection(
            state = state,
            multiSelectMode = multiSelectMode,
            selectedItems = selectedItems,
            emit = emit
        )
    }
}

private fun LazyListScope.homeEntryItems(
    resourceState: MainHomeResourceState,
    emit: MainUiIntent.() -> Unit
) {
    item {
        HeroEntryCard(
            title = stringResource(R.string.new_session),
            subtitle = stringResource(R.string.new_session_desc),
            onClick = { MainUiIntent.OpenCreateChat.emit() }
        )
    }
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeQuickActionCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Groups,
                title = stringResource(R.string.group_chat),
                onClick = { MainUiIntent.OpenCreateGroupChat.emit() }
            )
            HomeQuickActionCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.AutoStories,
                title = stringResource(R.string.story_create_story),
                onClick = { MainUiIntent.OpenCreateStory.emit() }
            )
        }
    }
    item {
        HomeAssetDock(resourceState, emit)
    }
}

private fun LazyListScope.homeContentSection(
    state: MainHomeState,
    multiSelectMode: Boolean,
    selectedItems: Set<MainHomeItemSelection>,
    emit: MainUiIntent.() -> Unit
) {
    item {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.recent_content),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!multiSelectMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = state.selectedContentTab == MainHomeContentTab.All,
                        onClick = {
                            MainUiIntent.SelectHomeContentTab(MainHomeContentTab.All).emit()
                        },
                        label = { Text(stringResource(R.string.home_session_filter_all)) }
                    )
                    FilterChip(
                        selected = state.selectedContentTab == MainHomeContentTab.Single,
                        onClick = {
                            MainUiIntent.SelectHomeContentTab(MainHomeContentTab.Single).emit()
                        },
                        label = { Text(stringResource(R.string.home_session_filter_single)) }
                    )
                    FilterChip(
                        selected = state.selectedContentTab == MainHomeContentTab.Group,
                        onClick = {
                            MainUiIntent.SelectHomeContentTab(MainHomeContentTab.Group).emit()
                        },
                        label = { Text(stringResource(R.string.home_session_filter_group)) }
                    )
                    FilterChip(
                        selected = state.selectedContentTab == MainHomeContentTab.Story,
                        onClick = {
                            MainUiIntent.SelectHomeContentTab(MainHomeContentTab.Story).emit()
                        },
                        label = { Text(stringResource(R.string.home_content_filter_story)) }
                    )
                }
            }
        }
    }

    when (state.selectedContentTab) {
        MainHomeContentTab.All -> {
            if (state.allRecentItems.isEmpty()) {
                item {
                    RpInfoCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.ChatBubble,
                        title = stringResource(R.string.no_recent_content),
                        subtitle = stringResource(R.string.no_recent_content_desc)
                    )
                }
            } else {
                recentAllItems(
                    items = state.allRecentItems,
                    multiSelectMode = multiSelectMode,
                    selectedItems = selectedItems,
                    emit = emit
                )
            }
        }

        MainHomeContentTab.Single -> {
            when (state.recentChatsState) {
                MainRecentChatsState.Empty -> item {
                    RpInfoCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.ChatBubble,
                        title = stringResource(R.string.no_recent_chats),
                        subtitle = stringResource(R.string.no_recent_chats_desc)
                    )
                }

                is MainRecentChatsState.Content -> recentChatSessionItems(
                    state = state.recentChatsState,
                    multiSelectMode = multiSelectMode,
                    selectedItems = selectedItems,
                    emit = emit
                )
            }
        }

        MainHomeContentTab.Group -> {
            when (state.recentGroupChatsState) {
                MainRecentGroupChatsState.Empty -> item {
                    RpInfoCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.Groups,
                        title = stringResource(R.string.no_group_chats),
                        subtitle = stringResource(R.string.no_group_chats_desc)
                    )
                }

                is MainRecentGroupChatsState.Content -> recentGroupChatSessionItems(
                    state = state.recentGroupChatsState,
                    multiSelectMode = multiSelectMode,
                    selectedItems = selectedItems,
                    emit = emit
                )
            }
        }

        MainHomeContentTab.Story -> {
            when (state.recentStoriesState) {
                MainRecentStoriesState.Empty -> item {
                    RpInfoCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.AutoStories,
                        title = stringResource(R.string.story_empty_title),
                        subtitle = stringResource(R.string.story_empty_subtitle)
                    )
                }

                is MainRecentStoriesState.Content -> recentStoryItems(
                    state = state.recentStoriesState,
                    multiSelectMode = multiSelectMode,
                    selectedItems = selectedItems,
                    emit = emit
                )
            }
        }
    }
}

private fun LazyListScope.recentAllItems(
    items: List<MainHomeContentItem>,
    multiSelectMode: Boolean,
    selectedItems: Set<MainHomeItemSelection>,
    emit: MainUiIntent.() -> Unit
) {
    items(
        items = items,
        key = { item ->
            when (item) {
                is MainChatSessionItem -> "session-${item.id}"
                is MainGroupChatSessionItem -> "group-session-${item.id}"
                is MainStoryItem -> "story-${item.id}"
            }
        }
    ) { item ->
        when (item) {
            is MainChatSessionItem -> RecentChatSessionCard(
                session = item,
                multiSelectMode = multiSelectMode,
                selectedItems = selectedItems,
                emit = emit
            )

            is MainGroupChatSessionItem -> RecentGroupChatSessionCard(
                session = item,
                multiSelectMode = multiSelectMode,
                selectedItems = selectedItems,
                emit = emit
            )

            is MainStoryItem -> RecentStoryCard(
                story = item,
                multiSelectMode = multiSelectMode,
                selectedItems = selectedItems,
                emit = emit
            )
        }
    }
}

private fun LazyListScope.recentChatSessionItems(
    state: MainRecentChatsState.Content,
    multiSelectMode: Boolean,
    selectedItems: Set<MainHomeItemSelection>,
    emit: MainUiIntent.() -> Unit
) {
    state.sessionGroups.forEach { group ->
        val characterId = group.characterId
        val expanded = characterId !in state.collapsedCharacterIds
        item(key = "character-$characterId") {
            SessionCharacterHeader(
                modifier = Modifier.animateItem(),
                characterName = group.characterName,
                sessionCount = group.sessions.size,
                expanded = expanded,
                onClick = { MainUiIntent.ToggleSessionGroup(characterId).emit() }
            )
        }
        if (!expanded) return@forEach
        items(
            items = group.sessions,
            key = { session -> "session-${session.id}" }
        ) { session ->
            RecentChatSessionCard(
                session = session,
                multiSelectMode = multiSelectMode,
                selectedItems = selectedItems,
                emit = emit
            )
        }
    }
}

private fun LazyListScope.recentGroupChatSessionItems(
    state: MainRecentGroupChatsState.Content,
    multiSelectMode: Boolean,
    selectedItems: Set<MainHomeItemSelection>,
    emit: MainUiIntent.() -> Unit
) {
    items(
        items = state.sessions,
        key = { "group-session-${it.id}" }
    ) { session ->
        RecentGroupChatSessionCard(
            session = session,
            multiSelectMode = multiSelectMode,
            selectedItems = selectedItems,
            emit = emit
        )
    }
}

private fun LazyListScope.recentStoryItems(
    state: MainRecentStoriesState.Content,
    multiSelectMode: Boolean,
    selectedItems: Set<MainHomeItemSelection>,
    emit: MainUiIntent.() -> Unit
) {
    items(
        items = state.stories,
        key = { "story-${it.id}" }
    ) { story ->
        RecentStoryCard(
            story = story,
            multiSelectMode = multiSelectMode,
            selectedItems = selectedItems,
            emit = emit
        )
    }
}

@Composable
private fun LazyItemScope.RecentChatSessionCard(
    session: MainChatSessionItem,
    multiSelectMode: Boolean,
    selectedItems: Set<MainHomeItemSelection>,
    emit: MainUiIntent.() -> Unit
) {
    val selection = MainHomeItemSelection(MainHomeItemType.Chat, session.id)
    HomeContentCard(
        modifier = Modifier.animateItem(),
        accentKey = session.characterName,
        icon = Icons.Rounded.ChatBubble,
        title = session.title,
        preview = session.preview,
        metadata = listOf(
            session.characterName,
            stringResource(R.string.message_count, session.messageCount),
            session.updatedAt
        ),
        multiSelectMode = multiSelectMode,
        selected = selection in selectedItems,
        onClick = {
            if (multiSelectMode) {
                MainUiIntent.ToggleItemSelection(selection).emit()
            } else {
                MainUiIntent.OpenChat(session.id).emit()
            }
        },
        onLongClick = {
            if (!multiSelectMode) MainUiIntent.EnterMultiSelect(selection).emit()
        },
        trailingContent = {
            HomeItemMenu(
                onRename = {
                    MainUiIntent.ShowRenameItemDialog(selection).emit()
                },
                onDelete = {
                    MainUiIntent.EnterMultiSelect(selection).emit()
                    MainUiIntent.ShowDeleteSelectedDialog.emit()
                }
            )
        }
    )
}

@Composable
private fun LazyItemScope.RecentGroupChatSessionCard(
    session: MainGroupChatSessionItem,
    multiSelectMode: Boolean,
    selectedItems: Set<MainHomeItemSelection>,
    emit: MainUiIntent.() -> Unit
) {
    val selection = MainHomeItemSelection(MainHomeItemType.GroupChat, session.id)
    HomeContentCard(
        modifier = Modifier.animateItem(),
        accentKey = session.title,
        icon = Icons.Rounded.Groups,
        title = session.title,
        preview = session.preview,
        metadata = listOf(
            session.memberNames,
            stringResource(R.string.message_count, session.messageCount),
            session.updatedAt
        ),
        multiSelectMode = multiSelectMode,
        selected = selection in selectedItems,
        onClick = {
            if (multiSelectMode) {
                MainUiIntent.ToggleItemSelection(selection).emit()
            } else {
                MainUiIntent.OpenGroupChat(session.id).emit()
            }
        },
        onLongClick = {
            if (!multiSelectMode) MainUiIntent.EnterMultiSelect(selection).emit()
        },
        trailingContent = {
            HomeItemMenu(
                onRename = {
                    MainUiIntent.ShowRenameItemDialog(selection).emit()
                },
                onDelete = {
                    MainUiIntent.EnterMultiSelect(selection).emit()
                    MainUiIntent.ShowDeleteSelectedDialog.emit()
                }
            )
        }
    )
}

@Composable
private fun LazyItemScope.RecentStoryCard(
    story: MainStoryItem,
    multiSelectMode: Boolean,
    selectedItems: Set<MainHomeItemSelection>,
    emit: MainUiIntent.() -> Unit
) {
    val selection = MainHomeItemSelection(
        type = MainHomeItemType.Story,
        itemId = story.id.toString()
    )
    HomeContentCard(
        modifier = Modifier.animateItem(),
        accentKey = story.title,
        icon = Icons.Rounded.AutoStories,
        title = story.title,
        preview = story.preview.ifBlank {
            stringResource(R.string.story_empty_document_preview)
        },
        metadata = listOf(
            stringResource(
                R.string.story_character_count,
                story.contentCharacterCount
            ),
            story.updatedAt
        ),
        multiSelectMode = multiSelectMode,
        selected = selection in selectedItems,
        onClick = {
            if (multiSelectMode) {
                MainUiIntent.ToggleItemSelection(selection).emit()
            } else {
                MainUiIntent.OpenStory(story.id).emit()
            }
        },
        onLongClick = {
            if (!multiSelectMode) MainUiIntent.EnterMultiSelect(selection).emit()
        },
        trailingContent = {
            HomeItemMenu(
                onRename = {
                    MainUiIntent.ShowRenameItemDialog(selection).emit()
                },
                onDelete = {
                    MainUiIntent.EnterMultiSelect(selection).emit()
                    MainUiIntent.ShowDeleteSelectedDialog.emit()
                }
            )
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeContentCard(
    modifier: Modifier = Modifier,
    accentKey: String,
    icon: ImageVector,
    title: String,
    preview: String,
    metadata: List<String>,
    multiSelectMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    trailingContent: (@Composable () -> Unit)? = null
) {
    val hapticFeedback = LocalHapticFeedback.current
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
        },
        label = "homeContentCardBorder"
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "homeContentCardContainer"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(
            if (selected) 1.dp else 0.5.dp,
            borderColor
        )
    ) {
        val accentColor = remember(accentKey) { getMacaronColor(accentKey) }
        val ambientBrush = remember(accentColor) {
            Brush.horizontalGradient(
                listOf(
                    accentColor.copy(alpha = 0.08f),
                    accentColor.copy(alpha = 0.02f),
                    Color.Transparent
                )
            )
        }
        Row(
            modifier = Modifier
                .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
                .background(ambientBrush),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor, RoundedCornerShape(2.dp))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RpIconBubble(
                        icon = icon,
                        containerColor = accentColor.copy(alpha = 0.14f),
                        contentColor = accentColor,
                        modifier = Modifier.size(38.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                RpMetaRow(items = metadata)
            }
            if (multiSelectMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            } else {
                trailingContent?.invoke()
            }
        }
    }
}

@Composable
private fun HomeItemMenu(
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.more)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rename)) },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onRename()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun SessionCharacterHeader(
    modifier: Modifier = Modifier,
    characterName: String,
    sessionCount: Int,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = characterName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(R.string.session_group_count, sessionCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = stringResource(
                if (expanded) R.string.collapse_session_group else R.string.expand_session_group
            ),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun SettingsPage(
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
        item {
            RpPageTitle(
                title = stringResource(R.string.setting_page_title),
                subtitle = stringResource(R.string.setting_subtitle)
            )
        }

        // ================= 1. 用户身份与人设 =================
        item {
            RpSectionHeader(title = stringResource(R.string.user_identity))
        }
        item { UserIdentityPanel(state.identityState, emit) }

        // ================= 2. 模型与推理服务 =================
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

        // ================= 3. 提示词与上下文记忆 =================
        item {
            RpSectionHeader(title = stringResource(R.string.prompt_and_memory_section))
        }
        item { PromptPresetEntryCard { MainUiIntent.OpenPromptPreset.emit() } }
        item { PromptBehaviorPanel(state.promptBehaviorState, emit) }
        item { WorldInfoBudgetPanel(state.worldInfoBudgetState, emit) }
        item { SummaryPanel(state.summaryState, emit) }

        // ================= 4. 数据与系统 =================
        item {
            RpSectionHeader(title = stringResource(R.string.system_and_data_section))
        }
        item { ChatDataManagementPanel(state.chatDataManagementState, emit) }
        item { TokenUsageEntryCard { emit(MainUiIntent.OpenTokenUsage) } }
        item { DebugPanel(state.debugState, emit) }
        item { AboutEntryCard { emit(MainUiIntent.OpenAbout) } }
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
                    modifier = Modifier.fillMaxWidth()
                )
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
        Surface(
            modifier = Modifier.size(70.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
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
        Surface(
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
            val selectedMode = (postProcessingState as? MainProviderPostProcessingState.Available)?.mode

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
    Surface(
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 0.5.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(
                alpha = 0.22f
            )
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RpIconBubble(
                icon = if (provider.isEnabled) Icons.Rounded.Key else Icons.Rounded.Storage,
                contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                containerColor = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.60f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)
                }
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
            Surface(
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
            onClick = { MainUiIntent.ShowGenerationParameterDialog(MainGenerationParameter.TopP).emit() }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MainSummarySettingsTab.entries.forEach { tab ->
                    val isSelected = tab == state.selectedTab
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { MainUiIntent.SelectSummarySettingsTab(tab).emit() },
                        color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shape = RoundedCornerShape(9.dp),
                        shadowElevation = if (isSelected) 1.dp else 0.dp
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(tab.titleRes()),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            when (state.selectedTab) {
                MainSummarySettingsTab.General -> GeneralSummarySettings(state, emit)
                MainSummarySettingsTab.Conversation -> ConversationSummarySettings(state, emit)
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
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
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
            modifier = Modifier.width(100.dp),
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

private object MaterialThemeLike {
    @Composable
    fun background() = MaterialTheme.colorScheme.background
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun MainLayoutPreview() {
    AppTheme(dynamicColor = false) {
        MainLayout(
            uiState = MainUiState.Normal(
                homeState = MainHomeState(
                    resourceState = MainHomeResourceState(
                        totalCharacters = 24,
                        totalWorldBooks = 7
                    ),
                    recentChatsState = MainRecentChatsState.Content(
                        sessionGroups = listOf(
                            MainChatSessionGroup(
                                characterId = "1",
                                characterName = "Luna",
                                sessions = listOf(
                                    MainChatSessionItem(
                                        id = "1",
                                        characterId = "1",
                                        characterName = "Luna",
                                        title = "Night train",
                                        preview = "The city lights recede beyond the window.",
                                        messageCount = 18,
                                        updatedAt = "06-15 21:30",
                                        latestTime = 1L
                                    )
                                )
                            )
                        )
                    ),
                    recentGroupChatsState = MainRecentGroupChatsState.Content(
                        sessions = listOf(
                            MainGroupChatSessionItem(
                                id = "1",
                                title = "Expedition team",
                                memberNames = "Luna, Aster, Rowan",
                                preview = "We should reach the ruins before sunrise.",
                                messageCount = 42,
                                updatedAt = "06-15 22:10",
                                latestTime = 2L
                            )
                        )
                    ),
                    recentStoriesState = MainRecentStoriesState.Content(
                        stories = listOf(
                            MainStoryItem(
                                id = 1L,
                                title = "Rain over the old city",
                                preview = "The station clock stopped at midnight.",
                                contentCharacterCount = 12840,
                                updatedAt = "06-15 22:30",
                                latestTime = 3L
                            )
                        )
                    ),
                    allRecentItems = listOf(
                        MainStoryItem(
                            id = 1L,
                            title = "Rain over the old city",
                            preview = "The station clock stopped at midnight.",
                            contentCharacterCount = 12840,
                            updatedAt = "06-15 22:30",
                            latestTime = 3L
                        ),
                        MainGroupChatSessionItem(
                            id = "1",
                            title = "Expedition team",
                            memberNames = "Luna, Aster, Rowan",
                            preview = "We should reach the ruins before sunrise.",
                            messageCount = 42,
                            updatedAt = "06-15 22:10",
                            latestTime = 2L
                        ),
                        MainChatSessionItem(
                            id = "1",
                            characterId = "1",
                            characterName = "Luna",
                            title = "Night train",
                            preview = "The city lights recede beyond the window.",
                            messageCount = 18,
                            updatedAt = "06-15 21:30",
                            latestTime = 1L
                        )
                    )
                ),
                settingsState = MainSettingsState(
                    identityState = MainUserIdentityState(
                        userName = "You",
                        userDescription = "",
                        userDescriptionPreview = "",
                        avatarState = MainUserAvatarState.None
                    ),
                    providerState = MainProviderSettingsState.Empty,
                    promptBehaviorState = MainPromptBehaviorState(
                        providerPostProcessingState = MainProviderPostProcessingState.Unavailable,
                        exampleDialogueBehavior = ExampleDialogueBehavior.default,
                        includeThinkInContext = false,
                        contextTrimmingAlert = true,
                        streamEnabled = true
                    ),
                    worldInfoBudgetState = MainWorldInfoBudgetState(
                        budgetPercent = 25,
                        budgetCap = 0,
                        overflowAlert = true
                    ),
                    summaryState = MainSummarySettingsState(
                        autoSummaryEnabled = false,
                        triggerMessageCount = 20,
                        wordsLimit = 500,
                        maxMessagesPerRequest = 0,
                        responseTokens = 800,
                        injectionState = MainSummaryInjectionState.AfterMain
                    ),
                    debugState = MainDebugSettingsState(enabled = false)
                )
            ),
            emit = {}
        )
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
