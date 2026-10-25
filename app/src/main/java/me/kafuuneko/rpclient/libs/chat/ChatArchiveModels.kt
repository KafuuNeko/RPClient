package me.kafuuneko.rpclient.libs.chat

/**
 * 可导入、导出的单聊归档。
 *
 * 该模型不携带本地 Room 主键；角色绑定由用户在导入确认阶段重新选择，避免跨设备
 * 复用自增 ID 导致误关联。
 */
data class ChatArchive(
    /** 供界面展示或持久化的标题。 */
    val title: String,
    /** 记录创建时的时间戳，单位为毫秒。 */
    val createTime: Long,
    /** 相关记录最后更新或活动的时间戳，单位为毫秒。 */
    val latestTime: Long,
    /** 当前会话或 Prompt 使用的用户名称。 */
    val userName: String,
    /** 当前会话或 Prompt 使用的用户设定。 */
    val userDescription: String,
    /** 仅供当前会话 Prompt 使用的用户备注。 */
    val userNote: String,
    /** 作者提供的角色使用说明和备注。 */
    val creatorNotes: String?,
    /** 用于快速判断条目是否存在的世界书条目集合。 */
    val lorebookEntrySet: String,
    /** 序列化后的世界书时序状态，需要随会话或故事持久化。 */
    val worldInfoStateJson: String,
    /** 当前会话的自动摘要是否暂停。 */
    val autoSummaryPaused: Boolean,
    /** 无法直接取得角色时使用的名称提示。 */
    val characterNameHint: String,
    /** 用于匹配同一角色卡内容的稳定指纹。 */
    val characterFingerprint: String?,
    /** 当前状态或请求包含的消息列表。 */
    val messages: List<ChatArchiveMessage>,
    /** 当前会话或故事使用的摘要内容。 */
    val summary: ChatArchiveSummary?,
    /** 本次导入暂存的所有者，只在当前进程使用，不写入归档。 */
    val importOwner: String? = null,
    /** 会话级角色卡示例对话开关，旧归档缺省时保持开启。 */
    val includeCharacterExamples: Boolean = true
)

/** 归档中的普通消息；列表顺序是唯一权威的对话顺序。 */
data class ChatArchiveMessage(
    /** 记录创建时的时间戳，单位为毫秒。 */
    val createTime: Long,
    /** 当前对象在业务流程中承担的角色。 */
    val role: ChatArchiveMessageRole,
    /** 当前对象承载的正文内容。 */
    val content: String,
    /** 按消息显示顺序保存的图片，正文为空也不能丢弃。 */
    val images: List<ChatArchiveImage> = emptyList()
)

/** 归档解析中的图片；外部引用仅用于导入阶段匹配资源，不写入数据库。 */
data class ChatArchiveImage(
    val mimeType: String? = null,
    val data: String? = null,
    val sourceUrl: String? = null,
    /** 导入时将 Base64 移到私有暂存后的随机资源键，不含路径且不导出。 */
    val resourceKey: String? = null,
    /** 归档压缩前文件字节的 SHA-256，小写十六进制；不是 Base64 或 GZIP 的摘要。 */
    val hash: String? = null,
    /** data 解码后的传输封装；恢复到暂存后重置为 none。 */
    val compression: String = "none"
)

/** 原子导入完成后的会话及未能恢复的图片数量。 */
data class ChatArchiveImportResult(val sessionId: Long, val skippedImages: Int)

/** RPClient 单聊消息与 SillyTavern 消息标记之间的稳定角色集合。 */
enum class ChatArchiveMessageRole {
    User,

    Character,

    Narrator
}

/**
 * 归档中的最新总结快照。
 *
 * [coveredMessageIndex] 使用普通消息列表的零基索引，-1 表示空边界。数据库写入后再转换为
 * 新生成的消息主键，避免把来源数据库 ID 带入目标数据库。
 */
data class ChatArchiveSummary(
    /** 当前对象承载的正文内容。 */
    val content: String,
    /** 记录创建时的时间戳，单位为毫秒。 */
    val createTime: Long,
    /** 摘要已经覆盖到的最后一条消息位置。 */
    val coveredMessageIndex: Int
)
