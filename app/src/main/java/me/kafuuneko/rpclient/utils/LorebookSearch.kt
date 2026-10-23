package me.kafuuneko.rpclient.utils

/**
 * 按世界书名称和条目字段过滤分组。
 *
 * 世界书名称命中时保留该分组的全部条目；仅条目命中时只保留命中的条目，
 * 这样搜索结果可以同时用于完整选择器和创建页的精确筛选。
 */
fun <Group, Entry> List<Group>.filterLorebookGroups(
    query: String,
    groupName: (Group) -> String,
    entries: (Group) -> List<Entry>,
    entrySearchFields: (Entry) -> Sequence<String>,
    copyWithEntries: (Group, List<Entry>) -> Group,
): List<Group> {
    // 空搜索保持原列表，调用方可以直接把结果写回可见状态。
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) return this
    // 世界书命中保留整组，条目命中则只保留匹配条目。
    return mapNotNull { group ->
        val groupMatches = groupName(group).contains(other = normalizedQuery, ignoreCase = true)
        val matchingEntries = entries(group).filter { entry ->
            entrySearchFields(entry).any { text ->
                text.contains(other = normalizedQuery, ignoreCase = true)
            }
        }
        when {
            groupMatches -> group
            matchingEntries.isNotEmpty() -> copyWithEntries(group, matchingEntries)
            else -> null
        }
    }
}
