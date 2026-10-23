package me.kafuuneko.rpclient.libs.regex

/**
 * 聊天消息的 Regex 流水线。
 *
 * - 统一用户输入、AI 输出和流式展示阶段的 placement 顺序；
 * - 保留单聊与群聊各自提供的脚本快照和宏上下文；
 * - 将 Source 持久化处理与 Markdown 临时展示处理分开，避免 ViewModel 重复编排。
 */
class RegexMessageProcessor(
    private val mRuntime: RegexScriptRuntime
) {
    /**
     * 按 SlashCommand 后 UserInput 的顺序处理用户文本。
     *
     * @param input 原始用户文本
     * @param scripts 当前生成或页面快照中的脚本
     * @param macros 当前会话可用的宏值
     * @param isEdit 是否正在编辑已有消息
     * @return 经过 Source 正则处理的文本
     */
    fun applyUserInput(
        input: String,
        scripts: List<ScopedRegexScript>,
        macros: Map<String, String>,
        isEdit: Boolean = false,
    ): String {
        // 指令输入先展开 SlashCommand，普通文本直接进入 UserInput。
        val slashProcessed = if (input.startsWith('/')) {
            mRuntime.execute(
                input = input,
                scripts = scripts,
                placement = RegexPlacement.SlashCommand,
                mode = RegexExecutionMode.Source,
                macros = macros,
                isEdit = isEdit,
            ).text
        } else {
            input
        }
        // 用户输入的最终结果进入 Source 阶段，随后才允许持久化。
        return mRuntime.execute(
            input = slashProcessed,
            scripts = scripts,
            placement = RegexPlacement.UserInput,
            mode = RegexExecutionMode.Source,
            macros = macros,
            isEdit = isEdit,
        ).text
    }

    /**
     * 处理 AI 输出，并分别执行正文和 `<think>` 推理块的 Source 脚本。
     *
     * @param input 原始 AI 输出
     * @param scripts 当前生成或页面快照中的脚本
     * @param macros 当前会话可用的宏值
     * @param isEdit 是否正在编辑已有消息
     * @return 经过 Source 正则处理的文本
     */
    fun applyAiResponse(
        input: String,
        scripts: List<ScopedRegexScript>,
        macros: Map<String, String>,
        isEdit: Boolean = false,
    ): String {
        return mRuntime.executeAiMessage(
            input = input,
            scripts = scripts,
            mode = RegexExecutionMode.Source,
            macros = macros,
            isEdit = isEdit,
        ).text
    }

    /** 对用户或角色历史消息执行 Prompt 阶段 Regex，并保留诊断结果。 */
    fun applyPrompt(
        input: String,
        source: RegexMessageSource,
        scripts: List<ScopedRegexScript>,
        macros: Map<String, String>,
        depth: Int? = null,
    ): RegexExecutionResult {
        // 用户正文使用 UserInput，角色正文使用 AiResponse；两者都共享 Prompt 阶段和深度约束。
        return when (source) {
            RegexMessageSource.User -> mRuntime.execute(
                input = input,
                scripts = scripts,
                placement = RegexPlacement.UserInput,
                mode = RegexExecutionMode.Prompt,
                macros = macros,
                depth = depth,
            )
            RegexMessageSource.Character -> mRuntime.executeAiMessage(
                input = input,
                scripts = scripts,
                mode = RegexExecutionMode.Prompt,
                macros = macros,
                depth = depth,
            )
        }
    }

    /** 对世界书条目执行 WorldInfo placement 的 Prompt 阶段 Regex。 */
    fun applyWorldInfo(
        input: String,
        scripts: List<ScopedRegexScript>,
        macros: Map<String, String>,
    ): RegexExecutionResult {
        return mRuntime.execute(
            input = input,
            scripts = scripts,
            placement = RegexPlacement.WorldInfo,
            mode = RegexExecutionMode.Prompt,
            macros = macros,
        )
    }

    /**
     * 根据生成目标选择用户输入或 AI 输出的 Source 流水线。
     *
     * @param input 原始生成内容
     * @param source 生成内容的逻辑来源
     * @param scripts 当前生成快照中的脚本
     * @param macros 当前生成快照中的宏值
     * @param isEdit 是否正在编辑已有消息
     * @return 经过 Source 正则处理的文本
     */
    fun applyGenerated(
        input: String,
        source: RegexMessageSource,
        scripts: List<ScopedRegexScript>,
        macros: Map<String, String>,
        isEdit: Boolean = false,
    ): String {
        return when (source) {
            RegexMessageSource.User -> applyUserInput(input, scripts, macros, isEdit)
            RegexMessageSource.Character -> applyAiResponse(input, scripts, macros, isEdit)
        }
    }

    /**
     * 对流式或已保存消息执行只供界面展示的 Markdown Regex。
     *
     * @param input 原始消息内容
     * @param source 消息的逻辑来源
     * @param scripts 当前页面快照中的脚本
     * @param macros 当前页面快照中的宏值
     * @param depth 消息在历史中的相对深度
     * @return 经过临时展示正则处理的文本
     */
    fun applyDisplay(
        input: String,
        source: RegexMessageSource,
        scripts: List<ScopedRegexScript>,
        macros: Map<String, String>,
        depth: Int? = null,
    ): String {
        // 用户和角色消息使用不同正文 placement，但共享同一套推理块处理。
        val bodyPlacement = when (source) {
            RegexMessageSource.User -> RegexPlacement.UserInput
            RegexMessageSource.Character -> RegexPlacement.AiResponse
        }
        return mRuntime.executeDisplayMessage(
            input = input,
            scripts = scripts,
            macros = macros,
            depth = depth,
            bodyPlacement = bodyPlacement,
        ).text
    }
}

/** 聊天消息进入 Regex 流水线时的逻辑来源。 */
enum class RegexMessageSource {
    User,

    Character
}
