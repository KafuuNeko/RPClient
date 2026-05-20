package me.kafuuneko.rpclient.libs.utils

private val ThinkBlockRegex = Regex("<think>[\\s\\S]*?(</think>|$)", RegexOption.IGNORE_CASE)

fun String?.takeIfNotBlank(): String? {
    return this?.takeIf { it.isNotBlank() }
}

fun String.stripThinkBlocks(): String {
    return replace(ThinkBlockRegex, "").trim()
}
