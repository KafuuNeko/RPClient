package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.kafuuneko.rpclient.utils.toPrettyJson
import me.kafuuneko.rpclient.libs.llm.image.ImageInputCapabilities
import me.kafuuneko.rpclient.libs.llm.model.ImageInputOptions
import me.kafuuneko.rpclient.libs.llm.model.ImageQuality
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol

/** 客户端选项复用模型配置的扩展存储，此节点在合并请求时移除。 */
internal const val CLIENT_IMAGE_OPTIONS_KEY = "_rpclient"

/** 协议适配层解析后的图片设置；未知全局枚举保留 null，不能猜成自动档位。 */
internal data class ResolvedImageInputOptions(
    val settings: ImageInputOptions,
    val inheritedQuality: ImageQuality? = ImageQuality.Auto,
) {
    /** 解析显式设置和既有协议全局值的覆盖顺序。 */
    val effectiveQuality: ImageQuality?
        get() = settings.quality.takeUnless { it == ImageQuality.Auto } ?: inheritedQuality
}

/** 只读取客户端拥有的通用画质，不校验其他协议的服务端字段。 */
internal fun JsonObject.imageInputOptions(): ImageInputOptions {
    val node = get(CLIENT_IMAGE_OPTIONS_KEY)
    if (node == null || node.isJsonNull) return ImageInputOptions()
    if (!node.isJsonObject) throw LLMRequestBodyPatchException("Client image options must be an object.")
    val value = node.asJsonObject.get("imageQuality")
    if (value == null || value.isJsonNull) return ImageInputOptions()
    val quality = ImageQuality.entries.firstOrNull { it.persistedValue == value.stringOrNull() }
        ?: throw LLMRequestBodyPatchException("Unsupported image quality setting.")
    return ImageInputOptions(quality)
}

/** 读取模型配置中独立于协议的画质设置，供表单状态投影使用。 */
internal fun String.readImageInputOptions(): ImageInputOptions =
    JsonParser.parseString(ifBlank { "{}" }).asJsonObject.imageInputOptions()

/** 在协议边界一次性解析通用设置和原生全局参数，发送与估算共用。 */
internal fun resolveImageInputOptions(provider: LLMProviderConfig): ResolvedImageInputOptions {
    val root = JsonParser.parseString(provider.requestBodyPatchJson.ifBlank { "{}" }).asJsonObject
    val stored = root.imageInputOptions()
    val supportsQuality = ImageInputCapabilities.qualities(
        provider.protocol, provider.model, provider.providerType,
    ).isNotEmpty()
    val settings = if (supportsQuality) stored else ImageInputOptions()
    if (provider.protocol != LLMProviderProtocol.Gemini) return ResolvedImageInputOptions(settings)
    // 原生协议支持两套 REST 字段名，歧义输入不能依赖服务端的解析顺序。
    if (root.has("generationConfig") && root.has("generation_config")) {
        throw LLMRequestBodyPatchException("Request body contains conflicting generation config aliases.")
    }
    val config = (root.get("generationConfig") ?: root.get("generation_config"))
        ?.takeIf { it.isJsonObject }?.asJsonObject
    if (config?.has("mediaResolution") == true && config.has("media_resolution")) {
        throw LLMRequestBodyPatchException("Request body contains conflicting media resolution aliases.")
    }
    val global = config?.let { it.get("mediaResolution") ?: it.get("media_resolution") }
    val inherited = if (global == null || global.isJsonNull) ImageQuality.Auto
    else global.stringOrNull()?.let { geminiQualityValues[it] }
    return ResolvedImageInputOptions(settings, inherited)
}

/** 更新通用画质，保留高级参数；自动档位移除额外覆盖并继承既有模型配置。 */
internal fun String.withImageQuality(quality: ImageQuality): String {
    val root = JsonParser.parseString(ifBlank { "{}" }).asJsonObject
    val client =
        root.get(CLIENT_IMAGE_OPTIONS_KEY)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
    if (quality == ImageQuality.Auto) client.remove("imageQuality")
    else client.addProperty("imageQuality", quality.persistedValue)
    if (client.size() == 0) root.remove(CLIENT_IMAGE_OPTIONS_KEY) else root.add(
        CLIENT_IMAGE_OPTIONS_KEY,
        client
    )
    return root.toPrettyJson()
}

/** 仅在协议边界将通用画质转换为 OpenAI Compatible/Responses 的 detail 字段。 */
internal fun ImageQuality.openAiDetailValue(): String? = when (this) {
    ImageQuality.Auto -> "auto"
    ImageQuality.Low -> "low"
    ImageQuality.High -> "high"
    ImageQuality.Original -> "original"
    else -> null
}

/** 仅在 Gemini 适配层将通用画质转换为媒体分辨率枚举。 */
internal fun ImageQuality.geminiResolutionValue(): String? =
    geminiQualityValues.entries.firstOrNull { it.value == this }?.key

private fun JsonElement?.stringOrNull(): String? =
    this?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

private val geminiQualityValues = mapOf(
    "MEDIA_RESOLUTION_UNSPECIFIED" to ImageQuality.Auto,
    "MEDIA_RESOLUTION_LOW" to ImageQuality.Low,
    "MEDIA_RESOLUTION_MEDIUM" to ImageQuality.Medium,
    "MEDIA_RESOLUTION_HIGH" to ImageQuality.High,
    "MEDIA_RESOLUTION_ULTRA_HIGH" to ImageQuality.UltraHigh,
)
