package me.kafuuneko.rpclient.ui.model

/**
 * 创建页摘要卡片中单本已启用的世界书。
 *
 * @param lorebookId 世界书唯一标识
 * @param name 世界书名称
 * @param selectedCount 当前已启用的条目数
 * @param totalCount 该世界书包含的全部条目数
 */
data class WorldBookSelectionSummaryItem(
    val lorebookId: Long,
    val name: String,
    val selectedCount: Int,
    val totalCount: Int,
)

/**
 * 保留包含已选条目的世界书，并按原分组顺序构建摘要。
 *
 * @param id 提取分组关联的世界书 ID
 * @param name 提取世界书名称
 * @param totalCount 提取分组的总条目数
 * @param selectedCount 统计分组中已选择的条目数
 * @return 供创建页展示的书目摘要
 */
internal fun <Group> List<Group>.selectedWorldBookSummaries(
    id: (Group) -> Long,
    name: (Group) -> String,
    totalCount: (Group) -> Int,
    selectedCount: (Group) -> Int,
): List<WorldBookSelectionSummaryItem> = mapNotNull { group ->
    val count = selectedCount(group)
    if (count == 0) return@mapNotNull null
    WorldBookSelectionSummaryItem(
        lorebookId = id(group),
        name = name(group),
        selectedCount = count,
        totalCount = totalCount(group),
    )
}
