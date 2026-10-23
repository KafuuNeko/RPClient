package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.StoryChapter
import me.kafuuneko.rpclient.libs.room.model.StoryChapterOverview

/** Story 章节内容、轻量大纲、排序和乐观锁写入接口。 */
@Dao
interface StoryChapterDao : MutableDao<StoryChapter> {
    /** 章节主键冲突必须中止，避免 REPLACE 将并发修改静默覆盖。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(data: StoryChapter): Long

    @Query("SELECT * FROM story_chapters WHERE id = :id")
    suspend fun getById(id: Long): StoryChapter?

    @Query(
        """
        SELECT chapter.*
        FROM story_chapters AS chapter
        LEFT JOIN story_volumes AS volume ON volume.id = chapter.volumeId
        WHERE chapter.storyId = :storyId
        ORDER BY CASE WHEN chapter.volumeId IS NULL THEN 0 ELSE 1 END ASC,
                 COALESCE(volume.sortOrder, chapter.sortOrder) ASC,
                 CASE WHEN chapter.volumeId IS NULL THEN 0 ELSE chapter.sortOrder END ASC,
                 chapter.id ASC
        """
    )
    suspend fun getByStoryId(storyId: Long): List<StoryChapter>

    @Query(
        """
        SELECT chapter.id,
               chapter.storyId,
               chapter.volumeId,
               chapter.title,
               LENGTH(chapter.content) AS contentCharacterCount,
               chapter.sortOrder,
               chapter.contentRevision,
               chapter.latestTime
        FROM story_chapters AS chapter
        LEFT JOIN story_volumes AS volume ON volume.id = chapter.volumeId
        WHERE chapter.storyId = :storyId
        ORDER BY CASE WHEN chapter.volumeId IS NULL THEN 0 ELSE 1 END ASC,
                 COALESCE(volume.sortOrder, chapter.sortOrder) ASC,
                 CASE WHEN chapter.volumeId IS NULL THEN 0 ELSE chapter.sortOrder END ASC,
                 chapter.id ASC
        """
    )
    suspend fun getOverviewsByStoryId(storyId: Long): List<StoryChapterOverview>

    @Query(
        """
        SELECT * FROM story_chapters
        WHERE storyId = :storyId
        ORDER BY latestTime DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getLatestByStoryId(storyId: Long): StoryChapter?

    @Query(
        """
        SELECT * FROM story_chapters
        WHERE storyId = :storyId
          AND ((:volumeId IS NULL AND volumeId IS NULL) OR volumeId = :volumeId)
        ORDER BY sortOrder ASC, id ASC
        """
    )
    suspend fun getByContainer(storyId: Long, volumeId: Long?): List<StoryChapter>

    @Query("SELECT COUNT(*) FROM story_chapters WHERE storyId = :storyId")
    suspend fun countByStoryId(storyId: Long): Int

    @Query(
        """
        UPDATE story_chapters
        SET title = :title,
            latestTime = :latestTime
        WHERE id = :id AND storyId = :storyId
        """
    )
    suspend fun rename(
        id: Long,
        storyId: Long,
        title: String,
        latestTime: Long,
    ): Int

    @Query(
        """
        UPDATE story_chapters
        SET content = :content,
            contentRevision = contentRevision + 1,
            latestTime = :latestTime
        WHERE id = :id
          AND storyId = :storyId
          AND contentRevision = :expectedRevision
        """
    )
    suspend fun updateContent(
        id: Long,
        storyId: Long,
        expectedRevision: Long,
        content: String,
        latestTime: Long,
    ): Int

    @Query(
        """
        UPDATE story_chapters
        SET content = :content,
            continuationGuidance = :continuationGuidance,
            contentRevision = contentRevision + 1,
            latestTime = :latestTime
        WHERE id = :id
          AND storyId = :storyId
          AND contentRevision = :expectedRevision
        """
    )
    suspend fun updateDraft(
        id: Long,
        storyId: Long,
        expectedRevision: Long,
        content: String,
        continuationGuidance: String,
        latestTime: Long,
    ): Int

    @Query(
        """
        UPDATE story_chapters
        SET volumeId = :volumeId,
            sortOrder = :sortOrder
        WHERE id = :id AND storyId = :storyId
        """
    )
    suspend fun updateLocation(
        id: Long,
        storyId: Long,
        volumeId: Long?,
        sortOrder: Int,
    ): Int

    @Query(
        """
        UPDATE story_chapters
        SET sortOrder = :sortOrder
        WHERE id = :id AND storyId = :storyId
        """
    )
    suspend fun updateSortOrder(id: Long, storyId: Long, sortOrder: Int): Int

    @Query("DELETE FROM story_chapters WHERE id = :id AND storyId = :storyId")
    suspend fun deleteById(id: Long, storyId: Long): Int
}
