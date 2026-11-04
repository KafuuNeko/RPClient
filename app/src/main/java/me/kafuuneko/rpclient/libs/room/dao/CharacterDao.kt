package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.model.CharacterContentItem
import me.kafuuneko.rpclient.libs.room.model.MessageType
import me.kafuuneko.rpclient.libs.room.model.RecentCharacterOverview

/** 两个入口共用同一成员口径。候选故事角色与静音群成员都保留关联。 */
private const val CHARACTER_CONTENT = """
    SELECT characterId, 'Chat' AS kind, id AS contentId, title, latestTime FROM chat_sessions
    UNION ALL
    SELECT m.characterId, 'Group', g.id, g.title, g.latestTime
    FROM group_chat_members m JOIN group_chat_sessions g ON g.id = m.sessionId
    UNION ALL
    SELECT c.characterId, 'Story', s.id, s.title, s.latestTime
    FROM story_characters c JOIN stories s ON s.id = c.storyId
"""

/** 角色表的基础查询入口；跨表导入、删除和文件清理由 Repository 统一编排。 */
@Dao
interface CharacterDao : MutableDao<Character> {
    /**
     * 统计角色卡总数，角色浏览模式无需读取完整卡片。
     *
     * @return 角色卡总数。
     */
    @Query("SELECT COUNT(*) FROM character")
    suspend fun countCharacters(): Int

    /**
     * 读取角色名称，避免加载完整卡片。
     *
     * @param characterId 角色 ID。
     * @return 角色名称，角色不存在时返回 null。
     */
    @Query("SELECT name FROM character WHERE id = :characterId")
    suspend fun getCharacterName(characterId: Long): String?

    /**
     * 按最近活动聚合角色，单个 MAX 使标题取自该时间对应的一项内容。
     *
     * @return 按最近活动倒序排列的角色概览。
     */
    @Query(
        "SELECT a.characterId, c.name AS characterName, c.avatar AS avatarUuid, a.title AS latestTitle, " +
            "MAX(a.latestTime) AS latestTime, COUNT(*) AS contentCount " +
            "FROM (" + CHARACTER_CONTENT + ") a JOIN character c ON c.id = a.characterId " +
            "GROUP BY a.characterId ORDER BY MAX(a.latestTime) DESC, a.characterId DESC"
    )
    suspend fun getRecentCharacters(): List<RecentCharacterOverview>

    /**
     * 按稳定顺序读取指定角色的关联内容及轻量预览。
     *
     * 每个会话只关联最新一条普通消息；故事仅截取与首页一致的章节片段。
     * 不加载完整消息历史、图片附件或章节正文，也不统计消息数。
     *
     * @param characterId 角色 ID。
     * @param singleMessageType 单聊图片的稳定消息类型编码。
     * @param groupMessageType 群聊图片的稳定消息类型编码。
     * @return 关联的单聊、群聊与故事元数据及预览。
     */
    @Query(
        """
        SELECT content.kind, content.contentId, content.title, content.latestTime,
               CASE content.kind
                   WHEN 'Chat' THEN chatMessage.content
                   WHEN 'Group' THEN groupMessage.content
                   WHEN 'Story' THEN (
                       SELECT SUBSTR(
                           REPLACE(REPLACE(TRIM(chapter.content), CHAR(13), ' '), CHAR(10), ' '),
                           1,
                           160
                       )
                       FROM story_chapters AS chapter
                       LEFT JOIN story_volumes AS volume ON volume.id = chapter.volumeId
                       WHERE chapter.storyId = content.contentId
                         AND TRIM(chapter.content) != ''
                       ORDER BY CASE WHEN chapter.volumeId IS NULL THEN 0 ELSE 1 END ASC,
                                COALESCE(volume.sortOrder, chapter.sortOrder) ASC,
                                CASE WHEN chapter.volumeId IS NULL THEN 0 ELSE chapter.sortOrder END ASC,
                                chapter.id ASC
                       LIMIT 1
                   )
               END AS previewContent,
               CASE content.kind
                   WHEN 'Chat' THEN EXISTS (
                       SELECT 1 FROM message_images AS images
                       WHERE images.messageType = :singleMessageType
                         AND images.messageId = chatMessage.id
                   )
                   WHEN 'Group' THEN EXISTS (
                       SELECT 1 FROM message_images AS images
                       WHERE images.messageType = :groupMessageType
                         AND images.messageId = groupMessage.id
                   )
                   ELSE 0
               END AS previewHasImages
        FROM (
        """ + CHARACTER_CONTENT + """
        ) AS content
        LEFT JOIN chat_messages AS chatMessage
            ON content.kind = 'Chat' AND chatMessage.id = (
                SELECT messages.id FROM chat_messages AS messages
                WHERE messages.sessionId = content.contentId
                  AND messages.source != 'Summary'
                ORDER BY messages.createTime DESC, messages.id DESC
                LIMIT 1
            )
        LEFT JOIN group_chat_messages AS groupMessage
            ON content.kind = 'Group' AND groupMessage.id = (
                SELECT messages.id FROM group_chat_messages AS messages
                WHERE messages.sessionId = content.contentId
                ORDER BY messages.createTime DESC, messages.id DESC
                LIMIT 1
            )
        WHERE content.characterId = :characterId
        ORDER BY content.latestTime DESC, content.kind, content.contentId DESC
        """
    )
    suspend fun getCharacterContent(
        characterId: Long,
        singleMessageType: MessageType,
        groupMessageType: MessageType,
    ): List<CharacterContentItem>

    /**
     * 获取所有角色。
     *
     * @return 按 id 倒序排列的角色列表。
     */
    @Query("SELECT * FROM character ORDER BY id DESC")
    suspend fun getAllCharacters(): List<Character>

    /**
     * 根据角色 id 查询角色。
     *
     * @param id 角色 id。
     * @return 匹配的角色；如果不存在则返回 null。
     */
    @Query("SELECT * FROM character WHERE id = :id")
    suspend fun getCharacterById(id: Long): Character?

    /**
     * 根据角色 id 删除角色。
     *
     * @param id 角色 id。
     */
    @Query("DELETE FROM character WHERE id = :id")
    suspend fun deleteCharacterById(id: Long)

    /**
     * 清除所有绑定到指定世界书的角色关联。
     *
     * @param lorebookId 已删除的世界书 id。
     */
    @Query("UPDATE character SET characterLorebookId = 0 WHERE characterLorebookId = :lorebookId")
    suspend fun clearLorebookAssociations(lorebookId: Long)

    /** 观察本地角色背景关联，删除角色后自动回退为空。 */
    @Query("SELECT backgroundFileUuid FROM character WHERE id = :id")
    fun observeBackground(id: Long): Flow<String?>
}
