package me.kafuuneko.rpclient.libs.agent.runtime

import java.util.concurrent.ConcurrentHashMap
import me.kafuuneko.rpclient.libs.agent.model.AgentExecutionToken
import me.kafuuneko.rpclient.libs.agent.model.AgentJson
import me.kafuuneko.rpclient.libs.agent.model.ConversationRef
import me.kafuuneko.rpclient.libs.agent.tool.ToolErrorCode
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.ToolRegistry
import me.kafuuneko.rpclient.libs.llm.RoutingSessionId
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMToolGenerationRequest
import me.kafuuneko.rpclient.libs.llm.tool.ToolWireRequest
import me.kafuuneko.rpclient.libs.macro.MacroScope
import me.kafuuneko.rpclient.libs.macro.MacroValue
import me.kafuuneko.rpclient.libs.memory.MemoryPromptRenderer
import me.kafuuneko.rpclient.libs.memory.MemoryTable
import me.kafuuneko.rpclient.libs.prompt.macro.promptMacroExpander
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspection
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspectionItem
import me.kafuuneko.rpclient.libs.prompt.model.PromptMessageDraft
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.model.PromptSource
import me.kafuuneko.rpclient.libs.prompt.model.PromptSourceKind
import me.kafuuneko.rpclient.libs.prompt.preset.AgentPromptSnapshot
import me.kafuuneko.rpclient.libs.prompt.request.DEFAULT_STRICT_PROMPT_PLACEHOLDER
import me.kafuuneko.rpclient.libs.prompt.request.PromptRequestFinalizer
import me.kafuuneko.rpclient.libs.prompt.request.PromptTokenizerRegistry
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.toConfig
import me.kafuuneko.rpclient.libs.room.repository.AgentLocalDataRepository
import me.kafuuneko.rpclient.libs.room.repository.AgentRunRepository
import me.kafuuneko.rpclient.libs.room.repository.PromptPresetRepository
import me.kafuuneko.rpclient.libs.room.repository.SceneStateRepository

/**
 * Agent 初次最终化与完整发送投影的唯一入口。
 * - 世界书及 Prompt Regex 只复用输入草稿，不在工具循环里重新激活。
 * - 系统指令和工具 Schema 先计量，后续交换段只向冻结前缀尾部追加。
 */
class AgentPromptComposer(
    private val mPresets: PromptPresetRepository,
    private val mRuns: AgentRunRepository,
    private val mRegistry: ToolRegistry,
    private val mScenes: SceneStateRepository,
    private val mData: AgentLocalDataRepository,
    private val mTokenizers: PromptTokenizerRegistry,
    private val mFinalizer: PromptRequestFinalizer,
) {
    private val mInspections = ConcurrentHashMap<String, PromptInspection>()

    /** 整库替换后丢弃旧前缀，检查器不能跨库复用会话 ID。 */
    fun clearInspections() {
        mInspections.clear()
    }

    /** 等待恢复及新运行开始前重新读取配置，用冻结指纹定位对应版本。 */
    suspend fun refreshTools() = mRegistry.refresh()

    /** 恢复前保护检查点引用的工具版本，缺失旧版本仍明确拒绝续跑。 */
    fun retainTools(runId: String, digests: Collection<String>) = mRegistry.retain(runId, digests)

    /** 运行终止后释放内存中的指纹保留记录。 */
    fun releaseTools(runId: String) = mRegistry.release(runId)

    /** 整库替换后丢弃旧工具处理器及检查器引用。 */
    suspend fun clearTools() = mRegistry.clear()

    /** 返回最近的 Agent 发送投影，原始交换链不进入页面状态。 */
    fun inspection(conversation: ConversationRef): PromptInspection? =
        mInspections[conversation.key]

    /** 检查器与 HTTP 共用最终编码计数，同时仅显示基础前缀和客户端 Schema。 */
    fun recordProjection(
        plan: AgentTurnPlan,
        projection: LLMToolGenerationRequest = request(plan)
    ) {
        val request = projection
        val tokenizer = mTokenizers.resolve(plan.input.provider)
        // 按消息粒度计量基础前缀，标注剧情状态来源供检查器区分。
        val items = request.base.messages.mapIndexed { index, message ->
            val source =
                if (message.content.startsWith("[Scene state]")) PromptSource(PromptSourceKind.SceneState) else PromptSource(
                    PromptSourceKind.Other
                )
            PromptInspectionItem(
                index,
                message.role,
                listOf(source),
                tokenizer.countMessage(message),
                message.content,
                message.images
            )
        }.toMutableList()
        // 按冻结指纹拼接本轮暴露给模型的工具契约快照。
        val definitions =
            request.tools.joinToString("\n") {
                mRegistry.find(
                    it.toolId,
                    plan.toolDigests[it.toolId]
                )?.snapshotJson.orEmpty()
            }
        if (definitions.isNotEmpty()) items.add(
            PromptInspectionItem(
                items.size,
                LLMMessageRole.System,
                listOf(PromptSource(PromptSourceKind.Other)),
                tokenizer.countText(definitions),
                definitions
            )
        )
        // 缓存与真实发送同源的 Token 统计结果，不暴露原始工具交换链。
        mInspections[plan.input.conversation.key] = PromptInspection(
            plan.input.provider.model,
            tokenizer.name,
            tokenizer.strategy,
            contextLimit = plan.input.provider.contextTokens,
            responseReserve = plan.input.provider.maxTokens,
            promptBudget = plan.input.provider.contextTokens - plan.input.provider.maxTokens,
            finalTokenCount = inputTokens(plan, request).coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt(),
            items = items,
            omittedItems = emptyList(),
            postProcessingMode = PromptPostProcessingMode.fromOrdinal(plan.input.provider.promptPostProcessingMode)
        )
    }

    /** 捕获当前 Agent 覆盖和工具定义，明确空模板保持省略语义。 */
    suspend fun prepare(
        input: AgentTurnInput,
        presetId: Long?,
        token: AgentExecutionToken
    ): AgentTurnPlan {
        // 先按配置版本刷新目录，再解析本轮冻结预设并执行字面宏展开。
        mRegistry.refresh()
        val snapshot = AgentPromptSnapshot.resolve(
            input.templates,
            mPresets.getAgentSnapshot(presetId).templates
        ).templates
        val scope = MacroScope(input.macros.mapValues { MacroValue.Literal(it.value) })
        val render: (String) -> String = { text -> promptMacroExpander.expand(text, scope).text }
        val instruction = listOf(
            snapshot.agentInstruction, snapshot.agentToolGuidance,
            snapshot.agentGroupInstruction.takeIf { input.conversation is ConversationRef.Group }
                .orEmpty()
        ).map(render).filter(String::isNotBlank).joinToString("\n\n")
        // 组装固定系统前缀、当前剧情状态与去重后的可见记忆上下文。
        val scene = mScenes.get(input.conversation)
        val prefix = listOfNotNull(
            input.provider.systemPrompt.takeIf(String::isNotBlank)
                ?.let { LLMMessage(LLMMessageRole.System, it) },
            instruction.takeIf(String::isNotBlank)?.let { LLMMessage(LLMMessageRole.System, it) },
            scene.stateJson.takeIf { it != "{}" }
                ?.let { LLMMessage(LLMMessageRole.System, "[Scene state]\n$it") })
        val hasMemory =
            input.drafts.any { draft -> draft.sources.any { it.kind == PromptSourceKind.Memory } }
        val memories = mData.memories(
            ToolExecutionContext(
                token,
                0,
                "prepare",
                input.characterId,
                input.personaId
            )
        ).filterNot { hasMemory && it.ref.table == MemoryTable.Character }.take(6)
        val memoryText = MemoryPromptRenderer.renderContext(memories.map {
            MemoryPromptRenderer.renderItem(
                it.fields.content.body.take(300), snapshot, scope
            )
        }, snapshot, scope)
        val fixedPrefix = prefix + listOfNotNull(
            memoryText.takeIf(String::isNotBlank)?.let { LLMMessage(LLMMessageRole.System, it) })
        // 原子保留本轮允许的工具指纹，只读记忆策略下过滤记忆写工具。
        val policy = mRuns.policy(input.conversation)
        val tools = mRegistry.retainAvailable(token.runId, mRuns.enabledTools(policy))
            .filterNot { policy.memoryReadOnly && it.definition.writesMemory }
        val request = input.request.copy(
            messages = fixedPrefix + input.request.messages,
            includeReasoningInContent = false,
            routingSessionId = RoutingSessionId.forConversation(if (input.conversation is ConversationRef.Single) "chat:${input.conversation.id}" else "group:${input.conversation.id}"),
            isPromptFinalized = true
        )
        return AgentTurnPlan(
            input.copy(request = request, templates = snapshot),
            providerDigest(input.provider),
            tools.associate { it.definition.toolId to it.digest },
            render(snapshot.agentFinalize),
            render(snapshot.agentToolErrorGuidance),
            render(if (input.conversation is ConversationRef.Single) snapshot.summarizePrompt else snapshot.groupSummarizePrompt) + "\n\n" + render(
                snapshot.agentCompactionGuidance
            ),
            prefixMessageCount = fixedPrefix.size
        )
    }

    /** 输入和输出预留使用与 HTTP 相同的编码投影；图片只计资源 Token，不计 Base64。 */
    fun inputTokens(plan: AgentTurnPlan, request: LLMToolGenerationRequest): Long {
        val tokenizer = mTokenizers.resolve(plan.input.provider)
        return tokenizer.countText(
            ToolWireRequest.encode(
                plan.input.provider.toConfig(),
                request,
                plan.input.streaming
            ) { it.uuid }).toLong() +
            tokenizer.countImages(request.base.messages.flatMap { it.images })
    }

    /** 工具集冻结后恢复必须逐项复核版本，不能套用新处理器执行旧写入。 */
    fun request(plan: AgentTurnPlan): LLMToolGenerationRequest {
        val tools = plan.toolDigests.map { (id, digest) ->
            mRegistry.find(id, digest)?.definition
                ?: throw AgentRuntimeException(ToolErrorCode.TOOL_CONTRACT_CHANGED)
        }
        return LLMToolGenerationRequest(plan.input.request, tools, systemPromptApplied = true)
    }

    /** 只压缩来源明确的旧聊天历史，活动工具交换和最新用户输入始终保留。 */
    fun compactionCandidates(input: AgentTurnInput): List<PromptMessageDraft> {
        val histories =
            input.drafts.filter { draft -> draft.sources.all { it.kind == PromptSourceKind.ChatHistory } }
        return histories.dropLast(2).takeWhile { it.canDrop && it.images.isEmpty() }
    }

    /** 替换实际处理过的历史，不推进普通摘要边界，也不删除未处理的消息。 */
    fun compact(
        plan: AgentTurnPlan,
        candidates: List<PromptMessageDraft>,
        summary: String
    ): AgentTurnPlan {
        val input = plan.input
        val boundary =
            candidates.flatMap { it.sources }.mapNotNull { it.referenceId }.maxOrNull() ?: 0
        val summaryDraft = PromptMessageDraft(
            LLMMessageRole.System,
            summary,
            PromptSource(PromptSourceKind.Summary, referenceId = boundary),
            100,
            false
        )
        val drafts = input.drafts.toMutableList()
        val first = drafts.indexOf(candidates.first())
        if (first < 0) return plan
        drafts.removeAll(candidates.toSet())
        drafts.add(first.coerceAtMost(drafts.size), summaryDraft)
        // 重新最终化只处理已冻结草稿，不重新扫描世界书或运行 Prompt Regex。
        val prefix = input.request.messages.take(plan.prefixMessageCount)
        val overhead =
            inputTokens(plan, request(plan).copy(base = input.request.copy(messages = prefix)))
        val finalized = mFinalizer.finalize(
            drafts = drafts,
            provider = input.provider,
            model = input.provider.model,
            options = input.request.options,
            includeReasoningInContent = false,
            captureReasoning = true,
            maxContextTokens = (input.provider.contextTokens - overhead).toInt(),
            maxResponseTokens = input.provider.maxTokens,
            postProcessingMode = PromptPostProcessingMode.fromOrdinal(input.provider.promptPostProcessingMode),
            strictPromptPlaceholder = DEFAULT_STRICT_PROMPT_PLACEHOLDER
        )
        return plan.copy(
            input = input.copy(request = input.request.copy(messages = prefix + finalized.request.messages)),
            compactionCoveredMessageId = boundary
        )
    }

    companion object {
        /** 密钥仅参与不可逆指纹，检查点不会存储可重用的认证信息。 */
        fun providerDigest(provider: LLMProvider): String = AgentJson.digest(provider.toString())
    }
}
