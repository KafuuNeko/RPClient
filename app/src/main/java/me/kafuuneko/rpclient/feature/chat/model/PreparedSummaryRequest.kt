package me.kafuuneko.rpclient.feature.chat.model

import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.room.model.SummaryInputSnapshot

/** 已完成预算选择、可以直接发送并写回覆盖边界的摘要请求。 */
internal data class PreparedSummaryRequest(
    val request: LLMGenerationRequest,
    val coveredMessageId: Long,
    val summaryIdToUpdate: Long?,
    val inputSnapshot: SummaryInputSnapshot
)
