package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.SearchChunk
import me.kafuuneko.rpclient.libs.room.entity.SearchSource
import me.kafuuneko.rpclient.libs.search.SearchCursor
import me.kafuuneko.rpclient.libs.search.SearchDatabaseCallback
import me.kafuuneko.rpclient.libs.search.SearchHit
import me.kafuuneko.rpclient.libs.search.SearchIndexPhase
import me.kafuuneko.rpclient.libs.search.SearchIndexState
import me.kafuuneko.rpclient.libs.search.SearchPage
import me.kafuuneko.rpclient.libs.search.SearchQueryValidation
import me.kafuuneko.rpclient.libs.search.SearchRequest
import me.kafuuneko.rpclient.libs.search.SearchTextCodec
import me.kafuuneko.rpclient.libs.search.query.SearchQueryCoordinator
import me.kafuuneko.rpclient.libs.search.storage.SearchStorageMaintenance
import me.kafuuneko.rpclient.libs.search.storage.readSearchText
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/**
 * 内容搜索的业务入口，统一提供输入校验、分页查询、目标复核与索引管理。
 * - 私有持有查询协调器，查询与索引切换共享取消和分页失效机制，调用方不接触内部策略。
 * - 索引工作器属于应用进程，页面重建不影响正在执行的构建或清理。
 * - 默认关闭；用户确认开启后全量构建，成功后才开放索引搜索。
 * - 启用期间原始业务写入记录轻量脏标记，后台失败不回滚已经保存的正文。
 * - 首次构建中断后清理；已启用的增量待办可恢复，所有文字转换在数据库事务外完成。
 * - 分块提交并校验代次，旧任务不能覆盖编辑、删除或重新导入的内容。
 */
class ContentSearchRepository(private val mDatabase: AppDatabase) {
    private val mQueries = SearchQueryCoordinator(mDatabase)
    private val mDao = mDatabase.getContentSearchDao()
    private val mSourceDao = mDatabase.getSearchSourceDao()
    private val mScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mJob: Job? = null
    private val mLifecycle = Mutex()
    private var mInitialized = false
    private val mIndexState = MutableStateFlow<SearchIndexState>(SearchIndexState.Preparing)
    val indexState = mIndexState.asStateFlow()

    /**
     * 预检原始输入，不访问数据库；页面只负责将校验结果映射为提示。
     * - 先限制原始 UTF-16 长度，再按规范化后的码点数限制查询，避免超大粘贴输入阻塞页面。
     * - 查询长度与正文分块重叠范围一致，防止跨块命中丢失。
     *
     * @param query 用户输入的原始文字，无需调用方预先规范化。
     * @return 规范化后的有效查询，或空白、超长原因。
     */
    fun validateQuery(query: String): SearchQueryValidation {
        if (query.length > MAX_RAW_QUERY_LENGTH) return SearchQueryValidation.TooLong
        val normalized = SearchTextCodec.normalize(query)
        if (normalized.isBlank()) return SearchQueryValidation.Empty
        if (normalized.codePointCount(beginIndex = 0, endIndex = normalized.length) > SearchTextCodec.MAX_QUERY_POINTS) return SearchQueryValidation.TooLong
        return SearchQueryValidation.Valid(normalized)
    }

    /**
     * 查询一页内容，自动选择原文扫描或已启用的 FTS；始终在入口校验原始输入。
     *
     * @param request 固定的原始查询及范围；翻页时保持与首页一致。
     * @param cursor 上一页返回的游标；数据或模式变化后须从首页重新查询。
     * @param onPartial 当前页已找到的累计结果，在 IO 上下文调用；取消后不再发布结果。
     * @return 当前页、下一页游标与完整性状态。
     * @throws IllegalArgumentException 输入为空白或超过长度限制。
     */
    suspend fun search(
        request: SearchRequest,
        cursor: SearchCursor? = null,
        onPartial: suspend (List<SearchHit>) -> Unit = {},
    ): SearchPage {
        val validation = validateQuery(request.query)
        require(validation is SearchQueryValidation.Valid) { "Invalid search query" }
        return mQueries.search(request.copy(query = validation.normalizedQuery), cursor, onPartial)
    }

    /**
     * 打开结果前按当前业务数据复核归属及命中，不要求索引可用。
     *
     * @param hit 待打开的搜索结果。
     * @param query 原始查询；留空时只检查目标存在及归属。
     * @return 目标是否仍可打开；正文或章节标题结果还会复核非空查询。
     */
    suspend fun exists(hit: SearchHit, query: String = ""): Boolean = mQueries.exists(hit, query)

    /** 恢复已启用的增量维护或未完成清理，绝不替未确认用户构建索引。 */
    fun restore() {
        mScope.launch(start = CoroutineStart.UNDISPATCHED) { control { initializeLocked() } }
    }

    /** 显式确认后开始构建；调用返回后由应用作用域持有任务，页面重建不丢失进度。 */
    suspend fun enable() = control {
        initializeLocked()
        val phase = SearchDatabaseCallback.phase(mDatabase.openHelper.writableDatabase)
        if (phase == SearchIndexPhase.Disabled) beginBuildLocked()
    }

    /** 取消或关闭：先作废查询，等待旧工作器退出，再清理并回收空间。 */
    suspend fun disable() = control {
        mQueries.invalidate()
        mIndexState.value = SearchIndexState.Cleaning
        mJob?.cancelAndJoin()
        mJob = null
        mInitialized = true
        cleanLocked()
    }

    /** 停止进程内任务但保留持久阶段，供宿主退出及文件库恢复验证使用。 */
    suspend fun stop() = mLifecycle.withLock {
        mJob?.cancelAndJoin()
        mJob = null
        mInitialized = false
    }

    /** 重建作为一个串行操作；排队的取消会在构建启动后停止它，不会被重建尾部重新开启。 */
    suspend fun retry() = control {
        mQueries.invalidate()
        mJob?.cancelAndJoin()
        mJob = null
        mInitialized = true
        cleanLocked()
        if (mIndexState.value == SearchIndexState.Disabled) beginBuildLocked()
    }

    /** 按调用顺序进入互斥队列，再切回 IO；快速取消不能先于尚未调度的开启执行。 */
    private suspend fun control(action: suspend () -> Unit) = mScope.async(start = CoroutineStart.UNDISPATCHED) {
        mLifecycle.withLock {
            // 首次进入可能在主线程，先让出到本作用域的 IO 调度器再读写数据库。
            yield()
            action()
        }
    }.await()

    /** 显式操作已持有生命周期锁，安装失败只留下可重试的清理阶段。 */
    private suspend fun beginBuildLocked() {
        mQueries.invalidate()
        mIndexState.value = SearchIndexState.Building()
        try {
            // 状态与维护触发器同事务提交，后台保存从此纳入本次构建。
            mDatabase.withTransaction { SearchDatabaseCallback.beginBuild(mDatabase.openHelper.writableDatabase) }
            startWorker()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            cleanLocked(failed = true)
        }
    }

    /** 仅在生命周期锁内恢复，已启用的持久待办继续处理，构建中断则清理。 */
    private suspend fun initializeLocked() {
        if (mInitialized) return
        mInitialized = true
        try {
            when (SearchDatabaseCallback.phase(mDatabase.openHelper.writableDatabase)) {
                SearchIndexPhase.Enabled -> startWorker()
                SearchIndexPhase.Disabled -> mIndexState.value = SearchIndexState.Disabled
                else -> cleanLocked()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mIndexState.value = SearchIndexState.CleanupFailed
        }
    }

    /** 只有旧工作器退出后才可调用；保留 Cleaning 直到逻辑清理和文件回收全部成功。 */
    private suspend fun cleanLocked(failed: Boolean = false) {
        mQueries.invalidate()
        mIndexState.value = SearchIndexState.Cleaning
        try {
            mDatabase.withTransaction {
                val db = mDatabase.openHelper.writableDatabase
                SearchDatabaseCallback.setPhase(db, SearchIndexPhase.Cleaning)
                SearchDatabaseCallback.uninstall(db)
            }
            // 分批释放正文投影，再重建空 FTS，清除外部内容表之外的倒排内部数据。
            while (mDao.deleteChunkBatch() != 0) currentCoroutineContext().ensureActive()
            while (mDao.deleteSourceBatch() != 0) currentCoroutineContext().ensureActive()
            mDatabase.withTransaction {
                mDao.rebuildFts()
                mDao.clearProgress()
            }
            SearchStorageMaintenance(mDatabase).reclaim()
            mDatabase.withTransaction {
                SearchDatabaseCallback.setPhase(mDatabase.openHelper.writableDatabase, SearchIndexPhase.Disabled)
            }
            mIndexState.value = if (failed) SearchIndexState.Unavailable else SearchIndexState.Disabled
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mIndexState.value = SearchIndexState.CleanupFailed
        }
    }

    /** 工作器不获取生命周期锁，避免关闭等待任务时发生非重入死锁。 */
    private fun startWorker() {
        mJob = mScope.launch {
            try {
                delay(timeMillis = 100)
                processQueue()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val failedJob = currentCoroutineContext().job
                mQueries.invalidate()
                mIndexState.value = SearchIndexState.Cleaning
                // 在工作器退出后串行清理；较新的取消或重建操作具有优先权。
                mScope.launch {
                    mLifecycle.withLock {
                        if (mJob === failedJob) {
                            failedJob.join()
                            mJob = null
                            cleanLocked(failed = true)
                        }
                    }
                }
            }
        }
    }

    /** 串行处理源快照；本批全部结束之后才允许回收墓碑，避免同 ID 重建的代次复用。 */
    private suspend fun processQueue() {
        var nextPrune = 0L
        // 重启后仍给已提交的删除留出整理机会，不依赖进程内的删除计数存活。
        var mergeRounds = 8
        while (currentCoroutineContext().isActive) {
            check(hasStorageBudget()) { "Insufficient storage for search index" }
            val seeded = seedBatch()
            val pending = mDao.pending(System.currentTimeMillis() - 500)
            // 完成判定与持久启用同事务，构建期间的新增待办也必须全部确认。
            val building = mDatabase.withTransaction {
                val db = mDatabase.openHelper.writableDatabase
                val phase = SearchDatabaseCallback.phase(db)
                if (phase == SearchIndexPhase.Building && !seeded && !mDao.hasPending()) {
                    SearchDatabaseCallback.setPhase(db, SearchIndexPhase.Enabled)
                    mQueries.invalidate()
                    false
                } else phase == SearchIndexPhase.Building
            }
            mIndexState.value = when {
                building -> SearchIndexState.Building(mDao.indexedSourceCount())
                pending.isNotEmpty() || mDao.hasPending() -> SearchIndexState.Updating
                else -> SearchIndexState.Ready
            }
            pending.forEach { indexSource(it) }
            if (pending.any { it.deleted || it.indexedRevision > 0 }) mergeRounds = 8
            // 此处不再使用本批快照；播种完成后才清理，重试也必须等待本工作器退出。
            if (!seeded && (pending.any { it.deleted } || System.currentTimeMillis() >= nextPrune)) {
                val removed = mDao.pruneDeletedSources()
                nextPrune = if (removed == 256) 0 else System.currentTimeMillis() + 60_000
            }
            val canMergeIdleIndex = !seeded
                && pending.isEmpty()
                && !mDao.hasPending()
                && mergeRounds > 0
            if (canMergeIdleIndex) {
                mDao.mergeFts()
                mergeRounds--
            }
            delay((if (seeded || pending.isNotEmpty()) 50 else 750).milliseconds)
        }
    }

    /** 为业务写入保留余量；仅降低索引争抢磁盘的风险，不承诺磁盘耗尽时仍可写入。 */
    private fun hasStorageBudget(): Boolean {
        val path = mDatabase.openHelper.writableDatabase.path
        if (path == null || path == ":memory:") return true
        return File(path).parentFile?.usableSpace?.let { it >= 64L * 1024 * 1024 + BATCH_BYTES * 8 } == true
    }

    /** 分批登记历史源，检查点与登记必须同事务提交。 */
    private suspend fun seedBatch(): Boolean {
        val checkpoint = mDao.progress().firstOrNull { it.kind > 0 && !it.complete } ?: return false
        // 只读键，不把历史正文带入迁移或播种事务。
        val rows = mSourceDao.sourceBatch(
            kind = checkpoint.kind,
            afterId = checkpoint.cursor,
            parentId = null,
            limit = 200,
        )
        mDatabase.withTransaction {
            mDao.seed(rows)
            mDao.saveProgress(
                value = checkpoint.copy(
                    cursor = rows.lastOrNull()?.sourceId ?: checkpoint.cursor,
                    complete = rows.size < 200,
                ),
            )
        }
        return true
    }

    /** 计算可见正文副本并分块提交，最后确认的代次才对查询可见。 */
    private suspend fun indexSource(source: SearchSource) {
        currentCoroutineContext().ensureActive()
        val text = if (source.deleted) "" else mSourceDao.readSearchText(source)
        val normalized = withContext(Dispatchers.Default) {
            SearchTextCodec.normalize(SearchTextCodec.project(text))
        }
        // 开始和每次提交都核对版本，避免源读取期间的并发编辑产生错误索引。
        if (!isCurrent(source)) return
        val batch = ArrayList<SearchChunk>(16)
        var batchBytes = 0
        var replace = true
        for ((position, chunk) in SearchTextCodec.chunks(normalized)) {
            currentCoroutineContext().ensureActive()
            val tokens = SearchTextCodec.tokens(chunk)
            val bytes = tokens.toByteArray(Charsets.UTF_8).size
            if (batch.isNotEmpty() && batchBytes + bytes > BATCH_BYTES) {
                if (!writeBatch(source = source, chunks = batch, replace = replace, complete = false)) return
                replace = false
                batch.clear()
                batchBytes = 0
            }
            batch += SearchChunk(
                kind = source.kind,
                sourceId = source.sourceId,
                parentId = source.parentId,
                revision = source.revision,
                position = position,
                tokens = tokens,
            )
            batchBytes += bytes
            if (batch.size == 16) {
                if (!writeBatch(source = source, chunks = batch, replace = replace, complete = false)) return
                replace = false
                batch.clear()
                batchBytes = 0
            }
        }
        // 常见短消息把替换、写入和确认放进一个事务，长源仍分批且最后才发布。
        writeBatch(source = source, chunks = batch, replace = replace, complete = true)
    }

    private suspend fun isCurrent(source: SearchSource): Boolean =
        mDao.source(source.kind, source.sourceId)?.revision == source.revision

    /** 替换首批时删除旧块，末批才确认；每批都检查代次与空间预算。 */
    private suspend fun writeBatch(
        source: SearchSource,
        chunks: List<SearchChunk>,
        replace: Boolean,
        complete: Boolean,
    ): Boolean = mDatabase.withTransaction {
        if (!isCurrent(source) || (chunks.isNotEmpty() && !hasStorageBudget())) return@withTransaction false
        if (replace) mDao.deleteChunks(source.kind, source.sourceId)
        if (chunks.isNotEmpty()) mDao.insertChunks(chunks)
        if (complete && mDao.acknowledge(source.kind, source.sourceId, source.revision) != 0) mDao.invalidatePages()
        true
    }

    private companion object {
        const val MAX_RAW_QUERY_LENGTH = 4096
        // 限制单批编码量，调整块大小时不会把写事务和 WAL 峰值同步翻倍。
        const val BATCH_BYTES = 128 * 1024
    }
}
