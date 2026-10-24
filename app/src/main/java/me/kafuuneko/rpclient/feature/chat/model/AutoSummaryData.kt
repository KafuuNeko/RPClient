package me.kafuuneko.rpclient.feature.chat.model

import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

/**
 * 自动总结流程所需的数据包装类。
 */
internal data class AutoSummaryData(
    /** 当前页面展示或编辑的会话数据。 */
    val session: ChatSession,
    /** 当前状态或操作关联的角色数据。 */
    val character: Character,
    /** 当前请求关联的模型供应商类型。 */
    val provider: LLMProvider
)
