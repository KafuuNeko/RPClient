package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.entity.FileEntity

@Dao
interface FileDao {
    /**
     * 插入或替换一条文件记录。
     *
     * @param fileEntity 要插入的文件实体对象。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fileEntity: FileEntity)

    /**
     * 根据 UUID 获取对应的文件记录。
     *
     * @param uuid 文件的唯一标识符。
     * @return 匹配的文件实体对象，如果不存在则返回 null。
     */
    @Query("SELECT * FROM files WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): FileEntity?

    /**
     * 根据 UUID 删除对应的文件记录。
     *
     * @param uuid 要删除的文件的唯一标识符。
     */
    @Query("DELETE FROM files WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: String)

    /**
     * 统计具有指定哈希值（即实际文件名）的记录数量。
     * 用于在删除记录时判断是否还有其他 UUID 引用了该物理文件，以决定是否可以安全地删除物理文件。
     *
     * @param hash 文件的 SHA-256 哈希值。
     * @return 引用该哈希值的记录总数。
     */
    @Query("SELECT COUNT(*) FROM files WHERE hash = :hash")
    suspend fun countByHash(hash: String): Int
}
