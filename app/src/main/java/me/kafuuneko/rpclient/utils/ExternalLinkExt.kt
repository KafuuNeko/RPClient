package me.kafuuneko.rpclient.utils

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import me.kafuuneko.rpclient.R

/**
 * 安全打开外部链接；若系统缺少可用浏览器或目标应用，则降级复制链接到剪贴板并提示。
 *
 * @param url 待打开的完整外部地址。
 * @param clipboardLabel 降级复制时写入剪贴板的标签文案。
 */
fun Context.safeOpenExternalUrl(
    url: String,
    clipboardLabel: String = getString(R.string.app_name),
) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(clipboardLabel, url))
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }
}
