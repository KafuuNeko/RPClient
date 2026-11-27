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
import me.kafuuneko.rpclient.libs.room.entity.ToolDefinition

/** 公网无认证读取工具，身份和网络权限与自定义 HTTP 连接隔离。 */
class ReadUrlTool(
    private val mTool: ToolDefinition,
    private val mInvoker: HttpToolInvoker,
    private val mAvailability: ToolAvailability,
) : AbstractExternalTool(
    EffectiveToolConfig(ToolConfigCodec.definition(mTool, null)),
    mAvailability
) {
    /** @return 请求计划；派发前再次复核授权，等待期间停用不能绕过检查。 */
    override suspend fun planRequest(arguments: JsonObject): ExternalRequestPlan {
        val plan = mInvoker.plan(mTool, null, arguments)
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
