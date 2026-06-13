package me.kafuuneko.rpclient.libs.prompt

import com.google.gson.Gson
import com.google.gson.JsonParser
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import kotlin.random.Random

/**
 * SillyTavern 兼容的世界书激活器。
 *
 * 实现关键词、附加扫描源、递归、包含组、概率以及 sticky/cooldown/delay；
 * 激活只决定候选内容，最终是否进入请求仍由世界书和 Prompt 预算器裁剪。
 */
class WorldBookActivator {
    private val gson = Gson()

    /**
     * 仅返回最终激活条目的兼容入口。
     */
    fun activate(context: PromptBuildContext): List<LorebookEntry> {
        return activateStructured(context).activatedEntries
    }

    /**
     * 计算世界书触发结果，并同时返回按 ST 插入位置分组后的结构。
     *
     * 注意：会话开启的条目只是候选集，真正进入上下文前仍必须经过关键词、常驻、
     * 概率、sticky/cooldown、递归等规则判断。
     */
    fun activateStructured(context: PromptBuildContext): WorldBookActivationResult {
        return activateStructured(
            WorldBookScanContext(
                messages = context.messages.map {
                    WorldBookScanMessage(
                        speakerName = when (it.source) {
                            ChatMessage.Source.User -> context.userName
                            ChatMessage.Source.Char -> context.character.name
                            ChatMessage.Source.System,
                            ChatMessage.Source.Summary -> "System"
                        },
                        content = it.content
                    )
                },
                currentUserMessage = context.currentUserMessage?.let {
                    WorldBookScanMessage(context.userName, it)
                },
                totalMessageCount = context.totalMessageCount,
                worldInfoStateJson = context.session.worldInfoStateJson,
                candidateLorebookEntries = context.candidateLorebookEntries,
                candidateLorebooks = context.candidateLorebooks,
                recursiveScanningLorebookIds = context.recursiveScanningLorebookIds,
                generationType = context.generationMode.toWorldBookGenerationType(),
                characterDescription = context.character.description,
                userDescription = context.userDescription,
                characterPersonality = context.character.personality,
                characterDepthPrompt = context.character.depthPromptPrompt,
                scenario = context.character.scenario,
                creatorNotes = context.character.creatorNotes
            )
        )
    }

    /** 使用与具体聊天实体解耦的扫描上下文激活世界书。 */
    fun activateStructured(context: WorldBookScanContext): WorldBookActivationResult {
        val messageCount = context.totalMessageCount
        val state = TimedWorldInfoState.fromJson(context.worldInfoStateJson, gson)
            .discardIfChatRewound(messageCount)
        val activated = linkedMapOf<Long, LorebookEntry>()
        val timedStickyIds = mutableSetOf<Long>()
        val failedProbabilityIds = mutableSetOf<Long>()
        var recursionBuffer = ""

        // sticky 条目在有效期内可不重新命中关键词，但签名变化后会自动失效。
        context.candidateLorebookEntries
            .filter { it.allowsGenerationType(context.generationType) }
            .filter { it.isStickyActive(state, messageCount) }
            .sortedForActivation()
            .forEach {
                activated[it.id] = it
                timedStickyIds += it.id
            }

        var step = 0
        while (step <= MAX_RECURSION_STEPS) {
            if (step > 0 && recursionBuffer.isBlank()) break
            // 第 0 轮扫描聊天；后续轮次只在对应世界书启用 recursiveScanning 时扫描已激活条目内容。
            val matchedEntries = context.candidateLorebookEntries
                .filter { it.id !in activated }
                .filter { it.id !in failedProbabilityIds }
                .mapNotNull { entry ->
                    if (!entry.allowsGenerationType(context.generationType)) return@mapNotNull null
                    if (step > 0 && entry.preventRecursion) return@mapNotNull null
                    if (step > 0 && !context.isRecursiveScanningEnabled(entry.lorebookId)) {
                        return@mapNotNull null
                    }
                    if (step == 0 && entry.delayUntilRecursion) return@mapNotNull null
                    if (entry.isOnCooldown(state, messageCount)) return@mapNotNull null
                    val score = entry.activationScore(
                        context = context,
                        recursionBuffer = recursionBuffer.takeIf {
                            step > 0 && context.isRecursiveScanningEnabled(entry.lorebookId)
                        }.orEmpty(),
                        messageCount = messageCount
                    )
                    score?.let { EntryActivation(entry, it) }
                }
                .sortedWith(
                    compareByDescending<EntryActivation> { it.entry.constant }
                        .thenByDescending { it.entry.order }
                        .thenBy { it.entry.id }
                )

            if (matchedEntries.isEmpty()) break
            val activationScores = matchedEntries.associate { it.entry.id to it.score }
            val newlyActivated = matchedEntries
                .map { it.entry }
                .applyInclusionGroups(
                    activationScores = activationScores,
                    stickyIds = timedStickyIds,
                    alreadyActivated = activated.values
                )
                .filter { entry ->
                    entry.passesProbability().also { passed ->
                        if (!passed) failedProbabilityIds += entry.id
                    }
                }
            if (newlyActivated.isEmpty()) break

            newlyActivated.forEach { activation ->
                activated[activation.id] = activation
            }
            recursionBuffer = newlyActivated.joinToString("\n") { it.content }
            step += 1
        }

        val nextState = state.next(
            messageCount = messageCount,
            entries = activated.values.toList(),
            stickyIds = timedStickyIds,
            freshTimedIds = activated.values
                .filter { it.id !in timedStickyIds && it.hasTimedEffect() }
                .map { it.id }
                .toSet()
        )

        return activated.values.toList().toActivationResult(
            nextStateJson = nextState.toJson(gson),
            previousStateJson = context.worldInfoStateJson,
            messageCount = messageCount
        )
    }

    /**
     * 预算裁剪后重新生成时序状态，避免未实际注入 Prompt 的条目进入 sticky/cooldown。
     */
    fun resolveNextState(result: WorldBookActivationResult): WorldBookActivationResult {
        val state = TimedWorldInfoState.fromJson(result.previousStateJson, gson)
            .discardIfChatRewound(result.messageCount)
        val stickyIds = result.activatedEntries
            .filter { it.isStickyActive(state, result.messageCount) }
            .map { it.id }
            .toSet()
        val freshTimedIds = result.activatedEntries
            .filter { it.id !in stickyIds && it.hasTimedEffect() }
            .map { it.id }
            .toSet()
        val nextState = state.next(
            messageCount = result.messageCount,
            entries = result.activatedEntries,
            stickyIds = stickyIds,
            freshTimedIds = freshTimedIds
        )
        return result.copy(nextStateJson = nextState.toJson(gson))
    }

    private fun LorebookEntry.activationScore(
        context: WorldBookScanContext,
        recursionBuffer: String,
        messageCount: Int
    ): Int? {
        if (disabled) return null
        if ((delay ?: 0) > messageCount) return null
        if (constant) return 0

        // triggers 是生成类型过滤器，不参与关键词扫描。
        val scanBuffer = buildScanBuffer(context, this, recursionBuffer)
        if (scanBuffer.isBlank()) return null
        val primaryKeywords = getKeywordList().map { it.trim() }.filter { it.isEffectiveKeyword() }
        if (primaryKeywords.isEmpty()) return null

        val primaryHits = primaryKeywords.count { scanBuffer.matchesKeyword(it, this) }
        if (primaryHits == 0) return null

        val secondaryKeywords = getSecondaryKeywordList().map { it.trim() }.filter { it.isEffectiveKeyword() }
        val secondaryHits = secondaryKeywords.count { scanBuffer.matchesKeyword(it, this) }
        val secondaryMatches = when {
            secondaryKeywords.isEmpty() -> true
            selectiveLogic == LorebookEntry.LOGIC_NOT_ALL -> secondaryHits < secondaryKeywords.size
            selectiveLogic == LorebookEntry.LOGIC_NOT_ANY -> secondaryHits == 0
            selectiveLogic == LorebookEntry.LOGIC_AND_ALL -> secondaryHits == secondaryKeywords.size
            else -> secondaryHits > 0
        }
        if (!secondaryMatches) return null

        return when (selectiveLogic) {
            LorebookEntry.LOGIC_AND_ANY,
            LorebookEntry.LOGIC_AND_ALL -> primaryHits + secondaryHits
            else -> primaryHits
        }
    }

    private fun LorebookEntry.passesProbability(): Boolean {
        return probability >= 100 || (
            probability > 0 &&
                Random.nextInt(100) < probability
            )
    }

    private fun buildScanBuffer(
        context: WorldBookScanContext,
        entry: LorebookEntry,
        recursionBuffer: String
    ): String {
        val depth = entry.scanDepth
            ?: context.candidateLorebooks[entry.lorebookId]?.scanDepth
            ?: DEFAULT_SCAN_DEPTH
        val recentMessages = buildList {
            addAll(context.messages)
            context.currentUserMessage?.let { add(it) }
        }
            .takeLast(depth.coerceAtLeast(0))
            .asReversed()
            .map { it.toScanText(context.includeNames) }
        // 附加扫描源默认关闭，只在条目显式声明时扫描角色描述、场景等静态字段。
        return buildList {
            addAll(recentMessages.filter { it.isNotBlank() })
            if (entry.matchCharacterDescription) add(context.characterDescription)
            if (entry.matchPersonaDescription) add(context.userDescription)
            if (entry.matchCharacterPersonality) add(context.characterPersonality)
            if (entry.matchCharacterDepthPrompt) add(context.characterDepthPrompt)
            if (entry.matchScenario) add(context.scenario)
            if (entry.matchCreatorNotes) add(context.creatorNotes)
            recursionBuffer.takeIf { it.isNotBlank() }?.let { add(it) }
        }.filter { it.isNotBlank() }.joinToString("\n\u0001", prefix = "\u0001")
    }

    private fun String.matchesKeyword(keyword: String, entry: LorebookEntry): Boolean {
        parseRegexKeyword(keyword)?.let { parsed ->
            val match = parsed.regex.find(this)
            return match != null && (!parsed.sticky || match.range.first == 0)
        }

        val ignoreCase = entry.caseSensitive != true
        val matchWholeWords = entry.matchWholeWords ?: DEFAULT_MATCH_WHOLE_WORDS
        if (matchWholeWords && keyword.trim().split(Regex("""\s+""")).size == 1) {
            val escaped = Regex.escape(keyword)
            val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
            return Regex("""(?<![\p{L}\p{N}_])$escaped(?![\p{L}\p{N}_])""", options).containsMatchIn(this)
        }
        return contains(keyword, ignoreCase = ignoreCase)
    }

    private fun String.isEffectiveKeyword(): Boolean {
        return isNotBlank()
    }

    private fun List<LorebookEntry>.applyInclusionGroups(
        activationScores: Map<Long, Int>,
        stickyIds: Set<Long>,
        alreadyActivated: Collection<LorebookEntry> = emptyList()
    ): List<LorebookEntry> {
        val selectedIds = map { it.id }.toMutableSet()
        val groups = linkedMapOf<String, MutableList<LorebookEntry>>()
        val activatedGroups = alreadyActivated
            .flatMap { it.inclusionGroups() }
            .toSet()
        forEach { entry ->
            entry.inclusionGroups().forEach { group ->
                groups.getOrPut(group) { mutableListOf() } += entry
            }
        }

        groups.forEach { (groupName, originalEntries) ->
            var entries = originalEntries.filter { it.id in selectedIds }
            if (entries.isEmpty()) return@forEach
            if (groupName in activatedGroups) {
                entries.forEach { selectedIds -= it.id }
                return@forEach
            }
            if (entries.size <= 1) return@forEach

            val stickyEntries = entries.filter { it.id in stickyIds }
            if (stickyEntries.isNotEmpty()) {
                entries.filterNot { it.id in stickyIds }.forEach { selectedIds -= it.id }
                return@forEach
            }

            if (entries.any { it.useGroupScoring }) {
                val maxScore = entries.maxOf { activationScores[it.id] ?: 0 }
                entries.filter {
                    it.useGroupScoring && (activationScores[it.id] ?: 0) < maxScore
                }.forEach { selectedIds -= it.id }
                entries = entries.filter { it.id in selectedIds }
            }
            if (entries.size <= 1) return@forEach

            val prioritized = entries
                .filter { it.groupOverride }
                .sortedWith(compareByDescending<LorebookEntry> { it.order }.thenBy { it.id })
                .firstOrNull()
            val winner = prioritized ?: entries.weightedRandom()
            entries.filterNot { it.id == winner?.id }.forEach { selectedIds -= it.id }
        }

        return filter { it.id in selectedIds }.sortedForActivation()
    }

    private fun List<LorebookEntry>.weightedRandom(): LorebookEntry? {
        if (isEmpty()) return null
        val weighted = map { it to (it.groupWeight ?: 100).coerceAtLeast(1) }
        val total = weighted.sumOf { it.second }
        var roll = Random.nextInt(total)
        weighted.forEach { (entry, weight) ->
            roll -= weight
            if (roll < 0) return entry
        }
        return last()
    }

    private fun LorebookEntry.inclusionGroups(): List<String> {
        return group.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun LorebookEntry.allowsGenerationType(
        generationType: WorldBookGenerationType
    ): Boolean {
        val filters = getTriggerList()
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
        return filters.isEmpty() || generationType.value in filters
    }

    private fun WorldBookScanContext.isRecursiveScanningEnabled(lorebookId: Long): Boolean {
        return candidateLorebooks[lorebookId]?.recursiveScanning
            ?: (lorebookId in recursiveScanningLorebookIds)
    }

    private fun WorldBookScanMessage.toScanText(includeNames: Boolean): String {
        if (!includeNames || speakerName.isBlank()) return content
        return "$speakerName: $content"
    }

    private fun parseRegexKeyword(keyword: String): ParsedRegexKeyword? {
        if (!keyword.startsWith('/')) return null
        val delimiterIndex = keyword.lastIndexOf('/')
        if (delimiterIndex <= 0) return null

        val rawPattern = keyword.substring(1, delimiterIndex)
        val flags = keyword.substring(delimiterIndex + 1)
        if (
            rawPattern.isEmpty() ||
            rawPattern.hasUnescapedSlash() ||
            flags.any { it !in SUPPORTED_REGEX_FLAGS } ||
            flags.toSet().size != flags.length
        ) {
            return null
        }
        val options = buildSet {
            if ('i' in flags) add(RegexOption.IGNORE_CASE)
            if ('m' in flags) add(RegexOption.MULTILINE)
            if ('s' in flags) add(RegexOption.DOT_MATCHES_ALL)
        }
        val pattern = rawPattern.replace("\\/", "/")
        val regex = runCatching { Regex(pattern, options) }.getOrNull() ?: return null
        return ParsedRegexKeyword(regex = regex, sticky = 'y' in flags)
    }

    private fun String.hasUnescapedSlash(): Boolean {
        forEachIndexed { index, character ->
            if (character != '/') return@forEachIndexed
            var escapingBackslashes = 0
            var cursor = index - 1
            while (cursor >= 0 && this[cursor] == '\\') {
                escapingBackslashes += 1
                cursor -= 1
            }
            if (escapingBackslashes % 2 == 0) return true
        }
        return false
    }

    private fun List<LorebookEntry>.toActivationResult(
        nextStateJson: String,
        previousStateJson: String,
        messageCount: Int
    ): WorldBookActivationResult {
        val before = mutableListOf<LorebookEntry>()
        val after = mutableListOf<LorebookEntry>()
        val exampleBefore = mutableListOf<LorebookEntry>()
        val exampleAfter = mutableListOf<LorebookEntry>()
        val anTop = mutableListOf<LorebookEntry>()
        val anBottom = mutableListOf<LorebookEntry>()
        val depth = mutableListOf<WorldBookDepthEntry>()
        val outlet = linkedMapOf<String, MutableList<LorebookEntry>>()

        sortedWith(compareByDescending<LorebookEntry> { it.order }.thenBy { it.id }).forEach { entry ->
            when (entry.position) {
                LorebookEntry.POSITION_BEFORE -> before.add(0, entry)
                LorebookEntry.POSITION_AFTER -> after.add(0, entry)
                LorebookEntry.POSITION_EXAMPLE_TOP -> exampleBefore.add(0, entry)
                LorebookEntry.POSITION_EXAMPLE_BOTTOM -> exampleAfter.add(0, entry)
                LorebookEntry.POSITION_AN_TOP -> anTop.add(0, entry)
                LorebookEntry.POSITION_AN_BOTTOM -> anBottom.add(0, entry)
                LorebookEntry.POSITION_OUTLET -> {
                    if (entry.outletName.isNotBlank()) {
                        outlet.getOrPut(entry.outletName.trim()) { mutableListOf() }.add(0, entry)
                    }
                }
                LorebookEntry.POSITION_AT_DEPTH -> {
                    val existing = depth.firstOrNull { it.depth == entry.depth && it.role == entry.toMessageRole() }
                    if (existing == null) {
                        depth += WorldBookDepthEntry(entry.depth, entry.toMessageRole(), mutableListOf(entry))
                    } else {
                        existing.entries.add(0, entry)
                    }
                }
            }
        }

        return WorldBookActivationResult(
            activatedEntries = this,
            beforeCharacter = before,
            afterCharacter = after,
            exampleBefore = exampleBefore,
            exampleAfter = exampleAfter,
            anTop = anTop,
            anBottom = anBottom,
            depthEntries = depth,
            outletEntries = outlet,
            nextStateJson = nextStateJson,
            previousStateJson = previousStateJson,
            messageCount = messageCount
        )
    }

    private fun LorebookEntry.isStickyActive(state: TimedWorldInfoState, messageCount: Int): Boolean {
        if (disabled) return false
        val item = state.entries[id.toString()] ?: return false
        if (item.signature != timedSignature()) return false
        return messageCount < item.stickyUntil
    }

    private fun LorebookEntry.isOnCooldown(state: TimedWorldInfoState, messageCount: Int): Boolean {
        val item = state.entries[id.toString()] ?: return false
        if (item.signature != timedSignature()) return false
        return messageCount >= item.stickyUntil && messageCount < item.cooldownUntil
    }

    private fun LorebookEntry.hasTimedEffect(): Boolean {
        return (sticky ?: 0) > 0 || (cooldown ?: 0) > 0
    }

    private fun LorebookEntry.timedSignature(): String {
        return listOf(
            content,
            keywords,
            secondaryKeywords,
            constant.toString(),
            order.toString(),
            position.toString(),
            depth.toString(),
            role.toString(),
            probability.toString(),
            scanDepth?.toString().orEmpty(),
            matchWholeWords?.toString().orEmpty(),
            caseSensitive?.toString().orEmpty(),
            selectiveLogic.toString(),
            triggers,
            group,
            groupOverride.toString(),
            groupWeight?.toString().orEmpty(),
            useGroupScoring.toString(),
            sticky?.toString().orEmpty(),
            cooldown?.toString().orEmpty(),
            delay?.toString().orEmpty()
        ).joinToString("\u001F").hashCode().toString()
    }

    private fun List<LorebookEntry>.sortedForActivation(): List<LorebookEntry> {
        return sortedWith(
            compareByDescending<LorebookEntry> { it.constant }
                .thenByDescending { it.order }
                .thenBy { it.id }
        )
    }

    private fun LorebookEntry.toMessageRole(): LLMMessageRole {
        return when (role) {
            LorebookEntry.ROLE_USER -> LLMMessageRole.User
            LorebookEntry.ROLE_ASSISTANT -> LLMMessageRole.Assistant
            else -> LLMMessageRole.System
        }
    }

    private fun TimedWorldInfoState.discardIfChatRewound(messageCount: Int): TimedWorldInfoState {
        if (lastMessageCount <= messageCount) return this
        return TimedWorldInfoState(lastMessageCount = messageCount)
    }

    private fun TimedWorldInfoState.next(
        messageCount: Int,
        entries: List<LorebookEntry>,
        stickyIds: Set<Long>,
        freshTimedIds: Set<Long>
    ): TimedWorldInfoState {
        val nextEntries = this.entries
            .filterValues { it.cooldownUntil > messageCount || it.stickyUntil > messageCount }
            .toMutableMap()

        entries.forEach { entry ->
            val key = entry.id.toString()
            when {
                entry.id in stickyIds -> nextEntries[key] = nextEntries.getValue(key)
                entry.id in freshTimedIds -> {
                    val stickyUntil = messageCount + (entry.sticky ?: 0).coerceAtLeast(0)
                    val cooldownUntil = stickyUntil + (entry.cooldown ?: 0).coerceAtLeast(0)
                    nextEntries[key] = TimedEntryState(
                        activatedAt = messageCount,
                        stickyUntil = stickyUntil,
                        cooldownUntil = cooldownUntil,
                        signature = entry.timedSignature()
                    )
                }
            }
        }

        return TimedWorldInfoState(
            lastMessageCount = messageCount,
            entries = nextEntries
        )
    }

    private companion object {
        // 条目未指定扫描深度时使用的默认消息数。
        const val DEFAULT_SCAN_DEPTH = 2
        // ST 文档定义 Match Whole Words 默认开启，条目 null 时继承该默认值。
        const val DEFAULT_MATCH_WHOLE_WORDS = true
        // 递归扫描上限，避免世界书条目之间形成无限激活。
        const val MAX_RECURSION_STEPS = 5
        val SUPPORTED_REGEX_FLAGS = setOf('g', 'i', 'm', 's', 'u', 'y')
    }
}

private data class EntryActivation(
    val entry: LorebookEntry,
    val score: Int
)

private data class ParsedRegexKeyword(
    val regex: Regex,
    val sticky: Boolean
)

/** 世界书激活器使用的通用扫描上下文，可供单聊与群聊复用。 */
data class WorldBookScanContext(
    val messages: List<WorldBookScanMessage>,
    val currentUserMessage: WorldBookScanMessage?,
    val totalMessageCount: Int,
    val worldInfoStateJson: String,
    val candidateLorebookEntries: List<LorebookEntry>,
    val candidateLorebooks: Map<Long, Lorebook> = emptyMap(),
    val recursiveScanningLorebookIds: Set<Long> = emptySet(),
    val generationType: WorldBookGenerationType = WorldBookGenerationType.Normal,
    val includeNames: Boolean = true,
    val characterDescription: String = "",
    val userDescription: String = "",
    val characterPersonality: String = "",
    val characterDepthPrompt: String = "",
    val scenario: String = "",
    val creatorNotes: String = ""
)

/** 世界书扫描使用的轻量消息，显式保留发言者名称。 */
data class WorldBookScanMessage(
    val speakerName: String,
    val content: String
)

/** 世界书 triggers 可过滤的生成操作类型。 */
enum class WorldBookGenerationType(val value: String) {
    Normal("normal"),
    Continue("continue"),
    Impersonate("impersonate"),
    Swipe("swipe"),
    Regenerate("regenerate"),
    Quiet("quiet")
}

private fun PromptGenerationMode.toWorldBookGenerationType(): WorldBookGenerationType {
    return when (this) {
        PromptGenerationMode.Normal -> WorldBookGenerationType.Normal
        PromptGenerationMode.Continue -> WorldBookGenerationType.Continue
        PromptGenerationMode.Impersonate -> WorldBookGenerationType.Impersonate
        PromptGenerationMode.Regenerate -> WorldBookGenerationType.Regenerate
    }
}

/** 激活条目按 SillyTavern 插入位置分组后的完整结果。 */
data class WorldBookActivationResult(
    val activatedEntries: List<LorebookEntry>,
    val beforeCharacter: List<LorebookEntry> = emptyList(),
    val afterCharacter: List<LorebookEntry> = emptyList(),
    val exampleBefore: List<LorebookEntry> = emptyList(),
    val exampleAfter: List<LorebookEntry> = emptyList(),
    val anTop: List<LorebookEntry> = emptyList(),
    val anBottom: List<LorebookEntry> = emptyList(),
    val depthEntries: List<WorldBookDepthEntry> = emptyList(),
    val outletEntries: Map<String, List<LorebookEntry>> = emptyMap(),
    val nextStateJson: String = "{}",
    val previousStateJson: String = "{}",
    val messageCount: Int = 0
)

/** 在聊天历史指定深度插入的一组同角色世界书条目。 */
data class WorldBookDepthEntry(
    val depth: Int,
    val role: LLMMessageRole,
    val entries: MutableList<LorebookEntry>
)

private data class TimedWorldInfoState(
    val lastMessageCount: Int = 0,
    val entries: Map<String, TimedEntryState> = emptyMap()
) {
    fun toJson(gson: Gson): String {
        return gson.toJson(this)
    }

    companion object {
        fun fromJson(json: String, gson: Gson): TimedWorldInfoState {
            if (json.isBlank()) return TimedWorldInfoState()
            return runCatching {
                gson.fromJson(JsonParser.parseString(json), TimedWorldInfoState::class.java)
            }.getOrNull() ?: TimedWorldInfoState()
        }
    }
}

private data class TimedEntryState(
    val activatedAt: Int = 0,
    val stickyUntil: Int = 0,
    val cooldownUntil: Int = 0,
    val signature: String = ""
)
