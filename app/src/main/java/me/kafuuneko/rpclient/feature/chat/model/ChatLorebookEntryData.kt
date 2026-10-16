package me.kafuuneko.rpclient.feature.chat.model

import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry

/** 映射世界书 UI 分组所需的书籍索引与条目集合。 */
data class ChatLorebookEntryData(
    val lorebooks: Map<Long, Lorebook>,
    val entries: List<LorebookEntry>
)
