package me.kafuuneko.rpclient.libs.llm

import com.google.gson.JsonParser
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import me.kafuuneko.rpclient.libs.llm.catalog.model.LLMAvailableModel
import me.kafuuneko.rpclient.libs.llm.model.ImageInputSetting
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig

/** 图片请求可安全映射到界面的失败分类，不携带文件路径或服务商原文。 */
enum class ImageRequestFailure { Unsupported, Missing, InvalidImage, TooLarge, TooMany }

/** 图片准备、能力和预算错误的结构化边界。 */
class ImageRequestException(val failure: ImageRequestFailure) : IllegalStateException("Image request: $failure")

/**
 * 视觉能力解析器，用户覆盖优先于可信目录，未知允许尝试。
 * - 以完整配置的地址、模型、鉴权摘要与主键隔离缓存，不依据品牌或名字推断能力。
 * - 仅学习明确的图片能力错误，普通 HTTP 400、鉴权失败和超限不能污染能力。
 */
class ImageInputCapabilityResolver {
    private val mCatalog = ConcurrentHashMap<Scope, Pair<ImageInputSetting, Long>>()

    /** 记录目录明确声明的输入模态；字段缺失保持未知。 */
    fun record(provider: LLMProviderConfig, models: List<LLMAvailableModel>) {
        models.forEach { model ->
            val scope = scope(provider.copy(model = model.id))
            val modalities = model.inputModalities
            if (modalities == null) mCatalog.remove(scope)
            else mCatalog[scope] = (if ("image" in modalities) ImageInputSetting.Supported else ImageInputSetting.Unsupported) to System.currentTimeMillis()
        }
    }

    /** 解析本次实际模型，修改配置或目录过期后不会继承旧判断。 */
    fun resolve(provider: LLMProviderConfig): ImageInputSetting {
        if (provider.imageInputSetting != ImageInputSetting.Auto) return provider.imageInputSetting
        val cached = mCatalog[scope(provider)] ?: return ImageInputSetting.Auto
        return if (System.currentTimeMillis() - cached.second < 3_600_000L) cached.first else ImageInputSetting.Auto
    }

    /** 发送前执行能力门禁，未知不会触发额外付费探测。 */
    fun requireImages(provider: LLMProviderConfig) {
        if (resolve(provider) == ImageInputSetting.Unsupported) throw ImageRequestException(ImageRequestFailure.Unsupported)
    }

    /** 仅识别结构化图片能力拒绝，不将普通 400、格式或配额错误学习为不支持。 */
    fun recordFailure(provider: LLMProviderConfig, error: Throwable): Throwable {
        val http = error as? LLMHttpStatusException ?: return error
        if (http.statusCode !in setOf(400, 422)) return error
        val payload = runCatching { JsonParser.parseString(http.responseDetail).asJsonObject }.getOrNull()
        val detail = payload?.get("error")?.takeIf { it.isJsonObject }?.asJsonObject ?: return error
        val code = detail.get("code")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val message = detail.get("message")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty().lowercase()
        val explicit = code in setOf("vision_not_supported", "model_does_not_support_images") ||
            ("model" in message && ("does not support image" in message || "doesn't support image" in message || "does not support vision" in message))
        if (!explicit) return error
        mCatalog[scope(provider)] = ImageInputSetting.Unsupported to System.currentTimeMillis()
        return ImageRequestException(ImageRequestFailure.Unsupported)
    }

    private fun scope(provider: LLMProviderConfig) = Scope(provider.providerId, provider.baseUrl.trim().trimEnd('/'),
        provider.model, provider.protocol.name, digest(provider.apiKey + provider.customHeadersJson + provider.requestBodyPatchJson))

    private fun digest(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private data class Scope(val id: Long?, val address: String, val model: String, val protocol: String, val credentials: String)
}
