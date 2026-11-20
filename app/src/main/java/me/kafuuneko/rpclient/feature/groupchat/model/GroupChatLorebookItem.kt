package me.kafuuneko.rpclient.feature.groupchat.model

import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatLorebookGroupItem
import me.kafuuneko.rpclient.utils.filterLorebookGroups

/** 群聊设置中可添加角色的展示与检索信息。 */
data class GroupChatAvailableCharacterItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 供界面展示和业务识别的名称。 */
    val name: String,
    /** 当前角色是否已经属于目标群聊。 */
    val alreadyMember: Boolean,
    /** 角色简介摘要，供检索与识别使用。 */
    val description: String = "",
    /** 角色标签列表，供检索与识别使用。 */
    val tags: List<String> = emptyList(),
)

/**
 * 依据可选已选集合和搜索词过滤世界书分组及条目列表。
 *
 * @param query 搜索关键词
 * @param selectedEntryIds 非空时仅保留处于该集合内的条目
 * @return 过滤后的世界书分组列表
 */
internal fun List<GroupChatLorebookGroupItem>.filterForQuery(
    query: String,
    selectedEntryIds: Set<Long>? = null,
): List<GroupChatLorebookGroupItem> = filterLorebookGroups(
    query = query,
    groupName = { it.lorebookName },
    entries = { it.entries },
    entrySearchFields = { entry ->
        sequenceOf(entry.lorebookName, entry.name, entry.content) +
            entry.keywords.asSequence() +
            entry.secondaryKeywords.asSequence()
    },
    copyWithEntries = { group, entries -> group.copy(entries = entries) },
    entryFilter = selectedEntryIds?.let { ids -> { entry -> entry.id in ids } },
)

/** 更新世界书分组列表中所有条目的勾选激活状态。 */
internal fun List<GroupChatLorebookGroupItem>.withEnabledIds(
    selectedIds: Set<Long>,
): List<GroupChatLorebookGroupItem> = map { group ->
    group.copy(entries = group.entries.map { groupChatLorebookEntryItem ->
        groupChatLorebookEntryItem.copy(
            enabled = groupChatLorebookEntryItem.id in selectedIds
        )
    })
}
