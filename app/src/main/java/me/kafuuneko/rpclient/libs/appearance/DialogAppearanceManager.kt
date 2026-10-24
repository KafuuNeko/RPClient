package me.kafuuneko.rpclient.libs.appearance

import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.kafuuneko.rpclient.libs.AppModel

/** 对话框外观偏好管理器；单个轻量配置一次写入，已打开的页面通过状态流更新。 */
class DialogAppearanceManager(private val mGson: Gson) {
    private val mSettings = MutableStateFlow(readSettings())

    /** 已提交的全局对话框参数；编辑草稿不发布到其他页面。 */
    val settings = mSettings.asStateFlow()

    /** 保存经校验的配置后再发布，取消编辑不会影响当前弹窗。 */
    fun save(settings: DialogAppearance) {
        val normalized = settings.normalized()
        AppModel.dialogAppearance = mGson.toJson(normalized)
        mSettings.value = normalized
    }

    /** 缺失或损坏的偏好回退默认值，不影响其他主题设置。 */
    private fun readSettings(): DialogAppearance = runCatching {
        mGson.fromJson(AppModel.dialogAppearance, DialogAppearance::class.java)?.normalized()
    }.getOrNull() ?: DialogAppearance()
}
