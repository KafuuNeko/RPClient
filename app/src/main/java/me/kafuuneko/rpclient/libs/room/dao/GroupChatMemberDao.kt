package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMember

@Dao
interface GroupChatMemberDao : MutableDao<GroupChatMember> {
    @Query("SELECT * FROM group_chat_members WHERE sessionId = :sessionId ORDER BY sortOrder ASC")
    suspend fun getMembers(sessionId: Long): List<GroupChatMember>

    @Query(
        """
        UPDATE group_chat_members
        SET muted = :muted
        WHERE sessionId = :sessionId AND characterId = :characterId
        """
    )
    suspend fun updateMuted(sessionId: Long, characterId: Long, muted: Boolean)

    @Query(
        """
        UPDATE group_chat_members
        SET sortOrder = :sortOrder
        WHERE sessionId = :sessionId AND characterId = :characterId
        """
    )
    suspend fun updateSortOrder(sessionId: Long, characterId: Long, sortOrder: Int)

    @Query(
        """
        DELETE FROM group_chat_members
        WHERE sessionId = :sessionId AND characterId = :characterId
        """
    )
    suspend fun deleteMember(sessionId: Long, characterId: Long)
}
