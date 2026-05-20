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
    // 是否常驻激活；开启后不需要关键词命中也会进入本轮世界书候选结果。
    val constant: Boolean = false,
    // 插入顺序（数值越小优先级越高）
    val order: Int,
    // 插入深度（0放在最顶端，1插入在最后一条消息和倒数第二条消息之间，2插入在倒数第二条和倒数第三条之间, ...）
    val depth: Int,
    // 分类标签(Json)
    val category: String,
    // 条目内容
    val content: String,
    // 是否禁用条目；禁用后即使关键词命中也不会进入候选激活结果。
    val disabled: Boolean = false,
    // 插入位置，见 POSITION_* 常量，兼容 ST 的 before/after/at-depth/outlet 等位置。
    val position: Int = POSITION_AT_DEPTH,
    // 注入消息角色，见 ROLE_* 常量。
    val role: Int = ROLE_SYSTEM,
    // 触发概率，0-100；命中关键词后仍会按概率抽取。
    val probability: Int = 100,
    // 是否忽略世界书预算；开启后该条目不计入 worldInfoBudgetPercent 裁剪。
    val ignoreBudget: Boolean = false,
    // 条目级扫描深度；为空时使用默认扫描深度。
    val scanDepth: Int? = null,
    // secondaryKeywords 的判定逻辑，见 LOGIC_* 常量。
    val selectiveLogic: Int = LOGIC_AND_ANY,
    // 是否整词匹配；为空表示沿用默认的普通包含匹配。
    val matchWholeWords: Boolean? = null,
    // 是否大小写敏感；为空或 false 时按忽略大小写处理。
    val caseSensitive: Boolean? = null,
    // ST inclusion group 相关字段：同组条目可按权重或 override 规则只激活一个。
    val useGroupScoring: Boolean = false,
    val group: String = "",
    val groupOverride: Boolean = false,
    val groupWeight: Int? = null,
    // 递归扫描时阻止该条目被后续轮次触发。
    val preventRecursion: Boolean = false,
    // 仅允许递归扫描触发，首轮聊天扫描不触发。
    val delayUntilRecursion: Boolean = false,
    // 激活后保持若干消息轮次，即使下一轮未命中关键词也继续注入。
    val sticky: Int? = null,
    // sticky 结束后冷却若干消息轮次，冷却期内不再触发。
    val cooldown: Int? = null,
    // 聊天消息数达到该值前不允许触发。
    val delay: Int? = null,
    // outlet 名称；position=POSITION_OUTLET 时通过 {{outlet::name}} 注入。
    val outletName: String = "",
    // 额外触发词列表，按 JSON 数组保存，导入部分 ST 扩展时使用。
    val triggers: String = "[]",
    // 是否把用户 persona 描述纳入扫描；当前单角色场景暂未接入 persona 源。
    val matchPersonaDescription: Boolean = false,
    // 是否把角色描述纳入扫描源。
    val matchCharacterDescription: Boolean = false,
    // 是否把角色性格设定纳入扫描源。
    val matchCharacterPersonality: Boolean = false,
    // 是否把 Character's Note 纳入扫描源。
    val matchCharacterDepthPrompt: Boolean = false,
    // 是否把场景设定纳入扫描源。
    val matchScenario: Boolean = false,
    // 是否把 creator_notes 纳入扫描源；creator_notes 本身不会默认进入 prompt。
    val matchCreatorNotes: Boolean = false,
    // 条目 extensions 原始兼容数据，用于保留当前 App 未显式支持的第三方字段。
    val extensionsJson: String = "{}",
    // 导入时的原始条目 JSON，便于排查兼容问题和后续扩展映射。
    val rawJson: String = "{}"
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

    fun getTriggerList(): List<String> {
        return Gson().toStringList(triggers)
    }

    companion object {
        // secondaryKeywords 逻辑：主关键词命中后，再用以下规则过滤 secondaryKeywords。
        const val LOGIC_AND_ANY = 0
        const val LOGIC_NOT_ALL = 1
        const val LOGIC_NOT_ANY = 2
        const val LOGIC_AND_ALL = 3

        // 世界书插入位置，数值会持久化到数据库和角色卡 extensions。
        const val POSITION_BEFORE = 0
        const val POSITION_AFTER = 1
        const val POSITION_AN_TOP = 2
        const val POSITION_AN_BOTTOM = 3
        const val POSITION_AT_DEPTH = 4
        const val POSITION_EXAMPLE_TOP = 5
        const val POSITION_EXAMPLE_BOTTOM = 6
        const val POSITION_OUTLET = 7

        // 注入消息角色，适配不同 LLM adapter 时可能会降级。
        const val ROLE_SYSTEM = 0
        const val ROLE_USER = 1
        const val ROLE_ASSISTANT = 2
    }
}
