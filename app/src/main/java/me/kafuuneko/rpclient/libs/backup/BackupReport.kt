package me.kafuuneko.rpclient.libs.backup

import java.io.IOException
import java.util.Collections
import java.util.IdentityHashMap

/**
 * 收集备份操作中可展示、可复制的技术摘要。
 *
 * - 记录调用方提供的非敏感阶段、数量与相对归档位置。
 * - 异常只转换为固定错误类别与异常类型，不透传原始消息、输入内容或堆栈。
 * - 对异常关系使用身份去重和数量上限，避免循环引用影响失败收尾。
 */
class BackupReport {
    private val mLines = mutableListOf<String>()

    /**
     * 记录非敏感的操作阶段或数据集统计。
     * @param message 由调用方组织的阶段名称与统计，不包含正文、凭据或本地路径。
     */
    @Synchronized
    fun info(message: String) {
        mLines += "[INFO] $message"
    }

    /**
     * 记录可恢复问题。
     * @param message 只包含相对归档位置、标识和数量的诊断。
     */
    @Synchronized
    fun warn(message: String) {
        mLines += "[WARN] $message"
    }

    /**
     * 返回已记录的技术摘要。
     * @return 按记录顺序合并的诊断文本。
     */
    @Synchronized
    fun text(): String = mLines.joinToString("\n")

    /**
     * 将异常归类为固定英文诊断，不向详情复制原始错误消息。
     *
     * @param error 需要归类的异常，可能包含原因与附加异常。
     * @param operation 由调用方提供的固定操作名称。
     * @return 不含原始输入、路径或凭据的错误摘要。
     */
    fun statusMessage(error: Throwable, operation: String = "Backup/restore"): String {
        // 消息仅参与分类；输出始终取固定文本，避免解析器将源 JSON 带到界面。
        val failures = collectFailures(error)
        val messages = failures.mapNotNull { it.message }
        val reason = when {
            messages.any {
                it.contains(
                    "fields do not match database columns",
                    ignoreCase = true
                )
            } ->
                "Database field mapping is incompatible with this build."

            failures.any { it is SecurityException } -> "File access was denied."
            failures.any { it is IOException } -> "File read or write failed."
            messages.any {
                it.contains(
                    "Unsupported portable backup version",
                    ignoreCase = true
                )
            } ->
                "The backup format version is unsupported."

            messages.any {
                it.contains("manifest", ignoreCase = true) || it.contains(
                    "ZIP",
                    ignoreCase = true
                )
            } ->
                "The backup archive is invalid or incomplete."

            messages.any {
                it.contains("database", ignoreCase = true) || it.contains(
                    "SQLite",
                    ignoreCase = true
                )
            } ->
                "Database processing or validation failed."

            else -> "The operation could not be completed."
        }
        return "$operation failed: $reason"
    }

    /**
     * 在阶段摘要后附加安全错误类别和异常类型。
     *
     * @param error 操作失败时捕获的异常。
     * @return 可进入普通页面状态和剪贴板的技术摘要，不含原始异常消息或堆栈。
     */
    fun textWithError(error: Throwable): String {
        val types = collectFailures(error).joinToString(" -> ") { it.javaClass.simpleName }
        return text() + "\n\n[ERROR] ${statusMessage(error)}\n[EXCEPTION TYPES] $types"
    }

    /** 遍历原因和附加异常；身份去重避免异常关系形成环时无法结束。 */
    private fun collectFailures(error: Throwable): List<Throwable> {
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        val pending = ArrayDeque<Throwable>().apply { add(error) }
        val failures = mutableListOf<Throwable>()
        // 只读取有限个异常节点，错误展示不能因异常链过长再次耗尽资源。
        while (pending.isNotEmpty() && failures.size < MAX_FAILURES) {
            val failure = pending.removeFirst()
            if (!seen.add(failure)) continue
            failures.add(failure)
            failure.cause?.let { pending.add(it) }
            failure.suppressed.take(MAX_FAILURES).forEach { pending.add(it) }
        }
        return failures
    }

    private companion object {
        const val MAX_FAILURES = 16
    }
}

/** 保留底层异常供操作内部处理；界面展示必须通过 [BackupReport] 生成安全摘要。 */
internal class BackupOperationException(
    message: String,
    cause: Throwable
) : Exception(message, cause)
