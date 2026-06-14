package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.prompt.PromptInspection
import me.kafuuneko.rpclient.libs.prompt.PromptInspectionItem
import me.kafuuneko.rpclient.libs.prompt.PromptOmissionReason
import me.kafuuneko.rpclient.libs.prompt.PromptOmittedItem
import me.kafuuneko.rpclient.libs.prompt.PromptSource
import me.kafuuneko.rpclient.libs.prompt.PromptSourceKind
import me.kafuuneko.rpclient.libs.prompt.PromptTokenizerStrategy

/** 展示最终 Prompt、来源、预算裁剪和 Regex 执行记录的调试对话框。 */
@Composable
fun PromptInspectorDialog(
    inspection: PromptInspection,
    onDismissRequest: () -> Unit
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
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                InspectorHeader(onDismissRequest)
                HorizontalDivider()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { InspectionSummary(inspection) }
                    if (inspection.omittedItems.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(
                                    R.string.prompt_inspector_omitted_title,
                                    inspection.omittedItems.size
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
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
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        items(inspection.regexExecutions) { hit ->
                            Card(
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
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
                                        if (hit.changed) "changed" else "matched",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall
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
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        items(inspection.regexErrors) { error ->
                            Text(
                                text = "${error.scriptName}: ${error.message}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    item {
                        Text(
                            text = stringResource(R.string.prompt_inspector_final_messages),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    itemsIndexed(inspection.items) { _, item ->
                        InspectionItemCard(item)
                    }
                    item { Spacer(modifier = Modifier.height(4.dp)) }
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
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.prompt_inspector_token_summary,
                    inspection.finalTokenCount,
                    inspection.promptBudget,
                    inspection.responseReserve
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
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
                    stringResource(
                        if (inspection.tokenizerStrategy == PromptTokenizerStrategy.ModelAware) {
                            R.string.prompt_tokenizer_model_aware
                        } else {
                            R.string.prompt_tokenizer_conservative
                        }
                    )
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
private fun OmittedItemCard(item: PromptOmittedItem) {
    Card(
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = item.source.label(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InspectionItemCard(item: PromptInspectionItem) {
    val sourceLabel = promptSourcesLabel(item.sources)
    Card(
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "#${item.index}  ${item.role.name.uppercase()}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.prompt_inspector_item_tokens, item.tokenCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = sourceLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            SelectionContainer {
                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    )
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
            PromptSourceKind.PostProcessing -> R.string.prompt_source_post_processing
            PromptSourceKind.Other -> R.string.prompt_source_other
        }
    )
    return if (detail.isBlank()) base else "$base · $detail"
}
