package me.kafuuneko.rpclient.feature.chat.model

import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.room.model.SummaryInputSnapshot
import me.kafuuneko.rpclient.libs.room.model.SummaryDraftVersion
import me.kafuuneko.rpclient.libs.room.model.SummaryVersion

/** 已完成预算选择的摘要请求，保留草稿或持久化提交所需的覆盖位置与输入依赖。 */
internal data class PreparedSummaryRequest(
    val request: LLMGenerationRequest,
    val coveredMessageId: Long,
    val summaryIdToUpdate: Long?,
    val inputSnapshot: SummaryInputSnapshot,
    val sourceSummaries: List<SummaryVersion> = emptyList(),
    val intermediateVersions: List<SummaryDraftVersion> = emptyList(),
)
