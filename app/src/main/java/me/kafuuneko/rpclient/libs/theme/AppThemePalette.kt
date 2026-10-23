package me.kafuuneko.rpclient.libs.theme

/** 与明暗模式独立的配色选择；稳定标识允许新增预设而不破坏旧偏好。 */
enum class AppThemePalette(val persistedValue: String) {
    Classic("classic"),

    Sakura("sakura"),

    Forest("forest"),

    Twilight("twilight"),

    Parchment("parchment"),

    Graphite("graphite"),

    Dynamic("dynamic");

    companion object {
        /** 未知或旧版本缺失的配色回退到经典蓝。 */
        fun fromPersistedValue(value: String): AppThemePalette =
            entries.firstOrNull { it.persistedValue == value } ?: Classic
    }
}
