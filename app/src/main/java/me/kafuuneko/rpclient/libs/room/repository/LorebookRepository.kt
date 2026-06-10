package me.kafuuneko.rpclient.libs.room.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.character.LorebookCodec
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.utils.toJsonString
import me.kafuuneko.rpclient.utils.toStringList

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
     * 导入世界书条目
     */
    suspend fun importFromUri(uri: Uri): Long = withContext(Dispatchers.IO) {
        val bytes = mContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Cannot read world book file")
        val json = bytes.toString(Charsets.UTF_8)
        val codec = LorebookCodec(mGson)
        val parsed = codec.parseLorebook(json)

        val bookId = saveLorebook(parsed.lorebook)
        parsed.entries.forEach { entry -> saveEntry(entry.copy(lorebookId = bookId)) }
        bookId
    }

    suspend fun exportJson(lorebookId: Long): String = withContext(Dispatchers.IO) {
        val lorebook = getLorebookById(lorebookId) ?: error("World book not found")
        val entries = getEntriesByLorebookId(lorebookId)
        val codec = LorebookCodec(mGson)
        codec.toLorebookJson(lorebook, entries)
    }
}
