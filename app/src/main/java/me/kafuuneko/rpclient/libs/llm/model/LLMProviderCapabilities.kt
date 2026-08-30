package me.kafuuneko.rpclient.libs.llm.model

/** 模型配置编辑和请求序列化共用的参数能力约束。 */
data class LLMProviderCapabilities(
    val temperatureRange: ClosedFloatingPointRange<Float>,
    val topPRange: ClosedFloatingPointRange<Float>,
    val defaultSendTemperature: Boolean,
    val defaultSendTopP: Boolean,
    /** 是否允许用户显式请求流式用量；false 不代表服务端响应无法携带用量。 */
    val supportsStreamUsageRequest: Boolean
) {
    companion object {
        /** 按协议给出保守默认值；用户仍可在模型配置页面显式调整发送开关。 */
        fun forProtocol(protocol: LLMProviderProtocol): LLMProviderCapabilities {
            return when (protocol) {
                LLMProviderProtocol.OpenAICompatible -> LLMProviderCapabilities(
                    temperatureRange = 0f..2f,
                    topPRange = 0f..1f,
                    defaultSendTemperature = true,
                    defaultSendTopP = true,
                    supportsStreamUsageRequest = true
                )
                LLMProviderProtocol.Gemini -> LLMProviderCapabilities(
                    temperatureRange = 0f..2f,
                    topPRange = 0f..1f,
                    defaultSendTemperature = true,
                    defaultSendTopP = true,
                    supportsStreamUsageRequest = false
                )
                LLMProviderProtocol.AnthropicMessages -> LLMProviderCapabilities(
                    temperatureRange = 0f..1f,
                    topPRange = 0f..1f,
                    defaultSendTemperature = true,
                    defaultSendTopP = false,
                    supportsStreamUsageRequest = false
                )
            }
        }
    }
}
