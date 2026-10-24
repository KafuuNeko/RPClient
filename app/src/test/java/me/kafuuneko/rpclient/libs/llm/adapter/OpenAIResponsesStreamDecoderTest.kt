package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import me.kafuuneko.rpclient.libs.llm.GenerationFailure
import me.kafuuneko.rpclient.libs.llm.LLMEmptyResponseException
import me.kafuuneko.rpclient.libs.llm.LLMResponseException
import me.kafuuneko.rpclient.libs.llm.classifyGenerationFailure
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.llm.model.LLMReasoningKind
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIResponsesStreamDecoderTest {
    @Test
    fun deltasDoneItemsAndTerminalSnapshotsProduceTextExactlyOnce() {
        // 同一正文同时出现在增量、内容完成、Item 完成和响应完成中，只能展示一次。
        val decoder = OpenAIResponsesStreamDecoder()
        val parts = buildList {
            addAll(decoder.accept(delta("Hello")))
            addAll(decoder.accept(delta(" ")))
            addAll(decoder.accept(delta("world")))
            addAll(decoder.accept(event("""{"type":"response.output_text.done","output_index":0,"content_index":0,"text":"Hello world"}""")))
            assertFalse(decoder.isFinished)
            addAll(decoder.accept(event("""{"type":"response.output_item.done","output_index":0,"item":{"type":"message","content":[{"type":"output_text","text":"Hello world"}]}}""")))
            addAll(decoder.accept(terminal(response("Hello world"))))
        }
        assertEquals("Hello world", parts.filterIsInstance<LLMProviderStreamPart.Text>().joinToString("") { it.content })
        assertEquals(1, parts.filterIsInstance<LLMProviderStreamPart.Finished>().size)
        assertTrue(decoder.isFinished)
        decoder.requireFinished()
        assertTrue(decoder.accept(delta("ignored after completion")).isEmpty())
    }

    @Test
    fun terminalSnapshotRecoversMissingDeltasAndIncludesUsage() {
        val decoder = OpenAIResponsesStreamDecoder()
        decoder.accept(delta("Hello"))
        val parts = decoder.accept(terminal(response("Hello world")))
        assertEquals(" world", (parts.first() as LLMProviderStreamPart.Text).content)
        val finish = parts.last() as LLMProviderStreamPart.Finished
        assertEquals(12, finish.usage?.promptTokens)
        assertEquals(9, finish.usage?.completionTokens)
        assertEquals(3, finish.usage?.reasoningTokens)
        assertEquals(4, finish.usage?.cachedPromptTokens)
    }

    @Test
    fun summaryRefusalAndLiteralNullRemainDistinct() {
        val decoder = OpenAIResponsesStreamDecoder()
        val summary = decoder.accept(event("""{"type":"response.reasoning_summary_text.delta","output_index":0,"summary_index":0,"delta":"Plan"}""")).single()
        assertEquals(LLMReasoningKind.Summary, (summary as LLMProviderStreamPart.Reasoning).kind)
        val refusal = decoder.accept(event("""{"type":"response.refusal.delta","output_index":1,"content_index":0,"delta":"Cannot do that"}""")).single()
        assertEquals("Cannot do that", (refusal as LLMProviderStreamPart.Text).content)
        val literal = OpenAIResponsesResponseCodec.decode(response("null").toString(), "fallback", LLMProviderType.ChatGPT, false)
        assertEquals("null", literal.content)
    }

    @Test
    fun unknownEventsDoNotFinishAndPrematureDisconnectFails() {
        val decoder = OpenAIResponsesStreamDecoder()
        decoder.accept(delta("Partial"))
        assertTrue(decoder.accept(event("""{"type":"response.future_event","metadata":{}}""")).isEmpty())
        assertThrows(IOException::class.java) { decoder.requireFinished() }
        assertThrows(LLMResponseException::class.java) { decoder.accept(event("{broken")) }
    }

    @Test
    fun errorInsideSuccessfulHttpStreamIsSafelyClassified() {
        val decoder = OpenAIResponsesStreamDecoder()
        val error = assertThrows(LLMResponseException::class.java) {
            decoder.accept(event("""{"type":"error","code":"rate_limit_exceeded","message":"private payload"}"""))
        }
        assertEquals(GenerationFailure.RateLimited, classifyGenerationFailure(error))
        assertFalse(error.message.orEmpty().contains("private"))
        assertThrows(LLMResponseException::class.java) {
            decoder.accept(event("""{"type":"response.failed","response":{"status":"failed","error":{"code":"server_error"}}}"""))
        }
    }

    @Test
    fun incompleteOutputKeepsPartialTextAndNativeLengthReason() {
        val raw = response("Partial").apply {
            addProperty("status", "incomplete")
            add("incomplete_details", JsonObject().apply { addProperty("reason", "max_output_tokens") })
        }
        val complete = OpenAIResponsesResponseCodec.decode(raw.toString(), "fallback", LLMProviderType.ChatGPT, false)
        assertEquals("max_output_tokens", complete.finishReason)
        val decoder = OpenAIResponsesStreamDecoder()
        val parts = decoder.accept(terminal(raw, "response.incomplete"))
        assertEquals("Partial", (parts.first() as LLMProviderStreamPart.Text).content)
        assertEquals("max_output_tokens", (parts.last() as LLMProviderStreamPart.Finished).finishReason)
    }

    @Test
    fun reasoningOnlyAndPendingClientToolsAreNotSuccessfulReplies() {
        // 输出额度耗尽不能被推理或尚待执行的工具调用掩盖为空成功。
        val raw = response("").apply {
            addProperty("status", "incomplete")
            add("incomplete_details", JsonObject().apply { addProperty("reason", "max_output_tokens") })
        }
        val error = assertThrows(LLMEmptyResponseException::class.java) {
            OpenAIResponsesStreamDecoder().accept(terminal(raw, "response.incomplete"))
        }
        assertTrue(error.outputTokenLimitReached)
        val tool = response("").apply {
            add("output", JsonParser.parseString("""[{"type":"function_call","call_id":"call_1","name":"lookup","arguments":"{}"}]"""))
        }
        assertEquals("tool_output_required", assertThrows(LLMResponseException::class.java) {
            OpenAIResponsesResponseCodec.decode(tool.toString(), "fallback", LLMProviderType.ChatGPT, false)
        }.code)
    }

    @Test
    fun completedSnapshotAndStreamingMapperPreserveWhitespace() {
        val raw = response(" A\n B ")
        val decoder = OpenAIResponsesStreamDecoder()
        val mapper = LLMStreamPartMapper(false, true)
        val events = decoder.accept(terminal(raw)).flatMap(mapper::map)
        val result = OpenAIResponsesResponseCodec.decode(raw.toString(), "fallback", LLMProviderType.ChatGPT, false)
        assertEquals(result.content, events.filterIsInstance<LLMStreamEvent.Delta>().joinToString("") { it.content })
        assertEquals(" ", (mapper.map(LLMProviderStreamPart.Text(" ", "raw")).single() as LLMStreamEvent.Delta).content)
    }

    @Test
    fun inconsistentSnapshotsFailInsteadOfDuplicatingTheReply() {
        val decoder = OpenAIResponsesStreamDecoder()
        decoder.accept(delta("Hello"))
        assertThrows(LLMResponseException::class.java) { decoder.accept(terminal(response("Different"))) }
    }

    @Test
    fun commentaryIsNotSavedAsFinalAnswerForEitherTransport() {
        // 服务端工具执行前的进度说明与最终答复属于不同阶段。
        val commentary = """{"type":"message","phase":"commentary","content":[{"type":"output_text","text":"Searching"}]}"""
        val raw = response("Answer").apply {
            val finalAnswer = getAsJsonArray("output")[0]
            add("output", JsonParser.parseString("[$commentary,$finalAnswer]"))
        }
        val decoder = OpenAIResponsesStreamDecoder()
        decoder.accept(event("""{"type":"response.output_item.added","output_index":0,"item":$commentary}"""))
        assertTrue(decoder.accept(delta("Searching")).isEmpty())
        decoder.accept(event("""{"type":"response.output_item.done","output_index":0,"item":$commentary}"""))
        val parts = decoder.accept(terminal(raw))
        assertEquals("Answer", parts.filterIsInstance<LLMProviderStreamPart.Text>().joinToString("") { it.content })
        assertEquals("Answer", OpenAIResponsesResponseCodec.decode(raw.toString(), "fallback", LLMProviderType.ChatGPT, false).content)
    }

    @Test
    fun finalOutputCannotSilentlyOmitPreviouslyEmittedText() {
        val decoder = OpenAIResponsesStreamDecoder()
        decoder.accept(delta("Unexpected"))
        val raw = response("").apply { add("output", JsonParser.parseString("[]")) }
        assertThrows(LLMResponseException::class.java) { decoder.accept(terminal(raw)) }
    }

    private fun event(json: String) = SseEvent("", json)

    private fun delta(text: String) = event(JsonObject().apply {
        addProperty("type", "response.output_text.delta")
        addProperty("output_index", 0)
        addProperty("content_index", 0)
        addProperty("delta", text)
    }.toString())

    private fun terminal(response: JsonObject, type: String = "response.completed") = event(JsonObject().apply {
        addProperty("type", type)
        add("response", response)
    }.toString())

    private fun response(text: String): JsonObject = JsonParser.parseString("""{
        "id":"resp_1","object":"response","status":"completed","model":"routed-model",
        "output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":""}]}],
        "usage":{"input_tokens":12,"output_tokens":9,"total_tokens":21,
          "input_tokens_details":{"cached_tokens":4},"output_tokens_details":{"reasoning_tokens":3}}
    }""").asJsonObject.apply {
        getAsJsonArray("output")[0].asJsonObject.getAsJsonArray("content")[0].asJsonObject.addProperty("text", text)
    }
}
