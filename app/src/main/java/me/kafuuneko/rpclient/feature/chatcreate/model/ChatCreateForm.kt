package me.kafuuneko.rpclient.feature.chatcreate.model

/** 创建单聊会话时尚未持久化的用户选择。 */
data class ChatCreateForm(
    val selectedCharacterId: Long? = null,
    val selectedFirstMessageIndex: Int? = null,
    val title: String = "",
    val userNote: String = "",
    val selectedLorebookEntryIds: Set<Long> = emptySet()
) {
    fun selectCharacter(
        characterId: Long?,
        hasFirstMessage: Boolean,
        previousLinkedLorebookEntryIds: Set<Long> = emptySet(),
        linkedLorebookEntryIds: Set<Long> = emptySet()
    ): ChatCreateForm {
        return copy(
            selectedCharacterId = characterId,
            selectedFirstMessageIndex = if (hasFirstMessage) 0 else null,
            selectedLorebookEntryIds = selectedLorebookEntryIds -
                previousLinkedLorebookEntryIds +
                linkedLorebookEntryIds
        )
    }
}
