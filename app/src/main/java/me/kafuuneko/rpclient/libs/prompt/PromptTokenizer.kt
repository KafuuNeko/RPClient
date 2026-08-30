package me.kafuuneko.rpclient.libs.prompt

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingType
import kotlin.math.ceil
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.llm.model.LocalTokenEstimatorType
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

/** 根据模型配置的手动选择或自动规则表解析 Tokenizer。 */
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
    /**
     * 自动模式的有序匹配表。
     *
     * 规则顺序属于兼容行为：已知 OpenAI 编码优先于模型族代理，末项负责统一回退。
     * 新增自动识别规则时必须集中添加到此表，不能在调用方另设模型判断分支。
     */
    private val mAutomaticEstimatorMap by lazy {
        val openAiReasoningModelPattern = Regex("""o[134]\b.*""")
        val o200kOpenAiPrefixes = listOf("gpt-5", "gpt-4o", "o1", "o3", "o4")
        val o200kProxyMarkers = listOf("gemini", "gemma", "grok")
        val isOpenAiModel = { context: AutomaticEstimatorContext ->
            context.protocol == LLMProviderProtocol.OpenAICompatible &&
                (context.providerType == LLMProviderType.ChatGPT ||
                    context.normalizedModel.startsWith("gpt-") ||
                    context.normalizedModel.matches(openAiReasoningModelPattern))
        }
        linkedMapOf(
            AutomaticEstimatorMatcher { context ->
                isOpenAiModel(context) &&
                    o200kOpenAiPrefixes.any(context.normalizedModel::startsWith)
            } to AutomaticEstimatorFactory { _, _ -> mO200k },
            AutomaticEstimatorMatcher { context ->
                isOpenAiModel(context) && context.registeredEncoding != null
            } to AutomaticEstimatorFactory { context, _ ->
                JTokkitPromptTokenizer(requireNotNull(context.registeredEncoding))
            },
            AutomaticEstimatorMatcher { context ->
                    context.protocol == LLMProviderProtocol.Gemini ||
                    context.providerType == LLMProviderType.Gemini ||
                    context.providerType == LLMProviderType.Grok ||
                    o200kProxyMarkers.any(context.normalizedModel::contains)
            } to AutomaticEstimatorFactory { _, reservePercent ->
                estimatedO200k(reservePercent)
            },
            AutomaticEstimatorMatcher { true } to
                AutomaticEstimatorFactory { _, reservePercent ->
                    estimatedCl100k(reservePercent)
                }
        )
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
            estimatorType = provider.localTokenEstimatorType
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
            estimatorType = provider.localTokenEstimatorType
        )
    }

    private fun resolve(
        model: String,
        protocol: LLMProviderProtocol,
        providerType: LLMProviderType,
        reservePercent: Int,
        estimatorType: LocalTokenEstimatorType
    ): PromptTokenizer {
        return when (estimatorType) {
            // 手动选择绕过自动匹配表，保证未知模型别名稳定复用指定编码。
            LocalTokenEstimatorType.Cl100kBase -> estimatedCl100k(reservePercent)
            LocalTokenEstimatorType.O200kBase -> estimatedO200k(reservePercent)
            LocalTokenEstimatorType.Automatic -> resolveAutomatic(
                model = model,
                protocol = protocol,
                providerType = providerType,
                reservePercent = reservePercent
            )
        }
    }

    /** 使用唯一的有序规则表解析自动模式，避免 Entity 与运行时配置产生不同判断。 */
    private fun resolveAutomatic(
        model: String,
        protocol: LLMProviderProtocol,
        providerType: LLMProviderType,
        reservePercent: Int
    ): PromptTokenizer {
        val context = AutomaticEstimatorContext(
            normalizedModel = model.lowercase(),
            protocol = protocol,
            providerType = providerType,
            registeredEncoding = mEncodingRegistry.getEncodingForModel(model).orElse(null)
        )
        val factory = mAutomaticEstimatorMap.entries
            .first { (matcher, _) -> matcher.matches(context) }
            .value
        return factory.create(context, reservePercent)
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

}

/** 自动模式进行有序匹配所需的无敏感信息上下文。 */
private data class AutomaticEstimatorContext(
    val normalizedModel: String,
    val protocol: LLMProviderProtocol,
    val providerType: LLMProviderType,
    val registeredEncoding: Encoding?
)

/** 自动模式规则表的匹配条件。 */
private fun interface AutomaticEstimatorMatcher {
    fun matches(context: AutomaticEstimatorContext): Boolean
}

/** 自动模式规则表命中后创建实际 Tokenizer。 */
private fun interface AutomaticEstimatorFactory {
    fun create(context: AutomaticEstimatorContext, reservePercent: Int): PromptTokenizer
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
