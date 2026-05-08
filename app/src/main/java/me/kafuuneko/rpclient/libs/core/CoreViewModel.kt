package me.kafuuneko.rpclient.libs.core

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class UiIntentObserver(val cla: KClass<*>)

typealias CoroutineBlock = suspend CoroutineScope.() -> Unit

data class AsyncTask<S>(
    val context: CoroutineContext,
    val restoreState: S,
    val block: CoroutineBlock
)

abstract class CoreViewModel<I, S>(initStatus: S) : ViewModel() {
    // Ui State (Model -> View)
    private val mUiStateFlow = MutableStateFlow(initStatus)
    val uiStateFlow = mUiStateFlow.asStateFlow()

    // Ui Intent (View -> Model)
    private val mUiIntentFlow = MutableSharedFlow<I>(extraBufferCapacity = 64)

    // UiIntent观察者函数映射缓存表
    private val mUiIntentObserverMap: MutableMap<KClass<*>, List<KFunction<*>>> = mutableMapOf()

    // 已注册的Live观察者与Live的关系表
    private val mForeverObservers: MutableMap<LiveData<*>, MutableSet<Observer<*>>> = mutableMapOf()

    // 异步协程工作流程
    private val mAsyncTaskChannel: Channel<AsyncTask<S>> = Channel(capacity = Channel.UNLIMITED)
    private var mCurrentAsyncTask: Pair<S, Job>? = null
    private val mAsyncQueueMutex = Mutex()

    /**
     * 此构造函数将扫描所有UiIntent观察者并缓存后启动UiIntent收集
     */
    init {
        doCacheUiIntentObservers()
        viewModelScope.launch { mUiIntentFlow.collect { onReceivedUiIntent(it) } }
        startLoopAsyncTask()
    }

    /**
     * ViewModel清理函数，将清理所有LiveData观察者
     */
    override fun onCleared() {
        super.onCleared()
        mForeverObservers.forEach { (liveData, observers) ->
            @Suppress("UNCHECKED_CAST")
            observers.forEach { observer ->
                (liveData as LiveData<Any>).removeObserver(observer as Observer<Any>)
            }
        }
        mForeverObservers.clear()
    }

    /**
     * 注册 observeForever 并自动在 onCleared 中移除
     */
    protected fun <T> LiveData<T>.observeForeverAutoRemove(observer: Observer<T>) {
        observeForever(observer)
        mForeverObservers.getOrPut(this) { mutableSetOf() }.add(observer)
    }

    /**
     * 扫描并缓存所有被UiIntentObserver注解的成员函数并存储
     */
    private fun doCacheUiIntentObservers() {
        val allFunctions = this::class.memberFunctions
        val observerMap: MutableMap<KClass<*>, MutableList<KFunction<*>>> = mutableMapOf()

        for (func in allFunctions) {
            val annotation = func.findAnnotation<UiIntentObserver>() ?: continue
            func.isAccessible = true
            observerMap.getOrPut(annotation.cla) { mutableListOf() }.add(func)
        }

        mUiIntentObserverMap.putAll(observerMap)
    }

    /**
     * 启动异步任务队列
     */
    private fun startLoopAsyncTask() = viewModelScope.launch {
        while (isActive) {
            val (context, restoreState, block) = mAsyncTaskChannel.receive()
            val job = launch(context = context, start = CoroutineStart.DEFAULT, block = block)
            mAsyncQueueMutex.withLock {
                mCurrentAsyncTask = restoreState to job
            }
            try {
                job.join()
            } finally {
                mAsyncQueueMutex.withLock {
                    if (mCurrentAsyncTask?.second === job) {
                        mCurrentAsyncTask = null
                    }
                }
            }
        }
    }

    /**
     * 取消当前的异步协程任务并将UIState还原
     */
    suspend fun cancelActiveTaskAndRestore() = mAsyncQueueMutex.withLock {
        val (state, job) = mCurrentAsyncTask ?: return@withLock
        if (job.isActive) {
            runCatching { job.cancel() }
        }
        mUiStateFlow.value = state
        mCurrentAsyncTask = null
    }

    /**
     * 将新的异步协程任务加入等待队列
     */
    suspend fun enqueueAsyncTask(
        context: CoroutineContext = Dispatchers.Default,
        restoreState: S = uiStateFlow.value,
        block: CoroutineBlock
    ) {
        mAsyncTaskChannel.send(AsyncTask(context, restoreState, block))
    }

    /**
     * 发送一个UI意图
     */
    fun emit(uiIntent: I) {
        mUiIntentFlow.tryEmit(uiIntent)
    }

    /**
     * 接收到ui意图并处理
     */
    private suspend fun onReceivedUiIntent(uiIntent: I) {
        val clazz = (uiIntent ?: return)::class
        val observers = mUiIntentObserverMap[clazz] ?: return
        for (func in observers) {
            // @formatter:off
            when (val size = func.parameters.size) {
                1 -> if (func.isSuspend) func.callSuspend(this) else func.call(this)
                2 -> if (func.isSuspend) func.callSuspend(this, uiIntent) else func.callSuspend(this, uiIntent)
                else -> throw IllegalArgumentException("Unsupported number of parameters: $size")
            }
            // @formatter:on
        }
    }

    /**
     * 将Flow转换成StateFlow，并默认在viewModelScope中启动收集
     */
    protected fun <T> Flow<T>.stateInThis(): StateFlow<T?> {
        return this.stateIn(viewModelScope, SharingStarted.Lazily, null)
    }

    /**
     * 获取指定类型UiState
     */
    protected inline fun <reified T : S> getOrNull(): T? = uiStateFlow.value as? T

    /**
     * 判断当前State类型
     */
    protected inline fun <reified T : S> isStateOf() = uiStateFlow.value is T

    /**
     * 等待uiState变更为指定状态类型后返回
     */
    protected suspend inline fun <reified T> awaitStateOf(): T {
        return uiStateFlow.filterIsInstance<T>().first()
    }

    /**
     * 等待uiState变更为指定状态类型且满足自定义条件后返回
     */
    protected suspend inline fun <reified T> awaitStateOf(
        crossinline predicate: suspend (T) -> Boolean
    ): T {
        return uiStateFlow.filterIsInstance<T>().filter(predicate).first()
    }

    /**
     * 将某个UiState设置为当前状态
     */
    protected fun S.setup() {
        mUiStateFlow.value = this
    }
}