package me.kafuuneko.rpclient.libs.groupchat.model

/** 群聊发言人选择策略。 */
enum class GroupChatActivationStrategy { Manual, Natural, List, Pooled }

/** 多角色卡进入 Prompt 时采用的组合模式。 */
enum class GroupChatCharacterCardMode { Swap, Join }

/** 群聊消息在展示层中的来源。 */
enum class GroupChatMessageSource { User, Character, System }

/** 群聊页面共享的世界书分组及其会话启用状态。 */
data class GroupChatLorebookGroupItem(
    /** 关联世界书的唯一 ID。 */
    val lorebookId: Long,
    /** 关联世界书的显示名称。 */
    val lorebookName: String,
    /** 当前分组、请求或结果包含的条目列表。 */
    val entries: List<GroupChatLorebookEntryItem>
) {
    val enabledCount: Int
        get() = entries.count { it.enabled }

    val totalCount: Int
        get() = entries.size

    val isAllEnabled: Boolean
        get() = totalCount > 0 && enabledCount == totalCount
}

/** 群聊页面共享的世界书条目展示数据。 */
data class GroupChatLorebookEntryItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 关联世界书的唯一 ID。 */
    val lorebookId: Long,
    /** 关联世界书的显示名称。 */
    val lorebookName: String,
    /** 供界面展示和业务识别的名称。 */
    val name: String,
    /** 当前对象承载的正文内容。 */
    val content: String,
    /** 用于触发世界书条目的主关键词列表。 */
    val keywords: List<String>,
    /** 与主关键词共同参与筛选的次要关键词列表。 */
    val secondaryKeywords: List<String>,
    /** 是否忽略关键词并始终激活当前世界书条目。 */
    val constant: Boolean,
    /** 当前对象在同类数据中的排序值。 */
    val order: Int,
    /** 当前内容相对聊天末尾的插入或扫描深度。 */
    val depth: Int,
    /** 当前对象或功能是否启用。 */
    val enabled: Boolean
)
