package me.kafuuneko.rpclient.libs.regex

/** Regex 脚本的来源作用域；执行时按全局、预设、角色卡的顺序依次应用。 */
enum class RegexScriptScope {
    Global,
    Preset,
    Character
}

/**
 * SillyTavern Regex placement 编号。
 *
 * [MarkdownDisplay] 是上游保留的旧版位置；现代显示阶段通常仍使用
 * [UserInput] 或 [AiResponse]，并通过 `markdownOnly` 区分临时显示脚本。
 */
enum class RegexPlacement(val value: Int) {
    MarkdownDisplay(0),
    UserInput(1),
    AiResponse(2),
    SlashCommand(3),
    WorldInfo(5),
    Reasoning(6);

    companion object {
        /** 将角色卡中的数值 placement 转为已知枚举，未知值返回 null 以便原样往返。 */
        fun fromValue(value: Int): RegexPlacement? = entries.firstOrNull { it.value == value }
    }
}

/** Find Regex 中宏展开的方式，与 SillyTavern `substituteRegex` 字段一致。 */
enum class RegexFindMacroMode(val value: Int) {
    Disabled(0),
    Raw(1),
    Escaped(2);

    companion object {
        /** 未知模式按禁用宏展开处理，避免导入数据意外扩大匹配范围。 */
        fun fromValue(value: Int): RegexFindMacroMode =
            entries.firstOrNull { it.value == value } ?: Disabled
    }
}

/** 脚本执行阶段；只有 Source 阶段的结果允许写回消息或业务数据。 */
enum class RegexExecutionMode {
    Source,
    Markdown,
    Prompt
}

/**
 * 可持久化的 Regex 脚本领域模型。
 *
 * 字段名称保持 SillyTavern `RegexScriptData` 格式，便于角色卡和 JSON 文件无损往返。
 * [rawJson] 保存当前版本尚不认识的扩展字段，导出时会在其基础上覆盖已知字段。
 */
data class RegexScript(
    val id: String,
    val scriptName: String,
    val findRegex: String,
    val replaceString: String,
    val trimStrings: List<String> = emptyList(),
    val placement: List<Int> = emptyList(),
    val disabled: Boolean = false,
    val markdownOnly: Boolean = false,
    val promptOnly: Boolean = false,
    val runOnEdit: Boolean = false,
    val substituteRegex: Int = RegexFindMacroMode.Disabled.value,
    val minDepth: Int? = null,
    val maxDepth: Int? = null,
    val rawJson: String = "{}"
) {
    /** 判断脚本是否声明支持指定执行位置。 */
    fun supports(placement: RegexPlacement): Boolean = placement.value in this.placement
}

/**
 * 带来源信息的运行时脚本。
 *
 * [order] 只在同一作用域内排序；[ownerId] 与 [ownerName] 用于授权、诊断和 Inspector 展示。
 */
data class ScopedRegexScript(
    val script: RegexScript,
    val scope: RegexScriptScope,
    val ownerId: String = "",
    val ownerName: String = "",
    val order: Int = 0
)

/** 单次 Regex 执行所需的阶段、深度、编辑状态和宏上下文。 */
data class RegexExecutionContext(
    val placement: RegexPlacement,
    val mode: RegexExecutionMode = RegexExecutionMode.Source,
    val isEdit: Boolean = false,
    val depth: Int? = null,
    val macros: Map<String, String> = emptyMap()
)

/** 一条实际命中脚本的诊断记录。 */
data class RegexExecutionHit(
    val scriptId: String,
    val scriptName: String,
    val scope: RegexScriptScope,
    val ownerName: String,
    val placement: RegexPlacement,
    val mode: RegexExecutionMode,
    val changed: Boolean
) {
    /** Source 阶段会持久化结果；Markdown 与 Prompt 阶段仅对当前展示或请求临时生效。 */
    val persisted: Boolean
        get() = mode == RegexExecutionMode.Source
}

/** 无效脚本或执行异常的隔离记录；错误不会阻断后续脚本。 */
data class RegexExecutionError(
    val scriptId: String,
    val scriptName: String,
    val message: String
)

/** 一次脚本链执行后的文本、命中信息和隔离错误。 */
data class RegexExecutionResult(
    val text: String,
    val hits: List<RegexExecutionHit> = emptyList(),
    val errors: List<RegexExecutionError> = emptyList()
)
