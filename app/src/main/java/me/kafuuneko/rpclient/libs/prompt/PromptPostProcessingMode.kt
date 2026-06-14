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
    /** 保留开头 system，并将中途 system 在原位置降级为 user。 */
    SemiStrict,
    /** 在 SemiStrict 基础上，确保第一条聊天正文为 user。 */
    Strict,
    /** 将所有 role 转为 user，并把用户或角色名称写入正文。 */
    SingleUserMessage;

    companion object {
        fun fromOrdinal(value: Int): PromptPostProcessingMode {
            return entries.getOrElse(value) { None }
        }
    }
}
