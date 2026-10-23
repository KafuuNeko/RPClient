package me.kafuuneko.rpclient.libs.story

import me.kafuuneko.rpclient.utils.removeThinkBlocks

/** 只移除可确定为模型包装内容的输出，不改写正文标点和排版。 */
class StoryOutputSanitizer {
    fun sanitize(content: String): String {
        var result = content.removeThinkBlocks()
        CODE_FENCE.matchEntire(result.trim())?.groupValues?.get(1)?.let { result = it }
        val firstLineEnd = result.indexOf('\n')
        if (
            firstLineEnd >= 0
            && result
                .substring(startIndex = 0, endIndex = firstLineEnd)
                .trimEnd('\r')
                .trim() in KNOWN_PREAMBLES
        ) {
            result = result.substring(firstLineEnd + 1)
        }
        return result
    }

    private companion object {
        val CODE_FENCE = Regex("^```(?:[A-Za-z0-9_-]+)?[ \\t]*\\r?\\n([\\s\\S]*?)\\r?\\n```$")
        val KNOWN_PREAMBLES = setOf(
            "改写结果：",
            "改写如下：",
            "续写如下：",
            "续写内容：",
            "Rewrite:",
            "Continuation:",
        )
    }
}

/**
 * 在模型未提供任何空白边界时，为连续正文补充段落分隔；已有排版保持原样。
 */
internal fun prepareStoryContinuationText(
    sourceContent: String,
    continuation: String,
): String {
    val canAppendWithoutSeparator = sourceContent.isEmpty()
        || continuation.isEmpty()
        || sourceContent.last().isWhitespace()
        || continuation.first().isWhitespace()
    if (canAppendWithoutSeparator) {
        return continuation
    }
    return "\n\n$continuation"
}
