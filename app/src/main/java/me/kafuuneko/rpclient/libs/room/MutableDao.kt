package me.kafuuneko.rpclient.libs.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Update

/** 为各实体 DAO 提供统一的插入、批量插入、更新和删除操作。 */
@Dao
interface MutableDao<T> {
    /** 按 REPLACE 策略写入单条实体，并返回行 ID。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(data: T): Long

    /** 按 REPLACE 策略批量写入实体。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceAll(data: List<T>): List<Long>

    /** 冲突时忽略单条实体，返回值由 Room 表示是否插入成功。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(data: T): Long

    /** 冲突时忽略对应实体的批量写入。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnoreAll(data: List<T>): List<Long>

    @Update
    suspend fun update(data: T)

    @Delete
    suspend fun delete(data: T)
}
