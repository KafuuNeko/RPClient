package me.kafuuneko.rpclient.libs.prompt

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingType
import java.nio.charset.StandardCharsets
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

/**
 * Prompt Token 统计抽象。
 *
 * 消息统计包含通用聊天模板开销；具体供应商若无公开编码器，可实现保守上界策略。
 */
interface PromptTokenizer {
    /** 调试界面展示的编码器名称。 */
    val name: String
    /** 当前统计属于模型感知还是保守上界。 */
    val strategy: PromptTokenizerStrategy

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

/** 根据供应商协议和模型名称选择 Tokenizer。 */
fun interface PromptTokenizerResolver {
    fun resolve(provider: LLMProvider?): PromptTokenizer
}

/**
 * 内置 Tokenizer 注册表。
 *
 * 已知 OpenAI 模型使用 JTokkit 精确编码；其他协议使用 UTF-8 字节数作为安全上界，
 * 避免因套用错误编码器而低估上下文。
 */
class PromptTokenizerRegistry : PromptTokenizerResolver {
    private val mEncodingRegistry by lazy { Encodings.newDefaultEncodingRegistry() }
    private val mCl100k by lazy {
        JTokkitPromptTokenizer(mEncodingRegistry.getEncoding(EncodingType.CL100K_BASE))
    }
    private val mO200k by lazy {
        JTokkitPromptTokenizer(mEncodingRegistry.getEncoding(EncodingType.O200K_BASE))
    }
    private val mConservative = Utf8UpperBoundPromptTokenizer()

    override fun resolve(provider: LLMProvider?): PromptTokenizer {
        if (provider == null || !provider.usesOpenAiTokenizer()) return mConservative
        val model = provider.model.lowercase()
        return when {
            model.startsWith("gpt-5") ||
                model.startsWith("gpt-4o") ||
                model.startsWith("o1") ||
                model.startsWith("o3") ||
                model.startsWith("o4") -> mO200k
            else -> mEncodingRegistry.getEncodingForModel(provider.model)
                .map(::JTokkitPromptTokenizer)
                .orElse(mCl100k)
        }
    }

    private fun LLMProvider.usesOpenAiTokenizer(): Boolean {
        if (protocol != LLMProviderProtocol.OpenAICompatible) return false
        return providerType == LLMProviderType.ChatGPT ||
            model.startsWith("gpt-", ignoreCase = true) ||
            model.matches(Regex("""o[134]\b.*""", RegexOption.IGNORE_CASE))
    }
}

private class JTokkitPromptTokenizer(
    private val encoding: Encoding
) : PromptTokenizer {
    override val name: String = "JTokkit ${encoding.name}"
    override val strategy: PromptTokenizerStrategy = PromptTokenizerStrategy.ModelAware

    override fun countText(text: String): Int {
        if (text.isEmpty()) return 0
        return encoding.countTokensOrdinary(text)
    }
}

private class Utf8UpperBoundPromptTokenizer : PromptTokenizer {
    override val name: String = "UTF-8 byte upper bound"
    override val strategy: PromptTokenizerStrategy = PromptTokenizerStrategy.Conservative

    override fun countText(text: String): Int {
        return text.toByteArray(StandardCharsets.UTF_8).size
    }

    override fun countMessage(message: LLMMessage): Int {
        val adapterSystemPrefixReserve = if (message.role == LLMMessageRole.System) {
            SYSTEM_PREFIX_RESERVE
        } else {
            0
        }
        return super.countMessage(message) + adapterSystemPrefixReserve
    }

    private companion object {
        // Gemini/Anthropic adapters may serialize an in-history system message as "[System]\n...".
        const val SYSTEM_PREFIX_RESERVE = 16
    }
}
