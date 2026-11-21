package me.kafuuneko.rpclient.feature.chat.model

/** 单聊设置中独立自动保存的文本字段，避免不同输入框共用保存回执。 */
enum class ChatSettingField {
    Title,
    Summary,
    UserNote,
    UserName,
    UserDescription,
    CreatorNotes;

    /**
     * 从已加载的会话快照读取保存后的实际展示文本。
     * @param session 持久化后重新加载的会话快照。
     * @return 当前字段的实际展示文本。
     */
    fun valueIn(session: ChatSessionItem): String = when (this) {
        Title -> session.title
        Summary -> session.summarize
        UserNote -> session.userNote
        UserName -> session.userName
        UserDescription -> session.userDescription
        CreatorNotes -> session.creatorNotes
    }
}

/**
 * 自动保存的编辑标识与文本。
 * - 提交时携带用户原文，回执携带持久化后重新加载的文本。
 * - 标识在输入框重建后也不会重复，文本规范化不影响回执归属。
 */
data class ChatSettingEdit(val id: String, val value: String)
