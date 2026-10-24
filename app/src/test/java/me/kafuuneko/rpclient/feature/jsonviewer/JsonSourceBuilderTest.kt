package me.kafuuneko.rpclient.feature.jsonviewer

import kotlinx.coroutines.CancellationException
import me.kafuuneko.rpclient.feature.jsonviewer.model.JsonSourceBuilder
import me.kafuuneko.rpclient.feature.jsonviewer.model.JsonSourceState
import me.kafuuneko.rpclient.utils.JsonSyntaxTokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonSourceBuilderTest {
    /** 根字符串跨块后仍保持字符串高亮，块内区间不能超出文本。 */
    @Test
    fun build_projectsStringTokensAcrossChunks() {
        val source = "\"" + "中😀".repeat(3000) + "\""
        val result = JsonSourceBuilder.build(source) as JsonSourceState.Ready
        assertFalse(result.isRaw)
        assertEquals(source, result.blocks.joinToString("") { it.text })
        assertTrue(result.blocks.size > 2)
        assertTrue(result.blocks.all { block ->
            block.tokens.single().let { token ->
                token.type == JsonSyntaxTokenType.String && token.start == 0 && token.end == block.text.length
            }
        })
    }

    /** 旧日志已破坏语法时只显示原文，不丢弃截断标记。 */
    @Test
    fun build_keepsInvalidLogAsRawText() {
        val source = "{\"content\":\"unfinished\n[log truncated]"
        val result = JsonSourceBuilder.build(source) as JsonSourceState.Ready
        assertTrue(result.isRaw)
        assertEquals(source, result.blocks.joinToString("") { it.text })
    }

    /** 巨型输入、深层嵌套和大量 Token 都不能构建不受控的源码结果。 */
    @Test
    fun build_rejectsResourceHeavyDocuments() {
        assertEquals(JsonSourceState.TooLarge, JsonSourceBuilder.build("中".repeat(1_048_577)))
        assertEquals(
            JsonSourceState.TooLarge,
            JsonSourceBuilder.build("[".repeat(129) + "0" + "]".repeat(129))
        )
        assertEquals(
            JsonSourceState.TooLarge,
            JsonSourceBuilder.build("[" + "0,".repeat(60_000) + "0]")
        )
    }

    /** 切换视图引发的取消必须传回调用方，不能作为原文降级继续分词。 */
    @Test
    fun build_propagatesCancellation() {
        assertThrows(CancellationException::class.java) {
            JsonSourceBuilder.build("{}", checkActive = { throw CancellationException() })
        }
    }
}
