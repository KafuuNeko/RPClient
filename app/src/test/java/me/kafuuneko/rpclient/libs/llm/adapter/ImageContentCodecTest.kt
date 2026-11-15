package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.kafuuneko.rpclient.libs.llm.ImageRequestException
import me.kafuuneko.rpclient.libs.llm.ImageRequestFailure
import me.kafuuneko.rpclient.libs.llm.image.ImageTokenEstimatorRegistry
import me.kafuuneko.rpclient.libs.llm.model.ImageQuality
import me.kafuuneko.rpclient.libs.llm.model.ImageTokenEstimatorType
import me.kafuuneko.rpclient.libs.llm.model.LLMContentBlock
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 保护通用画质设置在持久化、协议编码、模型限制与预算之间的一致性。 */
class ImageContentCodecTest {
    private val mProvider = LLMProviderConfig(
        name = "test", providerType = LLMProviderType.ChatGPT,
        protocol = LLMProviderProtocol.OpenAIResponses,
        baseUrl = "https://example.invalid", model = "gpt-6-astra",
    )
    private val mImage = LLMImageReference("image", "image/png", 2048, 2048, 100)

    @Test
    fun modelQualityUsesTheSameValuesInWireAndEstimateWithoutChangingTheResource() {
        val low = mProvider.copy(requestBodyPatchJson = "{}".withImageQuality(ImageQuality.Low))
        val counter = ImageTokenEstimatorRegistry().resolve(low)
        assertEquals("low", ImageContentCodec(low).encode(mImage, "marker")["detail"].asString)
        assertEquals(308L, counter.count(listOf(mImage)))
        val high = low.copy(requestBodyPatchJson = "{}".withImageQuality(ImageQuality.High))
        assertEquals("high", ImageContentCodec(high).encode(mImage, "marker")["detail"].asString)
        assertEquals(3000L, ImageTokenEstimatorRegistry().resolve(high).count(listOf(mImage)))
        assertEquals(308L, counter.count(listOf(mImage)))
        val compatible =
            ImageContentCodec(low.copy(protocol = LLMProviderProtocol.OpenAICompatible))
        assertEquals(
            "low",
            compatible.encode(mImage, "marker").getAsJsonObject("image_url")["detail"].asString
        )
    }

    @Test
    fun clientOnlyPreferencesSurviveEditingButNeverReachAnyProtocol() {
        val initial = """{"reasoning":{"effort":"low"},"provider":{"order":["anthropic"]}}"""
        val patch = initial.withImageQuality(ImageQuality.UltraHigh)
        for (protocol in LLMProviderProtocol.entries) {
            val merged = mergeRequestBodyJson(
                "{}",
                patch,
                protectedRequestBodyPaths(protocol),
                protocol = protocol
            )
            val json = JsonParser.parseString(merged).asJsonObject
            assertFalse(json.has(CLIENT_IMAGE_OPTIONS_KEY))
            assertEquals("low", json.getAsJsonObject("reasoning")["effort"].asString)
            assertEquals(
                "anthropic",
                json.getAsJsonObject("provider").getAsJsonArray("order")[0].asString
            )
        }
        assertFalse(
            JsonParser.parseString(patch.withImageQuality(ImageQuality.Auto)).asJsonObject.has(
                CLIENT_IMAGE_OPTIONS_KEY
            )
        )
        // 非法通用枚举在表单校验时被拒绝，发送与估算不能各自猜测默认值。
        for (bad in listOf(
            """{"_rpclient":[]}""",
            """{"_rpclient":{"imageQuality":2}}""",
            """{"_rpclient":{"imageQuality":"typo"}}"""
        )) {
            assertTrue(validateRequestBodyPatch(bad, emptySet()).isFailure)
        }
    }

    @Test
    fun geminiUsesPartFieldsForModernModelsAndGlobalFieldsForLegacyModels() {
        val patch = """{"generationConfig":{"mediaResolution":"MEDIA_RESOLUTION_LOW"}}"""
        val modern = mProvider.copy(
            protocol = LLMProviderProtocol.Gemini, model = "gemini-4-carbon",
            requestBodyPatchJson = patch.withImageQuality(ImageQuality.UltraHigh),
        )
        val part = ImageContentCodec(modern).encode(mImage, "marker")
        assertEquals(
            "MEDIA_RESOLUTION_ULTRA_HIGH",
            part.getAsJsonObject("mediaResolution")["level"].asString
        )
        assertEquals(2240L, ImageTokenEstimatorRegistry().resolve(modern).count(listOf(mImage)))
        // Auto 必须继承现有全局参数，不因展示自动选项而覆盖用户原先的低档位。
        val automatic =
            modern.copy(requestBodyPatchJson = patch.withImageQuality(ImageQuality.Auto))
        assertFalse(ImageContentCodec(automatic).encode(mImage, "marker").has("mediaResolution"))
        assertEquals(280L, ImageTokenEstimatorRegistry().resolve(automatic).count(listOf(mImage)))
        val legacy = modern.copy(
            model = "gemini-2.5-flash",
            requestBodyPatchJson = patch.withImageQuality(ImageQuality.Low)
        )
        val payload = JsonObject().apply {
            add("generationConfig", JsonObject().apply { addProperty("maxOutputTokens", 1000) })
        }.applyLegacyGeminiImageOptions(legacy)
        assertFalse(ImageContentCodec(legacy).encode(mImage, "marker").has("mediaResolution"))
        assertEquals(
            "MEDIA_RESOLUTION_LOW",
            payload.getAsJsonObject("generationConfig")["mediaResolution"].asString
        )
        assertEquals(1000, payload.getAsJsonObject("generationConfig")["maxOutputTokens"].asInt)
        assertEquals(64L, ImageTokenEstimatorRegistry().resolve(legacy).count(listOf(mImage)))
    }

    @Test
    fun patchRejectionBoundaryAndUnsupportedQualityFollowTheActualModel() {
        val codec =
            ImageContentCodec(mProvider.copy(imageTokenEstimatorType = ImageTokenEstimatorType.Gemini3))
        val boundary = mImage.copy(width = 6400, height = 4800)
        assertEquals("auto", codec.encode(boundary, "marker")["detail"].asString)
        val tooLarge = assertThrows(ImageRequestException::class.java) {
            codec.encode(boundary.copy(height = 4801), "marker")
        }
        assertEquals(ImageRequestFailure.TooLarge, tooLarge.failure)
        // high 使用缩放预算；手选较低估算类别不能绕过 auto 的单图拒绝条件。
        val high = mProvider.copy(requestBodyPatchJson = "{}".withImageQuality(ImageQuality.High))
        assertEquals(
            "high",
            ImageContentCodec(high).encode(
                mImage.copy(width = 6000, height = 6000),
                "marker"
            )["detail"].asString
        )
        val unsupported = assertThrows(ImageRequestException::class.java) {
            ImageContentCodec(
                mProvider.copy(
                    model = "gpt-4o",
                    requestBodyPatchJson = "{}".withImageQuality(ImageQuality.Original)
                )
            ).encode(mImage, "marker")
        }
        assertEquals(ImageRequestFailure.UnsupportedDetail, unsupported.failure)
        val grok = mProvider.copy(providerType = LLMProviderType.Grok, model = "grok-4.7")
        assertEquals(
            ImageRequestFailure.UnsupportedFormat,
            assertThrows(ImageRequestException::class.java) {
                ImageContentCodec(grok).encode(mImage.copy(mimeType = "image/webp"), "marker")
            }.failure
        )
    }

    @Test
    fun responseEncodingUsesTheRequestModelOverrideAndOmitsClientMetadata() {
        val messages =
            listOf(LLMMessage(LLMMessageRole.User, "", listOf(LLMContentBlock.Image(mImage))))
        val configured = mProvider.copy(
            model = "gpt-4o",
            requestBodyPatchJson = "{}".withImageQuality(ImageQuality.Original)
        )
        val result = JsonParser.parseString(
            OpenAIResponsesRequestCodec.encode(
                configured,
                LLMGenerationRequest(messages, model = "gpt-6-astra"),
                "gpt-6-astra",
                false,
                imageUrl = { "marker" },
            )
        ).asJsonObject
        assertFalse(result.has(CLIENT_IMAGE_OPTIONS_KEY))
        assertEquals(
            "original",
            result.getAsJsonArray("input")[0].asJsonObject.getAsJsonArray("content")[0].asJsonObject["detail"].asString
        )
    }

    @Test
    fun geminiRestAliasesShareCanonicalFieldsAndCannotBypassPromptProtection() {
        val protocol = LLMProviderProtocol.Gemini
        val patch = """{"generation_config":{"media_resolution":"MEDIA_RESOLUTION_LOW"}}"""
        val result = JsonParser.parseString(
            mergeRequestBodyJson(
                "{}",
                patch,
                protectedRequestBodyPaths(protocol),
                protocol = protocol
            )
        ).asJsonObject
        assertFalse(result.has("generation_config"))
        assertEquals(
            "MEDIA_RESOLUTION_LOW",
            result.getAsJsonObject("generationConfig")["mediaResolution"].asString
        )
        val provider = mProvider.copy(
            model = "gemini-4-carbon",
            protocol = protocol,
            requestBodyPatchJson = patch
        )
        assertEquals(280L, ImageTokenEstimatorRegistry().resolve(provider).count(listOf(mImage)))
        // 同义字段也须遵守输出参数和系统消息保护，冲突字段不能依赖解析顺序。
        val invalid = listOf(
            """{"generation_config":{"max_output_tokens":2000}}""",
            """{"generationConfig":{"top_p":0.5}}""",
            """{"system_instruction":{"parts":[{"text":"unbudgeted"}]}}""",
            """{"generationConfig":{},"generation_config":{}}""",
        )
        invalid.forEach { assertTrue(it, validateRequestBodyPatch(it, protocol).isFailure) }
    }

    @Test
    fun compatibleProtocolsDoNotAssumeNativeQualityMappingsForThirdPartyModels() {
        val compatibility = mProvider.copy(
            protocol = LLMProviderProtocol.OpenAICompatible, model = "google/gemini-4-carbon",
            requestBodyPatchJson = "{}".withImageQuality(ImageQuality.UltraHigh),
        )
        assertFalse(
            ImageContentCodec(compatibility).encode(mImage, "marker").getAsJsonObject("image_url")
                .has("detail")
        )
        val counter = ImageTokenEstimatorRegistry().resolve(compatibility)
        assertEquals(1120L, counter.count(listOf(mImage)))
        assertTrue(counter.usesApproximation)
        val native = compatibility.copy(protocol = LLMProviderProtocol.Gemini)
        assertEquals(2240L, ImageTokenEstimatorRegistry().resolve(native).count(listOf(mImage)))
        assertEquals(
            "MEDIA_RESOLUTION_ULTRA_HIGH",
            ImageContentCodec(native).encode(mImage, "marker")
                .getAsJsonObject("mediaResolution")["level"].asString
        )
    }
}
