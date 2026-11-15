package me.kafuuneko.rpclient.libs.llm.image

import me.kafuuneko.rpclient.libs.llm.ImageInputCapabilityResolver
import me.kafuuneko.rpclient.libs.llm.adapter.withImageQuality
import me.kafuuneko.rpclient.libs.llm.catalog.model.LLMAvailableModel
import me.kafuuneko.rpclient.libs.llm.model.ImageInputSetting
import me.kafuuneko.rpclient.libs.llm.model.ImageTokenEstimatorType
import me.kafuuneko.rpclient.libs.llm.model.LLMContentBlock
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.llm.model.ImageQuality
import me.kafuuneko.rpclient.libs.llm.model.messageWithBlocks
import me.kafuuneko.rpclient.libs.prompt.PromptTokenizer
import me.kafuuneko.rpclient.libs.prompt.PromptTokenizerRegistry
import me.kafuuneko.rpclient.libs.prompt.model.PromptTokenizerStrategy
import me.kafuuneko.rpclient.libs.room.Converters
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.toConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证模型 sizing、能力优先级和完整／有界统计的共同边界。 */
class ImageTokenEstimatorTest {
    private val mCapabilities = ImageInputCapabilityResolver()
    private val mImages = ImageTokenEstimatorRegistry(mCapabilities)
    private val mTokenizers = PromptTokenizerRegistry(mImages)
    private val mProvider = LLMProvider(
        name = "test",
        providerType = LLMProviderType.Custom,
        protocol = LLMProviderProtocol.OpenAICompatible,
        baseUrl = "https://example.invalid",
        model = "gpt-4o",
    )

    private fun image(width: Int, height: Int) = LLMImageReference(
        uuid = "same",
        mimeType = "image/png",
        width = width,
        height = height,
        byteCount = 10,
    )

    private fun count(type: ImageTokenEstimatorType, width: Int, height: Int): Long =
        mImages.resolve(mProvider.copy(imageTokenEstimatorType = type).toConfig())
            .count(listOf(image(width, height)))

    @Test
    fun tileFamiliesUseDifferentCoefficientsWithoutUpscalingSmallImages() {
        assertEquals(
            765L,
            count(type = ImageTokenEstimatorType.OpenAiTile4o, width = 1024, height = 1024)
        )
        assertEquals(
            25501L,
            count(type = ImageTokenEstimatorType.OpenAiTile4oMini, width = 1024, height = 1024)
        )
        assertEquals(
            255L,
            count(type = ImageTokenEstimatorType.OpenAiTile4o, width = 128, height = 128)
        )
        assertEquals(
            1105L,
            count(type = ImageTokenEstimatorType.OpenAiTile4o, width = 2048, height = 4096)
        )
    }

    @Test
    fun patchFamiliesRespectTheirOwnSizingTableAndMultiplier() {
        assertEquals(
            1659L,
            count(type = ImageTokenEstimatorType.OpenAiPatch41Mini, width = 1024, height = 1024)
        )
        assertEquals(
            6636L,
            count(type = ImageTokenEstimatorType.OpenAiPatch41Mini, width = 2048, height = 2048)
        )
        assertEquals(
            1229L,
            count(type = ImageTokenEstimatorType.OpenAiPatch54, width = 1024, height = 1024)
        )
        assertEquals(
            3000L,
            count(type = ImageTokenEstimatorType.OpenAiPatch54, width = 2048, height = 2048)
        )
        assertEquals(
            2L,
            count(type = ImageTokenEstimatorType.OpenAiPatch54, width = 32, height = 32)
        )
        assertEquals(
            3L,
            count(type = ImageTokenEstimatorType.OpenAiPatch54, width = 33, height = 32)
        )
        assertEquals(
            count(type = ImageTokenEstimatorType.OpenAiPatch54, width = 4096, height = 512),
            count(type = ImageTokenEstimatorType.OpenAiPatch54, width = 512, height = 4096),
        )
    }

    @Test
    fun claudeMatchesOfficialResizingExamplesAndPatchBoundaries() {
        // 使用官方给出的整数缩放样例，尤其保护阶跃边界。
        val standard = ImageTokenEstimatorType.ClaudeStandard
        val high = ImageTokenEstimatorType.ClaudeHighResolution
        assertEquals(64L, count(type = standard, width = 200, height = 200))
        assertEquals(1296L, count(type = standard, width = 1000, height = 1000))
        assertEquals(1560L, count(type = standard, width = 1920, height = 1080))
        assertEquals(1564L, count(type = standard, width = 2000, height = 1500))
        assertEquals(4784L, count(type = high, width = 3840, height = 2160))
        assertEquals(2691L, count(type = high, width = 1920, height = 1080))
        assertEquals(1L, count(type = standard, width = 28, height = 28))
        assertEquals(2L, count(type = standard, width = 29, height = 28))
        assertEquals(
            count(type = standard, width = 1075, height = 1520),
            count(
                type = standard,
                width = 1520,
                height = 1075,
            ),
        )
    }

    @Test
    fun automaticAliasesAreExplicitAndManualChoiceCanCrossProtocols() {
        assertEquals(
            ImageTokenEstimatorType.OpenAiTile4oMini,
            mImages.automaticType("openai/gpt-4o-mini")
        )
        assertEquals(
            ImageTokenEstimatorType.OpenAiPatch41Mini,
            mImages.automaticType("gpt-4.1-mini-2025-04-14")
        )
        for (unknown in listOf("gateway/gpt-4o", "gpt-4o-custom", "gpt-future", "gemini-99-pro")) {
            assertEquals(ImageTokenEstimatorType.Generic, mImages.automaticType(unknown))
        }
        val config = mProvider.copy(
            protocol = LLMProviderProtocol.AnthropicMessages,
            imageTokenEstimatorType = ImageTokenEstimatorType.OpenAiTile4oMini,
        ).toConfig()
        assertEquals(
            25501L,
            mImages.resolve(config).count(listOf(image(width = 1024, height = 1024)))
        )
        assertEquals(
            ImageTokenEstimatorType.Automatic,
            Converters().toImageTokenEstimatorType("future-value")
        )
    }

    @Test
    fun geminiReadsExistingResolutionWithoutChangingRequestParameters() {
        val provider = mProvider.copy(
            protocol = LLMProviderProtocol.Gemini,
            imageTokenEstimatorType = ImageTokenEstimatorType.Gemini3
        )
        val expected = mapOf(
            "MEDIA_RESOLUTION_LOW" to 280L,
            "MEDIA_RESOLUTION_MEDIUM" to 560L,
            "MEDIA_RESOLUTION_HIGH" to 1120L,
            "future" to 4096L,
        )
        for ((resolution, tokens) in expected) {
            val config =
                provider.copy(requestBodyPatchJson = """{"generationConfig":{"mediaResolution":"$resolution"}}""")
                    .toConfig()
            assertEquals(
                tokens,
                mImages.resolve(config).count(listOf(image(width = 1024, height = 1024)))
            )
        }
        assertEquals(
            1120L,
            mImages.resolve(provider.toConfig()).count(listOf(image(width = 1024, height = 1024)))
        )
    }

    @Test
    fun unsupportedDoesNotCallEstimatorEvenForUnavailableMetadata() {
        // 禁用状态在任何元数据读取或公式执行之前直接返回零。
        val counter = ImageTokenCounter(mEstimator = null)
        assertEquals(0L, counter.count(listOf(image(width = 0, height = 0))))
        assertEquals(0L, counter.countUnavailable(3))
        for (content in listOf("text", "")) {
            val provider = mProvider.copy(
                imageInputSetting = ImageInputSetting.Unsupported,
                imageTokenEstimatorType = ImageTokenEstimatorType.OpenAiTile4oMini,
            )
            val tokenizer = mTokenizers.resolve(provider)
            val withImage = messageWithBlocks(
                role = LLMMessageRole.User,
                blocks = listOf(
                    LLMContentBlock.Image(image(width = 0, height = 0)),
                    LLMContentBlock.Text(content)
                ),
            )
            val expected = tokenizer.countMessages(listOf(LLMMessage(LLMMessageRole.User, content)))
            assertTrue(expected > 0)
            assertEquals(expected, tokenizer.countMessages(listOf(withImage)))
            assertEquals(expected, tokenizer.countMessagesUpTo(listOf(withImage), expected))
            assertEquals(
                expected,
                mTokenizers.resolveForUsage(provider.toConfig()).countMessages(listOf(withImage))
            )
        }
    }

    @Test
    fun catalogCapabilityAndEstimatorAreFrozenPerTokenizer() {
        val knownBefore = mTokenizers.resolve(mProvider)
        val config = mProvider.toConfig()
        mCapabilities.record(
            config,
            listOf(LLMAvailableModel(id = config.model, inputModalities = setOf("text")))
        )
        val disabled = mTokenizers.resolve(mProvider)
        assertEquals(0L, disabled.countImages(listOf(image(width = 1024, height = 1024))))
        assertEquals(765L, knownBefore.countImages(listOf(image(width = 1024, height = 1024))))
        val enabled =
            mTokenizers.resolve(mProvider.copy(imageInputSetting = ImageInputSetting.Supported))
        assertEquals(765L, enabled.countImages(listOf(image(width = 1024, height = 1024))))
        assertEquals(
            4096L,
            mTokenizers.resolve(mProvider.copy(model = "unknown")).countUnavailableImages(1)
        )
    }

    @Test
    fun eachOccurrenceAndEachModelUsesItsOwnEstimate() {
        val images = listOf(image(width = 1024, height = 1024), image(width = 1024, height = 1024))
        assertEquals(1530L, mTokenizers.resolve(mProvider).countImages(images))
        assertEquals(
            51002L,
            mTokenizers.resolve(mProvider.copy(model = "gpt-4o-mini")).countImages(images)
        )
        assertEquals(
            0L,
            mTokenizers.resolve(mProvider.copy(imageInputSetting = ImageInputSetting.Unsupported))
                .countImages(images),
        )
        assertEquals(1530L, mTokenizers.resolve(mProvider).countImages(images))
        assertEquals(8192L, mTokenizers.resolve(provider = null).countImages(images))
    }

    @Test
    fun fullAndBoundedCountingSaturateInsteadOfWrapping() {
        val tokenizer = object : PromptTokenizer {
            override val name = "extreme"
            override val strategy = PromptTokenizerStrategy.Estimated
            override fun countText(text: String) = Int.MAX_VALUE
        }
        val messages =
            listOf(LLMMessage(LLMMessageRole.User, "a"), LLMMessage(LLMMessageRole.Assistant, "b"))
        assertEquals(Int.MAX_VALUE, tokenizer.countMessages(messages))
        assertEquals(Int.MAX_VALUE, tokenizer.countMessagesUpTo(messages, Int.MAX_VALUE))
        for (type in ImageTokenEstimatorType.entries) {
            assertTrue(count(type = type, width = Int.MAX_VALUE, height = 1) > 0)
            assertTrue(count(type, Int.MAX_VALUE, Int.MAX_VALUE) > 0)
        }
    }

    @Test
    fun latestModelsSnapshotsAndRouterNamesSelectOnlyVerifiedRules() {
        val known = mapOf(
            "gpt-5.1" to ImageTokenEstimatorType.OpenAiTile51,
            "gpt-5.2" to ImageTokenEstimatorType.OpenAiPatch52,
            "gpt-5.5" to ImageTokenEstimatorType.OpenAiPatch55,
            "openai/gpt-5.6-sol:nitro" to ImageTokenEstimatorType.OpenAiPatch56,
            "gpt-6-astra-2026-09-03" to ImageTokenEstimatorType.OpenAiPatch6,
            "claude-haiku-4-5-20251001" to ImageTokenEstimatorType.ClaudeStandard,
            "anthropic/claude-opus-4.6" to ImageTokenEstimatorType.ClaudeStandard,
            "anthropic/claude-opus-5.5:floor" to ImageTokenEstimatorType.ClaudeHighResolution,
            "claude-fable-5-2" to ImageTokenEstimatorType.ClaudeHighResolution,
            "claude-sonnet-5-5" to ImageTokenEstimatorType.ClaudeHighResolution,
            "claude-haiku-5-5" to ImageTokenEstimatorType.ClaudeHighResolution,
            "google/gemini-4-carbon" to ImageTokenEstimatorType.Gemini3,
            "gemini-4-argon" to ImageTokenEstimatorType.Gemini3,
            "gemini-4-hydrogen" to ImageTokenEstimatorType.Gemini3,
            "gemini-3.8-flash" to ImageTokenEstimatorType.Gemini3,
            "gemini-2.5-pro" to ImageTokenEstimatorType.Gemini25,
        )
        // 保护网关命名与快照兼容，同时禁止规则从相似名字扩散到未知模型。
        known.forEach { (name, type) -> assertEquals(name, type, mImages.automaticType(name)) }
        for (name in listOf(
            "gpt-6.1-sol",
            "gpt-6-luna",
            "grok-4.7",
            "gateway/gpt-6-astra",
            "gpt-6-astra-custom",
            "openai/gpt-6-astra:custom"
        )) {
            assertEquals(name, ImageTokenEstimatorType.Generic, mImages.automaticType(name))
        }
    }

    @Test
    fun openAiAutoLowHighAndOriginalUseModelSpecificSizing() {
        fun tokens(
            model: String,
            width: Int,
            height: Int,
            detail: ImageQuality = ImageQuality.Auto
        ): Long {
            val counter = mImages.resolve(
                mProvider.copy(
                    model = model,
                    requestBodyPatchJson = "{}".withImageQuality(detail)
                ).toConfig()
            )
            return counter.count(listOf(image(width, height)))
        }
        // 新系列 auto 保留原始细节，不能沿用 GPT-5.4 的 high 缩放预算。
        assertEquals(11060L, tokens("gpt-6-astra", 3072, 3072))
        assertEquals(11060L, tokens("gpt-5.5", 3072, 3072))
        assertEquals(19661L, tokens("gpt-5.6-sol", 4096, 4096))
        assertEquals(12000L, tokens("gpt-5.5", 6000, 6000))
        assertEquals(3000L, tokens("gpt-5.4", 3072, 3072))
        assertEquals(4916L, tokens("gpt-5.2", 2048, 2048))
        assertEquals(85L, tokens("gpt-4o", 1024, 1024, ImageQuality.Low))
        assertEquals(2833L, tokens("gpt-4o-mini", 1024, 1024, ImageQuality.Low))
        assertEquals(308L, tokens("gpt-6-astra", 1024, 1024, ImageQuality.Low))
        // GPT-5.4 的 low 预算反而高于 high；Astra high 的边长规则也不同于 5.6。
        assertEquals(4916L, tokens("gpt-5.4", 2048, 2048, ImageQuality.Low))
        assertEquals(3000L, tokens("gpt-5.4", 2048, 2048, ImageQuality.High))
        assertEquals(2458L, tokens("gpt-6-astra", 4096, 512, ImageQuality.High))
        assertEquals(615L, tokens("gpt-5.6-sol", 4096, 512, ImageQuality.High))
    }

    @Test
    fun modelQualityAndNullPatchDeletionFollowTheActualRequestOptions() {
        val configured = mProvider.copy(
            model = "gemini-4-carbon",
            protocol = LLMProviderProtocol.Gemini,
            requestBodyPatchJson = """{"generationConfig":{"mediaResolution":"MEDIA_RESOLUTION_LOW"},"_rpclient":{"imageQuality":"ultra_high"}}""",
        )
        val counter = mImages.resolve(configured.toConfig())
        val ordinary = image(1024, 1024)
        assertEquals(2240L, counter.count(listOf(ordinary)))
        val medium = configured.copy(
            requestBodyPatchJson = configured.requestBodyPatchJson.withImageQuality(ImageQuality.Medium)
        )
        assertEquals(560L, mImages.resolve(medium.toConfig()).count(listOf(ordinary)))
        assertEquals(2240L, counter.count(listOf(ordinary)))
        // Merge Patch 的 null 删除字段应回到模型默认值，而不是变成未知值。
        for (patch in listOf(
            """{"generationConfig":null}""",
            """{"generationConfig":{"mediaResolution":null}}"""
        )) {
            assertEquals(
                1120L,
                mImages.resolve(configured.copy(requestBodyPatchJson = patch).toConfig())
                    .count(listOf(ordinary))
            )
        }
        val legacy = mProvider.copy(
            model = "gemini-2.5-flash",
            protocol = LLMProviderProtocol.Gemini,
            requestBodyPatchJson = "{}".withImageQuality(ImageQuality.Low)
        )
        assertEquals(64L, mImages.resolve(legacy.toConfig()).count(listOf(ordinary)))
        assertTrue(mImages.resolve(legacy.toConfig()).usesApproximation)
    }

    @Test
    fun genericLargeImagesScaleWithAreaAndDowngradeOnlyImageContainingUsage() {
        val provider = mProvider.copy(model = "gpt-5.9")
        val tokenizer = mTokenizers.resolve(provider)
        val textOnly = LLMMessage(LLMMessageRole.User, "text")
        val illustrated =
            messageWithBlocks(LLMMessageRole.User, listOf(LLMContentBlock.Image(image(3072, 3072))))
        assertEquals(
            PromptTokenizerStrategy.ModelAware,
            tokenizer.strategyForMessages(listOf(textOnly))
        )
        assertEquals(
            PromptTokenizerStrategy.Estimated,
            tokenizer.strategyForMessages(listOf(illustrated))
        )
        assertTrue(tokenizer.countImages(illustrated.images) >= 11060L)
        assertTrue(
            tokenizer.countImages(listOf(image(6000, 6000))) > tokenizer.countImages(
                illustrated.images
            )
        )
        assertEquals(4096L, tokenizer.countImages(listOf(image(0, 0))))
    }
}
