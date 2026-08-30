package me.kafuuneko.rpclient.libs.room.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import androidx.room.withTransaction
import me.kafuuneko.rpclient.libs.llm.GenerationFailure
import me.kafuuneko.rpclient.libs.llm.LLMClientFactory
import me.kafuuneko.rpclient.libs.llm.LLMProviderRequestException
import me.kafuuneko.rpclient.libs.llm.LLMRequestException
import me.kafuuneko.rpclient.libs.llm.NoEnabledLLMProviderException
import me.kafuuneko.rpclient.libs.llm.RoutingSessionId
import me.kafuuneko.rpclient.libs.llm.classifyGenerationFailure
import me.kafuuneko.rpclient.libs.llm.model.DEFAULT_CLAUDE_REQUEST_BODY_PATCH_JSON
import me.kafuuneko.rpclient.libs.llm.model.DEFAULT_DEEPSEEK_REQUEST_BODY_PATCH_JSON
import me.kafuuneko.rpclient.libs.llm.model.DEFAULT_GEMINI_REQUEST_BODY_PATCH_JSON
import me.kafuuneko.rpclient.libs.llm.model.DEFAULT_GROK_REQUEST_BODY_PATCH_JSON
import me.kafuuneko.rpclient.libs.llm.model.DEFAULT_OPENROUTER_REQUEST_BODY_PATCH_JSON
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationResponse
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.llm.requireNonEmptyContent
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.prompt.DEFAULT_STRICT_PROMPT_PLACEHOLDER
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.withPostProcessedMessages
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.toConfig

internal const val DEFAULT_GEMINI_MODEL = "gemini-3.5-flash"
internal const val DEFAULT_CLAUDE_MODEL = "claude-sonnet-4-6"
internal const val DEFAULT_DEEPSEEK_MODEL = "deepseek-v4-flash"
internal const val DEFAULT_GROK_MODEL = "grok-4.5-latest"
internal const val DEFAULT_OPENROUTER_MODEL = "~anthropic/claude-sonnet-latest"

/**
 * 模型配置与生成调用的统一业务入口。
 *
 * 核心职责：
 * - 初始化默认模型配置并同步当前选择。
 * - 在请求前执行 Prompt 最终化兜底。
 * - 将模型调用内部的未知异常收敛为脱敏请求错误。
 *
 * HTTP 协议细节由 [LLMClientFactory] 创建的适配器承担。
 */
class LLMRepository(
    private val mAppDatabase: AppDatabase,
    private val mLLMClientFactory: LLMClientFactory
) {
    /** 模型配置表访问入口，仅在 Repository 内暴露。 */
    private val mLLMProviderDao = mAppDatabase.getLLMProviderDao()
    private val mCharacterLLMProviderAssociationDao =
        mAppDatabase.getCharacterLLMProviderAssociationDao()

    /**
     * 获取所有模型配置。首次访问时会初始化常见在线模型默认配置。
     */
    suspend fun getAllProviders(): List<LLMProvider> {
        ensureDefaultProviders()
        return mLLMProviderDao.getAllProviders()
    }

    /**
     * 获取已启用的模型配置。
     */
    suspend fun getEnabledProviders(): List<LLMProvider> {
        ensureDefaultProviders()
        return mLLMProviderDao.getEnabledProviders()
    }

    /**
     * 根据 id 获取模型配置。
     */
    suspend fun getProviderById(id: Long): LLMProvider? {
        ensureDefaultProviders()
        return mLLMProviderDao.getProviderById(id)
    }

    /**
     * 获取当前选中的已启用模型配置。
     *
     * 此处刻意不自动回退到其他模型配置，避免一次生成请求在用户不知情时切换模型。
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
     * 保存模型配置。
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
     * 将指定模型配置设为当前选中项。
     */
    fun updateCurrentProvider(id: Long) {
        AppModel.currentLLMProvider = id
    }

    /**
     * 启用或停用模型配置。
     */
    suspend fun updateProviderEnabled(id: Long, isEnabled: Boolean) {
        mLLMProviderDao.updateProviderEnabled(id, isEnabled)
        syncCurrentProvider(preferredProviderId = id.takeIf { isEnabled })
    }

    /** 获取绑定到指定模型配置的角色数量，用于删除前说明影响范围。 */
    suspend fun getCharacterAssociationCount(id: Long): Int {
        return mCharacterLLMProviderAssociationDao.countByLLMProviderId(id)
    }

    /**
     * 删除模型配置，并让所有绑定角色及摘要设置恢复跟随全局模型。
     *
     * 角色关联和模型配置记录必须在同一 Room 事务内删除，避免留下悬空引用；
     * 摘要模型保存在 Kotpref 中，只能在数据库事务成功后单独清理。
     */
    suspend fun deleteProvider(id: Long) {
        mAppDatabase.withTransaction {
            mCharacterLLMProviderAssociationDao.deleteByLLMProviderId(id)
            mLLMProviderDao.deleteProviderById(id)
        }
        AppModel.llmDefaultProvidersInitialized = true
        if (AppModel.summaryLLMProvider == id) {
            AppModel.summaryLLMProvider = 0L
        }
        syncCurrentProvider()
    }

    /**
     * 同步当前选中的模型配置。
     *
     * 当已有当前模型配置且仍然启用时，不会覆盖用户选择；仅在当前模型配置为空、
     * 已删除或被禁用时，才优先切换到本次启用/保存的配置，否则回退到第一个已启用配置。
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
     * 使用指定模型配置进行一次性生成。
     */
    suspend fun generate(providerId: Long, request: LLMGenerationRequest): LLMGenerationResponse {
        val provider = mLLMProviderDao.getProviderById(providerId)
            ?: error("LLM provider not found: $providerId")
        return mLLMClientFactory.create(provider.toConfig()).generate(
            request.postProcessPrompt(provider)
        ).requireNonEmptyContent()
    }

    /**
     * 使用当前选中的模型配置进行一次性生成。
     */
    suspend fun generateWithSelectedProvider(
        request: LLMGenerationRequest,
        routingSessionKey: String? = null
    ): LLMGenerationResponse {
        val provider = getSelectedProvider() ?: throw NoEnabledLLMProviderException()
        return mLLMClientFactory.create(provider.toConfig()).generate(
            request.postProcessPrompt(provider).withRoutingSession(routingSessionKey)
        ).requireNonEmptyContent()
    }

    /** 使用调用方指定的模型配置生成，并可为网关附加稳定的业务会话路由键。 */
    suspend fun generateWithProvider(
        provider: LLMProvider,
        request: LLMGenerationRequest,
        routingSessionKey: String? = null
    ): LLMGenerationResponse {
        return try {
            mLLMClientFactory.create(provider.toConfig()).generate(
                request.postProcessPrompt(provider).withRoutingSession(routingSessionKey)
            ).requireNonEmptyContent()
        } catch (error: Exception) {
            throw error.withProviderRequestContext(provider.name)
        }
    }

    /**
     * 使用指定模型配置进行流式生成。
     */
    suspend fun streamGenerate(providerId: Long, request: LLMGenerationRequest): Flow<LLMStreamEvent> {
        val provider = mLLMProviderDao.getProviderById(providerId)
            ?: error("LLM provider not found: $providerId")
        return mLLMClientFactory.create(provider.toConfig()).streamGenerate(
            request.postProcessPrompt(provider)
        ).requireNonEmptyContent()
    }

    /**
     * 使用当前选中的模型配置进行流式生成。
     */
    suspend fun streamGenerateWithSelectedProvider(
        request: LLMGenerationRequest,
        routingSessionKey: String? = null
    ): Flow<LLMStreamEvent> {
        val provider = getSelectedProvider() ?: throw NoEnabledLLMProviderException()
        return mLLMClientFactory.create(provider.toConfig()).streamGenerate(
            request.postProcessPrompt(provider).withRoutingSession(routingSessionKey)
        ).requireNonEmptyContent()
    }

    /**
     * 使用临时模型配置进行流式生成，适合编辑页保存前测试。
     */
    fun streamGenerateWithProvider(
        provider: LLMProvider,
        request: LLMGenerationRequest,
        routingSessionKey: String? = null
    ): Flow<LLMStreamEvent> {
        return mLLMClientFactory.create(provider.toConfig()).streamGenerate(
            request.postProcessPrompt(provider).withRoutingSession(routingSessionKey)
        ).requireNonEmptyContent().catch { error ->
            if (error is Exception) {
                throw error.withProviderRequestContext(provider.name)
            }
            throw error
        }
    }

    /**
     * 在所有协议适配器之前统一执行 Prompt Post-Processing。
     */
    private fun LLMGenerationRequest.postProcessPrompt(
        provider: LLMProvider
    ): LLMGenerationRequest {
        if (isPromptFinalized) return this
        return withPostProcessedMessages(
            mode = PromptPostProcessingMode.fromOrdinal(
                provider.promptPostProcessingMode
            ),
            strictPromptPlaceholder = DEFAULT_STRICT_PROMPT_PLACEHOLDER
        )
    }

    private fun LLMGenerationRequest.withRoutingSession(
        routingSessionKey: String?
    ): LLMGenerationRequest {
        if (routingSessionKey == null) return this
        return copy(routingSessionId = RoutingSessionId.forConversation(routingSessionKey))
    }

    /**
     * 首次启动时写入常用在线模型配置模板。
     */
    private suspend fun ensureDefaultProviders() {
        if (AppModel.llmDefaultProvidersInitialized) return
        if (mLLMProviderDao.getAllProviders().isNotEmpty()) {
            AppModel.llmDefaultProvidersInitialized = true
            return
        }
        mAppDatabase.withTransaction {
            if (mLLMProviderDao.getAllProviders().isNotEmpty()) return@withTransaction
            mLLMProviderDao.insertOrReplaceAll(createDefaultLLMProviders())
        }
        AppModel.llmDefaultProvidersInitialized = true
    }
}

/** 仅包装尚未分类的模型调用异常，保留网络、HTTP、空响应和取消语义。 */
private fun Exception.asModelRequestException(): Exception {
    return if (classifyGenerationFailure(this) == GenerationFailure.Unknown) {
        LLMRequestException(this)
    } else {
        this
    }
}

/** 为非取消请求错误附加模型配置名称，供页面给出准确修复提示。 */
private fun Exception.withProviderRequestContext(providerName: String): Exception {
    if (this is CancellationException || this is LLMProviderRequestException) return this
    return LLMProviderRequestException(
        providerName = providerName,
        requestCause = asModelRequestException()
    )
}

/**
 * 默认模型配置列表。API Key 留空，用户配置后即可启用真实请求。
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
            requestStreamUsage = true,
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
            requestBodyPatchJson = DEFAULT_GEMINI_REQUEST_BODY_PATCH_JSON,
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
            requestBodyPatchJson = DEFAULT_CLAUDE_REQUEST_BODY_PATCH_JSON,
            sendTopP = false,
            createTime = now,
            updateTime = now,
            isEnabled = false
        ),
        LLMProvider(
            name = "DeepSeek",
            providerType = LLMProviderType.DeepSeek,
            protocol = LLMProviderProtocol.OpenAICompatible,
            baseUrl = "https://api.deepseek.com",
            model = DEFAULT_DEEPSEEK_MODEL,
            requestBodyPatchJson = DEFAULT_DEEPSEEK_REQUEST_BODY_PATCH_JSON,
            createTime = now,
            updateTime = now,
            isEnabled = false
        ),
        LLMProvider(
            name = "Grok",
            providerType = LLMProviderType.Grok,
            protocol = LLMProviderProtocol.OpenAICompatible,
            baseUrl = "https://api.x.ai/v1",
            model = DEFAULT_GROK_MODEL,
            requestBodyPatchJson = DEFAULT_GROK_REQUEST_BODY_PATCH_JSON,
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
            requestBodyPatchJson = DEFAULT_OPENROUTER_REQUEST_BODY_PATCH_JSON,
            createTime = now,
            updateTime = now,
            isEnabled = false
        )
    )
}
