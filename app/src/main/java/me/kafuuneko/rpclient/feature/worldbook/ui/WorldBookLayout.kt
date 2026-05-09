package me.kafuuneko.rpclient.feature.worldbook.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.worldbook.presentation.WorldBookUiIntent
import me.kafuuneko.rpclient.feature.worldbook.presentation.WorldBookUiState
import me.kafuuneko.rpclient.libs.model.LoreBookUiModel
import me.kafuuneko.rpclient.libs.model.LoreEntryUiModel
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpTagRow

@Composable
fun WorldBookLayout(
    uiState: WorldBookUiState,
    emit: WorldBookUiIntent.() -> Unit
) {
    BackHandler { WorldBookUiIntent.Back.emit() }
    when (uiState) {
        WorldBookUiState.None, WorldBookUiState.Finished -> Unit
        is WorldBookUiState.Normal -> WorldBookNormal(uiState, emit)
    }
}

@Composable
private fun WorldBookNormal(
    state: WorldBookUiState.Normal,
    emit: WorldBookUiIntent.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        AppTopBar(
            title = stringResource(R.string.world_book_manager),
            onBack = { WorldBookUiIntent.Back.emit() },
            actions = {
                IconButton(onClick = { WorldBookUiIntent.CreateLoreBook.emit() }) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.create_world_book))
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
            item { RpPageTitle(title = stringResource(R.string.world_book_title), subtitle = stringResource(R.string.world_book_subtitle)) }
            item { RpSectionHeader(title = stringResource(R.string.world_book_library), action = stringResource(R.string.create)) { WorldBookUiIntent.CreateLoreBook.emit() } }
            item {
                LoreBookSelector(
                    loreBooks = state.loreBooks,
                    selectedLoreBookId = state.selectedLoreBookId,
                    emit = emit
                )
            }
            item { RpSectionHeader(title = stringResource(R.string.entries), action = stringResource(R.string.add)) { WorldBookUiIntent.CreateEntry.emit() } }
            items(state.entries) { entry ->
                LoreEntryCard(entry)
            }
        }
    }
}

@Composable
private fun LoreBookSelector(
    loreBooks: List<LoreBookUiModel>,
    selectedLoreBookId: String,
    emit: WorldBookUiIntent.() -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(loreBooks) { loreBook ->
            val selected = loreBook.id == selectedLoreBookId
            Surface(
                modifier = Modifier
                    .width(190.dp)
                    .clickable { WorldBookUiIntent.SelectLoreBook(loreBook.id).emit() },
                shape = RoundedCornerShape(8.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        loreBook.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${loreBook.scope} · ${stringResource(R.string.entry_count, loreBook.entries)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                    )
                    Text(
                        loreBook.updatedAt,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.62f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.44f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LoreBookCard(
    loreBook: LoreBookUiModel
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RpIconBubble(Icons.Rounded.Book)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(loreBook.title, style = MaterialTheme.typography.titleSmall)
                Text("${loreBook.scope} · ${stringResource(R.string.entry_count, loreBook.entries)} · ${loreBook.updatedAt}", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = {}) {
                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit_world_book))
            }
        }
    }
}

@Composable
private fun LoreEntryCard(entry: LoreEntryUiModel) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RpIconBubble(Icons.Rounded.Book)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.title, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.priority, entry.priority), style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit))
                }
            }
            Text(entry.content, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
            RpTagRow(entry.keywords)
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun WorldBookLayoutPreview() {
    AppTheme(dynamicColor = false) {
        WorldBookLayout(
            uiState = WorldBookUiState.Normal(
                selectedLoreBookId = "l",
                loreBooks = listOf(LoreBookUiModel("l", "Fog Harbor Old District", "Chat Lore", 18, true, "Today")),
                entries = listOf(LoreEntryUiModel("e", "Old District", listOf("Fog Harbor"), "Setting content", 80, true))
            ),
            emit = {}
        )
    }
}