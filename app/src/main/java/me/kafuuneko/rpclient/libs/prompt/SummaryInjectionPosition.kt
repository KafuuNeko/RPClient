package me.kafuuneko.rpclient.libs.prompt

/**
 * 摘要在 Prompt 中的注入位置。
 *
 * [persistedValue] 使用独立稳定值，避免调整枚举顺序后误读已保存的用户设置。
 */
enum class SummaryInjectionPosition(val persistedValue: Int) {
    None(-1),
    BeforeMain(10),
    AfterMain(11),
    InChat(12);

    companion object {
        /**
         * 读取当前设置值，并兼容旧版本的四种角色卡/聊天历史相对位置。
         */
        fun fromPersistedValue(value: Int): SummaryInjectionPosition {
            return when (value) {
                None.persistedValue -> None
                BeforeMain.persistedValue -> BeforeMain
                AfterMain.persistedValue -> AfterMain
                InChat.persistedValue -> InChat
                0 -> BeforeMain
                1 -> AfterMain
                2, 3 -> InChat
                else -> AfterMain
            }
        }
    }
}
