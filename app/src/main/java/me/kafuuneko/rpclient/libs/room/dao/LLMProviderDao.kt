package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

/** LLM Provider 配置的数据库访问接口。 */
@Dao
interface LLMProviderDao : MutableDao<LLMProvider> {
    /** 按创建顺序读取全部 Provider。 */
    @Query("SELECT * FROM llm_providers ORDER BY id ASC")
    suspend fun getAllProviders(): List<LLMProvider>

    /** 读取允许被选择和调用的 Provider。 */
    @Query("SELECT * FROM llm_providers WHERE isEnabled = 1 ORDER BY id ASC")
    suspend fun getEnabledProviders(): List<LLMProvider>

    /** 根据主键读取 Provider。 */
    @Query("SELECT * FROM llm_providers WHERE id = :id")
    suspend fun getProviderById(id: Long): LLMProvider?

    /** 更新 Provider 启停状态。 */
    @Query("UPDATE llm_providers SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun updateProviderEnabled(
        id: Long,
        isEnabled: Boolean
    )

    /** 根据主键删除 Provider。 */
    @Query("DELETE FROM llm_providers WHERE id = :id")
    suspend fun deleteProviderById(id: Long)
}
