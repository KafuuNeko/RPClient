package me.kafuuneko.rpclient.libs.backup

import java.io.File
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFiles.copyDirectory
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFiles.copyFile
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFiles.deleteSidecars
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFiles.replaceDirectory
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFiles.replaceFile

/** 一次替换所需的暂存文件和目标；数据库条目额外清理旧日志。 */
internal data class BackupReplacement(
    val source: File,
    val target: File,
    val isDatabase: Boolean = false
)

/**
 * 统一执行新旧格式的文件替换与异常回滚。
 * - 先关闭数据库，使 WAL 中已提交的数据进入回滚快照。
 * - 替换失败时尝试恢复每个条目，回滚失败则保留快照供后续恢复。
 */
internal object BackupDataReplacer {
    /**
     * 对已校验的暂存数据执行替换，返回后调用方必须重启应用。
     * @param rollback 保存本次原始数据的独立目录。
     * @param replacements 待替换文件或目录，不允许目标彼此嵌套。
     * @param closeDatabases 在快照复制前关闭所有受影响的 Room 实例。
     */
    fun replace(rollback: File, replacements: List<BackupReplacement>, closeDatabases: () -> Unit) {
        replacements.forEach { require(it.source.exists()) { "Restore source is missing." } }
        check(rollback.mkdirs()) { "Could not create rollback workspace." }
        var preserveRollback = false
        try {
            // 关闭失败或快照复制失败均发生在替换前；当前文件保持原样，但连接必须重建。
            closeDatabases()
            val existed = replacements.map { it.target.exists() }
            replacements.forEachIndexed { index, item ->
                copy(item.target, File(rollback, index.toString()))
            }
            try {
                replacements.forEach { item ->
                    if (item.isDatabase) deleteSidecars(item.target)
                    replace(item.source, item.target)
                }
            } catch (error: Throwable) {
                // 单个条目回滚失败不能阻止其他条目的恢复，也不能清除唯一的原始副本。
                val rollbackError = recover(rollback, replacements, existed)
                if (rollbackError != null) {
                    preserveRollback = true
                    error.addSuppressed(rollbackError)
                }
                throw BackupRestoreException(
                    if (preserveRollback) "Restore rollback was incomplete; rollback files were retained."
                    else "Restore failed; previous data was recovered.",
                    restartRequired = true,
                    cause = error
                )
            }
        } catch (error: BackupRestoreException) {
            throw error
        } catch (error: Throwable) {
            throw BackupRestoreException("Could not prepare restore rollback.", true, error)
        } finally {
            if (!preserveRollback) rollback.deleteRecursively()
        }
    }

    /** 尽力恢复全部条目，并收集错误，避免第一个失败掩盖其他可恢复的数据。 */
    private fun recover(
        rollback: File,
        replacements: List<BackupReplacement>,
        existed: List<Boolean>
    ): Throwable? {
        var failure: Throwable? = null
        replacements.forEachIndexed { index, item ->
            try {
                if (item.isDatabase) deleteSidecars(item.target)
                if (existed[index]) {
                    // 复制恢复而不移动快照，后续条目失败时仍保留完整回滚材料。
                    check(!item.target.exists() || item.target.deleteRecursively()) { "Could not remove restore target." }
                    copy(File(rollback, index.toString()), item.target)
                } else {
                    check(!item.target.exists() || item.target.deleteRecursively()) { "Could not remove new restore target." }
                }
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure?.addSuppressed(error)
            }
        }
        return failure
    }

    private fun copy(source: File, target: File) {
        if (source.isDirectory) copyDirectory(source, target) else copyFile(source, target)
    }

    private fun replace(source: File, target: File) {
        if (source.isDirectory) replaceDirectory(source, target) else replaceFile(source, target)
    }
}
