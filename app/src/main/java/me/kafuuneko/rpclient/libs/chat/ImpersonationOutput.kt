package me.kafuuneko.rpclient.libs.chat

/** 扮演用户的输出位置，持久化使用稳定键而非枚举下标。 */
enum class ImpersonationOutput(val key: String) {
    Message("message"),
    Draft("draft");

    companion object {
        /** 兼容未配置及未知偏好，继续使用原有消息模式。 */
        fun fromKey(key: String): ImpersonationOutput =
            entries.firstOrNull { it.key == key } ?: Message
    }
}
