package me.kafuuneko.rpclient.libs

import com.chibatching.kotpref.KotprefModel
import me.kafuuneko.rpclient.libs.defaults.DefaultNames
import me.kafuuneko.rpclient.libs.defaults.normalizedUserName
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionPosition
import me.kafuuneko.rpclient.libs.theme.AppThemeMode

/**
 * 应用级持久偏好模型。
 *
 * 这里只存放跨会话的轻量设置；提示词模板、角色、聊天及世界书等领域数据由 Room 管理。
 */
object AppModel : KotprefModel() {
    // 应用联系邮箱。
    const val EMAIL = "kafuuneko@gmail.com"

    // 仓库地址。
    const val GITHUB_REPO = "https://github.com/KafuuNeko/RPClient"

    /** 普通生成默认最多读取的最近历史消息数。 */
    const val DEFAULT_MAX_PROMPT_HISTORY_MESSAGES = 500

    // 最后一个成功完成的业务升级步骤 versionCode；保留属性名以兼容既有偏好键。
    var lastMigratedVersionCode by intPref(default = 0, commitByDefault = true)

    /** 开启后以主题化 Markdown 预览故事章节；默认保留原文编辑方式。 */
    var storyMarkdownRenderingEnabled by booleanPref(default = false, commitByDefault = true)

    /** 首页最近区域的稳定模式编码，默认显示最近内容。 */
    var homeRecentMode by intPref(
        default = HomeRecentMode.RecentContent.persistedValue,
        commitByDefault = true
    )

    /** 编辑消息时只展示正文，保存时保留原消息的思考链。 */
    var separateThinkWhenEditing by booleanPref(default = true)

    // 未设置时保留扮演用户直接写入聊天记录的行为。
    var impersonationOutput by stringPref(default = "message")

    /** 是否已提示启用角色卡背景；跨角色和应用重启只提示一次。 */
    var characterBackgroundPromptShown by booleanPref(default = false)

    // 最后一个成功清理旧存储的业务升级步骤 versionCode。
    var lastCleanedUpgradeVersionCode by intPref(default = 0, commitByDefault = true)

    /** 当前提示词预设；-1 表示直接使用 PromptType 中的默认值。 */
    var activePromptPresetId by longPref(default = -1L, commitByDefault = true)

    // 当前选中的模型配置 ID。
    var currentLLMProvider by longPref()

    // 对话框独立外观参数，不包含文件引用。
    var dialogAppearance by stringPref(default = "")

    // 配色与明暗模式分开保存，切换配色不改变用户的明暗偏好。
    var themePalette by stringPref(default = "classic")

    var themeMode by stringPref(default = AppThemeMode.FollowSystem.persistedValue)

    // 摘要专用模型配置 ID；0 表示跟随当前全局模型配置。
    var summaryLLMProvider by longPref()

    // 默认模型配置模板是否已完成首次初始化，防止用户删除全部配置后被自动重建。
    var llmDefaultProvidersInitialized by booleanPref(default = false)

    // OpenRouter 会话粘性使用的匿名安装 ID；不会发送原值，只发送按会话哈希后的 ID。
    var llmRoutingInstallationId by stringPref(default = "")

    // 全部图片发送参数以一个值保存，避免并发请求读到混合配置。
    var imageSendSettings by stringPref(default = "auto:2048:1536:1536")

    // 是否启用流式响应。
    var streamEnabled by booleanPref(default = true)

    // Prompt 宏中的用户名称，对应 {{user}}。
    var userName by stringPref(default = DefaultNames.USER)

    /** 始终可安全用于展示、持久化和 Prompt 宏的全局用户名。 */
    val resolvedUserName: String
        get() = userName.normalizedUserName()

    // 用户头像，存储的是 File UUID。
    var userAvatar by stringPref(default = "")

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
    var summaryResponseTokens by intPref(default = 1000)

    // 摘要注入位置使用 SummaryInjectionPosition.persistedValue 持久化。
    var summaryInjectionPosition by intPref(default = SummaryInjectionPosition.default.persistedValue)

    // 摘要位于聊天内时，从聊天末尾向前计算的插入深度。
    var summaryInjectionDepth by intPref(default = 2)

    // 摘要位于聊天内时使用的消息角色，对应 SummaryInjectionRole.persistedValue。
    var summaryInjectionRole by intPref(default = 0)

    // 世界书占输入 Prompt 预算的百分比。
    var worldInfoBudgetPercent by intPref(default = 25)

    // 世界书固定 Token 上限；0 表示仅使用上下文百分比，不额外设上限。
    var worldInfoBudgetCap by intPref(default = 0)

    // 世界书候选条目因独立预算未进入 Prompt 时是否提示用户。
    var worldInfoOverflowAlert by booleanPref(default = true)

    // Prompt 内容因上下文预算被正常裁剪时是否提示用户。
    var contextTrimmingAlert by booleanPref(default = false)

    // 示例对话的全局保留策略，使用 ExampleDialogueBehavior.persistedValue 持久化。
    var exampleDialogueBehavior by intPref(default = ExampleDialogueBehavior.default.persistedValue)

    // 是否把已保存消息中的 <think>...</think> 思考块继续纳入后续上下文。
    var includeThinkInContext by booleanPref(default = false)

    // 普通生成最多读取的最近历史消息数；0 表示不限制并保持旧版行为。
    var maxPromptHistoryMessages by intPref(default = DEFAULT_MAX_PROMPT_HISTORY_MESSAGES)

    // 是否启用调试模式；开启后记录原始 LLM 请求和响应 JSON。
    var debugModeEnabled by booleanPref(default = false)

}
