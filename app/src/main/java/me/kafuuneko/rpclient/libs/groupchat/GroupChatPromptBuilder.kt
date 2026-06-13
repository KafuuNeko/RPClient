package me.kafuuneko.rpclient.libs.groupchat

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.prompt.PromptInspection
import me.kafuuneko.rpclient.libs.prompt.PromptMessageDraft
import me.kafuuneko.rpclient.libs.prompt.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.PromptRequestFinalizer
import me.kafuuneko.rpclient.libs.prompt.PromptSource
import me.kafuuneko.rpclient.libs.prompt.PromptSourceKind
import me.kafuuneko.rpclient.libs.prompt.SummaryInjectionPosition
import me.kafuuneko.rpclient.libs.prompt.WorldBookActivationResult
import me.kafuuneko.rpclient.libs.prompt.WorldBookActivator
import me.kafuuneko.rpclient.libs.prompt.WorldBookGenerationType
import me.kafuuneko.rpclient.libs.prompt.WorldBookScanMessage
import me.kafuuneko.rpclient.libs.prompt.WorldBookScanContext
import me.kafuuneko.rpclient.libs.prompt.fitWorldInfoToBudget
import me.kafuuneko.rpclient.libs.prompt.filterEntries
import me.kafuuneko.rpclient.libs.prompt.retainStateEntries
import me.kafuuneko.rpclient.libs.prompt.mapEntryContent
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
    Regenerate
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

        val finalized = mRequestFinalizer.finalize(
            drafts = buildList {
                addAll(fixedMessages.beforeHistory)
                addAll(historyMessages)
                addAll(fixedMessages.afterHistory)
            },
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
            strictPromptPlaceholder = readNewGroupChatPrompt()
                .ifBlank { AppModel.DEFAULT_NEW_GROUP_CHAT_PROMPT },
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
                scenario = context.session.scenario.ifBlank { context.speaker.scenario },
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
        val memberNames = context.members.joinToString(", ") { it.character.name }

        before += requiredSystem(
            context.mainPrompt().resolve(context, memberNames),
            PromptSourceKind.MainPrompt
        )
        worldInfo.beforeCharacter.forEach {
            before += optionalSystem(
                formatWorldInfo(it.content),
                PromptSource(PromptSourceKind.WorldInfo, it.name, it.id),
                PRIORITY_WORLD_INFO
            )
        }
        if (readSummaryInjectionPosition() == SummaryInjectionPosition.BeforeCharacter) {
            summaryDraft(context)?.let { before += it }
        }
        before += requiredSystem(buildGroupIdentity(context, memberNames), PromptSourceKind.GroupIdentity)
        before += buildCharacterCards(context)
        worldInfo.afterCharacter.forEach {
            before += optionalSystem(
                formatWorldInfo(it.content),
                PromptSource(PromptSourceKind.WorldInfo, it.name, it.id),
                PRIORITY_WORLD_INFO
            )
        }
        context.session.userDescription.takeIf { it.isNotBlank() }?.let {
            before += requiredSystem("User persona:\n$it", PromptSourceKind.UserPersona)
        }
        context.session.scenario.takeIf { it.isNotBlank() }?.let {
            before += requiredSystem("Group scenario:\n$it", PromptSourceKind.Scenario)
        }
        if (readSummaryInjectionPosition() == SummaryInjectionPosition.AfterCharacter) {
            summaryDraft(context)?.let { before += it }
        }
        readAuxiliaryPrompt().takeIf { it.isNotBlank() }?.let {
            before += optionalSystem(
                it.resolve(context, memberNames),
                PromptSource(PromptSourceKind.AuxiliaryPrompt),
                PRIORITY_AUXILIARY
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
        if (readSummaryInjectionPosition() == SummaryInjectionPosition.BeforeHistory) {
            summaryDraft(context)?.let { before += it }
        }

        if (readSummaryInjectionPosition() == SummaryInjectionPosition.AfterHistory) {
            summaryDraft(context)?.let { after += it }
        }
        context.speaker.postHistoryInstructions.takeIf { it.isNotBlank() }?.let {
            after += requiredSystem(
                it.resolve(context, memberNames),
                PromptSourceKind.PostHistoryInstructions
            )
        }
        if (context.generationMode == GroupChatGenerationMode.Continue) {
            after += requiredSystem(
                readContinueNudgePrompt().resolve(context, memberNames),
                PromptSourceKind.ContinueNudge
            )
        }
        after += requiredSystem(
            context.groupNudgePrompt().resolve(context, memberNames),
            PromptSourceKind.GroupNudge
        )
        return PromptSections(
            beforeHistory = before.filter { it.content.isNotBlank() },
            afterHistory = after.filter { it.content.isNotBlank() }
        )
    }

    /** 根据角色卡模式拼装本轮可见的角色设定。 */
    private fun buildCharacterCards(context: GroupChatPromptContext): List<PromptMessageDraft> {
        return context.cardMembers().flatMap { member ->
            val character = member.character
            buildList {
                character.description.takeIf { it.isNotBlank() }?.let {
                    add(
                        requiredSystem(
                            "<character name=\"${character.name}\">\nDescription:\n$it",
                            PromptSourceKind.CharacterCard,
                            character.name
                        )
                    )
                }
                character.personality.takeIf { it.isNotBlank() }?.let {
                    add(
                        requiredSystem(
                            "${character.name}'s personality:\n$it",
                            PromptSourceKind.CharacterCard,
                            character.name
                        )
                    )
                }
                if (context.session.scenario.isBlank()) {
                    character.scenario.takeIf { it.isNotBlank() }?.let {
                        add(
                            requiredSystem(
                                "${character.name}'s scenario:\n$it",
                                PromptSourceKind.CharacterCard,
                                character.name
                            )
                        )
                    }
                }
                character.depthPromptPrompt.takeIf { it.isNotBlank() }?.let {
                    add(
                        optionalSystem(
                            it.resolve(context, context.memberNames()),
                            PromptSource(PromptSourceKind.CharacterNote, character.name),
                            PRIORITY_CHARACTER_NOTE
                        )
                    )
                }
                if (character.description.isNotBlank()) {
                    add(
                        requiredSystem(
                            "</character>",
                            PromptSourceKind.CharacterCard,
                            character.name
                        )
                    )
                }
            }
        }
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
        worldInfo.anTop.forEach {
            pieces += InChatPiece(
                message = optionalSystem(
                    it.content,
                    PromptSource(PromptSourceKind.WorldInfo, it.name, it.id),
                    PRIORITY_WORLD_INFO
                ),
                depth = USER_NOTE_DEPTH,
                order = it.order,
                tieBreaker = it.id
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
        worldInfo.anBottom.forEach {
            pieces += InChatPiece(
                message = optionalSystem(
                    it.content,
                    PromptSource(PromptSourceKind.WorldInfo, it.name, it.id),
                    PRIORITY_WORLD_INFO
                ),
                depth = USER_NOTE_DEPTH,
                order = it.order,
                tieBreaker = it.id
            )
        }
        worldInfo.depthEntries.forEach { group ->
            group.entries.forEach {
                pieces += InChatPiece(
                    PromptMessageDraft(
                        role = group.role,
                        content = it.content,
                        source = PromptSource(PromptSourceKind.WorldInfo, it.name, it.id),
                        retentionPriority = PRIORITY_WORLD_INFO,
                        canDrop = true
                    ),
                    group.depth,
                    it.order,
                    it.id
                )
            }
        }
        return pieces
    }

    /** 按深度、顺序和稳定键将动态片段插入聊天历史。 */
    private fun insertInChatPieces(
        messages: MutableList<PromptMessageDraft>,
        pieces: List<InChatPiece>
    ) {
        pieces.sortedWith(
            compareByDescending<InChatPiece> { it.depth }
                .thenBy { it.order }
                .thenBy { it.tieBreaker }
        ).forEach { piece ->
            val index = (messages.size - piece.depth).coerceIn(0, messages.size)
            messages.add(index, piece.message)
        }
    }

    /** 声明群成员和当前发言者，避免模型混淆历史消息归属。 */
    private fun buildGroupIdentity(
        context: GroupChatPromptContext,
        memberNames: String
    ): String {
        return """
            Group members: $memberNames
            Current responding character: ${context.speaker.name}
            Every historical line is prefixed with its actual speaker. Treat those names as authoritative.
        """.trimIndent()
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
            members.filterNot { it.relation.muted }
        }
    }

    private fun GroupChatPromptContext.memberNames(): String {
        return members.joinToString(", ") { it.character.name }
    }

    private fun GroupChatPromptContext.mainPrompt(): String {
        return session.systemPromptOverride.trim()
            .ifBlank {
                speaker.systemPrompt.trim().ifBlank { readMainPrompt() }
            }
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
        groupNames: String
    ): String {
        return replace("{{char}}", context.speaker.name, ignoreCase = true)
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

    private fun readMainPrompt(): String =
        runCatching { AppModel.mainPrompt }.getOrDefault(AppModel.DEFAULT_MAIN_PROMPT)

    private fun readAuxiliaryPrompt(): String =
        runCatching { AppModel.auxiliaryPrompt }
            .getOrDefault(AppModel.DEFAULT_AUXILIARY_PROMPT)

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

    private fun readWorldInfoBudgetPercent(): Int =
        runCatching { AppModel.worldInfoBudgetPercent }.getOrDefault(25)

    private fun readIncludeThinkInContext(): Boolean =
        runCatching { AppModel.includeThinkInContext }.getOrDefault(false)

    private fun readPostProcessingMode(provider: LLMProvider): PromptPostProcessingMode {
        return PromptPostProcessingMode.fromOrdinal(provider.promptPostProcessingMode)
    }

    private fun readSummaryInjectionPosition(): SummaryInjectionPosition {
        return SummaryInjectionPosition.fromOrdinal(
            runCatching { AppModel.summaryInjectionPosition }
                .getOrDefault(SummaryInjectionPosition.AfterCharacter.ordinal)
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
        return requiredSystem(content, PromptSourceKind.Summary)
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
        // 作者注释在聊天历史中的默认插入深度。
        const val USER_NOTE_DEPTH = 4
        // 同深度片段中作者注释的排序值。
        const val USER_NOTE_ORDER = 500
    }
}
