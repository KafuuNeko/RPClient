package me.kafuuneko.rpclient.libs.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 验证流式分片与损坏标签不会将推理或半个标签写入可发送正文。 */
class UserWritingOutputParserTest {
    @Test
    fun everySplitOfThinkingTagsKeepsReasoningOutOfDraft() {
        val output = "<think>计划</think>我轻声说：你好。"
        // 遍历所有分割点，同时校验中间态，避免仅在完成时清理标签的伪兼容。
        for (split in 0..output.length) {
            val parser = UserWritingOutputParser()
            parser.append(output.take(split))
            assertFalse(parser.body.contains("计划"))
            assertFalse(parser.body.contains("<"))
            parser.append(output.drop(split))
            parser.finish()
            assertEquals("我轻声说：你好。", parser.body)
            assertEquals("计划", parser.reasoning)
        }
    }

    @Test
    fun characterByCharacterStreamHandlesMultipleCaseInsensitiveBlocks() {
        val parser = UserWritingOutputParser()
        "<THINK>准备</THINK>你好。<think>补充</think>再见。".forEach { parser.append(it.toString()) }
        parser.finish()
        assertEquals("你好。再见。", parser.body)
        assertEquals("准备补充", parser.reasoning)
    }

    @Test
    fun unfinishedReasoningPreservesEarlierBodyWithoutLeakingTheRest() {
        val parser = UserWritingOutputParser()
        parser.append("已收到的正文<think>尚未完成的构思</thi")
        parser.finish()
        assertEquals("已收到的正文", parser.body)
        assertFalse(parser.body.contains("构思"))
        assertEquals("尚未完成的构思", parser.reasoning)
    }

    @Test
    fun stoppedInsideOpeningTagDoesNotExposePartialTag() {
        val parser = UserWritingOutputParser()
        parser.append("正文<thi")
        parser.finish()
        assertEquals("正文", parser.body)
    }

    @Test
    fun ordinaryAngleBracketsAndPersonaInnerThoughtsRemainBody() {
        val parser = UserWritingOutputParser()
        listOf("我心想：2 <", " 3。<th", "is> 是文字。\n").forEach(parser::append)
        parser.finish()
        assertEquals("我心想：2 < 3。<this> 是文字。\n", parser.body)
        assertEquals("", parser.reasoning)
    }

    @Test
    fun reasoningOnlyOutputHasNoSendableBody() {
        val parser = UserWritingOutputParser()
        parser.append("<think>" + "x".repeat(UserWritingOutputParser.MAX_REASONING_CHARS + 100))
        parser.finish()
        assertEquals("", parser.body)
        assertEquals(UserWritingOutputParser.MAX_REASONING_CHARS, parser.reasoning.length)
    }

    @Test
    fun preservesWhitespaceAtContinuationBoundary() {
        val parser = UserWritingOutputParser()
        parser.append(" a continuation\n\n")
        parser.finish()
        assertEquals(" a continuation\n\n", parser.body)
    }
}
