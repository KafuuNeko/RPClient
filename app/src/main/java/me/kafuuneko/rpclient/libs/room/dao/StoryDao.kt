package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.Story
import me.kafuuneko.rpclient.libs.room.model.StoryOverview

/** Story 表的设置、聚合 revision 和首页轻量查询入口。 */
@Dao
interface StoryDao : MutableDao<Story> {
    @Query(
        """
        SELECT story.id,
               story.title,
               COALESCE((
                   SELECT SUM(LENGTH(chapter.content))
                   FROM story_chapters AS chapter
                   WHERE chapter.storyId = story.id
               ), 0) AS contentCharacterCount,
               COALESCE((
                   SELECT SUBSTR(
                       REPLACE(REPLACE(TRIM(chapter.content), CHAR(13), ' '), CHAR(10), ' '),
                       1,
                       160
                   )
                   FROM story_chapters AS chapter
                   LEFT JOIN story_volumes AS volume ON volume.id = chapter.volumeId
                   WHERE chapter.storyId = story.id
                     AND TRIM(chapter.content) != ''
                   ORDER BY CASE WHEN chapter.volumeId IS NULL THEN 0 ELSE 1 END ASC,
                            COALESCE(volume.sortOrder, chapter.sortOrder) ASC,
                            CASE WHEN chapter.volumeId IS NULL THEN 0 ELSE chapter.sortOrder END ASC,
                            chapter.id ASC
                   LIMIT 1
               ), '') AS preview,
               story.latestTime
        FROM stories AS story
        ORDER BY story.latestTime DESC, story.id DESC
        """
    )
    suspend fun getStoryOverviews(): List<StoryOverview>

    @Query("SELECT * FROM stories WHERE id = :id")
    suspend fun getStory(id: Long): Story?

    @Query(
        """
        UPDATE stories
        SET title = :title,
            revision = revision + 1,
            latestTime = :latestTime
        WHERE id = :id
          AND revision = :expectedRevision
        """
    )
    suspend fun renameStory(
        id: Long,
        expectedRevision: Long,
        title: String,
        latestTime: Long,
    ): Int

    @Query(
        """
        UPDATE stories
        SET memory = :memory,
            summary = :summary,
            authorNote = :authorNote,
            includeUserPersona = :includeUserPersona,
            revision = revision + 1,
            latestTime = :latestTime
        WHERE id = :id
          AND revision = :expectedRevision
        """
    )
    suspend fun updateStorySettings(
        id: Long,
        expectedRevision: Long,
        memory: String,
        summary: String,
        authorNote: String,
        includeUserPersona: Boolean,
        latestTime: Long,
    ): Int

    @Query(
        """
        UPDATE stories
        SET summary = :summary,
            revision = revision + 1,
            latestTime = :latestTime
        WHERE id = :storyId
          AND revision = :expectedStoryRevision
        """
    )
    suspend fun updateSummary(
        storyId: Long,
        expectedStoryRevision: Long,
        summary: String,
        latestTime: Long,
    ): Int

    @Query(
        """
        UPDATE stories
        SET revision = revision + 1,
            latestTime = :latestTime
        WHERE id = :storyId
          AND revision = :expectedRevision
        """
    )
    suspend fun advanceRevision(
        storyId: Long,
        expectedRevision: Long,
        latestTime: Long,
    ): Int

    @Query(
        """
        UPDATE stories
        SET revision = revision + 1,
            worldInfoGenerationStep = :worldInfoGenerationStep,
            latestTime = :latestTime
        WHERE id = :storyId
          AND revision = :expectedRevision
        """
    )
    suspend fun updateGenerationState(
        storyId: Long,
        expectedRevision: Long,
        worldInfoGenerationStep: Int,
        latestTime: Long,
    ): Int

    @Query("DELETE FROM stories WHERE id = :id")
    suspend fun deleteStory(id: Long): Int
}
