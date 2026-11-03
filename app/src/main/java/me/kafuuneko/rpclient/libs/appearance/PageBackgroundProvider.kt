package me.kafuuneko.rpclient.libs.appearance

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import me.kafuuneko.rpclient.libs.room.repository.PageBackgroundRepository

/**
 * 订阅已保存的背景配置，通过图像管线提供底图与栏位图片。
 * - 返回冷流，由页面的可见生命周期控制订阅和图像任务。
 * - 图片失效时回退主题表面，不让外观故障影响阅读或编辑。
 */
class PageBackgroundProvider(
    private val mRepository: PageBackgroundRepository,
    private val mRuntime: BackgroundImageRuntime,
) {

    /**
     * 切换会话角色时取消上一个角色的背景订阅。
     * @param characterIds 当前会话的角色 ID 流。
     * @return 按单聊背景优先级加载的背景资源。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeChat(characterIds: Flow<Long>): Flow<RenderedPageBackground> =
        render(characterIds.distinctUntilChanged().flatMapLatest { mRepository.observeChat(it) })

    /**
     * 为角色内容页渲染通用外观与可选的角色背景。
     *
     * @param characterId 当前浏览的角色 ID。
     * @return 使用角色图片或通用图片的背景资源。
     */
    fun observeCharacterContent(characterId: Long): Flow<RenderedPageBackground> =
        render(mRepository.observeCharacterContent(characterId))

    /**
     * 观察指定页面继承通用设置后的背景。
     * @param page 使用背景的页面类型。
     * @return 当前配置对应的背景资源。
     */
    fun observe(page: BackgroundPage): Flow<RenderedPageBackground> =
        render(mRepository.observeEffective(page))

    /** 图片加载失败不丢弃独立栏位设置，仍按主题底色显示页面。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun render(settingsFlow: Flow<PageBackground>): Flow<RenderedPageBackground> =
        settingsFlow.mapLatest { settings ->
            // 渲染任务随订阅取消，只有图像不可用时才回退到主题底色。
            try {
                mRuntime.renderBackground(settings)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                RenderedPageBackground(settings)
            } catch (_: OutOfMemoryError) {
                RenderedPageBackground(settings)
            }
        }
}
