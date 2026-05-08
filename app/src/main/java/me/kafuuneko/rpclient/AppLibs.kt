package me.kafuuneko.rpclient

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import org.koin.core.component.KoinComponent

class AppLibs(
    private val mContext: Context
) : KoinComponent {
    fun getString(@StringRes id: Int, vararg args: Any): String {
        return mContext.resources?.getString(id, *args).toString()
    }

    fun getVersionName(): String {
        return mContext.packageManager.getPackageInfo(mContext.packageName, 0)
            .versionName ?: getString(R.string.unknown_version)
    }

    fun jumpToUrl(url: String) {
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }.also {
            mContext.startActivity(it)
        }
    }
}