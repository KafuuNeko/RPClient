package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import me.kafuuneko.rpclient.utils.toStringList

@Entity(
    tableName = "character"
)
data class Character(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // 角色名称
    val name: String,
    // 角色头像(存储的是File UUID，可通过[FileRepository]查找到文件数据)
    val avatar: String,
    // 角色标签(JSON, 例如["腼腆", "可爱"])
    val characterTags: String,
    // 角色描述
    val description: String,
    // 核心/性格设定
    val personality: String,
    // 场景设定
    val scenario: String,
    // 开场白
    val firstMessages: String,
    // 对话示例
    val examplesOfDialogue: String,
    // 后置提示词
    val postHistoryInstructions: String
) {
    fun getCharacterTagList(): List<String> {
        return Gson().toStringList(characterTags)
    }

    fun getFirstMessageList(): List<String> {
        return firstMessages.split("<START>")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}
