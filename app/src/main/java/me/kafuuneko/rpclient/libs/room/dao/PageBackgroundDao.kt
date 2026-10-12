package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.kafuuneko.rpclient.libs.room.entity.PageBackgroundEntity

/** 页面背景配置的数据接口，文件发布与回收由 Repository 统一处理。 */
@Dao
interface PageBackgroundDao {
    /** 观察指定页面的持久背景，删除记录代表恢复默认。 */
    @Query("SELECT * FROM page_backgrounds WHERE page = :page")
    fun observe(page: String): Flow<PageBackgroundEntity?>

    /** 在文件事务中取得旧引用。 */
    @Query("SELECT * FROM page_backgrounds WHERE page = :page")
    suspend fun get(page: String): PageBackgroundEntity?

    /** 写入完整背景设置，避免滑块参数分次保存。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(value: PageBackgroundEntity)

}
