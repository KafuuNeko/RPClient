package me.kafuuneko.rpclient.libs.prompt

import com.google.gson.Gson
import com.google.gson.JsonParser
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import kotlin.random.Random

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
        val messageCount = context.messages.size + if (context.currentUserMessage.isNullOrBlank()) 0 else 1
        val state = TimedWorldInfoState.fromJson(context.session.worldInfoStateJson, gson)
            .discardIfChatRewound(messageCount)
        val activated = linkedMapOf<Long, LorebookEntry>()
        val timedStickyIds = mutableSetOf<Long>()
        val freshTimedIds = mutableSetOf<Long>()
        var recursionBuffer = ""

        // sticky 条目在有效期内可不重新命中关键词，但签名变化后会自动失效。
        context.candidateLorebookEntries
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
            val newlyActivated = context.candidateLorebookEntries
                .filter { it.id !in activated }
                .filter { entry ->
                    if (step > 0 && entry.preventRecursion) return@filter false
                    if (step > 0 && entry.lorebookId !in context.recursiveScanningLorebookIds) return@filter false
                    if (step == 0 && entry.delayUntilRecursion) return@filter false
                    if (entry.isOnCooldown(state, messageCount)) return@filter false
                    entry.shouldActivate(
                        context = context,
                        recursionBuffer = recursionBuffer.takeIf { step > 0 && entry.lorebookId in context.recursiveScanningLorebookIds }.orEmpty(),
                        messageCount = messageCount
                    )
                }
                .sortedForActivation()

            if (newlyActivated.isEmpty()) break
            newlyActivated.forEach {
                activated[it.id] = it
                if (it.hasTimedEffect()) freshTimedIds += it.id
            }
            recursionBuffer = newlyActivated.joinToString("\n") { it.content }
            step += 1
        }

        val grouped = activated.values.toList().applyInclusionGroups()
        val groupedIds = grouped.map { it.id }.toSet()
        val nextState = state.next(
            messageCount = messageCount,
            entries = grouped,
            stickyIds = timedStickyIds.intersect(groupedIds),
            freshTimedIds = freshTimedIds.intersect(groupedIds)
        )

        return grouped.toActivationResult(nextState.toJson(gson))
    }

    private fun LorebookEntry.shouldActivate(
        context: PromptBuildContext,
        recursionBuffer: String,
        messageCount: Int
    ): Boolean {
        if (disabled) return false
        if ((delay ?: 0) > messageCount) return false
        if (probability <= 0) return false
        if (probability < 100 && Random.nextInt(100) >= probability) return false
        if (constant) return true

        // 普通条目必须至少命中一个主关键词或 trigger，secondary keywords 再按 selectiveLogic 过滤。
        val scanBuffer = buildScanBuffer(context, this, recursionBuffer)
        if (scanBuffer.isBlank()) return false
        val primaryKeywords = (getKeywordList() + getTriggerList()).map { it.trim() }.filter { it.isEffectiveKeyword() }
        if (primaryKeywords.isEmpty()) return false

        val primaryHit = primaryKeywords.any { scanBuffer.matchesKeyword(it, this) }
        if (!primaryHit) return false

        val secondaryKeywords = getSecondaryKeywordList().map { it.trim() }.filter { it.isEffectiveKeyword() }
        if (secondaryKeywords.isEmpty()) return true

        val secondaryHits = secondaryKeywords.count { scanBuffer.matchesKeyword(it, this) }
        return when (selectiveLogic) {
            LorebookEntry.LOGIC_NOT_ALL -> secondaryHits < secondaryKeywords.size
            LorebookEntry.LOGIC_NOT_ANY -> secondaryHits == 0
            LorebookEntry.LOGIC_AND_ALL -> secondaryHits == secondaryKeywords.size
            else -> secondaryHits > 0
        }
    }

    private fun buildScanBuffer(
        context: PromptBuildContext,
        entry: LorebookEntry,
        recursionBuffer: String
    ): String {
        val depth = entry.scanDepth ?: DEFAULT_SCAN_DEPTH
        val recentMessages = context.messages.takeLast(depth.coerceAtLeast(0))
            .joinToString("\n") { it.content }
        // 附加扫描源默认关闭，只在条目显式声明时扫描角色描述、场景等静态字段。
        return buildList {
            if (recentMessages.isNotBlank()) add(recentMessages)
            context.currentUserMessage?.takeIf { it.isNotBlank() }?.let { add(it) }
            recursionBuffer.takeIf { it.isNotBlank() }?.let { add(it) }
            if (entry.matchCharacterDescription) add(context.character.description)
            if (entry.matchPersonaDescription) add(context.userDescription)
            if (entry.matchCharacterPersonality) add(context.character.personality)
            if (entry.matchCharacterDepthPrompt) add(context.character.depthPromptPrompt)
            if (entry.matchScenario) add(context.character.scenario)
            if (entry.matchCreatorNotes) add(context.character.creatorNotes)
        }.filter { it.isNotBlank() }.joinToString("\n")
    }

    private fun String.matchesKeyword(keyword: String, entry: LorebookEntry): Boolean {
        val ignoreCase = entry.caseSensitive != true
        val isRegex = keyword.length >= 2 && keyword.first() == '/' && keyword.last() == '/'
        if (isRegex) {
            // 空正则会匹配任意文本，必须先排除，避免“无关键词也触发”。
            val pattern = keyword.substring(1, keyword.lastIndex)
            if (pattern.isBlank()) return false
            val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
            return runCatching { Regex(pattern, options).containsMatchIn(this) }.getOrDefault(false)
        }
        if (entry.matchWholeWords == true) {
            val escaped = Regex.escape(keyword)
            val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
            return Regex("""(?<![\p{L}\p{N}_])$escaped(?![\p{L}\p{N}_])""", options).containsMatchIn(this)
        }
        return contains(keyword, ignoreCase = ignoreCase)
    }

    private fun String.isEffectiveKeyword(): Boolean {
        if (isBlank()) return false
        if (length >= 2 && first() == '/' && last() == '/') {
            return substring(1, lastIndex).isNotBlank()
        }
        return true
    }

    private fun List<LorebookEntry>.applyInclusionGroups(): List<LorebookEntry> {
        // Inclusion group 只保留一个条目：override 时取最高 order，否则按权重随机。
        val ungrouped = filter { it.group.isBlank() }
        val grouped = filter { it.group.isNotBlank() }
            .groupBy { it.group }
            .values
            .mapNotNull { entries ->
                if (entries.any { it.groupOverride }) {
                    entries.maxWithOrNull(compareBy<LorebookEntry> { it.order }.thenByDescending { it.id })
                } else {
                    entries.weightedRandom()
                }
            }
        return (ungrouped + grouped).sortedForActivation()
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

    private fun List<LorebookEntry>.toActivationResult(nextStateJson: String): WorldBookActivationResult {
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
            nextStateJson = nextStateJson
        )
    }

    private fun LorebookEntry.isStickyActive(state: TimedWorldInfoState, messageCount: Int): Boolean {
        if (disabled) return false
        val item = state.entries[id.toString()] ?: return false
        if (item.signature != timedSignature()) return false
        return item.stickyUntil >= messageCount
    }

    private fun LorebookEntry.isOnCooldown(state: TimedWorldInfoState, messageCount: Int): Boolean {
        val item = state.entries[id.toString()] ?: return false
        if (item.signature != timedSignature()) return false
        return item.stickyUntil < messageCount && item.cooldownUntil >= messageCount
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
            .filterValues { it.cooldownUntil >= messageCount || it.stickyUntil >= messageCount }
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
        const val DEFAULT_SCAN_DEPTH = 2
        const val MAX_RECURSION_STEPS = 5
    }
}

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
    val nextStateJson: String = "{}"
)

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
