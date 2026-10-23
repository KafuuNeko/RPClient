package me.kafuuneko.rpclient.libs.story

import org.junit.Assert.assertEquals
import org.junit.Test

class StoryOutputSanitizerTest {
    private val mSanitizer = StoryOutputSanitizer()

    @Test
    fun removesReasoningFenceAndKnownPreambleConservatively() {
        val result =
            mSanitizer.sanitize("<think>hidden</think>\n```markdown\n续写如下：\n雨落在长街上。\n```")

        assertEquals("雨落在长街上。", result)
    }

    @Test
    fun preservesInternalMarkdownAndPunctuation() {
        val content = "她说：“不要动。”\n\n***\n\n## 下一幕"
        assertEquals(content, mSanitizer.sanitize(content))
    }

    @Test
    fun preservesUnwrappedBoundaryWhitespace() {
        val content = "  第一行\n\n第二行  "

        assertEquals(content, mSanitizer.sanitize(content))
    }

    @Test
    fun continuationAddsParagraphBoundaryOnlyWhenModelProvidesNone() {
        assertEquals("\n\n续写", prepareStoryContinuationText("已有正文", "续写"))
        assertEquals("续写", prepareStoryContinuationText("已有正文\n\n", "续写"))
        assertEquals("\n续写", prepareStoryContinuationText("已有正文", "\n续写"))
    }
}
