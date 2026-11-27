package me.kafuuneko.rpclient.libs.agent

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.utils.jsonArray
import me.kafuuneko.rpclient.utils.jsonObject

/** 五协议的单调用和最终正文合成响应，验证真实客户端与 HTTP 工具闭环。 */
internal fun toolModelReply(
    protocol: LLMProviderProtocol,
    name: String,
    arguments: JsonObject? = null
): String {
    val function = arguments != null
    // 每种协议都保留原生调用结构，不能用普通正文中的工具标记代替。
    return when (protocol) {
        LLMProviderProtocol.OpenAICompatible -> jsonObject(
            "choices" to jsonArray(
                listOf(
                    jsonObject(
                        "finish_reason" to if (function) "tool_calls" else "stop",
                        "message" to jsonObject(
                            "role" to "assistant",
                            "content" to if (function) "" else "done"
                        ).apply {
                            if (function) add(
                                "tool_calls",
                                jsonArray(
                                    listOf(
                                        jsonObject(
                                            "id" to "http-call",
                                            "type" to "function",
                                            "function" to jsonObject(
                                                "name" to name,
                                                "arguments" to arguments.toString()
                                            )
                                        )
                                    )
                                )
                            )
                        },
                    )
                )
            )
        )

        LLMProviderProtocol.OpenAIResponses -> jsonObject(
            "status" to "completed", "output" to jsonArray(
                listOf(
                    if (function) jsonObject(
                        "type" to "function_call",
                        "id" to "f",
                        "call_id" to "http-call",
                        "status" to "completed",
                        "name" to name,
                        "arguments" to arguments.toString()
                    )
                    else jsonObject(
                        "type" to "message",
                        "role" to "assistant",
                        "content" to jsonArray(
                            listOf(
                                jsonObject(
                                    "type" to "output_text",
                                    "text" to "done"
                                )
                            )
                        )
                    ),
                )
            )
        )

        LLMProviderProtocol.AnthropicMessages -> jsonObject(
            "stop_reason" to if (function) "tool_use" else "end_turn", "content" to jsonArray(
                listOf(
                    if (function) jsonObject(
                        "type" to "tool_use",
                        "id" to "http-call",
                        "name" to name,
                        "input" to arguments
                    )
                    else jsonObject("type" to "text", "text" to "done"),
                )
            )
        )

        LLMProviderProtocol.Gemini -> jsonObject(
            "candidates" to jsonArray(
                listOf(
                    jsonObject(
                        "finishReason" to "STOP", "content" to jsonObject(
                            "role" to "model", "parts" to jsonArray(
                                listOf(
                                    if (function) jsonObject(
                                        "functionCall" to jsonObject(
                                            "name" to name,
                                            "args" to arguments
                                        )
                                    ) else jsonObject("text" to "done"),
                                )
                            )
                        )
                    )
                )
            )
        )

        LLMProviderProtocol.GeminiInteractions -> jsonObject(
            "status" to if (function) "requires_action" else "completed", "steps" to jsonArray(
                listOf(
                    if (function) jsonObject(
                        "type" to "function_call",
                        "id" to "http-call",
                        "name" to name,
                        "arguments" to arguments
                    )
                    else jsonObject(
                        "type" to "model_output",
                        "content" to jsonArray(
                            listOf(
                                jsonObject(
                                    "type" to "text",
                                    "text" to "done"
                                )
                            )
                        )
                    ),
                )
            )
        )
    }.toString()
}
