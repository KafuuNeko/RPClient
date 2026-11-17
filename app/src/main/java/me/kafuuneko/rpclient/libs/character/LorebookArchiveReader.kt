package me.kafuuneko.rpclient.libs.character

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
    val entries: Int = MAX_ENTRIES,
    val bookEntries: Int = 16_384,
    val totalBookEntries: Int = 32_768,
) {
    companion object {
        const val MAX_ENTRIES = 1_000
    }
}

/** 世界书内容超过导入预算，宿主可提示用户拆分或缩小世界书。 */
internal class LorebookArchiveLimitException :
    Exception("World book archive exceeds import limits")

/**
 * 在资源预算内解析独立世界书或 ZIP，使用应用私有缓存预检后再解码。
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
        return readStandalone(source, cacheDirectory)
    }

    /**
     * 单本使用整批 JSON 字节预算，兼容大于 ZIP 成员限额的历史导出。
     * @param input 独立 JSON 输入流。
     * @param cacheDirectory 暂存同一份内容供预检和解码使用的目录。
     * @return 单本世界书草稿和实际字节数。
     */
    suspend fun readStandalone(input: InputStream, cacheDirectory: File): LorebookImportBatch {
        val file = File.createTempFile("rpclient-worldbook-", ".json", cacheDirectory)
        try {
            val bytes = copyDocument(input, file) { size ->
                if (size > mLimits.totalJsonBytes) throw LorebookArchiveLimitException()
            }
            val context = currentCoroutineContext()
            file.reader().use {
                inspectLorebookJson(
                    it,
                    minOf(mLimits.bookEntries, mLimits.totalBookEntries),
                    context::ensureActive
                )
            }
            val parsed = file.reader().use { mCodec.parseLorebook(it, context::ensureActive) }
            return LorebookImportBatch(listOf(parsed), 0, 1, bytes)
        } finally {
            file.delete()
        }
    }

    /** 归档只复制一次；每个成员复用暂存文件，所有退出路径都会清理。 */
    private suspend fun readArchive(input: InputStream, cacheDirectory: File): LorebookImportBatch {
        val archive = File.createTempFile("rpclient-worldbook-", ".zip", cacheDirectory)
        try {
            copyDocument(input, archive) { size ->
                if (size > mLimits.archiveBytes) throw ArchiveLimitException("World book archive exceeds the compressed size limit")
            }
            val book = File.createTempFile("rpclient-worldbook-", ".json", cacheDirectory)
            try {
                return ZipFile(archive).use { readEntries(it, book) }
            } finally {
                book.delete()
            }
        } finally {
            archive.delete()
        }
    }

    /** 分块计量实际字节数，在写入缓存前执行单文件与累计限制。 */
    private suspend fun copyDocument(
        input: InputStream,
        file: File,
        checkSize: (Long) -> Unit
    ): Long {
        var bytes = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        file.outputStream().buffered().use { output ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                bytes += count
                checkSize(bytes)
                output.write(buffer, 0, count)
            }
        }
        return bytes
    }

    /** 枚举安全 JSON 条目；整包预算必须在本次创建实体前检查。 */
    private suspend fun readEntries(zip: ZipFile, book: File): LorebookImportBatch {
        if (zip.size() > mLimits.entries) {
            throw ArchiveLimitException("World book archive contains too many entries")
        }
        val drafts = mutableListOf<CharacterBookImport>()
        var failureCount = 0
        var candidateCount = 0
        var totalJsonBytes = 0L
        var acceptedEntries = 0
        val context = currentCoroutineContext()
        // 声明大小只用于快速排除，读取失败或大小伪造仍按实际解压字节计量。
        for (entry in zip.entries()) {
            context.ensureActive()
            if (entry.isDirectory || !entry.name.endsWith(".json", ignoreCase = true) ||
                !isSafePath(entry.name)
            ) continue
            candidateCount++
            if (entry.size > mLimits.entryBytes) {
                failureCount++
                continue
            }
            val previousBytes = totalJsonBytes
            try {
                zip.getInputStream(entry).use { input ->
                    copyDocument(input, book) { size ->
                        totalJsonBytes = previousBytes + size
                        if (totalJsonBytes > mLimits.totalJsonBytes) {
                            throw ArchiveLimitException("World book archive exceeds the total JSON size limit")
                        }
                        if (size > mLimits.entryBytes) throw LorebookArchiveLimitException()
                    }
                }
                // 完整备份即使在有效世界书之后才出现清单，也不返回任何草稿。
                if (entry.name == "manifest.json" && book.reader()
                        .use { isLorebookBackupManifest(it, context::ensureActive) }
                ) {
                    return LorebookImportBatch(emptyList(), failureCount = 1, totalCount = 1)
                }
                val count = book.reader()
                    .use { inspectLorebookJson(it, mLimits.bookEntries, context::ensureActive) }
                if (count > mLimits.totalBookEntries - acceptedEntries) {
                    throw ArchiveLimitException("World book archive contains too many world book entries")
                }
                drafts += book.reader().use { mCodec.parseLorebook(it, context::ensureActive) }
                acceptedEntries += count
            } catch (error: Exception) {
                if (error is CancellationException || error is ArchiveLimitException) throw error
                failureCount++
            }
        }
        if (candidateCount == 0) failureCount++
        return LorebookImportBatch(drafts, failureCount, maxOf(candidateCount, 1), totalJsonBytes)
    }

    /** 非安全路径直接忽略；只写入随机命名的缓存文件，不使用归档路径。 */
    private fun isSafePath(name: String): Boolean {
        return !name.startsWith('/') && !name.startsWith('\\') &&
            !name.contains('\u0000') && name.split('/', '\\').none { it == ".." }
    }

    private class ArchiveLimitException(message: String) : Exception(message)
}

/** 世界书文件解析批次；原始数据只由 ViewModel 暂存，不进入 UiState。 */
data class LorebookImportBatch(
    val drafts: List<CharacterBookImport>,
    val failureCount: Int,
    val totalCount: Int,
    val decodedBytes: Long = 0,
)
