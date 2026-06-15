package me.kafuuneko.rpclient.feature.groupchatcreate.model

/** 创建群聊时使用的角色选项，并记录角色卡绑定的世界书。 */
data class GroupChatCreateCharacterItem(
    val id: Long,
    val name: String,
    val description: String,
    val selected: Boolean,
    val characterLorebookId: Long = 0L,
    val greetings: List<String> = emptyList()
)
