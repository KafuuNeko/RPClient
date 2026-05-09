package me.kafuuneko.rpclient.feature.llmprovideredit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.llmprovideredit.model.LLMProviderEditForm
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditLoadState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditMode
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditTestState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditUiIntent
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditUiState
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader

@Composable
fun LLMProviderEditLayout(
    uiState: LLMProviderEditUiState,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    BackHandler { LLMProviderEditUiIntent.Back.emit() }
    when (uiState) {
        LLMProviderEditUiState.None, LLMProviderEditUiState.Finished -> Unit
        is LLMProviderEditUiState.Normal -> LLMProviderEditNormal(uiState, emit)
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
            .statusBarsPadding()
    ) {
        AppTopBar(
            title = if (state.mode == LLMProviderEditMode.Create) stringResource(R.string.create_model_title) else stringResource(R.string.edit_model_title),
            onBack = { LLMProviderEditUiIntent.Back.emit() }
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                RpPageTitle(
                    title = if (state.mode == LLMProviderEditMode.Create) stringResource(R.string.create_model_subtitle) else state.form.name.ifBlank { stringResource(R.string.model_provider_title) },
                    subtitle = stringResource(R.string.edit_model_subtitle)
                )
            }
            item { BasicPanel(state.form, emit) }
            item { ProtocolPanel(state.form, emit) }
            item { ParameterPanel(state.form, emit) }
            item { TestPanel(state.testState, emit) }
            item { ActionPanel(state, emit) }
        }
    }
}

@Composable
private fun BasicPanel(
    form: LLMProviderEditForm,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    Panel {
        RpSectionHeader(title = stringResource(R.string.basic_info))
        FormTextField(stringResource(R.string.name), form.name) { LLMProviderEditUiIntent.ChangeName(it).emit() }
        FormTextField(stringResource(R.string.base_url), form.baseUrl) { LLMProviderEditUiIntent.ChangeBaseUrl(it).emit() }
        FormTextField(
            label = stringResource(R.string.api_key),
            value = form.apiKey,
            visualTransformation = if (form.apiKey.isBlank()) VisualTransformation.None else PasswordVisualTransformation(),
            onChange = { LLMProviderEditUiIntent.ChangeApiKey(it).emit() }
        )
        FormTextField(stringResource(R.string.model_name), form.model) { LLMProviderEditUiIntent.ChangeModel(it).emit() }
        Row(verticalAlignment = Alignment.CenterVertically) {
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
private fun ProtocolPanel(
    form: LLMProviderEditForm,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    Panel {
        RpSectionHeader(title = stringResource(R.string.protocol))
        Text(stringResource(R.string.provider_type), style = MaterialTheme.typography.titleSmall)
        EnumChipRow(
            values = LLMProviderType.entries,
            selected = form.providerType,
            label = { it.name },
            onSelect = { LLMProviderEditUiIntent.ChangeProviderType(it).emit() }
        )
        Text(stringResource(R.string.protocol), style = MaterialTheme.typography.titleSmall)
        EnumChipRow(
            values = LLMProviderProtocol.entries,
            selected = form.protocol,
            label = { it.name },
            onSelect = { LLMProviderEditUiIntent.ChangeProtocol(it).emit() }
        )
        FormTextField(
            label = stringResource(R.string.custom_headers_json),
            value = form.customHeadersJson,
            minLines = 3,
            onChange = { LLMProviderEditUiIntent.ChangeCustomHeadersJson(it).emit() }
        )
    }
}

@Composable
private fun ParameterPanel(
    form: LLMProviderEditForm,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    Panel {
        RpSectionHeader(title = stringResource(R.string.generation_parameters))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FormTextField(
                label = stringResource(R.string.temperature),
                value = form.temperature,
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Decimal,
                onChange = { LLMProviderEditUiIntent.ChangeTemperature(it).emit() }
            )
            FormTextField(
                label = stringResource(R.string.top_p),
                value = form.topP,
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Decimal,
                onChange = { LLMProviderEditUiIntent.ChangeTopP(it).emit() }
            )
        }
        FormTextField(
            label = stringResource(R.string.max_tokens),
            value = form.maxTokens,
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Number,
            onChange = { LLMProviderEditUiIntent.ChangeMaxTokens(it).emit() }
        )
        FormTextField(
            label = stringResource(R.string.context) + " " + stringResource(R.string.tokens),
            value = form.contextTokens,
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Number,
            onChange = { LLMProviderEditUiIntent.ChangeContextTokens(it).emit() }
        )
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
                }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(stringResource(R.string.model_test), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = when (testState) {
                        LLMProviderEditTestState.None -> stringResource(R.string.send_short_message)
                        LLMProviderEditTestState.Testing -> stringResource(R.string.testing)
                        is LLMProviderEditTestState.Success -> testState.message
                        is LLMProviderEditTestState.Failed -> testState.message
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                )
            }
            OutlinedButton(
                enabled = testState !is LLMProviderEditTestState.Testing,
                onClick = { LLMProviderEditUiIntent.TestClick.emit() }
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.test))
                Text(stringResource(R.string.test))
            }
        }
    }
}

@Composable
private fun ActionPanel(
    state: LLMProviderEditUiState.Normal,
    emit: LLMProviderEditUiIntent.() -> Unit
) {
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = state.loadState !is LLMProviderEditLoadState.Saving,
        onClick = { LLMProviderEditUiIntent.SaveClick.emit() }
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null)
        Text(if (state.mode == LLMProviderEditMode.Create) stringResource(R.string.create) else stringResource(R.string.save))
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun FormTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(8.dp)
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
                label = { Text(label(value)) }
            )
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
                    baseUrl = "https://openrouter.ai/api/v1",
                    model = "anthropic/claude-3.5-sonnet"
                )
            ),
            emit = {}
        )
    }
}