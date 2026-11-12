package me.kafuuneko.rpclient.libs.llm.adapter

import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnthropicMessagesUsageTest {
    @Test
    fun inputTotalIncludesCacheWritesAndReadsWithoutCombiningTheirDetails() {
        val usage = parseAnthropicUsage(
            """{"usage":{"input_tokens":512,"output_tokens":64,"cache_creation_input_tokens":4096,"cache_read_input_tokens":8192}}""",
        )!!

        assertEquals(12800, usage.promptTokens)
        assertEquals(64, usage.completionTokens)
        assertEquals(12864, usage.totalTokens)
        assertEquals(8192, usage.cachedPromptTokens)
        assertEquals(4096, usage.cacheCreationPromptTokens)
    }

    @Test
    fun writingCacheWithNoHitPreservesZeroReads() {
        val usage = parseAnthropicUsage(
            """{"usage":{"input_tokens":10,"output_tokens":20,"cache_creation_input_tokens":4096,"cache_read_input_tokens":0}}""",
        )!!

        assertEquals(4106, usage.promptTokens)
        assertEquals(4126, usage.totalTokens)
        assertEquals(0, usage.cachedPromptTokens)
        assertEquals(4096, usage.cacheCreationPromptTokens)
    }

    @Test
    fun missingCacheDetailsRemainUnknown() {
        val usage = parseAnthropicUsage(
            """{"usage":{"input_tokens":10,"output_tokens":20}}""",
        )!!

        assertEquals(30, usage.totalTokens)
        assertNull(usage.cachedPromptTokens)
        assertNull(usage.cacheCreationPromptTokens)
        assertNull(parseAnthropicUsage("""{"usage":{}}"""))
    }

    @Test
    fun partialUsageCanReportOnlyOneCacheCategory() {
        val write = parseAnthropicUsage("""{"usage":{"cache_creation_input_tokens":4096}}""")!!
        val read = parseAnthropicUsage("""{"usage":{"cache_read_input_tokens":8192}}""")!!

        assertEquals(4096, write.promptTokens)
        assertNull(write.cachedPromptTokens)
        assertEquals(4096, write.cacheCreationPromptTokens)
        assertNull(write.totalTokens)
        assertEquals(8192, read.promptTokens)
        assertEquals(8192, read.cachedPromptTokens)
        assertNull(read.cacheCreationPromptTokens)
    }

    @Test
    fun streamingOutputUpdatesPreserveCacheDetailsAndRecomputeTotal() {
        val mapper =
            LLMStreamPartMapper(includeReasoningInContent = false, captureReasoning = false)
        // 起始事件包含缓存细分，后续 message_delta 只更新累计输出。
        val lines = listOf(
            """data: {"type":"message_start","message":{"model":"claude-model","usage":{"input_tokens":10,"output_tokens":1,"cache_creation_input_tokens":4096,"cache_read_input_tokens":8192}}}""",
            """data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":20}}""",
            """data: {"type":"message_stop"}""",
        )
        val events = lines.flatMap { line ->
            parseAnthropicStreamParts(line).flatMap(mapper::map)
        } + mapper.finish()
        val finished = events.filterIsInstance<LLMStreamEvent.Finished>().single()
        val usage = finished.usage!!

        assertEquals("claude-model", finished.model)
        assertEquals(12298, usage.promptTokens)
        assertEquals(20, usage.completionTokens)
        assertEquals(12318, usage.totalTokens)
        assertEquals(8192, usage.cachedPromptTokens)
        assertEquals(4096, usage.cacheCreationPromptTokens)
    }
}
