package me.kafuuneko.rpclient.libs.utils

private val ThinkBlockRegex = Regex("<think>[\\s\\S]*?(</think>|$)", RegexOption.IGNORE_CASE)

fun String?.takeIfNotBlank(): String? {
    return this?.takeIf { it.isNotBlank() }
}

fun String.stripThinkBlocks(): String {
    return replace(ThinkBlockRegex, "").trim()
}

fun String.toPreview(maxLength: Int = 0): String {
    if (maxLength == 0 || length <= maxLength) return this
    return take(maxLength) + "..."
}
