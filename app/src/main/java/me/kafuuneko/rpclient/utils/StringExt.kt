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

/**
 * 生成适合列表展示的预览，避免将完整长文本交给布局测量。
 *
 * @param maxLength 保留正文的 UTF-16 长度上限，零表示不截断；不拆分完整的代理对。
 * @return 原文或带省略标记的预览，不修改原始字符串。
 */
fun String.toPreview(maxLength: Int = 0): String {
    require(maxLength >= 0) { "Preview length must not be negative" }
    if (maxLength == 0 || length <= maxLength) return this
    val end = if (this[maxLength - 1].isHighSurrogate() && this[maxLength].isLowSurrogate()) {
        maxLength - 1
    } else {
        maxLength
    }
    return substring(0, end) + "..."
}
