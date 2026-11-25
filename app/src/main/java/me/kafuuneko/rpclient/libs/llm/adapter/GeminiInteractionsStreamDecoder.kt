package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.llm.LLMEmptyResponseException
import me.kafuuneko.rpclient.libs.llm.LLMResponseException
import me.kafuuneko.rpclient.libs.llm.model.LLMReasoningKind
import java.io.IOException

/**
 * 将一次 Interactions SSE 响应转换为普通聊天事件。
 * - 按步骤索引关联增量；若步骤交错，延后后续步骤输出，保证和完整响应的步骤顺序一致。
 * - 只在收到交互级终态时报告完成。步骤结束、连接关闭和取消都不能代替终态。
 */
internal class GeminiInteractionsStreamDecoder {
    private class Step(val type: String) {
        val pending = mutableListOf<LLMProviderStreamPart>()
        var closed = false
    }

    private val mSteps = linkedMapOf<Int, Step>()
    private var mLastIndex = -1
    private var mHasText = false
    var isFinished = false
        private set

    /**
     * 解析完整帧并返回本次新可展示的片段，签名和服务端工具步骤不进入正文。
     * @param event SSE 帧解码器交付的完整事件。
     * @return 按服务端步骤顺序可释放的正文、摘要或完成片段。
     * @throws LLMResponseException 帧结构或步骤时序无效。
     * @throws IOException 交互终态之前连接结束。
     */
    fun accept(event: SseEvent): List<LLMProviderStreamPart> {
        // 连接结束和步骤结束都不能替代交互终态，防止断流被当作完整回复。
        if (isFinished) return emptyList()
        val data = event.data.trim()
        if (data.isEmpty()) return emptyList()
        if (data == "[DONE]") throw IOException("Interaction closed before completion.")
        val json =
            parseStreamJsonObject(data) ?: throw LLMResponseException("invalid_stream_event")
        val type = json.cleanString("event_type").ifEmpty { event.event }
        // 按协议事件分发，只有交互终态能够完成这一轮生成。
        return when (type) {
            "step.start" -> start(json, event.data)
            "step.delta" -> delta(json, event.data)
            "step.stop" -> {
                openStep(json).closed = true
                drain()
            }

            "interaction.completed" -> finish(json)
            "error" -> throw LLMResponseException(json.objectOrNull("error")?.cleanString("code"))
            else -> emptyList()
        }
    }

    /**
     * 校验连接结束前已收到整轮终态，最后一条正文不能替代服务端完成确认。
     * @throws IOException 连接结束时交互尚未确认完成。
     */
    fun requireFinished() {
        if (!isFinished) throw IOException("Interaction stream closed before completion.")
    }

    /** 登记有序步骤；兼容开始帧内已经携带的文本。 */
    private fun start(json: JsonObject, raw: String): List<LLMProviderStreamPart> {
        val index = index(json)
        val data = json.objectOrNull("step") ?: throw LLMResponseException("invalid_step")
        if (index <= mLastIndex) throw LLMResponseException("invalid_step_order")
        mLastIndex = index
        val step = Step(data.cleanString("type"))
        mSteps[index] = step
        GeminiInteractionsResponseCodec.displayPart(data, raw)?.let { append(step, it) }
        return drain()
    }

    /** 依据步骤类型读取增量，避免误把工具或思考签名拼入聊天。 */
    private fun delta(json: JsonObject, raw: String): List<LLMProviderStreamPart> {
        // 增量必须归属仍开放的步骤，不能拼接到已结束或未知步骤中。
        val step = openStep(json)
        val delta = json.objectOrNull("delta") ?: throw LLMResponseException("invalid_step_delta")
        // 正文与思考摘要分别建模，工具数据和思考签名不进入可展示内容。
        when (step.type) {
            "model_output" -> if (delta.cleanString("type") == "text") append(
                step,
                LLMProviderStreamPart.Text(delta.interactionText(), raw)
            )

            "thought" -> if (delta.cleanString("type") == "thought_summary") {
                val content = delta.objectOrNull("content")
                    ?: throw LLMResponseException("invalid_summary_delta")
                if (content.cleanString("type") == "text") append(
                    step,
                    LLMProviderStreamPart.Reasoning(
                        content.interactionText(),
                        raw,
                        LLMReasoningKind.Summary
                    )
                )
            }
        }
        // 交错到达的后继步骤继续等待，保持与完整响应相同的展示顺序。
        return drain()
    }

    private fun index(json: JsonObject): Int =
        json.get("index")?.toString()?.toIntOrNull()?.takeIf { it >= 0 }
            ?: throw LLMResponseException("invalid_step_index")

    private fun openStep(json: JsonObject): Step =
        mSteps[index(json)]?.takeUnless { it.closed } ?: throw LLMResponseException("step_not_open")

    private fun append(step: Step, part: LLMProviderStreamPart) {
        if (part is LLMProviderStreamPart.Text && part.content.isNotBlank()) mHasText = true
        step.pending += part
    }

    /** 只释放当前最早步骤及其已确认的后继；已交付的正文不再留在缓冲中。 */
    private fun drain(): List<LLMProviderStreamPart> = buildList {
        val steps = mSteps.values.iterator()
        while (steps.hasNext()) {
            val step = steps.next()
            addAll(step.pending)
            step.pending.clear()
            if (!step.closed) break
            steps.remove()
        }
    }

    private fun finish(json: JsonObject): List<LLMProviderStreamPart> {
        // 不完整终态可以保留已收到的正文，但仍须验证步骤闭合和正文有效性。
        val response =
            json.objectOrNull("interaction") ?: throw LLMResponseException("missing_interaction")
        val reason = GeminiInteractionsResponseCodec.finishReason(response)
        if (mSteps.isNotEmpty()) throw LLMResponseException("unfinished_step")
        if (!mHasText) throw LLMEmptyResponseException()
        isFinished = true
        return listOf(
            LLMProviderStreamPart.Finished(
                finishReason = reason, model = response.cleanString("model").ifBlank { null },
                usage = GeminiInteractionsResponseCodec.usage(response),
            )
        )
    }
}
