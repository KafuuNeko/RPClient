package me.kafuuneko.rpclient.libs.search

/** 搜索专用轻量 SQL 投影；类型编码和表名均来自应用，用户查询只能作为绑定参数。 */
internal object SearchSql {
    /** 正文、归属与展示标签的存储契约；不可在查询和触发器中维护两份表名。 */
    data class Source(val kind: Int, val table: String, val parent: String, val columns: List<String>, val hidden: String = "0")

    val sources = listOf(
        Source(1, "chat_messages", "sessionId", listOf("content", "source", "sessionId"), "NEW.source = 'Summary'"),
        Source(2, "group_chat_messages", "sessionId", listOf("content", "speakerNameSnapshot", "sessionId")),
        Source(3, "story_chapters", "storyId", listOf("content", "title", "storyId"))
    )
    val metadataColumns = mapOf(
        "chat_sessions" to listOf("title", "characterId", "latestTime", "userName"),
        "group_chat_sessions" to listOf("title", "latestTime"),
        "stories" to listOf("title", "latestTime"),
        "character" to listOf("name"),
        "group_chat_members" to listOf("sessionId", "characterId"),
        "story_characters" to listOf("storyId", "characterId")
    )
    val observedTables = sources.map { it.table } + metadataColumns.keys

    val objects = """
        SELECT 1 kind, s.id parentId, s.title, c.name names, s.latestTime, 0 sourceId
        FROM chat_sessions s JOIN character c ON c.id = s.characterId
        UNION ALL
        SELECT 2, s.id, s.title,
            IFNULL((SELECT group_concat(c.name, char(10)) FROM group_chat_members m
                JOIN character c ON c.id = m.characterId WHERE m.sessionId = s.id), ''), s.latestTime, 0
        FROM group_chat_sessions s
        UNION ALL
        SELECT 3, s.id, s.title,
            IFNULL((SELECT group_concat(c.name, char(10)) FROM story_characters m
                JOIN character c ON c.id = m.characterId WHERE m.storyId = s.id), ''), s.latestTime, 0
        FROM stories s
    """.trimIndent()

    val metadata = """
        $objects
        UNION ALL
        SELECT 3, s.id, s.title, c.title, s.latestTime, c.id
        FROM story_chapters c JOIN stories s ON s.id = c.storyId
    """.trimIndent()

    /** 返回内部固定表名和归属列，拒绝未知类型。 */
    fun sourceTable(kind: Int): Pair<String, String> {
        val source = sources.firstOrNull { it.kind == kind } ?: error("Unknown search source kind")
        return source.table to source.parent
    }
}
