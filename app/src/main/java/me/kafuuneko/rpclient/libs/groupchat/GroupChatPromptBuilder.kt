package me.kafuuneko.rpclient.libs.groupchat

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.prompt.DEFAULT_STRICT_PROMPT_PLACEHOLDER
import me.kafuuneko.rpclient.libs.prompt.PromptInspection
import me.kafuuneko.rpclient.libs.prompt.PromptMessageDraft
import me.kafuuneko.rpclient.libs.prompt.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.PromptPostProcessingNames
import me.kafuuneko.rpclient.libs.prompt.PromptRequestFinalizer
import me.kafuuneko.rpclient.libs.prompt.PromptSource
import me.kafuuneko.rpclient.libs.prompt.PromptSourceKind
import me.kafuuneko.rpclient.libs.prompt.SummaryInjectionPosition
import me.kafuuneko.rpclient.libs.prompt.SummaryInjectionRole
import me.kafuuneko.rpclient.libs.prompt.WorldBookActivationResult
import me.kafuuneko.rpclient.libs.prompt.WorldBookActivator
import me.kafuuneko.rpclient.libs.prompt.WorldBookGenerationType
import me.kafuuneko.rpclient.libs.prompt.WorldBookScanMessage
import me.kafuuneko.rpclient.libs.prompt.WorldBookScanContext
import me.kafuuneko.rpclient.libs.prompt.fitWorldInfoToBudget
import me.kafuuneko.rpclient.libs.prompt.filterEntries
import me.kafuuneko.rpclient.libs.prompt.mapEntryContent
import me.kafuuneko.rpclient.libs.prompt.parseExampleMessages
import me.kafuuneko.rpclient.libs.prompt.retainStateEntries
import me.kafuuneko.rpclient.libs.regex.RegexExecutionError
import me.kafuuneko.rpclient.libs.regex.RegexExecutionHit
import me.kafuuneko.rpclient.libs.regex.RegexExecutionMode
import me.kafuuneko.rpclient.libs.regex.RegexPlacement
import me.kafuuneko.rpclient.libs.regex.RegexScriptRuntime
import me.kafuuneko.rpclient.libs.regex.ScopedRegexScript
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.repository.GroupChatMemberData
import me.kafuuneko.rpclient.libs.utils.stripThinkBlocks

/** 构建一次群聊生成请求所需的完整上下文。 */
data class GroupChatPromptContext(
    val session: GroupChatSession,
    val members: List<GroupChatMemberData>,
    val speaker: Character,
    val messages: List<GroupChatMessage>,
    val provider: LLMProvider,
    val summary: String = "",
    val candidateLorebookEntries: List<LorebookEntry> = emptyList(),
    val candidateLorebooks: Map<Long, Lorebook> = emptyMap(),
    val recursiveScanningLorebookIds: Set<Long> = emptySet(),
    val generationMode: GroupChatGenerationMode = GroupChatGenerationMode.Normal,
    val regexScripts: List<ScopedRegexScript> = emptyList()
)

/** 群聊回复的生成模式。 */
enum class GroupChatGenerationMode {
    Normal,
    Continue,
    Regenerate,
    Impersonate
}

/** 仅普通群聊回复和重新生成需要“指定角色下一条回复”的主任务及 Group Nudge。 */
private fun GroupChatGenerationMode.usesCharacterReplyTask(): Boolean {
    return this == GroupChatGenerationMode.Normal || this == GroupChatGenerationMode.Regenerate
}

/** 提示词构建结果，同时返回需要持久化的世界书时序状态。 */
data class GroupChatPromptBuildResult(
    val request: LLMGenerationRequest,
    val worldInfoStateJson: String,
    val inspection: PromptInspection
)

/**
 * 群聊 Prompt 构建器。
 *
 * 将成员角色卡、群聊历史、世界书和 Regex 脚本组装为统一草稿，
 * 最终交由 PromptRequestFinalizer 执行协议后处理与上下文预算裁剪。
 */
class GroupChatPromptBuilder(
    private val mWorldBookActivator: WorldBookActivator = WorldBookActivator(),
    private val mRegexRuntime: RegexScriptRuntime = RegexScriptRuntime(
        me.kafuuneko.rpclient.libs.regex.RegexScriptEngine()
    ),
    private val mRequestFinalizer: PromptRequestFinalizer = PromptRequestFinalizer()
) {
    /** 构建可直接发送给模型的群聊生成请求。 */
    fun build(context: GroupChatPromptContext): LLMGenerationRequest {
        return buildWithMetadata(context).request
    }

    /** 构建生成请求，并携带世界书激活后的下一状态。 */
    fun buildWithMetadata(context: GroupChatPromptContext): GroupChatPromptBuildResult {
        val maxPromptTokens = (
            context.provider.contextTokens - context.provider.maxTokens
        ).coerceAtLeast(0)
        val worldBudget =
            maxPromptTokens * readWorldInfoBudgetPercent().coerceIn(0, 40) / 100
        val regexHits = mutableListOf<RegexExecutionHit>()
        val regexErrors = mutableListOf<RegexExecutionError>()
        val regexMacros = RegexScriptRuntime.macros(
            userName = context.session.userName,
            characterName = context.speaker.name,
            userDescription = context.session.userDescription,
            scenario = context.session.scenario,
            groupNames = context.memberNames()
        )
        val rawWorldInfo = activateWorldInfo(context)
        val activatedWorldInfo = rawWorldInfo.mapEntryContent { entry ->
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
            tokenizer = mRequestFinalizer.tokenizerFor(context.provider)
        )
        val worldInfo = worldSelection.result
        val fixedMessages = buildFixedMessages(context, worldInfo)
        val inChatPieces = buildInChatPieces(context, worldInfo)
        val history = sanitizeHistory(context.messages).mapIndexed { index, message ->
            val depth = context.messages.lastIndex - index
            val result = when (message.source) {
                GroupChatMessage.Source.User -> mRegexRuntime.execute(
                    input = message.content,
                    scripts = context.regexScripts,
                    placement = RegexPlacement.UserInput,
                    mode = RegexExecutionMode.Prompt,
                    macros = regexMacros,
                    depth = depth
                )
                GroupChatMessage.Source.Character -> mRegexRuntime.executeAiMessage(
                    input = message.content,
                    scripts = context.regexScripts,
                    mode = RegexExecutionMode.Prompt,
                    macros = regexMacros,
                    depth = depth
                )
                GroupChatMessage.Source.System -> null
            }
            if (result == null) {
                message
            } else {
                regexHits += result.hits
                regexErrors += result.errors
                message.copy(content = result.text)
            }
        }
        val historyMessages = history.mapIndexed { index, message ->
            message.toPromptDraft(
                userName = context.session.userName,
                retentionPriority = PRIORITY_HISTORY_BASE + index,
                canDrop = index != history.lastIndex
            )
        }.toMutableList()
        insertInChatPieces(historyMessages, inChatPieces)
        val continueTarget = if (context.generationMode == GroupChatGenerationMode.Continue) {
            val targetIndex = historyMessages.indexOfLast {
                it.source.kind == PromptSourceKind.ChatHistory &&
                    it.role == LLMMessageRole.Assistant
            }
            if (targetIndex >= 0) historyMessages.removeAt(targetIndex) else null
        } else {
            null
        }
        if (context.generationMode.usesCharacterReplyTask()) {
            context.groupNudgePrompt()
                .resolve(context, context.memberNames())
                .takeIf { it.isNotBlank() }
                ?.let {
                    historyMessages += requiredUser(it, PromptSourceKind.GroupNudge)
                }
        }

        val rawDrafts = buildList {
            addAll(fixedMessages.beforeHistory)
            addAll(historyMessages)
            addAll(fixedMessages.afterHistory)
            continueTarget?.let(::add)
            buildGenerationControlDraft(context)?.let(::add)
        }
        val finalized = mRequestFinalizer.finalize(
            drafts = ensureTerminalCharacterReplyTurn(rawDrafts, context),
            provider = context.provider,
            model = context.provider.model,
            options = LLMGenerationOptions(
                temperature = context.provider.temperature,
                maxTokens = context.provider.maxTokens,
                topP = context.provider.topP
            ),
            includeReasoningInContent = true,
            maxContextTokens = context.provider.contextTokens,
            maxResponseTokens = context.provider.maxTokens,
            postProcessingMode = readPostProcessingMode(context.provider),
            strictPromptPlaceholder = DEFAULT_STRICT_PROMPT_PLACEHOLDER,
            postProcessingNames = PromptPostProcessingNames(
                userName = context.session.userName,
                characterName = context.speaker.name,
                groupNames = context.members.map { it.character.name }
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
        return GroupChatPromptBuildResult(
            request = finalized.request,
            worldInfoStateJson = stateResult.nextStateJson,
            inspection = inspection
        )
    }

    /** 按群聊消息、成员卡和会话场景激活本轮世界书条目。 */
    private fun activateWorldInfo(context: GroupChatPromptContext): WorldBookActivationResult {
        val cardMembers = context.cardMembers()
        return mWorldBookActivator.activateStructured(
            WorldBookScanContext(
                messages = context.messages.map {
                    WorldBookScanMessage(it.speakerNameSnapshot, it.content)
                },
                currentUserMessage = null,
                totalMessageCount = context.messages.size,
                worldInfoStateJson = context.session.worldInfoStateJson,
                candidateLorebookEntries = context.candidateLorebookEntries,
                candidateLorebooks = context.candidateLorebooks,
                recursiveScanningLorebookIds = context.recursiveScanningLorebookIds,
                generationType = when (context.generationMode) {
                    GroupChatGenerationMode.Normal -> WorldBookGenerationType.Normal
                    GroupChatGenerationMode.Continue -> WorldBookGenerationType.Continue
                    GroupChatGenerationMode.Regenerate -> WorldBookGenerationType.Regenerate
                    GroupChatGenerationMode.Impersonate -> WorldBookGenerationType.Impersonate
                },
                characterDescription = cardMembers.joinToString("\n") {
                    "${it.character.name}: ${it.character.description}"
                },
                userDescription = context.session.userDescription,
                characterPersonality = cardMembers.joinToString("\n") {
                    "${it.character.name}: ${it.character.personality}"
                },
                characterDepthPrompt = cardMembers.joinToString("\n") {
                    "${it.character.name}: ${it.character.depthPromptPrompt}"
                },
                scenario = context.session.scenario
                    .takeIf { it.isNotBlank() }
                    ?.resolve(context, context.memberNames())
                    ?: context.combineCharacterField(cardMembers) { it.scenario },
                creatorNotes = cardMembers.joinToString("\n") {
                    "${it.character.name}: ${it.character.creatorNotes}"
                }
            )
        )
    }

    /** 构建位于聊天历史前后、位置固定的系统消息。 */
    private fun buildFixedMessages(
        context: GroupChatPromptContext,
        worldInfo: WorldBookActivationResult
    ): PromptSections {
        val before = mutableListOf<PromptMessageDraft>()
        val after = mutableListOf<PromptMessageDraft>()
        val memberNames = context.memberNames()
        val summaryPosition = readSummaryInjectionPosition()

        if (summaryPosition == SummaryInjectionPosition.BeforeMain) {
            summaryDraft(context)?.let { before += it }
        }
        // 群聊主提示词同样属于“当前角色回复”任务，续写和扮演用户时必须完全停用。
        if (context.generationMode.usesCharacterReplyTask()) {
            before += requiredSystem(
                context.mainPrompt(),
                PromptSourceKind.MainPrompt
            )
        }
        if (summaryPosition == SummaryInjectionPosition.AfterMain) {
            summaryDraft(context)?.let { before += it }
        }
        worldInfo.beforeCharacter.forEach {
            before += optionalSystem(
                formatWorldInfo(it.content),
                PromptSource(PromptSourceKind.WorldInfo, it.name, it.id),
                PRIORITY_WORLD_INFO
            )
        }
        context.session.userDescription.takeIf { it.isNotBlank() }?.let {
            before += requiredSystem("User persona:\n$it", PromptSourceKind.UserPersona)
        }
        before += buildCharacterCards(context)
        readAuxiliaryPrompt().takeIf { it.isNotBlank() }?.let {
            before += optionalSystem(
                it.resolve(context, memberNames),
                PromptSource(PromptSourceKind.AuxiliaryPrompt),
                PRIORITY_AUXILIARY
            )
        }
        worldInfo.afterCharacter.forEach {
            before += optionalSystem(
                formatWorldInfo(it.content),
                PromptSource(PromptSourceKind.WorldInfo, it.name, it.id),
                PRIORITY_WORLD_INFO
            )
        }
        worldInfo.exampleBefore.forEach {
            before += optionalSystem(
                formatWorldInfo(it.content),
                PromptSource(PromptSourceKind.WorldInfo, it.name, it.id),
                PRIORITY_EXAMPLE
            )
        }
        buildExamples(context).forEach { before += it }
        worldInfo.exampleAfter.forEach {
            before += optionalSystem(
                formatWorldInfo(it.content),
                PromptSource(PromptSourceKind.WorldInfo, it.name, it.id),
                PRIORITY_EXAMPLE
            )
        }
        context.newGroupChatPrompt().takeIf {
            it.isNotBlank() && context.messages.isNotEmpty()
        }?.let {
            before += optionalSystem(
                it.resolve(context, memberNames),
                PromptSource(PromptSourceKind.NewChatMarker),
                PRIORITY_NEW_CHAT
            )
        }

        if (context.generationMode.usesCharacterReplyTask()) {
            context.postHistoryInstructions().takeIf { it.isNotBlank() }?.let {
                after += requiredSystem(
                    it,
                    PromptSourceKind.PostHistoryInstructions
                )
            }
        }
        return PromptSections(
            beforeHistory = before.filter { it.content.isNotBlank() },
            afterHistory = after.filter { it.content.isNotBlank() }
        )
    }

    /**
     * 按描述、性格、场景三个固定字段合并本轮可见角色卡。
     *
     * Join 模式在每段内容前保留角色名，Swap 模式直接使用当前发言者字段。
     */
    private fun buildCharacterCards(context: GroupChatPromptContext): List<PromptMessageDraft> {
        val members = context.cardMembers()
        val description = context.combineCharacterField(members) { it.description }
        val personality = context.combineCharacterField(members) { it.personality }
        val scenario = context.session.scenario
            .takeIf { it.isNotBlank() }
            ?.resolve(context, context.memberNames())
            ?: context.combineCharacterField(members) { it.scenario }

        return buildList {
            description.takeIf { it.isNotBlank() }?.let {
                add(requiredSystem(it, PromptSourceKind.CharacterDescription))
            }
            personality.takeIf { it.isNotBlank() }?.let {
                add(
                    requiredSystem(
                        formatPersonality(it),
                        PromptSourceKind.CharacterPersonality
                    )
                )
            }
            scenario.takeIf { it.isNotBlank() }?.let {
                add(requiredSystem(formatScenario(it), PromptSourceKind.Scenario))
            }
        }
    }

    /** 合并角色字段，并在 Join 模式中标明每段内容所属角色。 */
    private fun GroupChatPromptContext.combineCharacterField(
        cardMembers: List<GroupChatMemberData>,
        readField: (Character) -> String
    ): String {
        return cardMembers.mapNotNull { member ->
            val value = readField(member.character).trim()
            if (value.isBlank()) {
                null
            } else if (session.characterCardMode == GroupChatSession.CharacterCardMode.Swap) {
                value.resolve(this, memberNames())
            } else {
                "${member.character.name}:\n${value.resolve(this, memberNames())}"
            }
        }.joinToString("\n")
    }

    /** 将成员角色卡中的示例对话转换为模型消息。 */
    private fun buildExamples(context: GroupChatPromptContext): List<PromptMessageDraft> {
        return context.cardMembers().flatMap { member ->
            member.character.examplesOfDialogue
                .split("<START>")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .flatMap { block ->
                    buildList {
                        readNewExampleChatPrompt().takeIf { it.isNotBlank() }?.let {
                            add(
                                optionalSystem(
                                    it,
                                    PromptSource(
                                        PromptSourceKind.ExampleDialogue,
                                        member.character.name
                                    ),
                                    PRIORITY_EXAMPLE
                                )
                            )
                        }
                        val parsed = parseExampleMessages(
                            block = block,
                            userName = context.session.userName,
                            characterName = member.character.name
                        )
                        if (parsed.isEmpty()) {
                            add(
                                optionalSystem(
                                    "${member.character.name} example:\n$block",
                                    PromptSource(
                                        PromptSourceKind.ExampleDialogue,
                                        member.character.name
                                    ),
                                    PRIORITY_EXAMPLE
                                )
                            )
                        } else {
                            parsed.forEach { message ->
                                val speaker = if (message.role == LLMMessageRole.User) {
                                    context.session.userName
                                } else {
                                    member.character.name
                                }
                                add(
                                    PromptMessageDraft(
                                        role = message.role,
                                        content = "$speaker: ${message.content}",
                                        source = PromptSource(
                                            PromptSourceKind.ExampleDialogue,
                                            member.character.name
                                        ),
                                        retentionPriority = PRIORITY_EXAMPLE,
                                        canDrop = true
                                    )
                                )
                            }
                        }
                    }
                }
        }
    }

    /** 构建需要按深度插入聊天历史的作者注释和世界书片段。 */
    private fun buildInChatPieces(
        context: GroupChatPromptContext,
        worldInfo: WorldBookActivationResult
    ): List<InChatPiece> {
        val pieces = mutableListOf<InChatPiece>()
        if (readSummaryInjectionPosition() == SummaryInjectionPosition.InChat) {
            summaryDraft(context)?.let {
                pieces += InChatPiece(
                    message = it,
                    depth = readSummaryInjectionDepth(),
                    order = SUMMARY_ORDER,
                    tieBreaker = Long.MIN_VALUE
                )
            }
        }
        worldInfo.anTop.forEachIndexed { index, entry ->
            pieces += InChatPiece(
                message = optionalSystem(
                    entry.content,
                    PromptSource(PromptSourceKind.WorldInfo, entry.name, entry.id),
                    PRIORITY_WORLD_INFO
                ),
                depth = USER_NOTE_DEPTH,
                order = AN_TOP_ORDER,
                tieBreaker = index.toLong()
            )
        }
        context.session.userNote.takeIf { it.isNotBlank() }?.let {
            pieces += InChatPiece(
                optionalSystem(
                    it.resolve(context, context.memberNames()),
                    PromptSource(PromptSourceKind.UserNote),
                    PRIORITY_USER_NOTE
                ),
                USER_NOTE_DEPTH,
                USER_NOTE_ORDER,
                Long.MIN_VALUE
            )
        }
        worldInfo.anBottom.forEachIndexed { index, entry ->
            pieces += InChatPiece(
                message = optionalSystem(
                    entry.content,
                    PromptSource(PromptSourceKind.WorldInfo, entry.name, entry.id),
                    PRIORITY_WORLD_INFO
                ),
                depth = USER_NOTE_DEPTH,
                order = AN_BOTTOM_ORDER,
                tieBreaker = index.toLong()
            )
        }
        // 群聊 Character Note 同样面向角色输出，特殊模式只保留人物与世界背景。
        if (context.generationMode.usesCharacterReplyTask()) {
            context.cardMembers().forEach { member ->
                member.character.depthPromptPrompt.takeIf { it.isNotBlank() }?.let {
                    pieces += InChatPiece(
                        message = PromptMessageDraft(
                            role = member.character.depthPromptRole.toMessageRole(),
                            content = it.resolve(context, context.memberNames()),
                            source = PromptSource(
                                PromptSourceKind.CharacterNote,
                                member.character.name
                            ),
                            retentionPriority = PRIORITY_CHARACTER_NOTE,
                            canDrop = true
                        ),
                        depth = member.character.depthPromptDepth.coerceAtLeast(0),
                        order = CHARACTER_NOTE_ORDER,
                        tieBreaker = member.character.id
                    )
                }
            }
        }
        worldInfo.depthEntries.forEach { group ->
            val sources = group.entries.map {
                PromptSource(PromptSourceKind.WorldInfo, it.name, it.id)
            }
            val first = group.entries.firstOrNull() ?: return@forEach
            pieces += InChatPiece(
                PromptMessageDraft(
                    role = group.role,
                    content = group.entries.joinToString("\n") { it.content },
                    source = sources.first(),
                    retentionPriority = PRIORITY_WORLD_INFO,
                    canDrop = true,
                    sources = sources
                ),
                group.depth,
                first.order,
                first.id
            )
        }
        return pieces
    }

    /** 按深度、顺序和稳定键将动态片段插入聊天历史。 */
    private fun insertInChatPieces(
        messages: MutableList<PromptMessageDraft>,
        pieces: List<InChatPiece>
    ) {
        val injections = pieces
            .groupBy { (messages.size - it.depth).coerceIn(0, messages.size) }
            .mapValues { (_, group) ->
                group.sortedWith(
                    compareBy<InChatPiece> { it.order }.thenBy { it.tieBreaker }
                )
            }
        val result = buildList {
            for (index in 0..messages.size) {
                injections[index]?.forEach { add(it.message) }
                if (index < messages.size) add(messages[index])
            }
        }
        messages.clear()
        messages.addAll(result)
    }

    private fun sanitizeHistory(messages: List<GroupChatMessage>): List<GroupChatMessage> {
        return messages.mapNotNull { message ->
            val cleaned = if (readIncludeThinkInContext()) {
                message.content
            } else {
                message.content.stripThinkBlocks()
            }.trim()
            if (cleaned.isBlank()) null else message.copy(content = cleaned)
        }
    }

    /** 根据角色卡模式和静音设置确定本轮注入的成员卡。 */
    private fun GroupChatPromptContext.cardMembers(): List<GroupChatMemberData> {
        if (session.characterCardMode == GroupChatSession.CharacterCardMode.Swap) {
            return members.filter { it.character.id == speaker.id }
        }
        return if (session.includeMutedCards) {
            members
        } else {
            members.filter {
                !it.relation.muted || it.character.id == speaker.id
            }
        }
    }

    private fun GroupChatPromptContext.memberNames(): String {
        return members.joinToString(", ") { it.character.name }
    }

    private fun GroupChatPromptContext.mainPrompt(): String {
        val original = readMainPrompt()
        return session.systemPromptOverride.trim()
            .ifBlank {
                speaker.systemPrompt.trim().ifBlank { original }
            }
            .resolve(this, memberNames(), original)
    }

    private fun GroupChatPromptContext.postHistoryInstructions(): String {
        val original = readPostHistoryInstructions()
        return speaker.postHistoryInstructions.trim()
            .ifBlank { original }
            .resolve(this, memberNames(), original)
    }

    private fun GroupChatPromptContext.groupNudgePrompt(): String {
        return session.groupNudgePromptOverride.trim()
            .ifBlank { readGroupNudgePrompt() }
    }

    private fun GroupChatPromptContext.newGroupChatPrompt(): String {
        return session.newGroupChatPromptOverride.trim()
            .ifBlank { readNewGroupChatPrompt() }
    }

    private fun GroupChatMessage.toPromptDraft(
        userName: String,
        retentionPriority: Int,
        canDrop: Boolean
    ): PromptMessageDraft {
        val role = when (source) {
            GroupChatMessage.Source.User -> LLMMessageRole.User
            GroupChatMessage.Source.Character -> LLMMessageRole.Assistant
            GroupChatMessage.Source.System -> LLMMessageRole.System
        }
        val speaker = if (source == GroupChatMessage.Source.User) {
            userName
        } else {
            speakerNameSnapshot
        }
        return PromptMessageDraft(
            role = role,
            content = "$speaker: $content",
            source = PromptSource(PromptSourceKind.ChatHistory, "Message #$id"),
            retentionPriority = retentionPriority,
            canDrop = canDrop
        )
    }

    /** 替换群聊提示词支持的角色、用户、场景与成员宏。 */
    private fun String.resolve(
        context: GroupChatPromptContext,
        groupNames: String,
        original: String = this
    ): String {
        return replace("{{original}}", original, ignoreCase = true)
            .replace("{{char}}", context.speaker.name, ignoreCase = true)
            .replace("{{user}}", context.session.userName, ignoreCase = true)
            .replace("{{persona}}", context.session.userDescription, ignoreCase = true)
            .replace("{{scenario}}", context.session.scenario, ignoreCase = true)
            .replace("{{group}}", groupNames, ignoreCase = true)
            .replace("{{charIfNotGroup}}", context.speaker.name, ignoreCase = true)
            .replace("<CHAR>", context.speaker.name, ignoreCase = true)
            .replace("<BOT>", context.speaker.name, ignoreCase = true)
            .replace("<USER>", context.session.userName, ignoreCase = true)
    }

    private fun formatWorldInfo(content: String): String {
        return readWorldInfoFormat().replace("{0}", content)
    }

    private fun formatPersonality(content: String): String {
        return readPersonalityFormat().let { template ->
            if (template.contains("{{personality}}")) {
                template.replace("{{personality}}", content)
            } else {
                content
            }
        }
    }

    private fun formatScenario(content: String): String {
        return readScenarioFormat().let { template ->
            if (template.contains("{{scenario}}")) {
                template.replace("{{scenario}}", content)
            } else {
                content
            }
        }
    }

    private fun readMainPrompt(): String =
        runCatching { AppModel.mainPrompt }.getOrDefault(AppModel.DEFAULT_MAIN_PROMPT)

    private fun readPostHistoryInstructions(): String =
        runCatching { AppModel.postHistoryInstructions }.getOrDefault("")

    private fun readAuxiliaryPrompt(): String =
        runCatching { AppModel.auxiliaryPrompt }
            .getOrDefault(AppModel.DEFAULT_AUXILIARY_PROMPT)

    private fun readImpersonationPrompt(): String =
        runCatching { AppModel.impersonationPrompt }
            .getOrDefault(AppModel.DEFAULT_IMPERSONATION_PROMPT)

    private fun readContinueNudgePrompt(): String =
        runCatching { AppModel.continueNudgePrompt }
            .getOrDefault(AppModel.DEFAULT_CONTINUE_NUDGE_PROMPT)

    private fun readNewExampleChatPrompt(): String =
        runCatching { AppModel.newExampleChatPrompt }
            .getOrDefault(AppModel.DEFAULT_NEW_EXAMPLE_CHAT_PROMPT)

    private fun readGroupNudgePrompt(): String =
        runCatching { AppModel.groupNudgePrompt }
            .getOrDefault(AppModel.DEFAULT_GROUP_NUDGE_PROMPT)

    private fun readNewGroupChatPrompt(): String =
        runCatching { AppModel.newGroupChatPrompt }
            .getOrDefault(AppModel.DEFAULT_NEW_GROUP_CHAT_PROMPT)

    private fun readWorldInfoFormat(): String =
        runCatching { AppModel.worldInfoFormat }
            .getOrDefault(AppModel.DEFAULT_WORLD_INFO_FORMAT)

    private fun readPersonalityFormat(): String =
        runCatching { AppModel.personalityFormat }
            .getOrDefault(AppModel.DEFAULT_PERSONALITY_FORMAT)

    private fun readScenarioFormat(): String =
        runCatching { AppModel.scenarioFormat }
            .getOrDefault(AppModel.DEFAULT_SCENARIO_FORMAT)

    private fun readWorldInfoBudgetPercent(): Int =
        runCatching { AppModel.worldInfoBudgetPercent }.getOrDefault(25)

    private fun readIncludeThinkInContext(): Boolean =
        runCatching { AppModel.includeThinkInContext }.getOrDefault(false)

    private fun readPostProcessingMode(provider: LLMProvider): PromptPostProcessingMode {
        return PromptPostProcessingMode.fromOrdinal(provider.promptPostProcessingMode)
    }

    private fun readSummaryInjectionPosition(): SummaryInjectionPosition {
        return SummaryInjectionPosition.fromPersistedValue(
            runCatching { AppModel.summaryInjectionPosition }
                .getOrDefault(SummaryInjectionPosition.AfterMain.persistedValue)
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

    private fun summaryDraft(context: GroupChatPromptContext): PromptMessageDraft? {
        if (context.summary.isBlank()) return null
        val template = runCatching { AppModel.summaryInjectionTemplate }
            .getOrDefault(AppModel.DEFAULT_SUMMARY_INJECTION_TEMPLATE)
        val content = if (template.contains("{{summary}}", ignoreCase = true)) {
            template.replace("{{summary}}", context.summary, ignoreCase = true)
        } else {
            listOf(template, context.summary).filter { it.isNotBlank() }.joinToString("\n")
        }
        return PromptMessageDraft(
            role = readSummaryInjectionRole().toMessageRole(),
            content = content,
            source = PromptSource(PromptSourceKind.Summary),
            retentionPriority = PRIORITY_ESSENTIAL,
            canDrop = false
        )
    }

    /**
     * 构建 Continue 或 Impersonate 模式位于请求末尾的唯一任务提示。
     *
     * 两种特殊模式统一使用 user 角色，使所有供应商都接收到明确且位于末尾的生成目标。
     */
    private fun buildGenerationControlDraft(context: GroupChatPromptContext): PromptMessageDraft? {
        val content = when (context.generationMode) {
            GroupChatGenerationMode.Normal,
            GroupChatGenerationMode.Regenerate -> return null
            GroupChatGenerationMode.Continue -> readContinueNudgePrompt()
            GroupChatGenerationMode.Impersonate -> readImpersonationPrompt()
        }.resolve(context, context.memberNames())
        val sourceKind = when (context.generationMode) {
            GroupChatGenerationMode.Continue -> PromptSourceKind.ContinueNudge
            GroupChatGenerationMode.Impersonate -> PromptSourceKind.ImpersonationNudge
            GroupChatGenerationMode.Normal,
            GroupChatGenerationMode.Regenerate -> return null
        }
        return content.takeIf { it.isNotBlank() }?.let {
            requiredUser(it, sourceKind)
        }
    }

    /** 确保普通群聊在 Group Nudge 关闭时仍由明确的 user 轮次触发当前角色回复。 */
    private fun ensureTerminalCharacterReplyTurn(
        drafts: List<PromptMessageDraft>,
        context: GroupChatPromptContext
    ): List<PromptMessageDraft> {
        if (!context.generationMode.usesCharacterReplyTask()) return drafts
        if (drafts.lastOrNull()?.role == LLMMessageRole.User) return drafts
        return drafts + requiredUser(
            DEFAULT_CHARACTER_REPLY_NUDGE.resolve(context, context.memberNames()),
            PromptSourceKind.GroupNudge
        )
    }

    private fun requiredSystem(
        content: String,
        sourceKind: PromptSourceKind,
        detail: String = ""
    ): PromptMessageDraft {
        return PromptMessageDraft(
            role = LLMMessageRole.System,
            content = content,
            source = PromptSource(sourceKind, detail),
            retentionPriority = PRIORITY_ESSENTIAL,
            canDrop = false
        )
    }

    private fun requiredUser(
        content: String,
        sourceKind: PromptSourceKind,
        detail: String = ""
    ): PromptMessageDraft {
        return PromptMessageDraft(
            role = LLMMessageRole.User,
            content = content,
            source = PromptSource(sourceKind, detail),
            retentionPriority = PRIORITY_ESSENTIAL,
            canDrop = false
        )
    }

    private fun optionalSystem(
        content: String,
        source: PromptSource,
        retentionPriority: Int
    ): PromptMessageDraft {
        return PromptMessageDraft(
            role = LLMMessageRole.System,
            content = content,
            source = source,
            retentionPriority = retentionPriority,
            canDrop = true
        )
    }

    private data class PromptSections(
        val beforeHistory: List<PromptMessageDraft>,
        val afterHistory: List<PromptMessageDraft>
    )

    private data class InChatPiece(
        val message: PromptMessageDraft,
        val depth: Int,
        val order: Int,
        val tieBreaker: Long
    )

    private companion object {
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

/** 将角色卡 depth prompt 的整数角色转换为通用消息角色。 */
private fun Int.toMessageRole(): LLMMessageRole {
    return when (this) {
        LorebookEntry.ROLE_USER -> LLMMessageRole.User
        LorebookEntry.ROLE_ASSISTANT -> LLMMessageRole.Assistant
        else -> LLMMessageRole.System
    }
}
