package me.kafuuneko.rpclient.libs.utils

import me.kafuuneko.rpclient.utils.stripThinkBlocks
import me.kafuuneko.rpclient.utils.toPreview
import org.junit.Assert.assertEquals
import org.junit.Test

class StringExtTest {
    /** 超长单段正文在交给预览测量前被限制。 */
    @Test
    fun toPreview_boundsLongParagraphBeforeMeasurement() {
        val content = "正文".repeat(500_000)

        assertEquals(content.substring(0, 512) + "...", content.toPreview(maxLength = 512))
    }

    /** 截断位置落在补充字符中间时，不向文本布局传入孤立代理项。 */
    @Test
    fun toPreview_preservesSurrogatePairBoundaries() {
        val content = "abc\uD83D\uDE00def"

        assertEquals("abc...", content.toPreview(maxLength = 4))
        assertEquals("abc\uD83D\uDE00...", content.toPreview(maxLength = 5))
        assertEquals("...", "\uD83D\uDE00def".toPreview(maxLength = 1))
    }

    @Test
    fun stripThinkBlocks_removesClosedThinkBlock() {
        val content = "<think>\nmodel reasoning\n</think>\n\nactual reply"

        assertEquals("actual reply", content.stripThinkBlocks())
    }

    @Test
    fun stripThinkBlocks_removesUnclosedThinkBlock() {
        val content = "actual reply\n<think>\nunpersisted reasoning"

        assertEquals("actual reply", content.stripThinkBlocks())
    }

    @Test
    fun stripThinkBlocks_removesMultipleThinkBlocksCaseInsensitively() {
        val content = "<THINK>first</THINK>\nhello\n<think>second</think>\nworld"

        assertEquals("hello\n\nworld", content.stripThinkBlocks())
    }
}
