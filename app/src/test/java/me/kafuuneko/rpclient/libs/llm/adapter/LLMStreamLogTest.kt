package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LLMStreamLogTest {
    /** 超过旧上限的普通请求不能继续在持久化前丢掉正文。 */
    @Test
    fun sanitizeLog_retainsLongTextAndUsesValidTruncationEnvelope() {
        val source = "{\"content\":\"" + "中".repeat(100_000) + "\"}"
        assertEquals(source, ImageLogSanitizer.sanitizeLog(source))
        val tooLarge = "{\"content\":\"" + "中".repeat(ImageLogSanitizer.MAX_LOG_CHARACTERS) + "\"}"
        val truncated = JsonParser.parseString(ImageLogSanitizer.sanitizeLog(tooLarge)).asJsonObject
        assertEquals("truncated", truncated.get("_rpclientLog").asString)
        assertTrue(truncated.get("redactedCharacters").asInt > ImageLogSanitizer.MAX_LOG_CHARACTERS)
    }

    /** 编码载荷仍被脱敏，但正文预算不再受错误提示的短上限控制。 */
    @Test
    fun sanitizeLog_keepsImageRedaction() {
        val source = "{\"image\":\"data:image/png;base64," + "A".repeat(2000) + "\"}"
        val safe = ImageLogSanitizer.sanitizeLog(source)
        assertFalse(safe.contains("A".repeat(256)))
        assertTrue(safe.contains("omitted"))
        JsonParser.parseString(safe)
    }

    /** 采集预算包含引号转义与数组包装，达到上限只丢弃整行并保留诊断标记。 */
    @Test
    fun streamLog_preservesWholeFramesAndReportsTruncation() {
        val log = LLMStreamLog()
        val frame = "data: {\"delta\":\"" + "中".repeat(2000) + "\"}"
        repeat(2500) { log.append(frame) }
        val text = log.toJson()
        val frames = JsonParser.parseString(text).asJsonArray
        assertTrue(text.length <= ImageLogSanitizer.MAX_LOG_CHARACTERS)
        assertTrue(frames.size() > 100)
        assertTrue(frames.toList().dropLast(1).all { it.asString == frame })
        assertEquals("truncated", frames.last().asJsonObject.get("_rpclientLog").asString)
        assertEquals(text, log.toJson())
    }

    /** 断流记录保留已采集行，并附上受脱敏约束的中断信息。 */
    @Test
    fun streamLog_retainsPartialFramesOnFailure() {
        val log = LLMStreamLog()
        log.append("data: {}")
        log.addError("Connection closed.")
        val frames = JsonParser.parseString(log.toJson()).asJsonArray
        assertEquals("data: {}", frames[0].asString)
        assertEquals("interrupted", frames[1].asJsonObject.get("_rpclientLog").asString)
    }
}
