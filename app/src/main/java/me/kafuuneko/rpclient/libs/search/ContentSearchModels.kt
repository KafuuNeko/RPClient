package me.kafuuneko.rpclient.libs.search

/** 稳定的内容类型编码，与持久索引、导航参数一致。 */
enum class SearchKind(val code: Int) { All(0), Chat(1), Group(2), Story(3) }

/** 全部、轻量元数据或完整正文的显式检索范围。 */
enum class SearchScope { All, Metadata, Body }

/** 一次提交固定的查询快照，不解释用户输入中的 MATCH 运算符。 */
data class SearchRequest(
    val query: String,
    val kind: SearchKind = SearchKind.All,
    val scope: SearchScope = SearchScope.All,
    val parentId: Long? = null,
    val directScan: Boolean = false
)

/** 对象标题及角色名称的轻量投影，不加载角色卡或消息正文。 */
data class SearchObject(
    val kind: Int,
    val parentId: Long,
    val title: String,
    val names: String,
    val latestTime: Long,
    val sourceId: Long = 0
)

/** 搜索结果领域模型；UI 负责文案与主题，高亮位置相对于 snippet 的 UTF-16。 */
data class SearchHit(
    val kind: Int,
    val parentId: Long,
    val title: String,
    val names: String,
    val latestTime: Long,
    val sourceId: Long = 0,
    val snippet: String = "",
    val matchStart: Int = -1,
    val matchEnd: Int = -1,
    val sourceLabel: String = ""
) {
    val key: String get() = "$kind:$parentId"
}

/** 对象分页使用稳定排序锚点；revision 变化时拒绝继续旧分页。 */
data class SearchCursor(
    val revision: Long,
    val phase: Int,
    val time: Long,
    val kind: Int,
    val id: Long,
    val sourceId: Long = 0
)

/** 一页结果及完整性；未补建完成不能发布确定的零结果。 */
data class SearchPage(val hits: List<SearchHit>, val next: SearchCursor?, val complete: Boolean)

/** 后台工作器状态，失败只影响派生索引。 */
sealed class SearchIndexState {
    data object Preparing : SearchIndexState()
    data object Ready : SearchIndexState()
    data object Updating : SearchIndexState()
    data object Unavailable : SearchIndexState()
}

/** 数据已变化，需要从首页重新提交，防止跨版本分页漏项。 */
class SearchChangedException : IllegalStateException("Search data changed")

/** DAO 返回的正文匹配块，不把全库正文载入内存。 */
data class SearchChunkMatch(
    val kind: Int,
    val parentId: Long,
    val sourceId: Long,
    val rowid: Long,
    val latestTime: Long,
    val title: String,
    val names: String,
    val tokens: String,
    val sourceLabel: String
)
