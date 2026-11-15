package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.llm.ImageRequestException
import me.kafuuneko.rpclient.libs.llm.ImageRequestFailure
import me.kafuuneko.rpclient.libs.llm.image.ImageInputCapabilities
import me.kafuuneko.rpclient.libs.llm.image.ImageModelAliases
import me.kafuuneko.rpclient.libs.llm.image.openAiImageProfiles
import me.kafuuneko.rpclient.libs.llm.model.ImageQuality
import me.kafuuneko.rpclient.libs.llm.model.ImageTokenEstimatorType
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType

/**
 * 图片协议边界，负责将通用请求设置转换为各协议的图片字段。
 * - 资源引用只提供不可变元数据，服务商参数不进入通用资源模型。
 * - 发送与估算共用选项解析和模型规则，手选预估器不能改变实际输入限制。
 */
internal class ImageContentCodec(private val mProvider: LLMProviderConfig) {
    private val mOptions by lazy { resolveImageInputOptions(mProvider) }
    private val mQuality get() = mOptions.settings.quality
    private val mType = ImageModelAliases.typeFor(mProvider.model)
    private val mAllowedQualities = ImageInputCapabilities.qualities(
        mProvider.protocol,
        mProvider.model,
        mProvider.providerType
    )

    /**
     * 编码图片并在创建请求文件前拒绝不支持的画质、格式和处理后尺寸。
     * @param image 不含任何协议选项的资源元数据。
     * @param marker 本次请求登记的资源占位符，不包含图片字节。
     * @return 当前协议的图片内容块。
     */
    fun encode(image: LLMImageReference, marker: String): JsonObject {
        validate(image)
        // 协议形状及字段值只在适配层转换，资源本身不携带请求选项。
        return when (mProvider.protocol) {
            LLMProviderProtocol.OpenAIResponses -> JsonObject().apply {
                addProperty("type", "input_image")
                addProperty("image_url", marker)
                addProperty("detail", mQuality.openAiDetailValue())
            }

            LLMProviderProtocol.OpenAICompatible -> JsonObject().apply {
                addProperty("type", "image_url")
                add("image_url", JsonObject().apply {
                    addProperty("url", marker)
                    if (mQuality != ImageQuality.Auto) addProperty(
                        "detail",
                        mQuality.openAiDetailValue()
                    )
                })
            }

            LLMProviderProtocol.Gemini -> JsonObject().apply {
                add("inlineData", JsonObject().apply {
                    addProperty("mimeType", image.mimeType)
                    addProperty("data", marker)
                })
                if (mType != ImageTokenEstimatorType.Gemini25 && mQuality != ImageQuality.Auto) {
                    add("mediaResolution", JsonObject().apply {
                        addProperty("level", mQuality.geminiResolutionValue())
                    })
                }
            }

            LLMProviderProtocol.AnthropicMessages -> JsonObject().apply {
                addProperty("type", "image")
                add("source", JsonObject().apply {
                    addProperty("type", "base64")
                    addProperty("media_type", image.mimeType)
                    addProperty("data", marker)
                })
            }
        }
    }

    private fun validate(image: LLMImageReference) {
        if (mAllowedQualities.isNotEmpty() && mQuality !in mAllowedQualities) {
            throw ImageRequestException(ImageRequestFailure.UnsupportedDetail)
        }
        // 已核实的服务端限制只应用于真实协议和模型，不使用手选估算类别推断。
        when (mProvider.protocol) {
            LLMProviderProtocol.OpenAICompatible, LLMProviderProtocol.OpenAIResponses -> {
                openAiImageProfiles[mType]?.validate(image, mQuality)
                if (mProvider.providerType == LLMProviderType.Grok && image.mimeType !in setOf(
                        "image/jpeg",
                        "image/png"
                    )
                ) {
                    throw ImageRequestException(ImageRequestFailure.UnsupportedFormat)
                }
            }

            LLMProviderProtocol.Gemini -> if (mOptions.inheritedQuality == ImageQuality.UltraHigh) {
                throw ImageRequestException(ImageRequestFailure.UnsupportedDetail)
            }

            LLMProviderProtocol.AnthropicMessages -> Unit
        }
    }
}

/**
 * 旧版 Gemini 将通用画质设置写入全局参数；现代型号在各图片内容块中编码。
 * @param provider 本次实际请求的模型配置。
 * @return 应用画质设置后的当前请求对象。
 */
internal fun JsonObject.applyLegacyGeminiImageOptions(provider: LLMProviderConfig): JsonObject {
    // 仅旧版 Gemini 使用全局画质，自动档位保留模型已有配置。
    if (provider.protocol != LLMProviderProtocol.Gemini ||
        ImageModelAliases.typeFor(provider.model) != ImageTokenEstimatorType.Gemini25
    ) return this
    val quality = provider.requestBodyPatchJson.readImageInputOptions().quality
    if (quality == ImageQuality.Auto) return this
    if (quality !in ImageInputCapabilities.qualities(
            provider.protocol,
            provider.model,
            provider.providerType
        )
    ) {
        throw ImageRequestException(ImageRequestFailure.UnsupportedDetail)
    }
    // 写入统一字段前移除兼容别名，避免同义参数并存。
    val config = get("generationConfig")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
    config.remove("media_resolution")
    config.addProperty("mediaResolution", quality.geminiResolutionValue())
    add("generationConfig", config)
    return this
}
