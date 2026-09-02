package me.kafuuneko.rpclient.libs.prompt

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.prompt.model.PromptBuildContext
import me.kafuuneko.rpclient.libs.prompt.model.PromptGenerationMode
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.regex.JavaScriptRegexCompiler
import kotlin.random.Random

/**
 * SillyTavern 兼容的世界书激活器。
 *
 * 核心功能：
 * - 支持主关键词、次要选择性关键词（AND_ANY / AND_ALL / NOT_ANY / NOT_ALL）与正则表达式匹配；
 * - 支持附加静态扫描源（角色描述、用户人设、性格设定、场景设定、深度提示词、创作者备注等）；
 * - 支持多轮递归激活（Recursive Scanning）与递归阻断；
 * - 支持包含互斥组（Inclusion Groups）的组评分淘汰、强制覆盖与按权重加权随机选择；
 * - 支持概率触发（Probability 0-100%）；
 * - 支持时序控制：延迟激活（Delay）、生效常驻（Sticky）、冷却轮次（Cooldown）及历史回滚自动丢弃；
 * - 按照 SillyTavern 规范将激活结果分类汇总至不同插入位置（Before/After Character、Example Top/Bottom、Author's Note Top/Bottom、Depth 深度插入、Outlet 自定义插槽）。
 */
class WorldBookActivator {
    private val mGson = Gson()

    /** 仅返回最终激活条目列表的兼容入口。 */
    fun activate(context: PromptBuildContext): List<LorebookEntry> {
        return activateStructured(context).activatedEntries
    }

    /**
     * 计算世界书触发结果，并同时返回按 SillyTavern 插入位置分组后的完整结构。
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

    /** 使用与具体聊天实体解耦的扫描上下文执行世界书激活流水线。 */
    fun activateStructured(context: WorldBookScanContext): WorldBookActivationResult {
        val messageCount = context.totalMessageCount
        // 加载并校验时序状态，若聊天发生回滚则自动重置时钟
        val state = (context.worldInfoState
            ?: WorldInfoRuntimeState.fromJson(context.worldInfoStateJson, mGson))
            .discardIfChatRewound(messageCount)
        val activated = linkedMapOf<Long, LorebookEntry>()
        val timedStickyIds = mutableSetOf<Long>()
        val failedProbabilityIds = mutableSetOf<Long>()
        var recursionBuffer = ""

        // 收集仍处于 Sticky 有效期内的时序条目
        context.candidateLorebookEntries
            .filter { it.allowsGenerationType(context.generationType) }
            .filter { it.isStickyActive(state, messageCount) }
            .sortedForActivation()
            .forEach {
                activated[it.id] = it
                timedStickyIds += it.id
            }

        // 多轮递归扫描匹配（第 0 轮扫描上下文文本；后续轮次扫描递归缓冲区）
        var step = 0
        while (step <= MAX_RECURSION_STEPS) {
            if (step > 0 && recursionBuffer.isBlank()) break
            // 过滤并评分当前轮次命中的候选条目
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
            // 执行包含互斥组裁决与概率判定
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

            // 将新激活条目并入结果并填充下一轮递归缓冲区
            newlyActivated.forEach { activation ->
                activated[activation.id] = activation
            }
            recursionBuffer = newlyActivated.joinToString("\n") { it.content }
            step += 1
        }

        // 推进并生成下一轮的时序状态快照
        val nextState = state.next(
            messageCount = messageCount,
            entries = activated.values.toList(),
            stickyIds = timedStickyIds,
            freshTimedIds = activated.values
                .filter { it.id !in timedStickyIds && it.hasTimedEffect() }
                .map { it.id }
                .toSet()
        )

        // 转换为按位置结构化分组的激活结果对象
        return activated.values.toList().toActivationResult(
            nextState = nextState,
            previousState = state,
            messageCount = messageCount
        )
    }

    /**
     * 预算裁剪后重新生成时序状态，避免未实际注入 Prompt 的条目进入 sticky/cooldown。
     */
    fun resolveNextState(result: WorldBookActivationResult): WorldBookActivationResult {
        val state = result.previousState
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
        return result.copy(
            nextStateJson = nextState.toJson(mGson),
            nextState = nextState
        )
    }

    /** 计算单个条目在当前扫描文本中的匹配命中得分，未命中时返回 null。 */
    private fun LorebookEntry.activationScore(
        context: WorldBookScanContext,
        recursionBuffer: String,
        messageCount: Int
    ): Int? {
        if (disabled) return null
        if ((delay ?: 0) > messageCount) return null
        if (constant) return 0

        // 构建当前条目的扫描文本缓冲（包含指定深度的历史消息与声明的静态扫描源）
        val scanBuffer = buildScanBuffer(context, this, recursionBuffer)
        if (scanBuffer.isBlank()) return null
        val primaryKeywords = getKeywordList().map { it.trim() }.filter { it.isEffectiveKeyword() }
        if (primaryKeywords.isEmpty()) return null

        // 校验主关键词命中数
        val primaryHits = primaryKeywords.count { scanBuffer.matchesKeyword(it, this) }
        if (primaryHits == 0) return null

        // 校验次要关键词逻辑（AND_ANY, AND_ALL, NOT_ANY, NOT_ALL）
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

    /** 判定条目是否通过概率掷骰测试。 */
    private fun LorebookEntry.passesProbability(): Boolean {
        return probability >= 100 || (
            probability > 0 &&
                Random.nextInt(100) < probability
            )
    }

    /** 组合并构建当前条目的全量待扫描文本缓冲区。 */
    private fun buildScanBuffer(
        context: WorldBookScanContext,
        entry: LorebookEntry,
        recursionBuffer: String
    ): String {
        // 解析生效的扫描消息深度
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
        // 拼接附加扫描源（静态属性与递归文本）
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

    /** 检查扫描文本是否匹配指定关键词（支持 `/pattern/flags` 正则、全词匹配与大小写敏感）。 */
    private fun String.matchesKeyword(keyword: String, entry: LorebookEntry): Boolean {
        parseRegexKeyword(keyword)?.let { parsed ->
            val match = parsed.regex.find(this)
            return match != null && (!parsed.sticky || match.range.first == 0)
        }

        return matchesPlainTextKey(
            key = keyword,
            ignoreCase = entry.caseSensitive != true,
            // 当前编辑器是二态开关；未设置值必须与 UI 的关闭状态保持一致。
            matchWholeWords = entry.matchWholeWords == true
        )
    }

    /** 校验关键词是否为非空有效字符串。 */
    private fun String.isEffectiveKeyword(): Boolean {
        return isNotBlank()
    }

    /**
     * 对同一 inclusion group 的已激活条目执行互斥选择。
     *
     * 已在前一递归轮次激活的组不再选择新成员；当前 sticky 条目优先于评分和随机选择。
     * 启用 group scoring 时先淘汰低分候选，再按 override、权重随机的顺序决定唯一赢家。
     */
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
        // 按组名聚合条目
        forEach { entry ->
            entry.inclusionGroups().forEach { group ->
                groups.getOrPut(group) { mutableListOf() } += entry
            }
        }

        // 针对每个互斥组执行裁决
        groups.forEach { (groupName, originalEntries) ->
            var entries = originalEntries.filter { it.id in selectedIds }
            if (entries.isEmpty()) return@forEach
            if (groupName in activatedGroups) {
                entries.forEach { selectedIds -= it.id }
                return@forEach
            }
            if (entries.size <= 1) return@forEach

            // 优先保留 Sticky 条目
            val stickyEntries = entries.filter { it.id in stickyIds }
            if (stickyEntries.isNotEmpty()) {
                entries.filterNot { it.id in stickyIds }.forEach { selectedIds -= it.id }
                return@forEach
            }

            // 组内评分机制（淘汰最高分以下的条目）
            if (entries.any { it.useGroupScoring }) {
                val maxScore = entries.maxOf { activationScores[it.id] ?: 0 }
                entries.filter {
                    it.useGroupScoring && (activationScores[it.id] ?: 0) < maxScore
                }.forEach { selectedIds -= it.id }
                entries = entries.filter { it.id in selectedIds }
            }
            if (entries.size <= 1) return@forEach

            // 强制覆盖优先，否则按权重加权随机决出唯一赢家
            val prioritized = entries
                .filter { it.groupOverride }
                .sortedWith(compareByDescending<LorebookEntry> { it.order }.thenBy { it.id })
                .firstOrNull()
            val winner = prioritized ?: entries.weightedRandom()
            entries.filterNot { it.id == winner?.id }.forEach { selectedIds -= it.id }
        }

        return filter { it.id in selectedIds }.sortedForActivation()
    }

    /** 依权重从条目列表中按轮盘赌加权随机选取一个条目。 */
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

    /** 提取条目声明的所有互斥包含组名称列表。 */
    private fun LorebookEntry.inclusionGroups(): List<String> {
        return group.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    /** 校验条目是否允许在当前生成类型（如 Normal / Continue / Impersonate 等）下激活。 */
    private fun LorebookEntry.allowsGenerationType(
        generationType: WorldBookGenerationType
    ): Boolean {
        val filters = getTriggerList()
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
        return filters.isEmpty() || generationType.value in filters
    }

    /** 判定指定世界书是否开启了递归扫描模式。 */
    private fun WorldBookScanContext.isRecursiveScanningEnabled(lorebookId: Long): Boolean {
        return candidateLorebooks[lorebookId]?.recursiveScanning
            ?: (lorebookId in recursiveScanningLorebookIds)
    }

    /** 将扫描消息格式化为待匹配文本。 */
    private fun WorldBookScanMessage.toScanText(includeNames: Boolean): String {
        if (!includeNames || speakerName.isBlank()) return content
        return "$speakerName: $content"
    }

    /**
     * 解析 SillyTavern 风格的 `/pattern/flags` 世界书关键词。
     *
     * 接受常见 JavaScript 标志 g/i/m/s/u/y；g 不改变这里只判断是否命中的语义，u 会转换
     * ECMAScript code point escape，y 要求匹配从扫描文本开头开始。格式、重复标志或正则
     * 无效时返回 null，让调用方按普通关键词处理，避免坏配置中断整轮激活。
     */
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
        val pattern = rawPattern.replace("\\/", "/")
        val regex = runCatching {
            JavaScriptRegexCompiler.compile(pattern, flags.toSet())
        }.getOrNull() ?: return null
        return ParsedRegexKeyword(regex = regex, sticky = 'y' in flags)
    }

    /** 检查字符串中是否存在未转义的斜杠字符。 */
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

    /**
     * 将最终激活条目按 SillyTavern position 拆成 Prompt 注入分组。
     *
     * 输入先按 order 降序遍历、再通过头部插入恢复同位置的稳定升序；相同 depth 和 role
     * 合并为一条消息，避免协议后处理改变条目之间的相对顺序。
     */
    private fun List<LorebookEntry>.toActivationResult(
        nextState: WorldInfoRuntimeState,
        previousState: WorldInfoRuntimeState,
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

        // 按 order 降序遍历并头插以保留同位置同顺序的稳定升序
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
            nextStateJson = nextState.toJson(mGson),
            previousStateJson = previousState.toJson(mGson),
            messageCount = messageCount,
            nextState = nextState,
            previousState = previousState
        )
    }

    /** 检查条目是否处于 Sticky 保持生效期内。 */
    private fun LorebookEntry.isStickyActive(
        state: WorldInfoRuntimeState,
        messageCount: Int
    ): Boolean {
        if (disabled) return false
        val item = state.entries[id.toString()] ?: return false
        if (item.signature != timedSignature()) return false
        return messageCount < item.stickyUntil
    }

    /** 检查条目是否处于 Cooldown 冷却期内。 */
    private fun LorebookEntry.isOnCooldown(
        state: WorldInfoRuntimeState,
        messageCount: Int
    ): Boolean {
        val item = state.entries[id.toString()] ?: return false
        if (item.signature != timedSignature()) return false
        return messageCount >= item.stickyUntil && messageCount < item.cooldownUntil
    }

    /** 检查条目是否配置了时序效果（Sticky 或 Cooldown 大于 0）。 */
    private fun LorebookEntry.hasTimedEffect(): Boolean {
        return (sticky ?: 0) > 0 || (cooldown ?: 0) > 0
    }

    /**
     * 生成 timed effect 的行为签名。
     *
     * 当前条目任何会影响激活、预算或注入的字段发生变化后，旧 sticky/cooldown 状态立即失效；
     * 此签名只用于当前本地运行状态校验，不作为跨版本稳定标识。
     */
    private fun LorebookEntry.timedSignature(): String {
        return listOf(
            lorebookId.toString(),
            content,
            keywords,
            secondaryKeywords,
            constant.toString(),
            disabled.toString(),
            order.toString(),
            position.toString(),
            depth.toString(),
            role.toString(),
            probability.toString(),
            ignoreBudget.toString(),
            scanDepth?.toString().orEmpty(),
            matchWholeWords?.toString().orEmpty(),
            caseSensitive?.toString().orEmpty(),
            selectiveLogic.toString(),
            group,
            groupOverride.toString(),
            groupWeight?.toString().orEmpty(),
            useGroupScoring.toString(),
            preventRecursion.toString(),
            delayUntilRecursion.toString(),
            sticky?.toString().orEmpty(),
            cooldown?.toString().orEmpty(),
            delay?.toString().orEmpty(),
            outletName,
            triggers,
            matchPersonaDescription.toString(),
            matchCharacterDescription.toString(),
            matchCharacterPersonality.toString(),
            matchCharacterDepthPrompt.toString(),
            matchScenario.toString(),
            matchCreatorNotes.toString()
        ).joinToString("\u001F").hashCode().toString()
    }

    /** 按常驻优先、Order 降序、ID 升序对条目进行激活排序。 */
    private fun List<LorebookEntry>.sortedForActivation(): List<LorebookEntry> {
        return sortedWith(
            compareByDescending<LorebookEntry> { it.constant }
                .thenByDescending { it.order }
                .thenBy { it.id }
        )
    }

    /** 将条目的持久化角色数值映射为 LLMMessageRole。 */
    private fun LorebookEntry.toMessageRole(): LLMMessageRole {
        return when (role) {
            LorebookEntry.ROLE_USER -> LLMMessageRole.User
            LorebookEntry.ROLE_ASSISTANT -> LLMMessageRole.Assistant
            else -> LLMMessageRole.System
        }
    }

    /** 检查会话是否回滚，若当前消息轮数小于最后记录轮数则丢弃旧时序状态。 */
    private fun WorldInfoRuntimeState.discardIfChatRewound(
        messageCount: Int
    ): WorldInfoRuntimeState {
        if (lastMessageCount <= messageCount) return this
        return WorldInfoRuntimeState(lastMessageCount = messageCount)
    }

    /**
     * 仅以最终保留进 Prompt 的条目推进 sticky/cooldown 时钟。
     *
     * 仍处于 sticky 的条目复用原期限，新触发条目从当前消息数开始计算；已过期记录
     * 会先清理，防止状态 JSON 随会话轮次无限增长。
     */
    private fun WorldInfoRuntimeState.next(
        messageCount: Int,
        entries: List<LorebookEntry>,
        stickyIds: Set<Long>,
        freshTimedIds: Set<Long>
    ): WorldInfoRuntimeState {
        // 清理已过期的时序记录
        val nextEntries = this.entries
            .filterValues { it.cooldownUntil > messageCount || it.stickyUntil > messageCount }
            .toMutableMap()

        // 推进生效与新触发条目的期限
        entries.forEach { entry ->
            val key = entry.id.toString()
            when {
                entry.id in stickyIds -> nextEntries[key] = nextEntries.getValue(key)
                entry.id in freshTimedIds -> {
                    val stickyUntil = messageCount + (entry.sticky ?: 0).coerceAtLeast(0)
                    val cooldownUntil = stickyUntil + (entry.cooldown ?: 0).coerceAtLeast(0)
                    nextEntries[key] = WorldInfoEntryRuntimeState(
                        activatedAt = messageCount,
                        stickyUntil = stickyUntil,
                        cooldownUntil = cooldownUntil,
                        signature = entry.timedSignature()
                    )
                }
            }
        }

        return WorldInfoRuntimeState(
            lastMessageCount = messageCount,
            entries = nextEntries
        )
    }

    private companion object {
        /** 条目未指定扫描深度时使用的默认历史消息条数。 */
        const val DEFAULT_SCAN_DEPTH = 2
        /** 递归扫描最大步数上限，避免循环相互引用导致死循环。 */
        const val MAX_RECURSION_STEPS = 5
        /** 正则表达式关键词支持的修饰标志集合。 */
        val SUPPORTED_REGEX_FLAGS = setOf('g', 'i', 'm', 's', 'u', 'y')
    }
}

/** 封装条目及其匹配命中得分。 */
private data class EntryActivation(
    /** 当前流程正在处理的单个条目。 */
    val entry: LorebookEntry,
    /** 世界书条目按关键词与分组规则计算出的匹配分数。 */
    val score: Int
)

/** 正则表达式关键词解析结果（包含编译后的 Regex 及是否为 sticky 匹配）。 */
private data class ParsedRegexKeyword(
    /** 当前规则编译后的正则表达式。 */
    val regex: Regex,
    /** 世界书条目命中后继续保持激活的生成轮数。 */
    val sticky: Boolean
)

/** 世界书激活器使用的通用扫描上下文，可供单聊与群聊复用。 */
data class WorldBookScanContext(
    /** 当前状态或请求包含的消息列表。 */
    val messages: List<WorldBookScanMessage>,
    /** 触发本次生成的当前用户消息。 */
    val currentUserMessage: WorldBookScanMessage?,
    /** 统计范围内包含的消息总数。 */
    val totalMessageCount: Int,
    /** 序列化后的世界书时序状态，需要随会话或故事持久化。 */
    val worldInfoStateJson: String,
    /** 通过作用域筛选后待扫描的世界书条目列表。 */
    val candidateLorebookEntries: List<LorebookEntry>,
    /** 本次扫描可能参与激活的世界书列表。 */
    val candidateLorebooks: Map<Long, Lorebook> = emptyMap(),
    /** 允许参与递归激活扫描的世界书 ID 集合。 */
    val recursiveScanningLorebookIds: Set<Long> = emptySet(),
    /** 本次群聊生成对应的业务类型。 */
    val generationType: WorldBookGenerationType = WorldBookGenerationType.Normal,
    /** 世界书扫描文本是否带上消息发言者名称。 */
    val includeNames: Boolean = true,
    /** 参与 Prompt 构建的角色描述。 */
    val characterDescription: String = "",
    /** 当前会话或 Prompt 使用的用户设定。 */
    val userDescription: String = "",
    /** 参与 Prompt 构建的角色性格设定。 */
    val characterPersonality: String = "",
    /** 插入聊天历史指定深度的角色附加提示词。 */
    val characterDepthPrompt: String = "",
    /** 角色对话发生的场景设定。 */
    val scenario: String = "",
    /** 作者提供的角色使用说明和备注。 */
    val creatorNotes: String = "",
    /** 本轮世界书扫描开始时使用的时序状态。 */
    val worldInfoState: WorldInfoRuntimeState? = null
)

/** 世界书扫描使用的轻量消息，显式保留发言者名称。 */
data class WorldBookScanMessage(
    /** 当前发言者的显示名称快照。 */
    val speakerName: String,
    /** 当前对象承载的正文内容。 */
    val content: String
)

/** 世界书 triggers 可过滤的生成操作类型枚举。 */
enum class WorldBookGenerationType(val value: String) {
    Normal("normal"),
    Continue("continue"),
    Impersonate("impersonate"),
    Swipe("swipe"),
    Regenerate("regenerate"),
    Quiet("quiet")
}

/** 将 Prompt 生成模式映射为世界书生成类型。 */
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
    /** 本次扫描最终激活的世界书条目列表。 */
    val activatedEntries: List<LorebookEntry>,
    /** 插入角色定义之前的世界书内容列表。 */
    val beforeCharacter: List<LorebookEntry> = emptyList(),
    /** 插入角色定义之后的世界书内容列表。 */
    val afterCharacter: List<LorebookEntry> = emptyList(),
    /** 插入示例对话之前的世界书内容列表。 */
    val exampleBefore: List<LorebookEntry> = emptyList(),
    /** 插入示例对话之后的世界书内容列表。 */
    val exampleAfter: List<LorebookEntry> = emptyList(),
    /** 插入作者注释上方的世界书内容列表。 */
    val anTop: List<LorebookEntry> = emptyList(),
    /** 插入作者注释下方的世界书内容列表。 */
    val anBottom: List<LorebookEntry> = emptyList(),
    /** 按聊天深度插入的世界书内容列表。 */
    val depthEntries: List<WorldBookDepthEntry> = emptyList(),
    /** 按自定义插槽名称分组的世界书内容。 */
    val outletEntries: Map<String, List<LorebookEntry>> = emptyMap(),
    /** 本轮扫描后需要持久化的世界书时序状态 JSON。 */
    val nextStateJson: String = "{}",
    /** 本轮扫描前保存的世界书时序状态 JSON。 */
    val previousStateJson: String = "{}",
    /** 当前会话或分组包含的消息数量。 */
    val messageCount: Int = 0,
    /** 本轮扫描后生成的世界书时序状态。 */
    val nextState: WorldInfoRuntimeState = WorldInfoRuntimeState(),
    /** 本轮扫描前保存的世界书时序状态。 */
    val previousState: WorldInfoRuntimeState = WorldInfoRuntimeState()
)

/** 在聊天历史指定深度插入的一组同角色世界书条目。 */
data class WorldBookDepthEntry(
    /** 当前内容相对聊天末尾的插入或扫描深度。 */
    val depth: Int,
    /** 当前对象在业务流程中承担的角色。 */
    val role: LLMMessageRole,
    /** 当前分组、请求或结果包含的条目列表。 */
    val entries: MutableList<LorebookEntry>
)

/** 世界书激活器使用的结构化时序状态；具体持久化形式由调用方决定。 */
data class WorldInfoRuntimeState(
    /** 上次推进世界书时已处理的消息数量。 */
    @field:SerializedName("lastMessageCount")
    val lastMessageCount: Int = 0,
    /** 当前分组、请求或结果包含的条目列表。 */
    @field:SerializedName("entries")
    val entries: Map<String, WorldInfoEntryRuntimeState> = emptyMap()
) {
    fun toJson(gson: Gson): String {
        return gson.toJson(this)
    }

    companion object {
        fun fromJson(json: String, gson: Gson): WorldInfoRuntimeState {
            if (json.isBlank()) return WorldInfoRuntimeState()
            return runCatching {
                gson.fromJson(JsonParser.parseString(json), WorldInfoRuntimeState::class.java)
            }.getOrNull() ?: WorldInfoRuntimeState()
        }
    }
}

/** 单个条目的时序状态（激活轮次、Sticky 截止轮次、Cooldown 截止轮次与行为签名）。 */
data class WorldInfoEntryRuntimeState(
    /** 世界书条目最近一次被直接命中的生成轮次。 */
    @field:SerializedName("activatedAt")
    val activatedAt: Int = 0,
    /** 世界书条目粘滞激活保持到的生成轮次。 */
    @field:SerializedName("stickyUntil")
    val stickyUntil: Int = 0,
    /** 世界书条目冷却结束的生成轮次。 */
    @field:SerializedName("cooldownUntil")
    val cooldownUntil: Int = 0,
    /** 用于判断状态内容是否发生变化的稳定签名。 */
    @field:SerializedName("signature")
    val signature: String = ""
)
