package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.entity.FileEntity

/**
 * 应用私有文件索引的数据库访问接口。
 *
 * 物理文件读写与共享哈希引用计数由 FileRepository 处理，DAO 不直接操作文件系统。
 */
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

    /** 按批次释放专属文件索引；调用方先删除附件关系。 */
    @Query("DELETE FROM files WHERE uuid IN (:uuids)")
    suspend fun deleteByUuids(uuids: List<String>)

    /**
     * 按 UUID 列表批量读取文件记录，供消息附件批量加载使用。
     *
     * 长列表需要在 Repository 层分批调用以规避 SQLite 查询参数上限。
     *
     * @param uuids 文件 UUID 列表。
     * @return 匹配的文件实体列表；不存在的 UUID 不出现在结果中。
     */
    @Query("SELECT * FROM files WHERE uuid IN (:uuids)")
    suspend fun getByUuids(uuids: List<String>): List<FileEntity>

    /**
     * 读取指定哈希值的全部文件记录，用于查询共享同一物理文件的引用。
     *
     * @param hash 文件的 SHA-256 哈希值。
     * @return 引用该哈希值的所有文件实体。
     */
    @Query("SELECT * FROM files WHERE hash = :hash")
    suspend fun getByHash(hash: String): List<FileEntity>
}
