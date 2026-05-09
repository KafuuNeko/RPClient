package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.Gson
import me.kafuuneko.rpclient.utils.toStringList

@Entity(
    tableName = "lorebook_entries",
    foreignKeys = [
        ForeignKey(
            entity = Lorebook::class,
            parentColumns = ["id"],
            childColumns = ["lorebookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("lorebookId")
    ]
)
data class LorebookEntry(
    // 世界书条目id
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // 世界树id, 关联[Lorebook.id]
    val lorebookId: Long,
    // 名称
    val name: String,
    // 关键词(Json)
    val keywords: String,
    // 次要关键词(Json)
    val secondaryKeywords: String,
    // 插入顺序（数值越小优先级越高）
    val order: Int,
    // 插入深度（0放在最顶端，1插入在最后一条消息和倒数第二条消息之间，2插入在倒数第二条和倒数第三条之间, ...）
    val depth: Int,
    // 分类标签(Json)
    val category: String,
    // 条目内容
    val content: String
) {
    fun getKeywordList(): List<String> {
        return Gson().toStringList(keywords)
    }

    fun getSecondaryKeywordList(): List<String> {
        return Gson().toStringList(secondaryKeywords)
    }

    fun getCategoryList(): List<String> {
        return Gson().toStringList(category)
    }
}
