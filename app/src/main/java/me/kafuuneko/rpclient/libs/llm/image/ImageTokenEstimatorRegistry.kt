package me.kafuuneko.rpclient.libs.llm.image

import com.google.gson.JsonParser
import me.kafuuneko.rpclient.libs.llm.ImageInputCapabilityResolver
import me.kafuuneko.rpclient.libs.llm.model.ImageInputSetting
import me.kafuuneko.rpclient.libs.llm.model.ImageTokenEstimatorType
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig

/**
 * 为一次统计冻结图片能力和预估器，调用方无需重复解析目录缓存。
 * - 手动类别优先；模型别名仅选择算法，不据此推断图片能力。
 * - 无模型信息使用 Generic；Unsupported 在访问元数据前直接归零。
 */
class ImageTokenEstimatorRegistry(
    private val mCapabilities: ImageInputCapabilityResolver = ImageInputCapabilityResolver()
) {
    /** 解析实际模型配置，返回包含能力门禁的统一图片计数器。 */
    fun resolve(provider: LLMProviderConfig?): ImageTokenCounter {
        if (provider != null && mCapabilities.resolve(provider) == ImageInputSetting.Unsupported) {
            return ImageTokenCounter(mEstimator = null)
        }
        val selected = provider?.imageTokenEstimatorType ?: ImageTokenEstimatorType.Generic
        val type =
            if (selected == ImageTokenEstimatorType.Automatic) automaticType(provider?.model.orEmpty()) else selected
        // 参数快照来自 2026-09-14 官方 sizing 表；当前请求省略 detail，使用各模型 auto 行为。
        val estimator = when (type) {
            ImageTokenEstimatorType.OpenAiTile4o -> TileImageTokenEstimator(mBase = 85, mTile = 170)
            ImageTokenEstimatorType.OpenAiTile4oMini -> TileImageTokenEstimator(
                mBase = 2833,
                mTile = 5667
            )

            ImageTokenEstimatorType.OpenAiPatch41Mini -> OpenAiPatchImageTokenEstimator(
                mMaxEdge = 2048,
                mPatchBudget = 6144,
                mMultiplier = 1.62,
            )

            ImageTokenEstimatorType.OpenAiPatch54 -> OpenAiPatchImageTokenEstimator(
                mMaxEdge = 2048,
                mPatchBudget = 2500,
                mMultiplier = 1.2,
            )

            ImageTokenEstimatorType.ClaudeStandard -> ClaudeImageTokenEstimator(
                mMaxEdge = 1568,
                mMaxTokens = 1568
            )

            ImageTokenEstimatorType.ClaudeHighResolution -> ClaudeImageTokenEstimator(
                mMaxEdge = 2576,
                mMaxTokens = 4784,
            )

            ImageTokenEstimatorType.Gemini3 -> geminiEstimator(provider?.requestBodyPatchJson.orEmpty())
            ImageTokenEstimatorType.Generic, ImageTokenEstimatorType.Automatic -> ImageTokenEstimator { GENERIC_IMAGE_TOKENS }
        }
        return ImageTokenCounter(estimator)
    }

    /** 只接受明确模型 ID 与日期快照，未知网关命名保持 Generic。 */
    internal fun automaticType(model: String): ImageTokenEstimatorType {
        val normalized = model.lowercase().trim()
        val alias = mAliases.entries.firstOrNull { (name, _) ->
            normalized == name || normalized.matches(Regex("${Regex.escape(name)}-\\d{4}-\\d{2}-\\d{2}"))
        }
        return alias?.value ?: ImageTokenEstimatorType.Generic
    }

    /** 只读取既有 Gemini Patch 参数；非法或未知档位回退，不重写质量参数。 */
    private fun geminiEstimator(patch: String): ImageTokenEstimator {
        // https://ai.google.dev/gemini-api/docs/generate-content/media-resolution
        val resolution = runCatching {
            val root = JsonParser.parseString(patch.ifBlank { "{}" }).asJsonObject
            root.getAsJsonObject("generationConfig")?.get("mediaResolution")?.asString
                ?: "MEDIA_RESOLUTION_UNSPECIFIED"
        }.getOrNull()
        val tokens = when (resolution) {
            "MEDIA_RESOLUTION_UNSPECIFIED", "MEDIA_RESOLUTION_HIGH" -> 1120L
            "MEDIA_RESOLUTION_LOW" -> 280L
            "MEDIA_RESOLUTION_MEDIUM" -> 560L
            else -> GENERIC_IMAGE_TOKENS
        }
        return ImageTokenEstimator { tokens }
    }

    private val mAliases: Map<String, ImageTokenEstimatorType> = buildMap {
        fun aliases(type: ImageTokenEstimatorType, namespace: String, vararg names: String) {
            for (name in names) {
                put(name, type)
                put("$namespace/$name", type)
            }
        }
        aliases(ImageTokenEstimatorType.OpenAiTile4o, "openai", "gpt-4o", "gpt-4.1")
        aliases(ImageTokenEstimatorType.OpenAiTile4oMini, "openai", "gpt-4o-mini")
        aliases(ImageTokenEstimatorType.OpenAiPatch41Mini, "openai", "gpt-4.1-mini")
        aliases(
            ImageTokenEstimatorType.OpenAiPatch54,
            "openai",
            "gpt-5.4",
            "gpt-5.4-mini",
            "gpt-5.4-nano"
        )
        aliases(
            ImageTokenEstimatorType.ClaudeStandard,
            "anthropic",
            "claude-sonnet-4-6",
            "claude-opus-4-6",
            "claude-haiku-4-5",
            "claude-sonnet-4-5",
        )
        aliases(
            ImageTokenEstimatorType.ClaudeHighResolution,
            "anthropic",
            "claude-opus-4-7",
            "claude-opus-5"
        )
        aliases(
            ImageTokenEstimatorType.Gemini3,
            "google",
            "gemini-3-pro-preview",
            "gemini-3-flash-preview",
            "gemini-3.1-pro-preview",
        )
    }
}

/** 一次解析后的图片计数入口；缺失元数据的候选也遵循相同能力门禁。 */
class ImageTokenCounter internal constructor(private val mEstimator: ImageTokenEstimator?) {
    /** 每次出现都计数，不按资源 hash 去重。 */
    fun count(images: List<LLMImageReference>): Long =
        if (mEstimator == null) 0L else images.sumOf { image ->
            if (image.width <= 0 || image.height <= 0) GENERIC_IMAGE_TOKENS else mEstimator.estimate(
                image
            )
        }

    /** 不可用候选没有实际尺寸，使用通用近似且不调用模型公式。 */
    fun countUnavailable(count: Int): Long =
        if (mEstimator == null) 0L else count.toLong() * GENERIC_IMAGE_TOKENS
}
