package me.kafuuneko.rpclient.ui.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspection
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspectionItem
import me.kafuuneko.rpclient.libs.prompt.model.PromptOmissionReason
import me.kafuuneko.rpclient.libs.prompt.model.PromptOmittedItem
import me.kafuuneko.rpclient.libs.prompt.model.PromptSource
import me.kafuuneko.rpclient.libs.prompt.model.PromptSourceKind
import me.kafuuneko.rpclient.libs.prompt.model.PromptTokenizerStrategy
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn

/** 展示最终 Prompt、来源、预算裁剪和 Regex 执行记录的现代化调试对话框。 */
@Composable
fun PromptInspectorDialog(
    inspection: PromptInspection,
    onDismissRequest: () -> Unit,
    onCopyRequest: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(12.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                InspectorHeader(onDismissRequest)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                RpLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item { InspectionSummary(inspection) }

                    if (inspection.omittedItems.isNotEmpty()) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = stringResource(
                                        R.string.prompt_inspector_omitted_title,
                                        inspection.omittedItems.size
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        items(inspection.omittedItems) { OmittedItemCard(it) }
                    }

                    if (inspection.regexExecutions.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(
                                    R.string.prompt_inspector_regex_title,
                                    inspection.regexExecutions.size
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(inspection.regexExecutions) { hit ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                Text(
                                    text = "${hit.scriptName} · ${hit.scope.name} · " +
                                        "${hit.placement.name} · ${hit.mode.name} · " +
                                        stringResource(
                                            if (hit.persisted) {
                                                R.string.regex_persisted
                                            } else {
                                                R.string.regex_temporary
                                            }
                                        ) + " · " +
                                        stringResource(
                                            if (hit.changed) {
                                                R.string.regex_changed
                                            } else {
                                                R.string.regex_matched
                                            }
                                        ),
                                    modifier = Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    if (inspection.regexErrors.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(
                                    R.string.prompt_inspector_regex_errors,
                                    inspection.regexErrors.size
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(inspection.regexErrors) { error ->
                            Text(
                                text = "${error.scriptName}: " +
                                    stringResource(R.string.regex_execution_invalid),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    item {
                        Text(
                            text = stringResource(R.string.prompt_inspector_final_messages),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    itemsIndexed(inspection.items) { _, item ->
                        InspectionItemCard(item, onCopyRequest)
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun InspectorHeader(onDismissRequest: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.DataObject,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.prompt_inspector_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.prompt_inspector_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDismissRequest) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.close)
            )
        }
    }
}

@Composable
private fun InspectionSummary(inspection: PromptInspection) {
    val totalCapacity = (inspection.promptBudget + inspection.responseReserve).coerceAtLeast(1)
    val usageRatio = (inspection.finalTokenCount.toFloat() / totalCapacity.toFloat()).coerceIn(0f, 1f)

    Card(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.prompt_inspector_token_summary,
                    inspection.finalTokenCount,
                    inspection.promptBudget,
                    inspection.responseReserve
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // 可视化 Token 配额进度条
            LinearProgressIndicator(
                progress = { usageRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (usageRatio > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            Text(
                text = stringResource(
                    R.string.prompt_inspector_model,
                    inspection.model.ifBlank { stringResource(R.string.prompt_unknown_model) }
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(
                    R.string.prompt_inspector_tokenizer,
                    inspection.tokenizerName,
                    tokenizerStrategyLabel(inspection)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(
                    R.string.prompt_inspector_post_processing,
                    inspection.postProcessingMode.name
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun tokenizerStrategyLabel(inspection: PromptInspection): String {
    val strategy = stringResource(
        when (inspection.tokenizerStrategy) {
            PromptTokenizerStrategy.ModelAware -> R.string.prompt_tokenizer_model_aware
            PromptTokenizerStrategy.Estimated -> R.string.prompt_tokenizer_estimated
            PromptTokenizerStrategy.Conservative -> R.string.prompt_tokenizer_conservative
        }
    )
    if (inspection.tokenizerReservePercent <= 0) return strategy
    return "$strategy · ${stringResource(
        R.string.prompt_tokenizer_reserve,
        inspection.tokenizerReservePercent
    )}"
}

@Composable
private fun OmittedItemCard(item: PromptOmittedItem) {
    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.source.label(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = stringResource(
                    R.string.prompt_inspector_omitted_detail,
                    item.tokenCount,
                    stringResource(
                        if (item.reason == PromptOmissionReason.WorldInfoBudget) {
                            R.string.prompt_omission_world_info_budget
                        } else {
                            R.string.prompt_omission_context_budget
                        }
                    )
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun InspectionItemCard(
    item: PromptInspectionItem,
    onCopyRequest: (String) -> Unit
) {
    val sourceLabel = promptSourcesLabel(item.sources)

    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${item.index}  ${item.role.name.uppercase()}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = stringResource(R.string.prompt_inspector_item_tokens, item.tokenCount),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    IconButton(
                        onClick = { onCopyRequest(item.content) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.copy),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Text(
                text = sourceLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            SelectionContainer {
                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun promptSourcesLabel(sources: List<PromptSource>): String {
    val labels = mutableListOf<String>()
    for (source in sources) {
        labels += source.label()
    }
    return labels.joinToString(" + ")
}

@Composable
private fun PromptSource.label(): String {
    val base = stringResource(
        when (kind) {
            PromptSourceKind.MainPrompt -> R.string.prompt_source_main
            PromptSourceKind.WorldInfo -> R.string.prompt_source_world_info
            PromptSourceKind.UserPersona -> R.string.prompt_source_user_persona
            PromptSourceKind.CharacterDescription -> R.string.prompt_source_character_description
            PromptSourceKind.CharacterPersonality -> R.string.prompt_source_character_personality
            PromptSourceKind.Scenario -> R.string.prompt_source_scenario
            PromptSourceKind.Summary -> R.string.prompt_source_summary
            PromptSourceKind.AuxiliaryPrompt -> R.string.prompt_source_auxiliary
            PromptSourceKind.ExampleDialogue -> R.string.prompt_source_examples
            PromptSourceKind.NewChatMarker -> R.string.prompt_source_new_chat
            PromptSourceKind.ChatHistory -> R.string.prompt_source_history
            PromptSourceKind.UserNote -> R.string.prompt_source_user_note
            PromptSourceKind.CharacterNote -> R.string.prompt_source_character_note
            PromptSourceKind.PostHistoryInstructions -> R.string.prompt_source_post_history
            PromptSourceKind.CharacterReplyNudge -> R.string.prompt_source_character_reply_nudge
            PromptSourceKind.ContinueNudge -> R.string.prompt_source_continue
            PromptSourceKind.ImpersonationNudge -> R.string.prompt_source_impersonation
            PromptSourceKind.GroupIdentity -> R.string.prompt_source_group_identity
            PromptSourceKind.CharacterCard -> R.string.prompt_source_character_card
            PromptSourceKind.GroupNudge -> R.string.prompt_source_group_nudge
            PromptSourceKind.StoryMainPrompt -> R.string.prompt_source_story_main
            PromptSourceKind.StoryMemory -> R.string.prompt_source_story_memory
            PromptSourceKind.StorySummary -> R.string.prompt_source_story_summary
            PromptSourceKind.StoryAuthorNote -> R.string.prompt_source_story_author_note
            PromptSourceKind.StoryCharacter -> R.string.prompt_source_story_character
            PromptSourceKind.StoryDocumentContext -> R.string.prompt_source_story_document
            PromptSourceKind.StoryContinuationGuidance -> R.string.prompt_source_story_continuation_guidance
            PromptSourceKind.StoryTask -> R.string.prompt_source_story_task
            PromptSourceKind.PostProcessing -> R.string.prompt_source_post_processing
            PromptSourceKind.Other -> R.string.prompt_source_other
        }
    )
    return if (detail.isBlank()) base else "$base · $detail"
}
