package me.kafuuneko.rpclient.feature.textshare

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * 将应用内选择菜单传来的文本片段交给系统分享面板。
 *
 * 当前 Compose 的 SelectionContainer 没有公开选中文本读取接口，因此通过
 * PROCESS_TEXT 接收框架截取的内容，避免分享整条消息或借用剪贴板中转。
 * 此入口不导出、不渲染页面，只承担 Activity 层的系统跳转职责。
 */
class TextShareActivity : Activity() {
    /** 校验文本处理请求，打开分享面板后立即结束无界面入口。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        if (intent.action != Intent.ACTION_PROCESS_TEXT ||
            intent.type != "text/plain" || selectedText.isNullOrEmpty()
        ) {
            finish()
            return
        }

        // 仅转发用户选中的文本；系统选择目标后才会发送给其他应用。
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, selectedText)
        }
        startActivity(Intent.createChooser(shareIntent, null))
        // Theme.NoDisplay 要求在进入 resumed 状态前结束，取消分享会返回原页面。
        finish()
    }
}
