package me.kafuuneko.rpclient.libs.llm.image

import me.kafuuneko.rpclient.libs.llm.ImageInputCapabilityResolver
import me.kafuuneko.rpclient.libs.llm.adapter.ResolvedImageInputOptions
import me.kafuuneko.rpclient.libs.llm.adapter.resolveImageInputOptions
import me.kafuuneko.rpclient.libs.llm.model.ImageInputSetting
import me.kafuuneko.rpclient.libs.llm.model.ImageQuality
import me.kafuuneko.rpclient.libs.llm.model.ImageTokenEstimatorType
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol

/**
 * 为一次统计冻结图片能力、处理参数与预估器。
 * - 手动类别只选择算法；真实模型的图片能力与输入限制独立校验。
 * - 公开规则核对自 2026-11-15，未公开的模型使用面积代理，不承诺安全上界。
 */
class ImageTokenEstimatorRegistry(
    private val mCapabilities: ImageInputCapabilityResolver = ImageInputCapabilityResolver()
) {
    /**
     * 返回本次模型与通用画质设置对应的计数器；禁用图片时直接归零。
     * @param provider 本次实际模型配置；为空时使用通用面积代理。
     * @return 冻结参数和可信度的图片计数快照。
     */
    fun resolve(provider: LLMProviderConfig?): ImageTokenCounter {
        if (provider != null && mCapabilities.resolve(provider) == ImageInputSetting.Unsupported) {
            return ImageTokenCounter(mEstimator = null)
        }
        // 参数在创建计数快照时解析，无效配置由实际编码器给出受控失败。
        val selected = provider?.imageTokenEstimatorType ?: ImageTokenEstimatorType.Generic
        val type =
            if (selected == ImageTokenEstimatorType.Automatic) automaticType(provider?.model.orEmpty()) else selected
        val options = provider?.let { runCatching { resolveImageInputOptions(it) }.getOrNull() }
        val estimator =
            if (options == null) GenericImageTokenEstimator else estimator(type, options)
        val manualProxy =
            selected != ImageTokenEstimatorType.Automatic && selected != automaticType(provider?.model.orEmpty())
        val protocolProxy = when (type) {
            ImageTokenEstimatorType.Gemini3, ImageTokenEstimatorType.Gemini25 -> provider?.protocol != LLMProviderProtocol.Gemini
            ImageTokenEstimatorType.ClaudeStandard, ImageTokenEstimatorType.ClaudeHighResolution -> provider?.protocol != LLMProviderProtocol.AnthropicMessages
            else -> false
        }
        return ImageTokenCounter(
            mEstimator = estimator,
            usesApproximation = estimator === GenericImageTokenEstimator || type == ImageTokenEstimatorType.Gemini25 || manualProxy || protocolProxy,
        )
    }

    /** 匹配已核实的原生名称、日期快照及网关别名，不猜测未来型号。 */
    internal fun automaticType(model: String): ImageTokenEstimatorType =
        ImageModelAliases.typeFor(model)

    /**
     * 根据估算类别和已解析的画质选择算法。
     * @param type 自动识别或用户指定的估算类别。
     * @param options 当前协议解析后的画质快照。
     * @return 对应算法；没有适用规则时使用通用估算。
     */
    private fun estimator(
        type: ImageTokenEstimatorType,
        options: ResolvedImageInputOptions
    ): ImageTokenEstimator {
        // 已登记的 OpenAI 规则自行处理画质，无法选择档位时沿用通用估算。
        val profile = openAiImageProfiles[type]
        if (profile != null) return profile.estimator(options.settings.quality)
            ?: GenericImageTokenEstimator
        // 其他模型采用各自算法，Gemini 使用显式设置与继承参数合成的有效画质。
        return when (type) {
            ImageTokenEstimatorType.ClaudeStandard -> ClaudeImageTokenEstimator(
                mMaxEdge = 1568,
                mMaxTokens = 1568
            )

            ImageTokenEstimatorType.ClaudeHighResolution -> ClaudeImageTokenEstimator(
                mMaxEdge = 2576,
                mMaxTokens = 4784
            )

            ImageTokenEstimatorType.Gemini3, ImageTokenEstimatorType.Gemini25 -> geminiEstimator(
                type,
                options.effectiveQuality
            )

            else -> GenericImageTokenEstimator
        }
    }

    /** Gemini 档位是视觉预算；旧版 Pan & Scan 依赖内容，不能冒充精确计数。 */
    private fun geminiEstimator(
        type: ImageTokenEstimatorType,
        quality: ImageQuality?
    ): ImageTokenEstimator {
        // 基础图加约 2048 Token 的 Pan & Scan 只作代理，不作为可证明上界。
        val tokens = if (type == ImageTokenEstimatorType.Gemini25) when (quality) {
            ImageQuality.Low -> 64L
            ImageQuality.Medium -> 256L
            ImageQuality.Auto, ImageQuality.High -> 2304L
            else -> null
        } else when (quality) {
            ImageQuality.Auto, ImageQuality.High -> 1120L
            ImageQuality.Low -> 280L
            ImageQuality.Medium -> 560L
            ImageQuality.UltraHigh -> 2240L
            else -> null
        }
        return if (tokens == null) GenericImageTokenEstimator else ImageTokenEstimator { tokens }
    }
}

/** 一次解析后的图片计数入口，记录通用代理与内容相关算法的近似性质。 */
class ImageTokenCounter internal constructor(
    private val mEstimator: ImageTokenEstimator?,
    val usesApproximation: Boolean = false,
) {
    /**
     * 每次出现都计数，不按资源 hash 去重；异常元数据的总计采用饱和加法。
     * @param images 最终发送版本的资源描述。
     * @return 非负图片 Token 估值，禁用图片时为零。
     */
    fun count(images: List<LLMImageReference>): Long {
        val estimator = mEstimator ?: return 0L
        return images.fold(0L) { total, image ->
            val count =
                if (image.width <= 0 || image.height <= 0) GENERIC_IMAGE_TOKENS else estimator.estimate(
                    image
                )
            if (Long.MAX_VALUE - total < count) Long.MAX_VALUE else total + count
        }
    }

    /**
     * 为缺少实际尺寸的候选预留通用预算；最终发送仍必须通过资源校验。
     * @param count 不可用候选数，负数按零处理。
     * @return 候选预算，禁用图片时为零。
     */
    fun countUnavailable(count: Int): Long =
        if (mEstimator == null) 0L else count.coerceAtLeast(0).toLong() * GENERIC_IMAGE_TOKENS
}
