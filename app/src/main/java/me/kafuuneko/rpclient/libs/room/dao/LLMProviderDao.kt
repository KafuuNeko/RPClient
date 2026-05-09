package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

@Dao
interface LLMProviderDao : MutableDao<LLMProvider> {
    @Query("SELECT * FROM llm_providers ORDER BY id ASC")
    suspend fun getAllProviders(): List<LLMProvider>

    @Query("SELECT * FROM llm_providers WHERE isEnabled = 1 ORDER BY id ASC")
    suspend fun getEnabledProviders(): List<LLMProvider>

    @Query("SELECT * FROM llm_providers WHERE id = :id")
    suspend fun getProviderById(id: Long): LLMProvider?

    @Query("UPDATE llm_providers SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun updateProviderEnabled(
        id: Long,
        isEnabled: Boolean
    )

    @Query("DELETE FROM llm_providers WHERE id = :id")
    suspend fun deleteProviderById(id: Long)
}
