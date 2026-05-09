package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

@Dao
interface LLMProviderDao : MutableDao<LLMProvider> {
    @Query("SELECT * FROM llm_providers ORDER BY isSelected DESC, updateTime DESC, id DESC")
    suspend fun getAllProviders(): List<LLMProvider>

    @Query("SELECT * FROM llm_providers WHERE isEnabled = 1 ORDER BY isSelected DESC, updateTime DESC, id DESC")
    suspend fun getEnabledProviders(): List<LLMProvider>

    @Query("SELECT * FROM llm_providers WHERE id = :id")
    suspend fun getProviderById(id: Long): LLMProvider?

    @Query("SELECT * FROM llm_providers WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedProvider(): LLMProvider?

    @Query("UPDATE llm_providers SET isSelected = 0")
    suspend fun clearSelectedProvider()

    @Query("UPDATE llm_providers SET isSelected = 1, updateTime = :updateTime WHERE id = :id")
    suspend fun selectProvider(id: Long, updateTime: Long = System.currentTimeMillis())

    @Query("UPDATE llm_providers SET isEnabled = :isEnabled, updateTime = :updateTime WHERE id = :id")
    suspend fun updateProviderEnabled(
        id: Long,
        isEnabled: Boolean,
        updateTime: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM llm_providers WHERE id = :id")
    suspend fun deleteProviderById(id: Long)
}
