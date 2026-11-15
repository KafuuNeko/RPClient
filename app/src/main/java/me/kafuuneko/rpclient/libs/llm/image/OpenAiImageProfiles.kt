package me.kafuuneko.rpclient.libs.llm.image

import me.kafuuneko.rpclient.libs.llm.ImageRequestException
import me.kafuuneko.rpclient.libs.llm.ImageRequestFailure
import me.kafuuneko.rpclient.libs.llm.model.ImageTokenEstimatorType
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.llm.model.ImageQuality

/**
 * 官方已公开的 OpenAI 图片规则快照，估算和发送前校验共用。
 * - Auto 必须按模型解析；没有公开参数的 GPT-6.1、GPT-6 Luna 不写入此表。
 * - 原图模式的拒绝上限独立于缩放预算，不能为满足上限偷偷缩小图片。
 * https://developers.openai.com/api/docs/guides/images-vision#model-sizing-behavior
 */
internal sealed interface OpenAiImageProfile {
    /** 模型支持的通用画质范围，供编辑页和发送校验共同使用。 */
    val supportedQualities: List<ImageQuality>

    /** 返回指定档位的算法；模型不支持该档位时返回 null。 */
    fun estimator(detail: ImageQuality): ImageTokenEstimator?

    /** 校验真实模型的档位和处理后 Patch 上限，不采用用户手选的代理类别。 */
    fun validate(image: LLMImageReference, detail: ImageQuality) {
        val estimator = estimator(detail)
            ?: throw ImageRequestException(ImageRequestFailure.UnsupportedDetail)
        if (image.width <= 0 || image.height <= 0) {
            throw ImageRequestException(ImageRequestFailure.InvalidImage)
        }
        if (estimator is OpenAiPatchImageTokenEstimator && estimator.processedPatchCount(image) > 30_000) {
            throw ImageRequestException(ImageRequestFailure.TooLarge)
        }
    }

    /** Tile 模型的低档位只收取基础 Token，其他支持档位使用相同缩放规则。 */
    class Tile(private val mBase: Long, private val mTile: Long) : OpenAiImageProfile {
        override val supportedQualities =
            listOf(ImageQuality.Auto, ImageQuality.Low, ImageQuality.High)

        override fun estimator(detail: ImageQuality): ImageTokenEstimator? = when (detail) {
            ImageQuality.Low -> ImageTokenEstimator { mBase }
            ImageQuality.Auto, ImageQuality.High -> TileImageTokenEstimator(mBase, mTile)
            ImageQuality.Original, ImageQuality.Medium, ImageQuality.UltraHigh -> null
        }
    }

    /** 不同档位可使用独立的像素边长和可选 Patch 缩放预算。 */
    class Patch(
        private val mMultiplier: Double,
        private val mLow: Sizing,
        private val mHigh: Sizing,
        private val mOriginal: Sizing? = null,
        private val mAuto: ImageQuality = ImageQuality.High,
    ) : OpenAiImageProfile {
        override val supportedQualities = buildList {
            addAll(listOf(ImageQuality.Auto, ImageQuality.Low, ImageQuality.High))
            if (mOriginal != null) add(ImageQuality.Original)
        }

        override fun estimator(detail: ImageQuality): ImageTokenEstimator? {
            val effectiveDetail = if (detail == ImageQuality.Auto) mAuto else detail
            val sizing = when (effectiveDetail) {
                ImageQuality.Low -> mLow
                ImageQuality.High -> mHigh
                ImageQuality.Original -> mOriginal
                ImageQuality.Auto, ImageQuality.Medium, ImageQuality.UltraHigh -> null
            } ?: return null
            return OpenAiPatchImageTokenEstimator(sizing.maxEdge, sizing.patchBudget, mMultiplier)
        }
    }

    /** null 预算表示只限制边长；超过拒绝上限时不会继续缩放。 */
    data class Sizing(val maxEdge: Int, val patchBudget: Long?)
}

private val LOW_512 = OpenAiImageProfile.Sizing(maxEdge = 512, patchBudget = null)
private val LEGACY_PATCH = OpenAiImageProfile.Sizing(maxEdge = 2048, patchBudget = 6144)
private val HIGH_2048 = OpenAiImageProfile.Sizing(maxEdge = 2048, patchBudget = 2500)
private val ORIGINAL_6000 = OpenAiImageProfile.Sizing(maxEdge = 6000, patchBudget = 10_000)
private val ORIGINAL_65535 = OpenAiImageProfile.Sizing(maxEdge = 65_535, patchBudget = null)

/** 按稳定存储类别查找规则；只包含已核实的模型族。 */
internal val openAiImageProfiles: Map<ImageTokenEstimatorType, OpenAiImageProfile> = mapOf(
    ImageTokenEstimatorType.OpenAiTile4o to OpenAiImageProfile.Tile(mBase = 85, mTile = 170),
    ImageTokenEstimatorType.OpenAiTile4oMini to OpenAiImageProfile.Tile(mBase = 2833, mTile = 5667),
    ImageTokenEstimatorType.OpenAiTile51 to OpenAiImageProfile.Tile(mBase = 70, mTile = 140),
    ImageTokenEstimatorType.OpenAiTileReasoning to OpenAiImageProfile.Tile(mBase = 75, mTile = 150),
    ImageTokenEstimatorType.OpenAiPatch41Mini to OpenAiImageProfile.Patch(
        mMultiplier = 1.62, mLow = LEGACY_PATCH, mHigh = LEGACY_PATCH,
    ),
    ImageTokenEstimatorType.OpenAiPatch52 to OpenAiImageProfile.Patch(
        mMultiplier = 1.2, mLow = LEGACY_PATCH, mHigh = LEGACY_PATCH,
    ),
    ImageTokenEstimatorType.OpenAiPatch54 to OpenAiImageProfile.Patch(
        mMultiplier = 1.2, mLow = LEGACY_PATCH, mHigh = HIGH_2048, mOriginal = ORIGINAL_6000,
    ),
    ImageTokenEstimatorType.OpenAiPatch55 to OpenAiImageProfile.Patch(
        mMultiplier = 1.2, mLow = LOW_512, mHigh = HIGH_2048, mOriginal = ORIGINAL_6000,
        mAuto = ImageQuality.Original,
    ),
    ImageTokenEstimatorType.OpenAiPatch56 to OpenAiImageProfile.Patch(
        mMultiplier = 1.2, mLow = LOW_512, mHigh = HIGH_2048, mOriginal = ORIGINAL_65535,
        mAuto = ImageQuality.Original,
    ),
    ImageTokenEstimatorType.OpenAiPatch6 to OpenAiImageProfile.Patch(
        mMultiplier = 1.2, mLow = LOW_512,
        mHigh = OpenAiImageProfile.Sizing(maxEdge = 65_535, patchBudget = 2500),
        mOriginal = ORIGINAL_65535, mAuto = ImageQuality.Original,
    ),
)
