package me.kafuuneko.rpclient.feature.chatcreate.model

data class ChatCreateForm(
    val selectedCharacterId: Long? = null,
    val selectedFirstMessageIndex: Int? = null,
    val title: String = "",
    val userNote: String = "",
    val selectedLorebookEntryIds: Set<Long> = emptySet()
)
