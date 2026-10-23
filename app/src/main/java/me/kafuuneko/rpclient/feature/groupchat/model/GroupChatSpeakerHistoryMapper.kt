package me.kafuuneko.rpclient.feature.groupchat.model

import me.kafuuneko.rpclient.libs.groupchat.GroupChatSpeakerHistory
import me.kafuuneko.rpclient.libs.room.repository.GroupChatSpeakerSelectionData

/** 将数据库最小投影转换为发言者选择领域上下文。 */
internal fun GroupChatSpeakerSelectionData.toSpeakerHistory(): GroupChatSpeakerHistory {
    return GroupChatSpeakerHistory(
        spokenCharacterIdsSinceLastUserMessage = spokenCharacterIdsSinceLastUserMessage,
        lastCharacterSpeakerId = lastCharacterSpeakerId,
    )
}
