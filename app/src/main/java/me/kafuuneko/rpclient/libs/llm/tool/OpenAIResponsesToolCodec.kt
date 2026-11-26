package me.kafuuneko.rpclient.libs.llm.tool

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import me.kafuuneko.rpclient.libs.llm.adapter.OpenAIResponsesResponseCodec
import me.kafuuneko.rpclient.libs.llm.adapter.arrayOrNull
import me.kafuuneko.rpclient.libs.llm.adapter.cleanString
import me.kafuuneko.rpclient.libs.llm.adapter.objectOrNull
import me.kafuuneko.rpclient.libs.llm.model.LLMToolCall
import me.kafuuneko.rpclient.libs.llm.model.LLMToolChoice
import me.kafuuneko.rpclient.libs.llm.model.LLMToolGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMToolProtocolException
import me.kafuuneko.rpclient.libs.llm.model.LLMToolTurn
import me.kafuuneko.rpclient.utils.jsonArray
import me.kafuuneko.rpclient.utils.jsonObject

/** Responses 使用完整本地历史，reasoning 与 encrypted_content 原样续接。 */
internal class OpenAIResponsesToolCodec : ToolProtocolCodec {
    /** 将冻结工具和已完成交换按协议原序编码。 */
    override fun extend(payload: JsonObject, request: LLMToolGenerationRequest) {
        // 工具结果必须保持调用位置，不能改变已经发送的前缀。
        request.checkExchanges()
        val input = payload.history("input")
        request.exchanges.forEach { exchange ->
            if (exchange.instructionBefore.isNotEmpty()) input.add(
                jsonObject(
                    "role" to "user",
                    "content" to exchange.instructionBefore
                )
            )
            exchange.turn.continuation.forEach { input.add(it.deepCopy()) }
            exchange.turn.calls.zip(exchange.results).forEach { (call, result) ->
                input.add(
                    jsonObject(
                        "type" to "function_call_output",
                        "call_id" to requireNotNull(call.remoteId),
                        "output" to result.toString()
                    )
                )
            }
        }
        if (request.tailInstruction.isNotEmpty()) input.add(
            jsonObject(
                "role" to "user",
                "content" to request.tailInstruction
            )
        )
        payload.add(
            "tools",
            jsonArray(request.tools.map {
                functionDefinition(it).apply {
                    addProperty(
                        "type",
                        "function"
                    )
                }
            })
        )
        payload.add(
            "tool_choice", when (val choice = request.choice) {
                LLMToolChoice.Auto -> JsonPrimitive("auto")
                LLMToolChoice.None -> JsonPrimitive("none")
                is LLMToolChoice.Named -> jsonObject("type" to "function", "name" to choice.name)
            }
        )
        // store=false 时服务端不会保留推理上下文，必须显式要求可续接密文。
        val include =
            payload.arrayOrNull("include") ?: JsonArray().also { payload.add("include", it) }
        if (include.none { it.asString == "reasoning.encrypted_content" }) include.add("reasoning.encrypted_content")
    }

    /** 解析完整响应并保留原生续接载荷，终态由公共校验器复核。 */
    override fun decode(response: JsonObject, model: String): LLMToolTurn {
        response.checkError()
        val output = response.objects("output")
        val calls = output.filter { it.cleanString("type") == "function_call" }.map {
            if (it.cleanString("status") !in setOf("", "completed")) throw LLMToolProtocolException(
                "unfinished_call"
            )
            LLMToolCall(it.required("name"), it.callArguments("arguments"), it.required("call_id"))
        }
        // 未实现的客户端 Item 不能忽略后继续提交剧情。
        val supportedCalls =
            setOf("function_call", "web_search_call", "file_search_call", "code_interpreter_call")
        if (output.any {
                it.cleanString("type")
                    .endsWith("_call") && it.cleanString("type") !in supportedCalls
            }) throw LLMToolProtocolException("unsupported_call")
        val text =
            output.filter { it.cleanString("type") == "message" }.flatMap { it.objects("content") }
                .joinToString("") {
                    if (it.cleanString("type") == "refusal") throw LLMToolProtocolException("refusal")
                    it.cleanString("text")
                }
        val reasoning = output.filter { it.cleanString("type") == "reasoning" }
            .flatMap { it.objects("summary") }.joinToString("") { it.cleanString("text") }
        return LLMToolTurn(
            text,
            calls,
            jsonArray(output),
            response.required("status"),
            response.cleanString("model").ifEmpty { model },
            OpenAIResponsesResponseCodec.usage(response),
            reasoning
        )
    }

    /** 终态完整快照含全部原生 Item；中途增量不能自行拼造缺失签名。 */
    override fun stream(): ToolStreamAccumulator = object : ToolStreamAccumulator {
        private var result: JsonObject? = null
        override fun accept(type: String, data: JsonObject) {
            data.checkError()
            if (type in setOf(
                    "response.failed",
                    "error"
                )
            ) throw LLMToolProtocolException("provider_error")
            if (type in setOf("response.completed", "response.incomplete")) result =
                data.objectOrNull("response") ?: throw LLMToolProtocolException("missing_response")
        }

        /** 校验终态及未闭合块后生成可执行轮次。 */
        override fun finish(model: String): LLMToolTurn =
            decode(result ?: throw LLMToolProtocolException("stream_interrupted"), model)
    }
}
