package me.kafuuneko.rpclient.feature.llmproviderlist.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.llmproviderlist.presentation.LLMProviderListLoadState
import me.kafuuneko.rpclient.feature.llmproviderlist.presentation.LLMProviderListUiIntent
import me.kafuuneko.rpclient.feature.llmproviderlist.presentation.LLMProviderListUiState
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpTagRow

/** 模型供应商列表页 Compose 入口。 */
@Composable
fun LLMProviderListLayout(
    uiState: LLMProviderListUiState,
    emit: LLMProviderListUiIntent.() -> Unit
) {
    BackHandler { LLMProviderListUiIntent.Back.emit() }
    when (uiState) {
        LLMProviderListUiState.None, LLMProviderListUiState.Finished -> Unit
        is LLMProviderListUiState.Normal -> LLMProviderListNormal(uiState, emit)
    }
}

@Composable
private fun LLMProviderListNormal(
    state: LLMProviderListUiState.Normal,
    emit: LLMProviderListUiIntent.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = stringResource(R.string.model_provider_title),
            onBack = { LLMProviderListUiIntent.Back.emit() },
            actions = {
                IconButton(onClick = { LLMProviderListUiIntent.CreateProvider.emit() }) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.create_model)
                    )
                }
            }
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
                    title = stringResource(R.string.model_list_title),
                    subtitle = stringResource(R.string.model_list_subtitle)
                )
            }
            item {
                RpSectionHeader(
                    title = stringResource(R.string.all_models),
                    action = stringResource(R.string.create),
                    onAction = { LLMProviderListUiIntent.CreateProvider.emit() }
                )
            }
            if (state.loadState is LLMProviderListLoadState.Loading) {
                item { LoadingRow() }
            }
            items(state.providers) { provider ->
                ProviderListCard(
                    provider = provider,
                    onClick = {
                        LLMProviderListUiIntent.EditProvider(provider.id.toString()).emit()
                    },
                    onCheckedChange = {
                        LLMProviderListUiIntent.ToggleProviderEnabled(provider.id.toString(), it)
                            .emit()
                    }
                )
            }
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ProviderListCard(
    provider: LLMProvider,
    onClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (provider.isEnabled) 2.dp else 1.dp,
            color = if (provider.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RpIconBubble(if (provider.isEnabled) Icons.Rounded.Key else Icons.Rounded.Storage)
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = provider.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.edit),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(provider.model, style = MaterialTheme.typography.bodySmall)
                Text(
                    provider.baseUrl,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                RpTagRow(
                    modifier = Modifier.padding(top = 4.dp),
                    tags = listOf(provider.typeText(), provider.statusText()),
                    maxCount = 2
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Switch(checked = provider.isEnabled, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun LLMProvider.typeText(): String {
    return "${providerType.name} / ${protocol.name}"
}

@Composable
private fun LLMProvider.statusText(): String {
    return when {
        !isEnabled -> stringResource(R.string.not_enabled)
        apiKey.isBlank() -> stringResource(R.string.pending_config)
        else -> stringResource(R.string.available)
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun LLMProviderListLayoutPreview() {
    AppTheme(dynamicColor = false) {
        LLMProviderListLayout(
            uiState = LLMProviderListUiState.Normal(
                providers = listOf(
                    LLMProvider(
                        id = 1,
                        name = "OpenRouter",
                        providerType = LLMProviderType.OpenRouter,
                        protocol = LLMProviderProtocol.OpenAICompatible,
                        baseUrl = "https://openrouter.ai/api/v1",
                        model = "~anthropic/claude-sonnet-latest",
                        isEnabled = true
                    ),
                    LLMProvider(
                        id = 2,
                        name = "Gemini",
                        providerType = LLMProviderType.Gemini,
                        protocol = LLMProviderProtocol.Gemini,
                        baseUrl = "https://generativelanguage.googleapis.com",
                        model = "gemini-3.5-flash",
                        isEnabled = false
                    )
                )
            ),
            emit = {}
        )
    }
}
