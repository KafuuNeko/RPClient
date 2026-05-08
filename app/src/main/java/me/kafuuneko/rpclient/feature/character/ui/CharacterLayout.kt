package me.kafuuneko.rpclient.feature.character.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.feature.character.presentation.CharacterUiIntent
import me.kafuuneko.rpclient.feature.character.presentation.CharacterUiState
import me.kafuuneko.rpclient.libs.model.RpCharacterUiModel
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpInfoCard
import me.kafuuneko.rpclient.ui.widgets.RpMetaRow
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpTagRow

@Composable
fun CharacterLayout(
    uiState: CharacterUiState,
    emit: CharacterUiIntent.() -> Unit
) {
    BackHandler { CharacterUiIntent.Back.emit() }
    when (uiState) {
        CharacterUiState.None, CharacterUiState.Finished -> Unit
        is CharacterUiState.Normal -> CharacterNormal(uiState, emit)
    }
}

@Composable
private fun CharacterNormal(
    state: CharacterUiState.Normal,
    emit: CharacterUiIntent.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        AppTopBar(
            title = "角色管理",
            onBack = { CharacterUiIntent.Back.emit() },
            actions = {
                IconButton(onClick = { CharacterUiIntent.ImportCharacter.emit() }) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = "导入角色")
                }
                IconButton(onClick = { CharacterUiIntent.CreateCharacter.emit() }) {
                    Icon(Icons.Rounded.Add, contentDescription = "新建角色")
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
            item { RpPageTitle(title = "角色卡", subtitle = "创建、导入、编辑角色定义与高级设定") }
            item { SearchPreview() }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.characters) { character ->
                        CharacterTile(
                            character = character,
                            selected = character.id == state.selectedCharacterId,
                            onClick = { CharacterUiIntent.SelectCharacter(character.id).emit() }
                        )
                    }
                }
            }
            val selected = state.characters.find { it.id == state.selectedCharacterId }
            if (selected != null) {
                item { CharacterDetail(selected) }
            }
        }
    }
}

@Composable
private fun SearchPreview() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, contentDescription = "搜索")
            Spacer(modifier = Modifier.width(10.dp))
            Text("搜索名称、标签、作者备注", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.46f))
        }
    }
}

@Composable
private fun CharacterTile(
    character: RpCharacterUiModel,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(170.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RpAvatar(character.avatarText, Color(character.accentColor))
            Text(character.name, style = MaterialTheme.typography.titleMedium)
            Text(
                character.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            RpTagRow(character.tags, maxCount = 2)
        }
    }
}

@Composable
private fun CharacterDetail(character: RpCharacterUiModel) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RpAvatar(character.avatarText, Color(character.accentColor))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(character.name, style = MaterialTheme.typography.titleLarge)
                    Text(character.subtitle, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
                }
            }
            Text(character.description, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
            RpTagRow(character.tags)
            RpMetaRow(listOf("${character.sessions} 个会话", character.updatedAt, "角色 Lore 可绑定"))
            RpSectionHeader(title = "酒馆字段", action = "编辑")
            RpInfoCard(
                icon = Icons.Rounded.FileUpload,
                title = "Character Card V2",
                subtitle = "头像、描述、开场白、示例对话、作者备注"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("新建会话") })
                AssistChip(onClick = {}, label = { Text("高级定义") })
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun CharacterLayoutPreview() {
    AppTheme(dynamicColor = false) {
        CharacterLayout(
            uiState = CharacterUiState.Normal(
                selectedCharacterId = "lyra",
                characters = listOf(
                    RpCharacterUiModel("lyra", "Lyra", "雾港档案管理员", "描述", "L", listOf("悬疑"), 12, "刚刚", 0xFF315EFD)
                )
            ),
            emit = {}
        )
    }
}
