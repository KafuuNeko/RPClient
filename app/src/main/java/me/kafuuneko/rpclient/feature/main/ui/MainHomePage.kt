package me.kafuuneko.rpclient.feature.main.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.main.model.MainChatSessionGroup
import me.kafuuneko.rpclient.feature.main.model.MainHomeContentTab
import me.kafuuneko.rpclient.feature.main.model.MainHomeItemSelection
import me.kafuuneko.rpclient.feature.main.model.MainHomeItemType
import me.kafuuneko.rpclient.feature.main.model.items.MainChatSessionItem
import me.kafuuneko.rpclient.feature.main.model.items.MainGroupChatSessionItem
import me.kafuuneko.rpclient.feature.main.model.items.MainHomeContentItem
import me.kafuuneko.rpclient.feature.main.model.items.MainStoryItem
import me.kafuuneko.rpclient.feature.main.presentation.MainAppearanceSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainDebugSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainHomeResourceState
import me.kafuuneko.rpclient.feature.main.presentation.MainHomeSelectionState
import me.kafuuneko.rpclient.feature.main.presentation.MainHomeState
import me.kafuuneko.rpclient.feature.main.presentation.MainPromptBehaviorState
import me.kafuuneko.rpclient.feature.main.presentation.MainProviderPostProcessingState
import me.kafuuneko.rpclient.feature.main.presentation.MainProviderSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainRecentChatsState
import me.kafuuneko.rpclient.feature.main.presentation.MainRecentGroupChatsState
import me.kafuuneko.rpclient.feature.main.presentation.MainRecentStoriesState
import me.kafuuneko.rpclient.feature.main.presentation.MainSettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainSummaryInjectionState
import me.kafuuneko.rpclient.feature.main.presentation.MainSummarySettingsState
import me.kafuuneko.rpclient.feature.main.presentation.MainUiIntent
import me.kafuuneko.rpclient.feature.main.presentation.MainUiState
import me.kafuuneko.rpclient.feature.main.presentation.MainUserAvatarState
import me.kafuuneko.rpclient.feature.main.presentation.MainUserIdentityState
import me.kafuuneko.rpclient.feature.main.presentation.MainWorldInfoBudgetState
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.theme.AppThemeMode
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.getMacaronColor
import me.kafuuneko.rpclient.ui.widgets.AppCard
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpInfoCard
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpMetaRow
import me.kafuuneko.rpclient.ui.widgets.PageFilterChip as FilterChip

@Composable
private fun HeroEntryCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    // 点击由卡片接管，使焦点反馈与模糊底板共用完整圆角边界。
    AppCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppSurface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.AddComment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AppSurface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/** 顶部快捷行动双栏卡片（群聊与故事创作）。 */
@Composable
private fun HomeQuickActionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    // 快捷入口共用卡片点击语义，避免额外的矩形焦点层。
    AppCard(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RpIconBubble(icon = icon, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 首页资产快捷管理金刚区（角色卡、世界书、Regex 脚本）。 */
@Composable
private fun HomeAssetDock(
    resourceState: MainHomeResourceState,
    emit: MainUiIntent.() -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HomeAssetCapsule(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.Person,
            title = stringResource(R.string.character),
            count = resourceState.totalCharacters,
            onClick = { MainUiIntent.OpenCharacterManager.emit() }
        )
        HomeAssetCapsule(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.Book,
            title = stringResource(R.string.world_book),
            count = resourceState.totalWorldBooks,
            onClick = { MainUiIntent.OpenWorldBookManager.emit() }
        )
        HomeAssetCapsule(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.DataObject,
            title = stringResource(R.string.regex_script_title),
            count = null,
            onClick = { MainUiIntent.OpenRegexScripts.emit() }
        )
    }
}

/** 单个紧凑资产管理胶囊卡片，带数量 Badge。 */
@Composable
private fun HomeAssetCapsule(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    count: Int? = null,
    onClick: () -> Unit
) {
    // 资产入口与数量徽标分别布局，点击反馈由外层卡片统一裁切。
    AppCard(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                AppSurface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (count != null) {
                    AppSurface(
                        modifier = Modifier.offset(x = 6.dp, y = (-4).dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 1.dp
                    ) {
                        Text(
                            text = if (count > 99) "99+" else count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 首页主体，展示资源入口、最近会话与多选操作。 */
@Composable
internal fun MainHomePage(
    state: MainHomeState,
    emit: MainUiIntent.() -> Unit
) {
    // 多选时收起创建入口，列表位置仍由主页面的独立保存域管理。
    val selectionState = state.selectionState as? MainHomeSelectionState.Selecting
    val multiSelectMode = selectionState != null
    val selectedItems = selectionState?.selectedItems.orEmpty()

    RpLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = 18.dp,
            top = 8.dp,
            end = 18.dp,
            bottom = 110.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.statusBarsPadding())
        }
        if (multiSelectMode) {
            item {
                Text(
                    text = stringResource(R.string.selected_count, selectedItems.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
        } else {
            homeEntryItems(state.resourceState, emit)
        }
        homeContentSection(
            state = state,
            multiSelectMode = multiSelectMode,
            selectedItems = selectedItems,
            emit = emit
        )
    }
}

private fun LazyListScope.homeEntryItems(
    resourceState: MainHomeResourceState,
    emit: MainUiIntent.() -> Unit
) {
    item {
        HeroEntryCard(
            title = stringResource(R.string.new_session),
            subtitle = stringResource(R.string.new_session_desc),
            onClick = { MainUiIntent.OpenCreateChat.emit() }
        )
    }
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeQuickActionCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Groups,
                title = stringResource(R.string.group_chat),
                onClick = { MainUiIntent.OpenCreateGroupChat.emit() }
            )
            HomeQuickActionCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.AutoStories,
                title = stringResource(R.string.story_create_story),
                onClick = { MainUiIntent.OpenCreateStory.emit() }
            )
        }
    }
    item {
        HomeAssetDock(resourceState, emit)
    }
}

private fun LazyListScope.homeContentSection(
    state: MainHomeState,
    multiSelectMode: Boolean,
    selectedItems: Set<MainHomeItemSelection>,
    emit: MainUiIntent.() -> Unit
) {
    item {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.recent_content),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (!multiSelectMode) IconButton(onClick = { MainUiIntent.OpenContentSearch.emit() }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Search, stringResource(R.string.content_search_title))
                }
            }
            if (!multiSelectMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = state.selectedContentTab == MainHomeContentTab.All,
                        onClick = {
                            MainUiIntent.SelectHomeContentTab(MainHomeContentTab.All).emit()
                        },
                        label = { Text(stringResource(R.string.home_session_filter_all)) }
                    )
                    FilterChip(
                        selected = state.selectedContentTab == MainHomeContentTab.Single,
                        onClick = {
                            MainUiIntent.SelectHomeContentTab(MainHomeContentTab.Single).emit()
                        },
                        label = { Text(stringResource(R.string.home_session_filter_single)) }
                    )
                    FilterChip(
                        selected = state.selectedContentTab == MainHomeContentTab.Group,
                        onClick = {
                            MainUiIntent.SelectHomeContentTab(MainHomeContentTab.Group).emit()
                        },
                        label = { Text(stringResource(R.string.home_session_filter_group)) }
                    )
                    FilterChip(
                        selected = state.selectedContentTab == MainHomeContentTab.Story,
                        onClick = {
                            MainUiIntent.SelectHomeContentTab(MainHomeContentTab.Story).emit()
                        },
                        label = { Text(stringResource(R.string.home_content_filter_story)) }
                    )
                }
            }
        }
    }

    when (state.selectedContentTab) {
        MainHomeContentTab.All -> {
            if (state.allRecentItems.isEmpty()) {
                item {
                    RpInfoCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.ChatBubble,
                        title = stringResource(R.string.no_recent_content),
                        subtitle = stringResource(R.string.no_recent_content_desc)
                    )
                }
            } else {
                recentAllItems(
                    items = state.allRecentItems,
                    multiSelectMode = multiSelectMode,
                    selectedItems = selectedItems,
                    emit = emit
                )
            }
        }

        MainHomeContentTab.Single -> {
            when (state.recentChatsState) {
                MainRecentChatsState.Empty -> item {
                    RpInfoCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.ChatBubble,
                        title = stringResource(R.string.no_recent_chats),
                        subtitle = stringResource(R.string.no_recent_chats_desc)
                    )
                }

                is MainRecentChatsState.Content -> recentChatSessionItems(
                    state = state.recentChatsState,
                    multiSelectMode = multiSelectMode,
                    selectedItems = selectedItems,
                    emit = emit
                )
            }
        }

        MainHomeContentTab.Group -> {
            when (state.recentGroupChatsState) {
                MainRecentGroupChatsState.Empty -> item {
                    RpInfoCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.Groups,
                        title = stringResource(R.string.no_group_chats),
                        subtitle = stringResource(R.string.no_group_chats_desc)
                    )
                }

                is MainRecentGroupChatsState.Content -> recentGroupChatSessionItems(
                    state = state.recentGroupChatsState,
                    multiSelectMode = multiSelectMode,
                    selectedItems = selectedItems,
                    emit = emit
                )
            }
        }

        MainHomeContentTab.Story -> {
            when (state.recentStoriesState) {
                MainRecentStoriesState.Empty -> item {
                    RpInfoCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.AutoStories,
                        title = stringResource(R.string.story_empty_title),
                        subtitle = stringResource(R.string.story_empty_subtitle)
                    )
                }

                is MainRecentStoriesState.Content -> recentStoryItems(
                    state = state.recentStoriesState,
                    multiSelectMode = multiSelectMode,
                    selectedItems = selectedItems,
                    emit = emit
                )
            }
        }
    }
}

private fun LazyListScope.recentAllItems(
    items: List<MainHomeContentItem>,
    multiSelectMode: Boolean,
    selectedItems: Set<MainHomeItemSelection>,
    emit: MainUiIntent.() -> Unit
) {
    items(
        items = items,
        key = { item ->
            when (item) {
                is MainChatSessionItem -> "session-${item.id}"
                is MainGroupChatSessionItem -> "group-session-${item.id}"
                is MainStoryItem -> "story-${item.id}"
            }
        }
    ) { item ->
        when (item) {
            is MainChatSessionItem -> RecentChatSessionCard(
                session = item,
                multiSelectMode = multiSelectMode,
                selectedItems = selectedItems,
                emit = emit
            )

            is MainGroupChatSessionItem -> RecentGroupChatSessionCard(
                session = item,
                multiSelectMode = multiSelectMode,
                selectedItems = selectedItems,
                emit = emit
            )

            is MainStoryItem -> RecentStoryCard(
                story = item,
                multiSelectMode = multiSelectMode,
                selectedItems = selectedItems,
                emit = emit
            )
        }
    }
}

private fun LazyListScope.recentChatSessionItems(
    state: MainRecentChatsState.Content,
    multiSelectMode: Boolean,
    selectedItems: Set<MainHomeItemSelection>,
    emit: MainUiIntent.() -> Unit
) {
    state.sessionGroups.forEach { group ->
        val characterId = group.characterId
        val expanded = characterId !in state.collapsedCharacterIds
        item(key = "character-$characterId") {
            SessionCharacterHeader(
                modifier = Modifier.animateItem(),
                characterName = group.characterName,
                sessionCount = group.sessions.size,
                expanded = expanded,
                onClick = { MainUiIntent.ToggleSessionGroup(characterId).emit() }
            )
        }
        if (!expanded) return@forEach
        items(
            items = group.sessions,
            key = { session -> "session-${session.id}" }
        ) { session ->
            RecentChatSessionCard(
                session = session,
                multiSelectMode = multiSelectMode,
                selectedItems = selectedItems,
                emit = emit
            )
        }
    }
}

private fun LazyListScope.recentGroupChatSessionItems(
    state: MainRecentGroupChatsState.Content,
    multiSelectMode: Boolean,
    selectedItems: Set<MainHomeItemSelection>,
    emit: MainUiIntent.() -> Unit
) {
    items(
        items = state.sessions,
        key = { "group-session-${it.id}" }
    ) { session ->
        RecentGroupChatSessionCard(
            session = session,
            multiSelectMode = multiSelectMode,
            selectedItems = selectedItems,
            emit = emit
        )
    }
}

private fun LazyListScope.recentStoryItems(
    state: MainRecentStoriesState.Content,
    multiSelectMode: Boolean,
    selectedItems: Set<MainHomeItemSelection>,
    emit: MainUiIntent.() -> Unit
) {
    items(
        items = state.stories,
        key = { "story-${it.id}" }
    ) { story ->
        RecentStoryCard(
            story = story,
            multiSelectMode = multiSelectMode,
            selectedItems = selectedItems,
            emit = emit
        )
    }
}

@Composable
private fun LazyItemScope.RecentChatSessionCard(
    session: MainChatSessionItem,
    multiSelectMode: Boolean,
    selectedItems: Set<MainHomeItemSelection>,
    emit: MainUiIntent.() -> Unit
) {
    val selection = MainHomeItemSelection(MainHomeItemType.Chat, session.id)
    HomeContentCard(
        modifier = Modifier.animateItem(),
        accentKey = session.characterName,
        icon = Icons.Rounded.ChatBubble,
        title = session.title,
        preview = session.preview,
        metadata = listOf(
            session.characterName,
            stringResource(R.string.message_count, session.messageCount),
            session.updatedAt
        ),
        multiSelectMode = multiSelectMode,
        selected = selection in selectedItems,
        onClick = {
            if (multiSelectMode) {
                MainUiIntent.ToggleItemSelection(selection).emit()
            } else {
                MainUiIntent.OpenChat(session.id).emit()
            }
        },
        onLongClick = {
            if (!multiSelectMode) MainUiIntent.EnterMultiSelect(selection).emit()
        },
        trailingContent = {
            HomeItemMenu(
                onRename = {
                    MainUiIntent.ShowRenameItemDialog(selection).emit()
                },
                onDelete = {
                    MainUiIntent.EnterMultiSelect(selection).emit()
                    MainUiIntent.ShowDeleteSelectedDialog.emit()
                }
            )
        }
    )
}

@Composable
private fun LazyItemScope.RecentGroupChatSessionCard(
    session: MainGroupChatSessionItem,
    multiSelectMode: Boolean,
    selectedItems: Set<MainHomeItemSelection>,
    emit: MainUiIntent.() -> Unit
) {
    val selection = MainHomeItemSelection(MainHomeItemType.GroupChat, session.id)
    HomeContentCard(
        modifier = Modifier.animateItem(),
        accentKey = session.title,
        icon = Icons.Rounded.Groups,
        title = session.title,
        preview = session.preview,
        metadata = listOf(
            session.memberNames,
            stringResource(R.string.message_count, session.messageCount),
            session.updatedAt
        ),
        multiSelectMode = multiSelectMode,
        selected = selection in selectedItems,
        onClick = {
            if (multiSelectMode) {
                MainUiIntent.ToggleItemSelection(selection).emit()
            } else {
                MainUiIntent.OpenGroupChat(session.id).emit()
            }
        },
        onLongClick = {
            if (!multiSelectMode) MainUiIntent.EnterMultiSelect(selection).emit()
        },
        trailingContent = {
            HomeItemMenu(
                onRename = {
                    MainUiIntent.ShowRenameItemDialog(selection).emit()
                },
                onDelete = {
                    MainUiIntent.EnterMultiSelect(selection).emit()
                    MainUiIntent.ShowDeleteSelectedDialog.emit()
                }
            )
        }
    )
}

@Composable
private fun LazyItemScope.RecentStoryCard(
    story: MainStoryItem,
    multiSelectMode: Boolean,
    selectedItems: Set<MainHomeItemSelection>,
    emit: MainUiIntent.() -> Unit
) {
    val selection = MainHomeItemSelection(
        type = MainHomeItemType.Story,
        itemId = story.id.toString()
    )
    HomeContentCard(
        modifier = Modifier.animateItem(),
        accentKey = story.title,
        icon = Icons.Rounded.AutoStories,
        title = story.title,
        preview = story.preview.ifBlank {
            stringResource(R.string.story_empty_document_preview)
        },
        metadata = listOf(
            stringResource(
                R.string.story_character_count,
                story.contentCharacterCount
            ),
            story.updatedAt
        ),
        multiSelectMode = multiSelectMode,
        selected = selection in selectedItems,
        onClick = {
            if (multiSelectMode) {
                MainUiIntent.ToggleItemSelection(selection).emit()
            } else {
                MainUiIntent.OpenStory(story.id).emit()
            }
        },
        onLongClick = {
            if (!multiSelectMode) MainUiIntent.EnterMultiSelect(selection).emit()
        },
        trailingContent = {
            HomeItemMenu(
                onRename = {
                    MainUiIntent.ShowRenameItemDialog(selection).emit()
                },
                onDelete = {
                    MainUiIntent.EnterMultiSelect(selection).emit()
                    MainUiIntent.ShowDeleteSelectedDialog.emit()
                }
            )
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeContentCard(
    modifier: Modifier = Modifier,
    accentKey: String,
    icon: ImageVector,
    title: String,
    preview: String,
    metadata: List<String>,
    multiSelectMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    trailingContent: (@Composable () -> Unit)? = null
) {
    val hapticFeedback = LocalHapticFeedback.current
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
        },
        label = "homeContentCardBorder"
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "homeContentCardContainer"
    )

    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(
            if (selected) 1.dp else 0.5.dp,
            borderColor
        )
    ) {
        val accentColor = remember(accentKey) { getMacaronColor(accentKey) }
        val ambientBrush = remember(accentColor) {
            Brush.horizontalGradient(
                listOf(
                    accentColor.copy(alpha = 0.08f),
                    accentColor.copy(alpha = 0.02f),
                    Color.Transparent
                )
            )
        }
        Row(
            modifier = Modifier
                .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
                .background(ambientBrush),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor, RoundedCornerShape(2.dp))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RpIconBubble(
                        icon = icon,
                        containerColor = accentColor.copy(alpha = 0.14f),
                        contentColor = accentColor,
                        modifier = Modifier.size(38.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                RpMetaRow(items = metadata)
            }
            if (multiSelectMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            } else {
                trailingContent?.invoke()
            }
        }
    }
}

@Composable
private fun HomeItemMenu(
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.more)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rename)) },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onRename()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun SessionCharacterHeader(
    modifier: Modifier = Modifier,
    characterName: String,
    sessionCount: Int,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = characterName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(R.string.session_group_count, sessionCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = stringResource(
                if (expanded) R.string.collapse_session_group else R.string.expand_session_group
            ),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun MainHomePagePreview() {
    AppTheme(dynamicColor = false) {
        MainLayout(
            uiState = MainUiState.Normal(
                homeState = MainHomeState(
                    resourceState = MainHomeResourceState(
                        totalCharacters = 24,
                        totalWorldBooks = 7
                    ),
                    recentChatsState = MainRecentChatsState.Content(
                        sessionGroups = listOf(
                            MainChatSessionGroup(
                                characterId = "1",
                                characterName = "Luna",
                                sessions = listOf(
                                    MainChatSessionItem(
                                        id = "1",
                                        characterId = "1",
                                        characterName = "Luna",
                                        title = "Night train",
                                        preview = "The city lights recede beyond the window.",
                                        messageCount = 18,
                                        updatedAt = "06-15 21:30",
                                        latestTime = 1L
                                    )
                                )
                            )
                        )
                    ),
                    recentGroupChatsState = MainRecentGroupChatsState.Content(
                        sessions = listOf(
                            MainGroupChatSessionItem(
                                id = "1",
                                title = "Expedition team",
                                memberNames = "Luna, Aster, Rowan",
                                preview = "We should reach the ruins before sunrise.",
                                messageCount = 42,
                                updatedAt = "06-15 22:10",
                                latestTime = 2L
                            )
                        )
                    ),
                    recentStoriesState = MainRecentStoriesState.Content(
                        stories = listOf(
                            MainStoryItem(
                                id = 1L,
                                title = "Rain over the old city",
                                preview = "The station clock stopped at midnight.",
                                contentCharacterCount = 12840,
                                updatedAt = "06-15 22:30",
                                latestTime = 3L
                            )
                        )
                    ),
                    allRecentItems = listOf(
                        MainStoryItem(
                            id = 1L,
                            title = "Rain over the old city",
                            preview = "The station clock stopped at midnight.",
                            contentCharacterCount = 12840,
                            updatedAt = "06-15 22:30",
                            latestTime = 3L
                        ),
                        MainGroupChatSessionItem(
                            id = "1",
                            title = "Expedition team",
                            memberNames = "Luna, Aster, Rowan",
                            preview = "We should reach the ruins before sunrise.",
                            messageCount = 42,
                            updatedAt = "06-15 22:10",
                            latestTime = 2L
                        ),
                        MainChatSessionItem(
                            id = "1",
                            characterId = "1",
                            characterName = "Luna",
                            title = "Night train",
                            preview = "The city lights recede beyond the window.",
                            messageCount = 18,
                            updatedAt = "06-15 21:30",
                            latestTime = 1L
                        )
                    )
                ),
                settingsState = MainSettingsState(
                    appearanceState = MainAppearanceSettingsState(
                        themeMode = AppThemeMode.FollowSystem
                    ),
                    identityState = MainUserIdentityState(
                        userName = "You",
                        userDescription = "",
                        userDescriptionPreview = "",
                        avatarState = MainUserAvatarState.None
                    ),
                    providerState = MainProviderSettingsState.Empty,
                    promptBehaviorState = MainPromptBehaviorState(
                        providerPostProcessingState = MainProviderPostProcessingState.Unavailable,
                        exampleDialogueBehavior = ExampleDialogueBehavior.default,
                        includeThinkInContext = false,
                        maxHistoryMessages = 500,
                        contextTrimmingAlert = true,
                        streamEnabled = true
                    ),
                    worldInfoBudgetState = MainWorldInfoBudgetState(
                        budgetPercent = 25,
                        budgetCap = 0,
                        overflowAlert = true
                    ),
                    summaryState = MainSummarySettingsState(
                        autoSummaryEnabled = false,
                        triggerMessageCount = 20,
                        wordsLimit = 500,
                        maxMessagesPerRequest = 0,
                        responseTokens = 800,
                        injectionState = MainSummaryInjectionState.AfterMain
                    ),
                    debugState = MainDebugSettingsState(enabled = false)
                )
            ),
            emit = {}
        )
    }
}

