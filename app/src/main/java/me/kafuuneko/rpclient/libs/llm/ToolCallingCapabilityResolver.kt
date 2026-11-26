package me.kafuuneko.rpclient.libs.llm

import java.util.concurrent.ConcurrentHashMap
import me.kafuuneko.rpclient.libs.agent.model.AgentJson
import me.kafuuneko.rpclient.libs.llm.catalog.model.LLMAvailableModel
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.ToolCallingSetting
import me.kafuuneko.rpclient.libs.llm.model.UnsupportedToolCalling

/**
 * 工具能力按配置与目录隔离缓存，未知允许用户显式开启 Agent 后原生尝试。
 * - 不额外发起付费探测，也不把普通 HTTP 错误学习为能力不支持。
 * - 用户 Enabled/Disabled 始终优先于目录；普通生成不经过此入口。
 */
class ToolCallingCapabilityResolver {
    private val mCatalog = ConcurrentHashMap<String, Pair<Boolean, Long>>()

    /** 只采用目录明确提供的参数能力；空集合表示未知。 */
    fun record(provider: LLMProviderConfig, models: List<LLMAvailableModel>) {
        models.forEach { model ->
            val key = key(provider.copy(model = model.id))
            if (model.supportedParameters.isEmpty()) mCatalog.remove(key)
            else mCatalog[key] = model.supportedParameters.any {
                it in setOf(
                    "tools",
                    "tool_choice",
                    "function_calling"
                )
            } to System.currentTimeMillis()
        }
    }

    /** 返回三态供设置页提示，不依赖模型名字猜测。 */
    fun resolve(provider: LLMProviderConfig): ToolCallingSetting {
        if (provider.toolCallingSetting != ToolCallingSetting.Auto) return provider.toolCallingSetting
        val cached =
            mCatalog[key(provider)]?.takeIf { System.currentTimeMillis() - it.second < 3_600_000 }
                ?: return ToolCallingSetting.Auto
        return if (cached.first) ToolCallingSetting.Enabled else ToolCallingSetting.Disabled
    }

    /** 明确禁用时阻断整次运行，不静默降级普通聊天。 */
    fun requireSupported(provider: LLMProviderConfig) {
        if (resolve(provider) == ToolCallingSetting.Disabled) throw UnsupportedToolCalling(
            listOf(
                provider.model
            )
        )
    }

    private fun key(provider: LLMProviderConfig) = AgentJson.digest(
        listOf(
            provider.providerId,
            provider.protocol,
            provider.baseUrl,
            provider.model,
            provider.apiKey,
            provider.customHeadersJson
        ).joinToString("\u0000")
    )
}
