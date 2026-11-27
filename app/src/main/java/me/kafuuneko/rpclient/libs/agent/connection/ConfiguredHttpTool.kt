package me.kafuuneko.rpclient.libs.agent.connection

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.tool.ToolErrorCode
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.base.AbstractExternalTool
import me.kafuuneko.rpclient.libs.agent.tool.base.ExternalRequestPlan
import me.kafuuneko.rpclient.libs.agent.tool.base.ToolAuthorizationRevokedException
import me.kafuuneko.rpclient.libs.agent.tool.base.ToolAvailability
import me.kafuuneko.rpclient.libs.agent.tool.base.ToolAvailabilityState
import me.kafuuneko.rpclient.libs.agent.tool.catalog.EffectiveToolConfig
import me.kafuuneko.rpclient.libs.room.entity.ToolConnection
import me.kafuuneko.rpclient.libs.room.entity.ToolDefinition

/** 配置驱动的 HTTP 工具，不为不同服务商增加子类。 */
class ConfiguredHttpTool(
    private val mTool: ToolDefinition,
    private val mConnection: ToolConnection?,
    private val mInvoker: HttpToolInvoker,
    private val mAvailability: ToolAvailability,
) : AbstractExternalTool(
    EffectiveToolConfig(ToolConfigCodec.definition(mTool, mConnection)),
    mAvailability
) {
    /** @return 请求计划；派发前再次复核授权，等待期间停用不能绕过检查。 */
    override suspend fun planRequest(arguments: JsonObject): ExternalRequestPlan {
        val plan = mInvoker.plan(mTool, mConnection, arguments)
        return plan.copy(execute = {
            when (mAvailability.state()) {
                ToolAvailabilityState.Revoked -> throw ToolAuthorizationRevokedException()
                ToolAvailabilityState.NeedsConfiguration -> ToolResult.RecoverableError(
                    ToolErrorCode.HTTP_NEEDS_CONFIGURATION
                )

                ToolAvailabilityState.Ready -> plan.execute()
            }
        })
    }
}
