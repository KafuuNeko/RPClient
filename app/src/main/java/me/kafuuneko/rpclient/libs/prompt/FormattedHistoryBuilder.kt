package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.room.entity.ChatMessage

class FormattedHistoryBuilder {
    /**
     * 将消息列表展开成 Summary/Macro 可读的纯文本历史。
     */
    fun build(
        messages: List<ChatMessage>,
        userName: String,
        characterName: String
    ): String {
        return messages.joinToString("\n") { message ->
            val speaker = when (message.source) {
                ChatMessage.Source.User -> userName
                ChatMessage.Source.Char -> characterName
                ChatMessage.Source.System -> "System"
            }
            "$speaker: ${message.content}"
        }
    }
}
