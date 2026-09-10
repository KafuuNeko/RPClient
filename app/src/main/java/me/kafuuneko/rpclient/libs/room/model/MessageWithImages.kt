package me.kafuuneko.rpclient.libs.room.model

import me.kafuuneko.rpclient.libs.room.entity.FileEntity
import me.kafuuneko.rpclient.libs.room.entity.MessageImageEntity

/**
 * 消息正文与按 position 排序的附件聚合快照。
 *
 * - Repository 在一致读取事务内组装此模型，DAO 不直接返回此类型。
 * - Feature 通过 Repository 获取聚合结果，不直接访问 DAO。
 * - Prompt 准备层使用同一领域输入，不逐图查 DAO。
 */
data class MessageWithImages(
    /** 消息的跨类型唯一标识。 */
    val key: MessageKey,
    /** 消息正文。 */
    val content: String,
    /** 消息来源类型的原始名称（如 User / Char / Character 等）。 */
    val source: String,
    /** 消息创建时间。 */
    val createTime: Long,
    /** 按 position 排序的附件及其文件索引快照；无附件时为空列表。 */
    val images: List<MessageImageWithFile>,
    /** 群聊消息的发言角色 ID；单聊和非角色消息为空。 */
    val speakerCharacterId: Long? = null,
    /** 群聊消息生成时的发言者名称快照；单聊消息为空。 */
    val speakerNameSnapshot: String? = null,
    /** 单聊摘要消息的覆盖边界；普通消息为空。 */
    val coveredMessageId: Long? = null
)

/**
 * 单张附件的关联记录与文件索引快照。
 *
 * - [file] 为空表示文件索引丢失，上层应展示缺失状态并阻止含图请求。
 */
data class MessageImageWithFile(
    /** 附件关联记录。 */
    val image: MessageImageEntity,
    /** 对应的文件索引实体；不存在时为空。 */
    val file: FileEntity?
)

/** 消息分页的附件聚合结果，游标仍取首条消息的 createTime 和真实消息 ID。 */
data class MessageImagePage(
    val messages: List<MessageWithImages>,
    val canLoadOlderMessages: Boolean,
    val totalMessageCount: Int
)
