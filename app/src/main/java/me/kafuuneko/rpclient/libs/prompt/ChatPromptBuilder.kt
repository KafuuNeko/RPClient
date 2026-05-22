package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry

class ChatPromptBuilder(
    private val mMacroResolver: PromptMacroResolver,
    private val mHistoryBuilder: FormattedHistoryBuilder,
    private val mWorldBookActivator: WorldBookActivator
) {
    /**
     * 构建最终提交给模型的请求。
     *
     * 入口保留纯请求返回值，供旧调用方使用；需要保存世界书 sticky/cooldown 状态的调用方
     * 应使用 [buildWithMetadata]。
     */
    fun build(context: PromptBuildContext): LLMGenerationRequest {
        return buildWithMetadata(context).request
    }

    /**
     * 按 SillyTavern 的 prompt 分区思想组装真实上下文：
     * 1. 固定 system 区：主提示词、角色定义、世界书 before/after character。
     * 2. 示例对话区：按 <START> 切块，作为可裁剪的 examples。
     * 3. 聊天历史区：保留最近消息，并按 depth 插入 userNote、Character's Note、世界书条目。
     * 4. 尾部指令区：post-history instructions。
     */
    fun buildWithMetadata(context: PromptBuildContext): PromptBuildResult {
        val maxPromptTokens = (context.maxContextTokens - context.maxResponseTokens).coerceAtLeast(MIN_PROMPT_TOKENS)
        val worldBudget = (maxPromptTokens * readWorldInfoBudgetPercent().coerceIn(0, 40) / 100)
            .coerceAtLeast(MIN_WORLD_BUDGET)
        val worldInfo = fitWorldInfo(mWorldBookActivator.activateStructured(context), worldBudget)
        val outlets = worldInfo.outletEntries.mapValues { (_, entries) ->
            entries.sortedBy { it.order }.joinToString("\n") { it.content }
        }
        val fixedMessages = buildFixedMessages(context, worldInfo)
        val inChatPieces = buildInChatPieces(context, worldInfo)
        val examplePieces = buildExamplePieces(context, worldInfo)
        val fixedTokenCount = fixedMessages.sumOf { estimateTokens(it.content) } +
            inChatPieces.sumOf { estimateTokens(it.content) } +
            examplePieces.sumOf { estimateTokens(it.content) }
        // 固定段和注入段先占预算，剩余 token 再留给聊天历史，避免历史挤掉角色定义。
        val historyBudget = (maxPromptTokens - fixedTokenCount).coerceAtLeast(MIN_HISTORY_BUDGET)
        val historyMessages = fitHistory(context.messages, historyBudget)
        val historyText = mHistoryBuilder.build(historyMessages, context.userName, context.character.name)

        val messages = mutableListOf<LLMMessage>()
        fixedMessages.beforeHistory.forEach { messages += it.resolve(context, historyText, outlets) }
        examplePieces.forEach { messages += it.resolve(context, historyText, outlets) }
        buildNewChatPiece(context, historyText, outlets)?.let { messages += it }
        messages += buildChatMessages(historyMessages, context, historyText, inChatPieces, outlets)
        fixedMessages.afterHistory.forEach { messages += it.resolve(context, historyText, outlets) }

        return PromptBuildResult(
            request = LLMGenerationRequest(
                messages = messages.ifEmpty { listOf(LLMMessage(LLMMessageRole.System, readMainPrompt())) },
                model = context.provider?.model,
                options = LLMGenerationOptions(
                    temperature = context.provider?.temperature,
                    maxTokens = context.maxResponseTokens,
                    topP = context.provider?.topP
                ),
                includeReasoningInContent = true
            ),
            worldInfoStateJson = worldInfo.nextStateJson
        )
    }

    private fun buildFixedMessages(
        context: PromptBuildContext,
        worldInfo: WorldBookActivationResult
    ): PromptSections {
        val beforeHistory = mutableListOf<PromptPiece>()
        // before/after character 是固定 system 区；creator_notes 只作为元数据保留，不再默认注入。
        worldInfo.beforeCharacter.forEach {
            beforeHistory += PromptPiece(LLMMessageRole.System, formatWorldInfo(it.content), PromptPieceImportance.Optional)
        }
        beforeHistory += PromptPiece(LLMMessageRole.System, readCharacterMainPrompt(context), PromptPieceImportance.Required)
        worldInfo.afterCharacter.forEach {
            beforeHistory += PromptPiece(LLMMessageRole.System, formatWorldInfo(it.content), PromptPieceImportance.Optional)
        }
        beforeHistory += PromptPiece(LLMMessageRole.System, context.userDescription, PromptPieceImportance.Required)
        beforeHistory += PromptPiece(LLMMessageRole.System, context.character.description, PromptPieceImportance.Required)
        beforeHistory += PromptPiece(LLMMessageRole.System, formatPersonality(context.character.personality), PromptPieceImportance.Required)
        beforeHistory += PromptPiece(LLMMessageRole.System, formatScenario(context.character.scenario), PromptPieceImportance.Required)
        beforeHistory += PromptPiece(LLMMessageRole.System, context.session.summarize, PromptPieceImportance.Required)
        beforeHistory += PromptPiece(LLMMessageRole.System, readAuxiliaryPrompt(), PromptPieceImportance.Optional)

        val afterHistory = buildList {
            add(PromptPiece(
                role = LLMMessageRole.System,
                content = readCharacterPostHistoryInstructions(context),
                importance = PromptPieceImportance.Required
            ))
            val modePrompt = when (context.generationMode) {
                PromptGenerationMode.Normal -> ""
                PromptGenerationMode.Continue -> readContinueNudgePrompt()
                PromptGenerationMode.Impersonate -> readImpersonationPrompt()
            }
            if (modePrompt.isNotBlank()) {
                add(PromptPiece(LLMMessageRole.System, modePrompt, PromptPieceImportance.Required))
            }
        }

        return PromptSections(
            beforeHistory = beforeHistory.filter { it.content.isNotBlank() },
            afterHistory = afterHistory.filter { it.content.isNotBlank() }
        )
    }

    private fun buildInChatPieces(
        context: PromptBuildContext,
        worldInfo: WorldBookActivationResult
    ): List<InChatPromptPiece> {
        val pieces = mutableListOf<InChatPromptPiece>()
        // AN top/user note/AN bottom 都放在最后一条用户消息之前，顺序通过 order 固定。
        worldInfo.anTop.forEach {
            pieces += InChatPromptPiece(
                role = LLMMessageRole.System,
                content = it.content,
                importance = PromptPieceImportance.Optional,
                depth = USER_NOTE_DEPTH,
                order = AN_TOP_ORDER,
                tieBreaker = it.id
            )
        }
        context.session.userNote.takeIf { it.isNotBlank() }?.let {
            pieces += InChatPromptPiece(
                role = LLMMessageRole.System,
                content = it,
                importance = PromptPieceImportance.Optional,
                depth = USER_NOTE_DEPTH,
                order = USER_NOTE_ORDER,
                tieBreaker = Long.MIN_VALUE
            )
        }
        worldInfo.anBottom.forEach {
            pieces += InChatPromptPiece(
                role = LLMMessageRole.System,
                content = it.content,
                importance = PromptPieceImportance.Optional,
                depth = USER_NOTE_DEPTH,
                order = AN_BOTTOM_ORDER,
                tieBreaker = it.id
            )
        }
        context.character.depthPromptPrompt.takeIf { it.isNotBlank() }?.let {
            pieces += InChatPromptPiece(
                role = context.character.depthPromptRole.toMessageRole(),
                content = it,
                importance = PromptPieceImportance.Optional,
                depth = context.character.depthPromptDepth.coerceAtLeast(0),
                order = CHARACTER_NOTE_ORDER,
                tieBreaker = Long.MIN_VALUE + 1
            )
        }
        // 世界书 at-depth 条目按自身 depth/role 进入聊天历史内部，而不是全部堆在 system 头部。
        worldInfo.depthEntries.forEach { group ->
            group.entries.forEach {
                pieces += InChatPromptPiece(
                    role = group.role,
                    content = it.content,
                    importance = PromptPieceImportance.Optional,
                    depth = group.depth.coerceAtLeast(0),
                    order = it.order,
                    tieBreaker = it.id
                )
            }
        }
        return pieces
    }

    private fun buildExamplePieces(
        context: PromptBuildContext,
        worldInfo: WorldBookActivationResult
    ): List<PromptPiece> {
        // SillyTavern 示例对话以 <START> 分块；这里保持块粒度，后续可按预算整块裁剪。
        val blocks = buildList {
            worldInfo.exampleBefore.forEach { add(it.content) }
            addAll(parseExampleBlocks(context.character.examplesOfDialogue))
            worldInfo.exampleAfter.forEach { add(it.content) }
        }
        return blocks
            .filter { it.isNotBlank() }
            .flatMap { block ->
                buildList {
                    val marker = readNewExampleChatPrompt()
                    if (marker.isNotBlank()) {
                        add(PromptPiece(LLMMessageRole.System, marker, PromptPieceImportance.Optional))
                    }
                    add(PromptPiece(LLMMessageRole.System, block, PromptPieceImportance.Optional))
                }
            }
    }

    private fun buildNewChatPiece(
        context: PromptBuildContext,
        history: String,
        outlets: Map<String, String>
    ): LLMMessage? {
        if (context.messages.isEmpty() && context.currentUserMessage.isNullOrBlank()) return null
        val marker = readNewChatPrompt()
        if (marker.isBlank()) return null
        return PromptPiece(LLMMessageRole.System, marker, PromptPieceImportance.Optional)
            .resolve(context, history, outlets)
    }

    private fun fitHistory(messages: List<ChatMessage>, tokenBudget: Int): List<ChatMessage> {
        val selected = ArrayDeque<ChatMessage>()
        var usedTokens = 0
        // 从最新消息向前取，保证当前上下文优先保留最近对话。
        messages.asReversed().forEach { message ->
            val nextTokens = estimateTokens(message.content)
            if (selected.isNotEmpty() && usedTokens + nextTokens > tokenBudget) return@forEach
            selected.addFirst(message)
            usedTokens += nextTokens
        }
        return selected.toList()
    }

    private fun fitWorldInfo(result: WorldBookActivationResult, tokenBudget: Int): WorldBookActivationResult {
        val selected = mutableListOf<LorebookEntry>()
        var usedTokens = 0
        // 世界书预算只裁剪已触发条目；ignoreBudget 条目仍可越过该限制。
        result.activatedEntries.sortedWith(compareByDescending<LorebookEntry> { it.order }.thenBy { it.id })
            .forEach { entry ->
                val nextTokens = estimateTokens(entry.content)
                if (!entry.ignoreBudget && selected.isNotEmpty() && usedTokens + nextTokens > tokenBudget) return@forEach
                selected += entry
                if (!entry.ignoreBudget) usedTokens += nextTokens
            }
        val selectedIds = selected.map { it.id }.toSet()
        return result.copy(
            activatedEntries = result.activatedEntries.filter { it.id in selectedIds },
            beforeCharacter = result.beforeCharacter.filter { it.id in selectedIds },
            afterCharacter = result.afterCharacter.filter { it.id in selectedIds },
            exampleBefore = result.exampleBefore.filter { it.id in selectedIds },
            exampleAfter = result.exampleAfter.filter { it.id in selectedIds },
            anTop = result.anTop.filter { it.id in selectedIds },
            anBottom = result.anBottom.filter { it.id in selectedIds },
            depthEntries = result.depthEntries.mapNotNull { group ->
                val entries = group.entries.filter { it.id in selectedIds }.toMutableList()
                if (entries.isEmpty()) null else group.copy(entries = entries)
            },
            outletEntries = result.outletEntries.mapValues { (_, entries) -> entries.filter { it.id in selectedIds } }
                .filterValues { it.isNotEmpty() }
        )
    }

    private fun buildChatMessages(
        historyMessages: List<ChatMessage>,
        context: PromptBuildContext,
        historyText: String,
        inChatPieces: List<InChatPromptPiece>,
        outlets: Map<String, String>
    ): List<LLMMessage> {
        val chatMessages = historyMessages.map { it.toLlmMessage() }.toMutableList()
        context.currentUserMessage?.takeIf { it.isNotBlank() }?.let {
            chatMessages += LLMMessage(LLMMessageRole.User, resolve(it, context, historyText, it, outlets))
        }
        if (chatMessages.isEmpty() || inChatPieces.isEmpty()) return chatMessages

        val injections = inChatPieces
            .mapNotNull { piece ->
                val resolved = piece.resolve(context, historyText, outlets)
                if (resolved.content.isBlank()) null else piece.insertionIndex(chatMessages) to (piece to resolved)
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, pieces) ->
                pieces.sortedWith(compareBy<Pair<InChatPromptPiece, LLMMessage>> { it.first.order }.thenBy { it.first.tieBreaker })
                    .map { it.second }
            }

        return buildList {
            for (index in 0..chatMessages.size) {
                injections[index]?.let { addAll(it) }
                if (index < chatMessages.size) add(chatMessages[index])
            }
        }
    }

    private fun InChatPromptPiece.insertionIndex(chatMessages: List<LLMMessage>): Int {
        // depth=0 表示追加到聊天末尾；depth=1 表示插入到最后一条消息之前，以此类推。
        return if (tieBreaker == Long.MIN_VALUE && chatMessages.lastOrNull()?.role != LLMMessageRole.User) {
            chatMessages.size
        } else {
            (chatMessages.size - depth).coerceIn(0, chatMessages.size)
        }
    }

    private fun PromptPiece.resolve(context: PromptBuildContext, history: String, outlets: Map<String, String>): LLMMessage {
        val resolved = resolve(content, context, history, original, outlets).trim()
        val maxTokens = if (importance == PromptPieceImportance.Required) REQUIRED_PIECE_MAX_TOKENS else OPTIONAL_PIECE_MAX_TOKENS
        return LLMMessage(role, trimToTokenLimit(resolved, maxTokens))
    }

    private fun InChatPromptPiece.resolve(context: PromptBuildContext, history: String, outlets: Map<String, String>): LLMMessage {
        val resolved = resolve(content, context, history, content, outlets).trim()
        val maxTokens = if (importance == PromptPieceImportance.Required) REQUIRED_PIECE_MAX_TOKENS else OPTIONAL_PIECE_MAX_TOKENS
        return LLMMessage(role, trimToTokenLimit(resolved, maxTokens))
    }

    private fun resolve(
        text: String,
        context: PromptBuildContext,
        history: String,
        original: String,
        outlets: Map<String, String>
    ): String {
        return mMacroResolver.resolve(text, context, history, original, outlets)
    }

    private fun ChatMessage.toLlmMessage(): LLMMessage {
        val role = when (source) {
            ChatMessage.Source.User -> LLMMessageRole.User
            ChatMessage.Source.Char -> LLMMessageRole.Assistant
            ChatMessage.Source.System -> LLMMessageRole.System
        }
        return LLMMessage(role, content)
    }

    private fun trimToTokenLimit(text: String, maxTokens: Int): String {
        if (estimateTokens(text) <= maxTokens) return text
        val maxChars = (maxTokens * 3).coerceAtLeast(120)
        return text.take(maxChars).trimEnd() + "\n[Truncated to fit context]"
    }

    private fun estimateTokens(text: String): Int {
        return (text.length / 3).coerceAtLeast(1)
    }

    private fun readMainPrompt(): String {
        return runCatching { AppModel.mainPrompt }.getOrDefault(AppModel.DEFAULT_MAIN_PROMPT)
    }

    private fun readPostHistoryInstructions(): String {
        return runCatching { AppModel.postHistoryInstructions }.getOrDefault("")
    }

    private fun readAuxiliaryPrompt(): String {
        return runCatching { AppModel.auxiliaryPrompt }.getOrDefault(AppModel.DEFAULT_AUXILIARY_PROMPT)
    }

    private fun readImpersonationPrompt(): String {
        return runCatching { AppModel.impersonationPrompt }.getOrDefault(AppModel.DEFAULT_IMPERSONATION_PROMPT)
    }

    private fun readNewChatPrompt(): String {
        return runCatching { AppModel.newChatPrompt }.getOrDefault(AppModel.DEFAULT_NEW_CHAT_PROMPT)
    }

    private fun readNewExampleChatPrompt(): String {
        return runCatching { AppModel.newExampleChatPrompt }.getOrDefault(AppModel.DEFAULT_NEW_EXAMPLE_CHAT_PROMPT)
    }

    private fun readContinueNudgePrompt(): String {
        return runCatching { AppModel.continueNudgePrompt }.getOrDefault(AppModel.DEFAULT_CONTINUE_NUDGE_PROMPT)
    }

    private fun formatWorldInfo(content: String): String {
        return applyFormat(readWorldInfoFormat(), "{0}", content)
    }

    private fun formatScenario(content: String): String {
        return applyFormat(readScenarioFormat(), "{{scenario}}", content)
    }

    private fun formatPersonality(content: String): String {
        return applyFormat(readPersonalityFormat(), "{{personality}}", content)
    }

    private fun applyFormat(template: String, marker: String, content: String): String {
        if (content.isBlank()) return content
        if (template.isBlank()) return content
        return if (template.contains(marker)) template.replace(marker, content) else content
    }

    private fun readWorldInfoFormat(): String {
        return runCatching { AppModel.worldInfoFormat }.getOrDefault(AppModel.DEFAULT_WORLD_INFO_FORMAT)
    }

    private fun readScenarioFormat(): String {
        return runCatching { AppModel.scenarioFormat }.getOrDefault(AppModel.DEFAULT_SCENARIO_FORMAT)
    }

    private fun readPersonalityFormat(): String {
        return runCatching { AppModel.personalityFormat }.getOrDefault(AppModel.DEFAULT_PERSONALITY_FORMAT)
    }

    private fun readWorldInfoBudgetPercent(): Int {
        return runCatching { AppModel.worldInfoBudgetPercent }.getOrDefault(DEFAULT_WORLD_INFO_BUDGET_PERCENT)
    }

    private fun readCharacterMainPrompt(context: PromptBuildContext): String {
        val original = readMainPrompt()
        val override = context.character.systemPrompt.trim()
        if (override.isBlank()) return original
        return mMacroResolver.resolve(override, context, original = original)
    }

    private fun readCharacterPostHistoryInstructions(context: PromptBuildContext): String {
        val original = readPostHistoryInstructions()
        val override = context.character.postHistoryInstructions.trim()
        if (override.isBlank()) return original
        return mMacroResolver.resolve(override, context, original = original)
    }

    private data class PromptSections(
        val beforeHistory: List<PromptPiece>,
        val afterHistory: List<PromptPiece>
    ) {
        fun sumOf(selector: (LLMMessage) -> Int): Int {
            return beforeHistory.sumOf { selector(LLMMessage(it.role, it.content)) } +
                afterHistory.sumOf { selector(LLMMessage(it.role, it.content)) }
        }
    }

    private data class PromptPiece(
        val role: LLMMessageRole,
        val content: String,
        val importance: PromptPieceImportance,
        val original: String = content
    )

    private data class InChatPromptPiece(
        val role: LLMMessageRole,
        val content: String,
        val importance: PromptPieceImportance,
        val depth: Int,
        val order: Int,
        val tieBreaker: Long
    )

    private enum class PromptPieceImportance {
        Required,
        Optional
    }

    private companion object {
        const val MIN_PROMPT_TOKENS = 1024
        const val MIN_HISTORY_BUDGET = 512
        const val MIN_WORLD_BUDGET = 128
        const val REQUIRED_PIECE_MAX_TOKENS = 1600
        const val OPTIONAL_PIECE_MAX_TOKENS = 900
        const val DEFAULT_WORLD_INFO_BUDGET_PERCENT = 25
        const val USER_NOTE_DEPTH = 1
        const val AN_TOP_ORDER = Int.MIN_VALUE
        const val USER_NOTE_ORDER = Int.MIN_VALUE + 1
        const val AN_BOTTOM_ORDER = Int.MIN_VALUE + 2
        const val CHARACTER_NOTE_ORDER = Int.MIN_VALUE + 3
    }
}

data class PromptBuildResult(
    /** 实际提交给模型的请求。 */
    val request: LLMGenerationRequest,
    /** 本次构建后的世界书 timed effects 状态，需要由会话持久化。 */
    val worldInfoStateJson: String
)

private fun Int.toMessageRole(): LLMMessageRole {
    return when (this) {
        LorebookEntry.ROLE_USER -> LLMMessageRole.User
        LorebookEntry.ROLE_ASSISTANT -> LLMMessageRole.Assistant
        else -> LLMMessageRole.System
    }
}

private fun parseExampleBlocks(examples: String): List<String> {
    if (examples.isBlank() || examples.trim() == "<START>") return emptyList()
    val normalized = if (examples.trimStart().startsWith("<START>", ignoreCase = true)) {
        examples.trim()
    } else {
        "<START>\n${examples.trim()}"
    }
    return normalized
        .split(Regex("<START>", RegexOption.IGNORE_CASE))
        .drop(1)
        .map { "<START>\n${it.trim()}" }
        .filter { it.trim() != "<START>" }
}
