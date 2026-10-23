package me.kafuuneko.rpclient.feature.chat.model

import me.kafuuneko.rpclient.utils.filterLorebookGroups

/** 单聊世界书选择器中的分组展示模型。 */
data class ChatLorebookGroupItem(
    /** 关联世界书的唯一 ID。 */
    val lorebookId: Long,
    /** 关联世界书的显示名称。 */
    val lorebookName: String,
    /** 当前列表中已启用条目的数量。 */
    val enabledCount: Int,
    /** 当前查询或统计包含的总数量。 */
    val totalCount: Int,
    /** 当前分组、请求或结果包含的条目列表。 */
    val entries: List<ChatLorebookEntryItem>
) {
    /** 分组内是否存在条目且全部已为当前会话启用。 */
    val isAllEnabled: Boolean
        get() = totalCount > 0 && enabledCount == totalCount
}

/**
 * 依据搜索词过滤世界书分组及其内部条目。
 *
 * 匹配范围：世界书名称、条目名称、条目正文、主关键字、次关键字。
 *
 * @param query 搜索关键词
 * @return 过滤后的世界书分组列表
 */
internal fun List<ChatLorebookGroupItem>.filterForQuery(
    query: String,
): List<ChatLorebookGroupItem> = filterLorebookGroups(
    query = query,
    groupName = { it.lorebookName },
    entries = { it.entries },
    entrySearchFields = { entry ->
        sequenceOf(entry.lorebookName, entry.name, entry.content) +
            entry.keywords.asSequence() +
            entry.secondaryKeywords.asSequence()
    },
    copyWithEntries = { group, entries -> group.copy(entries = entries) },
)
