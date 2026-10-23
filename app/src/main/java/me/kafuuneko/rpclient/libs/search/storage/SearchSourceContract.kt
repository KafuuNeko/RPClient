package me.kafuuneko.rpclient.libs.search.storage

/** 搜索维护观察的业务表与列；仅保存应用固定标识符，不能接受用户输入。 */
internal object SearchSourceContract {
    /** 摘要的隐藏条件同时用于业务触发器和可见源投影，避免两种搜索出现遗漏或泄漏。 */
    const val CHAT_HIDDEN = "source = 'Summary'"

    /** 触发器观察的源类型、归属列、变更列及隐藏条件。 */
    data class Source(val kind: Int, val table: String, val parent: String, val columns: List<String>, val hidden: String = "0")

    val sources = listOf(
        Source(
            kind = 1,
            table = "chat_messages",
            parent = "sessionId",
            columns = listOf(
                "content",
                "source",
                "sessionId",
            ),
            hidden = "NEW.$CHAT_HIDDEN",
        ),
        Source(
            kind = 2,
            table = "group_chat_messages",
            parent = "sessionId",
            columns = listOf(
                "content",
                "speakerNameSnapshot",
                "sessionId",
            ),
        ),
        Source(kind = 3, table = "story_chapters", parent = "storyId", columns = listOf("content", "title", "storyId")),
    )
    val metadataColumns = mapOf(
        "chat_sessions" to listOf("title", "characterId", "latestTime", "userName"),
        "group_chat_sessions" to listOf("title", "latestTime"),
        "stories" to listOf("title", "latestTime"),
        "character" to listOf("name"),
        "group_chat_members" to listOf("sessionId", "characterId"),
        "story_characters" to listOf("storyId", "characterId"),
    )
    val observedTables = sources.map { it.table } + metadataColumns.keys
}
