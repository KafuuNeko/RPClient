package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.room.entity.ChatMessage

/** 将结构化聊天消息格式化为宏和摘要模板使用的带发言者纯文本。 */
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
                ChatMessage.Source.Summary -> error("Summary snapshots must not be formatted as chat history")
            }
            "$speaker: ${message.content}"
        }
    }
}
