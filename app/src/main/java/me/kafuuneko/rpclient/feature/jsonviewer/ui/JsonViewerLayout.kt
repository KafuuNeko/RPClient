package me.kafuuneko.rpclient.feature.jsonviewer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.jsonviewer.model.JsonViewerEntry
import me.kafuuneko.rpclient.feature.jsonviewer.model.JsonViewerNodeType
import me.kafuuneko.rpclient.feature.jsonviewer.presentation.JsonViewerUiIntent
import me.kafuuneko.rpclient.feature.jsonviewer.presentation.JsonViewerUiState
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpInfoCard
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle

@Composable
fun JsonViewerLayout(
    uiState: JsonViewerUiState,
    emit: JsonViewerUiIntent.() -> Unit = {}
) {
    BackHandler { JsonViewerUiIntent.Back.emit() }
    when (uiState) {
        JsonViewerUiState.None, JsonViewerUiState.Finished -> Unit
        is JsonViewerUiState.Normal -> NormalView(uiState = uiState, emit = emit)
        is JsonViewerUiState.Error -> ErrorView(uiState = uiState, emit = emit)
    }
}

@Composable
private fun NormalView(
    uiState: JsonViewerUiState.Normal,
    emit: JsonViewerUiIntent.() -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            AppTopBar(
                title = uiState.title.ifBlank { stringResource(R.string.json_viewer) },
                onBack = { JsonViewerUiIntent.Back.emit() }
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                RpPageTitle(
                    title = stringResource(R.string.json_viewer),
                    subtitle = stringResource(R.string.json_viewer_subtitle)
                )
            }
            item {
                JsonPathBar(path = uiState.path)
            }
            item {
                JsonSummaryCard(
                    currentType = uiState.currentType,
                    childCount = uiState.childCount
                )
            }
            if (uiState.entries.isEmpty()) {
                item {
                    RpInfoCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.DataObject,
                        title = stringResource(R.string.json_empty_node),
                        subtitle = stringResource(R.string.json_empty_node_desc)
                    )
                }
            }
            items(uiState.entries, key = { it.id }) { entry ->
                JsonEntryRow(
                    entry = entry,
                    onClick = { JsonViewerUiIntent.EntrySelected(entry.id).emit() }
                )
            }
        }
    }
}

@Composable
private fun ErrorView(
    uiState: JsonViewerUiState.Error,
    emit: JsonViewerUiIntent.() -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            AppTopBar(
                title = uiState.title.ifBlank { stringResource(R.string.json_viewer) },
                onBack = { JsonViewerUiIntent.Back.emit() }
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                RpInfoCard(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.DataObject,
                    title = stringResource(R.string.json_invalid_title),
                    subtitle = uiState.message
                )
            }
            if (uiState.rawPreview.isNotBlank()) {
                item {
                    JsonRawPreview(rawPreview = uiState.rawPreview)
                }
            }
        }
    }
}

@Composable
private fun JsonPathBar(path: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        LazyRow(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(path) { index, item ->
                Text(
                    text = item,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (index == path.lastIndex) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
                if (index < path.lastIndex) {
                    Text(
                        text = "/",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun JsonSummaryCard(
    currentType: JsonViewerNodeType,
    childCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            JsonTypeChip(type = currentType)
            Text(
                text = currentType.childCountText(childCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun JsonEntryRow(
    entry: JsonViewerEntry,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = entry.hasChildren, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            JsonTypeChip(type = entry.type)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (entry.hasChildren) entry.type.childCountText(entry.childCount) else entry.preview,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = if (entry.hasChildren) null else FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                )
                if (entry.hasChildren) {
                    Text(
                        text = entry.preview,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = if (entry.hasChildren) null else FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (entry.hasChildren) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        }
    }
}

@Composable
private fun JsonRawPreview(rawPreview: String) {
    Text(
        text = rawPreview,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    )
}

@Composable
private fun JsonTypeChip(type: JsonViewerNodeType) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = when (type) {
        JsonViewerNodeType.Object -> colorScheme.primary.copy(alpha = 0.12f)
        JsonViewerNodeType.Array -> colorScheme.tertiary.copy(alpha = 0.16f)
        JsonViewerNodeType.String -> colorScheme.secondary.copy(alpha = 0.14f)
        JsonViewerNodeType.Number -> colorScheme.error.copy(alpha = 0.12f)
        JsonViewerNodeType.Boolean -> colorScheme.primaryContainer.copy(alpha = 0.6f)
        JsonViewerNodeType.Null -> colorScheme.surfaceVariant
    }
    val contentColor = when (type) {
        JsonViewerNodeType.Object -> colorScheme.primary
        JsonViewerNodeType.Array -> colorScheme.tertiary
        JsonViewerNodeType.String -> colorScheme.secondary
        JsonViewerNodeType.Number -> colorScheme.error
        JsonViewerNodeType.Boolean -> colorScheme.onPrimaryContainer
        JsonViewerNodeType.Null -> colorScheme.onSurfaceVariant
    }

    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = type.labelText(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun JsonViewerNodeType.labelText(): String {
    return when (this) {
        JsonViewerNodeType.Object -> stringResource(R.string.json_type_object)
        JsonViewerNodeType.Array -> stringResource(R.string.json_type_array)
        JsonViewerNodeType.String -> stringResource(R.string.json_type_string)
        JsonViewerNodeType.Number -> stringResource(R.string.json_type_number)
        JsonViewerNodeType.Boolean -> stringResource(R.string.json_type_boolean)
        JsonViewerNodeType.Null -> stringResource(R.string.json_type_null)
    }
}

@Composable
private fun JsonViewerNodeType.childCountText(count: Int): String {
    return when (this) {
        JsonViewerNodeType.Object -> stringResource(R.string.json_object_children, count)
        JsonViewerNodeType.Array -> stringResource(R.string.json_array_children, count)
        else -> stringResource(R.string.json_scalar_value)
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun JsonViewerLayoutPreview() {
    AppTheme(dynamicColor = false) {
        JsonViewerLayout(
            uiState = JsonViewerUiState.Normal(
                title = "Request JSON",
                path = listOf("Root", "messages", "[0]"),
                currentType = JsonViewerNodeType.Object,
                childCount = 3,
                entries = listOf(
                    JsonViewerEntry(
                        id = 0,
                        name = "role",
                        type = JsonViewerNodeType.String,
                        preview = "\"user\"",
                        childCount = 0
                    ),
                    JsonViewerEntry(
                        id = 1,
                        name = "content",
                        type = JsonViewerNodeType.Array,
                        preview = "[]",
                        childCount = 2,
                        sourceKey = "content"
                    )
                ),
                canNavigateUp = true
            )
        )
    }
}
