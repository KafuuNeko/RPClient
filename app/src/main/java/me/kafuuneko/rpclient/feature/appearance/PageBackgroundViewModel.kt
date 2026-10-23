package me.kafuuneko.rpclient.feature.appearance

import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import me.kafuuneko.rpclient.feature.appearance.presentation.PageBackgroundUiState
import me.kafuuneko.rpclient.libs.appearance.BackgroundImageRuntime
import me.kafuuneko.rpclient.libs.appearance.BackgroundPage
import me.kafuuneko.rpclient.libs.appearance.PageBackground
import me.kafuuneko.rpclient.libs.room.repository.PageBackgroundRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 可复用背景子状态持有者，与消息及正文生成状态隔离。
 * - 宿主停止观察后取消图像工作，重新可见时从 Room 恢复最新设置。
 * - 图片失效时回退主题表面，不让外观故障影响阅读或编辑。
 */
class PageBackgroundViewModel : ViewModel(), KoinComponent {
    private val mRepository by inject<PageBackgroundRepository>()
    private val mRuntime by inject<BackgroundImageRuntime>()
    private val mStates = mutableMapOf<BackgroundPage, StateFlow<PageBackgroundUiState>>()

    /** 宿主只传角色标识流；切换角色或返回编辑页时响应最新背景关联。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeChat(characterIds: Flow<Long>): StateFlow<PageBackgroundUiState> =
        render(characterIds.distinctUntilChanged().flatMapLatest { mRepository.observeChat(it) })

    /** 为当前 Activity 的页面类型提供共享状态，避免每次重组重复创建加载链路。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(page: BackgroundPage): StateFlow<PageBackgroundUiState> = mStates.getOrPut(page) {
        render(mRepository.observeEffective(page))
    }

    /** 图片加载失败不丢弃独立栏位设置，仍按主题底色显示页面。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun render(settingsFlow: Flow<PageBackground>): StateFlow<PageBackgroundUiState> =
        settingsFlow.mapLatest { settings ->
            try {
                PageBackgroundUiState(
                    settings = settings,
                    image = mRuntime.render(settings)?.asImageBitmap(),
                    surfaceImages = mRuntime.renderSurfaces(settings)
                        .mapValues { entry -> entry.value.asImageBitmap() },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                PageBackgroundUiState(settings)
            } catch (_: OutOfMemoryError) {
                PageBackgroundUiState(settings)
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
            PageBackgroundUiState()
        )
}
