package me.kafuuneko.rpclient.libs.agent

import me.kafuuneko.rpclient.libs.room.repository.AgentLocalDataRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 验证证据摘录匹配只容忍空白与单层外引号，且返回原文逐字子串，绝不接受改写。 */
class AgentEvidenceQuoteTest {
    @Test
    fun matchesExactTrimmedWrappedAndCrlfQuotesVerbatim() {
        val content = "第一行\n记住我们每天都要吃苹果\n第三行"
        assertEquals(
            "记住我们每天都要吃苹果",
            AgentLocalDataRepository.matchEvidenceQuote(content, "记住我们每天都要吃苹果"),
        )
        assertEquals(
            "记住我们每天都要吃苹果",
            AgentLocalDataRepository.matchEvidenceQuote(content, "  记住我们每天都要吃苹果  "),
        )
        assertEquals(
            "记住我们每天都要吃苹果",
            AgentLocalDataRepository.matchEvidenceQuote(content, "“记住我们每天都要吃苹果”"),
        )
        assertEquals(
            "记住我们每天都要吃苹果",
            AgentLocalDataRepository.matchEvidenceQuote(content, "「记住我们每天都要吃苹果」"),
        )
        assertEquals(
            "记住我们每天都要吃苹果",
            AgentLocalDataRepository.matchEvidenceQuote(content, "\"记住我们每天都要吃苹果\""),
        )
        assertEquals(
            "第一行\n记住我们每天都要吃苹果",
            AgentLocalDataRepository.matchEvidenceQuote(
                content,
                "第一行\r\n记住我们每天都要吃苹果"
            ),
        )
    }

    @Test
    fun rejectsBlankAndParaphrasedEvidenceQuotes() {
        val content = "记住我们每天都要吃苹果"
        assertNull(AgentLocalDataRepository.matchEvidenceQuote(content, ""))
        assertNull(AgentLocalDataRepository.matchEvidenceQuote(content, "   "))
        assertNull(AgentLocalDataRepository.matchEvidenceQuote(content, "“”"))
        assertNull(AgentLocalDataRepository.matchEvidenceQuote(content, "我们每天都要吃香蕉"))
        assertNull(AgentLocalDataRepository.matchEvidenceQuote("", "苹果"))
    }
}
