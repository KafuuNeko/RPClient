package me.kafuuneko.rpclient.feature.llmproviderlist.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.llmproviderlist.model.LLMProviderListItem
import me.kafuuneko.rpclient.feature.llmproviderlist.presentation.LLMProviderListDialogState
import me.kafuuneko.rpclient.feature.llmproviderlist.presentation.LLMProviderListLoadState
import me.kafuuneko.rpclient.feature.llmproviderlist.presentation.LLMProviderListUiIntent
import me.kafuuneko.rpclient.feature.llmproviderlist.presentation.LLMProviderListUiState
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpMetaPill
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader

/** 模型配置列表页 Compose 入口。 */
@Composable
fun LLMProviderListLayout(
    uiState: LLMProviderListUiState,
    emit: LLMProviderListUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is LLMProviderListUiState.Normal) { LLMProviderListUiIntent.Back.emit() }
    when (uiState) {
        LLMProviderListUiState.None -> Unit
        is LLMProviderListUiState.Finished -> LLMProviderListLayout(uiState.previous) {}
        is LLMProviderListUiState.Normal -> {
            LLMProviderListNormal(uiState, emit)
            LLMProviderListDialog(uiState.dialogState, emit)
        }
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
        RpLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                bottom = 24.dp
            ),
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
            if (state.providers.isEmpty() && state.loadState !is LLMProviderListLoadState.Loading) {
                item {
                    ProviderListEmptyState(
                        onCreateClick = { LLMProviderListUiIntent.CreateProvider.emit() }
                    )
                }
            } else {
                items(state.providers, key = { it.id }) { provider ->
                    ProviderListCard(
                        provider = provider,
                        onClick = {
                            LLMProviderListUiIntent.EditProvider(provider.id.toString()).emit()
                        },
                        onCheckedChange = {
                            LLMProviderListUiIntent.ToggleProviderEnabled(provider.id.toString(), it)
                                .emit()
                        },
                        onDelete = {
                            LLMProviderListUiIntent.ShowDeleteProviderDialog(provider.id.toString())
                                .emit()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ProviderListEmptyState(
    onCreateClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(
            0.8.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(
                text = stringResource(R.string.no_providers_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.no_providers_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onCreateClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.add_first_provider))
            }
        }
    }
}

@Composable
private fun ProviderListCard(
    provider: LLMProviderListItem,
    onClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = if (provider.isEnabled) 1.dp else 0.8.dp,
            color = if (provider.isEnabled)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (provider.isEnabled)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                ProviderBrandBadge(
                    providerType = provider.providerType,
                    isEnabled = provider.isEnabled
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = provider.baseUrl
                            .removePrefix("https://")
                            .removePrefix("http://"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = provider.isEnabled,
                    onCheckedChange = onCheckedChange
                )
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (provider.isEnabled)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(
                        0.5.dp,
                        if (provider.isEnabled)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.widthIn(max = 220.dp)
                ) {
                    Text(
                        text = provider.model.ifBlank { stringResource(R.string.pending_config) },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (provider.isEnabled)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RpMetaPill(provider.protocol.name)
                    ProviderStatusDot(isEnabled = provider.isEnabled, isConfigured = provider.isConfigured)
                }
            }
        }
    }
}

@Composable
private fun ProviderStatusDot(isEnabled: Boolean, isConfigured: Boolean) {
    val dotColor = when {
        !isEnabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        !isConfigured -> MaterialTheme.colorScheme.error
        else -> Color(0xFF10B981) // Emerald green
    }
    Surface(
        shape = CircleShape,
        color = dotColor.copy(alpha = 0.2f),
        modifier = Modifier.size(16.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                shape = CircleShape,
                color = dotColor,
                modifier = Modifier.size(7.dp)
            ) {}
        }
    }
}

@Composable
private fun ProviderBrandBadge(
    providerType: LLMProviderType,
    isEnabled: Boolean
) {
    val (brandColor, brandIcon) = when (providerType) {
        LLMProviderType.ChatGPT -> Color(0xFF10A37F) to Icons.Rounded.SmartToy
        LLMProviderType.Gemini -> Color(0xFF4E82EE) to Icons.Rounded.AutoAwesome
        LLMProviderType.Claude -> Color(0xFFD97706) to Icons.Rounded.Psychology
        LLMProviderType.DeepSeek -> Color(0xFF0066FF) to Icons.Rounded.Memory
        LLMProviderType.Grok -> Color(0xFF475569) to Icons.Rounded.Bolt
        LLMProviderType.OpenRouter -> Color(0xFF7C3AED) to Icons.Rounded.Hub
        LLMProviderType.Custom -> MaterialTheme.colorScheme.primary to Icons.Rounded.Dns
    }

    val activeColor = if (isEnabled) brandColor else brandColor.copy(alpha = 0.4f)

    Surface(
        modifier = Modifier.size(42.dp),
        shape = RoundedCornerShape(13.dp),
        color = activeColor.copy(alpha = if (isEnabled) 0.14f else 0.08f),
        border = BorderStroke(0.5.dp, activeColor.copy(alpha = if (isEnabled) 0.28f else 0.15f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = brandIcon,
                contentDescription = providerType.name,
                tint = activeColor,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun LLMProviderListDialog(
    dialogState: LLMProviderListDialogState,
    emit: LLMProviderListUiIntent.() -> Unit
) {
    when (dialogState) {
        LLMProviderListDialogState.None -> Unit
        is LLMProviderListDialogState.DeleteProvider -> AppDangerDialog(
            onDismissRequest = { LLMProviderListUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.delete_model_config),
            message = if (dialogState.associatedCharacterCount == 0) {
                stringResource(R.string.delete_model_config_message, dialogState.providerName)
            } else {
                pluralStringResource(
                    R.plurals.delete_model_config_with_characters_message,
                    dialogState.associatedCharacterCount,
                    dialogState.providerName,
                    dialogState.associatedCharacterCount
                )
            },
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            confirmEnabled = !dialogState.isDeleting,
            isConfirmLoading = dialogState.isDeleting,
            onConfirm = { LLMProviderListUiIntent.ConfirmDeleteProvider.emit() }
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun LLMProviderListLayoutPreview() {
    AppTheme(dynamicColor = false) {
        LLMProviderListLayout(
            uiState = LLMProviderListUiState.Normal(
                providers = listOf(
                    LLMProviderListItem(
                        id = 1,
                        name = "OpenRouter",
                        providerType = LLMProviderType.OpenRouter,
                        protocol = LLMProviderProtocol.OpenAICompatible,
                        baseUrl = "https://openrouter.ai/api/v1",
                        model = "~anthropic/claude-sonnet-latest",
                        isEnabled = true
                    ),
                    LLMProviderListItem(
                        id = 2,
                        name = "DeepSeek Official",
                        providerType = LLMProviderType.DeepSeek,
                        protocol = LLMProviderProtocol.OpenAICompatible,
                        baseUrl = "https://api.deepseek.com/v1",
                        model = "deepseek-chat",
                        isEnabled = true
                    ),
                    LLMProviderListItem(
                        id = 3,
                        name = "Gemini Pro",
                        providerType = LLMProviderType.Gemini,
                        protocol = LLMProviderProtocol.Gemini,
                        baseUrl = "https://generativelanguage.googleapis.com",
                        model = "gemini-2.5-flash",
                        isEnabled = false
                    )
                )
            ),
            emit = {}
        )
    }
}

@Preview(widthDp = 390, heightDp = 400, showBackground = true)
@Composable
private fun LLMProviderListEmptyPreview() {
    AppTheme(dynamicColor = false) {
        LLMProviderListLayout(
            uiState = LLMProviderListUiState.Normal(
                providers = emptyList()
            ),
            emit = {}
        )
    }
}
