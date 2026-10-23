package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.kafuuneko.rpclient.libs.llm.model.LLMContentBlock
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
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

class OpenAIResponsesRequestCodecTest {
    private val provider = LLMProviderConfig(
        name = "OpenAI",
        providerType = LLMProviderType.ChatGPT,
        protocol = LLMProviderProtocol.OpenAIResponses,
        baseUrl = "https://example.invalid/v1",
        model = "gpt-6-luna",
    )
    private val image = LLMImageReference(
        uuid = "image",
        mimeType = "image/png",
        width = 20,
        height = 10,
        byteCount = 80,
    )

    @Test
    fun nativeInputPreservesMidConversationInstructionsAssistantPhaseAndImageOrder() {
        // 中途插入的世界书指令和图片前后文本必须保持最终 Prompt 的位置。
        val messages = listOf(
            LLMMessage(LLMMessageRole.System, "Main"),
            LLMMessage(LLMMessageRole.Assistant, "Hello"),
            LLMMessage(LLMMessageRole.System, "World book"),
            LLMMessage(
                role = LLMMessageRole.User,
                content = "projection",
                blocks = listOf(
                    LLMContentBlock.Text(" before "),
                    LLMContentBlock.Image(image),
                    LLMContentBlock.Text(" after "),
                ),
            ),
        )
        val encoded = encode(
            request = LLMGenerationRequest(messages = messages, captureReasoning = true),
            stream = true,
        )
        // 使用 Responses 独立字段，不复用 Chat Completions 的消息或用量开关。
        val input = encoded.getAsJsonArray("input")
        assertEquals(listOf("system", "assistant", "system", "user"), input.map { it.asJsonObject["role"].asString })
        assertEquals("final_answer", input[1].asJsonObject["phase"].asString)
        val content = input[3].asJsonObject.getAsJsonArray("content")
        assertEquals(
            listOf("input_text", "input_image", "input_text"),
            content.map { it.asJsonObject["type"].asString },
        )
        assertEquals(" before ", content[0].asJsonObject["text"].asString)
        assertEquals("registered-image", content[1].asJsonObject["image_url"].asString)
        assertFalse(encoded["store"].asBoolean)
        assertEquals("disabled", encoded["truncation"].asString)
        assertEquals("auto", encoded.getAsJsonObject("reasoning")["summary"].asString)
        assertTrue(encoded.has("max_output_tokens"))
        assertFalse(encoded.has("messages"))
        assertFalse(encoded.has("stream_options"))
    }

    @Test
    fun reasoningAndSamplingAreResolvedAfterTheUserPatch() {
        val request = LLMGenerationRequest(listOf(LLMMessage(LLMMessageRole.User, "Hi")))
        // 高级扩展里的 logprobs 同样受推理能力约束，其他 include 项仍需保留。
        val reasoning = encode(
            request = request,
            patch = """{"reasoning":{"effort":"high"},"top_logprobs":3,
            "include":["message.output_text.logprobs","web_search_call.action.sources"]}""",
        )
        assertFalse(reasoning.has("temperature"))
        assertFalse(reasoning.has("top_p"))
        assertFalse(reasoning.has("top_logprobs"))
        assertEquals(listOf("web_search_call.action.sources"), reasoning.getAsJsonArray("include").map { it.asString })
        val noReasoning = encode(request = request, patch = """{"reasoning":{"effort":"none"}}""")
        assertTrue(noReasoning.has("temperature"))
        assertTrue(noReasoning.has("top_p"))
        val unknown = encode(request = request, model = "custom-model")
        assertTrue(unknown.has("temperature"))
        val oldModel = encode(request = request.copy(captureReasoning = true), model = "gpt-4o")
        assertFalse(oldModel.has("reasoning"))
    }

    @Test
    fun explicitSummaryAndStructuredOutputRemainUserConfigurable() {
        // 用户扩展可以控制原生功能，但不会覆盖本地维护的历史与生命周期。
        val request = LLMGenerationRequest(
            messages = listOf(LLMMessage(LLMMessageRole.User, "Hi")),
            captureReasoning = true,
        )
        val result = encode(
            request = request,
            patch = """{
            "reasoning":{"summary":"detailed"},
            "text":{"format":{"type":"json_schema","name":"result","strict":true,
              "schema":{"type":"object","properties":{},"required":[],"additionalProperties":false}}},
            "tools":[{"type":"web_search"}]
        }""",
        )
        assertEquals("detailed", result.getAsJsonObject("reasoning")["summary"].asString)
        assertEquals("json_schema", result.getAsJsonObject("text").getAsJsonObject("format")["type"].asString)
        assertEquals("web_search", result.getAsJsonArray("tools")[0].asJsonObject["type"].asString)
        val removed = encode(request = request, patch = """{"reasoning":{"summary":null}}""")
        assertFalse(removed.getAsJsonObject("reasoning").has("summary"))
    }

    @Test
    fun patchCannotChangeHistoryLifecycleOrInjectChatCompletionsFields() {
        val paths = protectedRequestBodyPaths(LLMProviderProtocol.OpenAIResponses)
        listOf(
            "input",
            "instructions",
            "store",
            "background",
            "previous_response_id",
            "conversation",
            "prompt",
            "truncation",
            "messages",
            "max_tokens",
            "max_completion_tokens",
            "stop",
            "reasoning_effort",
        ).forEach { field ->
            val patch = JsonObject().apply { add(field, JsonNull.INSTANCE) }
            assertTrue(field, validateRequestBodyPatch(patch.toString(), paths).isFailure)
        }
        assertTrue(validateRequestBodyPatch("""{"stream_options":{"include_usage":true}}""", paths).isFailure)
    }

    @Test
    fun unsupportedStopAndSystemImagesFailBeforeNetworkAccess() {
        assertThrows(IllegalArgumentException::class.java) {
            encode(LLMGenerationRequest(messages = emptyList(), options = LLMGenerationOptions(stop = listOf("END"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            encode(
                request = LLMGenerationRequest(
                    messages = listOf(
                        element = LLMMessage(
                            role = LLMMessageRole.System,
                    content = "",
                    blocks = listOf(LLMContentBlock.Image(image)),
                        ),
                    ),
                ),
            )
        }
    }

    private fun encode(
        request: LLMGenerationRequest,
        model: String = provider.model,
        patch: String = "{}",
        stream: Boolean = false,
    ) = JsonParser.parseString(
        OpenAIResponsesRequestCodec.encode(
            provider = provider.copy(model = model, requestBodyPatchJson = patch),
            request = request,
            model = model,
            stream = stream,
        ) { "registered-image" },
    ).asJsonObject
}
