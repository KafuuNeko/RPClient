package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.google.gson.Gson
import me.kafuuneko.rpclient.utils.toStringList

/**
 * 本地角色卡实体。
 *
 * 角色卡的列表及第三方扩展字段以 JSON 或兼容字符串保存，导入导出时由 CharacterCardMapper
 * 负责与 Character Card V2 格式转换。
 */
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
    // 角色备注
    @ColumnInfo(defaultValue = "")
    val creatorNotes: String = "",
    // 核心/性格设定
    val personality: String,
    // 场景设定
    val scenario: String,
    // 开场白
    val firstMessages: String,
    // 对话示例
    val examplesOfDialogue: String,
    // 后置提示词
    val postHistoryInstructions: String,
    // 角色级 Main Prompt 覆盖；为空时使用 AppModel.mainPrompt，支持 {{original}} 引用全局提示词。
    @ColumnInfo(defaultValue = "")
    val systemPrompt: String = "",
    // Character Card V2 的作者字段，仅作为元数据展示和导出，不参与默认 prompt。
    @ColumnInfo(defaultValue = "")
    val creator: String = "",
    // Character Card V2 的角色版本字段，用于导入导出兼容。
    @ColumnInfo(defaultValue = "")
    val characterVersion: String = "",
    // 备用开场白列表，按 JSON 数组保存，兼容 V2 alternate_greetings。
    @ColumnInfo(defaultValue = "[]")
    val alternateGreetings: String = "[]",
    // 角色卡 extensions 原始兼容数据；未被当前 App 识别的第三方字段会保存在这里。
    @ColumnInfo(defaultValue = "{}")
    val extensionsJson: String = "{}",
    // Character's Note / depth_prompt 的正文，按 depth 插入聊天历史内部。
    @ColumnInfo(defaultValue = "")
    val depthPromptPrompt: String = "",
    // Character's Note 的插入深度；0 表示聊天末尾，1 表示最后一条消息之前。
    @ColumnInfo(defaultValue = "4")
    val depthPromptDepth: Int = 4,
    // Character's Note 的消息角色：0=system，1=user，2=assistant。
    @ColumnInfo(defaultValue = "0")
    val depthPromptRole: Int = 0,
    // 从角色卡 data.character_book 导入并绑定的世界书 ID；0 表示未绑定。
    @ColumnInfo(defaultValue = "0")
    val characterLorebookId: Long = 0L
) {
    /** 解析角色标签 JSON；损坏数据由 Gson 工具按空列表处理。 */
    fun getCharacterTagList(): List<String> {
        return Gson().toStringList(characterTags)
    }

    /** 解析主开场白；历史格式使用 `<START>` 分隔多条内容。 */
    fun getFirstMessageList(): List<String> {
        return firstMessages.split("<START>")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /** 解析 Character Card V2 的备用开场白数组。 */
    fun getAlternateGreetingList(): List<String> {
        return Gson().toStringList(alternateGreetings)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /** 返回聊天创建页可选择的全部主开场白和备用开场白。 */
    fun getChatFirstMessageList(): List<String> {
        return getFirstMessageList() + getAlternateGreetingList()
    }
}
