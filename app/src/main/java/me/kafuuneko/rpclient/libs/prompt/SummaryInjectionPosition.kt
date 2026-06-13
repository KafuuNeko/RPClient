package me.kafuuneko.rpclient.libs.prompt

/** 总结记忆在常规聊天 Prompt 中的注入位置。 */
enum class SummaryInjectionPosition {
    BeforeCharacter,
    AfterCharacter,
    BeforeHistory,
    AfterHistory;

    companion object {
        fun fromOrdinal(value: Int): SummaryInjectionPosition {
            return entries.getOrElse(value) { AfterCharacter }
        }
    }
}
