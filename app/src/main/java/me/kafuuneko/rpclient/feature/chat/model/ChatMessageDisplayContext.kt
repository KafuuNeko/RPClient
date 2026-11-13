package me.kafuuneko.rpclient.feature.chat.model

import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.UserPersona

/** 单聊分页消息执行 Display Regex 所需的持久化上下文。 */
internal data class ChatMessageDisplayContext(
    val persona: UserPersona,
    val character: Character
)
