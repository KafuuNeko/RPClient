package me.kafuuneko.rpclient.libs.agent.tool

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.model.AgentExecutionToken

/** 工具副作用决定执行、确认与重试策略，随机读取不能当作确定性只读重算。 */
enum class ToolEffect { ReadOnly, NonDeterministicRead, LocalWrite, ExternalWrite }

/** 面向模型的稳定工具契约，与 Provider 协议无关。 */
data class AgentToolDefinition(
    val toolId: String,
    val name: String,
    val contractVersion: Int,
    val description: String,
    val parameters: ToolSchema,
    val effect: ToolEffect,
    val parallelSafe: Boolean = false,
    val writesMemory: Boolean = false,
)

/** 由客户端注入身份，不允许模型参数覆盖会话、人设及角色归属。 */
data class ToolExecutionContext(
    val token: AgentExecutionToken,
    val stepId: Long,
    val invocationId: String,
    val characterId: Long,
    val personaId: Long,
)

/** 写入结果必须区分待正文核验的提案与已持久化事实。 */
enum class ToolWriteDisposition { Proposed, Committed }

/** 确认与普通提问分别处理，不能把 ask_user 的回答当作外部写入授权。 */
enum class ToolUserRequestKind { Input, ExternalWriteConfirmation, LocalWriteConfirmation }

/**
 * 工具执行结果，只进入工具交换链及本机检查点。
 * - 分页由处理器生成完整 JSON，执行器超限时拒绝结果，不硬截正文。
 * - 错误码属于内部诊断，上层负责将终止原因映射为本地化资源。
 */
sealed interface ToolResult {
    data class Success(
        val data: JsonObject,
        val writeDisposition: ToolWriteDisposition? = null,
        val truncated: Boolean = false,
        val nextCursor: String? = null,
    ) : ToolResult

    data class RecoverableError(val code: String) : ToolResult
    data class FatalError(val code: String) : ToolResult
    data class AwaitingUser(
        val question: String,
        val options: List<String> = emptyList(),
        val kind: ToolUserRequestKind = ToolUserRequestKind.Input,
        val details: JsonObject = JsonObject(),
    ) : ToolResult
}

/**
 * 准备阶段与副作用阶段分开，支持结果先落检查点后短事务提交。
 * - 本地写入的 commit 只能访问本地事务数据，禁止网络、文件 IO 或长计算。
 * - 读取和外部请求在事务外执行，随机结果与外部写入未决时禁止自动重试。
 */
sealed interface ToolAction {
    class Read(val execute: suspend () -> ToolResult) : ToolAction
    class LocalWrite(
        val result: ToolResult.Success,
        val confirmation: ToolResult.AwaitingUser? = null,
        val commit: suspend () -> Unit
    ) : ToolAction

    class ExternalWrite(
        val destinationHost: String,
        val parameterSummary: JsonObject,
        val execute: suspend () -> ToolResult
    ) : ToolAction

    data class AwaitingUser(val result: ToolResult.AwaitingUser) : ToolAction
}

/** 参数或领域版本变化属于可恢复错误，不应把运行标记为未知副作用。 */
class ToolPreparationException(val code: String) :
    IllegalArgumentException("Tool preparation failed: $code")

/**
 * 内置与 HTTP 工具的真实多实现边界。
 * - prepare 仅校验和准备执行计划，不产生副作用或掷骰结果。
 * - 后续新增工具只注册定义和处理器，不在聊天页及协议客户端增加名称分支。
 */
interface AgentTool {
    val definition: AgentToolDefinition

    /** 根据已校验参数准备动作；写入动作在执行器再次授权后执行。 */
    suspend fun prepare(context: ToolExecutionContext, arguments: JsonObject): ToolAction
}
