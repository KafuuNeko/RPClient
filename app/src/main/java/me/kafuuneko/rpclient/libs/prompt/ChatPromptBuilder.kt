package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.regex.RegexExecutionError
import me.kafuuneko.rpclient.libs.regex.RegexExecutionHit
import me.kafuuneko.rpclient.libs.regex.RegexExecutionMode
import me.kafuuneko.rpclient.libs.regex.RegexPlacement
import me.kafuuneko.rpclient.libs.regex.RegexScriptRuntime
import me.kafuuneko.rpclient.libs.utils.stripThinkBlocks

/**
 * 单角色聊天 Prompt 构建器。
 *
 * 负责宏展开、世界书激活与位置插入、Regex Prompt 处理和消息草稿组装，
 * 最终预算及协议消息约束由 [PromptRequestFinalizer] 统一完成。
 */
class ChatPromptBuilder(
    private val mMacroResolver: PromptMacroResolver,
    private val mHistoryBuilder: FormattedHistoryBuilder,
    private val mWorldBookActivator: WorldBookActivator,
    private val mRegexRuntime: RegexScriptRuntime = RegexScriptRuntime(
        me.kafuuneko.rpclient.libs.regex.RegexScriptEngine()
    ),
    private val mRequestFinalizer: PromptRequestFinalizer = PromptRequestFinalizer()
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
        val maxPromptTokens = (context.maxContextTokens - context.maxResponseTokens).coerceAtLeast(0)
        val worldBudget = maxPromptTokens * readWorldInfoBudgetPercent().coerceIn(0, 40) / 100
        val tokenizer = mRequestFinalizer.tokenizerFor(context.provider)
        val regexHits = mutableListOf<RegexExecutionHit>()
        val regexErrors = mutableListOf<RegexExecutionError>()
        val regexMacros = RegexScriptRuntime.macros(
            userName = context.userName,
            characterName = context.character.name,
            userDescription = context.userDescription,
            scenario = context.character.scenario
        )
        val rawWorldInfo = mWorldBookActivator.activateStructured(context)
        val activatedWorldInfo = rawWorldInfo
            .mapEntryContent { entry ->
                val result = mRegexRuntime.execute(
                    input = entry.content,
                    scripts = context.regexScripts,
                    placement = RegexPlacement.WorldInfo,
                    mode = RegexExecutionMode.Prompt,
                    macros = regexMacros
                )
                regexHits += result.hits
                regexErrors += result.errors
                entry.copy(content = result.text)
            }
        val worldSelection = fitWorldInfoToBudget(
            result = activatedWorldInfo,
            globalTokenBudget = worldBudget,
            promptTokenBudget = maxPromptTokens,
            lorebooks = context.candidateLorebooks,
            tokenizer = tokenizer
        )
        val worldInfo = worldSelection.result
        val outlets = worldInfo.outletEntries.mapValues { (_, entries) ->
            entries.sortedByDescending { it.order }.joinToString("\n") { it.content }
        }
        val fixedMessages = buildFixedMessages(context, worldInfo)
        val inChatPieces = buildInChatPieces(context, worldInfo)
        val examplePieces = buildExamplePieces(context, worldInfo)
        val historyMessages = context.messages.sanitizeThinkBlocks().mapIndexed { index, message ->
            val depth = context.messages.lastIndex - index
            val result = when (message.source) {
                ChatMessage.Source.User -> mRegexRuntime.execute(
                    input = message.content,
                    scripts = context.regexScripts,
                    placement = RegexPlacement.UserInput,
                    mode = RegexExecutionMode.Prompt,
                    macros = regexMacros,
                    depth = depth
                )
                ChatMessage.Source.Char -> mRegexRuntime.executeAiMessage(
                    input = message.content,
                    scripts = context.regexScripts,
                    mode = RegexExecutionMode.Prompt,
                    macros = regexMacros,
                    depth = depth
                )
                ChatMessage.Source.System,
                ChatMessage.Source.Summary -> null
            }
            if (result == null) {
                message
            } else {
                regexHits += result.hits
                regexErrors += result.errors
                message.copy(content = result.text)
            }
        }
        val historyText = mHistoryBuilder.build(historyMessages, context.userName, context.character.name)
        val chatMessages = buildChatMessages(
            historyMessages,
            context,
            historyText,
            inChatPieces,
            outlets
        ).toMutableList()
        val continueTarget = if (context.generationMode == PromptGenerationMode.Continue) {
            val targetIndex = chatMessages.indexOfLast {
                it.source.kind == PromptSourceKind.ChatHistory &&
                    it.role == LLMMessageRole.Assistant
            }
            if (targetIndex >= 0) chatMessages.removeAt(targetIndex) else null
        } else {
            null
        }

        val rawDrafts = buildList {
            fixedMessages.beforeHistory.forEach {
                add(it.resolve(context, historyText, outlets))
            }
            examplePieces.forEach {
                add(it.resolve(context, historyText, outlets))
            }
            buildNewChatPiece(context, historyText, outlets)?.let(::add)
            addAll(chatMessages)
            fixedMessages.afterHistory.forEach {
                add(it.resolve(context, historyText, outlets))
            }
            continueTarget?.let(::add)
            buildGenerationControlPiece(context)?.let {
                add(it.resolve(context, historyText, outlets))
            }
        }
        val fallbackDrafts = rawDrafts.ifEmpty {
            listOf(
                PromptMessageDraft(
                    role = fallbackRole(context),
                    content = fallbackPrompt(context),
                    source = PromptSource(fallbackSourceKind(context)),
                    retentionPriority = PRIORITY_ESSENTIAL,
                    canDrop = false
                )
            )
        }
        val drafts = ensureTerminalCharacterReplyTurn(
            drafts = fallbackDrafts,
            context = context,
            history = historyText,
            outlets = outlets
        )
        val finalized = mRequestFinalizer.finalize(
            drafts = drafts,
            provider = context.provider,
            model = context.provider?.model,
            options = LLMGenerationOptions(
                temperature = context.provider?.temperature,
                maxTokens = context.maxResponseTokens,
                topP = context.provider?.topP
            ),
            includeReasoningInContent = true,
            maxContextTokens = context.maxContextTokens,
            maxResponseTokens = context.maxResponseTokens,
            postProcessingMode = readPostProcessingMode(context.provider),
            strictPromptPlaceholder = DEFAULT_STRICT_PROMPT_PLACEHOLDER,
            postProcessingNames = PromptPostProcessingNames(
                userName = context.userName,
                characterName = context.character.name
            ),
            preOmittedItems = worldSelection.omittedItems
        )
        val inspection = finalized.inspection.copy(
            regexExecutions = regexHits,
            regexErrors = regexErrors
        )
        val selectedWorldInfoIds = worldInfo.activatedEntries.map { it.id }.toSet()
        val stateResult = mWorldBookActivator.resolveNextState(
            rawWorldInfo
                .filterEntries(selectedWorldInfoIds)
                .retainStateEntries(inspection)
        )
        return PromptBuildResult(
            request = finalized.request,
            worldInfoStateJson = stateResult.nextStateJson,
            inspection = inspection
        )
    }

    private fun buildFixedMessages(
        context: PromptBuildContext,
        worldInfo: WorldBookActivationResult
    ): PromptSections {
        val beforeHistory = mutableListOf<PromptPiece>()
        val summaryPosition = readSummaryInjectionPosition()
        if (summaryPosition == SummaryInjectionPosition.BeforeMain) {
            buildSummaryPiece(context)?.let { beforeHistory += it }
        }
        // 主提示词描述的是“让角色生成下一条回复”这一任务，只能用于普通回复和重新生成。
        // 扮演用户或续写时继续注入角色卡覆盖提示，会让同一请求同时出现两个互斥目标。
        if (context.generationMode.usesCharacterReplyTask()) {
            beforeHistory += PromptPiece(
                LLMMessageRole.System,
                readCharacterMainPrompt(context),
                PromptSource(PromptSourceKind.MainPrompt),
                PRIORITY_ESSENTIAL,
                false
            )
        }
        if (summaryPosition == SummaryInjectionPosition.AfterMain) {
            buildSummaryPiece(context)?.let { beforeHistory += it }
        }
        worldInfo.beforeCharacter.forEach {
            beforeHistory += PromptPiece(
                role = LLMMessageRole.System,
                content = formatWorldInfo(it.content),
                source = PromptSource(PromptSourceKind.WorldInfo, it.name, it.id),
                retentionPriority = PRIORITY_WORLD_INFO,
                canDrop = true
            )
        }
        beforeHistory += PromptPiece.required(
            LLMMessageRole.System,
            context.userDescription,
            PromptSourceKind.UserPersona
        )
        beforeHistory += PromptPiece.required(
            LLMMessageRole.System,
            context.character.description,
            PromptSourceKind.CharacterDescription
        )
        beforeHistory += PromptPiece.required(
            LLMMessageRole.System,
            formatPersonality(context.character.personality),
            PromptSourceKind.CharacterPersonality
        )
        beforeHistory += PromptPiece.required(
            LLMMessageRole.System,
            formatScenario(context.character.scenario),
            PromptSourceKind.Scenario
        )
        beforeHistory += PromptPiece(
            LLMMessageRole.System,
            readAuxiliaryPrompt(),
            PromptSource(PromptSourceKind.AuxiliaryPrompt),
            PRIORITY_AUXILIARY,
            true
        )
        worldInfo.afterCharacter.forEach {
            beforeHistory += PromptPiece(
                role = LLMMessageRole.System,
                content = formatWorldInfo(it.content),
                source = PromptSource(PromptSourceKind.WorldInfo, it.name, it.id),
                retentionPriority = PRIORITY_WORLD_INFO,
                canDrop = true
            )
        }

        val afterHistory = buildList {
            // PHI 与主提示词共同约束“下一条角色回复”，特殊生成模式必须由自己的任务提示接管。
            if (context.generationMode.usesCharacterReplyTask()) {
                add(
                    PromptPiece.required(
                        role = LLMMessageRole.User,
                        content = readCharacterPostHistoryInstructions(context),
                        sourceKind = PromptSourceKind.PostHistoryInstructions
                    )
                )
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
        if (readSummaryInjectionPosition() == SummaryInjectionPosition.InChat) {
            buildSummaryPiece(context)?.let {
                pieces += InChatPromptPiece(
                    role = readSummaryInjectionRole().toMessageRole(),
                    content = it.content,
                    source = it.source,
                    retentionPriority = PRIORITY_ESSENTIAL,
                    canDrop = false,
                    depth = readSummaryInjectionDepth(),
                    order = SUMMARY_ORDER,
                    tieBreaker = Long.MIN_VALUE
                )
            }
        }
        worldInfo.anTop.forEachIndexed { index, entry ->
            pieces += InChatPromptPiece(
                role = LLMMessageRole.System,
                content = entry.content,
                source = PromptSource(PromptSourceKind.WorldInfo, entry.name, entry.id),
                retentionPriority = PRIORITY_WORLD_INFO,
                canDrop = true,
                depth = USER_NOTE_DEPTH,
                order = AN_TOP_ORDER,
                tieBreaker = index.toLong()
            )
        }
        context.session.userNote.takeIf { it.isNotBlank() }?.let {
            pieces += InChatPromptPiece(
                role = LLMMessageRole.System,
                content = it,
                source = PromptSource(PromptSourceKind.UserNote),
                retentionPriority = PRIORITY_USER_NOTE,
                canDrop = true,
                depth = USER_NOTE_DEPTH,
                order = USER_NOTE_ORDER,
                tieBreaker = Long.MIN_VALUE
            )
        }
        worldInfo.anBottom.forEachIndexed { index, entry ->
            pieces += InChatPromptPiece(
                role = LLMMessageRole.System,
                content = entry.content,
                source = PromptSource(PromptSourceKind.WorldInfo, entry.name, entry.id),
                retentionPriority = PRIORITY_WORLD_INFO,
                canDrop = true,
                depth = USER_NOTE_DEPTH,
                order = AN_BOTTOM_ORDER,
                tieBreaker = index.toLong()
            )
        }
        // Character Note 通常直接约束角色输出，在扮演用户和续写时与本轮任务存在同类冲突。
        if (context.generationMode.usesCharacterReplyTask()) {
            context.character.depthPromptPrompt.takeIf { it.isNotBlank() }?.let {
                pieces += InChatPromptPiece(
                    role = context.character.depthPromptRole.toMessageRole(),
                    content = it,
                    source = PromptSource(PromptSourceKind.CharacterNote),
                    retentionPriority = PRIORITY_CHARACTER_NOTE,
                    canDrop = true,
                    depth = context.character.depthPromptDepth.coerceAtLeast(0),
                    order = CHARACTER_NOTE_ORDER,
                    tieBreaker = Long.MIN_VALUE + 1
                )
            }
        }
        worldInfo.depthEntries.forEach { group ->
            val sources = group.entries.map {
                PromptSource(PromptSourceKind.WorldInfo, it.name, it.id)
            }
            val first = group.entries.firstOrNull() ?: return@forEach
            pieces += InChatPromptPiece(
                role = group.role,
                content = group.entries.joinToString("\n") { it.content },
                source = sources.first(),
                retentionPriority = PRIORITY_WORLD_INFO,
                canDrop = true,
                depth = group.depth.coerceAtLeast(0),
                order = first.order,
                tieBreaker = first.id,
                sources = sources
            )
        }
        return pieces
    }

    private fun buildExamplePieces(
        context: PromptBuildContext,
        worldInfo: WorldBookActivationResult
    ): List<PromptPiece> {
        // 示例对话以 <START> 分块，每个块作为独立的上下文预算单元。
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
                        add(
                            PromptPiece(
                                LLMMessageRole.System,
                                marker,
                                PromptSource(PromptSourceKind.ExampleDialogue),
                                PRIORITY_EXAMPLE,
                                true
                            )
                        )
                    }
                    val parsed = parseExampleMessages(
                        block = block,
                        userName = context.userName,
                        characterName = context.character.name
                    )
                    if (parsed.isEmpty()) {
                        add(
                            PromptPiece(
                                LLMMessageRole.System,
                                block,
                                PromptSource(PromptSourceKind.ExampleDialogue),
                                PRIORITY_EXAMPLE,
                                true
                            )
                        )
                    } else {
                        parsed.forEach { message ->
                            add(
                                PromptPiece(
                                    message.role,
                                    message.content,
                                    PromptSource(PromptSourceKind.ExampleDialogue),
                                    PRIORITY_EXAMPLE,
                                    true
                                )
                            )
                        }
                    }
                }
            }
    }

    private fun buildNewChatPiece(
        context: PromptBuildContext,
        history: String,
        outlets: Map<String, String>
    ): PromptMessageDraft? {
        if (context.messages.isEmpty() && context.currentUserMessage.isNullOrBlank()) return null
        val marker = readNewChatPrompt()
        if (marker.isBlank()) return null
        return PromptPiece(
            LLMMessageRole.System,
            marker,
            PromptSource(PromptSourceKind.NewChatMarker),
            PRIORITY_NEW_CHAT,
            true
        )
            .resolve(context, history, outlets)
    }

    private fun buildChatMessages(
        historyMessages: List<ChatMessage>,
        context: PromptBuildContext,
        historyText: String,
        inChatPieces: List<InChatPromptPiece>,
        outlets: Map<String, String>
    ): List<PromptMessageDraft> {
        val lastHistoryIndex = historyMessages.lastIndex
        val chatMessages = historyMessages.mapIndexed { index, message ->
            message.toPromptDraft(
                retentionPriority = PRIORITY_HISTORY_BASE + index,
                canDrop = index != lastHistoryIndex
            )
        }.toMutableList()
        context.currentUserMessage?.takeIf { it.isNotBlank() }?.let {
            val regexResult = mRegexRuntime.execute(
                input = it,
                scripts = context.regexScripts,
                placement = RegexPlacement.UserInput,
                mode = RegexExecutionMode.Prompt,
                macros = RegexScriptRuntime.macros(
                    context.userName,
                    context.character.name,
                    context.userDescription,
                    context.character.scenario
                ),
                depth = 0
            )
            chatMessages += PromptMessageDraft(
                role = LLMMessageRole.User,
                content = resolve(regexResult.text, context, historyText, it, outlets),
                source = PromptSource(PromptSourceKind.ChatHistory, "Current user message"),
                retentionPriority = PRIORITY_ESSENTIAL,
                canDrop = false
            )
        }
        if (chatMessages.isEmpty() || inChatPieces.isEmpty()) return chatMessages

        val injections = inChatPieces
            .mapNotNull { piece ->
                val resolved = piece.resolve(context, historyText, outlets)
                if (resolved.content.isBlank()) null else piece.insertionIndex(chatMessages) to (piece to resolved)
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, pieces) ->
                pieces.sortedWith(
                    compareBy<Pair<InChatPromptPiece, PromptMessageDraft>> {
                        it.first.order
                    }.thenBy { it.first.tieBreaker }
                )
                    .map { it.second }
            }

        return buildList {
            for (index in 0..chatMessages.size) {
                injections[index]?.let { addAll(it) }
                if (index < chatMessages.size) add(chatMessages[index])
            }
        }
    }

    private fun InChatPromptPiece.insertionIndex(chatMessages: List<PromptMessageDraft>): Int {
        // depth=0 表示追加到聊天末尾；depth=1 表示插入到最后一条消息之前，以此类推。
        return (chatMessages.size - depth).coerceIn(0, chatMessages.size)
    }

    /**
     * 构建位于完整聊天上下文末尾的生成任务提示。
     *
     * Impersonate 与 Continue 使用 user 角色，确保严格要求用户末尾轮次的模型
     * 不会把控制指令提升到开头，也不会因最后一条是 system 而返回空结果。
     */
    private fun buildGenerationControlPiece(context: PromptBuildContext): PromptPiece? {
        return when (context.generationMode) {
            PromptGenerationMode.Normal,
            PromptGenerationMode.Regenerate -> null
            PromptGenerationMode.Continue -> PromptPiece.required(
                LLMMessageRole.User,
                readContinueNudgePrompt(),
                PromptSourceKind.ContinueNudge
            )
            PromptGenerationMode.Impersonate -> PromptPiece.required(
                LLMMessageRole.User,
                readImpersonationPrompt(),
                PromptSourceKind.ImpersonationNudge
            )
        }?.takeIf { it.content.isNotBlank() }
    }

    /**
     * 普通生成应由 user 轮次触发。
     *
     * PHI、depth=0 世界书或仅有角色开场白时，请求可能以 system/assistant 结束；
     * 此处追加最小任务提示，兼容要求由 user 轮次触发生成的模型和协议实现。
     */
    private fun ensureTerminalCharacterReplyTurn(
        drafts: List<PromptMessageDraft>,
        context: PromptBuildContext,
        history: String,
        outlets: Map<String, String>
    ): List<PromptMessageDraft> {
        if (!context.generationMode.usesCharacterReplyTask()) return drafts
        if (drafts.lastOrNull()?.role == LLMMessageRole.User) return drafts
        val nudge = PromptPiece.required(
            role = LLMMessageRole.User,
            content = DEFAULT_CHARACTER_REPLY_NUDGE,
            sourceKind = PromptSourceKind.CharacterReplyNudge
        ).resolve(context, history, outlets)
        return drafts + nudge
    }

    /** 特殊模式即使上下文为空，也不能回退到要求角色回复的主提示词。 */
    private fun fallbackPrompt(context: PromptBuildContext): String {
        return when (context.generationMode) {
            PromptGenerationMode.Normal,
            PromptGenerationMode.Regenerate -> readMainPrompt()
            PromptGenerationMode.Continue -> readContinueNudgePrompt()
            PromptGenerationMode.Impersonate -> readImpersonationPrompt()
        }
    }

    private fun fallbackRole(context: PromptBuildContext): LLMMessageRole {
        return if (context.generationMode.usesCharacterReplyTask()) {
            LLMMessageRole.System
        } else {
            LLMMessageRole.User
        }
    }

    private fun fallbackSourceKind(context: PromptBuildContext): PromptSourceKind {
        return when (context.generationMode) {
            PromptGenerationMode.Normal,
            PromptGenerationMode.Regenerate -> PromptSourceKind.MainPrompt
            PromptGenerationMode.Continue -> PromptSourceKind.ContinueNudge
            PromptGenerationMode.Impersonate -> PromptSourceKind.ImpersonationNudge
        }
    }

    private fun PromptPiece.resolve(
        context: PromptBuildContext,
        history: String,
        outlets: Map<String, String>
    ): PromptMessageDraft {
        val resolved = resolve(content, context, history, original, outlets).trim()
        return PromptMessageDraft(
            role = role,
            content = resolved,
            source = source,
            retentionPriority = retentionPriority,
            canDrop = canDrop
        )
    }

    private fun InChatPromptPiece.resolve(
        context: PromptBuildContext,
        history: String,
        outlets: Map<String, String>
    ): PromptMessageDraft {
        val resolved = resolve(content, context, history, content, outlets).trim()
        return PromptMessageDraft(
            role = role,
            content = resolved,
            source = source,
            retentionPriority = retentionPriority,
            canDrop = canDrop,
            sources = sources
        )
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

    private fun ChatMessage.toPromptDraft(
        retentionPriority: Int,
        canDrop: Boolean
    ): PromptMessageDraft {
        val role = when (source) {
            ChatMessage.Source.User -> LLMMessageRole.User
            ChatMessage.Source.Char -> LLMMessageRole.Assistant
            ChatMessage.Source.System -> LLMMessageRole.System
            ChatMessage.Source.Summary -> error("Summary snapshots must not be added to chat history")
        }
        return PromptMessageDraft(
            role = role,
            content = content,
            source = PromptSource(PromptSourceKind.ChatHistory, "Message #$id"),
            retentionPriority = retentionPriority,
            canDrop = canDrop
        )
    }

    private fun List<ChatMessage>.sanitizeThinkBlocks(): List<ChatMessage> {
        if (runCatching { AppModel.includeThinkInContext }.getOrDefault(false)) return this
        // 已保存的推理块只用于 UI 展示；默认不再带回后续上下文，避免模型复读或继承旧思路。
        return mapNotNull { message ->
            val cleaned = message.content.stripThinkBlocks().trim()
            when {
                cleaned.isBlank() -> null
                cleaned == message.content -> message
                else -> message.copy(content = cleaned)
            }
        }
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

    private fun readPostProcessingMode(provider: LLMProvider?): PromptPostProcessingMode {
        val ordinal = provider?.promptPostProcessingMode
            ?: PromptPostProcessingMode.None.ordinal
        return PromptPostProcessingMode.fromOrdinal(ordinal)
    }

    private fun readSummaryInjectionPosition(): SummaryInjectionPosition {
        return SummaryInjectionPosition.fromPersistedValue(
            runCatching { AppModel.summaryInjectionPosition }
                .getOrDefault(SummaryInjectionPosition.default.persistedValue)
        )
    }

    private fun readSummaryInjectionDepth(): Int {
        return runCatching { AppModel.summaryInjectionDepth }
            .getOrDefault(2)
            .coerceAtLeast(0)
    }

    private fun readSummaryInjectionRole(): SummaryInjectionRole {
        return SummaryInjectionRole.fromPersistedValue(
            runCatching { AppModel.summaryInjectionRole }.getOrDefault(0)
        )
    }

    private fun buildSummaryPiece(context: PromptBuildContext): PromptPiece? {
        if (context.summary.isBlank()) return null
        val template = runCatching { AppModel.summaryInjectionTemplate }
            .getOrDefault(AppModel.DEFAULT_SUMMARY_INJECTION_TEMPLATE)
        val content = if (template.contains("{{summary}}", ignoreCase = true)) {
            template.replace("{{summary}}", context.summary, ignoreCase = true)
        } else {
            listOf(template, context.summary).filter { it.isNotBlank() }.joinToString("\n")
        }
        return PromptPiece.required(
            role = LLMMessageRole.System,
            content = content,
            sourceKind = PromptSourceKind.Summary
        )
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
        val systemPrompt = override.ifBlank { original }
        return mMacroResolver.resolve(systemPrompt, context, original = original)
    }

    private fun readCharacterPostHistoryInstructions(context: PromptBuildContext): String {
        val original = readPostHistoryInstructions()
        val override = context.character.postHistoryInstructions.trim()
        val instructions = override.ifBlank { original }
        return mMacroResolver.resolve(instructions, context, original = original)
    }

    private data class PromptSections(
        val beforeHistory: List<PromptPiece>,
        val afterHistory: List<PromptPiece>
    )

    private data class PromptPiece(
        val role: LLMMessageRole,
        val content: String,
        val source: PromptSource,
        val retentionPriority: Int,
        val canDrop: Boolean,
        val original: String = content
    ) {
        companion object {
            fun required(
                role: LLMMessageRole,
                content: String,
                sourceKind: PromptSourceKind
            ): PromptPiece {
                return PromptPiece(
                    role = role,
                    content = content,
                    source = PromptSource(sourceKind),
                    retentionPriority = PRIORITY_ESSENTIAL,
                    canDrop = false
                )
            }
        }
    }

    private data class InChatPromptPiece(
        val role: LLMMessageRole,
        val content: String,
        val source: PromptSource,
        val retentionPriority: Int,
        val canDrop: Boolean,
        val depth: Int,
        val order: Int,
        val tieBreaker: Long,
        val sources: List<PromptSource> = listOf(source)
    )

    private companion object {
        const val DEFAULT_WORLD_INFO_BUDGET_PERCENT = 25
        const val PRIORITY_EXAMPLE = 10
        const val PRIORITY_AUXILIARY = 20
        const val PRIORITY_NEW_CHAT = 30
        const val PRIORITY_WORLD_INFO = 40
        const val PRIORITY_HISTORY_BASE = 100
        const val PRIORITY_USER_NOTE = 300
        const val PRIORITY_CHARACTER_NOTE = 310
        const val PRIORITY_ESSENTIAL = 1_000
        const val USER_NOTE_DEPTH = 4
        const val SUMMARY_ORDER = Int.MIN_VALUE
        const val AN_TOP_ORDER = Int.MIN_VALUE + 1
        const val USER_NOTE_ORDER = Int.MIN_VALUE + 2
        const val AN_BOTTOM_ORDER = Int.MIN_VALUE + 3
        const val CHARACTER_NOTE_ORDER = Int.MIN_VALUE + 4
        const val DEFAULT_CHARACTER_REPLY_NUDGE = "[Write {{char}}'s next reply.]"
    }
}

/** 单聊 Prompt 构建结果及需要回写会话的运行时元数据。 */
data class PromptBuildResult(
    /** 实际提交给模型的请求。 */
    val request: LLMGenerationRequest,
    /** 本次构建后的世界书 timed effects 状态，需要由会话持久化。 */
    val worldInfoStateJson: String,
    /** 宏展开、后处理和最终预算完成后的可解释 Prompt 明细。 */
    val inspection: PromptInspection
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
