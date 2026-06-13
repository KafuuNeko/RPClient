package me.kafuuneko.rpclient.libs.room.repository

import kotlinx.coroutines.flow.Flow
import androidx.room.withTransaction
import me.kafuuneko.rpclient.libs.llm.LLMClientFactory
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationResponse
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.prompt.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.withPostProcessedMessages
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.toConfig

internal const val DEFAULT_GEMINI_MODEL = "gemini-3.5-flash"
internal const val DEFAULT_CLAUDE_MODEL = "claude-sonnet-4-6"
internal const val DEFAULT_OPENROUTER_MODEL = "~anthropic/claude-sonnet-latest"

/**
 * 模型供应商配置与生成调用的统一业务入口。
 *
 * 该类负责默认供应商初始化、当前供应商同步和 Prompt 最终化兜底；
 * HTTP 协议细节由 [LLMClientFactory] 创建的适配器承担。
 */
class LLMRepository(
    private val mAppDatabase: AppDatabase,
    private val mLLMClientFactory: LLMClientFactory
) {
    /** 供应商表访问入口，仅在 Repository 内暴露。 */
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
     * 获取当前选中的已启用供应商。
     *
     * 此处刻意不自动回退到其他供应商，避免一次生成请求在用户不知情时切换模型。
     */
    suspend fun getSelectedProvider(): LLMProvider? {
        ensureDefaultProviders()
        val currentId = AppModel.currentLLMProvider
        if (currentId != 0L) {
            mLLMProviderDao.getProviderById(currentId)?.let {
                return it.takeIf { it.isEnabled }
            }
        }
        // 读取阶段不自动回退；新增、删除和启停配置时才由 syncCurrentProvider 修正选择。
        return null
    }

    /**
     * 保存供应商配置。
     */
    suspend fun saveProvider(provider: LLMProvider): Long {
        val now = System.currentTimeMillis()
        val nextProvider = provider.copy(updateTime = now)
        val providerId = if (provider.id == 0L) {
            mLLMProviderDao.insertOrReplace(nextProvider.copy(createTime = now))
        } else {
            mLLMProviderDao.update(nextProvider)
            provider.id
        }
        syncCurrentProvider(preferredProviderId = providerId.takeIf { nextProvider.isEnabled })
        return providerId
    }

    /**
     * 将指定供应商设为当前选中项。
     */
    fun updateCurrentProvider(id: Long) {
        AppModel.currentLLMProvider = id
    }

    /**
     * 启用或停用供应商。
     */
    suspend fun updateProviderEnabled(id: Long, isEnabled: Boolean) {
        mLLMProviderDao.updateProviderEnabled(id, isEnabled)
        syncCurrentProvider(preferredProviderId = id.takeIf { isEnabled })
    }

    /**
     * 删除供应商配置。
     */
    suspend fun deleteProvider(id: Long) {
        mLLMProviderDao.deleteProviderById(id)
        syncCurrentProvider()
    }

    /**
     * 同步当前选中的模型供应商。
     *
     * 当已有当前供应商且仍然启用时，不会覆盖用户选择；仅在当前供应商为空、
     * 已删除或被禁用时，才优先切换到本次启用/保存的供应商，否则回退到第一个已启用供应商。
     */
    private suspend fun syncCurrentProvider(preferredProviderId: Long? = null) {
        preferredProviderId
            ?.let { mLLMProviderDao.getProviderById(it) }
            ?.takeIf { it.isEnabled }
            ?.let { preferredProvider ->
                val currentProvider = AppModel.currentLLMProvider
                    .takeIf { it != 0L }
                    ?.let { mLLMProviderDao.getProviderById(it) }
                    ?.takeIf { it.isEnabled }
                if (currentProvider == null) {
                    AppModel.currentLLMProvider = preferredProvider.id
                    return
                }
            }

        val currentProvider = AppModel.currentLLMProvider
            .takeIf { it != 0L }
            ?.let { mLLMProviderDao.getProviderById(it) }
            ?.takeIf { it.isEnabled }
        if (currentProvider != null) return

        AppModel.currentLLMProvider = mLLMProviderDao.getEnabledProviders().firstOrNull()?.id ?: 0L
    }

    /**
     * 使用指定供应商进行一次性生成。
     */
    suspend fun generate(providerId: Long, request: LLMGenerationRequest): LLMGenerationResponse {
        val provider = mLLMProviderDao.getProviderById(providerId)
            ?: error("LLM provider not found: $providerId")
        return mLLMClientFactory.create(provider.toConfig()).generate(request.postProcessPrompt())
    }

    /**
     * 使用当前选中的供应商进行一次性生成。
     */
    suspend fun generateWithSelectedProvider(request: LLMGenerationRequest): LLMGenerationResponse {
        val provider = getSelectedProvider() ?: error("No enabled LLM provider configured")
        return mLLMClientFactory.create(provider.toConfig()).generate(request.postProcessPrompt())
    }

    /**
     * 使用临时供应商配置进行一次性生成，适合编辑页保存前测试。
     */
    suspend fun generateWithProvider(provider: LLMProvider, request: LLMGenerationRequest): LLMGenerationResponse {
        return mLLMClientFactory.create(provider.toConfig()).generate(request.postProcessPrompt())
    }

    /**
     * 使用指定供应商进行流式生成。
     */
    suspend fun streamGenerate(providerId: Long, request: LLMGenerationRequest): Flow<LLMStreamEvent> {
        val provider = mLLMProviderDao.getProviderById(providerId)
            ?: error("LLM provider not found: $providerId")
        return mLLMClientFactory.create(provider.toConfig()).streamGenerate(request.postProcessPrompt())
    }

    /**
     * 使用当前选中的供应商进行流式生成。
     */
    suspend fun streamGenerateWithSelectedProvider(request: LLMGenerationRequest): Flow<LLMStreamEvent> {
        val provider = getSelectedProvider() ?: error("No enabled LLM provider configured")
        return mLLMClientFactory.create(provider.toConfig()).streamGenerate(request.postProcessPrompt())
    }

    /**
     * 使用临时供应商配置进行流式生成，适合编辑页保存前测试。
     */
    fun streamGenerateWithProvider(provider: LLMProvider, request: LLMGenerationRequest): Flow<LLMStreamEvent> {
        return mLLMClientFactory.create(provider.toConfig()).streamGenerate(request.postProcessPrompt())
    }

    /**
     * 在所有协议适配器之前统一执行 Prompt Post-Processing。
     */
    private fun LLMGenerationRequest.postProcessPrompt(): LLMGenerationRequest {
        if (isPromptFinalized) return this
        return withPostProcessedMessages(
            mode = PromptPostProcessingMode.fromOrdinal(AppModel.promptPostProcessingMode),
            strictPromptPlaceholder = AppModel.newChatPrompt.ifBlank { AppModel.DEFAULT_NEW_CHAT_PROMPT }
        )
    }

    /**
     * 首次启动时写入常用在线模型供应商模板。
     */
    private suspend fun ensureDefaultProviders() {
        if (mLLMProviderDao.getAllProviders().isNotEmpty()) return
        mAppDatabase.withTransaction {
            if (mLLMProviderDao.getAllProviders().isNotEmpty()) return@withTransaction
            mLLMProviderDao.insertOrReplaceAll(createDefaultLLMProviders())
        }
    }
}

/**
 * 默认供应商列表。API Key 留空，用户配置后即可启用真实请求。
 */
internal fun createDefaultLLMProviders(
    now: Long = System.currentTimeMillis()
): List<LLMProvider> {
    return listOf(
        LLMProvider(
            name = "ChatGPT",
            providerType = LLMProviderType.ChatGPT,
            protocol = LLMProviderProtocol.OpenAICompatible,
            baseUrl = "https://api.openai.com/v1",
            model = "gpt-4o-mini",
            createTime = now,
            updateTime = now,
            isEnabled = false
        ),
        LLMProvider(
            name = "Gemini",
            providerType = LLMProviderType.Gemini,
            protocol = LLMProviderProtocol.Gemini,
            baseUrl = "https://generativelanguage.googleapis.com",
            model = DEFAULT_GEMINI_MODEL,
            createTime = now,
            updateTime = now,
            isEnabled = false
        ),
        LLMProvider(
            name = "Claude",
            providerType = LLMProviderType.Claude,
            protocol = LLMProviderProtocol.AnthropicMessages,
            baseUrl = "https://api.anthropic.com",
            model = DEFAULT_CLAUDE_MODEL,
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
            model = DEFAULT_OPENROUTER_MODEL,
            createTime = now,
            updateTime = now,
            isEnabled = false
        )
    )
}
