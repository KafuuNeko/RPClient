package me.kafuuneko.rpclient.libs.room.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.kafuuneko.rpclient.libs.appearance.BackgroundPage
import me.kafuuneko.rpclient.libs.appearance.PageBackground
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.PageBackgroundEntity
import me.kafuuneko.rpclient.libs.room.model.PreparedFile

/**
 * 页面背景的持久化边界。
 * - 配置与文件索引在同一个文件事务内更新，替换失败保留原背景。
 * - 每个页面独占索引，删除背景不会误删头像或其他页面共享的物理文件。
 */
class PageBackgroundRepository(database: AppDatabase, private val mFiles: FileRepository) {
    private val mDao = database.getPageBackgroundDao()

    /** 观察页面设置，不存在记录时使用主题底色。 */
    fun observe(page: BackgroundPage): Flow<PageBackground> = mDao.observe(page.key).map { row ->
        row?.let {
            PageBackground(
                page = page, fileUuid = it.fileUuid, blur = it.blur, scrim = it.scrim,
                opacity = it.opacity, topBarOpacity = it.topBarOpacity,
                bottomBarOpacity = it.bottomBarOpacity, bubbleOpacity = it.bubbleOpacity,
                focalX = it.focalX, focalY = it.focalY
            ).normalized()
        } ?: PageBackground(page)
    }

    /** 原子应用预览草稿；prepared 非空时发布新源图，为空时只更新已有设置。 */
    suspend fun save(value: PageBackground, prepared: PreparedFile?) {
        val settings = value.normalized()
        mFiles.mutate(listOfNotNull(prepared)) {
            val previous = mDao.get(settings.page.key)
            val uuid = prepared?.file?.uuid ?: settings.fileUuid
            // 透明度独立于图片保存；清图时先解除业务引用，再释放旧文件索引。
            require(uuid == null || prepared != null || previous?.fileUuid == uuid) {
                "Stale background reference"
            }
            mDao.put(PageBackgroundEntity(
                page = settings.page.key, fileUuid = uuid, blur = settings.blur,
                scrim = settings.scrim, opacity = settings.opacity,
                focalX = settings.focalX, focalY = settings.focalY,
                topBarOpacity = settings.topBarOpacity, bottomBarOpacity = settings.bottomBarOpacity,
                bubbleOpacity = settings.bubbleOpacity
            ))
            previous?.fileUuid?.takeIf { it != uuid }?.let { removeFiles(listOf(it)) }
        }
    }
}
