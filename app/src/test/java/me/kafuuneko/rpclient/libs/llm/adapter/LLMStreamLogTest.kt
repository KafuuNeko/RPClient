package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonParser
import me.kafuuneko.rpclient.libs.json.JsonDocumentCodec
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

    /** 采集预算包含最终序列化结构，达到上限只丢弃整帧并保留诊断标记。 */
    @Test
    fun streamLog_preservesWholeFramesAndReportsTruncation() {
        val log = LLMStreamLog()
        val frame = "data: {\"delta\":\"" + "中".repeat(2000) + "\"}"
        repeat(2500) {
            log.append(frame)
            log.append("")
        }
        // 预算命中后不能追加半个对象，重复取出仍应得到同一份结果。
        val text = log.toJson()
        val frames = JsonParser.parseString(text).asJsonArray
        assertTrue(text.length <= ImageLogSanitizer.MAX_LOG_CHARACTERS)
        assertTrue(frames.size() > 100)
        assertTrue(
            frames.toList().dropLast(1)
                .all { it.asJsonObject.get("delta").asString == "中".repeat(2000) })
        assertEquals("truncated", frames.last().asJsonObject.get("_rpclientLog").asString)
        assertEquals(text, log.toJson())
    }

    /** 断流时提交没有分隔空行的尾帧，并附上受脱敏约束的中断信息。 */
    @Test
    fun streamLog_retainsPartialFramesOnFailure() {
        val log = LLMStreamLog()
        log.append("data: {}")
        log.addError("Connection closed.")
        val frames = JsonParser.parseString(log.toJson()).asJsonArray
        assertEquals("{}", frames[0].toString())
        assertEquals("interrupted", frames[1].asJsonObject.get("_rpclientLog").asString)
    }

    /** 常见流式对象直接保存，心跳和分隔空行不形成空字符串。 */
    @Test
    fun streamLog_storesObjectsAndAnExplicitDoneRecord() {
        val log = LLMStreamLog()
        listOf(
            "\uFEFF: heartbeat", "", "data: {\"content\":\"Hello\"}", "", "",
            "data: {\"content\":\"世界😀\"}", "", "data: [DONE]", ""
        ).forEach(log::append)
        val result = JsonParser.parseString(log.toJson()).asJsonArray
        // 分隔空行与心跳应被忽略，只保留两条载荷和一条结束记录。
        assertEquals(3, result.size())
        assertEquals("Hello", result[0].asJsonObject.get("content").asString)
        assertEquals("世界😀", result[1].asJsonObject.get("content").asString)
        assertEquals("done", result[2].asJsonObject.get("_rpclientLog").asString)
    }

    /** 多行 data 先组帧，元数据独立包装，不能覆盖服务端对象的同名字段。 */
    @Test
    fun streamLog_preservesMultilineDataAndEventMetadata() {
        val log = LLMStreamLog()
        listOf(
            "event: response.delta",
            "id: transport-id",
            "retry: 1000",
            "data: {\"id\":\"payload-id\",",
            "data:  \"n\":1}",
            "",
            "data: []"
        ).forEach(log::append)
        val result = JsonParser.parseString(log.toJson()).asJsonArray
        // 后一帧没有显式元数据，不能继承前一帧的包装。
        val event = result[0].asJsonObject
        assertEquals("event", event.get("_rpclientLog").asString)
        assertEquals("response.delta", event.get("event").asString)
        assertEquals("transport-id", event.get("id").asString)
        assertEquals("1000", event.get("retry").asString)
        assertEquals("payload-id", event.getAsJsonObject("payload").get("id").asString)
        assertEquals(1, event.getAsJsonObject("payload").get("n").asInt)
        assertTrue(result[1].isJsonArray)
    }

    /** 合法载荷不经 DOM 重建，重复键、大整数、负零和指数表示均保留。 */
    @Test
    fun streamLog_preservesJsonLexemesAndNativeScalarTypes() {
        val log = LLMStreamLog()
        val payload = """{"x":1,"x":2,"large":9007199254740993,"n":-0,"e":1e+09}"""
        log.append("data: $payload")
        log.append("")
        listOf("null", "true", "42", "\"text\"").forEach { value ->
            log.append("data: $value")
            log.append("")
        }
        assertEquals("[$payload,null,true,42,\"text\"]", log.toJson())
        JsonDocumentCodec.validate(log.toJson())
    }

    /** 非 JSON 载荷、空 data 和非 SSE 文本明确保留为原文记录。 */
    @Test
    fun streamLog_keepsMalformedDataAndRawLines() {
        val log = LLMStreamLog()
        listOf(
            "data: {\"unfinished\":",
            "",
            "data:",
            "",
            "unexpected upstream error"
        ).forEach(log::append)
        val result = JsonParser.parseString(log.toJson()).asJsonArray
        // 异常载荷仍保留可定位的原文，不能被空 data 或未知行静默覆盖。
        assertEquals(3, result.size())
        assertEquals("raw", result[0].asJsonObject.get("_rpclientLog").asString)
        assertEquals("{\"unfinished\":", result[0].asJsonObject.get("rawData").asString)
        assertEquals("", result[1].asJsonObject.get("rawData").asString)
        assertEquals("unexpected upstream error", result[2].asJsonObject.get("raw").asString)
    }

    /** 元数据事件和载荷开头的 BOM 不会产生非法数组结构。 */
    @Test
    fun streamLog_preservesMetadataOnlyEventsAndPayloadBom() {
        val log = LLMStreamLog()
        listOf("id: 7", "", "data: \uFEFF{\"ok\":true}", "").forEach(log::append)
        val text = log.toJson()
        JsonDocumentCodec.validate(text)
        val result = JsonParser.parseString(text).asJsonArray
        assertEquals("metadata", result[0].asJsonObject.get("_rpclientLog").asString)
        assertEquals("7", result[0].asJsonObject.get("id").asString)
        assertTrue(result[1].asJsonObject.get("ok").asBoolean)
    }

    /** 图片脱敏在结构化后仍有效，过深对象降级为文本而不递归构建对象树。 */
    @Test
    fun streamLog_redactsImagePayloadAndBoundsJsonDepth() {
        val log = LLMStreamLog()
        log.append("data: {\"image\":\"data:image/png;base64," + "A".repeat(2000) + "\"}")
        log.append("")
        val deep = "[".repeat(130) + "0" + "]".repeat(130)
        log.append("data: $deep")
        log.append("")
        listOf(
            "event: custom",
            "data: \"plain text\"",
            "",
            "data: upstream error",
            ""
        ).forEach(log::append)
        val text = ImageLogSanitizer.sanitizeLog(log.toJson())
        // 深层内容保存在字符串中，最终日志自身仍满足 JSON 深度预算。
        JsonDocumentCodec.validate(text)
        assertFalse(text.contains("A".repeat(256)))
        val result = JsonParser.parseString(text).asJsonArray
        assertTrue(result[0].asJsonObject.get("image").asString.contains("omitted"))
        assertEquals(deep, result[1].asJsonObject.get("rawData").asString)
        assertEquals("plain text", result[2].asJsonObject.get("payload").asString)
        assertEquals("upstream error", result[3].asJsonObject.get("rawData").asString)
    }

    /** 未完成的超大帧与大量需转义的字符均受限，不能先分配巨型序列化结果。 */
    @Test
    fun streamLog_boundsPendingFramesAndEscapedOutput() {
        val inputs =
            listOf("中".repeat(ImageLogSanitizer.MAX_LOG_CHARACTERS), "\u0001".repeat(800_000))
        // 输入长度限制和转义后的输出限制分别触发，保留此前完整事件。
        inputs.forEach { input ->
            val log = LLMStreamLog()
            log.append("data: {\"kept\":true}")
            log.append("")
            log.append("data: $input")
            log.append("")
            val text = log.toJson()
            assertTrue(text.length <= ImageLogSanitizer.MAX_LOG_CHARACTERS)
            val result = JsonParser.parseString(text).asJsonArray
            assertEquals(2, result.size())
            assertTrue(result[0].asJsonObject.get("kept").asBoolean)
            assertEquals("truncated", result[1].asJsonObject.get("_rpclientLog").asString)
        }
    }
}
