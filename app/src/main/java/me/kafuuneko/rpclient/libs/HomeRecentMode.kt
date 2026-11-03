package me.kafuuneko.rpclient.libs

/** 首页最近区域的浏览方式，整数编码独立于枚举声明顺序。 */
enum class HomeRecentMode(val persistedValue: Int) {
    RecentContent(0),
    RecentCharacters(1);

    companion object {
        /**
         * 将持久化整数解析为首页显示模式。
         *
         * @param value 已保存的模式编码。
         * @return 对应模式，未知值默认返回最近内容。
         */
        fun fromPersistedValue(value: Int): HomeRecentMode =
            entries.firstOrNull { it.persistedValue == value } ?: RecentContent
    }
}
