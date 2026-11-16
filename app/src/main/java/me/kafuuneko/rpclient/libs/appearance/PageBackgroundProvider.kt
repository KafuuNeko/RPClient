package me.kafuuneko.rpclient.libs.appearance

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.transformLatest
import me.kafuuneko.rpclient.libs.room.repository.PageBackgroundRepository

/**
 * 订阅已保存的背景配置，通过图像管线提供底图与栏位图片。
 * - 返回冷流，由页面的可见生命周期控制订阅和图像任务，底图就绪后先发布。
 * - 图片失效时回退主题表面，不让外观故障影响阅读或编辑。
 */
class PageBackgroundProvider(
    private val mRepository: PageBackgroundRepository,
    private val mRuntime: BackgroundImageRuntime,
) {

    /**
     * 按已确认的会话角色订阅单聊背景，切换角色时取消上一个订阅。
     * @param characterIds 当前会话已确认的角色 ID 流。
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

    /**
     * 先发布底图，再补齐局部模糊；派生图失败时保留已显示的底图。
     *
     * @param settingsFlow 已解析最终来源的背景配置。
     * @return 底图就绪及栏位派生图就绪时依次发布的背景资源。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun render(settingsFlow: Flow<PageBackground>): Flow<RenderedPageBackground> =
        settingsFlow.distinctUntilChanged().transformLatest { settings ->
            // 没有配置图片或图片不可用也会发布资源，首帧无需等待不存在的图片。
            val background = try {
                RenderedPageBackground(settings = settings, image = mRuntime.render(settings))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                RenderedPageBackground(settings)
            } catch (_: OutOfMemoryError) {
                RenderedPageBackground(settings)
            }
            emit(background)
            if (background.image == null) return@transformLatest

            // 局部模糊不阻塞整页底图；计算失败也不清空已经可用的背景。
            val surfaces = try {
                mRuntime.renderSurfaces(settings)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyMap()
            } catch (_: OutOfMemoryError) {
                emptyMap()
            }
            if (surfaces.isNotEmpty()) emit(background.copy(surfaceImages = surfaces))
        }
}
