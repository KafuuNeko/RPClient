package me.kafuuneko.rpclient.libs.regex

/** Regex 脚本的来源作用域；执行时按全局、角色卡的顺序依次应用。 */
enum class RegexScriptScope {
    Global,
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
    /** 当前记录或列表项的唯一标识。 */
    val id: String,
    /** 正则脚本的显示名称。 */
    val scriptName: String,
    /** 正则脚本用于查找内容的表达式。 */
    val findRegex: String,
    /** 正则脚本命中后使用的替换模板。 */
    val replaceString: String,
    /** 执行替换前需要从输入中移除的文本片段。 */
    val trimStrings: List<String> = emptyList(),
    /** 世界书内容在 Prompt 中的插入位置。 */
    val placement: List<Int> = emptyList(),
    /** 当前对象或功能是否禁用。 */
    val disabled: Boolean = false,
    /** 脚本是否只在 Markdown 展示阶段执行。 */
    val markdownOnly: Boolean = false,
    /** 脚本是否只在 Prompt 构建阶段执行。 */
    val promptOnly: Boolean = false,
    /** 用户编辑消息时是否仍执行当前脚本。 */
    val runOnEdit: Boolean = false,
    /** 是否先对正则表达式中的宏进行替换。 */
    val substituteRegex: Int = RegexFindMacroMode.Disabled.value,
    /** 脚本允许处理的最小消息深度。 */
    val minDepth: Int? = null,
    /** 脚本允许处理的最大消息深度。 */
    val maxDepth: Int? = null,
    /** 未经业务转换的原始 JSON 文本。 */
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
    /** 当前流程正在处理的正则脚本。 */
    val script: RegexScript,
    /** 正则脚本生效的业务作用域。 */
    val scope: RegexScriptScope,
    /** 拥有当前脚本或授权记录的对象 ID。 */
    val ownerId: String = "",
    /** 拥有当前脚本的角色或预设名称。 */
    val ownerName: String = "",
    /** 当前对象在同类数据中的排序值。 */
    val order: Int = 0
)

/** 单次 Regex 执行所需的阶段、深度、编辑状态和宏上下文。 */
data class RegexExecutionContext(
    /** 世界书内容在 Prompt 中的插入位置。 */
    val placement: RegexPlacement,
    /** 当前流程采用的处理模式。 */
    val mode: RegexExecutionMode = RegexExecutionMode.Source,
    /** 当前正则执行是否由用户编辑既有消息触发。 */
    val isEdit: Boolean = false,
    /** 当前内容相对聊天末尾的插入或扫描深度。 */
    val depth: Int? = null,
    /** 当前正则执行允许展开的宏变量映射。 */
    val macros: Map<String, String> = emptyMap()
)

/** 一条实际命中脚本的诊断记录。 */
data class RegexExecutionHit(
    /** 当前操作关联的正则脚本 ID。 */
    val scriptId: String,
    /** 正则脚本的显示名称。 */
    val scriptName: String,
    /** 正则脚本生效的业务作用域。 */
    val scope: RegexScriptScope,
    /** 拥有当前脚本的角色或预设名称。 */
    val ownerName: String,
    /** 世界书内容在 Prompt 中的插入位置。 */
    val placement: RegexPlacement,
    /** 当前流程采用的处理模式。 */
    val mode: RegexExecutionMode,
    /** 当前对象相较初始值是否发生变化。 */
    val changed: Boolean
) {
    /** Source 阶段会持久化结果；Markdown 与 Prompt 阶段仅对当前展示或请求临时生效。 */
    val persisted: Boolean
        get() = mode == RegexExecutionMode.Source
}

/** 无效脚本或执行异常的隔离记录；错误不会阻断后续脚本。 */
data class RegexExecutionError(
    /** 当前操作关联的正则脚本 ID。 */
    val scriptId: String,
    /** 正则脚本的显示名称。 */
    val scriptName: String,
    /** 需要展示或传递的消息内容。 */
    val message: String
)

/** 一次脚本链执行后的文本、命中信息和隔离错误。 */
data class RegexExecutionResult(
    /** 当前对象承载的文本内容。 */
    val text: String,
    /** 本次执行中成功命中的正则规则列表。 */
    val hits: List<RegexExecutionHit> = emptyList(),
    /** 本次执行中收集的非致命正则错误列表。 */
    val errors: List<RegexExecutionError> = emptyList()
)
