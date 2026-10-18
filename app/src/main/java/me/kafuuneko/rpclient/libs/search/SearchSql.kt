package me.kafuuneko.rpclient.libs.search

/** 搜索专用轻量 SQL 投影；类型编码和表名均来自应用，用户查询只能作为绑定参数。 */
internal object SearchSql {
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
    fun sourceTable(kind: Int): Pair<String, String> = when (kind) {
        1 -> "chat_messages" to "sessionId"
        2 -> "group_chat_messages" to "sessionId"
        3 -> "story_chapters" to "storyId"
        else -> error("Unknown search source kind")
    }
}
