package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.withTransaction
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry

class LorebookRepository(private val mAppDatabase: AppDatabase) {
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
            mLorebookEntryDao.deleteEntriesByLorebookId(id)
            mLorebookDao.deleteLorebookById(id)
        }
    }

    /**
     * 创建新的世界书条目。
     *
     * @param lorebookId 条目所属的世界书 id。
     * @param name 条目名称。
     * @param keywords 触发关键词。
     * @param secondaryKeywords 次要触发关键词。
     * @param order 插入顺序，数值越小优先级越高。
     * @param depth 插入深度。
     * @param category 分类标签。
     * @param content 条目正文内容。
     * @return 新创建的世界书条目 id。
     */
    suspend fun createEntry(
        lorebookId: Long,
        name: String,
        keywords: String = "",
        secondaryKeywords: String = "",
        order: Int = 0,
        depth: Int = 0,
        category: String = "",
        content: String = ""
    ): Long {
        return mLorebookEntryDao.insertOrReplace(
            LorebookEntry(
                lorebookId = lorebookId,
                name = name,
                keywords = keywords,
                secondaryKeywords = secondaryKeywords,
                order = order,
                depth = depth,
                category = category,
                content = content
            )
        )
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
}
