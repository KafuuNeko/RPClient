package me.kafuuneko.rpclient.libs.room.model

/**
 * 跨单聊与群聊的稳定消息类型编码。
 *
 * - 持久化时使用 [stableCode] 而非 ordinal，避免枚举顺序变化破坏已有数据。
 * - Room Converter 通过 [fromStableCode] 将数据库文本还原为枚举。
 * - 新增成员时只需追加条目和编码，不影响已有持久化值。
 */
enum class MessageType(val stableCode: String) {
    /** 单聊消息。 */
    Single("single"),

    /** 群聊消息。 */
    Group("group");

    companion object {
        /**
         * 从持久化编码恢复枚举值。
         *
         * @param code 数据库中保存的类型编码字符串。
         * @return 对应的 [MessageType]。
         * @throws IllegalArgumentException 编码不匹配任何已知类型时抛出。
         */
        fun fromStableCode(code: String): MessageType =
            entries.firstOrNull { it.stableCode == code }
                ?: throw IllegalArgumentException("未知的消息类型编码: $code")
    }
}
