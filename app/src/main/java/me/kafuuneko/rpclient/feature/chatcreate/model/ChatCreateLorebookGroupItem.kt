package me.kafuuneko.rpclient.feature.chatcreate.model

import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.model.LorebookWithEntries
import me.kafuuneko.rpclient.utils.filterLorebookGroups

/** 新建单聊页面中的世界书分组。 */
data class ChatCreateLorebookGroupItem(
    /** 关联世界书的唯一 ID。 */
    val lorebookId: Long,
    /** 关联世界书的显示名称。 */
    val lorebookName: String,
    /** 当前世界书或分组包含的条目数量。 */
    val entryCount: Int,
    /** 当前分组、请求或结果包含的条目列表。 */
    val entries: List<ChatCreateLorebookEntryItem>
)

/** 获取角色绑定的世界书条目 ID 集合。 */
internal fun Character.defaultLorebookEntryIds(
    lorebookGroups: List<ChatCreateLorebookGroupItem>,
): Set<Long> {
    if (characterLorebookId == 0L) return emptySet()
    return lorebookGroups
        .firstOrNull { it.lorebookId == characterLorebookId }
        ?.entries
        ?.mapTo(mutableSetOf()) { it.id }
        .orEmpty()
}

/** 根据关键词多字段过滤世界书分组与条目。 */
internal fun List<ChatCreateLorebookGroupItem>.filterForQuery(
    query: String,
): List<ChatCreateLorebookGroupItem> = filterLorebookGroups(
    query = query,
    groupName = { it.lorebookName },
    entries = { it.entries },
    entrySearchFields = { entry ->
        sequenceOf(entry.lorebookName, entry.name, entry.content) +
            entry.keywords.asSequence() +
            entry.secondaryKeywords.asSequence() +
            entry.category.asSequence()
    },
    copyWithEntries = { group, entries -> group.copy(entries = entries) },
)

/** 按条目的 order 构建创建页世界书分组，并排除空分组。 */
internal fun List<LorebookWithEntries>.toChatCreateLorebookGroups(): List<ChatCreateLorebookGroupItem> {
    // 保持各创建页原有字段投影，避免搜索能力或默认勾选状态变化。
    return map { lorebookWithEntries ->
        val lorebook = lorebookWithEntries.lorebook
        val entries = lorebookWithEntries.entries
            .sortedBy { entry -> entry.order }
            .map { entry ->
                ChatCreateLorebookEntryItem(
                    id = entry.id,
                    lorebookName = lorebook.name,
                    name = entry.name,
                    content = entry.content,
                    keywords = entry.getKeywordList(),
                    secondaryKeywords = entry.getSecondaryKeywordList(),
                    category = entry.getCategoryList(),
                    constant = entry.constant,
                    order = entry.order,
                    depth = entry.depth,
                )
            }
        ChatCreateLorebookGroupItem(
            lorebookId = lorebook.id,
            lorebookName = lorebook.name,
            entryCount = entries.size,
            entries = entries,
        )
    }.filter { it.entries.isNotEmpty() }
}
