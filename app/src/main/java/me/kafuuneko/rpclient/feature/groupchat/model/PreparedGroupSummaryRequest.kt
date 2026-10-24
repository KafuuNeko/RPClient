package me.kafuuneko.rpclient.feature.groupchat.model

import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.room.model.SummaryInputSnapshot

/** 已完成预算选择、可以直接发送并写回覆盖边界的群聊摘要请求。 */
internal data class PreparedGroupSummaryRequest(
    val request: LLMGenerationRequest,
    val coveredMessageId: Long,
    val inputSnapshot: SummaryInputSnapshot
)
