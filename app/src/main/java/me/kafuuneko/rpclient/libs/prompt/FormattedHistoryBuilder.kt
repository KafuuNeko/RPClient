package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage

/** 为历史文本宏与 Raw 摘要提供带发言者的素材。 */
class FormattedHistoryBuilder {
    /**
     * 将消息列表展开成 Summary/Macro 可读的纯文本历史。
     */
    fun build(
        messages: List<ChatMessage>,
        userName: String,
        characterName: String,
        imageCounts: Map<Long, Int> = emptyMap(),
    ): String {
        return messages.joinToString("\n") { message ->
            val speaker = speakerName(message.source, userName, characterName)
            "$speaker: ${message.content}" + imageCounts[message.id]?.takeIf { value -> value > 0 }
                ?.let { value -> " [Images: $value]" }.orEmpty()
        }
    }

    /** 按原始历史顺序提供 Raw 摘要的发言者、文字和图片。 */
    internal fun buildSummaryHistory(
        messages: List<ChatMessage>,
        userName: String,
        characterName: String,
        messageImages: Map<Long, List<LLMImageReference>>,
    ): List<RawSummaryHistoryMessage> = messages.map { message ->
        RawSummaryHistoryMessage(
            speakerName = speakerName(message.source, userName, characterName),
            content = message.content,
            images = messageImages[message.id].orEmpty(),
        )
    }

    private fun speakerName(
        source: ChatMessage.Source,
        userName: String,
        characterName: String
    ): String =
        when (source) {
            ChatMessage.Source.User -> userName
            ChatMessage.Source.Char -> characterName
            ChatMessage.Source.System -> "System"
            ChatMessage.Source.Summary -> error("Summary snapshots must not be formatted as chat history")
        }
}
