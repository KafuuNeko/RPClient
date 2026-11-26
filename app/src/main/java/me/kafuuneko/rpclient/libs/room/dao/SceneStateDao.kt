package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import me.kafuuneko.rpclient.libs.room.entity.SceneState
import me.kafuuneko.rpclient.libs.room.entity.SceneStateRevision

/** 剧情当前快照及历史记录；范围与消息边界校验留在仓库。 */
@Dao
interface SceneStateDao {
    /** 读取当前状态，不存在表示空初态。 */
    @Query("SELECT * FROM scene_states WHERE conversationKey = :key")
    suspend fun get(key: String): SceneState?

    /** 初始化当前快照，禁止 REPLACE 删除修订历史。 */
    @Insert
    suspend fun insert(value: SceneState)

    /** 写入事务内已校验版本的新快照。 */
    @Update
    suspend fun update(value: SceneState)

    /** 按版本顺序读取保留窗口内的完整快照。 */
    @Query("SELECT * FROM scene_state_revisions WHERE conversationKey = :key ORDER BY revision")
    suspend fun revisions(key: String): List<SceneStateRevision>

    /** 追加带已提交消息边界的修订。 */
    @Insert
    suspend fun insertRevision(value: SceneStateRevision)

    /** 只保留最近修订；最早保留的完整快照同时作为基线。 */
    @Query("DELETE FROM scene_state_revisions WHERE conversationKey = :key AND revision NOT IN (SELECT revision FROM scene_state_revisions WHERE conversationKey = :key ORDER BY revision DESC LIMIT 200)")
    suspend fun prune(key: String)

    /** 删除截断点及之后的状态，使未来分支不能再次读到被删除的剧情。 */
    @Query("DELETE FROM scene_state_revisions WHERE conversationKey = :key AND effectiveAfterMessageId >= :boundary AND effectiveAfterMessageId != 0")
    suspend fun deleteFrom(key: String, boundary: Long)
}
