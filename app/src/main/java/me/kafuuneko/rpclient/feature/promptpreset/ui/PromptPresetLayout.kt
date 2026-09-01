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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.promptpreset.model.PromptType
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetDialogState
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetUiIntent
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetUiState
import me.kafuuneko.rpclient.ui.dialog.AppPromptEditorDialog
import me.kafuuneko.rpclient.ui.dialog.DialogBadgeTone
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader

/** Prompt 预设列表与编辑对话框的 Compose 入口。 */
@Composable
fun PromptPresetLayout(
    uiState: PromptPresetUiState,
    emit: PromptPresetUiIntent.() -> Unit = {}
) {
    BackHandler(enabled = uiState is PromptPresetUiState.Normal) { PromptPresetUiIntent.Back.emit() }
    when (uiState) {
        PromptPresetUiState.None -> Unit
        is PromptPresetUiState.Finished -> PromptPresetLayout(uiState.previous) {}
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
            .fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.prompt_preset_title),
                onBack = { PromptPresetUiIntent.Back.emit() }
            )
        }
    ) { paddingValues ->
        RpLazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .navigationBarsPadding()
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
        shape = RoundedCornerShape(16.dp),
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
                    shape = RoundedCornerShape(12.dp),
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
                    .clip(RoundedCornerShape(12.dp))
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
            PromptType.ReplaceEmptyMessage,
            PromptType.GroupNudge,
            PromptType.NewGroupChat
        )
    ),
    PromptGroup(
        titleRes = R.string.prompt_story_section,
        types = listOf(
            PromptType.StoryMain,
            PromptType.StoryMemory,
            PromptType.StorySummary,
            PromptType.StorySummarize,
            PromptType.StoryContinuationGuidance,
            PromptType.StoryContinue
        )
    ),
    PromptGroup(
        titleRes = R.string.prompt_format_section,
        types = listOf(
            PromptType.WorldInfoFormat,
            PromptType.ScenarioFormat,
            PromptType.PersonalityFormat,
            PromptType.UserPersonaFormat
        )
    ),
    PromptGroup(
        titleRes = R.string.prompt_summary_section,
        types = listOf(
            PromptType.Summarize,
            PromptType.GroupSummarize,
            PromptType.SummaryInjection
        )
    )
)

private fun PromptType.icon(): ImageVector {
    return when (this) {
        PromptType.Summarize -> Icons.Rounded.Compress
        PromptType.GroupSummarize -> Icons.Rounded.Compress
        PromptType.StoryMemory -> Icons.Rounded.Compress
        PromptType.StorySummary -> Icons.Rounded.Compress
        PromptType.StorySummarize -> Icons.Rounded.Compress
        PromptType.SummaryInjection -> Icons.Rounded.Compress
        else -> Icons.Rounded.AutoAwesome
    }
}

private fun PromptType.titleRes(): Int {
    return when (this) {
        PromptType.Main -> R.string.prompt_main_title
        PromptType.Auxiliary -> R.string.prompt_auxiliary_title
        PromptType.PostHistory -> R.string.prompt_post_history_title
        PromptType.Summarize -> R.string.prompt_summarize_title
        PromptType.SummaryInjection -> R.string.summary_injection_template
        PromptType.Impersonation -> R.string.prompt_impersonation_title
        PromptType.NewChat -> R.string.prompt_new_chat_title
        PromptType.NewExampleChat -> R.string.prompt_new_example_chat_title
        PromptType.ContinueNudge -> R.string.prompt_continue_nudge_title
        PromptType.ReplaceEmptyMessage -> R.string.prompt_replace_empty_message_title
        PromptType.WorldInfoFormat -> R.string.prompt_world_info_format_title
        PromptType.ScenarioFormat -> R.string.prompt_scenario_format_title
        PromptType.PersonalityFormat -> R.string.prompt_personality_format_title
        PromptType.UserPersonaFormat -> R.string.prompt_user_persona_format_title
        PromptType.GroupNudge -> R.string.prompt_group_nudge_title
        PromptType.NewGroupChat -> R.string.prompt_new_group_chat_title
        PromptType.GroupSummarize -> R.string.prompt_group_summarize_title
        PromptType.StoryMain -> R.string.prompt_story_main_title
        PromptType.StoryMemory -> R.string.prompt_story_memory_title
        PromptType.StorySummary -> R.string.prompt_story_summary_title
        PromptType.StorySummarize -> R.string.prompt_story_summarize_title
        PromptType.StoryContinuationGuidance -> R.string.prompt_story_continuation_guidance_title
        PromptType.StoryContinue -> R.string.prompt_story_continue_title
    }
}

private fun PromptType.descriptionRes(): Int {
    return when (this) {
        PromptType.Main -> R.string.prompt_main_desc
        PromptType.Auxiliary -> R.string.prompt_auxiliary_desc
        PromptType.PostHistory -> R.string.prompt_post_history_desc
        PromptType.Summarize -> R.string.prompt_summarize_desc
        PromptType.SummaryInjection -> R.string.summary_injection_template_desc
        PromptType.Impersonation -> R.string.prompt_impersonation_desc
        PromptType.NewChat -> R.string.prompt_new_chat_desc
        PromptType.NewExampleChat -> R.string.prompt_new_example_chat_desc
        PromptType.ContinueNudge -> R.string.prompt_continue_nudge_desc
        PromptType.ReplaceEmptyMessage -> R.string.prompt_replace_empty_message_desc
        PromptType.WorldInfoFormat -> R.string.prompt_world_info_format_desc
        PromptType.ScenarioFormat -> R.string.prompt_scenario_format_desc
        PromptType.PersonalityFormat -> R.string.prompt_personality_format_desc
        PromptType.UserPersonaFormat -> R.string.prompt_user_persona_format_desc
        PromptType.GroupNudge -> R.string.prompt_group_nudge_desc
        PromptType.NewGroupChat -> R.string.prompt_new_group_chat_desc
        PromptType.GroupSummarize -> R.string.prompt_group_summarize_desc
        PromptType.StoryMain -> R.string.prompt_story_main_desc
        PromptType.StoryMemory -> R.string.prompt_story_memory_desc
        PromptType.StorySummary -> R.string.prompt_story_summary_desc
        PromptType.StorySummarize -> R.string.prompt_story_summarize_desc
        PromptType.StoryContinuationGuidance -> R.string.prompt_story_continuation_guidance_desc
        PromptType.StoryContinue -> R.string.prompt_story_continue_desc
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
            onChange = { PromptPresetUiIntent.ChangePromptDraft(it).emit() },
            onCopy = { PromptPresetUiIntent.CopyPromptDraft.emit() },
            onSave = { PromptPresetUiIntent.SavePrompt.emit() }
        )
    }
}

@Composable
private fun EditPromptDialog(
    dialogState: PromptPresetDialogState.EditPrompt,
    onDismiss: () -> Unit,
    onChange: (String) -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit
) {
    AppPromptEditorDialog(
        onDismissRequest = onDismiss,
        title = stringResource(dialogState.type.titleRes()),
        subtitle = stringResource(dialogState.type.descriptionRes()),
        badgeIcon = dialogState.type.icon(),
        badgeTone = DialogBadgeTone.Primary,
        value = dialogState.draftText,
        onValueChange = onChange,
        defaultValue = dialogState.defaultText,
        availableMacros = dialogState.availableMacros,
        onCopyRequest = { onCopy() },
        onConfirm = onSave
    )
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun PromptPresetLayoutPreview() {
    AppTheme(dynamicColor = false) {
        PromptPresetLayout(
            uiState = PromptPresetUiState.Normal(
                promptValues = mapOf(
                    PromptType.Main to "You are a creative roleplay assistant. Stay in character and respond naturally...",
                    PromptType.Summarize to "",
                    PromptType.SummaryInjection to "Story memory:\n{{summary}}"
                )
            )
        )
    }
}
