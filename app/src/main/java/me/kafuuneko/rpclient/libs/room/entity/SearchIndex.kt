package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/** 正文索引的持久源代次；删除标记由工作器在无旧任务的安全点回收。 */
@Entity(tableName = "search_sources", primaryKeys = ["kind", "sourceId"], indices = [
    Index(value = ["dirty", "changedTime"]), Index(value = ["deleted", "dirty", "kind", "sourceId"])
])
data class SearchSource(
    val kind: Int,
    val sourceId: Long,
    val parentId: Long,
    val revision: Long = 1,
    val indexedRevision: Long = 0,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
    val changedTime: Long = 0
)

/** 可重建的正文块；只持久化可逆字符 token，当前页展示时解码，避免重复保存正文。 */
@Entity(tableName = "search_chunks", indices = [Index(value = ["kind", "sourceId"]), Index(value = ["kind", "parentId"])])
data class SearchChunk(
    @PrimaryKey(autoGenerate = true) val rowid: Long = 0,
    val kind: Int,
    val sourceId: Long,
    val parentId: Long,
    val revision: Long,
    val position: Int,
    val tokens: String
)

/** simple 分词器只处理应用生成的字符 token，短语匹配保留相邻顺序。 */
@Fts4(contentEntity = SearchChunk::class, tokenizer = "simple")
@Entity(tableName = "search_chunks_fts")
data class SearchChunkFts(val tokens: String)

/**
 * 搜索持久状态，保留 Version 5 的既有结构。
 * - kind = -2 是唯一生命周期控制行，cursor 保存 SearchIndexPhase 编码，关闭后仍保留。
 * - kind = -1 保存投影格式，kind = 0 保存全库变更代次，正数保存各源表的补建游标。
 * - 清理会删除控制行以外的全部进度，不重置故事等业务修订号。
 */
@Entity(tableName = "search_progress")
data class SearchProgress(
    @PrimaryKey val kind: Int,
    val cursor: Long = 0,
    val complete: Boolean = false
)
