package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.dao.search.SearchProjectionSql
import me.kafuuneko.rpclient.libs.room.entity.SearchSource
import me.kafuuneko.rpclient.libs.search.SearchObject

/**
 * 原始业务数据的搜索读取入口，不访问 FTS 派生表。
 * - 为播种、普通扫描与导航提供相同的可见性规则。
 * - 固定查询使用具名参数，由 Room 校验并生成绑定代码。
 */
@Dao
interface SearchSourceDao {
    /** 按源 ID 读取有界批次；初始修订仅用于播种，不能覆盖已经登记的较新代次。 */
    @Query(
        """
        SELECT kind, sourceId, parentId, 1 revision, 0 indexedRevision, 0 deleted, 1 dirty, 0 changedTime
        FROM (${SearchProjectionSql.VISIBLE_SOURCES})
        WHERE kind = :kind AND sourceId > :afterId AND (:parentId IS NULL OR parentId = :parentId)
        ORDER BY sourceId LIMIT :limit
    """
    )
    suspend fun sourceBatch(
        kind: Int,
        afterId: Long,
        parentId: Long?,
        limit: Int,
    ): List<SearchSource>

    /** 导航前核对源的存在性、归属及可见性，不读取正文。 */
    @Query(
        """
        SELECT EXISTS(SELECT 1 FROM (${SearchProjectionSql.VISIBLE_SOURCES})
            WHERE kind = :kind AND sourceId = :sourceId AND parentId = :parentId)
    """
    )
    suspend fun sourceExists(kind: Int, sourceId: Long, parentId: Long): Boolean

    /** 仅加载本批源的所属对象，IN 参数由 Room 展开，不手动拼接占位符。 */
    @Query(
        """
        SELECT * FROM (${SearchProjectionSql.OBJECTS}) WHERE kind = :kind AND parentId IN (:parentIds)
    """
    )
    suspend fun objectsByIds(kind: Int, parentIds: List<Long>): List<SearchObject>

    /** 元数据使用键集分页，避免将长章节目录一次加载到内存。 */
    @Query(
        """
        SELECT * FROM (${SearchProjectionSql.METADATA}) WHERE (:filterKind = 0 OR kind = :filterKind)
        AND (kind > :afterKind OR (kind = :afterKind AND parentId > :afterParentId)
            OR (kind = :afterKind AND parentId = :afterParentId AND sourceId > :afterSourceId))
        ORDER BY kind, parentId, sourceId LIMIT :limit
    """
    )
    suspend fun metadataBatch(
        filterKind: Int,
        afterKind: Int,
        afterParentId: Long,
        afterSourceId: Long,
        limit: Int,
    ): List<SearchObject>

    /** 标签与 FTS 查询共享表达式，源被删除时返回空字符串。 */
    @Query(
        """
        WITH source AS (SELECT :kind kind, :sourceId sourceId)
        SELECT ${SearchProjectionSql.SOURCE_LABEL} FROM source
    """
    )
    suspend fun sourceLabel(kind: Int, sourceId: Long): String

    /**
     * 按主键与归属分段读取原文，避免超长正文溢出 CursorWindow。
     * @param offset 从一开始的 Unicode 码点位置
     * @param length 本段最多读取的码点数，由读取器限制
     */
    @Query(
        """
        SELECT substr(content, :offset, :length) FROM chat_messages
        WHERE :kind = 1 AND id = :sourceId AND sessionId = :parentId
        UNION ALL
        SELECT substr(content, :offset, :length) FROM group_chat_messages
        WHERE :kind = 2 AND id = :sourceId AND sessionId = :parentId
        UNION ALL
        SELECT substr(content, :offset, :length) FROM story_chapters
        WHERE :kind = 3 AND id = :sourceId AND storyId = :parentId
    """
    )
    suspend fun contentPart(
        kind: Int,
        sourceId: Long,
        parentId: Long,
        offset: Int,
        length: Int,
    ): String?
}
