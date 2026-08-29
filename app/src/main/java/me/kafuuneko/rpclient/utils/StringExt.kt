package me.kafuuneko.rpclient.utils

private val ThinkBlockRegex = Regex("<think>[\\s\\S]*?(</think>|$)", RegexOption.IGNORE_CASE)

/** 仅在字符串非空白时返回自身。 */
fun String?.takeIfNotBlank(): String? {
    return this?.takeIf { it.isNotBlank() }
}

/** 移除 think 块并保留块外原始空白。 */
fun String.removeThinkBlocks(): String {
    return replace(ThinkBlockRegex, "")
}

/** 移除保存回复中的 think 块，避免推理内容继续进入后续上下文。 */
fun String.stripThinkBlocks(): String {
    return removeThinkBlocks().trim()
}

/**
 * 根据思考块保留策略生成消息复制文本。
 *
 * @param includeThinkBlocks 是否保留已保存的 `<think>...</think>` 思考块
 * @return 可安全发送到剪贴板的消息文本
 */
fun String.toMessageCopyText(includeThinkBlocks: Boolean): String {
    return if (includeThinkBlocks) this else stripThinkBlocks()
}

/** 生成适合列表展示的预览，并按需截断长度。 */
fun String.toPreview(maxLength: Int = 0): String {
    if (maxLength == 0 || length <= maxLength) return this
    return take(maxLength) + "..."
}
