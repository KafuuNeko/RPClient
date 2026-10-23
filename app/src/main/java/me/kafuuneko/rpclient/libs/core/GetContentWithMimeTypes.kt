package me.kafuuneko.rpclient.libs.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

/**
 * 通过 ACTION_GET_CONTENT 选择一次性导入内容，同时支持多个 MIME 类型过滤条件。
 *
 * 返回的 URI 只用于当前导入流程，不请求跨重启持久访问权限。
 */
internal class GetContentWithMimeTypes : ActivityResultContract<Array<String>, Uri?>() {
    private val mDelegate = ActivityResultContracts.GetContent()

    override fun createIntent(context: Context, input: Array<String>): Intent {
        val mimeTypes = input
            .filter(String::isNotBlank)
            .distinct()
        require(mimeTypes.isNotEmpty()) {
            "At least one MIME type is required"
        }
        return mDelegate.createIntent(
            context = context,
            input = mimeTypes.singleOrNull() ?: "*/*",
        ).apply {
            if (mimeTypes.size > 1) {
                putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
            }
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return mDelegate.parseResult(resultCode, intent)
    }
}
