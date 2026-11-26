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

/** GenerateContent 保存 Part 的位置和 thoughtSignature，无 ID 调用按出现次序回传。 */
internal class GeminiToolCodec : ToolProtocolCodec {
    /** 将冻结工具和已完成交换按协议原序编码。 */
    override fun extend(payload: JsonObject, request: LLMToolGenerationRequest) {
        // 工具结果必须保持调用位置，不能改变已经发送的前缀。
        request.checkExchanges()
        val contents = payload.history("contents")
        request.exchanges.forEach { exchange ->
            if (exchange.instructionBefore.isNotEmpty()) contents.add(
                jsonObject(
                    "role" to "user",
                    "parts" to jsonArray(listOf(jsonObject("text" to exchange.instructionBefore)))
                )
            )
            contents.add(jsonObject("role" to "model", "parts" to exchange.turn.continuation))
            contents.add(
                jsonObject(
                    "role" to "user",
                    "parts" to jsonArray(
                        exchange.turn.calls.zip(exchange.results).map { (call, result) ->
                            jsonObject(
                                "functionResponse" to jsonObject(
                                    "name" to call.name,
                                    "id" to call.remoteId,
                                    "response" to result
                                )
                            )
                        })
                )
            )
        }
        if (request.tailInstruction.isNotEmpty()) contents.add(
            jsonObject(
                "role" to "user",
                "parts" to jsonArray(listOf(jsonObject("text" to request.tailInstruction)))
            )
        )
        payload.add(
            "tools",
            jsonArray(listOf(jsonObject("functionDeclarations" to jsonArray(request.tools.map {
                jsonObject(
                    "name" to it.name,
                    "description" to it.description,
                    "parametersJsonSchema" to wireSchema(it.parameters)
                )
            }))))
        )
        val config = when (val choice = request.choice) {
            LLMToolChoice.Auto -> jsonObject("mode" to "AUTO")
            LLMToolChoice.None -> jsonObject("mode" to "NONE")
            is LLMToolChoice.Named -> jsonObject(
                "mode" to "ANY",
                "allowedFunctionNames" to JsonArray().apply { add(choice.name) })
        }
        payload.add("toolConfig", jsonObject("functionCallingConfig" to config))
    }

    /** 解析完整响应并保留原生续接载荷，终态由公共校验器复核。 */
    override fun decode(response: JsonObject, model: String): LLMToolTurn {
        // 续接保留原生块，不能从可展示文本反向重建签名或函数调用。
        response.checkError()
        val candidate = response.objects("candidates").singleOrNull()
            ?: throw LLMToolProtocolException("invalid_candidates")
        val content =
            candidate.objectOrNull("content") ?: throw LLMToolProtocolException("missing_content")
        val parts = content.objects("parts")
        val calls = parts.mapNotNull { part ->
            part.objectOrNull("functionCall")?.let {
                if (it.has("partialArgs") || it.get("willContinue")?.asBoolean == true) throw LLMToolProtocolException(
                    "unfinished_call"
                )
                LLMToolCall(
                    it.required("name"),
                    it.callArguments("args"),
                    it.cleanString("id").ifEmpty { null })
            }
        }
        val usage = response.objectOrNull("usageMetadata")
        val reason = candidate.required("finishReason")
        return LLMToolTurn(
            parts.filter { it.get("thought")?.asBoolean != true }
                .joinToString("") { it.cleanString("text") },
            calls,
            jsonArray(parts),
            if (reason == "STOP") "stop" else reason,
            response.cleanString("modelVersion").ifEmpty { model },
            usage?.let {
                LLMUsage(
                    it.intOrNull("promptTokenCount"),
                    it.intOrNull("candidatesTokenCount")
                        ?.plus(it.intOrNull("thoughtsTokenCount") ?: 0),
                    it.intOrNull("totalTokenCount"),
                    it.intOrNull("cachedContentTokenCount"),
                    it.intOrNull("thoughtsTokenCount")
                )
            },
            parts.filter { it.get("thought")?.asBoolean == true }
                .joinToString("") { it.cleanString("text") }
        )
    }

    /** 未启用参数增量模式，每个完整函数 Part 独立保留，文本可分片。 */
    override fun stream(): ToolStreamAccumulator = object : ToolStreamAccumulator {
        private val parts = mutableListOf<JsonObject>()
        private val root = JsonObject()
        private var reason = ""

        /** 消费一帧协议事件，未完成字段只保存在当前缓冲。 */
        override fun accept(type: String, data: JsonObject) {
            data.checkError()
            data.get("usageMetadata")?.let { root.add("usageMetadata", it) }
            data.get("modelVersion")?.let { root.add("modelVersion", it) }
            val candidates = data.objects("candidates")
            if (candidates.size > 1) throw LLMToolProtocolException("multiple_candidates")
            val candidate = candidates.firstOrNull() ?: return
            if (reason.isNotEmpty()) throw LLMToolProtocolException("delta_after_finish")
            candidate.objectOrNull("content")?.objects("parts")
                ?.forEach { parts.add(it.deepCopy()) }
            candidate.cleanString("finishReason").takeIf { it.isNotEmpty() }?.let { reason = it }
        }

        /** 校验终态及未闭合块后生成可执行轮次。 */
        override fun finish(model: String): LLMToolTurn {
            if (reason.isEmpty()) throw LLMToolProtocolException("stream_interrupted")
            root.add(
                "candidates",
                jsonArray(
                    listOf(
                        jsonObject(
                            "content" to jsonObject(
                                "role" to "model",
                                "parts" to jsonArray(parts)
                            ), "finishReason" to reason
                        )
                    )
                )
            )
            return decode(root, model)
        }
    }
}
