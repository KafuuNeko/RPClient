package me.kafuuneko.rpclient.libs.llm.model

/** Provider 编辑和请求序列化共用的参数能力约束。 */
data class LLMProviderCapabilities(
    val temperatureRange: ClosedFloatingPointRange<Float>,
    val topPRange: ClosedFloatingPointRange<Float>,
    val defaultSendTemperature: Boolean,
    val defaultSendTopP: Boolean
) {
    companion object {
        /** 按协议给出保守默认值；用户仍可在 Provider 页面显式调整发送开关。 */
        fun forProtocol(protocol: LLMProviderProtocol): LLMProviderCapabilities {
            return when (protocol) {
                LLMProviderProtocol.OpenAICompatible -> LLMProviderCapabilities(
                    temperatureRange = 0f..2f,
                    topPRange = 0f..1f,
                    defaultSendTemperature = true,
                    defaultSendTopP = true
                )
                LLMProviderProtocol.Gemini -> LLMProviderCapabilities(
                    temperatureRange = 0f..2f,
                    topPRange = 0f..1f,
                    defaultSendTemperature = true,
                    defaultSendTopP = true
                )
                LLMProviderProtocol.AnthropicMessages -> LLMProviderCapabilities(
                    temperatureRange = 0f..1f,
                    topPRange = 0f..1f,
                    defaultSendTemperature = true,
                    defaultSendTopP = false
                )
            }
        }
    }
}
