package me.kafuuneko.rpclient.model

/**
 * 一条消息在展示层拆分出的正文或推理片段。
 *
 * 推理片段保留稳定 [Think.id]，使流式内容持续增长时仍能复用同一展开状态；
 * 此模型仅用于 UI 展示，不应写回消息正文或参与 Prompt 构建。
 */
sealed class MessageContentPart {
    data class Text(val content: String) : MessageContentPart()

    data class Think(
        val id: String,
        val content: String,
        val isComplete: Boolean = true
    ) : MessageContentPart()
}

/**
 * 解析模型输出中的 `<think>` 片段，同时保留标签之外的原始正文。
 *
 * 未闭合的末尾标签视为流式生成中的未完成推理块；空白或字面量 `null` 的推理内容
 * 不展示，但仍视为已识别标签，避免把内部标签作为普通正文回退显示。
 */
fun String.toMessageContentParts(messageId: String): List<MessageContentPart> {
    val regex = Regex("<think>([\\s\\S]*?)(</think>|$)", RegexOption.IGNORE_CASE)
    val parts = mutableListOf<MessageContentPart>()
    var cursor = 0
    var foundThinkBlock = false
    regex.findAll(this).forEachIndexed { index, match ->
        foundThinkBlock = true
        if (match.range.first > cursor) {
            parts += MessageContentPart.Text(substring(cursor, match.range.first))
        }
        val thinkContent = match.groupValues[1].trim()
        if (thinkContent.isNotBlank() && !thinkContent.equals(other = "null", ignoreCase = true)) {
            parts += MessageContentPart.Think(
                id = "$messageId:$index",
                content = thinkContent,
                isComplete = match.value.trimEnd().endsWith(suffix = "</think>", ignoreCase = true),
            )
        }
        cursor = match.range.last + 1
    }
    if (cursor < length) {
        parts += MessageContentPart.Text(substring(cursor))
    }
    return when {
        parts.isNotEmpty() -> parts
        foundThinkBlock -> emptyList()
        else -> listOf(MessageContentPart.Text(this))
    }
}
