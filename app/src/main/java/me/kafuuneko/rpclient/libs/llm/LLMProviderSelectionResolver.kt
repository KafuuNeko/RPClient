package me.kafuuneko.rpclient.libs.llm

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository

/** 显式模型配置不可用时，用于区分角色回复和摘要两类可行动错误。 */
class UnavailableLLMProviderSelectionException(
    val scope: LLMProviderSelectionScope,
    val providerId: Long,
    val providerName: String? = null
) : IllegalStateException("Selected LLM provider is unavailable")

/** 模型配置选择的业务作用域。 */
enum class LLMProviderSelectionScope {
    Character,

    Summary
}

/**
 * 统一解析一次生成应使用的模型配置快照。
 *
 * 显式绑定的配置被停用或丢失时不会静默切换模型；调用方应终止本次生成并展示安全提示。
 * 返回的 Room 实体快照应同时用于 Prompt 预算和最终网络请求，避免生成期间设置变化导致错配。
 */
class LLMProviderSelectionResolver(
    private val mLLMRepository: LLMRepository,
    private val mCharacterRepository: CharacterRepository
) {
    /** 解析当前全局模型配置。 */
    suspend fun requireDefaultProvider(): LLMProvider {
        return mLLMRepository.getSelectedProvider() ?: throw NoEnabledLLMProviderException()
    }

    /** 解析角色绑定配置；未绑定时跟随当前全局配置。 */
    suspend fun requireCharacterProvider(character: Character): LLMProvider {
        val providerId = getCharacterProviderId(character)
        if (providerId == 0L) return requireDefaultProvider()
        return requireExplicitProvider(providerId, LLMProviderSelectionScope.Character)
    }

    /**
     * 为不发起请求的角色文本宏解析模型；没有可用配置时返回 null，不阻止创建会话。
     */
    suspend fun getCharacterProviderOrNull(character: Character): LLMProvider? {
        val providerId = getCharacterProviderId(character)
        if (providerId == 0L) return mLLMRepository.getSelectedProvider()
        return mLLMRepository.getProviderById(providerId)?.takeIf { it.isEnabled }
    }

    /** 解析摘要专用配置；未单独设置时跟随当前全局配置。 */
    suspend fun requireSummaryProvider(): LLMProvider {
        val providerId = AppModel.summaryLLMProvider
        if (providerId == 0L) return requireDefaultProvider()
        return requireExplicitProvider(providerId, LLMProviderSelectionScope.Summary)
    }

    private suspend fun requireExplicitProvider(
        providerId: Long,
        scope: LLMProviderSelectionScope,
    ): LLMProvider {
        val provider = mLLMRepository.getProviderById(providerId)
        return provider?.takeIf { it.isEnabled }
            ?: throw UnavailableLLMProviderSelectionException(
                scope = scope,
                providerId = providerId,
                providerName = provider?.name,
            )
    }

    private suspend fun getCharacterProviderId(character: Character): Long {
        if (character.id == 0L) return 0L
        return mCharacterRepository.getLLMProviderId(character.id)
    }
}
