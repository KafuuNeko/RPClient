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
     * 构建真实发送给 LLM 的 Chat Completion 请求。
     *
     * 这里按 SillyTavern 风格将长期设定、Summary、世界书、近期历史和后置指令分层组织，
     * 并在字符数估算的 token 预算内优先保留核心设定与最近历史。
     */
    fun build(context: PromptBuildContext): LLMGenerationRequest {
        val maxPromptTokens = (context.maxContextTokens - context.maxResponseTokens).coerceAtLeast(MIN_PROMPT_TOKENS)
        val worldBudget = (maxPromptTokens * AppModel.worldInfoBudgetPercent.coerceIn(0, 40) / 100)
            .coerceAtLeast(MIN_WORLD_BUDGET)
        val activatedEntries = fitWorldInfo(mWorldBookActivator.activate(context), worldBudget)
        val fixedMessages = buildFixedMessages(context, activatedEntries)
        val fixedTokenCount = fixedMessages.sumOf { estimateTokens(it.content) }
        val historyBudget = (maxPromptTokens - fixedTokenCount).coerceAtLeast(MIN_HISTORY_BUDGET)
        val historyMessages = fitHistory(context.messages, historyBudget)
        val historyText = mHistoryBuilder.build(historyMessages, context.userName, context.character.name)

        val messages = mutableListOf<LLMMessage>()
        fixedMessages.beforeHistory.forEach { messages += it.resolve(context, historyText) }
        historyMessages.forEach { messages += it.toLlmMessage() }
        context.currentUserMessage?.takeIf { it.isNotBlank() }?.let {
            messages += LLMMessage(LLMMessageRole.User, resolve(it, context, historyText))
        }
        fixedMessages.afterHistory.forEach { messages += it.resolve(context, historyText) }

        return LLMGenerationRequest(
            messages = messages.ifEmpty { listOf(LLMMessage(LLMMessageRole.System, mainPrompt())) },
            model = context.provider?.model,
            options = LLMGenerationOptions(
                temperature = context.provider?.temperature,
                maxTokens = context.maxResponseTokens,
                topP = context.provider?.topP
            )
        )
    }

    private fun buildFixedMessages(
        context: PromptBuildContext,
        activatedEntries: List<LorebookEntry>
    ): PromptSections {
        // 固定区承载角色扮演的稳定约束；后续裁剪历史时不应优先丢失这些内容。
        val beforeHistory = mutableListOf<PromptPiece>()
        beforeHistory += PromptPiece(LLMMessageRole.System, mainPrompt(), PromptPieceImportance.Required)
        beforeHistory += PromptPiece(LLMMessageRole.System, context.character.description, PromptPieceImportance.Required)
        beforeHistory += PromptPiece(LLMMessageRole.System, context.character.personality, PromptPieceImportance.Required)
        beforeHistory += PromptPiece(LLMMessageRole.System, context.character.scenario, PromptPieceImportance.Required)
        beforeHistory += PromptPiece(LLMMessageRole.System, context.session.summarize, PromptPieceImportance.Required)
        beforeHistory += PromptPiece(LLMMessageRole.System, context.session.creatorNotes ?: context.character.creatorNotes, PromptPieceImportance.Optional)
        beforeHistory += PromptPiece(LLMMessageRole.System, context.session.userNote, PromptPieceImportance.Optional)
        beforeHistory += activatedEntries.map {
            PromptPiece(LLMMessageRole.System, it.toWorldInfoText(), PromptPieceImportance.Optional)
        }

        val afterHistory = listOf(
            PromptPiece(
                role = LLMMessageRole.System,
                content = context.character.postHistoryInstructions,
                importance = PromptPieceImportance.Required
            )
        )

        return PromptSections(
            beforeHistory = beforeHistory.filter { it.content.isNotBlank() },
            afterHistory = afterHistory.filter { it.content.isNotBlank() }
        )
    }

    private fun fitHistory(messages: List<ChatMessage>, tokenBudget: Int): List<ChatMessage> {
        // 历史从新到旧纳入预算，长对话时优先保留最近上下文。
        val selected = ArrayDeque<ChatMessage>()
        var usedTokens = 0
        messages.asReversed().forEach { message ->
            val nextTokens = estimateTokens(message.content)
            if (selected.isNotEmpty() && usedTokens + nextTokens > tokenBudget) return@forEach
            selected.addFirst(message)
            usedTokens += nextTokens
        }
        return selected.toList()
    }

    private fun fitWorldInfo(entries: List<LorebookEntry>, tokenBudget: Int): List<LorebookEntry> {
        // 世界书使用独立预算，避免被触发的 lore 过多时挤掉核心 prompt 和聊天历史。
        val selected = mutableListOf<LorebookEntry>()
        var usedTokens = 0
        entries.sortedWith(compareByDescending<LorebookEntry> { it.order }.thenBy { it.id })
            .forEach { entry ->
                val nextTokens = estimateTokens(entry.toWorldInfoText())
                if (selected.isNotEmpty() && usedTokens + nextTokens > tokenBudget) return@forEach
                selected += entry
                usedTokens += nextTokens
            }
        return selected.sortedWith(compareBy<LorebookEntry> { it.order }.thenBy { it.id })
    }

    private fun PromptPiece.resolve(context: PromptBuildContext, history: String): LLMMessage {
        // 单条固定 prompt 也做上限保护，避免超长角色卡独占整个上下文。
        val resolved = resolve(content, context, history).trim()
        val maxTokens = if (importance == PromptPieceImportance.Required) REQUIRED_PIECE_MAX_TOKENS else OPTIONAL_PIECE_MAX_TOKENS
        return LLMMessage(role, trimToTokenLimit(resolved, maxTokens))
    }

    private fun mainPrompt(): String {
        return AppModel.mainPrompt?.takeIf { it.isNotBlank() } ?: DEFAULT_MAIN_PROMPT
    }

    private fun resolve(text: String, context: PromptBuildContext, history: String): String {
        return mMacroResolver.resolve(text, context, history)
    }

    private fun ChatMessage.toLlmMessage(): LLMMessage {
        val role = when (source) {
            ChatMessage.Source.User -> LLMMessageRole.User
            ChatMessage.Source.Char -> LLMMessageRole.Assistant
            ChatMessage.Source.System -> LLMMessageRole.System
        }
        return LLMMessage(role, content)
    }

    private fun LorebookEntry.toWorldInfoText(): String {
        return if (depth <= 0) {
            content
        } else {
            "[World Info / Depth $depth]\n$content"
        }
    }

    private fun trimToTokenLimit(text: String, maxTokens: Int): String {
        if (estimateTokens(text) <= maxTokens) return text
        val maxChars = (maxTokens * 3).coerceAtLeast(120)
        return text.take(maxChars).trimEnd() + "\n[Truncated to fit context]"
    }

    private fun estimateTokens(text: String): Int {
        return (text.length / 3).coerceAtLeast(1)
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
        val importance: PromptPieceImportance
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
        const val DEFAULT_MAIN_PROMPT =
            "Write {{char}}'s next reply in a fictional chat between {{char}} and {{user}}.\n" +
                "Write one reply only. Do not decide what {{user}} says or does."
    }
}
