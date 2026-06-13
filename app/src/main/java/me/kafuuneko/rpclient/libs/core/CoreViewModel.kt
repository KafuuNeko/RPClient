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

/**
 * 标记处理指定 UiIntent 类型的 ViewModel 成员函数。
 *
 * 被标记函数可无参数或接收一个对应意图参数，并可声明为 suspend。
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class UiIntentObserver(val cla: KClass<*>)

/** 可在指定协程上下文中执行的 ViewModel 工作单元。 */
typealias CoroutineBlock = suspend CoroutineScope.() -> Unit

/** 串行异步任务及取消时需要恢复的 UI 状态。 */
data class AsyncTask<S>(
    val context: CoroutineContext,
    val restoreState: S,
    val block: CoroutineBlock
)

/**
 * 项目 MVI ViewModel 基类。
 *
 * 通过 [UiIntentObserver] 反射建立 Intent 到处理函数的映射，以 StateFlow 单向发布状态；
 * 同时提供串行异步任务队列和自动清理的 LiveData 永久观察能力。
 */
abstract class CoreViewModel<I, S>(initStatus: S) : ViewModel() {
    /** Model 向 View 发布的唯一状态源。 */
    private val mUiStateFlow = MutableStateFlow(initStatus)
    val uiStateFlow = mUiStateFlow.asStateFlow()

    /** View 向 Model 发送的意图流，短时突发输入由额外缓冲区吸收。 */
    private val mUiIntentFlow = MutableSharedFlow<I>(extraBufferCapacity = 64)

    /** Intent 类型到观察函数的反射缓存，仅在初始化时构建。 */
    private val mUiIntentObserverMap: MutableMap<KClass<*>, List<KFunction<*>>> = mutableMapOf()

    /** 需要在 onCleared 时解除的 observeForever 关系。 */
    private val mForeverObservers: MutableMap<LiveData<*>, MutableSet<Observer<*>>> = mutableMapOf()

    /** 严格串行执行的异步任务队列及当前任务快照。 */
    private val mAsyncTaskChannel: Channel<AsyncTask<S>> = Channel(capacity = Channel.UNLIMITED)
    private var mCurrentAsyncTask: Pair<S, Job>? = null
    private val mAsyncQueueMutex = Mutex()

    /** 初始化时缓存 Intent 观察者，并启动 Intent 收集和串行任务循环。 */
    init {
        doCacheUiIntentObservers()
        viewModelScope.launch { mUiIntentFlow.collect { onReceivedUiIntent(it) } }
        startLoopAsyncTask()
    }

    /** 清理由 [observeForeverAutoRemove] 注册的所有永久观察者。 */
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

    /** 扫描继承层级中的观察函数并按 Intent 类型分组缓存。 */
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

    /** 启动单消费者任务循环，保证加入队列的任务不会并发修改 UI 状态。 */
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

    /** 取消当前串行任务，并恢复该任务入队时记录的状态。 */
    suspend fun cancelActiveTaskAndRestore() = mAsyncQueueMutex.withLock {
        val (state, job) = mCurrentAsyncTask ?: return@withLock
        if (job.isActive) {
            runCatching { job.cancel() }
        }
        mUiStateFlow.value = state
        mCurrentAsyncTask = null
    }

    /** 将任务加入串行队列；默认以当前状态作为取消恢复点。 */
    suspend fun enqueueAsyncTask(
        context: CoroutineContext = Dispatchers.Default,
        restoreState: S = uiStateFlow.value,
        block: CoroutineBlock
    ) {
        mAsyncTaskChannel.send(AsyncTask(context, restoreState, block))
    }

    /** 非阻塞发送 UI 意图；缓冲区已满时返回前会丢弃本次发送结果。 */
    fun emit(uiIntent: I) {
        mUiIntentFlow.tryEmit(uiIntent)
    }

    /** 按运行时类型调用所有匹配的 Intent 观察函数。 */
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

    /** 在 viewModelScope 中将冷 Flow 懒启动为可观察 StateFlow。 */
    protected fun <T> Flow<T>.stateInThis(): StateFlow<T?> {
        return this.stateIn(viewModelScope, SharingStarted.Lazily, null)
    }

    /** 当前状态类型匹配时返回该状态，否则返回 null。 */
    protected inline fun <reified T : S> getOrNull(): T? = uiStateFlow.value as? T

    /** 判断当前状态是否为指定子类型。 */
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

    /** 发布新的完整 UI 状态。 */
    protected fun S.setup() {
        mUiStateFlow.value = this
    }
}
