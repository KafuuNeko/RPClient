package me.kafuuneko.rpclient.feature.groupchat.utils

import me.kafuuneko.rpclient.libs.groupchat.GroupChatMacros
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.repository.GroupChatData

/** 使用同一份群聊快照为消息处理和展示提供角色、人设及 Regex 的宏。 */
internal fun GroupChatData.macroContext(speaker: Character? = members.singleOrNull()?.character): GroupChatMacros =
    GroupChatMacros(session, members, persona, speaker)
