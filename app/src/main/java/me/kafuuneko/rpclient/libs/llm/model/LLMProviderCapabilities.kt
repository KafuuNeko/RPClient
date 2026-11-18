package me.kafuuneko.rpclient.libs.llm.model

/** 模型配置编辑和请求序列化共用的参数能力约束。 */
data class LLMProviderCapabilities(
    /** 当前协议允许的 temperature 参数范围。 */
    val temperatureRange: ClosedFloatingPointRange<Float>,
    /** 当前协议允许的 top_p 参数范围。 */
    val topPRange: ClosedFloatingPointRange<Float>,
    /** 新建配置默认是否发送 temperature 参数。 */
    val defaultSendTemperature: Boolean,
    /** 新建配置默认是否发送 top_p 参数。 */
    val defaultSendTopP: Boolean
) {
    companion object {
        /** 按协议给出保守默认值；用户仍可在模型配置页面显式调整发送开关。 */
        fun forProtocol(protocol: LLMProviderProtocol): LLMProviderCapabilities {
            return when (protocol) {
                LLMProviderProtocol.GeminiInteractions,
                LLMProviderProtocol.OpenAIResponses -> LLMProviderCapabilities(
                    temperatureRange = 0f..2f,
                    topPRange = 0f..1f,
                    defaultSendTemperature = false,
                    defaultSendTopP = false,
                )

                LLMProviderProtocol.OpenAICompatible -> LLMProviderCapabilities(
                    temperatureRange = 0f..2f,
                    topPRange = 0f..1f,
                    defaultSendTemperature = true,
                    defaultSendTopP = true,
                )

                LLMProviderProtocol.Gemini -> LLMProviderCapabilities(
                    temperatureRange = 0f..2f,
                    topPRange = 0f..1f,
                    defaultSendTemperature = true,
                    defaultSendTopP = true,
                )

                LLMProviderProtocol.AnthropicMessages -> LLMProviderCapabilities(
                    temperatureRange = 0f..1f,
                    topPRange = 0f..1f,
                    defaultSendTemperature = true,
                    defaultSendTopP = false,
                )
            }
        }
    }
}

/** Interactions v1 使用模型自身的采样设置，配置页和请求投影均跳过采样字段。 */
internal val LLMProviderProtocol.supportsSampling: Boolean
    get() = this != LLMProviderProtocol.GeminiInteractions
