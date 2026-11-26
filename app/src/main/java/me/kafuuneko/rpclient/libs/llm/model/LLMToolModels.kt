package me.kafuuneko.rpclient.libs.llm.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.model.AgentJson
import me.kafuuneko.rpclient.libs.agent.tool.AgentToolDefinition

/** 工具选择独立于普通文本生成，收尾时强制禁用客户端及服务端工具。 */
sealed interface LLMToolChoice {
    data object Auto : LLMToolChoice
    data object None : LLMToolChoice
    data class Named(val name: String) : LLMToolChoice
}

/** 远程 ID 只用于协议关联，执行幂等键由运行层另行分配。 */
data class LLMToolCall(val name: String, val arguments: String, val remoteId: String? = null)

/** 有序原生轮次快照只写入本机检查点，不能放入聊天正文或 UI 状态。 */
data class LLMToolTurn(
    val content: String,
    val calls: List<LLMToolCall>,
    val continuation: JsonArray,
    val finishReason: String,
    val model: String,
    val usage: LLMUsage? = null,
    val reasoning: String = "",
)

/** 每条结果按调用出现顺序回传，避免无 ID 协议及同名多调用发生串配。 */
data class LLMToolExchange(
    val turn: LLMToolTurn,
    val results: List<JsonObject>,
    val instructionBefore: String = ""
)

/** 基础请求沿用图文消息；工具交换不引入新的持久化消息角色。 */
data class LLMToolGenerationRequest(
    val base: LLMGenerationRequest,
    val tools: List<AgentToolDefinition>,
    val exchanges: List<LLMToolExchange> = emptyList(),
    val choice: LLMToolChoice = LLMToolChoice.Auto,
    val systemPromptApplied: Boolean = false,
    val runId: String? = null,
    val tailInstruction: String = "",
)

/** 高频预览不承诺最终正文；只有 Finished 内的完整轮次可以交给执行器。 */
sealed interface LLMToolStreamEvent {
    data object Connected : LLMToolStreamEvent
    data class Preview(val text: String, val reasoning: Boolean = false) : LLMToolStreamEvent
    data class Finished(val turn: LLMToolTurn) : LLMToolStreamEvent
}

/** 工具轮次校验不会放松普通文本响应的非空约束。 */
class LLMToolProtocolException(val code: String) :
    IllegalStateException("Invalid tool protocol: $code")

/** 缺失实现必须明确报错，不能悄悄退化为文本调用。 */
class UnsupportedToolCalling(val models: List<String> = emptyList()) :
    IllegalStateException("Native tool calling is unavailable")

/** 只在协议正常完成且参数完整时接受调用；本地 Schema 与授权由执行器继续校验。 */
fun LLMToolTurn.requireValidToolTurn(): LLMToolTurn {
    if (finishReason.lowercase() !in setOf(
            "stop",
            "tool_calls",
            "end_turn",
            "tool_use",
            "completed",
            "requires_action"
        )
    ) {
        throw LLMToolProtocolException("incomplete_or_rejected")
    }
    if (calls.size > 20 || (calls.isEmpty() && content.isBlank())) throw LLMToolProtocolException("empty_or_excessive_calls")
    val ids = calls.mapNotNull { it.remoteId }
    if (ids.distinct().size != ids.size) throw LLMToolProtocolException("duplicate_call_id")
    calls.forEach { call ->
        if (!call.name.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_-]{0,63}$"))) throw LLMToolProtocolException(
            "invalid_name"
        )
        try {
            require(AgentJson.parse(call.arguments, 65_536, 16).isJsonObject)
        } catch (_: IllegalArgumentException) {
            throw LLMToolProtocolException("invalid_arguments")
        }
    }
    return this
}
