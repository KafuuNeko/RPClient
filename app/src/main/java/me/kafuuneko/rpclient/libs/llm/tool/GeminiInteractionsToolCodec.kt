package me.kafuuneko.rpclient.libs.llm.tool

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import me.kafuuneko.rpclient.libs.llm.adapter.GeminiInteractionsResponseCodec
import me.kafuuneko.rpclient.libs.llm.adapter.cleanString
import me.kafuuneko.rpclient.libs.llm.adapter.intOrNull
import me.kafuuneko.rpclient.libs.llm.adapter.objectOrNull
import me.kafuuneko.rpclient.libs.llm.model.LLMToolCall
import me.kafuuneko.rpclient.libs.llm.model.LLMToolChoice
import me.kafuuneko.rpclient.libs.llm.model.LLMToolGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMToolProtocolException
import me.kafuuneko.rpclient.libs.llm.model.LLMToolTurn
import me.kafuuneko.rpclient.utils.jsonArray
import me.kafuuneko.rpclient.utils.jsonObject

/** Interactions 无存储工具链：完整有序步骤与签名保存在本机。 */
internal class GeminiInteractionsToolCodec : ToolProtocolCodec {
    /** 将冻结工具和已完成交换按协议原序编码。 */
    override fun extend(payload: JsonObject, request: LLMToolGenerationRequest) {
        // 工具结果必须保持调用位置，不能改变已经发送的前缀。
        request.checkExchanges()
        val input = payload.history("input")
        request.exchanges.forEach { exchange ->
            if (exchange.instructionBefore.isNotEmpty()) input.add(
                jsonObject(
                    "type" to "user_input",
                    "content" to jsonArray(
                        listOf(
                            jsonObject(
                                "type" to "text",
                                "text" to exchange.instructionBefore
                            )
                        )
                    )
                )
            )
            exchange.turn.continuation.forEach { input.add(it.deepCopy()) }
            exchange.turn.calls.zip(exchange.results).forEach { (call, result) ->
                input.add(
                    jsonObject(
                        "type" to "function_result",
                        "name" to call.name,
                        "call_id" to requireNotNull(call.remoteId),
                        "result" to jsonArray(
                            listOf(
                                jsonObject(
                                    "type" to "text",
                                    "text" to result.toString()
                                )
                            )
                        )
                    )
                )
            }
        }
        if (request.tailInstruction.isNotEmpty()) input.add(
            jsonObject(
                "type" to "user_input",
                "content" to jsonArray(
                    listOf(
                        jsonObject(
                            "type" to "text",
                            "text" to request.tailInstruction
                        )
                    )
                )
            )
        )
        payload.add(
            "tools",
            jsonArray(request.tools.map {
                jsonObject(
                    "type" to "function",
                    "name" to it.name,
                    "description" to it.description,
                    "parameters" to wireSchema(it.parameters)
                )
            })
        )
        val config = payload.objectOrNull("generation_config")
            ?: JsonObject().also { payload.add("generation_config", it) }
        config.add(
            "tool_choice", when (val choice = request.choice) {
                LLMToolChoice.Auto -> JsonPrimitive("auto")
                LLMToolChoice.None -> JsonPrimitive("none")
                is LLMToolChoice.Named -> jsonObject(
                    "allowed_tools" to jsonObject(
                        "mode" to "any",
                        "tools" to JsonArray().apply { add(choice.name) })
                )
            }
        )
    }

    /** 解析完整响应并保留原生续接载荷，终态由公共校验器复核。 */
    override fun decode(response: JsonObject, model: String): LLMToolTurn {
        // 续接保留原生块，不能从可展示文本反向重建签名或函数调用。
        response.checkError()
        val steps = response.objects("steps")
        val calls = steps.filter { it.cleanString("type") == "function_call" }.map {
            LLMToolCall(it.required("name"), it.callArguments("arguments"), it.required("id"))
        }
        val status = response.required("status")
        if ((calls.isNotEmpty() && status != "requires_action") || (calls.isEmpty() && status != "completed")) throw LLMToolProtocolException(
            "invalid_interaction_status"
        )
        return LLMToolTurn(
            text(steps, "model_output", "content"),
            calls,
            jsonArray(steps),
            status,
            response.cleanString("model").ifEmpty { model },
            GeminiInteractionsResponseCodec.usage(response),
            text(steps, "thought", "summary")
        )
    }

    private fun text(steps: List<JsonObject>, type: String, field: String) =
        steps.filter { it.cleanString("type") == type }
            .flatMap { it.objects(field) }.filter { it.cleanString("type") == "text" }
            .joinToString("") { it.cleanString("text") }

    /** 为本次响应创建隔离的增量聚合器。 */
    override fun stream(): ToolStreamAccumulator = Stream(this)

    private class Stream(private val codec: GeminiInteractionsToolCodec) : ToolStreamAccumulator {
        private val steps = sortedMapOf<Int, JsonObject>()
        private val open = mutableSetOf<Int>()
        private val arguments = mutableMapOf<Int, StringBuilder>()
        private var terminal: JsonObject? = null

        /** 消费一帧协议事件，未完成字段只保存在当前缓冲。 */
        override fun accept(type: String, data: JsonObject) {
            data.checkError()
            when (type) {
                "step.start" -> {
                    val index = index(data)
                    if (steps.containsKey(index)) throw LLMToolProtocolException("duplicate_step")
                    val step = data.objectOrNull("step")?.deepCopy()
                        ?: throw LLMToolProtocolException("missing_step")
                    steps[index] = step
                    open.add(index)
                    if (step.cleanString("type") == "function_call") arguments[index] =
                        StringBuilder()
                }

                "step.delta" -> delta(data)
                "step.stop" -> {
                    val index = index(data)
                    if (!open.remove(index)) throw LLMToolProtocolException("step_not_open")
                    data.objectOrNull("step")?.let { steps[index] = it.deepCopy() }
                    arguments[index]?.takeIf { it.isNotEmpty() }
                        ?.let { steps.getValue(index).add("arguments", parseObject(it.toString())) }
                }

                "interaction.completed", "interaction.requires_action" -> terminal =
                    data.objectOrNull("interaction")?.deepCopy()
                        ?: throw LLMToolProtocolException("missing_interaction")

                "interaction.status_update" -> if (data.cleanString("status") in setOf(
                        "requires_action",
                        "completed"
                    )
                ) terminal = data.deepCopy()

                "error", "interaction.failed", "interaction.cancelled" -> throw LLMToolProtocolException(
                    "provider_error"
                )
            }
        }

        private fun index(data: JsonObject) = data.intOrNull("index")?.takeIf { it in 0..99 }
            ?: throw LLMToolProtocolException("invalid_step_index")

        private fun delta(data: JsonObject) {
            val index = index(data)
            if (index !in open) throw LLMToolProtocolException("step_not_open")
            val step = steps.getValue(index)
            val delta =
                data.objectOrNull("delta") ?: throw LLMToolProtocolException("missing_delta")
            // 签名属于步骤自身，不能合并到别的步骤或展示文本中。
            delta.get("signature")?.let { step.add("signature", it.deepCopy()) }
            when (delta.cleanString("type")) {
                "function_call", "function_call_arguments", "arguments_delta" -> {
                    val buffer =
                        arguments[index] ?: throw LLMToolProtocolException("unexpected_arguments")
                    if (delta.get("arguments")?.isJsonObject == true) step.add(
                        "arguments",
                        delta.getAsJsonObject("arguments").deepCopy()
                    )
                    else buffer.append(
                        delta.cleanString("arguments_delta")
                            .ifEmpty { delta.cleanString("arguments") })
                    if (buffer.length > 65_536) throw LLMToolProtocolException("arguments_too_large")
                    delta.cleanString("name").takeIf { it.isNotEmpty() }
                        ?.let { step.addProperty("name", it) }
                    delta.cleanString("id").takeIf { it.isNotEmpty() }
                        ?.let { step.addProperty("id", it) }
                }

                "text" -> appendText(step, "content", delta.cleanString("text"))
                "thought_summary" -> delta.objectOrNull("content")
                    ?.let { appendText(step, "summary", it.cleanString("text")) }

                "thought" -> delta.get("signature")?.let { step.add("signature", it.deepCopy()) }
            }
        }

        private fun appendText(step: JsonObject, field: String, text: String) {
            val content = step.getAsJsonArray(field) ?: JsonArray().also { step.add(field, it) }
            content.add(jsonObject("type" to "text", "text" to text))
        }

        /** 校验终态及未闭合块后生成可执行轮次。 */
        override fun finish(model: String): LLMToolTurn {
            val response = terminal ?: throw LLMToolProtocolException("stream_interrupted")
            if (open.isNotEmpty()) throw LLMToolProtocolException("unfinished_step")
            if (!response.has("steps")) response.add("steps", jsonArray(steps.values))
            return codec.decode(response, model)
        }
    }
}
