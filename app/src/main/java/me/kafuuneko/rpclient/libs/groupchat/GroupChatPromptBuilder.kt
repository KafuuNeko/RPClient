package me.kafuuneko.rpclient.libs.groupchat

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.prompt.WorldBookActivationResult
import me.kafuuneko.rpclient.libs.prompt.WorldBookActivator
import me.kafuuneko.rpclient.libs.prompt.WorldBookScanContext
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
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
    val recursiveScanningLorebookIds: Set<Long> = emptySet(),
    val generationMode: GroupChatGenerationMode = GroupChatGenerationMode.Normal
)

/** 群聊回复的生成模式。 */
enum class GroupChatGenerationMode {
    Normal,
    Continue
}

/** 提示词构建结果，同时返回需要持久化的世界书时序状态。 */
data class GroupChatPromptBuildResult(
    val request: LLMGenerationRequest,
    val worldInfoStateJson: String
)

class GroupChatPromptBuilder(
    private val mWorldBookActivator: WorldBookActivator = WorldBookActivator()
) {
    /** 构建可直接发送给模型的群聊生成请求。 */
    fun build(context: GroupChatPromptContext): LLMGenerationRequest {
        return buildWithMetadata(context).request
    }

    /** 构建生成请求，并携带世界书激活后的下一状态。 */
    fun buildWithMetadata(context: GroupChatPromptContext): GroupChatPromptBuildResult {
        val maxPromptTokens = (
            context.provider.contextTokens - context.provider.maxTokens
        ).coerceAtLeast(MIN_PROMPT_TOKENS)
        val worldBudget = (
            maxPromptTokens * readWorldInfoBudgetPercent().coerceIn(0, 40) / 100
        ).coerceAtLeast(MIN_WORLD_BUDGET)
        val worldInfo = fitWorldInfo(activateWorldInfo(context), worldBudget)
        val fixedMessages = buildFixedMessages(context, worldInfo)
        val inChatPieces = buildInChatPieces(context, worldInfo)
        val fixedTokens = (
            fixedMessages.beforeHistory + fixedMessages.afterHistory
        ).sumOf { estimateTokens(it.content) } +
            inChatPieces.sumOf { estimateTokens(it.message.content) }
        val history = fitHistory(
            messages = context.messages,
            tokenBudget = (maxPromptTokens - fixedTokens).coerceAtLeast(MIN_HISTORY_TOKENS)
        )
        val historyMessages = history.map {
            it.toLlmMessage(context.session.userName)
        }.toMutableList()
        insertInChatPieces(historyMessages, inChatPieces)

        return GroupChatPromptBuildResult(
            request = LLMGenerationRequest(
                messages = buildList {
                    addAll(fixedMessages.beforeHistory)
                    addAll(historyMessages)
                    addAll(fixedMessages.afterHistory)
                },
                model = context.provider.model,
                options = LLMGenerationOptions(
                    temperature = context.provider.temperature,
                    maxTokens = context.provider.maxTokens,
                    topP = context.provider.topP
                ),
                includeReasoningInContent = true
            ),
            worldInfoStateJson = worldInfo.nextStateJson
        )
    }

    /** 按群聊消息、成员卡和会话场景激活本轮世界书条目。 */
    private fun activateWorldInfo(context: GroupChatPromptContext): WorldBookActivationResult {
        val cardMembers = context.cardMembers()
        return mWorldBookActivator.activateStructured(
            WorldBookScanContext(
                messages = context.messages.map {
                    "${it.speakerNameSnapshot}: ${it.content}"
                },
                currentUserMessage = null,
                totalMessageCount = context.messages.size,
                worldInfoStateJson = context.session.worldInfoStateJson,
                candidateLorebookEntries = context.candidateLorebookEntries,
                recursiveScanningLorebookIds = context.recursiveScanningLorebookIds,
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
        val before = mutableListOf<LLMMessage>()
        val after = mutableListOf<LLMMessage>()
        val memberNames = context.members.joinToString(", ") { it.character.name }

        before += system(context.mainPrompt().resolve(context, memberNames))
        worldInfo.beforeCharacter.forEach { before += system(formatWorldInfo(it.content)) }
        before += system(buildGroupIdentity(context, memberNames))
        before += buildCharacterCards(context)
        worldInfo.afterCharacter.forEach { before += system(formatWorldInfo(it.content)) }
        context.session.userDescription.takeIf { it.isNotBlank() }?.let {
            before += system("User persona:\n$it")
        }
        context.session.scenario.takeIf { it.isNotBlank() }?.let {
            before += system("Group scenario:\n$it")
        }
        context.summary.takeIf { it.isNotBlank() }?.let {
            before += system("Story memory:\n$it")
        }
        readAuxiliaryPrompt().takeIf { it.isNotBlank() }?.let {
            before += system(it.resolve(context, memberNames))
        }
        worldInfo.exampleBefore.forEach { before += system(formatWorldInfo(it.content)) }
        buildExamples(context).forEach { before += it }
        worldInfo.exampleAfter.forEach { before += system(formatWorldInfo(it.content)) }
        context.newGroupChatPrompt().takeIf {
            it.isNotBlank() && context.messages.isNotEmpty()
        }?.let { before += system(it.resolve(context, memberNames)) }

        context.speaker.postHistoryInstructions.takeIf { it.isNotBlank() }?.let {
            after += system(it.resolve(context, memberNames))
        }
        if (context.generationMode == GroupChatGenerationMode.Continue) {
            after += system(readContinueNudgePrompt().resolve(context, memberNames))
        }
        after += system(context.groupNudgePrompt().resolve(context, memberNames))
        return PromptSections(
            beforeHistory = before.filter { it.content.isNotBlank() },
            afterHistory = after.filter { it.content.isNotBlank() }
        )
    }

    /** 根据角色卡模式拼装本轮可见的角色设定。 */
    private fun buildCharacterCards(context: GroupChatPromptContext): List<LLMMessage> {
        return context.cardMembers().flatMap { member ->
            val character = member.character
            buildList {
                character.description.takeIf { it.isNotBlank() }?.let {
                    add(system("<character name=\"${character.name}\">\nDescription:\n$it"))
                }
                character.personality.takeIf { it.isNotBlank() }?.let {
                    add(system("${character.name}'s personality:\n$it"))
                }
                if (context.session.scenario.isBlank()) {
                    character.scenario.takeIf { it.isNotBlank() }?.let {
                        add(system("${character.name}'s scenario:\n$it"))
                    }
                }
                character.depthPromptPrompt.takeIf { it.isNotBlank() }?.let {
                    add(system(it.resolve(context, context.memberNames())))
                }
                if (character.description.isNotBlank()) {
                    add(system("</character>"))
                }
            }
        }
    }

    /** 将成员角色卡中的示例对话转换为模型消息。 */
    private fun buildExamples(context: GroupChatPromptContext): List<LLMMessage> {
        return context.cardMembers().flatMap { member ->
            member.character.examplesOfDialogue
                .split("<START>")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .flatMap { block ->
                    buildList {
                        readNewExampleChatPrompt().takeIf { it.isNotBlank() }?.let {
                            add(system(it))
                        }
                        add(system("${member.character.name} example:\n$block"))
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
            pieces += InChatPiece(system(it.content), USER_NOTE_DEPTH, it.order, it.id)
        }
        context.session.userNote.takeIf { it.isNotBlank() }?.let {
            pieces += InChatPiece(
                system(it.resolve(context, context.memberNames())),
                USER_NOTE_DEPTH,
                USER_NOTE_ORDER,
                Long.MIN_VALUE
            )
        }
        worldInfo.anBottom.forEach {
            pieces += InChatPiece(system(it.content), USER_NOTE_DEPTH, it.order, it.id)
        }
        worldInfo.depthEntries.forEach { group ->
            group.entries.forEach {
                pieces += InChatPiece(
                    LLMMessage(group.role, it.content),
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
        messages: MutableList<LLMMessage>,
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

    /** 从最新消息向前截取符合上下文预算的历史。 */
    private fun fitHistory(
        messages: List<GroupChatMessage>,
        tokenBudget: Int
    ): List<GroupChatMessage> {
        val selected = ArrayDeque<GroupChatMessage>()
        var usedTokens = 0
        messages.asReversed().forEach { message ->
            val cleaned = if (readIncludeThinkInContext()) {
                message.content
            } else {
                message.content.stripThinkBlocks()
            }.trim()
            if (cleaned.isBlank()) return@forEach
            val nextTokens = estimateTokens(cleaned)
            if (selected.isNotEmpty() && usedTokens + nextTokens > tokenBudget) return@forEach
            selected.addFirst(message.copy(content = cleaned))
            usedTokens += nextTokens
        }
        return selected.toList()
    }

    /** 按世界书预算裁剪条目，并同步过滤各注入位置的结果。 */
    private fun fitWorldInfo(
        result: WorldBookActivationResult,
        tokenBudget: Int
    ): WorldBookActivationResult {
        val selected = mutableListOf<LorebookEntry>()
        var usedTokens = 0
        result.activatedEntries
            .sortedWith(compareByDescending<LorebookEntry> { it.order }.thenBy { it.id })
            .forEach { entry ->
                val nextTokens = estimateTokens(entry.content)
                if (
                    !entry.ignoreBudget &&
                    selected.isNotEmpty() &&
                    usedTokens + nextTokens > tokenBudget
                ) {
                    return@forEach
                }
                selected += entry
                if (!entry.ignoreBudget) usedTokens += nextTokens
            }
        val ids = selected.map { it.id }.toSet()
        return result.copy(
            activatedEntries = result.activatedEntries.filter { it.id in ids },
            beforeCharacter = result.beforeCharacter.filter { it.id in ids },
            afterCharacter = result.afterCharacter.filter { it.id in ids },
            exampleBefore = result.exampleBefore.filter { it.id in ids },
            exampleAfter = result.exampleAfter.filter { it.id in ids },
            anTop = result.anTop.filter { it.id in ids },
            anBottom = result.anBottom.filter { it.id in ids },
            depthEntries = result.depthEntries.mapNotNull { group ->
                val entries = group.entries.filter { it.id in ids }.toMutableList()
                if (entries.isEmpty()) null else group.copy(entries = entries)
            },
            outletEntries = result.outletEntries.mapValues { (_, entries) ->
                entries.filter { it.id in ids }
            }.filterValues { it.isNotEmpty() }
        )
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

    private fun GroupChatMessage.toLlmMessage(userName: String): LLMMessage {
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
        return LLMMessage(role, "$speaker: $content")
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

    private fun system(content: String): LLMMessage {
        return LLMMessage(LLMMessageRole.System, content)
    }

    private fun estimateTokens(text: String): Int {
        return (text.length / 3).coerceAtLeast(1)
    }

    private data class PromptSections(
        val beforeHistory: List<LLMMessage>,
        val afterHistory: List<LLMMessage>
    )

    private data class InChatPiece(
        val message: LLMMessage,
        val depth: Int,
        val order: Int,
        val tieBreaker: Long
    )

    private companion object {
        // 提示词预算下限，避免小上下文模型完全丢失固定提示。
        const val MIN_PROMPT_TOKENS = 1024
        // 历史消息至少保留的估算 Token 预算。
        const val MIN_HISTORY_TOKENS = 512
        // 世界书至少可使用的估算 Token 预算。
        const val MIN_WORLD_BUDGET = 128
        // 作者注释在聊天历史中的默认插入深度。
        const val USER_NOTE_DEPTH = 4
        // 同深度片段中作者注释的排序值。
        const val USER_NOTE_ORDER = 500
    }
}
