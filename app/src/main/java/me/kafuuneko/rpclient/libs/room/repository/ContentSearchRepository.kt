package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.SearchChunk
import me.kafuuneko.rpclient.libs.room.entity.SearchProgress
import me.kafuuneko.rpclient.libs.room.entity.SearchSource
import me.kafuuneko.rpclient.libs.search.SearchIndexState
import me.kafuuneko.rpclient.libs.search.SearchSql
import me.kafuuneko.rpclient.libs.search.SearchTextCodec

/**
 * 可恢复的正文索引工作器，生命周期属于应用进程。
 * - 原始业务写入只记录轻量脏标记，索引异常不回滚已经保存的正文。
 * - 补建游标与源代次持久化，进程退出后继续；所有文字转换在数据库事务外完成。
 * - 分块提交并校验代次，旧任务不能覆盖编辑、删除或重新导入的内容。
 */
class ContentSearchRepository(private val mDatabase: AppDatabase, val queries: ContentSearchQueryRepository) {
    private val mDao = mDatabase.getContentSearchDao()
    private val mScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mJob: Job? = null
    private val mLifecycle = Mutex()
    private val mIndexState = MutableStateFlow<SearchIndexState>(SearchIndexState.Preparing)
    val indexState = mIndexState.asStateFlow()

    /** 启动单个工作器；生命周期锁保证停止、重试与新启动不会产生交叠的旧任务。 */
    fun start() {
        mScope.launch(start = CoroutineStart.UNDISPATCHED) {
            mLifecycle.withLock {
                if (mJob?.isActive == true) return@withLock
                mJob?.join()
                startWorker()
            }
        }
    }

    /** 停止并等待投影和事务退出，宿主随后可以安全关闭数据库。 */
    suspend fun stop() = mLifecycle.withLock {
        mJob?.cancelAndJoin()
        mJob = null
    }

    /** 显式恢复 FTS 并重新投影正文；损坏的 token 不会因仅重建 FTS 而永久保留。 */
    suspend fun retry() = mLifecycle.withLock {
        mJob?.cancelAndJoin()
        mIndexState.value = SearchIndexState.Unavailable
        withContext(Dispatchers.IO) {
            mDatabase.withTransaction {
                mDao.invalidateSources()
                mDao.invalidatePages()
                mDao.rebuildFts()
            }
        }
        startWorker()
    }

    /** 仅在生命周期锁内替换工作器；异常保留原文与持久待办。 */
    private fun startWorker() {
        mIndexState.value = SearchIndexState.Preparing
        mJob = mScope.launch {
            try {
                delay(500)
                prepareFormat()
                processQueue()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mIndexState.value = SearchIndexState.Unavailable
            }
        }
    }

    /** 格式变化先原子失效所有旧块，再由正常队列分批重建，避免一次复制整库。 */
    private suspend fun prepareFormat() = mDatabase.withTransaction {
        if (mDao.progress().firstOrNull { it.kind == -1 }?.cursor == SearchTextCodec.FORMAT_VERSION) return@withTransaction
        mDao.invalidateSources()
        mDao.invalidatePages()
        mDao.saveProgress(SearchProgress(-1, SearchTextCodec.FORMAT_VERSION, true))
    }

    /** 串行处理源快照；本批全部结束之后才允许回收墓碑，避免同 ID 重建的代次复用。 */
    private suspend fun processQueue() {
        var nextPrune = 0L
        // 重启后仍给已提交的删除留出整理机会，不依赖进程内的删除计数存活。
        var mergeRounds = 8
        while (currentCoroutineContext().isActive) {
            // 低空间时暂停派生写入，仍允许标题查询和用户主动选择的原文扫描。
            if (!hasStorageBudget()) {
                mIndexState.value = SearchIndexState.Unavailable
                delay(10_000)
                continue
            }
            val seeded = seedBatch()
            val pending = mDao.pending(System.currentTimeMillis() - 500)
            mIndexState.value = when {
                seeded -> SearchIndexState.Preparing
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
            if (!seeded && pending.isEmpty() && !mDao.hasPending() && mergeRounds > 0) {
                mDao.mergeFts()
                mergeRounds--
            }
            delay(if (seeded || pending.isNotEmpty()) 50 else 750)
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
        val (table, parent) = SearchSql.sourceTable(checkpoint.kind)
        val visible = if (checkpoint.kind == 1) "AND source != 'Summary'" else ""
        // 只读键，不把历史正文带入迁移或播种事务。
        val rows = mDao.sources(SimpleSQLiteQuery("""
            SELECT ${checkpoint.kind} kind, id sourceId, $parent parentId, 1 revision,
                0 indexedRevision, 0 deleted, 1 dirty, 0 changedTime FROM $table
            WHERE id > ? $visible ORDER BY id LIMIT 200
        """.trimIndent(), arrayOf(checkpoint.cursor)))
        mDatabase.withTransaction {
            mDao.seed(rows)
            mDao.saveProgress(checkpoint.copy(cursor = rows.lastOrNull()?.sourceId ?: checkpoint.cursor, complete = rows.size < 200))
        }
        return true
    }

    /** 计算可见正文副本并分块提交，最后确认的代次才对查询可见。 */
    private suspend fun indexSource(source: SearchSource) {
        currentCoroutineContext().ensureActive()
        val text = if (source.deleted) "" else mDao.readSearchText(source)
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
                if (!writeBatch(source, batch, replace, complete = false)) return
                replace = false
                batch.clear()
                batchBytes = 0
            }
            batch += SearchChunk(kind = source.kind, sourceId = source.sourceId, parentId = source.parentId,
                revision = source.revision, position = position, tokens = tokens)
            batchBytes += bytes
            if (batch.size == 16) {
                if (!writeBatch(source, batch, replace, complete = false)) return
                replace = false
                batch.clear()
                batchBytes = 0
            }
        }
        // 常见短消息把替换、写入和确认放进一个事务，长源仍分批且最后才发布。
        writeBatch(source, batch, replace, complete = true)
    }

    private suspend fun isCurrent(source: SearchSource): Boolean =
        mDao.source(source.kind, source.sourceId)?.revision == source.revision

    /** 替换首批时删除旧块，末批才确认；每批都检查代次与空间预算。 */
    private suspend fun writeBatch(
        source: SearchSource, chunks: List<SearchChunk>, replace: Boolean, complete: Boolean
    ): Boolean = mDatabase.withTransaction {
        if (!isCurrent(source) || (chunks.isNotEmpty() && !hasStorageBudget())) return@withTransaction false
        if (replace) mDao.deleteChunks(source.kind, source.sourceId)
        if (chunks.isNotEmpty()) mDao.insertChunks(chunks)
        if (complete && mDao.acknowledge(source.kind, source.sourceId, source.revision) != 0) mDao.invalidatePages()
        true
    }

    private companion object {
        // 限制单批编码量，调整块大小时不会把写事务和 WAL 峰值同步翻倍。
        const val BATCH_BYTES = 128 * 1024
    }
}
