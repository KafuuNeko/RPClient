package me.kafuuneko.rpclient.feature.main.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.main.model.MainGenerationParameter
import me.kafuuneko.rpclient.feature.main.presentation.MainDialogState
import me.kafuuneko.rpclient.feature.main.presentation.MainHomeSelectionState
import me.kafuuneko.rpclient.feature.main.presentation.MainPage
import me.kafuuneko.rpclient.feature.main.presentation.MainUiIntent
import me.kafuuneko.rpclient.feature.main.presentation.MainUiState
import me.kafuuneko.rpclient.libs.appearance.PageSurfaceKind
import me.kafuuneko.rpclient.libs.search.SearchIndexState
import me.kafuuneko.rpclient.model.TokenPreset
import me.kafuuneko.rpclient.ui.dialog.AppConfirmDialog
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.dialog.AppInputDialog
import me.kafuuneko.rpclient.ui.dialog.AppPromptEditorDialog
import me.kafuuneko.rpclient.ui.dialog.LoadingDialog
import me.kafuuneko.rpclient.ui.dialog.NumericEditDialog
import me.kafuuneko.rpclient.ui.dialog.NumericEditQuickOption
import me.kafuuneko.rpclient.ui.dialog.SliderConfig
import me.kafuuneko.rpclient.ui.widgets.AppPageScaffold
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.PageOverlayHost
import me.kafuuneko.rpclient.ui.widgets.pageCanvasColor
import androidx.compose.runtime.saveable.rememberSaveableStateHolder

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
    // 子页离开组合时保存各自列表与可恢复的局部状态，切回后恢复原位置。
    val pageStateHolder = rememberSaveableStateHolder()
    AppPageScaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(pageCanvasColor()),
        contentWindowInsets = WindowInsets(0.dp)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(pageCanvasColor(MaterialTheme.colorScheme.background))
        ) {
            // 滚动内容单独记录为后景，底栏不会把后方卡片擦成壁纸。
            PageOverlayHost(content = {
                pageStateHolder.SaveableStateProvider(uiState.selectedPage.name) {
                    when (uiState.selectedPage) {
                        MainPage.Home -> MainHomePage(uiState.homeState, emit)
                        MainPage.Settings -> MainSettingsPage(uiState.settingsState, emit)
                    }
                }
            }, overlay = {
                Box(Modifier.fillMaxSize()) {
                    val selectionState = uiState.homeState.selectionState as? MainHomeSelectionState.Selecting
                    val barModifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .navigationBarsPadding()
                    if (selectionState != null && uiState.selectedPage == MainPage.Home) {
                        MultiSelectBottomBar(barModifier, selectionState.selectedItems.size, emit)
                    } else {
                        MainBottomBar(barModifier, uiState.selectedPage, emit)
                    }
                }
            })
        }
    }

    DialogSwitch(
        dialogState = uiState.dialogState,
        searchIndexState = uiState.settingsState.searchIndexState.takeIf { uiState.selectedPage == MainPage.Settings },
        emit = emit
    )
}

@Composable
private fun MainBottomBar(
    modifier: Modifier = Modifier,
    selectedPage: MainPage,
    emit: MainUiIntent.() -> Unit
) {
    AppSurface(
        kind = PageSurfaceKind.BottomBar,
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
    AppSurface(
        kind = PageSurfaceKind.BottomBar,
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

/** 按主页状态分发互斥弹窗，搜索维护窗口只在设置页展示。 */
@Composable
private fun DialogSwitch(
    dialogState: MainDialogState,
    searchIndexState: SearchIndexState?,
    emit: MainUiIntent.() -> Unit
) {
    // 索引进度仅由设置页展示，并与其他业务窗口互斥。
    when (dialogState) {
        MainDialogState.None -> SearchIndexProgressDialog(searchIndexState, emit)

        MainDialogState.EnableSearchIndex -> AppConfirmDialog(
            onDismissRequest = { MainUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.content_search_index_confirm_title),
            message = stringResource(R.string.content_search_index_confirm_message),
            onConfirm = { MainUiIntent.ConfirmEnableSearchIndex.emit() }
        )

        MainDialogState.DisableSearchIndex -> AppDangerDialog(
            onDismissRequest = { MainUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.content_search_index_disable_confirm_title),
            message = stringResource(R.string.content_search_index_disable_confirm_message),
            confirmText = stringResource(R.string.confirm),
            onConfirm = { MainUiIntent.ConfirmDisableSearchIndex.emit() }
        )

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

/** 维护进度来自应用级任务，旋转页面可恢复；关闭失败后仍能重试清理。 */
@Composable
private fun SearchIndexProgressDialog(index: SearchIndexState?, emit: MainUiIntent.() -> Unit) {
    // 只有构建允许取消，清理完成前不能再次开启或隐藏进度。
    when (index) {
        is SearchIndexState.Building -> LoadingDialog(
            title = stringResource(R.string.content_search_index_title),
            description = stringResource(R.string.content_search_index_building, index.processed),
            onCancel = { MainUiIntent.CancelSearchIndexBuild.emit() },
            cancelOnBackPress = true
        )
        SearchIndexState.Preparing -> LoadingDialog(
            title = stringResource(R.string.content_search_index_title),
            description = stringResource(R.string.loading)
        )
        SearchIndexState.Cleaning -> LoadingDialog(
            title = stringResource(R.string.content_search_index_title),
            description = stringResource(R.string.content_search_index_cleaning)
        )
        SearchIndexState.CleanupFailed -> AppConfirmDialog(
            onDismissRequest = { MainUiIntent.Back.emit() },
            title = stringResource(R.string.content_search_index_title),
            message = stringResource(R.string.content_search_index_cleanup_failed),
            confirmText = stringResource(R.string.retry),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { MainUiIntent.RetrySearchIndexCleanup.emit() },
            properties = DialogProperties(dismissOnClickOutside = false)
        )
        else -> Unit
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

    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        label = "bottomBarContentColor"
    )
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        label = "bottomBarContainerColor"
    )

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
