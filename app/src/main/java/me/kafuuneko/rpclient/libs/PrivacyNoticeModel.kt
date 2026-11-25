package me.kafuuneko.rpclient.libs

import com.chibatching.kotpref.KotprefModel

/**
 * 隐私与数据说明的独立持久偏好模型（独立 SharedPreferences 文件）。
 *
 * 核心职责：
 * - 独立维护隐私协议已展示版本号，与常规业务偏好解耦；
 * - 判定启动时是否需要展示隐私与数据说明弹窗，并在展示后立即同步写入最新版本号。
 */
object PrivacyNoticeModel : KotprefModel() {
    /** 独立 SharedPreferences 文件名，避免与 AppModel 业务配置混存。 */
    override val kotprefName: String = "privacy_notice"

    /** 当前生效的隐私与数据说明版本号。 */
    const val CURRENT_VERSION = 1

    /** 已向当前设备展示过的隐私协议版本号；0 表示从未展示。 */
    var shownVersion by intPref(default = 0, commitByDefault = true)

    /**
     * 判断当前记录的版本号是否与最新隐私协议版本不一致。
     *
     * @return 当已记录版本不符合 [CURRENT_VERSION] 时返回 true。
     */
    fun shouldShowNotice(): Boolean = shownVersion != CURRENT_VERSION

    /** 将已展示的隐私协议版本号立即同步写入为当前最新版本。 */
    fun markCurrentVersionShown() {
        shownVersion = CURRENT_VERSION
    }
}
