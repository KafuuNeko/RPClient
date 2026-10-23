package me.kafuuneko.rpclient.libs.room.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import me.kafuuneko.rpclient.libs.appearance.BackgroundPage
import me.kafuuneko.rpclient.libs.appearance.PageBackground
import me.kafuuneko.rpclient.libs.appearance.resolveWith
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
    private val mCharacters = database.getCharacterDao()

    /** 观察页面设置，不存在记录时使用主题底色。 */
    fun observe(page: BackgroundPage): Flow<PageBackground> = mDao.observe(page.key).map { row ->
        // 原始值与显式继承开关一起恢复，派生配置只用于渲染。
        row?.let { pageBackgroundEntity ->
            PageBackground(
                page = page,
                fileUuid = pageBackgroundEntity.fileUuid,
                blur = pageBackgroundEntity.blur,
                scrim = pageBackgroundEntity.scrim,
                opacity = pageBackgroundEntity.opacity,
                topBarOpacity = pageBackgroundEntity.topBarOpacity,
                bottomBarOpacity = pageBackgroundEntity.bottomBarOpacity,
                bubbleOpacity = pageBackgroundEntity.bubbleOpacity,
                focalX = pageBackgroundEntity.focalX,
                focalY = pageBackgroundEntity.focalY,
                topBarBlur = pageBackgroundEntity.topBarBlur,
                bottomBarBlur = pageBackgroundEntity.bottomBarBlur,
                bubbleBlur = pageBackgroundEntity.bubbleBlur,
                contentBlur = pageBackgroundEntity.contentBlur,
                followGeneral = pageBackgroundEntity.followGeneral,
                useCharacterBackground = pageBackgroundEntity.useCharacterBackground,
            ).normalized()
        } ?: PageBackground(page)
    }

    /** 观察实际显示配置；编辑器仍读取原始值，继承不创建图片引用或覆盖独立草稿。 */
    fun observeEffective(page: BackgroundPage): Flow<PageBackground> {
        if (page == BackgroundPage.General) return observe(page)
        return combine(observe(page), observe(BackgroundPage.General)) { own, general ->
            own.resolveWith(general)
        }.distinctUntilChanged()
    }

    /** 订阅角色关联的文件索引，背景更新不需要复制文件。 */
    private fun observeCharacterImage(characterId: Long): Flow<String?> =
        mCharacters.observeBackground(characterId).distinctUntilChanged()

    /** 聊天优先采用开启选项后的角色图片，缺省按对话、通用顺序回退。 */
    fun observeChat(characterId: Long): Flow<PageBackground> =
        combine(
            observeEffective(BackgroundPage.Chat),
            observeCharacterImage(characterId)
        ) { settings, characterImage ->
            if (settings.useCharacterBackground && characterImage != null) settings.copy(fileUuid = characterImage) else settings
        }.distinctUntilChanged()

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
            mDao.put(
                value = PageBackgroundEntity(
                    page = settings.page.key,
                    fileUuid = uuid,
                    blur = settings.blur,
                    scrim = settings.scrim,
                    opacity = settings.opacity,
                    focalX = settings.focalX,
                    focalY = settings.focalY,
                    topBarOpacity = settings.topBarOpacity,
                    bottomBarOpacity = settings.bottomBarOpacity,
                    bubbleOpacity = settings.bubbleOpacity,
                    topBarBlur = settings.topBarBlur,
                    bottomBarBlur = settings.bottomBarBlur,
                    bubbleBlur = settings.bubbleBlur,
                    contentBlur = settings.contentBlur,
                    followGeneral = settings.followGeneral,
                    useCharacterBackground = settings.useCharacterBackground,
                ),
            )
            previous?.fileUuid?.takeIf { text -> text != uuid }
                ?.let { text -> removeFiles(listOf(text)) }
        }
    }
}
