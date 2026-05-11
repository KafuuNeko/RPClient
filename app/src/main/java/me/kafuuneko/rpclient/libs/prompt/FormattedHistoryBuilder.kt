package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.room.entity.ChatMessage

class FormattedHistoryBuilder {
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

