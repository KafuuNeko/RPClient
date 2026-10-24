package me.kafuuneko.rpclient.libs.json

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

/** 按准备结果为合法 JSON 和原文降级选择正确的 MIME。 */
class CreateJsonDocumentContract : ActivityResultContract<PreparedJsonExport, Uri?>() {
    /** 使用冻结快照的文件名与具体 MIME 打开系统保存选择器。 */
    override fun createIntent(context: Context, input: PreparedJsonExport): Intent =
        ActivityResultContracts.CreateDocument(input.mimeType).createIntent(context, input.fileName)

    /** 仅在用户确认保存时返回目标 URI，取消返回 null。 */
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}
