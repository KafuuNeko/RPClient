package me.kafuuneko.rpclient.feature.chat.model

/** 单聊页面使用的会话设置快照，不直接暴露 Room 实体。 */
data class ChatSessionItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 供界面展示或持久化的标题。 */
    val title: String,
    /** 当前会话是否存在可供展示的摘要。 */
    val summarize: String,
    /** 仅供当前会话 Prompt 使用的用户备注。 */
    val userNote: String,
    /** 当前会话或 Prompt 使用的用户名称。 */
    val userName: String,
    /** 当前会话或 Prompt 使用的用户设定。 */
    val userDescription: String,
    /** 作者提供的角色使用说明和备注。 */
    val creatorNotes: String,
    /** 当前会话的自动摘要是否暂停。 */
    val autoSummaryPaused: Boolean,
    /** 当前会话或分组包含的消息数量。 */
    val messageCount: Int,
    /** 当前会话已启用的世界书条目 ID 集合。 */
    val enabledLorebookEntryIds: Set<Long>
)
