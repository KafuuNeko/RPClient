package me.kafuuneko.rpclient.libs.backup

import android.content.Context
import android.net.Uri
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFormat.BUFFER_SIZE
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFormat.MAX_BYTES
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFormat.MAX_ENTRIES
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.Writer
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** 归档路径与校验、流式 ZIP 读取及文件替换的公共操作。 */
internal object BackupArchiveFiles {
    /** 校验 manifest 中每个文件的大小和摘要，并拒绝索引外的额外文件。 */
    fun validateChecksums(root: File, files: JSONObject) {
        val listed = mutableSetOf<String>()
        val keys = files.keys()
        while (keys.hasNext()) {
            val path = keys.next()
            listed.add(path)
            require(isSafePath(path)) { "Unsafe manifest path: " + path }
            val file = File(root, path).canonicalFile
            require(file.path.startsWith(root.canonicalPath + File.separator) && file.isFile) {
                "The archive is missing " + path
            }
            val info = files.getJSONObject(path)
            require(file.length() == info.getLong("size")) { "File size mismatch: " + path }
            require(sha256(file).equals(other = info.getString("sha256"), ignoreCase = true)) {
                "Checksum mismatch: " + path
            }
        }
        // 只排除根 manifest；私有目录允许同名文件且必须参与校验。
        val actual = root.walkTopDown().filter { it.isFile && it != File(root, "manifest.json") }
            .map { it.relativeTo(root).invariantSeparatorsPath }.toSet()
        require(actual == listed) { "The archive contains files not listed in manifest.json." }
    }

    /** 流式解压到独立目录，拒绝越界路径、重复条目和超过恢复限额的内容。 */
    fun extractArchive(context: Context, source: Uri, stage: File) {
        val input = context.contentResolver.openInputStream(source)
            ?: error("Could not read the selected archive.")
        input.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                var entries = 0
                var totalBytes = 0L
                val seenPaths = mutableSetOf<String>()
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    require(entries <= MAX_ENTRIES) { "The archive contains too many files." }
                    require(isSafePath(entry.name)) { "Unsafe archive path: " + entry.name }
                    require(seenPaths.add(entry.name)) { "The archive contains a duplicate path: " + entry.name }
                    // 规范化后的目标仍须位于暂存目录，不能只检查原始路径片段。
                    if (entry.isDirectory) {
                        val target = File(stage, entry.name).canonicalFile
                        require(
                            value = target.path == stage.canonicalPath
                            || target.path.startsWith(stage.canonicalPath + File.separator),
                        ) {
                            "Unsafe archive path: " + entry.name
                        }
                        target.mkdirs()
                    } else {
                        val target = File(stage, entry.name).canonicalFile
                        require(target.path.startsWith(stage.canonicalPath + File.separator)) {
                            "Unsafe archive path: " + entry.name
                        }
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                totalBytes += count
                                require(totalBytes <= MAX_BYTES) { "The archive exceeds the 2 GiB restore limit." }
                                output.write(buffer, 0, count)
                            }
                            output.fd.sync()
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
    }

    private fun isSafePath(name: String): Boolean {
        val hasUnsafePathSyntax = name.isBlank()
            || name.startsWith("/")
            || name.startsWith("\\")
            || name.contains('\\')
        if (hasUnsafePathSyntax) return false
        return name.removeSuffix("/").split('/').none { it.isBlank() || it == "." || it == ".." }
    }

    /** 输出单条 UTF-8 JSONL 记录，换行由写入器统一追加。 */
    fun writeJsonLine(json: JSONObject, writer: Writer) {
        writer.write(json.toString())
        writer.write("\n")
    }

    /** 创建父目录后写入 UTF-8 文档。 */
    fun writeText(file: File, content: String) {
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
    }

    /** 流式计算文件摘要，避免将附件整体读入内存。 */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** 复制存在的普通文件；原文件缺省时保留缺省状态。 */
    fun copyFile(source: File, destination: File) {
        if (!source.isFile) return
        destination.parentFile?.mkdirs()
        source.copyTo(target = destination, overwrite = true)
    }

    /** 复制目录树和空目录，供快照与回滚使用。 */
    fun copyDirectory(source: File, destination: File) {
        if (!source.isDirectory) return
        source.walkTopDown().forEach { file ->
            val target = File(destination, file.relativeTo(source).path)
            if (file.isDirectory) target.mkdirs()
            else {
                target.parentFile?.mkdirs()
                file.copyTo(target = target, overwrite = true)
            }
        }
    }

    /** 以已校验文件替换目标，跨卷时回退为复制。 */
    fun replaceFile(source: File, destination: File) {
        require(source.isFile) { "Restore archive is missing " + source.name }
        destination.parentFile?.mkdirs()
        if (destination.exists() && !destination.delete()) error("Could not replace " + destination.name)
        if (!source.renameTo(destination)) source.copyTo(target = destination, overwrite = true)
    }

    /** 完整替换目录，防止旧附件或设置残留。 */
    fun replaceDirectory(source: File, destination: File) {
        require(source.isDirectory) { "Restore archive is missing " + source.path }
        if (destination.exists() && !destination.deleteRecursively()) error("Could not replace " + destination.name)
        destination.parentFile?.mkdirs()
        if (!source.renameTo(destination)) copyDirectory(source, destination)
    }

    /**
     * 替换数据库时清除旧 WAL、SHM 和回滚日志，防止旧日志覆盖或破坏恢复的数据。
     */
    fun deleteSidecars(databaseFile: File) {
        File(databaseFile.path + "-wal").delete()
        File(databaseFile.path + "-shm").delete()
        File(databaseFile.path + "-journal").delete()
    }
}
