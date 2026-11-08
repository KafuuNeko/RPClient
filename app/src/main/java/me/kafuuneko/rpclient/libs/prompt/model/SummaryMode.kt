package me.kafuuneko.rpclient.libs.prompt.model

/**
 * 单聊与群聊摘要的历史选择策略。
 *
 * - Raw 从已有摘要之后按顺序处理连续历史，保留尚未处理的积压。
 * - Classic 使用普通聊天上下文生成近期记忆，允许越过被预算裁剪的旧历史。
 * - 持久化值独立于枚举顺序，旧安装默认沿用 Raw。
 */
enum class SummaryMode(val persistedValue: Int) {
    Raw(0),

    Classic(1);

    companion object {
        /**
         * 从偏好值恢复模式，未知值沿用原有增量策略。
         * @param value 已保存的模式值。
         * @return 已识别的模式，缺省为 [Raw]。
         */
        fun fromPersistedValue(value: Int): SummaryMode =
            entries.firstOrNull { it.persistedValue == value } ?: Raw
    }
}
