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
 * 带 UiEvent 的 CoreViewModel
 */
abstract class CoreViewModelWithEvent<I, S>(initStatus: S) : CoreViewModel<I, S>(initStatus) {
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

interface IViewEvent

sealed class AppViewEvent : IViewEvent {
    data class PopupToastMessage(val message: String) : AppViewEvent()
    data class PopupToastMessageByResId(@StringRes val message: Int) : AppViewEvent()
    data class StartActivity(val activity: Class<*>, val extras: Bundle? = null) : AppViewEvent()
    data class StartActivityByIntent(val intent: Intent) : AppViewEvent()
    data class SetResult(val resultCode: Int, val intent: Intent? = null) : AppViewEvent()
}