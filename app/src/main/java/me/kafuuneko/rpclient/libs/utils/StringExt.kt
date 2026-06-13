package me.kafuuneko.rpclient.libs.utils

private val ThinkBlockRegex = Regex("<think>[\\s\\S]*?(</think>|$)", RegexOption.IGNORE_CASE)

/** 仅在字符串非空白时返回自身。 */
fun String?.takeIfNotBlank(): String? {
    return this?.takeIf { it.isNotBlank() }
}

/** 移除保存回复中的 think 块，避免推理内容继续进入后续上下文。 */
fun String.stripThinkBlocks(): String {
    return replace(ThinkBlockRegex, "").trim()
}

/** 生成适合列表展示的预览，并按需截断长度。 */
fun String.toPreview(maxLength: Int = 0): String {
    if (maxLength == 0 || length <= maxLength) return this
    return take(maxLength) + "..."
}
