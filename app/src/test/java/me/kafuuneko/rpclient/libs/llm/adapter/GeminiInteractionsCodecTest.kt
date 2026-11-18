package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonParser
import me.kafuuneko.rpclient.libs.llm.LLMEmptyResponseException
import me.kafuuneko.rpclient.libs.llm.LLMResponseException
import me.kafuuneko.rpclient.libs.llm.model.LLMContentBlock
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.llm.model.LLMUsage
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 以稳定版 REST 和 SSE 的原生步骤约束普通回复、历史顺序和结束条件。 */
class GeminiInteractionsCodecTest {
    private val mProvider = LLMProviderConfig(
        name = "fixture",
        providerType = LLMProviderType.Gemini,
        protocol = LLMProviderProtocol.GeminiInteractions,
        baseUrl = "https://example.invalid/custom/v1",
        model = "fixture-model",
        requestBodyPatchJson = """{"generation_config":{"seed":42}}""",
    )

    /** 系统指令只提升连续前缀，私有图片标识不进入模型输入。 */
    @Test
    fun requestPreservesStepsAndContentOrderWithLocalHistory() {
        val messages = listOf(
            LLMMessage(LLMMessageRole.System, "provider"),
            LLMMessage(LLMMessageRole.System, "context"),
            LLMMessage(LLMMessageRole.User, "  user\n"),
            LLMMessage(LLMMessageRole.System, "depth"),
            LLMMessage(
                LLMMessageRole.Assistant, "caption", listOf(
                    LLMContentBlock.Image(LLMImageReference("private-id", "image/png", 10, 10, 20)),
                    LLMContentBlock.Text("caption"),
                )
            ),
        )
        // 对比整棵树，额外的工具、服务端存储或旧采样字段都会使断言失败。
        for (stream in listOf(false, true)) {
            val request = LLMGenerationRequest(
                messages,
                options = LLMGenerationOptions(maxTokens = 128, stop = listOf("END")),
                captureReasoning = true
            )
            val wire =
                GeminiInteractionsRequestCodec.encode(mProvider, request, stream) { "image-marker" }
            val expected = """{
                "model":"fixture-model","stream":$stream,"store":false,"system_instruction":"provider\n\ncontext",
                "input":[
                  {"type":"user_input","content":[{"type":"text","text":"  user\n"}]},
                  {"type":"user_input","content":[{"type":"text","text":"depth"}]},
                  {"type":"model_output","content":[{"type":"image","mime_type":"image/png","data":"image-marker"},{"type":"text","text":"caption"}]}
                ],
                "generation_config":{"seed":42,"stop_sequences":["END"],"max_output_tokens":128,"thinking_summaries":"auto"}
            }"""
            assertEquals(JsonParser.parseString(expected), JsonParser.parseString(wire))
        }
    }

    /** 高级扩展不能恢复服务端存储、切换托管 Agent 或替换原生历史。 */
    @Test
    fun advancedPatchCannotChangeOwnershipOrReplaceGenerationConfig() {
        for (key in listOf(
            "model",
            "store",
            "stream",
            "input",
            "steps",
            "system_instruction",
            "previous_interaction_id",
            "background",
            "agent",
            "agent_config",
            "continuation_token",
            "generation_config"
        )) {
            assertTrue(
                key,
                validateRequestBodyPatch("""{"$key":null}""", mProvider.protocol).isFailure
            )
        }
        assertTrue(
            validateRequestBodyPatch(
                """{"generation_config":{"max_output_tokens":7}}""",
                mProvider.protocol
            ).isFailure
        )
    }

    /** 最终内容仅来自展示块，服务端实际模型及缓存、思考计数独立保留。 */
    @Test
    fun httpAndInterleavedStreamProduceEquivalentTextAndUsage() {
        val usage =
            """{"total_input_tokens":7,"total_output_tokens":20,"total_thought_tokens":22,"total_tokens":49,"total_cached_tokens":4}"""
        val metadata = """"status":"completed","model":"served","usage":$usage"""
        val raw = """{$metadata,"steps":[
            {"type":"thought","signature":"private","summary":[{"type":"text","text":"summary"}]},
            {"type":"model_output","content":[{"type":"text","text":" "},{"type":"text","text":"null\n"}]}
        ]}"""
        val http = GeminiInteractionsResponseCodec.decode(raw, mProvider, includeReasoning = false)
        assertEquals(" null\n", http.content)
        assertEquals("summary", http.reasoningContent)
        assertEquals(LLMUsage(7, 42, 49, 4, 22), http.usage)
        // 后一步先到达的正文须待前一步摘要完成，并保留纯空白增量。
        val frames = listOf(
            "step.start" to """{"index":0,"step":{"type":"thought"}}""",
            "step.start" to """{"index":1,"step":{"type":"model_output"}}""",
            "step.delta" to """{"index":1,"delta":{"type":"text","text":" "}}""",
            "step.delta" to """{"index":0,"delta":{"type":"thought_signature","signature":"private"}}""",
            "step.delta" to """{"index":0,"delta":{"type":"thought_summary","content":{"type":"text","text":"summary"}}}""",
            "step.stop" to """{"index":0}""",
            "step.delta" to """{"index":1,"delta":{"type":"text","text":"null\n"}}""",
            "step.stop" to """{"index":1}""",
            "interaction.completed" to """{"interaction":{$metadata}}""",
        )
        val decoder = GeminiInteractionsStreamDecoder()
        val parts = frames.flatMap { (type, json) -> decoder.accept(SseEvent(type, json)) }
        decoder.requireFinished()
        assertEquals(
            http.reasoningContent,
            (parts.first() as LLMProviderStreamPart.Reasoning).content
        )
        assertEquals(
            http.content,
            parts.filterIsInstance<LLMProviderStreamPart.Text>().joinToString("") { it.content })
        // 聊天内联思考摘要时，两种传输也必须在同一位置展开和闭合标签。
        val mapper = LLMStreamPartMapper(includeReasoningInContent = true, captureReasoning = true)
        val visible = parts.flatMap(mapper::map).filterIsInstance<LLMStreamEvent.Delta>()
            .joinToString("") { it.content }
        assertEquals("<think>\nsummary\n</think>\n\n null\n", visible)
        assertEquals(
            visible,
            GeminiInteractionsResponseCodec.decode(raw, mProvider, includeReasoning = true).content
        )
        val end = parts.last() as LLMProviderStreamPart.Finished
        assertEquals(http.model, end.model)
        assertEquals(http.usage, end.usage)
        assertEquals(http.finishReason, end.finishReason)
    }

    /** 未结束步骤、断流、服务端错误和工具请求都不能伪装成成功的普通回复。 */
    @Test
    fun interruptedAndNonTextTurnsFailExplicitly() {
        val decoder = GeminiInteractionsStreamDecoder()
        decoder.accept(SseEvent("step.start", """{"index":0,"step":{"type":"model_output"}}"""))
        decoder.accept(
            SseEvent(
                "step.delta",
                """{"index":0,"delta":{"type":"text","text":"partial"}}"""
            )
        )
        assertFalse(decoder.isFinished)
        assertThrows(IOException::class.java) { decoder.requireFinished() }
        assertThrows(LLMResponseException::class.java) {
            decoder.accept(
                SseEvent(
                    "interaction.completed",
                    """{"interaction":{"status":"completed"}}"""
                )
            )
        }
        assertThrows(LLMResponseException::class.java) {
            decoder.accept(
                SseEvent(
                    "error",
                    """{"error":{"code":"invalid_api_key"}}"""
                )
            )
        }
        for (status in listOf(
            "incomplete",
            "failed",
            "cancelled",
            "in_progress",
            "requires_action",
            "requires_continuation"
        )) {
            val raw =
                """{"status":"$status","steps":[{"type":"model_output","content":[{"type":"text","text":"progress"}]}]}"""
            assertThrows(LLMResponseException::class.java) {
                GeminiInteractionsResponseCodec.decode(
                    raw,
                    mProvider,
                    false
                )
            }
        }
        val thought =
            """{"status":"completed","steps":[{"type":"thought","signature":"private"}]}"""
        assertThrows(LLMEmptyResponseException::class.java) {
            GeminiInteractionsResponseCodec.decode(
                thought,
                mProvider,
                true
            )
        }
    }
}
