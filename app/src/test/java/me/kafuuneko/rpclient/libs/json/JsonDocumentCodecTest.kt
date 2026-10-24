package me.kafuuneko.rpclient.libs.json

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter

class JsonDocumentCodecTest {
    /** 流式格式化不能合并重复键，或将数字先转换为浮点数。 */
    @Test
    fun format_preservesDuplicateKeysAndNumberLexemes() {
        val output = StringWriter()
        JsonDocumentCodec.format(
            """{"x":1,"x":2,"large":9007199254740993,"n":-0,"e":1e+09}""",
            output
        )
        val text = output.toString()
        assertEquals(2, Regex("\"x\"").findAll(text).count())
        assertTrue(text.contains("9007199254740993"))
        assertTrue(text.contains("-0"))
        assertTrue(text.contains("1e+09"))
    }

    /** 校验必须拒绝尾随根值和未完成的历史载荷。 */
    @Test
    fun validate_rejectsInvalidOrMultipleRoots() {
        listOf("{} []", "{\"x\":", "", "{x:1}", "[1,]").forEach { source ->
            assertThrows(Exception::class.java) { JsonDocumentCodec.validate(source) }
        }
        listOf("null", "true", "42", "\"text\"").forEach { JsonDocumentCodec.validate(it) }
    }

    /** 检查实际输出而非紧凑输入，缩进膨胀不能绕过预算。 */
    @Test
    fun format_stopsBeforeWritingBeyondOutputBudget() {
        val source = "[".repeat(100) + "0" + "]".repeat(100)
        val output = StringWriter()
        assertThrows(JsonResourceLimitException::class.java) {
            JsonDocumentCodec.format(source, output, JsonDocumentLimits(outputCharacters = 500))
        }
        assertTrue(output.buffer.length <= 500)
    }

    /** 深度、节点数量和换行数都单独受限，取消也不能降级为普通失败。 */
    @Test
    fun format_enforcesStructureLinesAndCancellation() {
        // 独立触发每种资源限制，取消异常必须原样传递。
        assertThrows(JsonResourceLimitException::class.java) {
            JsonDocumentCodec.validate("[[[0]]]", JsonDocumentLimits(depth = 2))
        }
        assertThrows(JsonResourceLimitException::class.java) {
            JsonDocumentCodec.validate("[1,2,3]", JsonDocumentLimits(tokens = 3))
        }
        assertThrows(JsonResourceLimitException::class.java) {
            JsonDocumentCodec.format("[1,2,3]", StringWriter(), JsonDocumentLimits(lines = 2))
        }
        assertThrows(CancellationException::class.java) {
            JsonDocumentCodec.format(
                "[1]",
                StringWriter(),
                checkActive = { throw CancellationException() })
        }
    }

    /** 无换行文本也要有界分块，并且不能切断 emoji 代理对。 */
    @Test
    fun textChunks_preserveUnicodeAndEveryCharacter() {
        val source = "a".repeat(2047) + "😀" + "中\n".repeat(5000)
        val blocks = splitJsonText(source)
        assertEquals(source, blocks.joinToString(""))
        assertTrue(blocks.all { it.length <= 2048 })
        assertTrue(blocks.none { it.last().isHighSurrogate() || it.first().isLowSurrogate() })
        assertTrue(splitJsonText("\n".repeat(3000)).all { it.count { char -> char == '\n' } <= 64 })
    }
}
