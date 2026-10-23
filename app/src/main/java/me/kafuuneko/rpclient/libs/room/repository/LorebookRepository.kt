package me.kafuuneko.rpclient.libs.room.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.character.CharacterBookImport
import me.kafuuneko.rpclient.libs.character.LorebookCodec
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
    private val mCharacterDao = mAppDatabase.getCharacterDao()

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
        mAppDatabase.withTransaction {
            mCharacterDao.clearLorebookAssociations(id)
            mLorebookEntryDao.deleteEntriesByLorebookId(id)
            mLorebookDao.deleteLorebookById(id)
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

    /** 从用户选择的文档读取并解析世界书，但不写入数据库。 */
    suspend fun readImportFromUri(uri: Uri): CharacterBookImport = withContext(Dispatchers.IO) {
        val bytes = mContext.contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
            ?: error("Cannot read world book file")
        val json = bytes.toString(Charsets.UTF_8)
        val codec = LorebookCodec(mGson)
        codec.parseLorebook(json)
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

    /** 将世界书 JSON 直接写入用户选择的文档 URI。 */
    suspend fun exportToUri(lorebookId: Long, uri: Uri) = withContext(Dispatchers.IO) {
        val json = exportJson(lorebookId)
        mContext.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(json.toByteArray(Charsets.UTF_8))
        } ?: error("Cannot open world book export destination")
    }
}
