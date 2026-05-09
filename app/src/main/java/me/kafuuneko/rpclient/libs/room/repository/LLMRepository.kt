package me.kafuuneko.rpclient.libs.room.repository

import kotlinx.coroutines.flow.Flow
import androidx.room.withTransaction
import me.kafuuneko.rpclient.libs.llm.LLMClientFactory
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationResponse
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

class LLMRepository(
    private val mAppDatabase: AppDatabase,
    private val mLLMClientFactory: LLMClientFactory
) {
    private val mLLMProviderDao = mAppDatabase.getLLMProviderDao()

    /**
     * 获取所有模型供应商配置。首次访问时会初始化常见在线模型默认配置。
     */
    suspend fun getAllProviders(): List<LLMProvider> {
        ensureDefaultProviders()
        return mLLMProviderDao.getAllProviders()
    }

    /**
     * 获取已启用的模型供应商配置。
     */
    suspend fun getEnabledProviders(): List<LLMProvider> {
        ensureDefaultProviders()
        return mLLMProviderDao.getEnabledProviders()
    }

    /**
     * 根据 id 获取供应商配置。
     */
    suspend fun getProviderById(id: Long): LLMProvider? {
        ensureDefaultProviders()
        return mLLMProviderDao.getProviderById(id)
    }

    /**
     * 获取当前选中的供应商；没有显式选中时回退到第一个已启用供应商。
     */
    suspend fun getSelectedProvider(): LLMProvider? {
        ensureDefaultProviders()
        return mLLMProviderDao.getSelectedProvider() ?: mLLMProviderDao.getEnabledProviders().firstOrNull()
    }

    /**
     * 保存供应商配置。
     */
    suspend fun saveProvider(provider: LLMProvider): Long {
        val now = System.currentTimeMillis()
        val nextProvider = provider.copy(updateTime = now)
        return if (provider.id == 0L) {
            mLLMProviderDao.insertOrReplace(nextProvider.copy(createTime = now))
        } else {
            mLLMProviderDao.update(nextProvider)
            provider.id
        }
    }

    /**
     * 将指定供应商设为当前选中项。
     */
    suspend fun selectProvider(id: Long) {
        mAppDatabase.withTransaction {
            mLLMProviderDao.clearSelectedProvider()
            mLLMProviderDao.selectProvider(id)
        }
    }

    /**
     * 启用或停用供应商。
     */
    suspend fun updateProviderEnabled(id: Long, isEnabled: Boolean) {
        mLLMProviderDao.updateProviderEnabled(id, isEnabled)
    }

    /**
     * 删除供应商配置。
     */
    suspend fun deleteProvider(id: Long) {
        mLLMProviderDao.deleteProviderById(id)
    }

    /**
     * 使用指定供应商进行一次性生成。
     */
    suspend fun generate(providerId: Long, request: LLMGenerationRequest): LLMGenerationResponse {
        val provider = mLLMProviderDao.getProviderById(providerId)
            ?: error("LLM provider not found: $providerId")
        return mLLMClientFactory.create(provider).generate(request)
    }

    /**
     * 使用当前选中的供应商进行一次性生成。
     */
    suspend fun generateWithSelectedProvider(request: LLMGenerationRequest): LLMGenerationResponse {
        val provider = getSelectedProvider() ?: error("No enabled LLM provider configured")
        return mLLMClientFactory.create(provider).generate(request)
    }

    /**
     * 使用临时供应商配置进行一次性生成，适合编辑页保存前测试。
     */
    suspend fun generateWithProvider(provider: LLMProvider, request: LLMGenerationRequest): LLMGenerationResponse {
        return mLLMClientFactory.create(provider).generate(request)
    }

    /**
     * 使用指定供应商进行流式生成。
     */
    suspend fun streamGenerate(providerId: Long, request: LLMGenerationRequest): Flow<LLMStreamEvent> {
        val provider = mLLMProviderDao.getProviderById(providerId)
            ?: error("LLM provider not found: $providerId")
        return mLLMClientFactory.create(provider).streamGenerate(request)
    }

    /**
     * 使用当前选中的供应商进行流式生成。
     */
    suspend fun streamGenerateWithSelectedProvider(request: LLMGenerationRequest): Flow<LLMStreamEvent> {
        val provider = getSelectedProvider() ?: error("No enabled LLM provider configured")
        return mLLMClientFactory.create(provider).streamGenerate(request)
    }

    /**
     * 使用临时供应商配置进行流式生成，适合编辑页保存前测试。
     */
    fun streamGenerateWithProvider(provider: LLMProvider, request: LLMGenerationRequest): Flow<LLMStreamEvent> {
        return mLLMClientFactory.create(provider).streamGenerate(request)
    }

    /**
     * 首次启动时写入常用在线模型供应商模板。
     */
    private suspend fun ensureDefaultProviders() {
        if (mLLMProviderDao.getAllProviders().isNotEmpty()) return
        mAppDatabase.withTransaction {
            if (mLLMProviderDao.getAllProviders().isNotEmpty()) return@withTransaction
            mLLMProviderDao.insertOrReplaceAll(defaultProviders())
        }
    }

    /**
     * 默认供应商列表。API Key 留空，用户配置后即可启用真实请求。
     */
    private fun defaultProviders(): List<LLMProvider> {
        val now = System.currentTimeMillis()
        return listOf(
            LLMProvider(
                name = "ChatGPT",
                providerType = LLMProviderType.ChatGPT,
                protocol = LLMProviderProtocol.OpenAICompatible,
                baseUrl = "https://api.openai.com/v1",
                model = "gpt-4o-mini",
                isSelected = true,
                createTime = now,
                updateTime = now,
                isEnabled = false
            ),
            LLMProvider(
                name = "Gemini",
                providerType = LLMProviderType.Gemini,
                protocol = LLMProviderProtocol.Gemini,
                baseUrl = "https://generativelanguage.googleapis.com",
                model = "gemini-1.5-flash",
                createTime = now,
                updateTime = now,
                isEnabled = false
            ),
            LLMProvider(
                name = "Claude",
                providerType = LLMProviderType.Claude,
                protocol = LLMProviderProtocol.AnthropicMessages,
                baseUrl = "https://api.anthropic.com",
                model = "claude-3-5-sonnet-latest",
                createTime = now,
                updateTime = now,
                isEnabled = false
            ),
            LLMProvider(
                name = "DeepSeek",
                providerType = LLMProviderType.DeepSeek,
                protocol = LLMProviderProtocol.OpenAICompatible,
                baseUrl = "https://api.deepseek.com",
                model = "deepseek-chat",
                createTime = now,
                updateTime = now,
                isEnabled = false
            ),
            LLMProvider(
                name = "OpenRouter",
                providerType = LLMProviderType.OpenRouter,
                protocol = LLMProviderProtocol.OpenAICompatible,
                baseUrl = "https://openrouter.ai/api/v1",
                model = "anthropic/claude-3.5-sonnet",
                createTime = now,
                updateTime = now,
                isEnabled = false
            )
        )
    }
}
