package me.kafuuneko.rpclient

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import org.koin.core.component.KoinComponent

/** 对 Android Context 常用能力的轻量封装，供 ViewModel 和 Compose 页面复用。 */
class AppLibs(
    private val mContext: Context
) : KoinComponent {
    /** 读取支持格式化参数的字符串资源。 */
    fun getString(@StringRes id: Int, vararg args: Any): String {
        return mContext.resources?.getString(id, *args).toString()
    }

    /** 获取当前安装包版本名，缺失时返回本地化占位文本。 */
    fun getVersionName(): String {
        return mContext.packageManager.getPackageInfo(mContext.packageName, 0)
            .versionName ?: getString(R.string.unknown_version)
    }

    /** 使用系统可处理 ACTION_VIEW 的应用打开外部链接。 */
    fun jumpToUrl(url: String) {
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }.also {
            mContext.startActivity(it)
        }
    }
}
