package me.kafuuneko.rpclient.libs.prompt.preset

/** 冻结基础预设与 Agent 覆盖来源，绑定预设仅替换 Agent 段和压缩摘要模板。 */
data class AgentPromptSnapshot(
    val templates: PromptTemplateSnapshot,
    val agentPresetId: Long,
    val agentPresetRevision: Long,
) {
    companion object {
        private val mOverridableTypes = setOf(
            PromptType.AgentInstruction,
            PromptType.AgentGroupInstruction,
            PromptType.AgentToolGuidance,
            PromptType.AgentFinalize,
            PromptType.AgentToolErrorGuidance,
            PromptType.AgentCompactionGuidance,
            PromptType.Summarize,
            PromptType.GroupSummarize,
            PromptType.RawSummaryPreviousFormat,
            PromptType.RawSummaryHistoryFormat,
            PromptType.RawSummaryMessageFormat,
            PromptType.RawSummaryImagePrompt,
        )

        /** 合并独立 Agent 预设，角色基础设定与普通生成预设保持同一来源。 */
        fun resolve(
            base: PromptTemplateSnapshot,
            agent: PromptTemplateSnapshot
        ): AgentPromptSnapshot = AgentPromptSnapshot(
            templates = base.copy(values = base.values + agent.values.filterKeys { it in mOverridableTypes }),
            agentPresetId = agent.presetId,
            agentPresetRevision = agent.revision,
        )
    }
}
