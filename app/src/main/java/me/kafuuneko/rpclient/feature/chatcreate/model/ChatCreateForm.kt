package me.kafuuneko.rpclient.feature.chatcreate.model

/** 创建单聊会话时尚未持久化的用户选择。 */
data class ChatCreateForm(
    /** 当前选中角色的 ID。 */
    val selectedCharacterId: Long? = null,
    /** 当前选中的角色开场白位置。 */
    val selectedFirstMessageIndex: Int? = null,
    /** 供界面展示或持久化的标题。 */
    val title: String = "",
    /** 仅供当前会话 Prompt 使用的用户备注。 */
    val userNote: String = "",
    /** 当前已选中的世界书条目 ID 集合。 */
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
