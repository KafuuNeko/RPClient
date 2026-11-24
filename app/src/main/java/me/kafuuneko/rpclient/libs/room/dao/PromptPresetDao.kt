package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.kafuuneko.rpclient.libs.room.entity.PromptPreset
import me.kafuuneko.rpclient.libs.room.entity.PromptPresetEntry
import me.kafuuneko.rpclient.libs.room.model.PromptPresetSummary

/** 自定义预设及差异正文的读写；父表禁止 REPLACE，避免级联删除正文。 */
@Dao
interface PromptPresetDao {
    /** 只观察用户创建的预设，默认预设由 Repository 合成。 */
    @Query("SELECT * FROM prompt_presets ORDER BY updatedAt DESC, id")
    fun observePresets(): Flow<List<PromptPreset>>

    /** 观察用户预设及其覆盖条目数量，覆盖表变更时同步触发列表刷新。 */
    @Query(
        """
        SELECT p.*, COUNT(e.promptKey) AS overrideCount
        FROM prompt_presets AS p
        LEFT JOIN prompt_preset_entries AS e ON e.presetId = p.id
        GROUP BY p.id
        ORDER BY p.updatedAt DESC, p.id
        """
    )
    fun observePresetSummaries(): Flow<List<PromptPresetSummary>>

    /** 读取自定义预设元数据。 */
    @Query("SELECT * FROM prompt_presets WHERE id = :id")
    suspend fun getPreset(id: Long): PromptPreset?

    /** 保留全部覆盖项，包括未来版本的未知键。 */
    @Query("SELECT * FROM prompt_preset_entries WHERE presetId = :id")
    suspend fun getEntries(id: Long): List<PromptPresetEntry>

    /** 新建预设并返回数据库分配的 Long ID。 */
    @Insert
    suspend fun insertPreset(preset: PromptPreset): Long

    /** 复制来源预设的差异项，不展开默认值。 */
    @Insert
    suspend fun insertEntries(entries: List<PromptPresetEntry>)

    /** 保存一项与默认值不同的正文。 */
    @Upsert
    suspend fun saveEntry(entry: PromptPresetEntry)

    /** 恢复默认值时删除覆盖行，后续新默认值自然生效。 */
    @Query("DELETE FROM prompt_preset_entries WHERE presetId = :id AND promptKey = :key")
    suspend fun deleteEntry(id: Long, key: String)

    /** 保存元数据并校验编辑版本，返回零表示发生冲突。 */
    @Query("UPDATE prompt_presets SET name = :name, description = :description, revision = revision + 1, updatedAt = :now WHERE id = :id AND revision = :revision")
    suspend fun updatePreset(
        id: Long,
        revision: Long,
        name: String,
        description: String,
        now: Long
    ): Int

    /** 删除预设时由外键级联删除覆盖项。 */
    @Query("DELETE FROM prompt_presets WHERE id = :id")
    suspend fun deletePreset(id: Long)
}
