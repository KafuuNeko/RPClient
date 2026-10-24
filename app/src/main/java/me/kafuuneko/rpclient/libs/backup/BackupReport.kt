package me.kafuuneko.rpclient.libs.backup

/**
 * 备份与恢复过程的技术日志。
 * - 页面在操作结束后展示完整文本，便于展开查看和复制。
 * - 日志只记录相对归档路径、表名与数量，不记录对话正文或凭据。
 */
class BackupReport {
    private val mLines = mutableListOf<String>()

    /** 记录阶段进度。 */
    @Synchronized
    fun info(message: String) {
        mLines += "[INFO] $message"
    }

    /** 记录不会中断操作但用户应当知道的情况，例如被忽略的文件或被移除的悬空记录。 */
    @Synchronized
    fun warn(message: String) {
        mLines += "[WARN] $message"
    }

    /** 返回当前全部日志。 */
    @Synchronized
    fun text(): String = mLines.joinToString("\n")

    /** 日志加上完整异常链，供失败时展示。 */
    fun textWithError(error: Throwable): String {
        return text() + "\n\n[ERROR] " + error.stackTraceToString()
    }
}
