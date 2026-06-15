package me.kafuuneko.rpclient.libs

import com.chibatching.kotpref.KotprefModel
import me.kafuuneko.rpclient.libs.prompt.SummaryInjectionPosition

/**
 * 应用级持久偏好模型。
 *
 * 这里只存放跨会话的全局设置和 Prompt 模板；角色、聊天及世界书等领域数据由 Room 管理。
 */
object AppModel : KotprefModel() {
    // 应用联系邮箱。
    const val EMAIL = "kafuuneko@gmail.com"

    // 仓库地址。
    const val GITHUB_REPO = "https://github.com/KafuuNeko/RPClient"

    // 默认主提示词，作为普通对话生成时的全局系统指令。
    const val DEFAULT_MAIN_PROMPT = """
Write {{char}}'s next reply in a fictional chat between {{char}} and {{user}}.
Write one reply only. Do not decide what {{user}} says or does.
"""

    // 默认摘要提示词，用于把未摘要聊天历史压缩为会话记忆。
    const val DEFAULT_SUMMARIZE_PROMPT = """
Please summarize the following chat history into concise story memory.
Rules:
- Do not continue roleplay.
- Do not generate a new reply for {{char}} or {{user}}.
- Only summarize what already happened in the chat.
- Preserve important facts, relationship changes, promises, injuries, locations, goals, unresolved conflicts and current scene state.
- Keep it within {{words}} words.
"""

    // 默认总结记忆包装模板，避免模型把剧情记忆与角色设定混为一谈。
    const val DEFAULT_SUMMARY_INJECTION_TEMPLATE = """
Story memory:
{{summary}}
"""

    // 默认历史后指令，追加在聊天历史之后。
    const val DEFAULT_POST_HISTORY_INSTRUCTIONS = ""

    // 默认辅助提示词，随角色定义放在聊天历史之前。
    const val DEFAULT_AUXILIARY_PROMPT = ""

    // 默认扮演用户提示词，用于从用户视角生成下一条消息。
    const val DEFAULT_IMPERSONATION_PROMPT = "[Write your next reply from the point of view of {{user}}. Do not write as {{char}} or system.]"

    // 默认新聊天边界提示词，用于区分设定区和真实聊天历史。
    const val DEFAULT_NEW_CHAT_PROMPT = "[Start a new Chat]"

    // 默认示例聊天边界提示词，用于标记示例对话块。
    const val DEFAULT_NEW_EXAMPLE_CHAT_PROMPT = "[Example Chat]"

    // 默认续写提示词，用于继续上一条助手回复。
    const val DEFAULT_CONTINUE_NUDGE_PROMPT = "[Continue your last message without repeating its original content.]"

    // 默认空消息替换文本；为空时输入框空白不会发送。
    const val DEFAULT_REPLACE_EMPTY_MESSAGE_PROMPT = ""

    // 默认世界书条目格式模板，{0} 会替换为世界书内容。
    const val DEFAULT_WORLD_INFO_FORMAT = "{0}"

    // 默认场景格式模板，{{scenario}} 会替换为角色场景。
    const val DEFAULT_SCENARIO_FORMAT = "{{scenario}}"

    // 默认性格格式模板，{{personality}} 会替换为角色性格。
    const val DEFAULT_PERSONALITY_FORMAT = "{{personality}}"

    // 群聊生成尾部提示词，约束模型只输出当前角色的回复。
    const val DEFAULT_GROUP_NUDGE_PROMPT = """
Write only {{char}}'s next reply.
Stay in character. Do not speak for {{user}} or other group members.
Do not prefix the reply with a speaker name.
"""

    // 已有消息的群聊开始提示词，用于明确本轮参与成员。
    const val DEFAULT_NEW_GROUP_CHAT_PROMPT = "[Start a new group chat with {{group}}]"

    // 群聊摘要提示词，保留事件归属、关系状态和未解决事项。
    const val DEFAULT_GROUP_SUMMARIZE_PROMPT = """
Summarize the following group chat into concise story memory.
You are a memory summarizer, not a roleplay participant.
Rules:
- Only summarize events actually stated or confirmed in the chat.
- Preserve who said or did each important thing.
- Preserve relationships, promises, injuries, locations, goals, unresolved conflicts, current scene state and character-specific knowledge.
- Mark plans, suspicions and hypotheses as unconfirmed.
- Do not add character-card lore, world info or writing instructions as events.
- Keep it within {{words}} words.
"""

    // 当前选中的模型供应商 ID。
    var currentLLMProvider by longPref()

    // 主提示词（Main Prompt），注入每次普通对话生成的系统区。
    var mainPrompt by stringPref(default = DEFAULT_MAIN_PROMPT)

    // 摘要提示词（Summarize Prompt），用于自动或手动总结聊天历史。
    var summarizePrompt by stringPref(default = DEFAULT_SUMMARIZE_PROMPT)

    // 全局历史后指令，追加在聊天历史之后；角色卡可通过 {{original}} 覆盖或继承该内容。
    var postHistoryInstructions by stringPref(default = DEFAULT_POST_HISTORY_INSTRUCTIONS)

    // 辅助提示词，随角色定义和世界书固定区一起放在聊天历史之前。
    var auxiliaryPrompt by stringPref(default = DEFAULT_AUXILIARY_PROMPT)

    // 扮演用户提示词，生成用户视角消息时作为尾部系统指令。
    var impersonationPrompt by stringPref(default = DEFAULT_IMPERSONATION_PROMPT)

    // 新聊天边界提示词，插入在设定区和真实聊天历史之间。
    var newChatPrompt by stringPref(default = DEFAULT_NEW_CHAT_PROMPT)

    // 示例聊天边界提示词，插入在每个示例对话块之前。
    var newExampleChatPrompt by stringPref(default = DEFAULT_NEW_EXAMPLE_CHAT_PROMPT)

    // 续写提示词，继续最新助手回复时作为尾部系统指令。
    var continueNudgePrompt by stringPref(default = DEFAULT_CONTINUE_NUDGE_PROMPT)

    // 空消息替换文本；输入框为空时若该字段非空，则使用它作为用户消息发送。
    var replaceEmptyMessagePrompt by stringPref(default = DEFAULT_REPLACE_EMPTY_MESSAGE_PROMPT)

    // 世界书格式模板，使用 {0} 包装已激活的世界书条目。
    var worldInfoFormat by stringPref(default = DEFAULT_WORLD_INFO_FORMAT)

    // 场景格式模板，使用 {{scenario}} 包装角色场景文本。
    var scenarioFormat by stringPref(default = DEFAULT_SCENARIO_FORMAT)

    // 性格格式模板，使用 {{personality}} 包装角色性格文本。
    var personalityFormat by stringPref(default = DEFAULT_PERSONALITY_FORMAT)

    // 全局群聊生成尾部提示词，可由具体群聊会话覆盖。
    var groupNudgePrompt by stringPref(default = DEFAULT_GROUP_NUDGE_PROMPT)

    // 全局群聊开始提示词，可由具体群聊会话覆盖。
    var newGroupChatPrompt by stringPref(default = DEFAULT_NEW_GROUP_CHAT_PROMPT)

    // 群聊专用摘要提示词。
    var groupSummarizePrompt by stringPref(default = DEFAULT_GROUP_SUMMARIZE_PROMPT)

    // 是否启用流式响应。
    var streamEnabled by booleanPref(default = true)

    // Prompt 宏中的用户名称，对应 {{user}}。
    var userName by stringPref(default = "You")

    // 用户人格描述，对应 {{persona}} 或 persona description。
    var userDescription by stringPref(default = "")

    // 摘要目标字数限制，对应摘要提示词中的 {{words}}。
    var summaryWordsLimit by intPref(default = 500)

    // 是否启用自动摘要。
    var autoSummaryEnabled by booleanPref(default = true)

    // 自动摘要触发消息数，未摘要消息达到该数量后触发。
    var summaryTriggerMessageCount by intPref(default = 20)

    // 单次摘要最多纳入的未摘要消息数；0 表示由上下文预算自动裁剪。
    var summaryMaxMessagesPerRequest by intPref(default = 0)

    // 摘要请求的最大输出 token 数。
    var summaryResponseTokens by intPref(default = 800)

    // 总结记忆注入常规聊天 Prompt 时使用的包装模板。
    var summaryInjectionTemplate by stringPref(default = DEFAULT_SUMMARY_INJECTION_TEMPLATE)

    // 摘要注入位置使用 SummaryInjectionPosition.persistedValue 持久化。
    var summaryInjectionPosition by intPref(
        default = SummaryInjectionPosition.default.persistedValue
    )

    // 摘要位于聊天内时，从聊天末尾向前计算的插入深度。
    var summaryInjectionDepth by intPref(default = 2)

    // 摘要位于聊天内时使用的消息角色，对应 SummaryInjectionRole.persistedValue。
    var summaryInjectionRole by intPref(default = 0)

    // 世界书占 prompt 预算的百分比。
    var worldInfoBudgetPercent by intPref(default = 25)

    // 是否把已保存消息中的 <think>...</think> 思考块继续纳入后续上下文。
    var includeThinkInContext by booleanPref(default = false)

    // 是否启用调试模式；开启后记录原始 LLM 请求和响应 JSON。
    var debugModeEnabled by booleanPref(default = false)

    // 全局 Regex 脚本，使用 SillyTavern RegexScriptData 数组格式保存。
    var globalRegexScriptsJson by stringPref(default = "[]")

    // 当前 Prompt 预设携带的 Regex 脚本。
    var presetRegexScriptsJson by stringPref(default = "[]")

    // 预设脚本必须由用户显式授权后才进入执行管线。
    var presetRegexScriptsAuthorized by booleanPref(default = false)

    // 已授权执行内嵌 Regex 脚本的角色 ID 列表。
    var authorizedCharacterRegexIdsJson by stringPref(default = "[]")
}
