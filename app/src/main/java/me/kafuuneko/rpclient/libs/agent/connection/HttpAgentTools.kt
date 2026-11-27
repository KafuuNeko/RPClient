package me.kafuuneko.rpclient.libs.agent.connection

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.tool.AgentTool
import me.kafuuneko.rpclient.libs.agent.tool.ToolErrorCode
import me.kafuuneko.rpclient.libs.agent.tool.ToolInputParser
import me.kafuuneko.rpclient.libs.agent.tool.ToolPreparationException
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.base.ToolAvailabilityState
import me.kafuuneko.rpclient.libs.room.entity.ToolConnection
import me.kafuuneko.rpclient.libs.room.entity.ToolDefinition
import me.kafuuneko.rpclient.libs.room.repository.ToolConfigRepository

/** HTTP 工具工厂与手工测试入口，不再装配内置工具或解释内置覆写。 */
class HttpAgentTools(
    private val mConfigurations: ToolConfigRepository,
    private val mInvoker: HttpToolInvoker,
    private val mStatus: ToolConfigurationStatus,
) {
    /** @return 冻结外部工具集合，配置目录负责与本地工具合成及冲突检查。 */
    fun create(tools: List<ToolDefinition>, connections: List<ToolConnection>): List<AgentTool> {
        val indexed = connections.associateBy { it.id }
        return tools.filter { it.enabled }.map { tool ->
            val connection = tool.connectionId?.let(indexed::get)
            val availability = mStatus.availability(tool, connection)
            when (tool.kind) {
                HttpToolKind.ReadUrl -> ReadUrlTool(tool, mInvoker, availability)
                HttpToolKind.Http -> ConfiguredHttpTool(tool, connection, mInvoker, availability)
                else -> throw ToolConfigValidationException(
                    ToolErrorCode.INVALID_TOOL,
                    "Unknown HTTP tool kind"
                )
            }
        }
    }

    /** @return 用户明确触发的有界测试结果，不创建或伪造聊天运行身份。 */
    suspend fun test(id: String, arguments: JsonObject): ToolResult {
        val tool = mConfigurations.tool(id)
            ?: throw ToolConfigValidationException(ToolErrorCode.INVALID_TOOL, "Tool is missing")
        val connection = tool.connectionId?.let { mConfigurations.connection(it) }
        // 与执行器共用原始参数解析，测试也不能绕过 Schema 或实时撤权。
        val parsed = try {
            ToolInputParser.parse(
                arguments.toString(),
                ToolConfigCodec.definition(tool, connection).parameters
            )
        } catch (_: ToolPreparationException) {
            throw ToolConfigValidationException(
                ToolErrorCode.INVALID_TEST_ARGUMENTS,
                "Invalid test arguments"
            )
        }
        return when (mStatus.availability(tool, connection).state()) {
            ToolAvailabilityState.Revoked -> ToolResult.FatalError(ToolErrorCode.TOOL_AUTHORIZATION_REVOKED)
            ToolAvailabilityState.NeedsConfiguration -> ToolResult.RecoverableError(ToolErrorCode.HTTP_NEEDS_CONFIGURATION)
            ToolAvailabilityState.Ready -> try {
                mInvoker.plan(tool, connection, parsed).execute()
            } catch (error: ToolPreparationException) {
                ToolResult.RecoverableError(error.code)
            }
        }
    }
}
