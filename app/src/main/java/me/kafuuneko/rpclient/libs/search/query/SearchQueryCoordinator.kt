package me.kafuuneko.rpclient.libs.search.query

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.search.SearchChangedException
import me.kafuuneko.rpclient.libs.search.SearchCursor
import me.kafuuneko.rpclient.libs.search.SearchDatabaseCallback
import me.kafuuneko.rpclient.libs.search.SearchEngineKind
import me.kafuuneko.rpclient.libs.search.SearchHit
import me.kafuuneko.rpclient.libs.search.SearchIndexPhase
import me.kafuuneko.rpclient.libs.search.SearchPage
import me.kafuuneko.rpclient.libs.search.SearchRequest
import me.kafuuneko.rpclient.libs.search.engine.ContentSearchEngine
import me.kafuuneko.rpclient.libs.search.engine.DatabaseScanSearchEngine
import me.kafuuneko.rpclient.libs.search.engine.FtsSearchEngine
import me.kafuuneko.rpclient.libs.search.storage.SearchDataRevision

/**
 * 仓库私有持有的查询协调器，负责策略路由与查询会话。
 * - 默认扫描业务原文，只有完整启用后才路由到 FTS。
 * - 模式切换取消旧查询；分页绑定策略和本次管理会话，关闭再开启也不能复用旧游标。
 */
internal class SearchQueryCoordinator(private val mDatabase: AppDatabase) {
    private val mSupport = SearchQuerySupport(mDatabase)
    private val mScan by lazy { DatabaseScanSearchEngine(mDatabase, mSupport, SearchDataRevision(mDatabase)) }
    private val mFts = FtsSearchEngine(mDatabase, mSupport)
    private val mJobs = ConcurrentHashMap.newKeySet<Job>()
    @Volatile private var mSession = UUID.randomUUID().toString()

    /** 切换或清理前同步作废所有旧请求；只取消本协调器创建的查询子协程。 */
    fun invalidate() {
        mSession = UUID.randomUUID().toString()
        mJobs.forEach { it.cancel() }
    }

    /** 接收仓库已校验、规范化的查询；两种策略共享部分结果与取消语义。 */
    suspend fun search(
        request: SearchRequest, cursor: SearchCursor? = null,
        onPartial: suspend (List<SearchHit>) -> Unit = {}
    ): SearchPage = withContext(Dispatchers.IO) {
        coroutineScope {
            val job = coroutineContext.job
            val session = mSession
            mJobs.add(job)
            try {
                val phase = SearchDatabaseCallback.phase(mDatabase.openHelper.readableDatabase)
                if (phase == SearchIndexPhase.Building || phase == SearchIndexPhase.Cleaning) throw SearchChangedException()
                val kind = if (phase == SearchIndexPhase.Enabled && !request.directScan) SearchEngineKind.Fts else SearchEngineKind.Scan
                if (session != mSession || cursor != null && (cursor.session != session || cursor.engine != kind)) throw SearchChangedException()
                val engine: ContentSearchEngine = if (kind == SearchEngineKind.Fts) mFts else mScan
                // 回调和最终返回都检查管理会话，避免切换后旧结果回写。
                val page = engine.search(request, cursor) {
                    currentCoroutineContext().ensureActive()
                    if (session != mSession) throw SearchChangedException()
                    onPartial(it)
                }
                currentCoroutineContext().ensureActive()
                if (session != mSession) throw SearchChangedException()
                page.copy(next = page.next?.copy(engine = kind, session = session))
            } finally {
                mJobs.remove(job)
            }
        }
    }

    /** 打开前按当前业务原文复核归属与命中，不依赖索引可用性。 */
    suspend fun exists(hit: SearchHit, query: String = ""): Boolean = withContext(Dispatchers.IO) {
        mSupport.exists(hit, query)
    }
}
