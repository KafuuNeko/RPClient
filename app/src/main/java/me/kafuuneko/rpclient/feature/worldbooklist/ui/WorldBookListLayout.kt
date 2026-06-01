package me.kafuuneko.rpclient.feature.worldbooklist.ui

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
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.worldbooklist.model.WorldBookListItem
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListLoadState
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListUiIntent
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListUiState
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpTagRow

@Composable
fun WorldBookListLayout(
    uiState: WorldBookListUiState,
    emit: WorldBookListUiIntent.() -> Unit
) {
    BackHandler { WorldBookListUiIntent.Back.emit() }
    when (uiState) {
        WorldBookListUiState.None, WorldBookListUiState.Finished -> Unit
        is WorldBookListUiState.Normal -> WorldBookListNormal(uiState, emit)
    }
}

@Composable
private fun WorldBookListNormal(
    state: WorldBookListUiState.Normal,
    emit: WorldBookListUiIntent.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = stringResource(R.string.world_book_manager),
            onBack = { WorldBookListUiIntent.Back.emit() },
            actions = {
                IconButton(onClick = { WorldBookListUiIntent.ImportWorldBookClick.emit() }) {
                    Icon(
                        Icons.Rounded.FileDownload,
                        contentDescription = stringResource(R.string.import_world_book)
                    )
                }
                IconButton(onClick = { WorldBookListUiIntent.CreateWorldBook.emit() }) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.create_world_book)
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
                    title = stringResource(R.string.world_book_title),
                    subtitle = stringResource(R.string.world_book_subtitle)
                )
            }
            item {
                RpSectionHeader(
                    title = stringResource(R.string.world_book_library),
                    action = stringResource(R.string.create),
                    onAction = { WorldBookListUiIntent.CreateWorldBook.emit() }
                )
            }
            if (state.loadState is WorldBookListLoadState.Loading) {
                item { LoadingRow() }
            } else if (state.lorebooks.isEmpty()) {
                item { EmptyPanel() }
            }
            items(state.lorebooks) { lorebook ->
                WorldBookCard(
                    lorebook = lorebook,
                    onClick = { WorldBookListUiIntent.EditWorldBook(lorebook.id).emit() },
                    onExport = { WorldBookListUiIntent.ExportWorldBookClick(lorebook.id).emit() }
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
private fun EmptyPanel() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RpIconBubble(Icons.AutoMirrored.Rounded.MenuBook)
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.no_world_books),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.no_world_books_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                )
            }
        }
    }
}

@Composable
private fun WorldBookCard(
    lorebook: WorldBookListItem,
    onClick: () -> Unit,
    onExport: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RpIconBubble(Icons.Rounded.Book)
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = lorebook.name,
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
                    IconButton(onClick = onExport) {
                        Icon(
                            Icons.Rounded.FileUpload,
                            contentDescription = stringResource(R.string.export_world_book)
                        )
                    }
                }
                RpTagRow(tags = listOf(stringResource(R.string.entry_count, lorebook.entryCount)))
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun WorldBookListLayoutPreview() {
    AppTheme(dynamicColor = false) {
        WorldBookListLayout(
            uiState = WorldBookListUiState.Normal(
                lorebooks = listOf(
                    WorldBookListItem(1L, "World Setting", 8),
                    WorldBookListItem(2L, "Case Notes", 3)
                )
            ),
            emit = {}
        )
    }
}
