package me.kafuuneko.rpclient.feature.chatcreate.model

/** 新建单聊页可选择的世界书条目快照。 */
data class ChatCreateLorebookEntryItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
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
    /** 当前世界书条目的分类名称。 */
    val category: List<String>,
    /** 是否忽略关键词并始终激活当前世界书条目。 */
    val constant: Boolean,
    /** 当前对象在同类数据中的排序值。 */
    val order: Int,
    /** 当前内容相对聊天末尾的插入或扫描深度。 */
    val depth: Int
)
