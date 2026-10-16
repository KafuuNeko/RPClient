package me.kafuuneko.rpclient.libs.llm.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseEventDecoderTest {
    @Test
    fun multilineFramesPreserveDataAndIgnoreHeartbeatMetadata() {
        val decoder = SseEventDecoder()
        assertNull(decoder.accept("\uFEFF: heartbeat"))
        assertNull(decoder.accept("event: response.completed"))
        assertNull(decoder.accept("id: 1"))
        assertNull(decoder.accept("retry: 1000"))
        assertNull(decoder.accept("data: {\"type\":\"response.completed\","))
        assertNull(decoder.accept("data:  \"response\":{}}"))
        assertEquals(SseEvent("response.completed", "{\"type\":\"response.completed\",\n \"response\":{}}"), decoder.accept(""))
        assertNull(decoder.accept(""))
        decoder.accept("data: {}")
        assertEquals(SseEvent("", "{}"), decoder.finish())
    }
}
