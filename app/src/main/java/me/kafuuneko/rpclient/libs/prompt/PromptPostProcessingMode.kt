package me.kafuuneko.rpclient.libs.prompt

/**
 * Prompt 发送前的通用后处理模式。
 *
 * 这里处理的是应用内部统一的 System/User/Assistant 消息结构，
 * 之后再交给 OpenAI/Gemini/Anthropic 等协议适配器转换。
 */
enum class PromptPostProcessingMode {
    /** 不改写 messages，保持 PromptBuilder 的原始结构。 */
    None,
    /** 只合并连续相同 role 的消息。 */
    Merge,
    /** 将全部 system 内容合并为开头的一条 system 消息。 */
    SemiStrict,
    /** 在 SemiStrict 基础上，确保聊天正文以 user 消息开始。 */
    Strict,
    /** 将所有 role 展平成一条 user 消息，兼容性最强但结构语义最弱。 */
    SingleUserMessage;

    companion object {
        fun fromOrdinal(value: Int): PromptPostProcessingMode {
            return entries.getOrElse(value) { None }
        }
    }
}
