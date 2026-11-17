package me.kafuuneko.rpclient.libs.room.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.Gson
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.character.CharacterBookImport
import me.kafuuneko.rpclient.libs.character.LorebookCodec
import me.kafuuneko.rpclient.libs.character.LorebookArchiveLimits
import me.kafuuneko.rpclient.libs.character.LorebookArchiveLimitException
import me.kafuuneko.rpclient.libs.character.LorebookArchiveReader
import me.kafuuneko.rpclient.libs.character.inspectLorebookJson
import me.kafuuneko.rpclient.libs.character.LorebookImportBatch
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.model.LorebookWithEntries
import me.kafuuneko.rpclient.utils.toJsonString
import me.kafuuneko.rpclient.utils.toStringList

/**
 * 世界书及条目的事务、列表字段序列化和文件导入导出仓库。
 *
 * 删除世界书时会同时解除角色卡绑定并清理其条目。
 */
class LorebookRepository(
    private val mAppDatabase: AppDatabase,
    private val mGson: Gson,
    private val mContext: Context
) {
    private val mLorebookDao = mAppDatabase.getLorebookDao()
    private val mLorebookEntryDao = mAppDatabase.getLorebookEntryDao()

    /**
     * 获取所有世界书。
     *
     * @return 世界书列表。
     */
    suspend fun getAllLorebooks(): List<Lorebook> {
        return mLorebookDao.getAllLorebooks()
    }

    /**
     * 根据世界书 id 获取世界书详情。
     *
     * @param id 世界书 id。
     * @return 匹配的世界书；如果不存在则返回 null。
     */
    suspend fun getLorebookById(id: Long): Lorebook? {
        return mLorebookDao.getLorebookById(id)
    }

    /**
     * 根据世界书 id 获取该世界书下的条目列表。
     *
     * @param lorebookId 世界书 id。
     * @return 世界书条目列表。
     */
    suspend fun getEntriesByLorebookId(lorebookId: Long): List<LorebookEntry> {
        return mLorebookEntryDao.getEntriesByLorebookId(lorebookId)
    }

    /** 批量获取全部世界书及其条目，底层固定为一次主体查询和一次条目查询。 */
    suspend fun getAllLorebooksWithEntries(): List<LorebookWithEntries> {
        return mAppDatabase.withTransaction {
            val lorebooks = mLorebookDao.getAllLorebooks()
            val entriesByLorebookId = mLorebookEntryDao.getAllEntries()
                .groupBy { entry -> entry.lorebookId }
            lorebooks.map { lorebook ->
                LorebookWithEntries(
                    lorebook = lorebook,
                    entries = entriesByLorebookId[lorebook.id].orEmpty(),
                )
            }
        }
    }

    /** 批量获取所有世界书的条目数量，底层只执行一条聚合查询。 */
    suspend fun getLorebookEntryCounts(): Map<Long, Int> {
        return mLorebookEntryDao.getEntryCounts().associate { it.lorebookId to it.count }
    }

    /**
     * 根据条目 id 获取世界书条目详情。
     *
     * @param id 世界书条目 id。
     * @return 匹配的世界书条目；如果不存在则返回 null。
     */
    suspend fun getEntryById(id: Long): LorebookEntry? {
        return mLorebookEntryDao.getEntryById(id)
    }

    /**
     * 创建新的世界书。
     *
     * @param name 世界书名称。
     * @return 新创建的世界书 id。
     */
    suspend fun createLorebook(name: String): Long {
        return mLorebookDao.insertOrReplace(Lorebook(name = name))
    }

    /**
     * 保存世界书。
     *
     * 当 id 为 0 时创建新世界书；否则更新已有世界书。
     *
     * @param lorebook 要保存的世界书。
     * @return 保存后的世界书 id。
     */
    suspend fun saveLorebook(lorebook: Lorebook): Long {
        if (lorebook.id == 0L) {
            return mLorebookDao.insertOrReplace(lorebook)
        }
        mLorebookDao.update(lorebook)
        return lorebook.id
    }

    /**
     * 修改世界书名称。
     *
     * @param id 世界书 id。
     * @param name 新的世界书名称。
     */
    suspend fun renameLorebook(id: Long, name: String) {
        mLorebookDao.updateLorebookName(id, name)
    }

    /**
     * 删除世界书及其所有条目。
     *
     * @param id 世界书 id。
     */
    suspend fun deleteLorebook(id: Long) {
        deleteLorebooks(listOf(id))
    }

    /** 在同一事务内删除所选世界书、条目并解除角色绑定。 */
    suspend fun deleteLorebooks(ids: List<Long>) {
        mAppDatabase.withTransaction {
            ids.distinct().forEach { id -> mAppDatabase.deleteLorebookInTransaction(id) }
        }
    }

    /** 只删除指定世界书内的所选条目，分批绑定参数并保持整批原子性。 */
    suspend fun deleteEntries(lorebookId: Long, ids: List<Long>) {
        mAppDatabase.withTransaction {
            ids.distinct().chunked(900).forEach { batch ->
                mLorebookEntryDao.deleteEntriesByIds(lorebookId, batch)
            }
        }
    }

    /**
     * 保存世界书条目。
     *
     * 当 id 为 0 时创建新条目；否则更新已有条目。
     *
     * @param entry 要保存的世界书条目。
     * @return 保存后的世界书条目 id。
     */
    suspend fun saveEntry(entry: LorebookEntry): Long {
        if (entry.id == 0L) {
            return mLorebookEntryDao.insertOrReplace(entry)
        }
        mLorebookEntryDao.update(entry)
        return entry.id
    }

    /**
     * 更新已有世界书条目。
     *
     * @param entry 要更新的世界书条目。
     */
    suspend fun updateEntry(entry: LorebookEntry) {
        mLorebookEntryDao.update(entry)
    }

    /**
     * 修改世界书条目的正文内容。
     *
     * @param id 世界书条目 id。
     * @param content 新的条目正文内容。
     */
    suspend fun updateEntryContent(id: Long, content: String) {
        mLorebookEntryDao.updateEntryContent(id, content)
    }

    /**
     * 修改条目的全局禁用状态。
     *
     * @return 条目存在且成功更新时为 true。
     */
    suspend fun updateEntryDisabled(id: Long, disabled: Boolean): Boolean {
        return mLorebookEntryDao.updateEntryDisabled(id, disabled) > 0
    }

    /**
     * 删除指定世界书条目。
     *
     * @param id 世界书条目 id。
     */
    suspend fun deleteEntry(id: Long) {
        mLorebookEntryDao.deleteEntryById(id)
    }

    /**
     * 清空指定世界书下的所有条目。
     *
     * 注意：该方法只删除条目，不删除世界书本体。删除世界书请调用 [deleteLorebook]。
     *
     * @param lorebookId 世界书 id。
     */
    suspend fun deleteEntriesByLorebookId(lorebookId: Long) {
        mLorebookEntryDao.deleteEntriesByLorebookId(lorebookId)
    }

    /**
     * 获取世界书条目的主要关键词列表。
     */
    suspend fun getEntryKeywords(id: Long): List<String> {
        val entry = getEntryById(id) ?: return emptyList()
        return mGson.toStringList(entry.keywords)
    }

    /**
     * 更新世界书条目的主要关键词列表。
     */
    suspend fun updateEntryKeywords(id: Long, keywords: List<String>): Boolean {
        val entry = getEntryById(id) ?: return false
        updateEntry(entry.copy(keywords = mGson.toJsonString(keywords)))
        return true
    }

    /**
     * 获取世界书条目的次要关键词列表。
     */
    suspend fun getEntrySecondaryKeywords(id: Long): List<String> {
        val entry = getEntryById(id) ?: return emptyList()
        return mGson.toStringList(entry.secondaryKeywords)
    }

    /**
     * 更新世界书条目的次要关键词列表。
     */
    suspend fun updateEntrySecondaryKeywords(id: Long, secondaryKeywords: List<String>): Boolean {
        val entry = getEntryById(id) ?: return false
        updateEntry(entry.copy(secondaryKeywords = mGson.toJsonString(secondaryKeywords)))
        return true
    }

    /**
     * 获取世界书条目的分类标签列表。
     */
    suspend fun getEntryCategory(id: Long): List<String> {
        val entry = getEntryById(id) ?: return emptyList()
        return mGson.toStringList(entry.category)
    }

    /**
     * 更新世界书条目的分类标签列表。
     */
    suspend fun updateEntryCategory(id: Long, category: List<String>): Boolean {
        val entry = getEntryById(id) ?: return false
        updateEntry(entry.copy(category = mGson.toJsonString(category)))
        return true
    }

    /**
     * 从用户选择的文档读取并解析世界书，但不写入数据库。
     * @param uri 独立世界书文档。
     * @return 已预检的单本草稿。
     */
    suspend fun readImportFromUri(uri: Uri): CharacterBookImport = withContext(Dispatchers.IO) {
        val input =
            mContext.contentResolver.openInputStream(uri) ?: error("Cannot read world book file")
        input.use {
            LorebookArchiveReader(LorebookCodec(mGson)).readStandalone(
                it,
                mContext.cacheDir
            ).drafts.single()
        }
    }

    /**
     * 解析独立 JSON 或 ZIP；损坏的 ZIP 条目计入失败，不阻断同包的有效世界书。
     * @param uri 系统选择的世界书或归档。
     * @return 可保存的草稿及失败计数。
     */
    suspend fun readImportItemsFromUri(uri: Uri): LorebookImportBatch =
        readImportItemsFromUri(uri, LorebookArchiveLimits())

    /**
     * 按当前多选批次剩余预算读取，避免新文件与待确认草稿同时占满内存。
     * @param uri 系统选择的文档。
     * @param limits 本次读取可用的字节和条目额度。
     * @return 待确认的草稿与失败统计。
     */
    internal suspend fun readImportItemsFromUri(
        uri: Uri,
        limits: LorebookArchiveLimits
    ): LorebookImportBatch =
        withContext(Dispatchers.IO) {
            val source = mContext.contentResolver.openInputStream(uri)
                ?: error("Cannot read world book file")
            source.use {
                LorebookArchiveReader(LorebookCodec(mGson), limits).read(
                    it,
                    mContext.cacheDir
                )
            }
        }

    /** 将已解析的世界书及其条目作为一个事务写入数据库。 */
    suspend fun saveImport(parsed: CharacterBookImport): Long = withContext(Dispatchers.IO) {
        mAppDatabase.withTransaction {
            val bookId = mLorebookDao.insertOrReplace(parsed.lorebook)
            mLorebookEntryDao.insertOrReplaceAll(parsed.entries.map { entry -> entry.copy(lorebookId = bookId) })
            bookId
        }
    }

    /** 读取并导入世界书；无需在写入前检查解析结果时使用。 */
    suspend fun importFromUri(uri: Uri): Long = withContext(Dispatchers.IO) {
        saveImport(readImportFromUri(uri))
    }

    /** 导出指定世界书及其条目为 SillyTavern 兼容 JSON。 */
    suspend fun exportJson(lorebookId: Long): String = withContext(Dispatchers.IO) {
        val lorebook = getLorebookById(lorebookId) ?: error("World book not found")
        val entries = getEntriesByLorebookId(lorebookId)
        val codec = LorebookCodec(mGson)
        codec.toLorebookJson(lorebook, entries)
    }

    /**
     * 将世界书 JSON 直接写入用户选择的文档 URI。
     * @param lorebookId 要导出的世界书 ID。
     * @param uri 系统选择的目标文档。
     */
    suspend fun exportToUri(lorebookId: Long, uri: Uri) = withContext(Dispatchers.IO) {
        val limits = LorebookArchiveLimits()
        val file = File.createTempFile("rpclient-worldbook-", ".json", mContext.cacheDir)
        try {
            mAppDatabase.withTransaction {
                val book = getLorebookById(lorebookId) ?: error("World book not found")
                writeExportBook(book, file, limits.totalJsonBytes, limits.bookEntries)
            }
            copyExportToUri(file, uri)
        } finally {
            file.delete()
        }
    }

    /**
     * 将所选世界书写为可重新导入的 JSON ZIP，完整封包成功后才写目标文档。
     * @param lorebookIds 用户选择的世界书 ID。
     * @param uri 系统文件创建器返回的目标文档。
     */
    suspend fun exportArchiveToUri(lorebookIds: List<Long>, uri: Uri) =
        withContext(Dispatchers.IO) {
            val ids = lorebookIds.distinct()
            require(ids.isNotEmpty()) { "No world books selected" }
            val limits = LorebookArchiveLimits()
            if (ids.size > limits.entries) throw LorebookArchiveLimitException()
            // 先生成完整归档，导出与导入共用限额，保证成功导出的归档能够重新读取。
            val archive =
                File.createTempFile("rpclient-worldbook-export-", ".zip", mContext.cacheDir)
            try {
                ZipOutputStream(archive.outputStream().buffered()).use { zip ->
                    writeArchiveBooks(ids, zip, limits)
                }
                if (archive.length() > limits.archiveBytes) throw LorebookArchiveLimitException()
                copyExportToUri(archive, uri)
            } finally {
                archive.delete()
            }
        }

    /** 在读取事务中获取一致快照，并逐本编码以限制瞬时内存。 */
    private suspend fun writeArchiveBooks(
        ids: List<Long>,
        zip: ZipOutputStream,
        limits: LorebookArchiveLimits
    ) {
        val file = File.createTempFile("rpclient-worldbook-", ".json", mContext.cacheDir)
        try {
            var remainingBytes = limits.totalJsonBytes
            var remainingEntries = limits.totalBookEntries
            mAppDatabase.withTransaction {
                for (id in ids) {
                    val book = getLorebookById(id) ?: error("World book not found")
                    // 任一本超限都会放弃整个暂存归档，目标文档只接收完整结果。
                    remainingEntries -= writeExportBook(
                        book = book,
                        file = file,
                        maxBytes = minOf(remainingBytes, limits.entryBytes.toLong()),
                        maxEntries = minOf(remainingEntries, limits.bookEntries)
                    )
                    remainingBytes -= file.length()
                    val name = book.name.map { char ->
                        if (char.code < 0x20 || char in "/\\:*?\"<>|") '_' else char
                    }.joinToString("").trim(' ', '.').take(80).ifBlank { "worldbook" }
                    zip.putNextEntry(ZipEntry("$name-$id.json"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } finally {
            file.delete()
        }
    }

    /**
     * 在调用方的事务内分页编码，再用导入器的结构预算核验暂存文件。
     * @param book 要导出的世界书。
     * @param file 当前书籍独占或复用的暂存文件。
     * @param maxBytes 当前可用 UTF-8 字节预算。
     * @param maxEntries 当前可用条目预算。
     * @return 实际写出的条目数。
     */
    private suspend fun writeExportBook(
        book: Lorebook,
        file: File,
        maxBytes: Long,
        maxEntries: Int
    ): Int {
        val context = currentCoroutineContext()
        file.bufferedWriter().use { writer ->
            LorebookCodec(mGson).writeLorebook(writer, book) { append ->
                var count = 0
                do {
                    context.ensureActive()
                    val page = mLorebookEntryDao.getExportPage(
                        book.id,
                        offset = count,
                        limit = EXPORT_PAGE_SIZE
                    )
                    if (page.size > maxEntries - count) throw LorebookArchiveLimitException()
                    page.forEach(append)
                    count += page.size
                    // UTF-8 缓冲刷新后以实际文件大小检查，不能用字符数代替字节数。
                    writer.flush()
                    if (file.length() > maxBytes) throw LorebookArchiveLimitException()
                } while (page.size == EXPORT_PAGE_SIZE)
            }
        }
        if (file.length() > maxBytes) throw LorebookArchiveLimitException()
        return file.reader().use { inspectLorebookJson(it, maxEntries, context::ensureActive) }
    }

    /** 完成所有限额检查后才截断目标文件。 */
    private suspend fun copyExportToUri(file: File, uri: Uri) {
        currentCoroutineContext().ensureActive()
        val output = mContext.contentResolver.openOutputStream(uri, "wt")
            ?: error("Cannot open world book export destination")
        output.use { target -> file.inputStream().use { it.copyTo(target) } }
    }

    private companion object {
        const val EXPORT_PAGE_SIZE = 16
    }

}

/** 在调用方已开启的事务内解除角色绑定，并删除世界书及其条目。 */
internal suspend fun AppDatabase.deleteLorebookInTransaction(id: Long) {
    getCharacterDao().clearLorebookAssociations(id)
    getLorebookEntryDao().deleteEntriesByLorebookId(id)
    getLorebookDao().deleteLorebookById(id)
}
