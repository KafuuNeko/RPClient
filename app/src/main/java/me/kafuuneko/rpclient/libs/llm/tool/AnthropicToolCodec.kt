package me.kafuuneko.rpclient.libs.llm.tool

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.llm.adapter.cleanString
import me.kafuuneko.rpclient.libs.llm.adapter.intOrNull
import me.kafuuneko.rpclient.libs.llm.adapter.objectOrNull
import me.kafuuneko.rpclient.libs.llm.model.LLMToolCall
import me.kafuuneko.rpclient.libs.llm.model.LLMToolChoice
import me.kafuuneko.rpclient.libs.llm.model.LLMToolGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMToolProtocolException
import me.kafuuneko.rpclient.libs.llm.model.LLMToolTurn
import me.kafuuneko.rpclient.libs.llm.model.LLMUsage
import me.kafuuneko.rpclient.utils.jsonArray
import me.kafuuneko.rpclient.utils.jsonObject

/** Messages 保留思考签名，工具结果位于后继 user 内容数组首部。 */
internal class AnthropicToolCodec : ToolProtocolCodec {
    /** 将冻结工具和已完成交换按协议原序编码。 */
    override fun extend(payload: JsonObject, request: LLMToolGenerationRequest) {
        // 工具结果必须保持调用位置，不能改变已经发送的前缀。
        request.checkExchanges()
        val messages = payload.history("messages")
        request.exchanges.forEach { exchange ->
            appendInstruction(messages, exchange.instructionBefore)
            messages.add(jsonObject("role" to "assistant", "content" to exchange.turn.continuation))
            messages.add(
                jsonObject(
                    "role" to "user",
                    "content" to jsonArray(
                        exchange.turn.calls.zip(exchange.results).map { (call, result) ->
                            jsonObject(
                                "type" to "tool_result",
                                "tool_use_id" to requireNotNull(call.remoteId),
                                "content" to result.toString(),
                                "is_error" to (result.cleanString("kind") in setOf(
                                    "recoverable_error",
                                    "fatal_error"
                                ))
                            )
                        })
                )
            )
        }
        appendInstruction(messages, request.tailInstruction)
        payload.add(
            "tools",
            jsonArray(request.tools.map {
                jsonObject(
                    "name" to it.name,
                    "description" to it.description,
                    "input_schema" to wireSchema(it.parameters)
                )
            })
        )
        payload.add(
            "tool_choice", when (val choice = request.choice) {
                LLMToolChoice.Auto -> jsonObject("type" to "auto")
                LLMToolChoice.None -> jsonObject("type" to "none")
                is LLMToolChoice.Named -> jsonObject("type" to "tool", "name" to choice.name)
            }
        )
    }

    private fun appendInstruction(messages: JsonArray, text: String) {
        if (text.isEmpty()) return
        val last = messages.lastOrNull()?.asJsonObject
        if (last?.cleanString("role") == "user" && last.get("content").isJsonArray) last.getAsJsonArray(
            "content"
        ).add(jsonObject("type" to "text", "text" to text))
        else messages.add(jsonObject("role" to "user", "content" to text))
    }

    /** 解析完整响应并保留原生续接载荷，终态由公共校验器复核。 */
    override fun decode(response: JsonObject, model: String): LLMToolTurn {
        response.checkError()
        val blocks = response.objects("content")
        val calls = blocks.filter { it.cleanString("type") == "tool_use" }.map {
            LLMToolCall(it.required("name"), it.callArguments("input"), it.required("id"))
        }
        // 不能用可展示思考摘要代替模型要求原样回传的签名块。
        if (calls.isNotEmpty() && blocks.any {
                it.cleanString("type") == "thinking" && it.cleanString(
                    "signature"
                ).isEmpty()
            }) throw LLMToolProtocolException("missing_signature")
        val usage = response.objectOrNull("usage")
        return LLMToolTurn(
            blocks.filter { it.cleanString("type") == "text" }
                .joinToString("") { it.cleanString("text") },
            calls,
            jsonArray(blocks),
            response.required("stop_reason"),
            response.cleanString("model").ifEmpty { model },
            usage?.let {
                LLMUsage(
                    promptTokens = it.intOrNull("input_tokens"),
                    completionTokens = it.intOrNull("output_tokens"),
                    cachedPromptTokens = it.intOrNull("cache_read_input_tokens"),
                    cacheCreationPromptTokens = it.intOrNull("cache_creation_input_tokens")
                )
            },
            blocks.filter { it.cleanString("type") == "thinking" }
                .joinToString("") { it.cleanString("thinking") }
        )
    }

    /** 为本次响应创建隔离的增量聚合器。 */
    override fun stream(): ToolStreamAccumulator = Stream(this)

    private class Stream(private val codec: AnthropicToolCodec) : ToolStreamAccumulator {
        private val blocks = sortedMapOf<Int, JsonObject>()
        private val arguments = mutableMapOf<Int, StringBuilder>()
        private val open = mutableSetOf<Int>()
        private var response = JsonObject()
        private var terminal = false

        /** 消费一帧协议事件，未完成字段只保存在当前缓冲。 */
        override fun accept(type: String, data: JsonObject) {
            data.checkError()
            when (type) {
                "message_start" -> response = data.objectOrNull("message")?.deepCopy()
                    ?: throw LLMToolProtocolException("missing_message")

                "content_block_start" -> {
                    val index = index(data)
                    if (blocks.containsKey(index)) throw LLMToolProtocolException("duplicate_block")
                    val block = data.objectOrNull("content_block")?.deepCopy()
                        ?: throw LLMToolProtocolException("missing_block")
                    blocks[index] = block
                    open.add(index)
                    if (block.cleanString("type") == "tool_use") arguments[index] = StringBuilder()
                }

                "content_block_delta" -> delta(data)
                "content_block_stop" -> {
                    val index = index(data)
                    if (!open.remove(index)) throw LLMToolProtocolException("block_not_open")
                    arguments[index]?.takeIf { it.isNotEmpty() }
                        ?.let { blocks.getValue(index).add("input", parseObject(it.toString())) }
                }

                "message_delta" -> {
                    data.objectOrNull("delta")?.get("stop_reason")
                        ?.let { response.add("stop_reason", it) }
                    data.objectOrNull("usage")?.let { usage ->
                        val merged = response.objectOrNull("usage")
                            ?: JsonObject().also { response.add("usage", it) }
                        usage.entrySet().forEach { (key, value) -> merged.add(key, value) }
                    }
                }

                "message_stop" -> terminal = true
                "error" -> throw LLMToolProtocolException("provider_error")
            }
        }

        private fun index(data: JsonObject): Int = data.intOrNull("index")?.takeIf { it in 0..99 }
            ?: throw LLMToolProtocolException("invalid_block_index")

        private fun delta(data: JsonObject) {
            val index = index(data)
            if (index !in open) throw LLMToolProtocolException("block_not_open")
            val block = blocks.getValue(index)
            val delta =
                data.objectOrNull("delta") ?: throw LLMToolProtocolException("missing_delta")
            when (delta.cleanString("type")) {
                "input_json_delta" -> {
                    val buffer =
                        arguments[index] ?: throw LLMToolProtocolException("unexpected_arguments")
                    buffer.append(delta.cleanString("partial_json"))
                    if (buffer.length > 65_536) throw LLMToolProtocolException("arguments_too_large")
                }

                "text_delta" -> block.append("text", delta.cleanString("text"))
                "thinking_delta" -> block.append("thinking", delta.cleanString("thinking"))
                "signature_delta" -> block.append("signature", delta.cleanString("signature"))
            }
        }

        /** 校验终态及未闭合块后生成可执行轮次。 */
        override fun finish(model: String): LLMToolTurn {
            if (!terminal || open.isNotEmpty()) throw LLMToolProtocolException("stream_interrupted")
            response.add("content", jsonArray(blocks.values))
            return codec.decode(response, model)
        }
    }
}
