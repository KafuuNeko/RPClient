package me.kafuuneko.rpclient.libs.agent.tool

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.model.AgentJson

/** 严格解析原始参数，执行器与手工测试共享相同的结构校验边界。 */
object ToolInputParser {
    /** @return 未合并默认值的独立参数对象；非法输入抛出稳定准备错误。 */
    fun parse(raw: String, schema: ToolSchema): JsonObject {
        val value = try {
            AgentJson.parse(raw).asJsonObject
        } catch (_: Exception) {
            throw ToolPreparationException(ToolErrorCode.INVALID_TOOL_ARGUMENTS)
        }
        if (ToolSchemaValidator.validate(schema, value).isNotEmpty()) {
            throw ToolPreparationException(ToolErrorCode.INVALID_TOOL_ARGUMENTS)
        }
        return value
    }
}
