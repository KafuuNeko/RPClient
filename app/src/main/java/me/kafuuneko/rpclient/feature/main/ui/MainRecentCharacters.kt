package me.kafuuneko.rpclient.feature.main.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.main.model.items.MainRecentCharacterItem
import me.kafuuneko.rpclient.feature.main.presentation.MainHomeRecentState
import me.kafuuneko.rpclient.feature.main.presentation.MainUiIntent
import me.kafuuneko.rpclient.libs.HomeRecentMode
import me.kafuuneko.rpclient.ui.widgets.RecentContentCard
import me.kafuuneko.rpclient.ui.widgets.RecentContentIconSize
import me.kafuuneko.rpclient.ui.widgets.RpInfoCard
import me.kafuuneko.rpclient.utils.formatTimestamp

/** 最近区域的标题同时作为下拉入口；是否展开只是当前控件的临时状态。 */
@Composable
internal fun RecentModeSelector(
    mode: HomeRecentMode,
    enabled: Boolean,
    onSelect: (HomeRecentMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        // 仅标题区域响应点击，多选期间保留原来的标题展示。
        Row(
            modifier = Modifier
                .clickable(enabled = enabled, role = Role.DropdownList) { expanded = true }
                .defaultMinSize(minHeight = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(mode.titleRes()),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (enabled) {
                Icon(imageVector = Icons.Rounded.KeyboardArrowDown, contentDescription = null)
            }
        }
        // 选择后先收起菜单，再交给 ViewModel 刷新并保存模式。
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            HomeRecentMode.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.titleRes())) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@StringRes
private fun HomeRecentMode.titleRes(): Int = when (this) {
    HomeRecentMode.RecentContent -> R.string.recent_content
    HomeRecentMode.RecentCharacters -> R.string.home_recent_characters
}

private fun recentCharacterKey(id: Long) = "character-$id"

/** 依据首页列表的真实可见范围请求头像，资源入口和屏外角色不参与解码。 */
@Composable
internal fun RecentAvatarViewport(
    listState: LazyListState,
    characters: List<MainRecentCharacterItem>,
    emit: MainUiIntent.() -> Unit,
) {
    val sizePx = with(LocalDensity.current) { RecentContentIconSize.roundToPx() }
    // 视口计算只处理列表键，不在 Compose 中读取或解码文件。
    LaunchedEffect(listState, characters, sizePx) {
        val avatarByKey =
            characters.associate { recentCharacterKey(it.characterId) to it.avatarUuid }
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.mapNotNullTo(mutableSetOf()) { item ->
                avatarByKey[item.key]?.takeIf(String::isNotBlank)
            }
        }.collect { uuids ->
            MainUiIntent.VisibleRecentAvatarsChanged(
                avatarUuids = uuids,
                sizePx = sizePx
            ).emit()
        }
    }
}

/** 首页只负责渲染角色入口，不持有角色详情页的筛选或会话状态。 */
internal fun LazyListScope.recentCharacterItems(
    state: MainHomeRecentState.RecentCharacters,
    emit: MainUiIntent.() -> Unit,
) {
    // 角色库可能非空，但还没有角色参加任何内容。
    if (state.characters.isEmpty()) {
        item {
            RpInfoCard(
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Person,
                title = stringResource(R.string.home_recent_characters),
                subtitle = stringResource(R.string.home_recent_characters_empty),
            )
        }
        return
    }
    // 复用最近内容卡片，每个角色只占一行。
    items(items = state.characters, key = { recentCharacterKey(it.characterId) }) { character ->
        RecentContentCard(
            accentKey = character.characterName,
            avatar = state.avatars[character.avatarUuid],
            icon = Icons.Rounded.Person,
            title = character.characterName.ifBlank { stringResource(R.string.unknown_character) },
            preview = character.latestTitle,
            metadata = listOf(
                stringResource(R.string.character_content_count, character.contentCount),
                character.latestTime.formatTimestamp("MM-dd HH:mm"),
            ),
            onClick = {
                MainUiIntent.OpenCharacterContent(characterId = character.characterId).emit()
            },
        )
    }
}
