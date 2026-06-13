package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMember

/** 群聊成员关系、静音状态和排序的数据库访问接口。 */
@Dao
interface GroupChatMemberDao : MutableDao<GroupChatMember> {
    /** 按持久化顺序读取会话成员。 */
    @Query("SELECT * FROM group_chat_members WHERE sessionId = :sessionId ORDER BY sortOrder ASC")
    suspend fun getMembers(sessionId: Long): List<GroupChatMember>

    /** 更新成员自动发言静音状态。 */
    @Query(
        """
        UPDATE group_chat_members
        SET muted = :muted
        WHERE sessionId = :sessionId AND characterId = :characterId
        """
    )
    suspend fun updateMuted(sessionId: Long, characterId: Long, muted: Boolean)

    /** 更新成员在群聊和 Prompt 中的排序位置。 */
    @Query(
        """
        UPDATE group_chat_members
        SET sortOrder = :sortOrder
        WHERE sessionId = :sessionId AND characterId = :characterId
        """
    )
    suspend fun updateSortOrder(sessionId: Long, characterId: Long, sortOrder: Int)

    /** 从指定会话移除一名成员关系。 */
    @Query(
        """
        DELETE FROM group_chat_members
        WHERE sessionId = :sessionId AND characterId = :characterId
        """
    )
    suspend fun deleteMember(sessionId: Long, characterId: Long)
}
