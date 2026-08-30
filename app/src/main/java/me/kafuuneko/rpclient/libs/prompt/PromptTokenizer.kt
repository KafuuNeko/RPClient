package me.kafuuneko.rpclient.libs.prompt

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingType
import kotlin.math.ceil
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.prompt.model.PromptTokenizerStrategy
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.DEFAULT_TOKEN_ESTIMATE_RESERVE_PERCENT
import me.kafuuneko.rpclient.libs.room.entity.MAX_TOKEN_ESTIMATE_RESERVE_PERCENT
import me.kafuuneko.rpclient.libs.room.entity.MIN_TOKEN_ESTIMATE_RESERVE_PERCENT

/**
 * Prompt Token 统计抽象。
 *
 * 消息统计包含通用聊天模板开销；具体模型服务若无公开编码器，可使用带预留率的代理估算。
 */
interface PromptTokenizer {
    /** 调试界面展示的编码器名称。 */
    val name: String
    /** 当前统计属于模型感知、代理估算还是可证明上界。 */
    val strategy: PromptTokenizerStrategy
    /** 代理估算应用的真实预算预留率；精确实现固定为 0。 */
    val reservePercent: Int
        get() = 0

    /** 统计纯文本 Token 数。 */
    fun countText(text: String): Int

    /** 统计一条消息的角色、正文及固定模板开销。 */
    fun countMessage(message: LLMMessage): Int {
        return MESSAGE_OVERHEAD_TOKENS +
            countText(message.role.name.lowercase()) +
            countText(message.content)
    }

    /** 统计完整消息列表，并预留模型开始回复所需的模板开销。 */
    fun countMessages(messages: List<LLMMessage>): Int {
        if (messages.isEmpty()) return 0
        return messages.sumOf(::countMessage) + RESPONSE_PRIMER_TOKENS
    }

    private companion object {
        const val MESSAGE_OVERHEAD_TOKENS = 3
        const val RESPONSE_PRIMER_TOKENS = 3
    }
}

/** 根据模型配置的协议和模型名称选择 Tokenizer。 */
fun interface PromptTokenizerResolver {
    fun resolve(provider: LLMProvider?): PromptTokenizer
}

/**
 * 内置 Tokenizer 注册表。
 *
 * 已知 OpenAI 模型使用 JTokkit 编码；其他模型族使用接近的 BPE 编码并加入
 * 模型配置的估算预留。这些模型服务未提供适合 Android 离线集成的官方 Tokenizer，
 * 因此调试名称会明确标记 proxy，避免把估算值误解为精确计数。
 */
class PromptTokenizerRegistry : PromptTokenizerResolver {
    private val mEncodingRegistry by lazy { Encodings.newDefaultEncodingRegistry() }
    private val mO200k by lazy {
        JTokkitPromptTokenizer(mEncodingRegistry.getEncoding(EncodingType.O200K_BASE))
    }
    override fun resolve(provider: LLMProvider?): PromptTokenizer {
        val reservePercent = provider?.tokenEstimateReservePercent
            ?.coerceIn(
                MIN_TOKEN_ESTIMATE_RESERVE_PERCENT,
                MAX_TOKEN_ESTIMATE_RESERVE_PERCENT
            )
            ?: DEFAULT_TOKEN_ESTIMATE_RESERVE_PERCENT
        if (provider == null) return estimatedCl100k(reservePercent)
        return resolve(
            model = provider.model,
            protocol = provider.protocol,
            providerType = provider.providerType,
            reservePercent = reservePercent,
            usesOpenAiTokenizer = provider.usesOpenAiTokenizer()
        )
    }

    /**
     * 为实际消耗统计选择 Tokenizer。
     *
     * 用量估算不会应用 Prompt 预算预留率，否则统计值会被人为放大。
     */
    fun resolveForUsage(provider: LLMProviderConfig): PromptTokenizer {
        return resolve(
            model = provider.model,
            protocol = provider.protocol,
            providerType = provider.providerType,
            reservePercent = 0,
            usesOpenAiTokenizer = provider.usesOpenAiTokenizer()
        )
    }

    private fun resolve(
        model: String,
        protocol: LLMProviderProtocol,
        providerType: LLMProviderType,
        reservePercent: Int,
        usesOpenAiTokenizer: Boolean
    ): PromptTokenizer {
        val normalizedModel = model.lowercase()
        // 已知 OpenAI 模型优先使用对应编码器，未知模型族再回退到离线代理
        if (usesOpenAiTokenizer) {
            if (normalizedModel.startsWith("gpt-5") ||
                normalizedModel.startsWith("gpt-4o") ||
                normalizedModel.startsWith("o1") ||
                normalizedModel.startsWith("o3") ||
                normalizedModel.startsWith("o4")
            ) return mO200k
            val encoding = mEncodingRegistry.getEncodingForModel(model)
            if (encoding.isPresent) return JTokkitPromptTokenizer(encoding.get())
        }
        return when {
            protocol == LLMProviderProtocol.Gemini ||
                providerType == LLMProviderType.Gemini ||
                providerType == LLMProviderType.Grok ||
                normalizedModel.contains("gemini") ||
                normalizedModel.contains("gemma") ||
                normalizedModel.contains("grok") -> estimatedO200k(reservePercent)
            protocol == LLMProviderProtocol.AnthropicMessages ||
                providerType == LLMProviderType.Claude ||
                providerType == LLMProviderType.DeepSeek ||
                normalizedModel.contains("claude") ||
                normalizedModel.contains("deepseek") ||
                normalizedModel.contains("qwen") ||
                normalizedModel.contains("llama") ||
                normalizedModel.contains("mistral") ||
                normalizedModel.contains("mixtral") -> estimatedCl100k(reservePercent)
            else -> estimatedCl100k(reservePercent)
        }
    }

    private fun estimatedCl100k(reservePercent: Int): PromptTokenizer {
        return EstimatedBpePromptTokenizer(
            mEncoding = mEncodingRegistry.getEncoding(EncodingType.CL100K_BASE),
            label = "CL100K proxy",
            reservePercent = reservePercent
        )
    }

    private fun estimatedO200k(reservePercent: Int): PromptTokenizer {
        return EstimatedBpePromptTokenizer(
            mEncoding = mEncodingRegistry.getEncoding(EncodingType.O200K_BASE),
            label = "O200K proxy",
            reservePercent = reservePercent
        )
    }

    private fun LLMProvider.usesOpenAiTokenizer(): Boolean {
        if (protocol != LLMProviderProtocol.OpenAICompatible) return false
        return providerType == LLMProviderType.ChatGPT ||
            model.startsWith("gpt-", ignoreCase = true) ||
            model.matches(Regex("""o[134]\b.*""", RegexOption.IGNORE_CASE))
    }

    private fun LLMProviderConfig.usesOpenAiTokenizer(): Boolean {
        if (protocol != LLMProviderProtocol.OpenAICompatible) return false
        return providerType == LLMProviderType.ChatGPT ||
            model.startsWith("gpt-", ignoreCase = true) ||
            model.matches(Regex("""o[134]\b.*""", RegexOption.IGNORE_CASE))
    }
}

private class JTokkitPromptTokenizer(
    private val mEncoding: Encoding
) : PromptTokenizer {
    override val name: String = "JTokkit ${mEncoding.name}"
    override val strategy: PromptTokenizerStrategy = PromptTokenizerStrategy.ModelAware

    override fun countText(text: String): Int {
        if (text.isEmpty()) return 0
        return mEncoding.countTokensOrdinary(text)
    }
}

/** 用可离线运行的 BPE 作为模型族代理，并按真实预算比例加入估算预留。 */
private class EstimatedBpePromptTokenizer(
    private val mEncoding: Encoding,
    label: String,
    override val reservePercent: Int
) : PromptTokenizer {
    override val name: String = label
    override val strategy: PromptTokenizerStrategy = PromptTokenizerStrategy.Estimated

    override fun countText(text: String): Int {
        if (text.isEmpty()) return 0
        val baseTokens = mEncoding.countTokensOrdinary(text)
        return ceil(baseTokens * 100.0 / (100 - reservePercent)).toInt().coerceAtLeast(1)
    }
}
