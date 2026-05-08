package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.PrimaryKey

data class ChatMessage(
    // Message ID
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // 对话 ID
    val sessionId: Long,
    // 创建时间
    val createTime: Long,
    // 来源类型
    val source: Source,
    // 消息内容
    val content: String,
    // 当前Message是否已被总结
    val isSummarized: Boolean,
) {
    companion object {
        enum class Source { Char, User, System }
    }
}