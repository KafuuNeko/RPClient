package me.kafuuneko.rpclient.libs.groupchat

import me.kafuuneko.rpclient.libs.macro.MacroExpansion
import me.kafuuneko.rpclient.libs.macro.MacroValue
import me.kafuuneko.rpclient.libs.prompt.buildUserWritingPrompt
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.prompt.macro.promptMacroExpander
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehaviorProvider
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspection
import me.kafuuneko.rpclient.libs.prompt.model.PromptMessageDraft
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.model.PromptRetentionPolicy
import me.kafuuneko.rpclient.libs.prompt.model.PromptSource
import me.kafuuneko.rpclient.libs.prompt.model.PromptSourceKind
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionPosition
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionRole
import me.kafuuneko.rpclient.libs.prompt.model.UnavailablePromptImage
import me.kafuuneko.rpclient.libs.prompt.parseExampleMessages
import me.kafuuneko.rpclient.libs.prompt.preset.PromptPreferences
import me.kafuuneko.rpclient.libs.prompt.request.DEFAULT_STRICT_PROMPT_PLACEHOLDER
import me.kafuuneko.rpclient.libs.prompt.request.PromptPostProcessingNames
import me.kafuuneko.rpclient.libs.prompt.request.PromptRequestFinalizer
import me.kafuuneko.rpclient.libs.prompt.summary.summarySafeContent
import me.kafuuneko.rpclient.libs.prompt.worldbook.WorldBookActivationResult
import me.kafuuneko.rpclient.libs.prompt.worldbook.WorldBookActivator
import me.kafuuneko.rpclient.libs.prompt.worldbook.WorldBookGenerationType
import me.kafuuneko.rpclient.libs.prompt.worldbook.WorldBookScanContext
import me.kafuuneko.rpclient.libs.prompt.worldbook.WorldBookScanMessage
import me.kafuuneko.rpclient.libs.prompt.worldbook.filterForExampleBehavior
import me.kafuuneko.rpclient.libs.prompt.worldbook.fitWorldInfoToBudget
import me.kafuuneko.rpclient.libs.prompt.worldbook.mapEntryContent
import me.kafuuneko.rpclient.libs.prompt.worldbook.resolveWorldInfoBudget
import me.kafuuneko.rpclient.libs.prompt.worldbook.retainStateEntries
import me.kafuuneko.rpclient.libs.regex.RegexExecutionError
import me.kafuuneko.rpclient.libs.regex.RegexExecutionHit
import me.kafuuneko.rpclient.libs.regex.RegexMessageProcessor
import me.kafuuneko.rpclient.libs.regex.RegexMessageSource
import me.kafuuneko.rpclient.libs.regex.RegexScriptEngine
import me.kafuuneko.rpclient.libs.regex.RegexScriptRuntime
import me.kafuuneko.rpclient.libs.regex.ScopedRegexScript
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.entity.UserPersona
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
 * @property protectedUserMessageId 仅本批次不可裁剪的触发用户消息，后续无新输入批次为空
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
    val regexScripts: List<ScopedRegexScript> = emptyList(),
    val messageImages: Map<Long, List<LLMImageReference>> = emptyMap(),
    val unavailableImages: Map<Long, List<UnavailablePromptImage>> = emptyMap(),
    val protectedUserMessageId: Long? = null,
    /** Classic 摘要复用普通上下文，末尾任务取代群聊发言引导。 */
    val summaryInstruction: String? = null,
    val persona: UserPersona = UserPersona().withOverrides(
        session.userName,
        session.userDescription
    ),
    /** 输入框续写的起点；空串表示从头代写，null 表示输出到历史或其他生成任务。 */
    val userWritingDraft: String? = null,
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
    private val mPreferences: PromptPreferences,
    private val mWorldBookActivator: WorldBookActivator = WorldBookActivator(),
    private val mRegexRuntime: RegexScriptRuntime = RegexScriptRuntime(mEngine = RegexScriptEngine()),
    private val mRequestFinalizer: PromptRequestFinalizer = PromptRequestFinalizer(),
    private val mExampleDialogueBehaviorProvider: ExampleDialogueBehaviorProvider =
        ExampleDialogueBehaviorProvider { ExampleDialogueBehavior.default },
    private val mRegexProcessor: RegexMessageProcessor = RegexMessageProcessor(mRegexRuntime)
) {
    /** 使用调用方捕获的偏好构建请求，测试可复用构造时的固定配置。 */
    fun build(
        context: GroupChatPromptContext,
        preferences: PromptPreferences = mPreferences
    ): LLMGenerationRequest =
        buildWithMetadata(context, preferences).request

    /**
     * 使用独立构建会话准备群聊请求，Classic 摘要始终排除推理块。
     * @param context 本轮群聊只读快照。
     * @param preferences 本轮预设与偏好。
     * @return 请求、检查信息及尚未持久化的世界书时序结果。
     */
    fun buildWithMetadata(
        context: GroupChatPromptContext,
        preferences: PromptPreferences = mPreferences
    ): GroupChatPromptBuildResult {
        // 清洗发生在世界书扫描之前，防止推理内容重新进入摘要背景。
        val safeContext = if (context.summaryInstruction == null) context else context.copy(
            summary = context.summary.summarySafeContent(),
            messages = context.messages.map { it.copy(content = it.content.summarySafeContent()) },
        )
        // 单次构建不回写任何消息或世界书状态。
        return GroupChatPromptBuildSession(
            mMacros = GroupChatMacros(
                context.session,
                context.members,
                context.persona,
                context.speaker
            ),
            mPreferences = preferences,
            mWorldBookActivator = mWorldBookActivator,
            mRegexRuntime = mRegexRuntime,
            mRequestFinalizer = mRequestFinalizer,
            mExampleDialogueBehaviorProvider = mExampleDialogueBehaviorProvider,
            mRegexProcessor = mRegexProcessor,
        ).buildWithMetadata(safeContext)
    }
}

/** 一次构建的只读依赖与提示词快照，不跨请求复用。 */
private class GroupChatPromptBuildSession(
    private val mMacros: GroupChatMacros,
    private val mPreferences: PromptPreferences,
    private val mWorldBookActivator: WorldBookActivator = WorldBookActivator(),
    private val mRegexRuntime: RegexScriptRuntime = RegexScriptRuntime(mEngine = RegexScriptEngine()),
    private val mRequestFinalizer: PromptRequestFinalizer = PromptRequestFinalizer(),
    private val mExampleDialogueBehaviorProvider: ExampleDialogueBehaviorProvider =
        ExampleDialogueBehaviorProvider { ExampleDialogueBehavior.default },
    private val mRegexProcessor: RegexMessageProcessor = RegexMessageProcessor(mRegexRuntime)
) {
    private val mSpeakerMacros = mMacros.forCharacter()
    private lateinit var mOutlets: GroupChatOutlets

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
        val tokenizer = mRequestFinalizer.tokenizerFor(context.provider)
        // 扣除回复预留，再由统一预算函数归一化并应用世界书上限
        val worldBudget = resolveWorldInfoBudget(
            promptTokenBudget = context.provider.contextTokens - context.provider.maxTokens,
            contextPercent = mPreferences.worldInfoBudgetPercent,
            tokenBudgetCap = mPreferences.worldInfoBudgetCap,
        )
        val regexHits = mutableListOf<RegexExecutionHit>()
        val regexErrors = mutableListOf<RegexExecutionError>()
        // 扫描群聊多成员与历史消息激活世界书条目
        val rawWorldInfo = activateWorldInfo(context)
        // 执行世界书 Prompt 阶段 Regex 替换
        val activatedWorldInfo = rawWorldInfo
            .mapEntryContent { entry ->
                val macros = mMacros.forLorebook(entry.lorebookId)
                val result = mRegexProcessor.applyWorldInfo(
                    input = entry.content,
                    scripts = context.regexScripts,
                    macros = macros,
                )
                regexHits += result.hits
                regexErrors += result.errors
                entry.copy(content = renderGroupMacros(result.text, macros))
            }
            .filterForExampleBehavior(exampleBehavior)
        // 按预算比例裁剪世界书条目
        val worldSelection = fitWorldInfoToBudget(
            result = activatedWorldInfo,
            globalTokenBudget = worldBudget,
            lorebooks = context.candidateLorebooks,
            tokenizer = tokenizer,
        )
        val worldInfo = worldSelection.result
        mOutlets = GroupChatOutlets(worldInfo.outletEntries)
        // 构建固定系统消息区段（主提示词、多角色卡合并、用户画像等）
        val fixedMessages = buildFixedMessages(context, worldInfo, exampleBehavior)
        // 构建待按深度插入的 In-Chat 注入项
        val inChatPieces = buildInChatPieces(context, worldInfo)
        // 过滤推理块并对群聊历史消息执行 Regex 替换
        val history = sanitizeHistory(
            context.messages,
            (context.messageImages.keys + context.unavailableImages.keys)
        ).mapIndexed { index, message ->
            val source = when (message.source) {
                GroupChatMessage.Source.User -> RegexMessageSource.User
                GroupChatMessage.Source.Character -> RegexMessageSource.Character
                GroupChatMessage.Source.System -> return@mapIndexed message
            }
            val result = mRegexProcessor.applyPrompt(
                input = message.content,
                source = source,
                scripts = context.regexScripts,
                macros = mMacros.forMessage(message),
                depth = context.messages.lastIndex - index,
            )
            regexHits += result.hits
            regexErrors += result.errors
            message.copy(content = result.text)
        }
        // 将历史消息转换为带发言者前缀的 Prompt 草稿
        val historyMessages = history.mapIndexed { index, message ->
            val hasImages = !context.messageImages[message.id].isNullOrEmpty()
                || !context.unavailableImages[message.id].isNullOrEmpty()
            // 只保护明确触发本批次的用户图文，旧批次图片恢复普通历史裁剪规则。
            val protectsUserImages = hasImages && message.source == GroupChatMessage.Source.User
                && message.id == context.protectedUserMessageId
            message.toPromptDraft(
                userName = context.persona.name,
                retentionPriority = PromptRetentionPolicy.HISTORY,
                canDrop = index != history.lastIndex && !protectsUserImages,
            ).copy(
                images = context.messageImages[message.id].orEmpty(),
                unavailableImages = context.unavailableImages[message.id].orEmpty(),
            )
        }.toMutableList()
        // 将 In-Chat 片段按深度插入群聊历史
        insertInChatPieces(historyMessages, inChatPieces)
        // 若处于 Continue 模式提取待续写的目标消息
        val continueTarget = if (context.generationMode == GroupChatGenerationMode.Continue) {
            val targetIndex = historyMessages.indexOfLast { draft ->
                draft.source.kind == PromptSourceKind.ChatHistory
                    && draft.role == LLMMessageRole.Assistant
            }
            if (targetIndex >= 0) historyMessages.removeAt(targetIndex) else null
        } else {
            null
        }
        // 注入群聊任务提示（Group Nudge）
        if (context.summaryInstruction == null && context.generationMode.usesCharacterReplyTask()) {
            render(context.groupNudgePrompt(), mSpeakerMacros)
                .takeIf { it.text.isNotBlank() }
                ?.let { historyMessages += requiredUser(it, PromptSourceKind.GroupNudge) }
        }

        // 串联所有分区消息草稿
        val rawDrafts = buildList {
            addAll(fixedMessages.beforeHistory)
            addAll(historyMessages)
            addAll(fixedMessages.afterHistory)
            continueTarget?.let(::add)
            buildGenerationControlDraft(context)?.let(::add)
        }
        val drafts = rawDrafts + listOfNotNull(buildUserWritingDraft(context))
        // 调用 Finalizer 执行协议后处理与 Token 预算裁剪
        val finalized = mRequestFinalizer.finalize(
            drafts = ensureTerminalCharacterReplyTurn(drafts, context),
            provider = context.provider,
            model = context.provider.model,
            options = LLMGenerationOptions(
                temperature = context.provider.temperature,
                maxTokens = context.provider.maxTokens,
                topP = context.provider.topP,
            ),
            includeReasoningInContent = context.userWritingDraft == null,
            captureReasoning = true,
            maxContextTokens = context.provider.contextTokens,
            maxResponseTokens = context.provider.maxTokens,
            postProcessingMode = readPostProcessingMode(context.provider),
            strictPromptPlaceholder = DEFAULT_STRICT_PROMPT_PLACEHOLDER,
            postProcessingNames = PromptPostProcessingNames(
                userName = context.persona.name,
                characterName = context.speaker.name,
                groupNames = context.members.map { it.character.name },
            ),
            preOmittedItems = worldSelection.omittedItems,
            tokenizer = tokenizer,
        )
        // 没有可用历史时不能用空素材摘要推进覆盖位置。
        if (context.summaryInstruction != null) {
            require(finalized.inspection.items.any { item ->
                item.sources.any { it.kind == PromptSourceKind.ChatHistory }
            }) { "No chat history fits in classic summary context" }
        }
        // 组装调试检查器元数据
        val inspection = finalized.inspection.copy(
            regexExecutions = regexHits,
            regexErrors = regexErrors,
        )
        // 推进世界书下一时序状态
        val stateResult = mWorldBookActivator.resolveNextState(
            result = rawWorldInfo.retainStateEntries(inspection, includeUntrackedOutlets = false),
        )
        return GroupChatPromptBuildResult(
            request = finalized.request,
            worldInfoStateJson = stateResult.nextStateJson,
            inspection = inspection,
        )
    }

    /**
     * 按群聊消息、成员卡和会话场景激活本轮世界书条目。
     *
     * @param context 群聊构建上下文
     * @return 激活的世界书分组结果
     */
    private fun activateWorldInfo(context: GroupChatPromptContext): WorldBookActivationResult {
        val cardMembers = mMacros.cardMembers

        // 保留扫描源中的成员署名，即使卡片字段为空也可按原有规则匹配名字。
        fun scanField(field: (Character) -> String): String =
            cardMembers.joinToString("\n") { member ->
                val character = member.character
                val fieldText = renderGroupMacros(
                    template = field(character),
                    values = mMacros.forCharacter(character)
                )
                "${character.name}: $fieldText"
            }
        return mWorldBookActivator.activateStructured(
            context = WorldBookScanContext(
                messages = context.messages.map { message ->
                    WorldBookScanMessage(
                        speakerName = message.speakerName(context.persona.name),
                        content = message.content,
                    )
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
                characterDescription = scanField { it.description },
                userDescription = renderGroupMacros("{{persona}}", mSpeakerMacros),
                characterPersonality = scanField { it.personality },
                characterDepthPrompt = scanField { it.depthPromptPrompt },
                scenario = if (context.session.scenario.isNotBlank()) renderGroupMacros(
                    "{{scenario}}",
                    mSpeakerMacros
                )
                else cardMembers.joinToString("\n") { member ->
                    val text = renderGroupMacros(
                        member.character.scenario,
                        mMacros.forCharacter(member.character)
                    )
                    if (context.session.characterCardMode == GroupChatSession.CharacterCardMode.Join) "${member.character.name}:\n$text" else text
                },
                creatorNotes = scanField { it.creatorNotes },
            ),
            recursiveContent = { entry ->
                renderGroupMacros(
                    entry.content,
                    mMacros.forLorebook(entry.lorebookId)
                )
            },
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
        exampleBehavior: ExampleDialogueBehavior,
    ): PromptSections {
        val before = mutableListOf<PromptMessageDraft>()
        val after = mutableListOf<PromptMessageDraft>()
        val summaryPosition = readSummaryInjectionPosition().let { position ->
            if (context.summaryInstruction != null && position == SummaryInjectionPosition.None) {
                SummaryInjectionPosition.BeforeMain
            } else position
        }

        // 摘要注入（BeforeMain 模式）
        if (summaryPosition == SummaryInjectionPosition.BeforeMain) {
            summaryDraft(context)?.let { before += it }
        }
        // 群聊主提示词同样属于“当前角色回复”任务，续写和扮演用户时必须完全停用。
        if (context.generationMode.usesCharacterReplyTask()) {
            before += requiredSystem(
                content = context.mainPrompt(),
                sourceKind = PromptSourceKind.MainPrompt,
            )
        }
        // 摘要注入（AfterMain 模式）
        if (summaryPosition == SummaryInjectionPosition.AfterMain) {
            summaryDraft(context)?.let { before += it }
        }
        // 注入 beforeCharacter 世界书条目
        worldInfo.beforeCharacter.forEach { entry ->
            before += prioritizedWorldInfo(
                content = formatWorldInfo(entry),
                source = PromptSource(
                    kind = PromptSourceKind.WorldInfo,
                    detail = entry.name,
                    referenceId = entry.id
                ),
                canDrop = !entry.ignoreBudget,
            )
        }
        // 注入用户形象设定（User persona）
        before += requiredSystem(
            content = formatField(
                mPreferences.userPersonaFormat,
                "persona",
                MacroValue.Template(mSpeakerMacros.getValue("persona")),
                mSpeakerMacros
            ),
            sourceKind = PromptSourceKind.UserPersona,
        )
        // 注入合并后的成员角色卡
        before += buildCharacterCards(context)
        // 注入辅助提示词
        mPreferences.auxiliaryPrompt.takeIf { it.isNotBlank() }?.let { text ->
            before += optionalSystem(
                content = render(text, mSpeakerMacros),
                source = PromptSource(PromptSourceKind.AuxiliaryPrompt),
                retentionPriority = PRIORITY_AUXILIARY,
            )
        }
        // 注入 afterCharacter 世界书条目
        worldInfo.afterCharacter.forEach { entry ->
            before += prioritizedWorldInfo(
                content = formatWorldInfo(entry),
                source = PromptSource(
                    kind = PromptSourceKind.WorldInfo,
                    detail = entry.name,
                    referenceId = entry.id
                ),
                canDrop = !entry.ignoreBudget,
            )
        }
        // 注入示例对话与关联世界书条目
        val examplePriority = exampleBehavior
            .takeUnless { it == ExampleDialogueBehavior.Disabled }
            ?.let(PromptRetentionPolicy::examplePriority)
        examplePriority?.let { priority ->
            worldInfo.exampleBefore.forEach { entry ->
                before += optionalSystem(
                    content = formatWorldInfo(entry),
                    source = PromptSource(
                        kind = PromptSourceKind.WorldInfo,
                        detail = entry.name,
                        referenceId = entry.id,
                    ),
                    retentionPriority = priority,
                )
            }
        }
        buildExamples(context, exampleBehavior).forEach { before += it }
        examplePriority?.let { priority ->
            worldInfo.exampleAfter.forEach { entry ->
                before += optionalSystem(
                    content = formatWorldInfo(entry),
                    source = PromptSource(
                        kind = PromptSourceKind.WorldInfo,
                        detail = entry.name,
                        referenceId = entry.id,
                    ),
                    retentionPriority = priority,
                )
            }
        }
        // 注入新群聊分隔提示词
        context.newGroupChatPrompt().takeIf { text ->
            text.isNotBlank() && context.messages.isNotEmpty()
        }?.let { text ->
            before += optionalSystem(
                content = render(text, mSpeakerMacros),
                source = PromptSource(PromptSourceKind.NewChatMarker),
                retentionPriority = PRIORITY_NEW_CHAT,
            )
        }

        // 注入历史后指令（Post-history instructions）
        if (context.generationMode.usesCharacterReplyTask()) {
            context.postHistoryInstructions().takeIf { it.text.isNotBlank() }?.let { text ->
                after += requiredSystem(
                    content = text,
                    sourceKind = PromptSourceKind.PostHistoryInstructions,
                )
            }
        }
        return PromptSections(
            beforeHistory = before.filter { it.content.isNotBlank() },
            afterHistory = after.filter { it.content.isNotBlank() },
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
        val members = mMacros.cardMembers
        val description = context.combineCharacterField(
            cardMembers = members,
            template = mPreferences.descriptionFormat,
            slot = "description",
        ) { it.description }
        val personality = context.combineCharacterField(
            cardMembers = members, template = mPreferences.personalityFormat, slot = "personality",
        ) { it.personality }
        // 群场景覆盖只注入一次，卡内场景则各自使用所属角色的模板。
        val scenario = if (context.session.scenario.isNotBlank()) {
            formatField(
                mPreferences.scenarioFormat,
                "scenario",
                MacroValue.Template(mSpeakerMacros.getValue("scenario")),
                mSpeakerMacros
            )
        } else {
            context.combineCharacterField(
                members,
                template = mPreferences.scenarioFormat,
                slot = "scenario"
            ) { it.scenario }
        }

        return buildList {
            description.takeIf { text -> text.text.isNotBlank() }?.let { text ->
                add(requiredSystem(text, PromptSourceKind.CharacterDescription))
            }
            personality.takeIf { text -> text.text.isNotBlank() }?.let { text ->
                add(
                    element = requiredSystem(
                        content = text,
                        sourceKind = PromptSourceKind.CharacterPersonality,
                    ),
                )
            }
            scenario.takeIf { text -> text.text.isNotBlank() }?.let { text ->
                add(requiredSystem(text, PromptSourceKind.Scenario))
            }
        }
    }

    /**
     * 合并角色字段，并在 Join 模式中标明每段内容所属角色。
     *
     * @param cardMembers 本轮可见成员列表
     * @param template 单个成员字段使用的外层格式。
     * @param slot 正文在格式中的插槽名。
     * @param readField 读取指定角色卡字段的 lambda
     * @return 格式化后的合并文本
     */
    private fun GroupChatPromptContext.combineCharacterField(
        cardMembers: List<GroupChatMemberData>,
        template: String,
        slot: String,
        readField: (Character) -> String,
    ): MacroExpansion {
        val rendered = cardMembers.mapNotNull { member ->
            val raw = readField(member.character).trim()
            if (raw.isBlank()) return@mapNotNull null
            val macros = mMacros.forCharacter(member.character)
            val text = formatField(template, slot, MacroValue.Template(raw), macros)
            if (session.characterCardMode == GroupChatSession.CharacterCardMode.Join) {
                text.copy(text = "${member.character.name}:\n${text.text}")
            } else text
        }
        return MacroExpansion(
            rendered.joinToString("\n") { it.text },
            rendered.flatMap { it.bindings }.toSet()
        )
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
        behavior: ExampleDialogueBehavior,
    ): List<PromptMessageDraft> {
        if (!context.session.includeCharacterExamples || behavior == ExampleDialogueBehavior.Disabled) {
            return emptyList()
        }
        // 示例中的用户宏和称谓与本轮 Prompt 身份保持一致。
        val retentionPriority = PromptRetentionPolicy.examplePriority(behavior)
        return mMacros.cardMembers.flatMap { member ->
            val macros = mMacros.forCharacter(member.character)
            member.character.examplesOfDialogue
                .split("<START>")
                .map { text -> text.trim() }
                .filter { text -> text.isNotBlank() }
                .flatMap { block ->
                    buildList {
                        mPreferences.newExampleChatPrompt.takeIf { text -> text.isNotBlank() }
                            ?.let { text ->
                                add(
                                    element = optionalSystem(
                                        content = render(text, macros),
                                        source = PromptSource(
                                            kind = PromptSourceKind.ExampleDialogue,
                                            detail = member.character.name,
                                        ),
                                        retentionPriority = retentionPriority,
                                    ),
                                )
                            }
                        val parsed = parseExampleMessages(
                            block = block,
                            userName = context.persona.name,
                            characterName = member.character.name,
                        )
                        if (parsed.isEmpty()) {
                            add(
                                element = optionalSystem(
                                    content = render(block, macros).let {
                                        it.copy(text = "${member.character.name} example:\n${it.text}")
                                    },
                                    source = PromptSource(
                                        kind = PromptSourceKind.ExampleDialogue,
                                        detail = member.character.name,
                                    ),
                                    retentionPriority = retentionPriority,
                                ),
                            )
                        } else {
                            parsed.forEach { message ->
                                val speaker = if (message.role == LLMMessageRole.User) {
                                    context.persona.name
                                } else {
                                    member.character.name
                                }
                                add(
                                    element = templateDraft(
                                        content = render(message.content, macros).let {
                                            it.copy(text = "$speaker: ${it.text}")
                                        },
                                        role = message.role,
                                        source = PromptSource(
                                            PromptSourceKind.ExampleDialogue,
                                            member.character.name
                                        ),
                                        retentionPriority = retentionPriority,
                                        canDrop = true,
                                    ),
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
        worldInfo: WorldBookActivationResult,
    ): List<InChatPiece> {
        val pieces = mutableListOf<InChatPiece>()
        if (readSummaryInjectionPosition() == SummaryInjectionPosition.InChat) {
            summaryDraft(context)?.let { draft ->
                pieces += InChatPiece(
                    message = draft,
                    depth = readSummaryInjectionDepth(),
                    order = SUMMARY_ORDER,
                    tieBreaker = Long.MIN_VALUE,
                )
            }
        }
        worldInfo.anTop.forEachIndexed { index, entry ->
            pieces += InChatPiece(
                message = prioritizedWorldInfo(
                    content = MacroExpansion(entry.content, emptySet()),
                    source = PromptSource(
                        kind = PromptSourceKind.WorldInfo,
                        detail = entry.name,
                        referenceId = entry.id,
                    ),
                    canDrop = !entry.ignoreBudget,
                ),
                depth = USER_NOTE_DEPTH,
                order = AN_TOP_ORDER,
                tieBreaker = index.toLong(),
            )
        }
        context.session.userNote.takeIf { it.isNotBlank() }?.let { text ->
            pieces += InChatPiece(
                message = optionalSystem(
                    content = render(text, mSpeakerMacros),
                    source = PromptSource(PromptSourceKind.UserNote),
                    retentionPriority = PRIORITY_USER_NOTE,
                ),
                depth = USER_NOTE_DEPTH,
                order = USER_NOTE_ORDER,
                tieBreaker = Long.MIN_VALUE,
            )
        }
        worldInfo.anBottom.forEachIndexed { index, entry ->
            pieces += InChatPiece(
                message = prioritizedWorldInfo(
                    content = MacroExpansion(entry.content, emptySet()),
                    source = PromptSource(
                        kind = PromptSourceKind.WorldInfo,
                        detail = entry.name,
                        referenceId = entry.id,
                    ),
                    canDrop = !entry.ignoreBudget,
                ),
                depth = USER_NOTE_DEPTH,
                order = AN_BOTTOM_ORDER,
                tieBreaker = index.toLong(),
            )
        }
        // 群聊 Character Note 同样面向角色输出，特殊模式只保留人物与世界背景。
        if (context.generationMode.usesCharacterReplyTask()) {
            mMacros.cardMembers.forEach { member ->
                member.character.depthPromptPrompt.takeIf { text -> text.isNotBlank() }
                    ?.let { text ->
                        pieces += InChatPiece(
                            message = templateDraft(
                                content = render(text, mMacros.forCharacter(member.character)),
                                role = member.character.depthPromptRole.toMessageRole(),
                                source = PromptSource(
                                    PromptSourceKind.CharacterNote,
                                    member.character.name
                                ),
                                retentionPriority = PRIORITY_CHARACTER_NOTE,
                                canDrop = true,
                            ),
                            depth = member.character.depthPromptDepth.coerceAtLeast(0),
                            order = CHARACTER_NOTE_ORDER,
                            tieBreaker = member.character.id,
                        )
                    }
            }
        }
        worldInfo.depthEntries.forEach { group ->
            val sources = group.entries.map { entry ->
                PromptSource(
                    kind = PromptSourceKind.WorldInfo,
                    detail = entry.name,
                    referenceId = entry.id
                )
            }
            val first = group.entries.firstOrNull() ?: return@forEach
            pieces += InChatPiece(
                message = PromptMessageDraft(
                    role = group.role,
                    content = group.entries.joinToString("\n") { entry -> entry.content },
                    source = sources.first(),
                    retentionPriority = PRIORITY_ESSENTIAL,
                    canDrop = group.entries.none { entry -> entry.ignoreBudget },
                    sources = sources,
                ),
                depth = group.depth,
                order = first.order,
                tieBreaker = first.id,
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
        pieces: List<InChatPiece>,
    ) {
        // 计算各注入项在消息列表中的相对位置
        val injections = pieces
            .groupBy {
                (messages.size - it.depth).coerceIn(
                    minimumValue = 0,
                    maximumValue = messages.size
                )
            }
            .mapValues { (_, group) ->
                group.sortedWith(
                    comparator = compareBy<InChatPiece> { inChatPiece -> inChatPiece.order }.thenBy { inChatPiece -> inChatPiece.tieBreaker },
                )
            }
        // 按深度将各注入项流式合并到消息列表中
        val result = buildList {
            for (index in 0..messages.size) {
                injections[index]?.forEach { inChatPiece -> add(inChatPiece.message) }
                if (index < messages.size) add(messages[index])
            }
        }
        messages.clear()
        messages.addAll(result)
    }

    /** 过滤群聊历史消息中的 `<think>...</think>` 推理思考块。 */
    private fun sanitizeHistory(
        messages: List<GroupChatMessage>,
        imageMessageIds: Set<Long>
    ): List<GroupChatMessage> {
        return messages.mapNotNull { message ->
            val cleaned = if (mPreferences.includeThinkInContext) {
                message.content
            } else {
                message.content.stripThinkBlocks()
            }.trim()
            if (cleaned.isBlank() && message.id !in imageMessageIds) null else message.copy(content = cleaned)
        }
    }

    /** 读取并解析群聊主提示词。 */
    private fun GroupChatPromptContext.mainPrompt(): MacroExpansion {
        val original = mPreferences.mainPrompt
        return session.systemPromptOverride.trim()
            .ifBlank {
                speaker.systemPrompt.trim().ifBlank { original }
            }
            .let { renderPromptOverride(it, original) }
    }

    /** 读取并解析群聊历史后指令。 */
    private fun GroupChatPromptContext.postHistoryInstructions(): MacroExpansion {
        val original = mPreferences.postHistoryInstructions
        return speaker.postHistoryInstructions.trim()
            .ifBlank { original }
            .let { renderPromptOverride(it, original) }
    }

    /** 读取群聊任务引导词（Group Nudge）。 */
    private fun GroupChatPromptContext.groupNudgePrompt(): String {
        return session.groupNudgePromptOverride.trim()
            .ifBlank { mPreferences.groupNudgePrompt }
    }

    /** 读取新群聊标记提示词。 */
    private fun GroupChatPromptContext.newGroupChatPrompt(): String {
        return session.newGroupChatPromptOverride.trim()
            .ifBlank { mPreferences.newGroupChatPrompt }
    }

    /** 将群聊消息实体转换为带发言者前缀的 Prompt 草稿。 */
    private fun GroupChatMessage.toPromptDraft(
        userName: String,
        retentionPriority: Int,
        canDrop: Boolean,
    ): PromptMessageDraft {
        // 仅在旧用户消息缺少署名时使用本轮名称，避免改写历史身份。
        val role = when (source) {
            GroupChatMessage.Source.User -> LLMMessageRole.User
            GroupChatMessage.Source.Character -> LLMMessageRole.Assistant
            GroupChatMessage.Source.System -> LLMMessageRole.System
        }
        val speaker = speakerName(userName)
        return PromptMessageDraft(
            role = role,
            content = "$speaker: $content",
            source = PromptSource(
                kind = PromptSourceKind.ChatHistory,
                detail = "Message #$id",
                referenceId = id
            ),
            retentionPriority = retentionPriority,
            canDrop = canDrop,
        )
    }

    /** 展开当前模板并保留被实际引用的世界书插槽。 */
    private fun render(
        template: String,
        values: Map<String, String> = mSpeakerMacros,
        original: String = template,
    ): MacroExpansion {
        val scope = groupMacroScope(values, mOutlets::value)
            .withValues(mapOf("original" to MacroValue.Template(original)))
        return promptMacroExpander.expand(template, scope)
    }

    /** 原始预设作为模板绑定参与同一次依赖求值。 */
    private fun renderPromptOverride(template: String, original: String): MacroExpansion =
        render(template, original = original)

    /** 格式槽和正文分别绑定，已展开正文不会再次作为模板扫描。 */
    private fun formatField(
        template: String,
        slot: String,
        content: MacroValue,
        values: Map<String, String>,
        appendIfMissing: Boolean = false,
    ): MacroExpansion {
        val raw = when (content) {
            is MacroValue.Literal -> content.text
            is MacroValue.Template -> content.text
        }
        if (raw.isBlank()) return MacroExpansion(raw, emptySet())
        val scope = groupMacroScope(values, mOutlets::value)
        val hasSlot = promptMacroExpander.references(template).any { it.name == slot }
        // 未配置包装或正文槽时沿用原有回退规则，字面正文直接返回。
        if (template.isBlank() || (!hasSlot && !appendIfMissing)) {
            return if (content is MacroValue.Template) promptMacroExpander.expand(raw, scope)
            else MacroExpansion(raw, emptySet())
        }
        // 模板正文沿用字段所属作用域，格式槽只覆盖外层包装。
        val value = if (content is MacroValue.Template) content.copy(scope = scope) else content
        val wrapper = if (hasSlot) template else "$template\n{{$slot}}"
        return promptMacroExpander.expand(wrapper, scope.withValues(mapOf(slot to value)))
    }

    /** 世界书正文已在预算前求值，格式包装仅解析外层模板。 */
    private fun formatWorldInfo(entry: LorebookEntry): MacroExpansion {
        return formatField(
            mPreferences.worldInfoFormat, "0", MacroValue.Literal(entry.content),
            mMacros.forLorebook(entry.lorebookId),
        )
    }

    /** 读取 Prompt 后处理模式。 */
    private fun readPostProcessingMode(provider: LLMProvider): PromptPostProcessingMode {
        return PromptPostProcessingMode.fromOrdinal(provider.promptPostProcessingMode)
    }

    /** 读取摘要注入位置。 */
    private fun readSummaryInjectionPosition(): SummaryInjectionPosition {
        return SummaryInjectionPosition.fromPersistedValue(mPreferences.summaryInjectionPosition)
    }

    /** 读取摘要注入深度。 */
    private fun readSummaryInjectionDepth(): Int {
        return mPreferences.summaryInjectionDepth.coerceAtLeast(0)
    }

    /** 读取摘要注入角色。 */
    private fun readSummaryInjectionRole(): SummaryInjectionRole {
        return SummaryInjectionRole.fromPersistedValue(mPreferences.summaryInjectionRole)
    }

    /** 构建群聊摘要消息草稿。 */
    private fun summaryDraft(context: GroupChatPromptContext): PromptMessageDraft? {
        if (context.summary.isBlank()) return null
        val content = formatField(
            mPreferences.summaryInjectionTemplate,
            "summary",
            MacroValue.Literal(context.summary),
            mSpeakerMacros,
            appendIfMissing = true,
        )
        return templateDraft(
            content = content,
            role = readSummaryInjectionRole().toMessageRole(),
            source = PromptSource(PromptSourceKind.Summary),
            retentionPriority = PRIORITY_ESSENTIAL,
            canDrop = false,
        )
    }

    /**
     * 构建 Continue 或 Impersonate 模式位于请求末尾的唯一任务提示。
     *
     * 两种特殊模式统一使用 user 角色，使所有模型服务都接收到明确且位于末尾的生成目标。
     */
    private fun buildGenerationControlDraft(context: GroupChatPromptContext): PromptMessageDraft? {
        // 保留角色卡和世界背景，末尾只保留摘要这一生成目标。
        context.summaryInstruction?.let { instruction ->
            return requiredUser(
                content = render(instruction, mSpeakerMacros),
                sourceKind = PromptSourceKind.SummaryTask,
            )
        }
        // 历史输出以群聊身份约束收尾，输入框输出在其后追加独立的续写任务。
        val (template, sourceKind) = when (context.generationMode) {
            GroupChatGenerationMode.Continue -> mPreferences.continueNudgePrompt to PromptSourceKind.ContinueNudge
            GroupChatGenerationMode.Impersonate -> mPreferences.groupImpersonationPrompt to PromptSourceKind.ImpersonationNudge
            GroupChatGenerationMode.Normal,
            GroupChatGenerationMode.Regenerate -> return null
        }
        val content = render(template, mSpeakerMacros)
        return content.takeIf { it.text.isNotBlank() }?.let { requiredUser(it, sourceKind) }
    }

    /** 群聊输入框输出单独追加续写模板，原文中的宏和空白保持不变。 */
    private fun buildUserWritingDraft(context: GroupChatPromptContext): PromptMessageDraft? {
        val originalDraft = context.userWritingDraft ?: return null
        require(context.generationMode == GroupChatGenerationMode.Impersonate) { "User writing requires impersonation mode" }
        val template = mPreferences.groupUserWritingPrompt
        val sources = mutableSetOf<PromptSource>()
        val draft = buildUserWritingPrompt(template, originalDraft) { part ->
            val result = renderPromptOverride(part, template)
            sources += mOutlets.sources(result.bindings)
            result.text
        } ?: return null
        return draft.copy(sources = draft.sources + sources)
    }

    /** 确保普通群聊在 Group Nudge 关闭时仍由明确的 user 轮次触发当前角色回复。 */
    private fun ensureTerminalCharacterReplyTurn(
        drafts: List<PromptMessageDraft>,
        context: GroupChatPromptContext,
    ): List<PromptMessageDraft> {
        if (context.summaryInstruction != null) return drafts
        if (!context.generationMode.usesCharacterReplyTask()) return drafts
        if (drafts.lastOrNull()?.role == LLMMessageRole.User) return drafts
        return drafts + requiredUser(
            content = render(DEFAULT_CHARACTER_REPLY_NUDGE, mSpeakerMacros),
            sourceKind = PromptSourceKind.GroupNudge,
        )
    }

    /** 创建不可裁剪的系统片段，宏来源随正文进入最终裁剪。 */
    private fun requiredSystem(
        content: MacroExpansion,
        sourceKind: PromptSourceKind,
        detail: String = "",
    ): PromptMessageDraft = templateDraft(
        content = content,
        role = LLMMessageRole.System,
        source = PromptSource(sourceKind, detail),
        retentionPriority = PRIORITY_ESSENTIAL,
        canDrop = false,
    )

    /** 创建不可裁剪的用户任务片段。 */
    private fun requiredUser(
        content: MacroExpansion,
        sourceKind: PromptSourceKind,
    ): PromptMessageDraft = templateDraft(
        content = content,
        role = LLMMessageRole.User,
        source = PromptSource(sourceKind),
        retentionPriority = PRIORITY_ESSENTIAL,
        canDrop = false,
    )

    /** 世界书优先于普通历史保留，条目自身的来源与格式中的插槽来源共同追踪。 */
    private fun prioritizedWorldInfo(
        content: MacroExpansion,
        source: PromptSource,
        canDrop: Boolean,
    ): PromptMessageDraft = templateDraft(
        content = content,
        role = LLMMessageRole.System,
        source = source,
        retentionPriority = PRIORITY_ESSENTIAL,
        canDrop = canDrop,
    )

    /** 创建可选的系统片段。 */
    private fun optionalSystem(
        content: MacroExpansion,
        source: PromptSource,
        retentionPriority: Int,
    ): PromptMessageDraft = templateDraft(
        content = content,
        role = LLMMessageRole.System,
        source = source,
        retentionPriority = retentionPriority,
        canDrop = true,
    )

    /** 将统一展开结果和业务来源组成最终草稿。 */
    private fun templateDraft(
        content: MacroExpansion,
        role: LLMMessageRole,
        source: PromptSource,
        retentionPriority: Int,
        canDrop: Boolean,
    ): PromptMessageDraft = PromptMessageDraft(
        role = role,
        content = content.text,
        source = source,
        retentionPriority = retentionPriority,
        canDrop = canDrop,
        sources = listOf(source) + mOutlets.sources(content.bindings),
    )

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
