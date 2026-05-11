package me.kafuuneko.rpclient.feature.chatcreate.model

import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry

data class ChatCreateLorebookEntryItem(
    val entry: LorebookEntry,
    val lorebookName: String
)

