package me.kafuuneko.rpclient.libs.room.dao.search

import me.kafuuneko.rpclient.libs.search.storage.SearchSourceContract

/**
 * 搜索读取原始数据时共用的轻量投影。
 * - 使用编译期常量，最终 SQL 由 Room 连同表、列和返回模型一起校验。
 * - 不投影完整正文；正文只能通过按主键限定的分段查询读取。
 */
internal object SearchProjectionSql {
    /** 摘要不参与播种、普通扫描和导航检查，三条路径共用这一可见源集合。 */
    const val VISIBLE_SOURCES = """
        SELECT 1 kind, id sourceId, sessionId parentId FROM chat_messages
        WHERE NOT (${SearchSourceContract.CHAT_HIDDEN})
        UNION ALL
        SELECT 2, id, sessionId FROM group_chat_messages
        UNION ALL
        SELECT 3, id, storyId FROM story_chapters
    """

    /** 对象标题、角色名称与排序时间，不读取角色卡扩展字段。 */
    const val OBJECTS = """
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
    """

    /** 章节标题作为所属故事的元数据命中，仍按对象去重。 */
    const val METADATA = """
        $OBJECTS
        UNION ALL
        SELECT 3, s.id, s.title, c.title, s.latestTime, c.id
        FROM story_chapters c JOIN stories s ON s.id = c.storyId
    """

    /**
     * 两种搜索共用标签规则，调用处提供 source(kind, sourceId) 别名。
     * 使用按主键关联的标量查询，避免分页后再关联 UNION 投影时物化全库标签。
     */
    const val SOURCE_LABEL = """
        CASE source.kind
            WHEN 1 THEN IFNULL((SELECT CASE WHEN m.source = 'User' THEN s.userName ELSE c.name END
                FROM chat_messages m JOIN chat_sessions s ON s.id = m.sessionId
                JOIN character c ON c.id = s.characterId WHERE m.id = source.sourceId), '')
            WHEN 2 THEN IFNULL((SELECT speakerNameSnapshot FROM group_chat_messages WHERE id = source.sourceId), '')
            WHEN 3 THEN IFNULL((SELECT title FROM story_chapters WHERE id = source.sourceId), '')
            ELSE '' END
    """
}
