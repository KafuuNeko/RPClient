package me.kafuuneko.rpclient.libs.groupchat

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.prompt.DEFAULT_STRICT_PROMPT_PLACEHOLDER
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehaviorProvider
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspection
import me.kafuuneko.rpclient.libs.prompt.model.PromptMessageDraft
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.PromptPostProcessingNames
import me.kafuuneko.rpclient.libs.prompt.PromptRequestFinalizer
import me.kafuuneko.rpclient.libs.prompt.model.PromptRetentionPolicy
import me.kafuuneko.rpclient.libs.prompt.model.PromptSource
import me.kafuuneko.rpclient.libs.prompt.model.PromptSourceKind
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionPosition
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionRole
import me.kafuuneko.rpclient.libs.prompt.WorldBookActivationResult
import me.kafuuneko.rpclient.libs.prompt.WorldBookActivator
import me.kafuuneko.rpclient.libs.prompt.WorldBookGenerationType
import me.kafuuneko.rpclient.libs.prompt.WorldBookScanMessage
import me.kafuuneko.rpclient.libs.prompt.WorldBookScanContext
import me.kafuuneko.rpclient.libs.prompt.fitWorldInfoToBudget
import me.kafuuneko.rpclient.libs.prompt.filterEntries
import me.kafuuneko.rpclient.libs.prompt.filterForExampleBehavior
import me.kafuuneko.rpclient.libs.prompt.mapEntryContent
import me.kafuuneko.rpclient.libs.prompt.parseExampleMessages
import me.kafuuneko.rpclient.libs.prompt.retainStateEntries
import me.kafuuneko.rpclient.libs.prompt.resolveWorldInfoBudget
import me.kafuuneko.rpclient.libs.prompt.renderUserPersonaTemplate
import me.kafuuneko.rpclient.libs.regex.RegexExecutionError
import me.kafuuneko.rpclient.libs.regex.RegexExecutionHit
import me.kafuuneko.rpclient.libs.regex.RegexMessageProcessor
import me.kafuuneko.rpclient.libs.regex.RegexMessageSource
import me.kafuuneko.rpclient.libs.regex.RegexScriptRuntime
import me.kafuuneko.rpclient.libs.regex.ScopedRegexScript
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.repository.GroupChatMemberData
import me.kafuuneko.rpclient.utils.stripThinkBlocks

/**
 * 构建一次群聊生成请求所需的完整上下文。
 *
 * @property session 群聊会话实体，包含群聊场景、主提示词覆盖与成员卡模式配置
 * @property members 群聊全体成员数据列表（包含角色卡与群内关系状态）
 * @property speaker 本轮被选中发言的目标角色实体
 * @property messages 当前群聊历史消息列表
 * @property totalMessageCount 当前群聊的完整消息总数，用于世界书时序计算
 * @property provider 调用的 LLM 服务提供商配置
 * @property summary 当前群聊会话的已有摘要内容
 * @property candidateLorebookEntries 候选世界书条目全集
 * @property candidateLorebooks 候选世界书字典映射
 * @property recursiveScanningLorebookIds 开启递归扫描的世界书 ID 集合
 * @property generationMode 群聊生成模式（普通、续写、重生成、扮演用户）
 * @property regexScripts 生效的 Regex 脚本列表
 */
data class GroupChatPromptContext(
    val session: GroupChatSession,
    val members: List<GroupChatMemberData>,
    val speaker: Character,
    val messages: List<GroupChatMessage>,
    val provider: LLMProvider,
    val totalMessageCount: Int = messages.size,
    val summary: String = "",
    val candidateLorebookEntries: List<LorebookEntry> = emptyList(),
    val candidateLorebooks: Map<Long, Lorebook> = emptyMap(),
    val recursiveScanningLorebookIds: Set<Long> = emptySet(),
    val generationMode: GroupChatGenerationMode = GroupChatGenerationMode.Normal,
    val regexScripts: List<ScopedRegexScript> = emptyList()
)

/** 群聊回复的生成模式。 */
enum class GroupChatGenerationMode {
    /** 普通群聊下一条角色回复。 */
    Normal,
    /** 对最后一条角色回复继续生成（续写）。 */
    Continue,
    /** 重新生成最后一条角色回复。 */
    Regenerate,
    /** 扮演当前用户生成下一条回复。 */
    Impersonate
}

/** 仅普通群聊回复和重新生成需要“指定角色下一条回复”的主任务及 Group Nudge。 */
private fun GroupChatGenerationMode.usesCharacterReplyTask(): Boolean {
    return this == GroupChatGenerationMode.Normal || this == GroupChatGenerationMode.Regenerate
}

/** 提示词构建结果，同时返回需要持久化的世界书时序状态。 */
data class GroupChatPromptBuildResult(
    /** 实际提交给模型的请求。 */
    val request: LLMGenerationRequest,
    /** 本次构建推进后的世界书时序状态 JSON。 */
    val worldInfoStateJson: String,
    /** 宏展开、后处理和最终预算完成后的可解释 Prompt 明细。 */
    val inspection: PromptInspection
)

/**
 * 群聊 Prompt 构建器。
 *
 * 核心架构与职责：
 * - 多成员角色卡合并：支持 Join（全员带名字拼接）与 Swap（仅保留当前发言角色）两种角色卡组织模式
 * - 发言者格式化：自动为历史消息标注 `Speaker: Content` 发言前缀，并在 Prompt 层面隔离推理思考块
 * - 群聊宏与 Regex 替换：支持 {{group}}、{{char}} 等占位符，支持用户输入与 AI 回复的分级正则清洗
 * - 多角色关联世界书扫描：聚合全体可见成员与群场景共同触发世界书条目
 * - 上下文预算与协议适配：统一交由 [PromptRequestFinalizer] 进行 Token 预算裁剪与格式后处理
 */
class GroupChatPromptBuilder(
    private val mWorldBookActivator: WorldBookActivator = WorldBookActivator(),
    private val mRegexRuntime: RegexScriptRuntime = RegexScriptRuntime(
        me.kafuuneko.rpclient.libs.regex.RegexScriptEngine()
    ),
    private val mRequestFinalizer: PromptRequestFinalizer = PromptRequestFinalizer(),
    private val mExampleDialogueBehaviorProvider: ExampleDialogueBehaviorProvider =
        ExampleDialogueBehaviorProvider { ExampleDialogueBehavior.default },
    private val mRegexProcessor: RegexMessageProcessor = RegexMessageProcessor(mRegexRuntime)
) {
    /**
     * 构建可直接发送给模型的群聊生成请求。
     *
     * @param context 群聊 Prompt 构建上下文
     * @return 组装完成的生成请求体
     */
    fun build(context: GroupChatPromptContext): LLMGenerationRequest {
        return buildWithMetadata(context).request
    }

    /**
     * 构建生成请求，并携带世界书激活后的下一状态与 Inspector 调试信息。
     *
     * 处理步骤：
     * - 计算可用 Prompt Token 预算
     * - 扫描多成员与历史消息，激活匹配的世界书条目并按预算裁剪
     * - 构建历史前后的固定系统消息（主提示词、多角色卡合并、用户画像等）
     * - 过滤历史消息中的思考块并执行 Prompt 阶段 Regex 替换
     * - 将群聊历史与 At Depth 深度片段合并
     * - 注入群聊任务提示（Group Nudge）与末尾触发轮次
     * - 调用 Finalizer 执行协议后处理与 Token 预算裁剪
     *
     * @param context 群聊 Prompt 构建上下文
     * @return 包含生成请求、世界书下一状态与 Inspector 明细的构建结果
     */
    fun buildWithMetadata(context: GroupChatPromptContext): GroupChatPromptBuildResult {
        val exampleBehavior = mExampleDialogueBehaviorProvider.current()
        // 计算可用 Prompt Token 预算（总上下文扣除最大响应 Token）
        val maxPromptTokens = (
            context.provider.contextTokens - context.provider.maxTokens
        ).coerceAtLeast(0)
        // 解析世界书全局 Token 预算上限
        val worldBudget = resolveWorldInfoBudget(
            promptTokenBudget = maxPromptTokens,
            contextPercent = readWorldInfoBudgetPercent(),
            tokenBudgetCap = readWorldInfoBudgetCap()
        )
        val regexHits = mutableListOf<RegexExecutionHit>()
        val regexErrors = mutableListOf<RegexExecutionError>()
        // 构造供 Regex 运行环境使用的宏变量映射字典（包含 {{group}} 等）
        val regexMacros = RegexScriptRuntime.macros(
            userName = context.session.userName,
            characterName = context.speaker.name,
            userDescription = context.session.userDescription,
            scenario = context.session.scenario,
            groupNames = context.memberNames()
        )
        // 扫描群聊多成员与历史消息激活世界书条目
        val rawWorldInfo = activateWorldInfo(context)
        // 执行世界书 Prompt 阶段 Regex 替换
        val activatedWorldInfo = rawWorldInfo
            .mapEntryContent { entry ->
                val result = mRegexProcessor.applyWorldInfo(
                    input = entry.content,
                    scripts = context.regexScripts,
                    macros = regexMacros
                )
                regexHits += result.hits
                regexErrors += result.errors
                entry.copy(content = result.text)
            }
            .filterForExampleBehavior(exampleBehavior)
        // 按预算比例裁剪世界书条目
        val worldSelection = fitWorldInfoToBudget(
            result = activatedWorldInfo,
            globalTokenBudget = worldBudget,
            lorebooks = context.candidateLorebooks,
            tokenizer = mRequestFinalizer.tokenizerFor(context.provider)
        )
        val worldInfo = worldSelection.result
        // 构建固定系统消息区段（主提示词、多角色卡合并、用户画像等）
        val fixedMessages = buildFixedMessages(context, worldInfo, exampleBehavior)
        // 构建待按深度插入的 In-Chat 注入项
        val inChatPieces = buildInChatPieces(context, worldInfo)
        // 过滤推理块并对群聊历史消息执行 Regex 替换
        val history = sanitizeHistory(context.messages).mapIndexed { index, message ->
            val depth = context.messages.lastIndex - index
            val result = when (message.source) {
                GroupChatMessage.Source.User -> mRegexProcessor.applyPrompt(
                    input = message.content,
                    source = RegexMessageSource.User,
                    scripts = context.regexScripts,
                    macros = regexMacros,
                    depth = depth
                )
                GroupChatMessage.Source.Character -> mRegexProcessor.applyPrompt(
                    input = message.content,
                    source = RegexMessageSource.Character,
                    scripts = context.regexScripts,
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
        // 将历史消息转换为带发言者前缀的 Prompt 草稿
        val historyMessages = history.mapIndexed { index, message ->
            message.toPromptDraft(
                userName = context.session.userName,
                retentionPriority = PromptRetentionPolicy.HISTORY,
                canDrop = index != history.lastIndex
            )
        }.toMutableList()
        // 将 In-Chat 片段按深度插入群聊历史
        insertInChatPieces(historyMessages, inChatPieces)
        // 若处于 Continue 模式提取待续写的目标消息
        val continueTarget = if (context.generationMode == GroupChatGenerationMode.Continue) {
            val targetIndex = historyMessages.indexOfLast {
                it.source.kind == PromptSourceKind.ChatHistory &&
                    it.role == LLMMessageRole.Assistant
            }
            if (targetIndex >= 0) historyMessages.removeAt(targetIndex) else null
        } else {
            null
        }
        // 注入群聊任务提示（Group Nudge）
        if (context.generationMode.usesCharacterReplyTask()) {
            context.groupNudgePrompt()
                .resolve(context, context.memberNames())
                .takeIf { it.isNotBlank() }
                ?.let {
                    historyMessages += requiredUser(it, PromptSourceKind.GroupNudge)
                }
        }

        // 串联所有分区消息草稿
        val rawDrafts = buildList {
            addAll(fixedMessages.beforeHistory)
            addAll(historyMessages)
            addAll(fixedMessages.afterHistory)
            continueTarget?.let(::add)
            buildGenerationControlDraft(context)?.let(::add)
        }
        // 调用 Finalizer 执行协议后处理与 Token 预算裁剪
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
        // 组装调试检查器元数据
        val inspection = finalized.inspection.copy(
            regexExecutions = regexHits,
            regexErrors = regexErrors
        )
        val selectedWorldInfoIds = worldInfo.activatedEntries.map { it.id }.toSet()
        // 推进世界书下一时序状态
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

    /**
     * 按群聊消息、成员卡和会话场景激活本轮世界书条目。
     *
     * @param context 群聊构建上下文
     * @return 激活的世界书分组结果
     */
    private fun activateWorldInfo(context: GroupChatPromptContext): WorldBookActivationResult {
        val cardMembers = context.cardMembers()
        return mWorldBookActivator.activateStructured(
            WorldBookScanContext(
                messages = context.messages.map {
                    WorldBookScanMessage(it.speakerNameSnapshot, it.content)
                },
                currentUserMessage = null,
                totalMessageCount = context.totalMessageCount,
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

    /**
     * 构建位于聊天历史前后、位置固定的系统消息。
     *
     * @param context 群聊构建上下文
     * @param worldInfo 激活的世界书集合
     * @param exampleBehavior 示例对话保留策略
     * @return 包含 beforeHistory 与 afterHistory 的分区对象
     */
    private fun buildFixedMessages(
        context: GroupChatPromptContext,
        worldInfo: WorldBookActivationResult,
        exampleBehavior: ExampleDialogueBehavior
    ): PromptSections {
        val before = mutableListOf<PromptMessageDraft>()
        val after = mutableListOf<PromptMessageDraft>()
        val memberNames = context.memberNames()
        val summaryPosition = readSummaryInjectionPosition()

        // 摘要注入（BeforeMain 模式）
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
        // 摘要注入（AfterMain 模式）
        if (summaryPosition == SummaryInjectionPosition.AfterMain) {
            summaryDraft(context)?.let { before += it }
        }
        // 注入 beforeCharacter 世界书条目
        worldInfo.beforeCharacter.forEach {
            before += prioritizedWorldInfo(
                formatWorldInfo(it.content),
                PromptSource(PromptSourceKind.WorldInfo, it.name, it.id),
                canDrop = !it.ignoreBudget
            )
        }
        // 注入用户形象设定（User persona）
        before += requiredSystem(
            renderUserPersonaTemplate(
                template = readUserPersonaFormat(),
                userName = context.session.userName,
                userDescription = context.session.userDescription,
                characterName = context.speaker.name
            ),
            PromptSourceKind.UserPersona
        )
        // 注入合并后的成员角色卡
        before += buildCharacterCards(context)
        // 注入辅助提示词
        readAuxiliaryPrompt().takeIf { it.isNotBlank() }?.let {
            before += optionalSystem(
                it.resolve(context, memberNames),
                PromptSource(PromptSourceKind.AuxiliaryPrompt),
                PRIORITY_AUXILIARY
            )
        }
        // 注入 afterCharacter 世界书条目
        worldInfo.afterCharacter.forEach {
            before += prioritizedWorldInfo(
                formatWorldInfo(it.content),
                PromptSource(PromptSourceKind.WorldInfo, it.name, it.id),
                canDrop = !it.ignoreBudget
            )
        }
        // 注入示例对话与关联世界书条目
        val examplePriority = exampleBehavior
            .takeUnless { it == ExampleDialogueBehavior.Disabled }
            ?.let(PromptRetentionPolicy::examplePriority)
        examplePriority?.let { priority ->
            worldInfo.exampleBefore.forEach {
                before += optionalSystem(
                    formatWorldInfo(it.content),
                    PromptSource(PromptSourceKind.WorldInfo, it.name, it.id),
                    priority
                )
            }
        }
        buildExamples(context, exampleBehavior).forEach { before += it }
        examplePriority?.let { priority ->
            worldInfo.exampleAfter.forEach {
                before += optionalSystem(
                    formatWorldInfo(it.content),
                    PromptSource(PromptSourceKind.WorldInfo, it.name, it.id),
                    priority
                )
            }
        }
        // 注入新群聊分隔提示词
        context.newGroupChatPrompt().takeIf {
            it.isNotBlank() && context.messages.isNotEmpty()
        }?.let {
            before += optionalSystem(
                it.resolve(context, memberNames),
                PromptSource(PromptSourceKind.NewChatMarker),
                PRIORITY_NEW_CHAT
            )
        }

        // 注入历史后指令（Post-history instructions）
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
     *
     * @param context 群聊构建上下文
     * @return 包含描述、性格与场景的系统消息草稿列表
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

    /**
     * 合并角色字段，并在 Join 模式中标明每段内容所属角色。
     *
     * @param cardMembers 本轮可见成员列表
     * @param readField 读取指定角色卡字段的 lambda
     * @return 格式化后的合并文本
     */
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

    /**
     * 将成员角色卡中的示例对话转换为模型消息草稿。
     *
     * @param context 群聊构建上下文
     * @param behavior 示例对话保留策略枚举
     * @return 示例对话消息草稿列表
     */
    private fun buildExamples(
        context: GroupChatPromptContext,
        behavior: ExampleDialogueBehavior
    ): List<PromptMessageDraft> {
        if (behavior == ExampleDialogueBehavior.Disabled) return emptyList()
        val retentionPriority = PromptRetentionPolicy.examplePriority(behavior)
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
                                    retentionPriority
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
                                    retentionPriority
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
                                        retentionPriority = retentionPriority,
                                        canDrop = true
                                    )
                                )
                            }
                        }
                    }
                }
        }
    }

    /**
     * 构建需要按深度插入聊天历史的作者注释和世界书片段。
     *
     * @param context 群聊构建上下文
     * @param worldInfo 激活的世界书结果
     * @return 待深度插入的片段列表
     */
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
                message = prioritizedWorldInfo(
                    entry.content,
                    PromptSource(PromptSourceKind.WorldInfo, entry.name, entry.id),
                    canDrop = !entry.ignoreBudget
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
                message = prioritizedWorldInfo(
                    entry.content,
                    PromptSource(PromptSourceKind.WorldInfo, entry.name, entry.id),
                    canDrop = !entry.ignoreBudget
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
                    retentionPriority = PRIORITY_ESSENTIAL,
                    canDrop = group.entries.none { it.ignoreBudget },
                    sources = sources
                ),
                group.depth,
                first.order,
                first.id
            )
        }
        return pieces
    }

    /**
     * 按深度、顺序和稳定键将动态片段插入聊天历史。
     *
     * @param messages 历史消息草稿列表（将被就地更新）
     * @param pieces 待按深度插入的片段列表
     */
    private fun insertInChatPieces(
        messages: MutableList<PromptMessageDraft>,
        pieces: List<InChatPiece>
    ) {
        // 计算各注入项在消息列表中的相对位置
        val injections = pieces
            .groupBy { (messages.size - it.depth).coerceIn(0, messages.size) }
            .mapValues { (_, group) ->
                group.sortedWith(
                    compareBy<InChatPiece> { it.order }.thenBy { it.tieBreaker }
                )
            }
        // 按深度将各注入项流式合并到消息列表中
        val result = buildList {
            for (index in 0..messages.size) {
                injections[index]?.forEach { add(it.message) }
                if (index < messages.size) add(messages[index])
            }
        }
        messages.clear()
        messages.addAll(result)
    }

    /** 过滤群聊历史消息中的 `<think>...</think>` 推理思考块。 */
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

    /** 格式化群聊成员姓名逗号分隔字符串。 */
    private fun GroupChatPromptContext.memberNames(): String {
        return members.joinToString(", ") { it.character.name }
    }

    /** 读取并解析群聊主提示词。 */
    private fun GroupChatPromptContext.mainPrompt(): String {
        val original = readMainPrompt()
        return session.systemPromptOverride.trim()
            .ifBlank {
                speaker.systemPrompt.trim().ifBlank { original }
            }
            .resolve(this, memberNames(), original)
    }

    /** 读取并解析群聊历史后指令。 */
    private fun GroupChatPromptContext.postHistoryInstructions(): String {
        val original = readPostHistoryInstructions()
        return speaker.postHistoryInstructions.trim()
            .ifBlank { original }
            .resolve(this, memberNames(), original)
    }

    /** 读取群聊任务引导词（Group Nudge）。 */
    private fun GroupChatPromptContext.groupNudgePrompt(): String {
        return session.groupNudgePromptOverride.trim()
            .ifBlank { readGroupNudgePrompt() }
    }

    /** 读取新群聊标记提示词。 */
    private fun GroupChatPromptContext.newGroupChatPrompt(): String {
        return session.newGroupChatPromptOverride.trim()
            .ifBlank { readNewGroupChatPrompt() }
    }

    /** 将群聊消息实体转换为带发言者前缀的 Prompt 草稿。 */
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

    /** 格式化世界书条目内容。 */
    private fun formatWorldInfo(content: String): String {
        return readWorldInfoFormat().replace("{0}", content)
    }

    /** 格式化性格描述文本。 */
    private fun formatPersonality(content: String): String {
        return readPersonalityFormat().let { template ->
            if (template.contains("{{personality}}")) {
                template.replace("{{personality}}", content)
            } else {
                content
            }
        }
    }

    /** 格式化场景描述文本。 */
    private fun formatScenario(content: String): String {
        return readScenarioFormat().let { template ->
            if (template.contains("{{scenario}}")) {
                template.replace("{{scenario}}", content)
            } else {
                content
            }
        }
    }

    /** 读取全局主提示词。 */
    private fun readMainPrompt(): String =
        runCatching { AppModel.mainPrompt }.getOrDefault(AppModel.DEFAULT_MAIN_PROMPT)

    /** 读取历史后指令。 */
    private fun readPostHistoryInstructions(): String =
        runCatching { AppModel.postHistoryInstructions }.getOrDefault("")

    /** 读取辅助提示词。 */
    private fun readAuxiliaryPrompt(): String =
        runCatching { AppModel.auxiliaryPrompt }
            .getOrDefault(AppModel.DEFAULT_AUXILIARY_PROMPT)

    /** 读取扮演用户提示词。 */
    private fun readImpersonationPrompt(): String =
        runCatching { AppModel.impersonationPrompt }
            .getOrDefault(AppModel.DEFAULT_IMPERSONATION_PROMPT)

    /** 读取续写引导提示词。 */
    private fun readContinueNudgePrompt(): String =
        runCatching { AppModel.continueNudgePrompt }
            .getOrDefault(AppModel.DEFAULT_CONTINUE_NUDGE_PROMPT)

    /** 读取示例对话分隔标记提示词。 */
    private fun readNewExampleChatPrompt(): String =
        runCatching { AppModel.newExampleChatPrompt }
            .getOrDefault(AppModel.DEFAULT_NEW_EXAMPLE_CHAT_PROMPT)

    /** 读取群聊任务引导提示词（Group Nudge）。 */
    private fun readGroupNudgePrompt(): String =
        runCatching { AppModel.groupNudgePrompt }
            .getOrDefault(AppModel.DEFAULT_GROUP_NUDGE_PROMPT)

    /** 读取新群聊标记提示词。 */
    private fun readNewGroupChatPrompt(): String =
        runCatching { AppModel.newGroupChatPrompt }
            .getOrDefault(AppModel.DEFAULT_NEW_GROUP_CHAT_PROMPT)

    /** 读取世界书包装模板。 */
    private fun readWorldInfoFormat(): String =
        runCatching { AppModel.worldInfoFormat }
            .getOrDefault(AppModel.DEFAULT_WORLD_INFO_FORMAT)

    /** 读取性格包装模板。 */
    private fun readPersonalityFormat(): String =
        runCatching { AppModel.personalityFormat }
            .getOrDefault(AppModel.DEFAULT_PERSONALITY_FORMAT)

    /** 读取场景包装模板。 */
    private fun readScenarioFormat(): String =
        runCatching { AppModel.scenarioFormat }
            .getOrDefault(AppModel.DEFAULT_SCENARIO_FORMAT)

    /** 读取统一用户人设包装模板。 */
    private fun readUserPersonaFormat(): String =
        runCatching { AppModel.userPersonaFormat }
            .getOrDefault(AppModel.DEFAULT_USER_PERSONA_FORMAT)

    /** 读取世界书预算百分比。 */
    private fun readWorldInfoBudgetPercent(): Int =
        runCatching { AppModel.worldInfoBudgetPercent }.getOrDefault(25)

    /** 读取世界书绝对 Token 预算上限。 */
    private fun readWorldInfoBudgetCap(): Int =
        runCatching { AppModel.worldInfoBudgetCap }
            .getOrDefault(0)

    /** 读取是否在上下文中保留推理思考块。 */
    private fun readIncludeThinkInContext(): Boolean =
        runCatching { AppModel.includeThinkInContext }.getOrDefault(false)

    /** 读取 Prompt 后处理模式。 */
    private fun readPostProcessingMode(provider: LLMProvider): PromptPostProcessingMode {
        return PromptPostProcessingMode.fromOrdinal(provider.promptPostProcessingMode)
    }

    /** 读取摘要注入位置。 */
    private fun readSummaryInjectionPosition(): SummaryInjectionPosition {
        return SummaryInjectionPosition.fromPersistedValue(
            runCatching { AppModel.summaryInjectionPosition }
                .getOrDefault(SummaryInjectionPosition.default.persistedValue)
        )
    }

    /** 读取摘要注入深度。 */
    private fun readSummaryInjectionDepth(): Int {
        return runCatching { AppModel.summaryInjectionDepth }
            .getOrDefault(2)
            .coerceAtLeast(0)
    }

    /** 读取摘要注入角色。 */
    private fun readSummaryInjectionRole(): SummaryInjectionRole {
        return SummaryInjectionRole.fromPersistedValue(
            runCatching { AppModel.summaryInjectionRole }.getOrDefault(0)
        )
    }

    /** 构建群聊摘要消息草稿。 */
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
     * 两种特殊模式统一使用 user 角色，使所有模型服务都接收到明确且位于末尾的生成目标。
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

    /** 创建不可裁剪的 System 角色消息草稿。 */
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

    /** 创建不可裁剪的 User 角色消息草稿。 */
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

    /** 预算内世界书优先于聊天历史保留，极端超限时仍允许最终安全裁剪。 */
    private fun prioritizedWorldInfo(
        content: String,
        source: PromptSource,
        canDrop: Boolean
    ): PromptMessageDraft {
        return PromptMessageDraft(
            role = LLMMessageRole.System,
            content = content,
            source = source,
            retentionPriority = PRIORITY_ESSENTIAL,
            canDrop = canDrop
        )
    }

    /** 创建可选系统消息草稿。 */
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

    /** 包含历史前与历史后固定系统消息的分区容器。 */
    private data class PromptSections(
        /** 插入聊天历史之前的 Prompt 内容列表。 */
        val beforeHistory: List<PromptMessageDraft>,
        /** 插入聊天历史之后的 Prompt 内容列表。 */
        val afterHistory: List<PromptMessageDraft>
    )

    /** 包含深度排序元数据的历史内部注入片段。 */
    private data class InChatPiece(
        /** 需要展示或传递的消息内容。 */
        val message: PromptMessageDraft,
        /** 当前内容相对聊天末尾的插入或扫描深度。 */
        val depth: Int,
        /** 当前对象在同类数据中的排序值。 */
        val order: Int,
        /** 业务优先级相同时用于保持稳定顺序的次级排序值。 */
        val tieBreaker: Long
    )

    private companion object {
        /** 辅助提示词（Auxiliary Prompt）的上下文保留优先级。 */
        const val PRIORITY_AUXILIARY = 20
        /** 新群聊标记提示词（New Chat Marker）的上下文保留优先级。 */
        const val PRIORITY_NEW_CHAT = 30
        /** 用户便签（User Note / AN）的上下文保留优先级。 */
        const val PRIORITY_USER_NOTE = 300
        /** 角色深度提示（Character Note / Depth Prompt）的上下文保留优先级。 */
        const val PRIORITY_CHARACTER_NOTE = 310
        /** 核心关键内容（如预算内世界书、摘要等）的上下文保留优先级。 */
        const val PRIORITY_ESSENTIAL = 1_000
        /** 用户便签（User Note / AN）默认插入的群聊历史深度（倒数第 4 条）。 */
        const val USER_NOTE_DEPTH = 4
        /** 摘要在同一深度插入时的内部排序序号（极小值保证排在最前）。 */
        const val SUMMARY_ORDER = Int.MIN_VALUE
        /** AN Top 世界书条目在同一深度插入时的内部排序序号。 */
        const val AN_TOP_ORDER = Int.MIN_VALUE + 1
        /** 用户便签在同一深度插入时的内部排序序号。 */
        const val USER_NOTE_ORDER = Int.MIN_VALUE + 2
        /** AN Bottom 世界书条目在同一深度插入时的内部排序序号。 */
        const val AN_BOTTOM_ORDER = Int.MIN_VALUE + 3
        /** 角色深度提示在同一深度插入时的内部排序序号。 */
        const val CHARACTER_NOTE_ORDER = Int.MIN_VALUE + 4
        /** 兜底的角色回复触发引导词，当未开启指定 Nudge 时确保群聊末尾由 User 轮次触发目标角色生成。 */
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
