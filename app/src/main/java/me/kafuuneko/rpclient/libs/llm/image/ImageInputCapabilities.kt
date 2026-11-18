package me.kafuuneko.rpclient.libs.llm.image

import me.kafuuneko.rpclient.libs.llm.model.ImageQuality
import me.kafuuneko.rpclient.libs.llm.model.ImageTokenEstimatorType
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType

/**
 * 图片处理选项的唯一能力策略，编辑页与请求编码共用。
 * - 协议决定参数范围，已核实的模型规则进一步收敛范围。
 * - 不根据这些选项推断模型是否支持图片，图片能力门禁仍由独立解析器处理。
 */
internal object ImageInputCapabilities {
    /**
     * 返回模型在当前协议下可配置的通用画质。
     * @param protocol 实际请求协议。
     * @param model 实际请求模型或已核实的网关别名。
     * @param providerType 用户配置的服务商类型，仅用于明确的协议限制。
     * @return 可选画质；空列表表示没有已核实的画质参数映射。
     */
    fun qualities(
        protocol: LLMProviderProtocol,
        model: String,
        providerType: LLMProviderType
    ): List<ImageQuality> {
        // 协议范围先行，模型规则只收敛已核实的档位，不承担图片能力探测。
        val type = ImageModelAliases.typeFor(model)
        return when (protocol) {
            LLMProviderProtocol.AnthropicMessages -> emptyList()
            LLMProviderProtocol.GeminiInteractions,
            LLMProviderProtocol.Gemini -> if (type == ImageTokenEstimatorType.Gemini25) mGeminiLegacy else mGemini

            LLMProviderProtocol.OpenAICompatible, LLMProviderProtocol.OpenAIResponses -> {
                // 第三方兼容接口不等于原生媒体参数；没有公开映射时保持模型默认处理。
                if (type in mNativeOnlyQualityTypes) emptyList()
                else if (providerType == LLMProviderType.Grok) mOpenAiBasic
                else openAiImageProfiles[type]?.supportedQualities ?: mOpenAi
            }
        }
    }

    private val mOpenAiBasic = listOf(ImageQuality.Auto, ImageQuality.Low, ImageQuality.High)
    private val mOpenAi = mOpenAiBasic + ImageQuality.Original
    private val mGeminiLegacy =
        listOf(ImageQuality.Auto, ImageQuality.Low, ImageQuality.Medium, ImageQuality.High)
    private val mGemini = mGeminiLegacy + ImageQuality.UltraHigh
    private val mNativeOnlyQualityTypes = setOf(
        ImageTokenEstimatorType.ClaudeStandard, ImageTokenEstimatorType.ClaudeHighResolution,
        ImageTokenEstimatorType.Gemini3, ImageTokenEstimatorType.Gemini25,
    )
}
