package me.kafuuneko.rpclient.libs.core

import android.content.Intent
import android.os.Bundle
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 支持一次性 ViewEvent 的 MVI ViewModel 基类。
 *
 * 可持久状态继续使用 UiState；Toast、导航和系统能力调用通过独立事件流发送，
 * 避免配置变化后因状态重放而重复执行。
 */
abstract class CoreViewModelWithEvent<I, S>(initStatus: S) : CoreViewModel<I, S>(initStatus) {
    /** 事件流使用包装器保证同一个事件最多消费一次。 */
    private val mViewEventFlow = MutableSharedFlow<ViewEventWrapper>(extraBufferCapacity = 64)
    val viewEventFlow = mViewEventFlow.asSharedFlow()

    /**
     * 尝试分发UI Event（一次性事件）
     */
    protected fun IViewEvent.tryEmit(): Boolean {
        return mViewEventFlow.tryEmit(ViewEventWrapper(this))
    }

    /**
     * 分发UI Event，缓冲区满则等待
     */
    protected suspend fun IViewEvent.emit() {
        mViewEventFlow.emit(ViewEventWrapper(this))
    }

    /**
     * 发一个 UI Event, 并等待其事件消费完成
     */
    protected suspend fun IViewEvent.emitAndAwait() {
        ViewEventWrapper(this)
            .apply { mViewEventFlow.emit(this) }
            .waitForConsumption()
    }
}

/** 带并发消费保护和消费完成信号的一次性事件包装器。 */
class ViewEventWrapper(private val content: IViewEvent) {
    private val mMutex = Mutex()
    private val mHasHandled = MutableStateFlow(false)

    suspend fun consumeIfNotHandled(handle: suspend (IViewEvent) -> Unit) = mMutex.withLock {
        if (mHasHandled.value) return@withLock false
        handle(content)
        mHasHandled.value = true
        return@withLock true
    }

    fun isHandled() = mHasHandled.value

    suspend fun waitForConsumption() {
        if (mHasHandled.value) return
        mHasHandled.first { it }
    }
}

/** 所有页面级一次性事件的标记接口。 */
interface IViewEvent

/** CoreActivityWithEvent 可统一处理的应用通用事件。 */
sealed class AppViewEvent : IViewEvent {
    data class PopupToastMessage(val message: String) : AppViewEvent()
    data class PopupToastMessageByResId(@StringRes val message: Int) : AppViewEvent()
    data class StartActivity(val activity: Class<*>, val extras: Bundle? = null) : AppViewEvent()
    data class StartActivityByIntent(val intent: Intent) : AppViewEvent()
    data class SetResult(val resultCode: Int, val intent: Intent? = null) : AppViewEvent()
}
