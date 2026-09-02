package me.kafuuneko.rpclient.libs.theme

/** 应用可用的配色主题模式，并提供稳定的偏好存储值。 */
enum class AppThemeMode(val persistedValue: String) {
    FollowSystem("system"),
    Light("light"),
    Dark("dark");

    /** 根据当前模式与系统外观判断界面是否应使用暗色配色。 */
    fun resolveDarkTheme(systemInDarkTheme: Boolean): Boolean {
        return when (this) {
            FollowSystem -> systemInDarkTheme
            Light -> false
            Dark -> true
        }
    }

    companion object {
        /** 将持久化值转换为主题模式，未知值安全回退为跟随系统。 */
        fun fromPersistedValue(value: String): AppThemeMode {
            return entries.firstOrNull { it.persistedValue == value } ?: FollowSystem
        }
    }
}
