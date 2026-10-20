package me.kafuuneko.rpclient.libs.room.dao.search

/**
 * FTS 聚合分页的完整查询，由 ContentSearchDao 的 @Query 编译校验。
 * - 先聚合对象或对象内的源，再分页，最后读取当前页 token 与标签。
 * - 范围与游标全部绑定；保留完整 SQL 结构，避免拆成难以追踪的零散条件片段。
 */
internal object SearchFtsSql {
    const val MATCH_PAGE = """
        WITH matched AS (
            SELECT MIN(c.rowid) rowid FROM search_chunks_fts
            JOIN search_chunks c ON c.rowid = search_chunks_fts.rowid
            JOIN search_sources s ON s.kind = c.kind AND s.sourceId = c.sourceId
            WHERE search_chunks_fts MATCH :phrase AND s.dirty = 0 AND s.deleted = 0 AND s.revision = c.revision
                AND (:filterKind = 0 OR c.kind = :filterKind) AND (:parentId IS NULL OR c.parentId = :parentId)
            GROUP BY c.kind, c.parentId, CASE WHEN :parentId IS NULL THEN 0 ELSE c.sourceId END
        ), selected AS (
            SELECT c.kind, c.parentId, c.sourceId, c.rowid, o.latestTime, o.title, o.names
            FROM matched m JOIN search_chunks c ON c.rowid = m.rowid
            JOIN (${SearchProjectionSql.OBJECTS}) o ON o.kind = c.kind AND o.parentId = c.parentId
            WHERE o.latestTime < :afterTime OR (o.latestTime = :afterTime AND c.kind > :afterKind)
                OR (o.latestTime = :afterTime AND c.kind = :afterKind AND c.parentId < :afterParentId)
                OR (:parentId IS NOT NULL AND o.latestTime = :afterTime AND c.kind = :afterKind
                    AND c.parentId = :afterParentId AND c.sourceId < :afterSourceId)
            ORDER BY o.latestTime DESC, c.kind, c.parentId DESC, c.sourceId DESC LIMIT :limit
        )
        SELECT source.*, c.tokens, ${SearchProjectionSql.SOURCE_LABEL} sourceLabel
        FROM selected source JOIN search_chunks c ON c.rowid = source.rowid
        ORDER BY source.latestTime DESC, source.kind, source.parentId DESC, source.sourceId DESC
    """
}
