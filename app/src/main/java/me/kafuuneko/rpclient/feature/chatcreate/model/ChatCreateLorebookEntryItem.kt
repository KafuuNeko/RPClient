package me.kafuuneko.rpclient.feature.chatcreate.model

import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry

/** 新建单聊页面中的世界书条目及所属世界书名称。 */
data class ChatCreateLorebookEntryItem(
    val entry: LorebookEntry,
    val lorebookName: String
)
