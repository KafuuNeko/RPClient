package me.kafuuneko.rpclient.utils

/**
 * 按条目筛选条件、世界书名称和条目字段过滤分组。
 *
 * - 若提供了 [entryFilter]，先裁剪每个分组的可见条目并丢弃空分组，确保“仅看已启用”与搜索条件叠加时不泄漏未启用条目。
 * - 世界书名称命中时保留该分组通过 [entryFilter] 后的全部条目；仅条目命中时只保留命中的条目。
 *
 * @param query 搜索关键词
 * @param groupName 提取世界书分组名称
 * @param entries 提取分组下的条目列表
 * @param entrySearchFields 提取单个条目的搜索字段序列
 * @param copyWithEntries 用过滤后的条目重建分组
 * @param entryFilter 可选的条目级前置筛选条件
 * @return 过滤后的世界书分组列表
 */
fun <Group, Entry> List<Group>.filterLorebookGroups(
    query: String,
    groupName: (Group) -> String,
    entries: (Group) -> List<Entry>,
    entrySearchFields: (Entry) -> Sequence<String>,
    copyWithEntries: (Group, List<Entry>) -> Group,
    entryFilter: ((Entry) -> Boolean)? = null,
): List<Group> {
    // 先应用条目级前置过滤（如仅看已启用），并移除不再包含有效条目的分组
    val scopedGroups = if (entryFilter == null) {
        this
    } else {
        mapNotNull { group ->
            val filtered = entries(group).filter(entryFilter)
            if (filtered.isEmpty()) null else copyWithEntries(group, filtered)
        }
    }
    // 空搜索直接返回前置筛选后的分组
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) return scopedGroups
    // 世界书名称命中保留当前分组剩余条目，条目命中则仅保留匹配条目
    return scopedGroups.mapNotNull { group ->
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
