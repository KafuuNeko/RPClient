package me.kafuuneko.rpclient.feature.charactercontent.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.charactercontent.model.CharacterContentItem
import me.kafuuneko.rpclient.libs.character.CharacterContentKind
import me.kafuuneko.rpclient.feature.charactercontent.presentation.CharacterContentListState
import me.kafuuneko.rpclient.feature.charactercontent.presentation.CharacterContentUiIntent
import me.kafuuneko.rpclient.feature.charactercontent.presentation.CharacterContentUiState
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppPageScaffold
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.PageFilterChip
import me.kafuuneko.rpclient.ui.widgets.RecentContentCard
import me.kafuuneko.rpclient.ui.widgets.RpInfoCard
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.utils.formatTimestamp

/** 使用通用页面底板与顶栏展示单个角色的内容入口。 */
@Composable
internal fun CharacterContentLayout(
    state: CharacterContentUiState,
    emit: CharacterContentUiIntent.() -> Unit
) {
    if (state is CharacterContentUiState.Finished) {
        CharacterContentLayout(state = state.previous, emit = {})
        return
    }
    BackHandler { CharacterContentUiIntent.Back.emit() }
    val normal = state as? CharacterContentUiState.Normal
    // 页标题以当前角色为准；加载期间仍然可以通过顶部返回。
    AppPageScaffold(
        topBar = {
            AppTopBar(
                title = normal?.characterName?.ifBlank { stringResource(R.string.unknown_character) }
                    ?: stringResource(R.string.home_recent_characters),
                onBack = { CharacterContentUiIntent.Back.emit() },
                actions = {
                    if (normal != null) IconButton(onClick = { CharacterContentUiIntent.CreateChat.emit() }) {
                        Icon(
                            imageVector = Icons.Rounded.AddComment,
                            contentDescription = stringResource(R.string.new_session)
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                is CharacterContentUiState.Normal -> CharacterContentList(
                    state = state.list,
                    emit = emit
                )

                CharacterContentUiState.Loading -> CircularProgressIndicator()
                CharacterContentUiState.LoadFailed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.character_content_load_failed))
                    TextButton(onClick = { CharacterContentUiIntent.Retry.emit() }) {
                        Text(
                            stringResource(R.string.retry)
                        )
                    }
                }

                is CharacterContentUiState.Finished -> Unit
            }
        }
    }
}

/** 筛选栏保持可见，条目列表按活动时间独立滚动。 */
@Composable
private fun CharacterContentList(
    state: CharacterContentListState,
    emit: CharacterContentUiIntent.() -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 横向滚动适配窄屏及较长译文，保持与首页筛选栏相同的控件。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PageFilterChip(
                selected = state.filter == null,
                onClick = { CharacterContentUiIntent.SelectFilter(filter = null).emit() },
                label = { Text(stringResource(R.string.home_session_filter_all)) },
            )
            CharacterContentKind.entries.forEach { kind ->
                PageFilterChip(
                    selected = state.filter == kind,
                    onClick = { CharacterContentUiIntent.SelectFilter(kind).emit() },
                    label = { Text(stringResource(kind.label())) },
                )
            }
        }
        // 类型属于列表键的一部分，允许三张表的主键值重叠。
        RpLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.visibleItems.isEmpty()) item {
                RpInfoCard(
                    icon = Icons.Rounded.ChatBubble,
                    title = stringResource(R.string.no_recent_content),
                    subtitle = stringResource(R.string.character_content_empty),
                )
            }
            items(items = state.visibleItems, key = { "${it.type}-${it.contentId}" }) { item ->
                CharacterContentRow(item = item, onClick = {
                    CharacterContentUiIntent.OpenContent(
                        type = item.type,
                        contentId = item.contentId
                    ).emit()
                })
            }
        }
    }
}

/** 独立内容页沿用最近内容条目的图标、圆角与日期标签。 */
@Composable
private fun CharacterContentRow(item: CharacterContentItem, onClick: () -> Unit) {
    val icon = when (item.type) {
        CharacterContentKind.Chat -> Icons.Rounded.ChatBubble
        CharacterContentKind.Group -> Icons.Rounded.Groups
        CharacterContentKind.Story -> Icons.Rounded.AutoStories
    }
    RecentContentCard(
        accentKey = item.title,
        icon = icon,
        title = item.title,
        preview = item.preview.ifBlank { stringResource(item.previewFallbackResId) },
        metadata = listOf(item.latestTime.formatTimestamp("MM-dd HH:mm")),
        onClick = onClick,
    )
}

@StringRes
private fun CharacterContentKind.label(): Int = when (this) {
    CharacterContentKind.Chat -> R.string.home_session_filter_single
    CharacterContentKind.Group -> R.string.home_session_filter_group
    CharacterContentKind.Story -> R.string.home_content_filter_story
}

@Preview
@Composable
private fun CharacterContentPreview() {
    // 使用三个内容类型共享的页面结构检查主题和长标题排版。
    AppTheme(dynamicColor = false) {
        CharacterContentLayout(
            state = CharacterContentUiState.Normal(
                characterName = "Alice",
                list = CharacterContentListState(
                    items = listOf(
                        CharacterContentItem(
                            type = CharacterContentKind.Chat,
                            contentId = 1L,
                            title = "Harbor",
                            latestTime = 0L,
                            preview = "The harbor lights shimmer on the water.",
                            previewFallbackResId = R.string.no_messages_yet,
                        ),
                    ),
                ),
            ),
            emit = {},
        )
    }
}
