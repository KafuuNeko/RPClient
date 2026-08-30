package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehaviorProvider
import me.kafuuneko.rpclient.libs.prompt.model.PromptBuildContext
import me.kafuuneko.rpclient.libs.prompt.model.PromptGenerationMode
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspection
import me.kafuuneko.rpclient.libs.prompt.model.PromptMessageDraft
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.model.PromptRetentionPolicy
import me.kafuuneko.rpclient.libs.prompt.model.PromptSource
import me.kafuuneko.rpclient.libs.prompt.model.PromptSourceKind
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionPosition
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionRole
import me.kafuuneko.rpclient.libs.prompt.model.usesCharacterReplyTask
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.regex.RegexExecutionError
import me.kafuuneko.rpclient.libs.regex.RegexExecutionHit
import me.kafuuneko.rpclient.libs.regex.RegexMessageProcessor
import me.kafuuneko.rpclient.libs.regex.RegexMessageSource
import me.kafuuneko.rpclient.libs.regex.RegexScriptRuntime
import me.kafuuneko.rpclient.utils.stripThinkBlocks

/**
 * 单角色聊天 Prompt 构建器。
 *
 * 核心架构与职责：
 * - 宏变量展开：支持 {{char}}、{{user}}、{{persona}}、{{scenario}} 等占位符的动态替换。
 * - 世界书（Lorebook）激活与插入：扫描聊天历史与上下文触发词，按设定位置（before/after character、At Depth、AN 等）注入。
 * - 正则表达式管线（Regex Script）：在 Prompt 组装阶段对输入、AI 历史与世界书条目执行预处理。
 * - 多分区上下文组装：按 SillyTavern 思想组装固定系统区、示例对话区、深度注入项与聊天历史。
 * - 上下文预算与协议适配：统一交由 [PromptRequestFinalizer] 进行 Token 预算裁剪与格式后处理。
 */
class ChatPromptBuilder(
    private val mMacroResolver: PromptMacroResolver,
    private val mHistoryBuilder: FormattedHistoryBuilder,
    private val mWorldBookActivator: WorldBookActivator,
    private val mRegexRuntime: RegexScriptRuntime = RegexScriptRuntime(
        me.kafuuneko.rpclient.libs.regex.RegexScriptEngine()
    ),
    private val mRequestFinalizer: PromptRequestFinalizer = PromptRequestFinalizer(),
    private val mExampleDialogueBehaviorProvider: ExampleDialogueBehaviorProvider =
        ExampleDialogueBehaviorProvider { ExampleDialogueBehavior.default },
    private val mRegexProcessor: RegexMessageProcessor = RegexMessageProcessor(mRegexRuntime)
) {
    /**
     * 构建最终提交给模型的请求。
     *
     * 入口保留纯请求返回值，供旧调用方使用；需要保存世界书 sticky/cooldown 状态的调用方
     * 应使用 [buildWithMetadata]。
     *
     * @param context 聊天 Prompt 构建上下文
     * @return 组装完成的大模型生成请求体
     */
    fun build(context: PromptBuildContext): LLMGenerationRequest {
        return buildWithMetadata(context).request
    }

    /**
     * 按 SillyTavern 的 Prompt 分区思想组装真实上下文并生成元数据：
     * - 固定 system 区：主提示词、角色定义、世界书 before/after character 条目；
     * - 示例对话区：按 `<START>` 切块，作为可裁剪的示例消息块；
     * - 聊天历史区：保留最近消息，并按 depth 插入 userNote、Character's Note、世界书条目；
     * - 尾部指令区：包含 post-history instructions 与角色回复触发引导词。
     *
     * @param context 聊天 Prompt 构建上下文
     * @return 包含生成请求、世界书下阶段状态与调试元数据的构建结果
     */
    fun buildWithMetadata(context: PromptBuildContext): PromptBuildResult {
        val exampleBehavior = mExampleDialogueBehaviorProvider.current()
        // 计算可用 Prompt Token 预算（总上下文扣除最大响应输出量）
        val maxPromptTokens = (context.maxContextTokens - context.maxResponseTokens).coerceAtLeast(0)
        // 解析世界书全局 Token 预算上限
        val worldBudget = resolveWorldInfoBudget(
            promptTokenBudget = maxPromptTokens,
            contextPercent = readWorldInfoBudgetPercent(),
            tokenBudgetCap = readWorldInfoBudgetCap()
        )
        val tokenizer = mRequestFinalizer.tokenizerFor(context.provider)
        val regexHits = mutableListOf<RegexExecutionHit>()
        val regexErrors = mutableListOf<RegexExecutionError>()
        // 构造供 Regex 脚本运行环境使用的宏变量映射字典
        val regexMacros = RegexScriptRuntime.macros(
            userName = context.userName,
            characterName = context.character.name,
            userDescription = context.userDescription,
            scenario = context.character.scenario
        )
        // 扫描上下文并激活匹配的世界书条目
        val rawWorldInfo = mWorldBookActivator.activateStructured(context)
        // 对激活的世界书条目内容执行 Prompt 阶段 Regex 替换
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
        // 根据 Token 预算筛选保留的世界书条目（溢出条目被标记为裁剪）
        val worldSelection = fitWorldInfoToBudget(
            result = activatedWorldInfo,
            globalTokenBudget = worldBudget,
            lorebooks = context.candidateLorebooks,
            tokenizer = tokenizer
        )
        val worldInfo = worldSelection.result
        // 将 outlet 位置的世界书条目按 order 降序聚合为占位字典
        val outlets = worldInfo.outletEntries.mapValues { (_, entries) ->
            entries.sortedByDescending { it.order }.joinToString("\n") { it.content }
        }
        // 构建固定系统消息区段与示例对话片段
        val fixedMessages = buildFixedMessages(context, worldInfo)
        val inChatPieces = buildInChatPieces(context, worldInfo)
        val examplePieces = buildExamplePieces(context, worldInfo, exampleBehavior)
        // 清洗历史消息推理块，并执行用户输入/AI 输出的 Prompt 阶段 Regex 处理
        val historyMessages = context.messages.sanitizeThinkBlocks().mapIndexed { index, message ->
            val depth = context.messages.lastIndex - index
            val result = when (message.source) {
                ChatMessage.Source.User -> mRegexProcessor.applyPrompt(
                    input = message.content,
                    source = RegexMessageSource.User,
                    scripts = context.regexScripts,
                    macros = regexMacros,
                    depth = depth
                )
                ChatMessage.Source.Char -> mRegexProcessor.applyPrompt(
                    input = message.content,
                    source = RegexMessageSource.Character,
                    scripts = context.regexScripts,
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
        // 组装格式化历史文本供 {{history}} 宏使用
        val historyText = mHistoryBuilder.build(historyMessages, context.userName, context.character.name)
        // 组装合并了 At Depth 注入项的聊天历史草稿列表
        val chatMessages = buildChatMessages(
            historyMessages,
            context,
            historyText,
            inChatPieces,
            outlets
        ).toMutableList()
        // 若处于 Continue 续写模式，提取最后一条 AI 回复以便后续单独处理
        val continueTarget = if (context.generationMode == PromptGenerationMode.Continue) {
            val targetIndex = chatMessages.indexOfLast {
                it.source.kind == PromptSourceKind.ChatHistory &&
                    it.role == LLMMessageRole.Assistant
            }
            if (targetIndex >= 0) chatMessages.removeAt(targetIndex) else null
        } else {
            null
        }

        // 按分区顺序串联所有消息草稿
        val rawDrafts = buildList {
            // 历史前固定消息
            fixedMessages.beforeHistory.forEach {
                add(it.resolve(context, historyText, outlets))
            }
            // 示例对话块
            examplePieces.forEach {
                add(it.resolve(context, historyText, outlets))
            }
            // 新对话分隔提示词
            buildNewChatPiece(context, historyText, outlets)?.let(::add)
            // 聊天历史与深度注入项
            addAll(chatMessages)
            // 历史后固定指令（Post-history instructions）
            fixedMessages.afterHistory.forEach {
                add(it.resolve(context, historyText, outlets))
            }
            // 续写目标消息（若有）
            continueTarget?.let(::add)
            // 尾部任务控制指令（如 Continue/Impersonate 提示）
            buildGenerationControlPiece(context)?.let {
                add(it.resolve(context, historyText, outlets))
            }
        }
        // 若生成草稿为空，使用兜底提示词
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
        // 确保末尾轮次符合模型角色的协议要求（如以 user 轮次收尾触发回复）
        val drafts = ensureTerminalCharacterReplyTurn(
            drafts = fallbackDrafts,
            context = context,
            history = historyText,
            outlets = outlets
        )
        // 调用 Finalizer 执行协议后处理、Token 预算裁剪与占位符替换
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
        // 组装检查器元数据
        val inspection = finalized.inspection.copy(
            regexExecutions = regexHits,
            regexErrors = regexErrors
        )
        val selectedWorldInfoIds = worldInfo.activatedEntries.map { it.id }.toSet()
        // 计算世界书时序推进后的下一状态
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

    /**
     * 构建聊天历史外侧的固定 Prompt 区段。
     *
     * 角色回复专用的主提示和 PHI 不进入 Continue/Impersonate，避免特殊任务与“生成下一条
     * 角色回复”的约束同时出现。
     *
     * @param context 聊天构建上下文
     * @param worldInfo 激活的世界书集合
     * @return 包含 beforeHistory 与 afterHistory 的分区对象
     */
    private fun buildFixedMessages(
        context: PromptBuildContext,
        worldInfo: WorldBookActivationResult
    ): PromptSections {
        val beforeHistory = mutableListOf<PromptPiece>()
        val summaryPosition = readSummaryInjectionPosition()
        // 摘要注入：位于主提示词之前
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
        // 摘要注入：位于主提示词之后
        if (summaryPosition == SummaryInjectionPosition.AfterMain) {
            buildSummaryPiece(context)?.let { beforeHistory += it }
        }
        // 插入 beforeCharacter 世界书条目
        worldInfo.beforeCharacter.forEach {
            beforeHistory += PromptPiece(
                role = LLMMessageRole.System,
                content = formatWorldInfo(it.content),
                source = PromptSource(PromptSourceKind.WorldInfo, it.name, it.id),
                retentionPriority = PRIORITY_ESSENTIAL,
                canDrop = !it.ignoreBudget
            )
        }
        // 插入用户设定、角色定义、性格与场景
        beforeHistory += PromptPiece.required(
            LLMMessageRole.System,
            renderUserPersonaTemplate(
                template = readUserPersonaFormat(),
                userName = context.userName,
                userDescription = context.userDescription,
                characterName = context.character.name
            ),
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
        // 插入辅助提示词（低保留优先级）
        beforeHistory += PromptPiece(
            LLMMessageRole.System,
            readAuxiliaryPrompt(),
            PromptSource(PromptSourceKind.AuxiliaryPrompt),
            PRIORITY_AUXILIARY,
            true
        )
        // 插入 afterCharacter 世界书条目
        worldInfo.afterCharacter.forEach {
            beforeHistory += PromptPiece(
                role = LLMMessageRole.System,
                content = formatWorldInfo(it.content),
                source = PromptSource(PromptSourceKind.WorldInfo, it.name, it.id),
                retentionPriority = PRIORITY_ESSENTIAL,
                canDrop = !it.ignoreBudget
            )
        }

        // 组装历史消息之后的固定指令区段
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

    /**
     * 收集按 depth 插入历史内部的摘要、作者注释、角色注释和世界书条目。
     *
     * 这里只保留相对深度和稳定排序键，实际索引必须等当前用户消息加入历史后再计算。
     *
     * @param context 聊天构建上下文
     * @param worldInfo 激活的世界书集合
     * @return 待按深度插入历史的片段列表
     */
    private fun buildInChatPieces(
        context: PromptBuildContext,
        worldInfo: WorldBookActivationResult
    ): List<InChatPromptPiece> {
        val pieces = mutableListOf<InChatPromptPiece>()
        // 注入群聊/单聊内嵌摘要（InChat 模式）
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
        // 注入 AN Top 世界书条目
        worldInfo.anTop.forEachIndexed { index, entry ->
            pieces += InChatPromptPiece(
                role = LLMMessageRole.System,
                content = entry.content,
                source = PromptSource(PromptSourceKind.WorldInfo, entry.name, entry.id),
                retentionPriority = PRIORITY_ESSENTIAL,
                canDrop = !entry.ignoreBudget,
                depth = USER_NOTE_DEPTH,
                order = AN_TOP_ORDER,
                tieBreaker = index.toLong()
            )
        }
        // 注入当前会话的作者注释（User Note）
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
        // 注入 AN Bottom 世界书条目
        worldInfo.anBottom.forEachIndexed { index, entry ->
            pieces += InChatPromptPiece(
                role = LLMMessageRole.System,
                content = entry.content,
                source = PromptSource(PromptSourceKind.WorldInfo, entry.name, entry.id),
                retentionPriority = PRIORITY_ESSENTIAL,
                canDrop = !entry.ignoreBudget,
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
        // 注入 At Depth 泛型深度世界书分组条目
        worldInfo.depthEntries.forEach { group ->
            val sources = group.entries.map {
                PromptSource(PromptSourceKind.WorldInfo, it.name, it.id)
            }
            val first = group.entries.firstOrNull() ?: return@forEach
            pieces += InChatPromptPiece(
                role = group.role,
                content = group.entries.joinToString("\n") { it.content },
                source = sources.first(),
                retentionPriority = PRIORITY_ESSENTIAL,
                canDrop = group.entries.none { it.ignoreBudget },
                depth = group.depth.coerceAtLeast(0),
                order = first.order,
                tieBreaker = first.id,
                sources = sources
            )
        }
        return pieces
    }

    /**
     * 将示例对话和示例位置世界书条目转换为可独立裁剪的消息块。
     *
     * 无法识别发言者格式的块以 system 原文保留，防止第三方角色卡示例在导入后静默丢失。
     *
     * @param context 聊天构建上下文
     * @param worldInfo 激活的世界书集合
     * @param behavior 示例对话保留策略枚举
     * @return 示例对话消息片段列表
     */
    private fun buildExamplePieces(
        context: PromptBuildContext,
        worldInfo: WorldBookActivationResult,
        behavior: ExampleDialogueBehavior
    ): List<PromptPiece> {
        if (behavior == ExampleDialogueBehavior.Disabled) return emptyList()
        val retentionPriority = PromptRetentionPolicy.examplePriority(behavior)
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
                                retentionPriority,
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
                                retentionPriority,
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
                                    retentionPriority,
                                    true
                                )
                            )
                        }
                    }
                }
            }
    }

    /**
     * 构建新聊天标记（New Chat Marker）消息草稿。
     *
     * @param context 聊天构建上下文
     * @param history 历史消息文本
     * @param outlets Outlet 占位替换字典
     * @return 若存在新聊天提示词则返回草稿，否则返回 null
     */
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

    /**
     * 组装真实聊天历史，并把所有 depth 注入项插入最终相对位置。
     *
     * 同一插入点先按世界书 order、再按稳定 tieBreaker 排序；最后一条真实历史不可裁剪，
     * 以维持当前生成任务与用户最近输入的一致性。
     *
     * @param historyMessages 已做正则替换的历史消息列表
     * @param context 聊天构建上下文
     * @param historyText 历史消息拼接字符串
     * @param inChatPieces 待深度注入的片段列表
     * @param outlets Outlet 占位替换字典
     * @return 组装完成的消息草稿列表
     */
    private fun buildChatMessages(
        historyMessages: List<ChatMessage>,
        context: PromptBuildContext,
        historyText: String,
        inChatPieces: List<InChatPromptPiece>,
        outlets: Map<String, String>
    ): List<PromptMessageDraft> {
        val lastHistoryIndex = historyMessages.lastIndex
        // 映射历史消息草稿，除最后一条外均允许在超出预算时被裁剪
        val chatMessages = historyMessages.mapIndexed { index, message ->
            message.toPromptDraft(
                retentionPriority = PromptRetentionPolicy.HISTORY,
                canDrop = index != lastHistoryIndex
            )
        }.toMutableList()
        // 若存在当前用户正在输入的消息，执行正则替换并作为末尾 User 消息加入
        context.currentUserMessage?.takeIf { it.isNotBlank() }?.let {
            val regexResult = mRegexProcessor.applyPrompt(
                input = it,
                source = RegexMessageSource.User,
                scripts = context.regexScripts,
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

        // 计算所有深度注入项在当前消息列表中的目标索引并分组排序
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

        // 将注入项插入聊天历史对应相对位置
        return buildList {
            for (index in 0..chatMessages.size) {
                injections[index]?.let { addAll(it) }
                if (index < chatMessages.size) add(chatMessages[index])
            }
        }
    }

    /**
     * 计算深度注入项在消息列表中的插入下标。
     *
     * depth=0 表示追加到聊天末尾；depth=1 表示插入到最后一条消息之前，以此类推。
     */
    private fun InChatPromptPiece.insertionIndex(chatMessages: List<PromptMessageDraft>): Int {
        return (chatMessages.size - depth).coerceIn(0, chatMessages.size)
    }

    /**
     * 构建位于完整聊天上下文末尾的生成任务提示。
     *
     * Impersonate 与 Continue 使用 user 角色，确保严格要求用户末尾轮次的模型
     * 不会把控制指令提升到开头，也不会因最后一条是 system 而返回空结果。
     *
     * @param context 聊天构建上下文
     * @return 控制指令 PromptPiece，普通模式返回 null
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
     *
     * @param drafts 原始构建消息草稿列表
     * @param context 聊天构建上下文
     * @param history 历史消息文本
     * @param outlets Outlet 占位替换字典
     * @return 确保以合法触发轮次收尾的消息草稿列表
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

    /** 获取兜底提示词的消息角色。 */
    private fun fallbackRole(context: PromptBuildContext): LLMMessageRole {
        return if (context.generationMode.usesCharacterReplyTask()) {
            LLMMessageRole.System
        } else {
            LLMMessageRole.User
        }
    }

    /** 获取兜底提示词的来源类型枚举。 */
    private fun fallbackSourceKind(context: PromptBuildContext): PromptSourceKind {
        return when (context.generationMode) {
            PromptGenerationMode.Normal,
            PromptGenerationMode.Regenerate -> PromptSourceKind.MainPrompt
            PromptGenerationMode.Continue -> PromptSourceKind.ContinueNudge
            PromptGenerationMode.Impersonate -> PromptSourceKind.ImpersonationNudge
        }
    }

    /** 将固定消息片段解析宏变量后转换为消息草稿。 */
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

    /** 将深度注入片段解析宏变量后转换为消息草稿。 */
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

    /** 调用宏解析器执行占位符替换。 */
    private fun resolve(
        text: String,
        context: PromptBuildContext,
        history: String,
        original: String,
        outlets: Map<String, String>
    ): String {
        return mMacroResolver.resolve(text, context, history, original, outlets)
    }

    /** 将数据库聊天消息实体转换为 Prompt 消息草稿对象。 */
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

    /**
     * 过滤历史消息中的 `<think>...</think>` 推理标签块。
     *
     * 已保存的推理块只用于 UI 展示；默认不再带回后续上下文，避免模型复读或继承旧思路。
     */
    private fun List<ChatMessage>.sanitizeThinkBlocks(): List<ChatMessage> {
        if (runCatching { AppModel.includeThinkInContext }.getOrDefault(false)) return this
        return mapNotNull { message ->
            val cleaned = message.content.stripThinkBlocks().trim()
            when {
                cleaned.isBlank() -> null
                cleaned == message.content -> message
                else -> message.copy(content = cleaned)
            }
        }
    }

    /** 读取全局主提示词。 */
    private fun readMainPrompt(): String {
        return runCatching { AppModel.mainPrompt }.getOrDefault(AppModel.DEFAULT_MAIN_PROMPT)
    }

    /** 读取历史后指令（Post-history instructions）。 */
    private fun readPostHistoryInstructions(): String {
        return runCatching { AppModel.postHistoryInstructions }.getOrDefault("")
    }

    /** 读取全局辅助提示词。 */
    private fun readAuxiliaryPrompt(): String {
        return runCatching { AppModel.auxiliaryPrompt }.getOrDefault(AppModel.DEFAULT_AUXILIARY_PROMPT)
    }

    /** 读取扮演用户提示词。 */
    private fun readImpersonationPrompt(): String {
        return runCatching { AppModel.impersonationPrompt }.getOrDefault(AppModel.DEFAULT_IMPERSONATION_PROMPT)
    }

    /** 读取新对话标记提示词。 */
    private fun readNewChatPrompt(): String {
        return runCatching { AppModel.newChatPrompt }.getOrDefault(AppModel.DEFAULT_NEW_CHAT_PROMPT)
    }

    /** 读取示例对话分隔标记提示词。 */
    private fun readNewExampleChatPrompt(): String {
        return runCatching { AppModel.newExampleChatPrompt }.getOrDefault(AppModel.DEFAULT_NEW_EXAMPLE_CHAT_PROMPT)
    }

    /** 读取续写引导提示词。 */
    private fun readContinueNudgePrompt(): String {
        return runCatching { AppModel.continueNudgePrompt }.getOrDefault(AppModel.DEFAULT_CONTINUE_NUDGE_PROMPT)
    }

    /** 获取当前 Provider 配置的 Prompt 后处理模式。 */
    private fun readPostProcessingMode(provider: LLMProvider?): PromptPostProcessingMode {
        val ordinal = provider?.promptPostProcessingMode
            ?: PromptPostProcessingMode.None.ordinal
        return PromptPostProcessingMode.fromOrdinal(ordinal)
    }

    /** 读取摘要注入位置配置。 */
    private fun readSummaryInjectionPosition(): SummaryInjectionPosition {
        return SummaryInjectionPosition.fromPersistedValue(
            runCatching { AppModel.summaryInjectionPosition }
                .getOrDefault(SummaryInjectionPosition.default.persistedValue)
        )
    }

    /** 读取摘要在 InChat 模式下的注入深度。 */
    private fun readSummaryInjectionDepth(): Int {
        return runCatching { AppModel.summaryInjectionDepth }
            .getOrDefault(2)
            .coerceAtLeast(0)
    }

    /** 读取摘要注入使用的消息角色。 */
    private fun readSummaryInjectionRole(): SummaryInjectionRole {
        return SummaryInjectionRole.fromPersistedValue(
            runCatching { AppModel.summaryInjectionRole }.getOrDefault(0)
        )
    }

    /** 根据模板与当前摘要内容构建摘要消息片段。 */
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

    /** 格式化世界书条目内容。 */
    private fun formatWorldInfo(content: String): String {
        return applyFormat(readWorldInfoFormat(), "{0}", content)
    }

    /** 格式化场景描述文本。 */
    private fun formatScenario(content: String): String {
        return applyFormat(readScenarioFormat(), "{{scenario}}", content)
    }

    /** 格式化性格描述文本。 */
    private fun formatPersonality(content: String): String {
        return applyFormat(readPersonalityFormat(), "{{personality}}", content)
    }

    /** 通用模板占位符替换辅助函数。 */
    private fun applyFormat(template: String, marker: String, content: String): String {
        if (content.isBlank()) return content
        if (template.isBlank()) return content
        return if (template.contains(marker)) template.replace(marker, content) else content
    }

    /** 读取世界书条目包装模板。 */
    private fun readWorldInfoFormat(): String {
        return runCatching { AppModel.worldInfoFormat }.getOrDefault(AppModel.DEFAULT_WORLD_INFO_FORMAT)
    }

    /** 读取场景包装模板。 */
    private fun readScenarioFormat(): String {
        return runCatching { AppModel.scenarioFormat }.getOrDefault(AppModel.DEFAULT_SCENARIO_FORMAT)
    }

    /** 读取性格包装模板。 */
    private fun readPersonalityFormat(): String {
        return runCatching { AppModel.personalityFormat }.getOrDefault(AppModel.DEFAULT_PERSONALITY_FORMAT)
    }

    /** 读取统一用户人设包装模板。 */
    private fun readUserPersonaFormat(): String {
        return runCatching { AppModel.userPersonaFormat }.getOrDefault(AppModel.DEFAULT_USER_PERSONA_FORMAT)
    }

    /** 读取世界书占可用 Prompt 预算百分比。 */
    private fun readWorldInfoBudgetPercent(): Int {
        return runCatching { AppModel.worldInfoBudgetPercent }.getOrDefault(DEFAULT_WORLD_INFO_BUDGET_PERCENT)
    }

    /** 读取世界书绝对 Token 预算上限（0 表示不限制）。 */
    private fun readWorldInfoBudgetCap(): Int {
        return runCatching { AppModel.worldInfoBudgetCap }
            .getOrDefault(0)
    }

    /** 读取并解析角色卡特定覆盖或全局的主提示词。 */
    private fun readCharacterMainPrompt(context: PromptBuildContext): String {
        val original = readMainPrompt()
        val override = context.character.systemPrompt.trim()
        val systemPrompt = override.ifBlank { original }
        return mMacroResolver.resolve(systemPrompt, context, original = original)
    }

    /** 读取并解析角色卡特定覆盖或全局的历史后指令。 */
    private fun readCharacterPostHistoryInstructions(context: PromptBuildContext): String {
        val original = readPostHistoryInstructions()
        val override = context.character.postHistoryInstructions.trim()
        val instructions = override.ifBlank { original }
        return mMacroResolver.resolve(instructions, context, original = original)
    }

    /** 包含历史前与历史后固定系统消息的分区容器。 */
    private data class PromptSections(
        /** 插入聊天历史之前的 Prompt 内容列表。 */
        val beforeHistory: List<PromptPiece>,
        /** 插入聊天历史之后的 Prompt 内容列表。 */
        val afterHistory: List<PromptPiece>
    )

    /** 尚未执行最终宏展开的固定消息片段。 */
    private data class PromptPiece(
        /** 当前对象在业务流程中承担的角色。 */
        val role: LLMMessageRole,
        /** 当前对象承载的正文内容。 */
        val content: String,
        /** 产生当前数据的来源。 */
        val source: PromptSource,
        /** Prompt 超出预算时保留当前内容的优先级。 */
        val retentionPriority: Int,
        /** Prompt 超出预算时是否允许移除当前内容。 */
        val canDrop: Boolean,
        /** 宏展开或后处理前保留的原始 Prompt 内容。 */
        val original: String = content
    ) {
        companion object {
            /** 创建不可裁剪的核心必需片段。 */
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

    /** 包含深度排序元数据的历史内部注入片段。 */
    private data class InChatPromptPiece(
        /** 当前对象在业务流程中承担的角色。 */
        val role: LLMMessageRole,
        /** 当前对象承载的正文内容。 */
        val content: String,
        /** 产生当前数据的来源。 */
        val source: PromptSource,
        /** Prompt 超出预算时保留当前内容的优先级。 */
        val retentionPriority: Int,
        /** Prompt 超出预算时是否允许移除当前内容。 */
        val canDrop: Boolean,
        /** 当前内容相对聊天末尾的插入或扫描深度。 */
        val depth: Int,
        /** 当前对象在同类数据中的排序值。 */
        val order: Int,
        /** 业务优先级相同时用于保持稳定顺序的次级排序值。 */
        val tieBreaker: Long,
        /** 当前 Prompt 项合并后保留的原始来源列表。 */
        val sources: List<PromptSource> = listOf(source)
    )

    private companion object {
        /** 世界书全局 Token 预算百分比默认值（占总可用 Prompt 预算的 25%）。 */
        const val DEFAULT_WORLD_INFO_BUDGET_PERCENT = 25
        /** 辅助提示词（Auxiliary Prompt）的上下文保留优先级。 */
        const val PRIORITY_AUXILIARY = 20
        /** 新对话标记提示词（New Chat Marker）的上下文保留优先级。 */
        const val PRIORITY_NEW_CHAT = 30
        /** 用户便签（User Note / AN）的上下文保留优先级。 */
        const val PRIORITY_USER_NOTE = 300
        /** 角色深度提示（Character Note / Depth Prompt）的上下文保留优先级。 */
        const val PRIORITY_CHARACTER_NOTE = 310
        /** 核心关键内容（如预算内世界书、摘要等）的上下文保留优先级。 */
        const val PRIORITY_ESSENTIAL = 1_000
        /** 用户便签（User Note / AN）默认插入的聊天历史深度（倒数第 4 条）。 */
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
        /** 兜底的角色回复触发引导词，当未开启指定 Nudge 时确保 Prompt 末尾由 User 轮次触发角色生成。 */
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

/** 将世界书条目设定的角色 Int 值映射为大模型消息角色枚举。 */
private fun Int.toMessageRole(): LLMMessageRole {
    return when (this) {
        LorebookEntry.ROLE_USER -> LLMMessageRole.User
        LorebookEntry.ROLE_ASSISTANT -> LLMMessageRole.Assistant
        else -> LLMMessageRole.System
    }
}

/** 解析示例对话原始字符串为按 `<START>` 划分的消息块列表。 */
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
