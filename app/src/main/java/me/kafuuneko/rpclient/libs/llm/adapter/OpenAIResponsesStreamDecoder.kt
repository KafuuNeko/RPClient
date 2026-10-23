package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.llm.LLMEmptyResponseException
import me.kafuuneko.rpclient.libs.llm.LLMResponseException
import java.io.IOException

/**
 * Responses 流式状态机。
 * - 用输出位置跟踪正文与推理，终态快照只补齐缺失后缀。
 * - 仅响应级终态结束请求，文本结束和未知事件不会提前关闭流。
 * - 断流必须显式失败，已向上层发出的增量仍由业务流程保留。
 */
internal class OpenAIResponsesStreamDecoder {
    private val mContents = linkedMapOf<ResponsesContentKey, StringBuilder>()
    private val mCommentaryOutputs = mutableSetOf<Int>()
    var isFinished: Boolean = false
        private set

    /** 消费完整 SSE 事件，返回按接收顺序排列的通用输出片段。 */
    fun accept(event: SseEvent): List<LLMProviderStreamPart> {
        if (isFinished) return emptyList()
        if (event.data.isBlank()) return emptyList()
        val json =
            parseStreamJsonObject(event.data) ?: throw LLMResponseException("invalid_stream_event")
        val type = json.cleanString("type").ifBlank { event.event }
        // 每种文本分支使用独立内容位置，避免正文、拒绝和摘要互相覆盖。
        return when (type) {
            "response.output_item.added" -> {
                registerPhase(json)
                emptyList()
            }

            "response.output_text.delta" -> delta(json, "output_text", event.data)
            "response.refusal.delta" -> delta(json, "refusal", event.data)
            "response.reasoning_summary_text.delta" -> delta(json, "summary_text", event.data)
            "response.reasoning_text.delta" -> delta(json, "reasoning_text", event.data)
            "response.output_text.done" -> done(json, "output_text", event.data)
            "response.refusal.done" -> done(json, "refusal", event.data)
            "response.reasoning_summary_text.done" -> done(json, "summary_text", event.data)
            "response.reasoning_text.done" -> done(json, "reasoning_text", event.data)
            "response.output_item.done" -> itemDone(json, event.data)
            "response.completed", "response.incomplete" -> terminal(json, event.data)
            "response.failed" -> throw OpenAIResponsesResponseCodec.error(
                payload = json.objectOrNull("response") ?: json,
            )

            "error" -> throw OpenAIResponsesResponseCodec.error(json)
            else -> emptyList()
        }
    }

    /** HTTP 正常关闭也必须有响应终态，不能把服务器中途断流伪装成完成。 */
    fun requireFinished() {
        if (!isFinished) throw IOException("The response stream closed before a terminal event.")
    }

    /** 追加当前内容位置的增量，提前登记的 commentary 只作为进度丢弃。 */
    private fun delta(json: JsonObject, type: String, raw: String): List<LLMProviderStreamPart> {
        val key = key(json, type)
        if (key.outputIndex in mCommentaryOutputs) return emptyList()
        val text = json.responsesText("delta")
        mContents.getOrPut(key) { StringBuilder() }.append(text)
        return part(ResponsesContent(key, text), raw)
    }

    /** 内容结束只是快照确认，不代表整轮请求已经完成。 */
    private fun done(json: JsonObject, type: String, raw: String): List<LLMProviderStreamPart> {
        val key = key(json, type)
        if (key.outputIndex in mCommentaryOutputs) return emptyList()
        val text = json.responsesText(if (type == "refusal") "refusal" else "text")
        return snapshot(ResponsesContent(key, text), raw)
    }

    /** Item 快照补足正文或推理，也负责识别需要客户端执行的工具调用。 */
    private fun itemDone(json: JsonObject, raw: String): List<LLMProviderStreamPart> {
        registerPhase(json)
        val item = json.objectOrNull("item") ?: throw LLMResponseException("invalid_output_item")
        val index =
            json.intOrNull("output_index") ?: throw LLMResponseException("invalid_output_index")
        return OpenAIResponsesResponseCodec.itemContents(item, index).flatMap { snapshot(it, raw) }
    }

    /** 在增量到达前登记 assistant 阶段，进度消息不进入聊天正文或后续历史。 */
    private fun registerPhase(json: JsonObject) {
        val item = json.objectOrNull("item") ?: throw LLMResponseException("invalid_output_item")
        val index =
            json.intOrNull("output_index") ?: throw LLMResponseException("invalid_output_index")
        if (item.cleanString("type") != "message" || item.cleanString("phase") != "commentary") return
        if (mContents.any { (key, text) -> key.outputIndex == index && text.isNotEmpty() }) {
            throw LLMResponseException("inconsistent_stream_phase")
        }
        mCommentaryOutputs.add(index)
    }

    /** 终态带有完整输出与用量；缺失过增量时补齐，已有增量保持恰好输出一次。 */
    private fun terminal(json: JsonObject, raw: String): List<LLMProviderStreamPart> {
        val response =
            json.objectOrNull("response") ?: throw LLMResponseException("invalid_response")
        val finishReason = OpenAIResponsesResponseCodec.finishReason(response)
        val contents = OpenAIResponsesResponseCodec.contents(response)
        val keys = contents.mapTo(mutableSetOf()) { it.key }
        if (mContents.any { (key, text) -> key !in keys && text.isNotEmpty() }) {
            throw LLMResponseException("inconsistent_stream_content")
        }
        val parts = contents.flatMap { snapshot(it, raw) }
        // 推理摘要不能替代实际回复，空正文时继续携带长度截断信息。
        if (mContents.none { (key, text) ->
                key.type in setOf("output_text", "refusal") && text.isNotBlank()
            }) {
            throw LLMEmptyResponseException(finishReason == "max_output_tokens")
        }
        isFinished = true
        return parts + LLMProviderStreamPart.Finished(
            rawChunk = raw,
            finishReason = finishReason,
            model = response.cleanString("model").takeIf { it.isNotEmpty() },
            usage = OpenAIResponsesResponseCodec.usage(response),
        )
    }

    /** 快照只能扩展已收到的前缀；发生不一致时显式报错，避免静默生成重复正文。 */
    private fun snapshot(content: ResponsesContent, raw: String): List<LLMProviderStreamPart> {
        val previous = mContents.getOrPut(content.key) { StringBuilder() }
        if (!content.text.startsWith(previous)) throw LLMResponseException("inconsistent_stream_content")
        val suffix = content.text.substring(previous.length)
        previous.append(suffix)
        return part(content.copy(text = suffix), raw)
    }

    private fun key(json: JsonObject, type: String): ResponsesContentKey {
        val output =
            json.intOrNull("output_index") ?: throw LLMResponseException("invalid_output_index")
        val field = if (type == "summary_text") "summary_index" else "content_index"
        val content = json.intOrNull(field) ?: throw LLMResponseException("invalid_content_index")
        return ResponsesContentKey(outputIndex = output, contentIndex = content, type = type)
    }

    private fun part(content: ResponsesContent, raw: String): List<LLMProviderStreamPart> {
        if (content.text.isEmpty()) return emptyList()
        return listOf(
            element = if (content.isReasoning) {
                LLMProviderStreamPart.Reasoning(
                    content = content.text,
                    rawChunk = raw,
                    kind = content.reasoningKind
                )
            } else {
                LLMProviderStreamPart.Text(content.text, raw)
            },
        )
    }
}
