package me.kafuuneko.rpclient.feature.main.presentation

import me.kafuuneko.rpclient.feature.main.model.MainChatSessionGroup
import me.kafuuneko.rpclient.feature.main.model.items.MainChatSessionItem
import me.kafuuneko.rpclient.feature.main.model.items.MainHomeContentItem
import me.kafuuneko.rpclient.feature.main.model.MainHomeItemSelection
import me.kafuuneko.rpclient.feature.main.model.items.MainGroupChatSessionItem
import me.kafuuneko.rpclient.feature.main.model.items.MainStoryItem

/** 首页内容流的筛选分类。 */
enum class MainHomeContentTab {
    All,
    Single,
    Group,
    Story
}

/** 首页状态树，组合资源统计、会话、故事、内容筛选 Tab 和多选交互状态。 */
data class MainHomeState(
    /** 主页角色和世界书资源统计的状态。 */
    val resourceState: MainHomeResourceState,
    /** 主页最近单聊区域的加载和内容状态。 */
    val recentChatsState: MainRecentChatsState,
    /** 主页最近群聊区域的加载和内容状态。 */
    val recentGroupChatsState: MainRecentGroupChatsState,
    /** 主页最近故事区域的加载和内容状态。 */
    val recentStoriesState: MainRecentStoriesState,
    /** 合并单聊、群聊和故事后的最近项目列表。 */
    val allRecentItems: List<MainHomeContentItem> = emptyList(),
    /** 主页内容区域当前选中的标签页。 */
    val selectedContentTab: MainHomeContentTab = MainHomeContentTab.All,
    /** 主页多选模式与已选项目的状态。 */
    val selectionState: MainHomeSelectionState = MainHomeSelectionState.None
)

/** 合并不同内容类型，并仅依据最近一次对话或写作时间倒序排列。 */
internal fun mergeAllRecentItems(
    chats: List<MainChatSessionItem>,
    groupChats: List<MainGroupChatSessionItem>,
    stories: List<MainStoryItem>
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
    item: MainHomeItemSelection
): MainHomeSelectionState.Selecting {
    val updated = if (item in selectedItems) {
        selectedItems - item
    } else {
        selectedItems + item
    }
    return copy(selectedItems = updated)
}

/**
 * 数据刷新后仅保留仍存在的单聊分组折叠状态。
 *
 * 多选状态不跨刷新恢复，避免数据库内容变化后保留失效的内容选择。
 */
internal fun MainHomeState.preserveCollapsedGroupsFrom(
    previous: MainHomeState
): MainHomeState {
    val tabPreserved = copy(selectedContentTab = previous.selectedContentTab)
    val refreshed = tabPreserved.recentChatsState as? MainRecentChatsState.Content ?: return tabPreserved
    val previousContent = previous.recentChatsState as? MainRecentChatsState.Content ?: return tabPreserved
    val availableCharacterIds = refreshed.sessionGroups.mapTo(mutableSetOf()) { it.characterId }
    return tabPreserved.copy(
        recentChatsState = refreshed.copy(
            collapsedCharacterIds = previousContent.collapsedCharacterIds
                .intersect(availableCharacterIds)
        )
    )
}
