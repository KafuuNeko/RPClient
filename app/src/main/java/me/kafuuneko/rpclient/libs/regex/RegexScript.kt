package me.kafuuneko.rpclient.libs.regex

enum class RegexScriptScope {
    Global,
    Preset,
    Character
}

enum class RegexPlacement(val value: Int) {
    MarkdownDisplay(0),
    UserInput(1),
    AiResponse(2),
    SlashCommand(3),
    WorldInfo(5),
    Reasoning(6);

    companion object {
        fun fromValue(value: Int): RegexPlacement? = entries.firstOrNull { it.value == value }
    }
}

enum class RegexFindMacroMode(val value: Int) {
    Disabled(0),
    Raw(1),
    Escaped(2);

    companion object {
        fun fromValue(value: Int): RegexFindMacroMode =
            entries.firstOrNull { it.value == value } ?: Disabled
    }
}

enum class RegexExecutionMode {
    Source,
    Markdown,
    Prompt
}

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
    fun supports(placement: RegexPlacement): Boolean = placement.value in this.placement
}

data class ScopedRegexScript(
    val script: RegexScript,
    val scope: RegexScriptScope,
    val ownerId: String = "",
    val ownerName: String = "",
    val order: Int = 0
)

data class RegexExecutionContext(
    val placement: RegexPlacement,
    val mode: RegexExecutionMode = RegexExecutionMode.Source,
    val isEdit: Boolean = false,
    val depth: Int? = null,
    val macros: Map<String, String> = emptyMap()
)

data class RegexExecutionHit(
    val scriptId: String,
    val scriptName: String,
    val scope: RegexScriptScope,
    val ownerName: String,
    val placement: RegexPlacement,
    val mode: RegexExecutionMode,
    val changed: Boolean
) {
    val persisted: Boolean
        get() = mode == RegexExecutionMode.Source
}

data class RegexExecutionError(
    val scriptId: String,
    val scriptName: String,
    val message: String
)

data class RegexExecutionResult(
    val text: String,
    val hits: List<RegexExecutionHit> = emptyList(),
    val errors: List<RegexExecutionError> = emptyList()
)
