package me.kafuuneko.rpclient.libs.agent

import me.kafuuneko.rpclient.libs.prompt.preset.AgentPromptSnapshot
import me.kafuuneko.rpclient.libs.prompt.preset.PromptTemplateSnapshot
import me.kafuuneko.rpclient.libs.prompt.preset.PromptType
import org.junit.Assert.assertEquals
import org.junit.Test

/** 保护会话 Agent 绑定不替换角色基础设定，并保持显式空串语义。 */
class AgentPromptSnapshotTest {
    /** 仅允许 Agent 与压缩摘要键使用独立来源，普通上下文模板继续使用基础预设。 */
    @Test
    fun scopedOverridesPreserveBaseAndExplicitEmptyValues() {
        // 会话绑定不能替换角色基础设定，也不能把显式清空恢复成默认指令。
        val base = PromptTemplateSnapshot.resolve(
            1,
            2,
            mapOf("main" to "base main", "world_info_format" to "base lore")
        )
        val agent = PromptTemplateSnapshot.resolve(
            3,
            4,
            mapOf(
                "main" to "must not replace",
                "agent_instruction" to "",
                "summarize" to "compact history"
            )
        )
        val result = AgentPromptSnapshot.resolve(base, agent)
        assertEquals("base main", result.templates.mainPrompt)
        assertEquals("base lore", result.templates.worldInfoFormat)
        assertEquals("", result.templates.agentInstruction)
        assertEquals("compact history", result.templates.summarizePrompt)
        assertEquals(3L, result.agentPresetId)
        assertEquals(4L, result.agentPresetRevision)
        assertEquals(PromptType.AgentToolGuidance.defaultValue, result.templates.agentToolGuidance)
    }
}
