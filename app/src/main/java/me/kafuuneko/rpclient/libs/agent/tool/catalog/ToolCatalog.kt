package me.kafuuneko.rpclient.libs.agent.tool.catalog

import com.google.gson.JsonPrimitive
import me.kafuuneko.rpclient.libs.agent.connection.BuiltinToolOverrideCodec
import me.kafuuneko.rpclient.libs.agent.connection.ToolConfigValidationException
import me.kafuuneko.rpclient.libs.agent.tool.AgentTool
import me.kafuuneko.rpclient.libs.agent.tool.ToolErrorCode
import me.kafuuneko.rpclient.libs.agent.tool.ToolRegistry
import me.kafuuneko.rpclient.utils.jsonArray

/**
 * 只读注册目录，排序和冲突检查不依赖 Koin 集合迭代顺序。
 * - 规格同时供策略、编辑器、备份校验和运行工厂使用。
 * - 创建工具时才传入业务服务，校验配置无需实例化工具。
 */
class ToolCatalog(registrations: List<ToolRegistration>) {
    val registrations =
        registrations.sortedWith(compareBy({ it.order }, { it.specification.toolId }))
    val reservedNames: Set<String> =
        this.registrations.mapTo(linkedSetOf()) { it.specification.toolId }
    val defaultToolIdsJson: String =
        jsonArray(this.registrations.filter { it.specification.defaultEnabled }
            .map { JsonPrimitive(it.specification.toolId) }).toString()

    init {
        require(reservedNames.size == this.registrations.size) { "Duplicate tool registration" }
        this.registrations.forEach { ToolRegistry.snapshot(it.specification.definition()) }
    }

    /** @return 指定稳定 ID 的元数据；未知工具不按名称猜测身份。 */
    fun find(id: String): ToolRegistration? =
        registrations.firstOrNull { it.specification.toolId == id }

    /** @return 经规格校验的冻结执行配置，展示名不进入执行指纹。 */
    fun resolve(id: String, raw: String?): EffectiveToolConfig {
        val specification = find(id)?.specification
            ?: throw ToolConfigValidationException(
                ToolErrorCode.INVALID_OVERRIDE,
                "Unknown built-in tool"
            )
        return BuiltinToolOverrideCodec.resolve(specification, raw)
    }

    /** @return 按稳定顺序构造的配置版本工具集合。 */
    fun create(overrides: Map<String, String>, services: ToolServices): List<AgentTool> =
        registrations.map { registration ->
            val id = registration.specification.toolId
            registration.factory.create(resolve(id, overrides[id]), services)
        }
}
