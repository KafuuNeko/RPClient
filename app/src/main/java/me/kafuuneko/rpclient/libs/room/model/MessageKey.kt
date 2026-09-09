package me.kafuuneko.rpclient.libs.room.model

/**
 * 跨单聊与群聊的消息唯一标识。
 *
 * - 单聊和群聊的消息 ID 自增序列独立，即使数值相同也不能混用；
 *   [messageType] 与 [messageId] 共同确定一条消息的归属表和主键。
 * - 用于附件关系、聚合查询和编辑操作中标识消息来源。
 */
data class MessageKey(
    /** 消息所属的聊天类型，决定归属的消息表。 */
    val messageType: MessageType,
    /** 消息在所属表中的主键。 */
    val messageId: Long
)
