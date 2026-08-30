package me.kafuuneko.rpclient.feature.llmprovideredit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.llmprovideredit.model.CredentialEditMode
import me.kafuuneko.rpclient.feature.llmprovideredit.model.LLMProviderEditForm
import me.kafuuneko.rpclient.feature.llmprovideredit.model.ProviderPreset
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditDialogState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditLoadState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditMode
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditModelCatalogState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditRequestExtensionsState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditTestState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditUiIntent
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditUiState
import me.kafuuneko.rpclient.libs.llm.catalog.LLMModelCatalogFailure
import me.kafuuneko.rpclient.libs.llm.catalog.model.LLMAvailableModel
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderCapabilities
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.room.entity.MAX_TOKEN_ESTIMATE_RESERVE_PERCENT
import me.kafuuneko.rpclient.libs.room.entity.MIN_TOKEN_ESTIMATE_RESERVE_PERCENT
import me.kafuuneko.rpclient.model.TokenPreset
import me.kafuuneko.rpclient.ui.dialog.AppCodeEditorDialog
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.dialog.AppDialogScaffold
import me.kafuuneko.rpclient.ui.dialog.AppInputDialog
import me.kafuuneko.rpclient.ui.dialog.DialogBadgeTone
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.utils.JsonSyntaxTokenType
import me.kafuuneko.rpclient.utils.rememberDefaultJsonSyntaxColors
import me.kafuuneko.rpclient.utils.tokenizeJsonSyntax
import kotlin.math.roundToInt
import me.kafuuneko.rpclient.ui.widgets.RpPanel as Panel

/** 模型配置创建与编辑页 Compose 入口。 */
@Composable
fun LLMProviderEditLayout(
    uiState: LLMProviderEditUiState,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is LLMProviderEditUiState.Normal) { LLMProviderEditUiIntent.Back.emit() }
    when (uiState) {
        LLMProviderEditUiState.None -> Unit
        is LLMProviderEditUiState.Finished -> LLMProviderEditLayout(uiState.previous) {}
        is LLMProviderEditUiState.Normal -> {
            LLMProviderEditNormal(uiState, emit)
            DialogSwitch(uiState.dialogState, emit)
        }
    }
}

@Composable
private fun LLMProviderEditNormal(
    state: LLMProviderEditUiState.Normal,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = if (state.mode == LLMProviderEditMode.Create) {
                stringResource(R.string.create_model_title)
            } else {
                stringResource(R.string.edit_model_title)
            },
            onBack = { LLMProviderEditUiIntent.Back.emit() },
            actions = {
                TopBarSaveButton(state, emit)
            }
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                )
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                RpPageTitle(
                    title = if (state.mode == LLMProviderEditMode.Create) {
                        stringResource(R.string.create_model_subtitle)
                    } else {
                        state.form.name.ifBlank { stringResource(R.string.model_provider_title) }
                    },
                    subtitle = stringResource(R.string.edit_model_subtitle)
                )
            }
            if (state.mode == LLMProviderEditMode.Create) {
                item {
                    ProviderPresetsSection(
                        selectedType = state.form.providerType,
                        selectedBaseUrl = state.form.baseUrl,
                        onSelectPreset = { preset ->
                            LLMProviderEditUiIntent.ApplyPresetTemplate(preset).emit()
                        }
                    )
                }
            }
            item { BasicPanel(state.form, state.modelCatalogState, emit) }
            item { ParameterPanel(state.form, emit) }
            item {
                CollapsibleAdvancedPanel(
                    form = state.form,
                    requestExtensionsState = state.requestExtensionsState,
                    emit = emit
                )
            }
            item { TestPanel(state.testState, emit) }
        }
    }
}

@Composable
private fun ProviderPresetsSection(
    selectedType: LLMProviderType,
    selectedBaseUrl: String,
    onSelectPreset: (ProviderPreset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.quick_presets),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(ProviderPreset.entries) { preset ->
                val isSelected = preset.providerType == selectedType &&
                        (preset == ProviderPreset.Custom || preset.baseUrl == selectedBaseUrl)
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectPreset(preset) },
                    label = { Text(preset.displayName) },
                    leadingIcon = {
                        val icon = when (preset) {
                            ProviderPreset.DeepSeek -> Icons.Rounded.Memory
                            ProviderPreset.Gemini -> Icons.Rounded.AutoAwesome
                            ProviderPreset.Claude -> Icons.Rounded.Psychology
                            ProviderPreset.ChatGPT -> Icons.Rounded.SmartToy
                            ProviderPreset.OpenRouter -> Icons.Rounded.Hub
                            ProviderPreset.Grok -> Icons.Rounded.Bolt
                            ProviderPreset.Custom -> Icons.Rounded.Dns
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(
                            alpha = 0.7f
                        ),
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    }
}

@Composable
private fun BasicPanel(
    form: LLMProviderEditForm,
    modelCatalogState: LLMProviderEditModelCatalogState,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    Panel {
        RpSectionHeader(title = stringResource(R.string.basic_info))
        FormTextField(
            stringResource(R.string.name),
            form.name
        ) { LLMProviderEditUiIntent.ChangeName(it).emit() }
        FormTextField(
            stringResource(R.string.base_url),
            form.baseUrl
        ) { LLMProviderEditUiIntent.ChangeBaseUrl(it).emit() }
        ModernCredentialControl(
            title = stringResource(R.string.api_key),
            icon = Icons.Rounded.Key,
            hasExistingValue = form.hasExistingApiKey,
            editMode = form.apiKeyEditMode,
            onEdit = { LLMProviderEditUiIntent.ShowApiKeyEditor.emit() },
            onClear = { LLMProviderEditUiIntent.ClearApiKey.emit() },
            onKeepExisting = { LLMProviderEditUiIntent.KeepExistingApiKey.emit() }
        )
        ModelField(
            value = form.model,
            catalogState = modelCatalogState,
            emit = emit
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.enabled), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.enabled_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Switch(
                checked = form.isEnabled,
                onCheckedChange = { LLMProviderEditUiIntent.ToggleEnabled(it).emit() }
            )
        }
    }
}

@Composable
private fun ModelField(
    value: String,
    catalogState: LLMProviderEditModelCatalogState,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = {
                LLMProviderEditUiIntent.ChangeModel(it).emit()
            },
            label = { Text(stringResource(R.string.model_name)) },
            trailingIcon = {
                val loading = catalogState is LLMProviderEditModelCatalogState.Loading
                IconButton(
                    onClick = {
                        if (loading) {
                            LLMProviderEditUiIntent.CancelModelQuery.emit()
                        } else {
                            LLMProviderEditUiIntent.QueryModels.emit()
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (loading) {
                            Icons.Rounded.Close
                        } else {
                            Icons.Rounded.Refresh
                        },
                        contentDescription = stringResource(
                            if (loading) {
                                R.string.cancel_model_query
                            } else {
                                R.string.query_models
                            }
                        )
                    )
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
        ModelCatalogSupportingView(catalogState, emit)
    }
}

@Composable
private fun ModelCatalogSupportingView(
    state: LLMProviderEditModelCatalogState,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    when (state) {
        LLMProviderEditModelCatalogState.Idle -> {
            Text(
                text = stringResource(R.string.model_manual_input_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        LLMProviderEditModelCatalogState.Loading -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 4.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = stringResource(R.string.querying_models),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        is LLMProviderEditModelCatalogState.Loaded -> {
            if (state.models.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_available_models_returned),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { LLMProviderEditUiIntent.ShowModelPicker.emit() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(
                                R.string.available_models_found,
                                state.models.size
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        is LLMProviderEditModelCatalogState.Failed -> {
            Text(
                text = modelCatalogFailureText(state.failure),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun modelCatalogFailureText(failure: LLMModelCatalogFailure): String {
    return when (failure) {
        LLMModelCatalogFailure.Unauthorized -> stringResource(R.string.generation_error_unauthorized)
        LLMModelCatalogFailure.Forbidden -> stringResource(R.string.generation_error_forbidden)
        LLMModelCatalogFailure.RateLimited -> stringResource(R.string.generation_error_rate_limited)
        LLMModelCatalogFailure.UnsupportedEndpoint -> stringResource(R.string.model_query_unsupported)
        LLMModelCatalogFailure.Network -> stringResource(R.string.generation_error_network)
        LLMModelCatalogFailure.InvalidResponse -> stringResource(R.string.model_query_invalid_response)
        is LLMModelCatalogFailure.HttpFailure -> stringResource(
            R.string.generation_error_http,
            failure.statusCode
        )

        LLMModelCatalogFailure.Unknown -> stringResource(R.string.model_query_failed)
    }
}

@Composable
private fun ModernCredentialControl(
    title: String,
    icon: ImageVector? = null,
    hasExistingValue: Boolean,
    editMode: CredentialEditMode,
    onEdit: () -> Unit,
    onClear: () -> Unit,
    onKeepExisting: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = when (editMode) {
                    CredentialEditMode.KeepExisting -> stringResource(
                        if (hasExistingValue) {
                            R.string.credential_keep_existing
                        } else {
                            R.string.credential_not_set
                        }
                    )

                    CredentialEditMode.Replace -> stringResource(R.string.credential_replace_on_save)
                    CredentialEditMode.Clear -> stringResource(R.string.credential_clear_on_save)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        stringResource(
                            if (hasExistingValue || editMode == CredentialEditMode.Replace) {
                                R.string.credential_replace
                            } else {
                                R.string.credential_set
                            }
                        )
                    )
                }
                if (hasExistingValue && editMode != CredentialEditMode.Clear) {
                    TextButton(onClick = onClear) {
                        Text(
                            stringResource(R.string.credential_clear),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                if (editMode != CredentialEditMode.KeepExisting) {
                    TextButton(onClick = onKeepExisting) {
                        Text(stringResource(R.string.credential_undo_change))
                    }
                }
            }
        }
    }
}

@Composable
private fun ParameterPanel(
    form: LLMProviderEditForm,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    Panel {
        RpSectionHeader(title = stringResource(R.string.generation_parameters))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FormTextField(
                label = stringResource(R.string.temperature),
                value = form.temperature,
                modifier = Modifier.weight(1f),
                enabled = form.sendTemperature,
                keyboardType = KeyboardType.Decimal,
                onChange = { LLMProviderEditUiIntent.ChangeTemperature(it).emit() }
            )
            FormTextField(
                label = stringResource(R.string.top_p),
                value = form.topP,
                modifier = Modifier.weight(1f),
                enabled = form.sendTopP,
                keyboardType = KeyboardType.Decimal,
                onChange = { LLMProviderEditUiIntent.ChangeTopP(it).emit() }
            )
        }

        ParameterSwitchRow(
            title = stringResource(R.string.provider_send_temperature),
            checked = form.sendTemperature,
            onCheckedChange = { LLMProviderEditUiIntent.ToggleSendTemperature(it).emit() }
        )
        ParameterSwitchRow(
            title = stringResource(R.string.provider_send_top_p),
            checked = form.sendTopP,
            onCheckedChange = { LLMProviderEditUiIntent.ToggleSendTopP(it).emit() }
        )

        TokenPresetField(
            label = stringResource(R.string.max_tokens),
            value = form.maxTokens,
            onChange = { LLMProviderEditUiIntent.ChangeMaxTokens(it).emit() }
        )
        TokenPresetField(
            label = stringResource(R.string.context) + " " + stringResource(R.string.tokens),
            value = form.contextTokens,
            onChange = { LLMProviderEditUiIntent.ChangeContextTokens(it).emit() }
        )
    }
}

@Composable
private fun CollapsibleAdvancedPanel(
    form: LLMProviderEditForm,
    requestExtensionsState: LLMProviderEditRequestExtensionsState,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val providerCapabilities = remember(form.protocol) {
        LLMProviderCapabilities.forProtocol(form.protocol)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { isExpanded = !isExpanded }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RpIconBubble(
                    icon = Icons.Rounded.Tune,
                    contentColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.advanced_settings),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.advanced_settings_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )

                    Text(
                        stringResource(R.string.provider_type),
                        style = MaterialTheme.typography.titleSmall
                    )
                    EnumChipRow(
                        values = LLMProviderType.entries,
                        selected = form.providerType,
                        label = { it.name },
                        onSelect = { LLMProviderEditUiIntent.ChangeProviderType(it).emit() }
                    )

                    Text(
                        stringResource(R.string.protocol),
                        style = MaterialTheme.typography.titleSmall
                    )
                    EnumChipRow(
                        values = LLMProviderProtocol.entries,
                        selected = form.protocol,
                        label = { it.name },
                        onSelect = { LLMProviderEditUiIntent.ChangeProtocol(it).emit() }
                    )

                    if (providerCapabilities.supportsStreamUsageRequest) {
                        ParameterSwitchRow(
                            title = stringResource(R.string.provider_request_stream_usage),
                            checked = form.requestStreamUsage,
                            onCheckedChange = {
                                LLMProviderEditUiIntent.ToggleRequestStreamUsage(it).emit()
                            }
                        )
                        Text(
                            text = stringResource(
                                R.string.provider_request_stream_usage_description
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    ModernCredentialControl(
                        title = stringResource(R.string.custom_headers_json),
                        hasExistingValue = form.hasExistingCustomHeaders,
                        editMode = form.customHeadersEditMode,
                        onEdit = { LLMProviderEditUiIntent.ShowCustomHeadersEditor.emit() },
                        onClear = { LLMProviderEditUiIntent.ClearCustomHeaders.emit() },
                        onKeepExisting = { LLMProviderEditUiIntent.KeepExistingCustomHeaders.emit() }
                    )

                    if (form.customHeadersJson.isNotBlank() && form.customHeadersJson.trim() != "{}" && form.customHeadersEditMode != CredentialEditMode.Clear) {
                        JsonSyntaxHighlightPreview(
                            title = stringResource(R.string.custom_headers_preview),
                            jsonString = form.customHeadersJson,
                            onEditClick = { LLMProviderEditUiIntent.ShowCustomHeadersEditor.emit() }
                        )
                    }

                    if (requestExtensionsState.isOpenRouter) {
                        ParameterSwitchRow(
                            title = stringResource(R.string.openrouter_preferred_provider),
                            checked = requestExtensionsState.usesPreferredProvider,
                            onCheckedChange = {
                                LLMProviderEditUiIntent.ToggleOpenRouterPreferredProvider(it).emit()
                            }
                        )
                        if (requestExtensionsState.usesPreferredProvider) {
                            FormTextField(
                                label = stringResource(R.string.openrouter_provider_slug),
                                value = requestExtensionsState.preferredProvider,
                                onChange = {
                                    LLMProviderEditUiIntent.ChangeOpenRouterPreferredProvider(it)
                                        .emit()
                                }
                            )
                            ParameterSwitchRow(
                                title = stringResource(R.string.openrouter_allow_fallbacks),
                                checked = requestExtensionsState.allowFallbacks,
                                onCheckedChange = {
                                    LLMProviderEditUiIntent.ToggleOpenRouterFallbacks(it).emit()
                                }
                            )
                        }
                        Text(
                            text = stringResource(R.string.openrouter_session_affinity_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    ModernRequestBodyPatchControl(
                        requestBodyPatchJson = form.requestBodyPatchJson,
                        onEdit = { LLMProviderEditUiIntent.ShowRequestBodyPatchDialog.emit() },
                        onResetToDefault = {
                            LLMProviderEditUiIntent.ConfirmRequestBodyPatch("{}").emit()
                        }
                    )

                    if (form.requestBodyPatchJson.isNotBlank() && form.requestBodyPatchJson.trim() != "{}") {
                        JsonSyntaxHighlightPreview(
                            title = stringResource(R.string.request_body_patch_preview),
                            jsonString = form.requestBodyPatchJson,
                            onEditClick = { LLMProviderEditUiIntent.ShowRequestBodyPatchDialog.emit() }
                        )
                    }

                    TokenEstimateReserveSlider(
                        value = form.tokenEstimateReservePercent,
                        onChange = {
                            LLMProviderEditUiIntent.ChangeTokenEstimateReservePercent(it).emit()
                        }
                    )

                    Text(
                        text = stringResource(R.string.prompt_post_processing_provider_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    EnumChipRow(
                        values = PromptPostProcessingMode.entries,
                        selected = form.promptPostProcessingMode,
                        label = { it.name },
                        onSelect = {
                            LLMProviderEditUiIntent.SelectPostProcessingMode(it).emit()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TokenEstimateReserveSlider(
    value: Int,
    onChange: (Int) -> Unit
) {
    val percent = value.coerceIn(
        MIN_TOKEN_ESTIMATE_RESERVE_PERCENT,
        MAX_TOKEN_ESTIMATE_RESERVE_PERCENT
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.token_estimate_reserve),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = percent.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = MIN_TOKEN_ESTIMATE_RESERVE_PERCENT.toFloat()..
                    MAX_TOKEN_ESTIMATE_RESERVE_PERCENT.toFloat()
        )
        Text(
            text = stringResource(R.string.token_estimate_reserve_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TokenPresetField(
    label: String,
    value: String,
    onChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FormTextField(
            label = label,
            value = value,
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Number,
            onChange = onChange
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(TokenPreset.entries) { preset ->
                FilterChip(
                    selected = value.toIntOrNull() == preset.value,
                    onClick = { onChange(preset.value.toString()) },
                    label = { Text(preset.displayName) },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
    }
}

@Composable
private fun ParameterSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TestPanel(
    testState: LLMProviderEditTestState,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RpIconBubble(
                icon = when (testState) {
                    is LLMProviderEditTestState.Failed -> Icons.Rounded.ErrorOutline
                    is LLMProviderEditTestState.Success -> Icons.Rounded.CloudDone
                    else -> Icons.Rounded.PlayArrow
                },
                contentColor = when (testState) {
                    is LLMProviderEditTestState.Failed -> MaterialTheme.colorScheme.error
                    is LLMProviderEditTestState.Success -> Color(0xFF10B981)
                    else -> MaterialTheme.colorScheme.primary
                },
                containerColor = when (testState) {
                    is LLMProviderEditTestState.Failed -> MaterialTheme.colorScheme.errorContainer.copy(
                        alpha = 0.45f
                    )

                    is LLMProviderEditTestState.Success -> Color(0xFF10B981).copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    stringResource(R.string.model_test),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = when (testState) {
                        LLMProviderEditTestState.None -> stringResource(R.string.send_short_message)
                        LLMProviderEditTestState.Testing -> stringResource(R.string.testing)
                        is LLMProviderEditTestState.Success -> testState.message
                            ?: stringResource(R.string.test_success)
                        LLMProviderEditTestState.Failed -> stringResource(R.string.test_failed)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (testState) {
                        is LLMProviderEditTestState.Failed -> MaterialTheme.colorScheme.error
                        is LLMProviderEditTestState.Success -> Color(0xFF059669)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    }
                )
            }
            OutlinedButton(
                shape = RoundedCornerShape(10.dp),
                onClick = {
                    if (testState is LLMProviderEditTestState.Testing) {
                        LLMProviderEditUiIntent.CancelTest.emit()
                    } else {
                        LLMProviderEditUiIntent.TestClick.emit()
                    }
                }
            ) {
                val isTesting = testState is LLMProviderEditTestState.Testing
                Icon(
                    imageVector = if (isTesting) Icons.Rounded.Close else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (isTesting) R.string.cancel else R.string.test),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(if (isTesting) R.string.cancel else R.string.test))
            }
        }
    }
}

@Composable
private fun TopBarSaveButton(
    state: LLMProviderEditUiState.Normal,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    TextButton(
        enabled = state.loadState !is LLMProviderEditLoadState.Saving,
        onClick = { LLMProviderEditUiIntent.SaveClick.emit() }
    ) {
        if (state.loadState is LLMProviderEditLoadState.Saving) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(6.dp))
        } else {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            when {
                state.loadState is LLMProviderEditLoadState.Saving -> stringResource(R.string.saving)
                state.mode == LLMProviderEditMode.Create -> stringResource(R.string.create)
                else -> stringResource(R.string.save)
            }
        )
    }
}

@Composable
private fun DialogSwitch(
    dialogState: LLMProviderEditDialogState,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    when (dialogState) {
        LLMProviderEditDialogState.None -> Unit
        LLMProviderEditDialogState.UnsavedChangesConfirm -> AppDangerDialog(
            onDismissRequest = { LLMProviderEditUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.unsaved_changes_title),
            message = stringResource(R.string.unsaved_changes_message),
            confirmText = stringResource(R.string.discard_changes),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { LLMProviderEditUiIntent.ConfirmDiscardChanges.emit() }
        )

        LLMProviderEditDialogState.ApiKeyEditor -> {
            var value by remember { mutableStateOf("") }
            AppInputDialog(
                onDismissRequest = { LLMProviderEditUiIntent.DismissDialog.emit() },
                title = stringResource(R.string.api_key_editor_title),
                subtitle = stringResource(R.string.credential_editor_privacy_note),
                value = value,
                onValueChange = { value = it },
                label = stringResource(R.string.api_key),
                password = true,
                confirmEnabled = value.isNotBlank(),
                onConfirm = { LLMProviderEditUiIntent.ConfirmApiKeyReplacement(value).emit() }
            )
        }

        is LLMProviderEditDialogState.CustomHeadersEditor -> {
            var value by remember(dialogState.initialValue) { mutableStateOf(dialogState.initialValue) }
            AppCodeEditorDialog(
                onDismissRequest = { LLMProviderEditUiIntent.DismissDialog.emit() },
                title = stringResource(R.string.custom_headers_editor_title),
                subtitle = stringResource(R.string.credential_editor_privacy_note),
                value = value,
                onValueChange = { value = it },
                confirmEnabled = value.isNotBlank(),
                onConfirm = {
                    LLMProviderEditUiIntent.ConfirmCustomHeadersReplacement(value).emit()
                }
            )
        }

        is LLMProviderEditDialogState.RequestBodyPatchEditor -> {
            var value by remember(dialogState.initialValue) { mutableStateOf(dialogState.initialValue) }
            AppCodeEditorDialog(
                onDismissRequest = { LLMProviderEditUiIntent.DismissDialog.emit() },
                title = stringResource(R.string.request_body_patch_editor_title),
                editorNote = stringResource(R.string.request_body_patch_editor_note),
                value = value,
                onValueChange = { value = it },
                onConfirm = { LLMProviderEditUiIntent.ConfirmRequestBodyPatch(value).emit() }
            )
        }

        is LLMProviderEditDialogState.ModelPicker -> ModelPickerDialog(
            state = dialogState,
            emit = emit
        )
    }
}

@Composable
private fun ModelPickerDialog(
    state: LLMProviderEditDialogState.ModelPicker,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    AppDialogScaffold(
        onDismissRequest = { LLMProviderEditUiIntent.DismissDialog.emit() },
        title = stringResource(R.string.choose_model),
        badgeIcon = Icons.Rounded.Search,
        badgeTone = DialogBadgeTone.Primary,
        confirmText = "",
        onConfirm = null,
        dismissText = stringResource(R.string.cancel),
        onDismiss = { LLMProviderEditUiIntent.DismissDialog.emit() }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = {
                    LLMProviderEditUiIntent.ChangeModelSearch(it).emit()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_models)) },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            if (state.items.isEmpty()) {
                Text(
                    stringResource(R.string.no_matching_models),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.items, key = { it.id }) { model ->
                        ModelPickerItem(model, emit)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelPickerItem(
    model: LLMAvailableModel,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                LLMProviderEditUiIntent.SelectAvailableModel(model.id).emit()
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = model.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (model.displayName != model.id) {
                Text(
                    text = model.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                )
            }
            ModelMetadataText(model)
        }
    }
}

@Composable
private fun ModelMetadataText(model: LLMAvailableModel) {
    val metadata = listOfNotNull(
        model.contextTokens?.let {
            stringResource(R.string.model_context_tokens, it)
        },
        model.maxOutputTokens?.let {
            stringResource(R.string.model_max_output_tokens, it)
        }
    )
    if (metadata.isEmpty()) return
    Text(
        text = metadata.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
    )
}

@Composable
private fun FormTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        enabled = enabled,
        onValueChange = onChange,
        label = { Text(label) },
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun <T> EnumChipRow(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(values) { value ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label(value)) },
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

@Composable
private fun JsonSyntaxHighlightPreview(
    title: String,
    jsonString: String,
    modifier: Modifier = Modifier,
    maxHeight: androidx.compose.ui.unit.Dp = 180.dp,
    onEditClick: (() -> Unit)? = null
) {
    if (jsonString.isBlank()) return

    val colors = rememberDefaultJsonSyntaxColors()
    val annotatedText = remember(jsonString, colors) {
        buildAnnotatedString {
            append(jsonString)
            tokenizeJsonSyntax(jsonString).forEach { token ->
                val style = when (token.type) {
                    JsonSyntaxTokenType.Key -> SpanStyle(
                        color = colors.key,
                        fontWeight = FontWeight.SemiBold
                    )

                    JsonSyntaxTokenType.String -> SpanStyle(color = colors.string)
                    JsonSyntaxTokenType.Number -> SpanStyle(color = colors.number)
                    JsonSyntaxTokenType.Literal -> SpanStyle(
                        color = colors.literal,
                        fontWeight = FontWeight.SemiBold
                    )

                    JsonSyntaxTokenType.Punctuation -> SpanStyle(color = colors.punctuation)
                }
                addStyle(style = style, start = token.start, end = token.end)
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onEditClick != null) {
                    Text(
                        text = stringResource(R.string.edit),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onEditClick)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .horizontalScroll(rememberScrollState())
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = annotatedText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun ModernRequestBodyPatchControl(
    requestBodyPatchJson: String,
    onEdit: () -> Unit,
    onResetToDefault: () -> Unit
) {
    val hasCustomPatch = remember(requestBodyPatchJson) {
        val trimmed = requestBodyPatchJson.trim()
        trimmed.isNotBlank() && trimmed != "{}"
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.request_body_patch_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = if (hasCustomPatch) {
                    stringResource(R.string.request_body_patch_status_custom)
                } else {
                    stringResource(R.string.request_body_patch_status_default)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Text(
                text = stringResource(R.string.request_body_patch_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.edit))
                }
                if (hasCustomPatch) {
                    TextButton(onClick = onResetToDefault) {
                        Text(
                            stringResource(R.string.reset),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun LLMProviderEditLayoutPreview() {
    AppTheme(dynamicColor = false) {
        LLMProviderEditLayout(
            uiState = LLMProviderEditUiState.Normal(
                mode = LLMProviderEditMode.Create,
                form = LLMProviderEditForm(
                    name = "OpenRouter",
                    providerType = LLMProviderType.OpenRouter,
                    baseUrl = "https://openrouter.ai/api/v1",
                    model = "~anthropic/claude-sonnet-latest",
                    requestBodyPatchJson = "{\n  \"session_id\": \"\$rpclient.routing_session_id\",\n  \"reasoning\": {\n    \"effort\": \"low\"\n  }\n}"
                ),
                requestExtensionsState = LLMProviderEditRequestExtensionsState(
                    isOpenRouter = true
                )
            ),
            emit = {}
        )
    }
}
