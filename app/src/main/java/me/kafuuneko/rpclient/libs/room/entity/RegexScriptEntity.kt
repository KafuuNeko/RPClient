package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Regex 脚本的 Room 持久化实体。
 *
 * [characterId] 为空时表示全局脚本，非空时表示对应角色的脚本。SillyTavern 脚本 ID
 * 不是数据库主键，因为第三方角色卡可能携带空白或重复 ID；数据库使用独立行 ID 保证导入不丢数据。
 */
@Entity(
    tableName = "regex_scripts",
    foreignKeys = [
        ForeignKey(
            entity = Character::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["characterId", "sortOrder"])
    ]
)
data class RegexScriptEntity(
    // Room 内部行 ID；不参与 SillyTavern 脚本导入导出。
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0L,
    // 所属角色 ID；为空表示 Global，非空表示对应 Character，角色删除时脚本级联删除。
    val characterId: Long? = null,
    // SillyTavern RegexScriptData 的外部脚本 ID，用于编辑、复制和导入导出。
    val scriptId: String,
    // 脚本在同一 Global 或 Character 作用域内的执行顺序，数值越小越先执行。
    val sortOrder: Int,
    // 脚本显示名称。
    val scriptName: String,
    // 查找表达式，兼容 `/pattern/flags` 和普通表达式格式。
    val findRegex: String,
    // 替换文本，支持捕获组引用和 RPClient Regex 宏。
    val replaceString: String,
    // 替换捕获组时需要从捕获内容中移除的字符串列表，按 JSON 数组保存。
    val trimStringsJson: String = "[]",
    // 允许执行该脚本的 SillyTavern placement 数值列表，按 JSON 数组保存。
    val placementJson: String = "[]",
    // 是否禁用脚本；禁用后任何执行模式都不会运行。
    val disabled: Boolean = false,
    // 是否仅在 Markdown 展示阶段执行。
    val markdownOnly: Boolean = false,
    // 是否仅在发送给模型的 Prompt 构建阶段执行。
    val promptOnly: Boolean = false,
    // 编辑已有消息时是否允许执行。
    val runOnEdit: Boolean = false,
    // Find Regex 的宏替换模式，对应 RegexFindMacroMode 的持久化数值。
    val substituteRegex: Int = 0,
    // 允许执行的最小消息深度；为空表示不限制下界。
    val minDepth: Int? = null,
    // 允许执行的最大消息深度；为空表示不限制上界。
    val maxDepth: Int? = null,
    // 导入时的原始脚本 JSON，用于保留当前版本尚未识别的第三方扩展字段。
    val rawJson: String = "{}"
)
