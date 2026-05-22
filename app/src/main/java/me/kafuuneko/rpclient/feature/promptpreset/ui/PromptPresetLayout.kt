package me.kafuuneko.rpclient.feature.promptpreset.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.promptpreset.model.PromptType
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetDialogState
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetUiIntent
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetUiState
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader

@Composable
fun PromptPresetLayout(
    uiState: PromptPresetUiState,
    emit: PromptPresetUiIntent.() -> Unit = {}
) {
    BackHandler { PromptPresetUiIntent.Back.emit() }
    when (uiState) {
        PromptPresetUiState.None, PromptPresetUiState.Finished -> Unit
        is PromptPresetUiState.Normal -> {
            NormalView(uiState, emit)
            DialogSwitch(uiState.dialogState, emit)
        }
    }
}

@Composable
private fun NormalView(
    uiState: PromptPresetUiState.Normal,
    emit: PromptPresetUiIntent.() -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.prompt_preset_title),
                onBack = { PromptPresetUiIntent.Back.emit() }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(top = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                RpPageTitle(
                    title = stringResource(R.string.prompt_preset_title),
                    subtitle = stringResource(R.string.prompt_preset_subtitle)
                )
            }
            promptGroups.forEach { group ->
                item {
                    RpSectionHeader(title = stringResource(group.titleRes))
                }
                group.types.forEach { type ->
                    item {
                        PromptCard(
                            icon = type.icon(),
                            title = stringResource(type.titleRes()),
                            description = stringResource(type.descriptionRes()),
                            promptPreview = uiState.promptValues[type].orEmpty(),
                            onClick = { PromptPresetUiIntent.EditPromptClick(type).emit() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptCard(
    icon: ImageVector,
    title: String,
    description: String,
    promptPreview: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // 内容预览框
            val hasContent = promptPreview.isNotBlank()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (hasContent)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (hasContent)
                        promptPreview.take(120).let { if (promptPreview.length > 120) "$it..." else it }
                    else
                        stringResource(R.string.prompt_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasContent)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }

            // 编辑标签
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(R.string.prompt_edit_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private data class PromptGroup(
    val titleRes: Int,
    val types: List<PromptType>
)

private val promptGroups = listOf(
    PromptGroup(
        titleRes = R.string.prompt_manager_section,
        types = listOf(
            PromptType.Main,
            PromptType.Auxiliary,
            PromptType.PostHistory
        )
    ),
    PromptGroup(
        titleRes = R.string.prompt_utility_section,
        types = listOf(
            PromptType.Impersonation,
            PromptType.NewChat,
            PromptType.NewExampleChat,
            PromptType.ContinueNudge,
            PromptType.ReplaceEmptyMessage
        )
    ),
    PromptGroup(
        titleRes = R.string.prompt_format_section,
        types = listOf(
            PromptType.WorldInfoFormat,
            PromptType.ScenarioFormat,
            PromptType.PersonalityFormat
        )
    ),
    PromptGroup(
        titleRes = R.string.prompt_summary_section,
        types = listOf(PromptType.Summarize)
    )
)

private fun PromptType.icon(): ImageVector {
    return when (this) {
        PromptType.Summarize -> Icons.Rounded.Compress
        else -> Icons.Rounded.AutoAwesome
    }
}

private fun PromptType.titleRes(): Int {
    return when (this) {
        PromptType.Main -> R.string.prompt_main_title
        PromptType.Auxiliary -> R.string.prompt_auxiliary_title
        PromptType.PostHistory -> R.string.prompt_post_history_title
        PromptType.Summarize -> R.string.prompt_summarize_title
        PromptType.Impersonation -> R.string.prompt_impersonation_title
        PromptType.NewChat -> R.string.prompt_new_chat_title
        PromptType.NewExampleChat -> R.string.prompt_new_example_chat_title
        PromptType.ContinueNudge -> R.string.prompt_continue_nudge_title
        PromptType.ReplaceEmptyMessage -> R.string.prompt_replace_empty_message_title
        PromptType.WorldInfoFormat -> R.string.prompt_world_info_format_title
        PromptType.ScenarioFormat -> R.string.prompt_scenario_format_title
        PromptType.PersonalityFormat -> R.string.prompt_personality_format_title
    }
}

private fun PromptType.descriptionRes(): Int {
    return when (this) {
        PromptType.Main -> R.string.prompt_main_desc
        PromptType.Auxiliary -> R.string.prompt_auxiliary_desc
        PromptType.PostHistory -> R.string.prompt_post_history_desc
        PromptType.Summarize -> R.string.prompt_summarize_desc
        PromptType.Impersonation -> R.string.prompt_impersonation_desc
        PromptType.NewChat -> R.string.prompt_new_chat_desc
        PromptType.NewExampleChat -> R.string.prompt_new_example_chat_desc
        PromptType.ContinueNudge -> R.string.prompt_continue_nudge_desc
        PromptType.ReplaceEmptyMessage -> R.string.prompt_replace_empty_message_desc
        PromptType.WorldInfoFormat -> R.string.prompt_world_info_format_desc
        PromptType.ScenarioFormat -> R.string.prompt_scenario_format_desc
        PromptType.PersonalityFormat -> R.string.prompt_personality_format_desc
    }
}

@Composable
private fun DialogSwitch(
    dialogState: PromptPresetDialogState,
    emit: PromptPresetUiIntent.() -> Unit
) {
    when (dialogState) {
        is PromptPresetDialogState.None -> Unit
        is PromptPresetDialogState.EditPrompt -> EditPromptDialog(
            dialogState = dialogState,
            onDismiss = { PromptPresetUiIntent.DismissPromptDialog.emit() },
            onSave = { text -> PromptPresetUiIntent.SavePrompt(text).emit() }
        )
    }
}

@Composable
private fun EditPromptDialog(
    dialogState: PromptPresetDialogState.EditPrompt,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var textFieldValue by remember(dialogState.type, dialogState.currentText) {
        mutableStateOf(TextFieldValue(dialogState.currentText))
    }

    val titleRes = dialogState.type.titleRes()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶部栏
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = stringResource(titleRes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = { onSave(textFieldValue.text) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.save))
                        }
                    }
                }

                // 编辑区
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.prompt_editor_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(14.dp)
                    ) {
                        if (textFieldValue.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.prompt_editor_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            )
                        }
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = { textFieldValue = it },
                            modifier = Modifier.fillMaxSize(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun PromptPresetLayoutPreview() {
    AppTheme(dynamicColor = false) {
        PromptPresetLayout(
            uiState = PromptPresetUiState.Normal(
                promptValues = mapOf(
                    PromptType.Main to "You are a creative roleplay assistant. Stay in character and respond naturally...",
                    PromptType.Summarize to ""
                )
            )
        )
    }
}
