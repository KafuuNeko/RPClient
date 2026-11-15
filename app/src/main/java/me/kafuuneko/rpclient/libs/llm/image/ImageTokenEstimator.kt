package me.kafuuneko.rpclient.libs.llm.image

import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sqrt

/** 纯函数估算发送版本的视觉 Token，不读取图片、不改变请求。 */
fun interface ImageTokenEstimator {
    /** 返回非负估值；缺少尺寸时使用通用近似。 */
    fun estimate(image: LLMImageReference): Long
}

/** 缺少尺寸时的通用近似；未知模型还需结合面积估算，均不承诺安全上界。 */
internal const val GENERIC_IMAGE_TOKENS = 4096L

/**
 * 未公开视觉规则的模型使用面积代理，避免原图与缩略图始终得到相同预算。
 * - 取公开 32px Patch 规则的最大乘数作为保守代理，不宣称适用于任意未来模型。
 * - 保留既有最低预留；缺少尺寸时不根据文件体积猜测像素。
 */
internal object GenericImageTokenEstimator : ImageTokenEstimator {
    override fun estimate(image: LLMImageReference): Long {
        if (image.width <= 0 || image.height <= 0) return GENERIC_IMAGE_TOKENS
        val patches = patchCount(image.width.toDouble(), image.height.toDouble(), patch = 32)
        return maxOf(GENERIC_IMAGE_TOKENS, ceil(patches * 2.46).toLong())
    }
}

/** OpenAI Tile 规则，参数核对自 2026-11-15 官方 images-vision 文档。 */
internal class TileImageTokenEstimator(private val mBase: Long, private val mTile: Long) :
    ImageTokenEstimator {
    override fun estimate(image: LLMImageReference): Long {
        if (image.width <= 0 || image.height <= 0) return GENERIC_IMAGE_TOKENS
        val fit = minOf(a = 1.0, b = 2048.0 / maxOf(image.width, image.height))
        val width = floor(image.width * fit).coerceAtLeast(1.0)
        val height = floor(image.height * fit).coerceAtLeast(1.0)
        val shortSideFit = minOf(a = 1.0, b = 768.0 / minOf(width, height))
        val tiles = patchCount(
            width = floor(width * shortSideFit),
            height = floor(height * shortSideFit),
            patch = 512
        )
        return mBase + mTile * tiles
    }
}

/**
 * OpenAI 32px Patch 算法；边长、patch 预算和乘数必须按具体模型指定。
 * https://developers.openai.com/api/docs/guides/images-vision#model-sizing-behavior
 */
internal class OpenAiPatchImageTokenEstimator(
    private val mMaxEdge: Int,
    private val mPatchBudget: Long?,
    private val mMultiplier: Double
) : ImageTokenEstimator {
    override fun estimate(image: LLMImageReference): Long {
        if (image.width <= 0 || image.height <= 0) return GENERIC_IMAGE_TOKENS
        return ceil(processedPatchCount(image) * mMultiplier).toLong()
    }

    /** 返回服务端缩放后的 Patch 数，供计数与发送前单图限制校验共用。 */
    fun processedPatchCount(image: LLMImageReference): Long {
        // 先限制像素边长，不放大小图；保持全精度至最后的像素取整。
        val fit = minOf(a = 1.0, b = mMaxEdge.toDouble() / maxOf(image.width, image.height))
        var width = round(image.width * fit).coerceAtLeast(1.0)
        var height = round(image.height * fit).coerceAtLeast(1.0)
        if (mPatchBudget != null && patchCount(
                width = width,
                height = height,
                patch = 32
            ) > mPatchBudget
        ) {
            val shrink = sqrt(32.0 * 32 * mPatchBudget / (width * height))
            val widthPatches = width * shrink / 32
            val heightPatches = height * shrink / 32
            val adjusted = shrink * minOf(
                a = floor(widthPatches) / widthPatches,
                b = floor(heightPatches) / heightPatches,
            )
            width = floor(width * adjusted).coerceAtLeast(1.0)
            height = floor(height * adjusted).coerceAtLeast(1.0)
        }
        return patchCount(width = width, height = height, patch = 32)
    }
}

/**
 * Claude 28px Patch 算法，按长边二分搜索最大可容纳尺寸。
 * 与官方参考实现一致，短边使用偶数舍入，限制补齐后的边长及 patch 数。
 * https://platform.claude.com/docs/en/build-with-claude/vision-coordinates
 */
internal class ClaudeImageTokenEstimator(
    private val mMaxEdge: Int,
    private val mMaxTokens: Long
) : ImageTokenEstimator {
    override fun estimate(image: LLMImageReference): Long {
        if (image.width <= 0 || image.height <= 0) return GENERIC_IMAGE_TOKENS
        val longEdge = maxOf(image.width, image.height)
        val shortEdge = minOf(image.width, image.height)
        if (fits(longEdge, shortEdge)) return patchCount(
            width = longEdge.toDouble(),
            height = shortEdge.toDouble(),
            patch = 28,
        )
        // 在整数长边上搜索，避免连续面积近似忽略 patch 补齐后的阶跃。
        val aspect = longEdge.toDouble() / shortEdge
        var low = 1
        var high = longEdge
        while (low + 1 < high) {
            val middle = low + (high - low) / 2
            if (fits(middle, round(middle / aspect).toInt().coerceAtLeast(1))) low = middle
            else high = middle
        }
        return patchCount(
            width = low.toDouble(),
            height = round(low / aspect).coerceAtLeast(1.0),
            patch = 28,
        )
    }

    private fun fits(width: Int, height: Int): Boolean =
        ceil(width / 28.0) * 28 <= mMaxEdge && ceil(height / 28.0) * 28 <= mMaxEdge
            && patchCount(
            width = width.toDouble(),
            height = height.toDouble(),
            patch = 28
        ) <= mMaxTokens
}

/** 先转为 Long 再相乘，避免极端尺寸使中间值溢出。 */
private fun patchCount(width: Double, height: Double, patch: Int): Long =
    ceil(width / patch).toLong() * ceil(height / patch).toLong()
