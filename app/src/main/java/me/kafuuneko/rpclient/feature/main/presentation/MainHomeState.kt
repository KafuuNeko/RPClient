package me.kafuuneko.rpclient.feature.main.presentation

import androidx.compose.ui.graphics.ImageBitmap
import me.kafuuneko.rpclient.feature.main.model.MainChatSessionGroup
import me.kafuuneko.rpclient.feature.main.model.MainHomeContentTab
import me.kafuuneko.rpclient.feature.main.model.MainHomeItemSelection
import me.kafuuneko.rpclient.feature.main.model.MainHomeItemType
import me.kafuuneko.rpclient.feature.main.model.items.MainChatSessionItem
import me.kafuuneko.rpclient.feature.main.model.items.MainGroupChatSessionItem
import me.kafuuneko.rpclient.feature.main.model.items.MainHomeContentItem
import me.kafuuneko.rpclient.feature.main.model.items.MainRecentCharacterItem
import me.kafuuneko.rpclient.feature.main.model.items.MainStoryItem
import me.kafuuneko.rpclient.libs.HomeRecentMode

/** 首页常驻的资源入口，以及当前选中的最近区域。 */
data class MainHomeState(
    val resourceState: MainHomeResourceState,
    val recentState: MainHomeRecentState,
) {
    /** 页面顶栏和底栏只在最近内容进入多选时切换操作。 */
    val activeSelection: MainHomeSelectionState.Selecting?
        get() = (recentState as? MainHomeRecentState.RecentContent)?.selectionState as? MainHomeSelectionState.Selecting
}

/** 两种布局的数据互斥，每个分支决定自己的模式和交互状态。 */
sealed class MainHomeRecentState(val mode: HomeRecentMode) {
    data class RecentContent(
        val recentChatsState: MainRecentChatsState = MainRecentChatsState.Empty,
        val recentGroupChatsState: MainRecentGroupChatsState = MainRecentGroupChatsState.Empty,
        val recentStoriesState: MainRecentStoriesState = MainRecentStoriesState.Empty,
        val allRecentItems: List<MainHomeContentItem> = emptyList(),
        val selectedContentTab: MainHomeContentTab = MainHomeContentTab.All,
        val selectionState: MainHomeSelectionState = MainHomeSelectionState.None,
    ) : MainHomeRecentState(HomeRecentMode.RecentContent) {
        /** 从当前首页状态中按 ID 查找故事。 */
        internal fun findStory(storyId: Long): MainStoryItem? {
            val content = recentStoriesState as? MainRecentStoriesState.Content ?: return null
            return content.stories.firstOrNull { it.id == storyId }
        }

        /** 从当前首页快照中读取可重命名内容的标题。 */
        internal fun findItemTitle(item: MainHomeItemSelection): String? {
            // 故事从故事分区读取；会话继续从合并列表读取，保留原有查找范围。
            return when (item.type) {
                MainHomeItemType.Chat -> allRecentItems
                    .filterIsInstance<MainChatSessionItem>()
                    .firstOrNull { it.id == item.itemId }
                    ?.title

                MainHomeItemType.GroupChat -> allRecentItems
                    .filterIsInstance<MainGroupChatSessionItem>()
                    .firstOrNull { it.id == item.itemId }
                    ?.title

                MainHomeItemType.Story -> item.itemId.toLongOrNull()
                    ?.let { storyId -> findStory(storyId) }
                    ?.title
            }
        }
    }

    data class RecentCharacters(
        val characters: List<MainRecentCharacterItem>,
        /** 只持有视口内头像，按文件 UUID 区分角色更换前后的图片。 */
        val avatars: Map<String, ImageBitmap> = emptyMap(),
        val avatarSizePx: Int = 0,
    ) : MainHomeRecentState(HomeRecentMode.RecentCharacters)
}

/** 合并不同内容类型，并仅依据最近一次对话或写作时间倒序排列。 */
internal fun mergeAllRecentItems(
    chats: List<MainChatSessionItem>,
    groupChats: List<MainGroupChatSessionItem>,
    stories: List<MainStoryItem>,
): List<MainHomeContentItem> {
    return (chats + groupChats + stories).sortedByDescending { it.latestTime }
}

/** 首页角色卡与世界书入口所需的资源统计。 */
data class MainHomeResourceState(
    /** 角色库中可供当前页面使用的角色总数。 */
    val totalCharacters: Int,
    /** 世界书库中可供当前页面使用的世界书总数。 */
    val totalWorldBooks: Int
)

/** 最近单聊列表状态；分组折叠属于该列表节点的可追踪 UI 状态。 */
sealed class MainRecentChatsState {
    data object Empty : MainRecentChatsState()

    data class Content(
        /** 按角色分组后的单聊会话列表。 */
        val sessionGroups: List<MainChatSessionGroup>,
        /** 主页会话分组中当前已折叠的角色 ID 集合。 */
        val collapsedCharacterIds: Set<String> = emptySet()
    ) : MainRecentChatsState()
}

/** 最近群聊列表的空内容与可渲染内容状态。 */
sealed class MainRecentGroupChatsState {
    data object Empty : MainRecentGroupChatsState()

    data class Content(
        /** 当前页面展示的会话列表。 */
        val sessions: List<MainGroupChatSessionItem>
    ) : MainRecentGroupChatsState()
}

/** 最近故事列表的空内容与可渲染内容状态。 */
sealed class MainRecentStoriesState {
    data object Empty : MainRecentStoriesState()

    data class Content(
        /** 当前页面展示的故事列表。 */
        val stories: List<MainStoryItem>
    ) : MainRecentStoriesState()
}

/** 首页普通浏览与批量选择的互斥状态。 */
sealed class MainHomeSelectionState {
    data object None : MainHomeSelectionState()

    data class Selecting(
        /** 多选模式下当前已选中的列表项集合。 */
        val selectedItems: Set<MainHomeItemSelection>
    ) : MainHomeSelectionState()
}

/** 切换单个主页内容的选择状态；内容类型属于稳定键的一部分。 */
internal fun MainHomeSelectionState.Selecting.toggleItem(
    item: MainHomeItemSelection,
): MainHomeSelectionState.Selecting {
    val updated = if (item in selectedItems) {
        selectedItems - item
    } else {
        selectedItems + item
    }
    return copy(selectedItems = updated)
}

/** 数据刷新后沿用有效的浏览状态，多选不跨数据刷新恢复。 */
internal fun MainHomeState.preserveBrowsingFrom(previous: MainHomeState): MainHomeState {
    // 两个模式各自恢复可保留的子状态，切换模式时直接采用新分支。
    val recent = when (val current = recentState) {
        is MainHomeRecentState.RecentContent -> {
            val old = previous.recentState as? MainHomeRecentState.RecentContent ?: return this
            current.copy(
                selectedContentTab = old.selectedContentTab,
                recentChatsState = current.recentChatsState.preserveCollapsedGroupsFrom(old.recentChatsState),
            )
        }

        is MainHomeRecentState.RecentCharacters -> {
            val old = previous.recentState as? MainHomeRecentState.RecentCharacters ?: return this
            // 编辑角色或恢复备份后，不能把已替换的头像带入新的列表。
            val uuids = current.characters.mapTo(mutableSetOf()) { it.avatarUuid }
            current.copy(
                avatars = old.avatars.filterKeys { it in uuids },
                avatarSizePx = old.avatarSizePx
            )
        }
    }
    return copy(recentState = recent)
}

/** 折叠状态只在刷新后仍然存在的单聊分组间继承。 */
private fun MainRecentChatsState.preserveCollapsedGroupsFrom(previous: MainRecentChatsState): MainRecentChatsState {
    if (this !is MainRecentChatsState.Content || previous !is MainRecentChatsState.Content) return this
    val available = sessionGroups.mapTo(mutableSetOf()) { it.characterId }
    return copy(collapsedCharacterIds = previous.collapsedCharacterIds.intersect(available))
}
