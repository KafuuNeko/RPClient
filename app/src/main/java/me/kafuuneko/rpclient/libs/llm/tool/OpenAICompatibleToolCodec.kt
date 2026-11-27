package me.kafuuneko.rpclient.libs.llm.tool

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import me.kafuuneko.rpclient.libs.llm.adapter.cleanString
import me.kafuuneko.rpclient.libs.llm.adapter.intOrNull
import me.kafuuneko.rpclient.libs.llm.adapter.objectOrNull
import me.kafuuneko.rpclient.libs.llm.adapter.parseOpenAIUsage
import me.kafuuneko.rpclient.libs.llm.model.LLMToolCall
import me.kafuuneko.rpclient.libs.llm.model.LLMToolChoice
import me.kafuuneko.rpclient.libs.llm.model.LLMToolGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMToolProtocolException
import me.kafuuneko.rpclient.libs.llm.model.LLMToolTurn
import me.kafuuneko.rpclient.utils.jsonArray
import me.kafuuneko.rpclient.utils.jsonObject

/** Chat Completions 原生函数轮次，保留 reasoning_content 供推理模型续接。 */
internal class OpenAICompatibleToolCodec : ToolProtocolCodec {
    /** 将冻结工具及已完成交换按协议原序编码，assistant 后紧随对应 tool 结果。 */
    override fun extend(payload: JsonObject, request: LLMToolGenerationRequest) {
        // 工具结果必须保持调用位置，不能改变已经发送的前缀。
        request.checkExchanges()
        val messages = payload.history("messages")
        request.exchanges.forEach { exchange ->
            if (exchange.instructionBefore.isNotEmpty()) messages.add(
                jsonObject(
                    "role" to "user",
                    "content" to exchange.instructionBefore
                )
            )
            exchange.turn.continuation.forEach { messages.add(it.deepCopy()) }
            exchange.turn.calls.zip(exchange.results).forEach { (call, result) ->
                messages.add(
                    jsonObject(
                        "role" to "tool",
                        "tool_call_id" to requireNotNull(call.remoteId),
                        "content" to result.toString()
                    )
                )
            }
        }
        if (request.tailInstruction.isNotEmpty()) messages.add(
            jsonObject(
                "role" to "user",
                "content" to request.tailInstruction
            )
        )
        payload.add(
            "tools",
            jsonArray(request.tools.map {
                jsonObject(
                    "type" to "function",
                    "function" to functionDefinition(it)
                )
            })
        )
        payload.add(
            "tool_choice", when (val choice = request.choice) {
                LLMToolChoice.Auto -> JsonPrimitive("auto")
                LLMToolChoice.None -> JsonPrimitive("none")
                is LLMToolChoice.Named -> jsonObject(
                    "type" to "function",
                    "function" to jsonObject("name" to choice.name)
                )
            }
        )
    }

    /** 完整消息作为续接快照，拒绝多候选和未知客户端工具类型。 */
    override fun decode(response: JsonObject, model: String): LLMToolTurn {
        // 先检查协议级错误、唯一候选与拒答字段。
        response.checkError()
        val choice = response.objects("choices").singleOrNull()
            ?: throw LLMToolProtocolException("invalid_choices")
        val message =
            choice.objectOrNull("message") ?: throw LLMToolProtocolException("missing_message")
        if (message.cleanString("refusal").isNotEmpty()) throw LLMToolProtocolException("refusal")
        // 仅接受 function 类型工具调用并校验必填函数名与远程调用 ID。
        val calls = message.objects("tool_calls").map { call ->
            if (call.required("type") != "function") throw LLMToolProtocolException("unsupported_call")
            val function =
                call.objectOrNull("function") ?: throw LLMToolProtocolException("missing_function")
            LLMToolCall(
                function.required("name"),
                function.callArguments("arguments"),
                call.required("id")
            )
        }
        return LLMToolTurn(
            message.cleanString("content"),
            calls,
            jsonArray(listOf(message)),
            choice.required("finish_reason"),
            response.cleanString("model").ifEmpty { model },
            parseOpenAIUsage(response.toString()),
            message.cleanString("reasoning_content").ifEmpty { message.cleanString("reasoning") })
    }

    /** 分片按 index 优先、远程 ID 次之关联，缺少两者且歧义时拒绝。 */
    override fun stream(): ToolStreamAccumulator = Stream(this)

    private class Stream(private val mCodec: OpenAICompatibleToolCodec) : ToolStreamAccumulator {
        private val mCalls = sortedMapOf<Int, JsonObject>()
        private val mMessage = jsonObject("role" to "assistant", "content" to "")
        private val mRoot = JsonObject()
        private var mReason = ""
        private var mTerminal = false

        /** 消费一帧协议事件，未完成字段只保存在当前缓冲。 */
        override fun accept(type: String, data: JsonObject) {
            data.checkError()
            data.get("model")?.let { mRoot.add("model", it) }
            data.get("usage")?.takeUnless { it.isJsonNull }?.let { mRoot.add("usage", it) }
            if (mTerminal) return
            val choices = data.objects("choices")
            if (choices.size > 1) throw LLMToolProtocolException("multiple_choices")
            val choice = choices.firstOrNull() ?: return
            val delta = choice.objectOrNull("delta") ?: JsonObject()
            // 只有明确的停止原因才结束调用聚合，[DONE] 或 EOF 不能补齐半截参数。
            delta.cleanString("refusal").takeIf { it.isNotEmpty() }
                ?.let { throw LLMToolProtocolException("refusal") }
            mMessage.append("content", delta.cleanString("content"))
            mMessage.append(
                "reasoning_content",
                delta.cleanString("reasoning_content").ifEmpty { delta.cleanString("reasoning") })
            delta.objects("tool_calls").forEach { mergeCall(it) }
            choice.cleanString("finish_reason").takeIf { it.isNotEmpty() }
                ?.let {
                    mReason = it
                    mTerminal = true
                }
        }

        private fun mergeCall(delta: JsonObject) {
            val id = delta.cleanString("id")
            val index = delta.intOrNull("index")
                ?: mCalls.entries.firstOrNull { id.isNotEmpty() && it.value.cleanString("id") == id }?.key
                ?: if (mCalls.isEmpty()) 0 else if (mCalls.size == 1 && id.isEmpty()) mCalls.firstKey() else if (id.isNotEmpty()) mCalls.lastKey() + 1 else throw LLMToolProtocolException(
                    "ambiguous_call_delta"
                )
            if (index !in 0..19) throw LLMToolProtocolException("invalid_call_index")
            val call = mCalls.getOrPut(index) {
                jsonObject(
                    "type" to "function",
                    "function" to jsonObject("name" to "", "arguments" to "")
                )
            }
            if (id.isNotEmpty()) {
                if (call.cleanString("id")
                        .isNotEmpty() && call.cleanString("id") != id
                ) throw LLMToolProtocolException("call_id_changed")
                call.addProperty("id", id)
            }
            val function = call.getAsJsonObject("function")
            delta.objectOrNull("function")?.let { value ->
                val name = value.cleanString("name")
                // 网关重复发完整名字和 ID 时不重复拼接，参数依旧只按增量处理。
                if (name.isNotEmpty() && function.cleanString("name") != name) function.append(
                    "name",
                    name
                )
                function.append("arguments", value.cleanString("arguments"))
                if (function.cleanString("arguments").length > 65_536) throw LLMToolProtocolException(
                    "arguments_too_large"
                )
            }
        }

        /** 校验终态及未闭合块后生成可执行轮次。 */
        override fun finish(model: String): LLMToolTurn {
            if (!mTerminal) throw LLMToolProtocolException("stream_interrupted")
            if (mCalls.isNotEmpty()) mMessage.add("tool_calls", jsonArray(mCalls.values))
            mRoot.add(
                "choices",
                jsonArray(listOf(jsonObject("message" to mMessage, "finish_reason" to mReason)))
            )
            return mCodec.decode(mRoot, model)
        }
    }
}
