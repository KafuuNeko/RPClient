package me.kafuuneko.rpclient.libs.prompt

/**
 * 按 SillyTavern 风格匹配非正则关键词，并为不使用空格分词的文字保留子串语义。
 *
 * ASCII 风格的单个关键词可使用完整词边界，避免 `king` 命中 `liking`；包含 CJK 等
 * 非 ASCII 字符的关键词无法可靠依赖空格确定词界，因此回退为子串匹配。
 */
internal fun String.matchesPlainTextKey(
    key: String,
    ignoreCase: Boolean,
    matchWholeWords: Boolean,
): Boolean {
    val normalizedKey = key.trim()
    if (normalizedKey.isEmpty()) return false
    // 若未开启全词匹配、包含空格或包含非 ASCII 字符（如中日韩文字），直接按纯文本子串匹配
    if (
        !matchWholeWords
        || normalizedKey.containsWhitespace()
        || !normalizedKey.supportsWordBoundary()
    ) {
        return contains(other = normalizedKey, ignoreCase = ignoreCase)
    }

    // 对纯 ASCII 单词构造前后无字符/数字/下划线的全词边界正则表达式进行匹配
    val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
    return Regex(
        pattern = "(?<![\\p{L}\\p{N}_])${Regex.escape(normalizedKey)}(?![\\p{L}\\p{N}_])",
        options = options,
    ).containsMatchIn(this)
}

private fun String.containsWhitespace(): Boolean = any(Char::isWhitespace)

/** 仅对可由空格和标点稳定分词的 ASCII 风格关键词启用完整词边界。 */
private fun String.supportsWordBoundary(): Boolean {
    return all { character ->
        character == '-' || character == '_'
            || (character.code < 128 && character.isLetterOrDigit())
    } && any(Char::isLetterOrDigit)
}
