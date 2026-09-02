package me.kafuuneko.rpclient.libs.theme

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.kafuuneko.rpclient.libs.AppModel

/**
 * 应用配色主题管理器。
 *
 * - 统一读写轻量主题偏好，避免 Compose 或 Activity 直接访问 [AppModel]。
 * - 通过进程级状态流让当前活动页面即时响应主题切换。
 */
class AppThemeManager {
    private val mThemeModeFlow = MutableStateFlow(readThemeMode())

    /** 当前生效的主题选择，供页面宿主与设置状态观察。 */
    val themeModeFlow = mThemeModeFlow.asStateFlow()

    /** 持久化新的主题选择，并通知当前活动页面刷新配色。 */
    fun setThemeMode(themeMode: AppThemeMode) {
        AppModel.themeMode = themeMode.persistedValue
        mThemeModeFlow.value = themeMode
    }

    /** 读取持久化主题；存储异常或旧版本未知值均回退为跟随系统。 */
    private fun readThemeMode(): AppThemeMode {
        val persistedValue = runCatching { AppModel.themeMode }
            .getOrDefault(AppThemeMode.FollowSystem.persistedValue)
        return AppThemeMode.fromPersistedValue(persistedValue)
    }
}
