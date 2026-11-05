package me.kafuuneko.rpclient.libs.character

import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 世界书 ZIP 的磁盘与内存预算；累计量也计算解析失败条目已经读入的字节。 */
internal data class LorebookArchiveLimits(
    val archiveBytes: Long = 64L * 1024 * 1024,
    val entryBytes: Int = 8 * 1024 * 1024,
    val totalJsonBytes: Long = 32L * 1024 * 1024,
    val entries: Int = MAX_ENTRIES
) {
    companion object {
        const val MAX_ENTRIES = 1_000
    }
}

/** 导出内容超过世界书 ZIP 的导入预算，宿主可提示用户拆分或缩小世界书。 */
internal class LorebookArchiveLimitException :
    Exception("World book archive exceeds import limits")

/**
 * 在资源预算内解析世界书 ZIP，不把条目解包到文件系统。
 * - 压缩包先限量复制到缓存，所有退出路径都会删除临时文件。
 * - 单本超限或损坏计入失败后继续；归档累计超限时拒绝整包，不返回部分草稿。
 * - ZIP 元数据仅用于提前拒绝，实际读取仍按字节计量，不能被伪造大小绕过。
 */
internal class LorebookArchiveReader(
    private val mCodec: LorebookCodec,
    private val mLimits: LorebookArchiveLimits = LorebookArchiveLimits()
) {
    /**
     * 按文件签名读取独立 JSON 或 ZIP，输入流由调用方关闭。
     * @param input 用户选择的文档流。
     * @param cacheDirectory ZIP 随机访问所需的缓存目录。
     * @return 尚未持久化的世界书与失败计数。
     */
    suspend fun read(input: InputStream, cacheDirectory: File): LorebookImportBatch {
        val source = PushbackInputStream(input.buffered(), 4)
        val signature = ByteArray(4)
        var size = 0
        // 部分文档提供器分段返回数据，不能假设一次 read 能读满签名。
        while (size < signature.size) {
            val count = source.read(signature, size, signature.size - size)
            if (count < 0) break
            size += count
        }
        if (size > 0) source.unread(signature, 0, size)
        if (size == 4 && signature[0] == 0x50.toByte() && signature[1] == 0x4b.toByte()) {
            return readArchive(source, cacheDirectory)
        }
        val bytes = readEntry(source) { }
        val parsed = mCodec.parseLorebook(bytes.toString(Charsets.UTF_8))
        return LorebookImportBatch(listOf(parsed), 0, 1, bytes.size.toLong())
    }

    /**
     * 读取已确认签名的世界书 ZIP，输入流由调用方关闭。
     * @param input 从 ZIP 起始位置读取的输入流。
     * @param cacheDirectory 用于随机访问 ZIP 的临时目录。
     * @return 全部有效世界书草稿及失败计数，尚未写入任何业务实体。
     */
    private suspend fun readArchive(input: InputStream, cacheDirectory: File): LorebookImportBatch {
        val archive = File.createTempFile("rpclient-worldbook-import-", ".zip", cacheDirectory)
        try {
            // 缓存复制也需要上限，非 JSON 附件不能无限占用应用磁盘。
            copyArchive(input, archive)
            return ZipFile(archive).use { readEntries(it) }
        } finally {
            archive.delete()
        }
    }

    /** 分块复制压缩数据，取消或超限时由外层统一清理缓存文件。 */
    private suspend fun copyArchive(input: InputStream, archive: File) {
        var copiedBytes = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        archive.outputStream().buffered().use { output ->
            // 不信任文件选择器报告的大小，按流中的实际字节数执行限制。
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                copiedBytes += count
                if (copiedBytes > mLimits.archiveBytes) {
                    throw ArchiveLimitException("World book archive exceeds the compressed size limit")
                }
                output.write(buffer, 0, count)
            }
        }
    }

    /** 枚举安全 JSON 条目；累计预算异常必须越过单本容错边界。 */
    private suspend fun readEntries(zip: ZipFile): LorebookImportBatch {
        if (zip.size() > mLimits.entries) {
            throw ArchiveLimitException("World book archive contains too many entries")
        }
        val drafts = mutableListOf<CharacterBookImport>()
        var failureCount = 0
        var candidateCount = 0
        var totalJsonBytes = 0L
        val entries = zip.entries()
        // 元数据只提前拒绝明显过大的单本；所有实际解压字节仍纳入整包预算。
        while (entries.hasMoreElements()) {
            currentCoroutineContext().ensureActive()
            val entry = entries.nextElement()
            if (entry.isDirectory || !entry.name.endsWith(".json", ignoreCase = true) ||
                !isSafePath(entry.name)
            ) continue
            candidateCount++
            if (entry.size > mLimits.entryBytes) {
                failureCount++
                continue
            }
            try {
                val bytes = zip.getInputStream(entry).use { input ->
                    readEntry(input) { count ->
                        totalJsonBytes += count
                        if (totalJsonBytes > mLimits.totalJsonBytes) {
                            throw ArchiveLimitException("World book archive exceeds the total JSON size limit")
                        }
                    }
                }
                val document = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
                // 误选完整备份时不导入其中的世界书；以根清单的格式标记识别，普通同名文件不影响其他世界书。
                if (entry.name == "manifest.json" && document.get("format")?.asString in BACKUP_FORMATS) {
                    return LorebookImportBatch(
                        emptyList(),
                        failureCount = 1,
                        totalCount = 1
                    )
                }
                drafts += mCodec.parseLorebook(document.toString())
            } catch (error: Exception) {
                if (error is CancellationException || error is ArchiveLimitException) throw error
                failureCount++
            }
        }
        if (candidateCount == 0) failureCount++
        return LorebookImportBatch(drafts, failureCount, maxOf(candidateCount, 1), totalJsonBytes)
    }

    /** 在扩容缓冲区之前核对实际解压大小，单本超限不会耗尽剩余归档的内存。 */
    private suspend fun readEntry(input: InputStream, consume: (Int) -> Unit): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        // ZIP 声明大小可能被伪造，必须在每次写入内存前重新检查预算。
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            consume(count)
            if (output.size().toLong() + count > mLimits.entryBytes) {
                throw EntryLimitException()
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    /** 非安全路径直接忽略；条目始终只在内存中解析，不作为磁盘路径使用。 */
    private fun isSafePath(name: String): Boolean {
        return !name.startsWith('/') && !name.startsWith('\\') &&
            !name.contains('\u0000') && name.split('/', '\\').none { it == ".." }
    }

    private companion object {
        val BACKUP_FORMATS = setOf("rpclient-portable-data", "rpclient-backup")
    }

    private class ArchiveLimitException(message: String) : Exception(message)
    private class EntryLimitException : Exception("World book JSON exceeds the entry size limit")
}

/** 世界书文件解析批次；原始数据只由 ViewModel 暂存，不进入 UiState。 */
data class LorebookImportBatch(
    val drafts: List<CharacterBookImport>,
    val failureCount: Int,
    val totalCount: Int,
    val decodedBytes: Long = 0,
)
