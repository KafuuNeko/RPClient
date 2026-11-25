package me.kafuuneko.rpclient.feature.contentsearch

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.chat.ChatActivity
import me.kafuuneko.rpclient.feature.contentsearch.presentation.ContentSearchInputState
import me.kafuuneko.rpclient.feature.contentsearch.presentation.ContentSearchResultState
import me.kafuuneko.rpclient.feature.contentsearch.presentation.ContentSearchScopeState
import me.kafuuneko.rpclient.feature.contentsearch.presentation.ContentSearchUiIntent
import me.kafuuneko.rpclient.feature.contentsearch.presentation.ContentSearchUiState
import me.kafuuneko.rpclient.feature.contentsearch.presentation.items
import me.kafuuneko.rpclient.feature.contentsearch.presentation.nextCursor
import me.kafuuneko.rpclient.feature.groupchat.GroupChatActivity
import me.kafuuneko.rpclient.feature.main.MainActivity
import me.kafuuneko.rpclient.feature.main.model.Route
import me.kafuuneko.rpclient.feature.storyeditor.StoryEditorActivity
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.repository.ContentSearchRepository
import me.kafuuneko.rpclient.libs.search.SearchChangedException
import me.kafuuneko.rpclient.libs.search.SearchCursor
import me.kafuuneko.rpclient.libs.search.SearchHit
import me.kafuuneko.rpclient.libs.search.SearchKind
import me.kafuuneko.rpclient.libs.search.SearchNavigation
import me.kafuuneko.rpclient.libs.search.SearchQueryValidation
import me.kafuuneko.rpclient.libs.search.SearchRequest
import me.kafuuneko.rpclient.libs.search.SearchScope
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.TimeSource

/**
 * 独立搜索页的 MVI 状态持有者。
 * - 输入草稿与已提交查询分离，长查询不占用串行 Intent 消费器。
 * - 每次提交、编辑及取消递增请求代次，阻止过期查询覆盖新状态。
 * - 短查询使用宽限期延迟展示加载态，慢扫描节流推送部分结果，避免搜索切换与扫描期间界面闪烁。
 * - 保存轻量草稿与范围，正文与结果不写入 SavedStateHandle。
 */
class ContentSearchViewModel(private val mSavedState: SavedStateHandle) :
    CoreViewModelWithEvent<ContentSearchUiIntent, ContentSearchUiState>(ContentSearchUiState.None),
    KoinComponent {
    private val mRepository by inject<ContentSearchRepository>()
    private var mSearchJob: Job? = null
    private var mOpenJob: Job? = null
    private var mGeneration = 0L
    private var mDirectScan = false
    private var mRefreshOnResume = false
    private var mActiveExisting: List<SearchHit> = emptyList()
    private var mPendingPartialHits: List<SearchHit>? = null

    /**
     * 初始化页面并观察工作器；恢复后等待显式提交，不自动发起昂贵查询。
     * @param intent 进入搜索页时指定的初始结果分类。
     */
    @UiIntentObserver(ContentSearchUiIntent.Init::class)
    private fun onInit(intent: ContentSearchUiIntent.Init) {
        if (!isStateOf<ContentSearchUiState.None>()) return
        // 读取已持久化的筛选条件，并直接同步当前索引快照避免首帧闪现 Preparing。
        val kind = SearchKind.entries.firstOrNull { it.code == mSavedState.get<Int>("kind") }
            ?: intent.kind
        val scope = SearchScope.entries.firstOrNull { it.name == mSavedState.get<String>("scope") }
            ?: SearchScope.All
        ContentSearchUiState.Normal(
            inputState = ContentSearchInputState(draft = mSavedState["query"] ?: ""),
            scopeState = ContentSearchScopeState(kind, scope),
            indexState = mRepository.indexState.value,
        ).setup()
        // 仅恢复持久状态；进入搜索页不代表用户确认开启。
        mRepository.restore()
        viewModelScope.launch {
            mRepository.indexState.collect { index ->
                val current = getOrNull<ContentSearchUiState.Normal>() ?: return@collect
                if (current.indexState == index) return@collect
                val changed = current.indexState.enabled != index.enabled
                    || current.indexState.busy != index.busy
                if (changed) {
                    cancelQuery()
                    mDirectScan = false
                }
                current.copy(
                    indexState = index,
                    resultState = if (changed) ContentSearchResultState.Idle else current.resultState,
                    resultParentId = if (changed) null else current.resultParentId,
                    isReplacingResults = !changed && current.isReplacingResults,
                ).setup()
            }
        }
    }

    /** 从目标页面返回后提示刷新，保留输入、结果和滚动位置。 */
    @UiIntentObserver(ContentSearchUiIntent.Resume::class)
    private fun onResume() {
        val state = interactiveState() ?: return
        if (!mRefreshOnResume) return
        mRefreshOnResume = false
        state.copy(
            resultState = ContentSearchResultState.Error(
                items = state.resultState.items(),
                message = R.string.content_search_changed,
            ),
        ).setup()
    }

    /**
     * 编辑立即使旧请求失效，但不在按键时扫描正文。
     * @param intent 用户编辑后尚未提交的查询正文。
     */
    @UiIntentObserver(ContentSearchUiIntent.ChangeQuery::class)
    private fun onChangeQuery(intent: ContentSearchUiIntent.ChangeQuery) {
        val state = interactiveState() ?: return
        if (intent.value == state.inputState.draft) return
        cancelQuery()
        // 恢复草稿的体积预算独立于查询有效性，尚未提交的无效输入也可以恢复。
        mSavedState["query"] = intent.value.takeIf { it.length <= MAX_SAVED_DRAFT_LENGTH }.orEmpty()
        state.copy(
            inputState = state.inputState.copy(draft = intent.value),
            resultState = ContentSearchResultState.Idle,
            resultParentId = null,
            isReplacingResults = false,
        ).setup()
    }

    /**
     * 已提交且未编辑时切分类自动重查，其余情况只更新草稿范围。
     * @param intent 用户切换到的结果分类。
     */
    @UiIntentObserver(ContentSearchUiIntent.SelectKind::class)
    private fun onSelectKind(intent: ContentSearchUiIntent.SelectKind) {
        val state = interactiveState() ?: return
        val nextScope = state.scopeState.copy(
            kind = intent.kind,
            parentId = null,
            parentTitle = "",
        )
        if (nextScope == state.scopeState) return
        updateScope(nextScope)
    }

    /**
     * 切换标题／正文范围保留查询，避免无意触发未提交文本。
     * @param intent 用户选择的标题或正文检索范围。
     */
    @UiIntentObserver(ContentSearchUiIntent.SelectScope::class)
    private fun onSelectScope(intent: ContentSearchUiIntent.SelectScope) {
        val state = interactiveState() ?: return
        val nextScope = state.scopeState.copy(
            scope = intent.scope,
            parentId = if (intent.scope == SearchScope.Body) state.scopeState.parentId else null,
            parentTitle = if (intent.scope == SearchScope.Body) state.scopeState.parentTitle else "",
        )
        if (nextScope == state.scopeState) return
        updateScope(nextScope)
    }

    /** 搜索按钮与键盘 Search 共用提交入口。 */
    @UiIntentObserver(ContentSearchUiIntent.Submit::class)
    private fun onSubmit() {
        search(directScan = false)
    }

    /** 明确选择慢速全文扫描时才绕过索引，保留停止和部分结果。 */
    @UiIntentObserver(ContentSearchUiIntent.SearchDirectly::class)
    private fun onSearchDirectly() {
        search(directScan = true)
    }

    /** 使用当前游标请求下一页，不允许重复分页任务。 */
    @UiIntentObserver(ContentSearchUiIntent.More::class)
    private fun onMore() {
        search(append = true)
    }

    /** 停止后保留真实结果，完整性明确标为未完成。 */
    @UiIntentObserver(ContentSearchUiIntent.Stop::class)
    private fun onStop() {
        val state = interactiveState() ?: return
        if (mSearchJob?.isActive != true) return
        stopSearch(state)
    }

    /** 返回对象范围的上一层，或结束整个搜索页。 */
    @UiIntentObserver(ContentSearchUiIntent.Back::class)
    private fun onBack() {
        val state = getOrNull<ContentSearchUiState.Normal>() ?: return
        if (state.scopeState.parentId != null && !state.indexState.busy) {
            onClearObjectScope()
            return
        }
        cancelQuery()
        ContentSearchUiState.Finished(state).setup()
    }

    /**
     * 索引维护统一交给全局设置，跳转前停止当前查询并保留已有结果。
     * - 维护期间也允许打开设置，以便取消构建或重试清理。
     */
    @UiIntentObserver(ContentSearchUiIntent.OpenSearchSettings::class)
    private fun onOpenSearchSettings() {
        val state = getOrNull<ContentSearchUiState.Normal>() ?: return
        if (mSearchJob?.isActive == true) {
            stopSearch(state)
        } else {
            cancelQuery()
        }
        AppViewEvent.StartActivity(
            activity = MainActivity::class.java,
            extras = Bundle().apply { putString(MainActivity.EXTRA_ROUTE, Route.Setting.name) },
        ).tryEmit()
    }

    /** 停止查询时保存当前请求已取得的部分结果，宽限期内也使用新范围。 */
    private fun stopSearch(state: ContentSearchUiState.Normal) {
        val preserved = latestQueryItems(state)
        cancelQuery()
        state.copy(
            resultState = ContentSearchResultState.Stopped(items = preserved),
            resultParentId = state.scopeState.parentId,
            isReplacingResults = false,
        ).setup()
    }

    /**
     * 在同一页面内按单个对象展示命中消息／章节。
     * @param intent 用于限定后续检索范围的命中对象。
     */
    @UiIntentObserver(ContentSearchUiIntent.SearchWithin::class)
    private fun onSearchWithin(intent: ContentSearchUiIntent.SearchWithin) {
        val state = stateForResultAction(intent.hit) ?: return
        val kind = SearchKind.entries.firstOrNull { it.code == intent.hit.kind } ?: return
        updateScope(
            scope = state.scopeState.copy(
                kind = kind,
                scope = SearchScope.Body,
                parentId = intent.hit.parentId,
                parentTitle = intent.hit.title,
            ),
        )
    }

    /** 回到当前分类中的全部对象。 */
    @UiIntentObserver(ContentSearchUiIntent.ClearObjectScope::class)
    private fun onClearObjectScope() {
        val state = interactiveState() ?: return
        updateScope(state.scopeState.copy(parentId = null, parentTitle = ""))
    }

    /**
     * 验证目标后分发导航事件；点击标题保留普通打开行为。
     * @param intent 用户点击的结果及其打开位置。
     */
    @UiIntentObserver(ContentSearchUiIntent.Open::class)
    private fun onOpen(intent: ContentSearchUiIntent.Open) {
        val hit = intent.hit
        val state = stateForResultAction(hit) ?: return
        val preserved = latestQueryItems(state)
        // 打开目标前先终止正在进行的搜索，避免跳转过程中状态继续抖动。
        cancelQuery()
        val generation = mGeneration
        if (state.resultState is ContentSearchResultState.Searching) {
            state.copy(
                resultState = ContentSearchResultState.Partial(
                    items = preserved,
                    next = null,
                ),
            ).setup()
        }
        mOpenJob = viewModelScope.launch {
            try {
                // 异步按当前业务库复核归属和命中有效性，避免跳入已删除或被改写的位置。
                val targetHit = if (intent.locate) hit else hit.copy(sourceId = 0)
                val available = mRepository.exists(targetHit, state.inputState.submitted)
                if (generation != mGeneration) return@launch
                if (!available) {
                    AppViewEvent.PopupToastMessageByResId(R.string.content_search_changed).tryEmit()
                    search()
                    return@launch
                }
                // 标题打开不带锚点，正文或章节命中携带稳定源 ID。
                val extras = Bundle().apply {
                    if (intent.locate && hit.sourceId > 0) {
                        putLong(SearchNavigation.EXTRA_SOURCE_ID, hit.sourceId)
                        putString(SearchNavigation.EXTRA_QUERY, state.inputState.submitted)
                    }
                }
                val target = resolveNavigationTarget(hit, extras)
                mRefreshOnResume = true
                AppViewEvent.StartActivity(target, extras).tryEmit()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == mGeneration) {
                    AppViewEvent.PopupToastMessageByResId(R.string.content_search_error).tryEmit()
                }
            }
        }
    }

    /** 按搜索命中类型组装导航参数并返回目标宿主页面类。 */
    private fun resolveNavigationTarget(hit: SearchHit, extras: Bundle): Class<*> {
        // 会话导航沿用字符串 ID，故事编辑器使用 Long ID。
        return when (hit.kind) {
            SearchKind.Chat.code -> {
                extras.putString(ChatActivity.EXTRA_SESSION_ID, hit.parentId.toString())
                ChatActivity::class.java
            }

            SearchKind.Group.code -> {
                extras.putString(GroupChatActivity.EXTRA_SESSION_ID, hit.parentId.toString())
                GroupChatActivity::class.java
            }

            else -> {
                extras.putLong(StoryEditorActivity.EXTRA_STORY_ID, hit.parentId)
                StoryEditorActivity::class.java
            }
        }
    }

    /** 更新筛选范围并在已提交查询匹配当前草稿时无缝重查，避免先闪回空闲态。 */
    private fun updateScope(scope: ContentSearchScopeState) {
        val state = interactiveState() ?: return
        val shouldResearch = state.inputState.draft.isNotBlank()
            && state.inputState.draft == state.inputState.submitted
        mSavedState["kind"] = scope.kind.code
        mSavedState["scope"] = scope.scope.name
        if (shouldResearch) {
            // 新范围与旧结果的只读标记一同发布，避免切换范围时旧卡片仍可触发导航。
            search(newScope = scope)
            return
        }
        cancelQuery()
        state.copy(
            scopeState = scope,
            resultState = ContentSearchResultState.Idle,
            resultParentId = null,
            isReplacingResults = false,
        ).setup()
    }

    /**
     * 启动独立搜索任务，所有回写都使用代次守卫和最新父状态。
     *
     * @param append 是否基于当前游标追加下一页结果。
     * @param newScope 切换筛选时使用的新范围，允许替换正在执行的查询。
     * @param directScan 是否绕过索引直接搜索原文。
     */
    private fun search(
        append: Boolean = false,
        newScope: ContentSearchScopeState? = null,
        directScan: Boolean = mDirectScan,
    ) {
        val state = interactiveState() ?: return
        // 非强制替换场景下，拦截进行中的重复提交与重复分页请求。
        if (newScope == null && mSearchJob?.isActive == true) return
        val scope = newScope ?: state.scopeState
        when (mRepository.validateQuery(state.inputState.draft)) {
            SearchQueryValidation.Empty -> return
            SearchQueryValidation.TooLong -> {
                cancelQuery()
                state.copy(
                    scopeState = scope,
                    resultState = ContentSearchResultState.Error(
                        items = emptyList(),
                        message = R.string.content_search_too_long,
                    ),
                    resultParentId = null,
                    isReplacingResults = false,
                ).setup()
                return
            }

            is SearchQueryValidation.Valid -> Unit
        }
        val cursor = if (append) state.resultState.nextCursor() ?: return else null
        cancelQuery()
        mDirectScan = directScan
        val generation = mGeneration
        val existing = if (append) state.resultState.items() else emptyList()
        mActiveExisting = existing
        val request = SearchRequest(
            query = state.inputState.draft,
            kind = scope.kind,
            scope = scope.scope,
            parentId = scope.parentId,
            directScan = directScan,
        )
        // 分页沿用当前结果，首页查询的旧卡片在宽限期内只保留画面。
        state.copy(
            inputState = state.inputState.copy(submitted = state.inputState.draft),
            scopeState = scope,
            isReplacingResults = !append,
        ).setup()
        launchSearchJob(
            generation = generation,
            request = request,
            cursor = cursor,
            append = append,
            existing = existing,
        )
    }

    /**
     * 在独立协程中执行查询，结合加载宽限期和节流器平滑发布结果。
     *
     * @param generation 本次请求代次，用于丢弃过期回调。
     * @param request 已组装的查询参数快照。
     * @param cursor 翻页游标；首页查询为 null。
     * @param append 是否属于分页追加模式。
     * @param existing 当前已确认保留的结果前缀。
     */
    private fun launchSearchJob(
        generation: Long,
        request: SearchRequest,
        cursor: SearchCursor?,
        append: Boolean,
        existing: List<SearchHit>,
    ) {
        // 长任务退出 Intent 消费器，取消、停止和重新输入才能立即得到处理。
        mSearchJob = viewModelScope.launch {
            val gate = SearchProgressGate(
                mScope = this,
                mRequestGeneration = generation,
                mAppend = append,
                mParentId = request.parentId,
                mExisting = existing,
            )
            gate.startGraceTimer()
            try {
                // IO 扫描阶段高频回调经节流门控平滑汇总，避免每批 32 行都触发列表抖动。
                val page = mRepository.search(request, cursor) { partial ->
                    val combined = existing + partial
                    withContext(Dispatchers.Main) {
                        gate.onPartial(combined)
                    }
                }
                gate.cancel()
                if (generation != mGeneration) return@launch
                val finalHits = existing + page.hits
                clearActiveTracking()
                publish(
                    generation = generation,
                    result = if (page.complete) {
                        ContentSearchResultState.Complete(finalHits, page.next)
                    } else {
                        ContentSearchResultState.Partial(finalHits, page.next)
                    },
                    resultParentId = request.parentId,
                    bumpEpoch = !append && !gate.enteredSearching,
                )
            } catch (cancelled: CancellationException) {
                gate.cancel()
                throw cancelled
            } catch (failure: Exception) {
                gate.cancel()
                if (generation != mGeneration) return@launch
                val message = if (failure is SearchChangedException) {
                    R.string.content_search_changed
                } else {
                    R.string.content_search_error
                }
                val current = getOrNull<ContentSearchUiState.Normal>() ?: return@launch
                val preserved = mPendingPartialHits
                    ?: if (gate.enteredSearching) current.resultState.items() else existing
                clearActiveTracking()
                publish(
                    generation = generation,
                    result = ContentSearchResultState.Error(preserved, message),
                    resultParentId = request.parentId,
                )
            }
        }
    }

    /**
     * 控制单次查询的加载态宽限延迟与部分结果节流推送，所有操作均在主线程串行执行。
     */
    private inner class SearchProgressGate(
        private val mScope: CoroutineScope,
        private val mRequestGeneration: Long,
        private val mAppend: Boolean,
        private val mParentId: Long?,
        private val mExisting: List<SearchHit>,
    ) {
        var enteredSearching: Boolean = false
            private set

        private var mIndicatorJob: Job? = null
        private var mFlushJob: Job? = null
        private var mLastPublishMark: TimeSource.Monotonic.ValueTimeMark? = null
        private var mLastPublishedItems: List<SearchHit>? = null

        /** 启动加载态宽限计时器；快查询在到期前结束可完全跳过 Loading 闪烁。 */
        fun startGraceTimer() {
            mIndicatorJob = mScope.launch {
                delay(SEARCH_INDICATOR_DELAY_MS)
                if (mRequestGeneration != mGeneration) return@launch
                enteredSearching = true
                publishSnapshot(
                    items = mPendingPartialHits ?: mExisting,
                    bumpEpoch = !mAppend,
                )
            }
        }

        /**
         * 接收最新累计命中，未过宽限期只缓存，进入搜索态后按固定间隔节流刷新。
         *
         * @param combined 当前查询已获得的累计命中，分页时包含原有结果前缀。
         */
        fun onPartial(combined: List<SearchHit>) {
            if (mRequestGeneration != mGeneration) return
            mPendingPartialHits = combined
            if (!enteredSearching || combined == mLastPublishedItems) return
            val elapsedMs = mLastPublishMark?.elapsedNow()?.inWholeMilliseconds ?: Long.MAX_VALUE
            if (elapsedMs >= PARTIAL_PUBLISH_INTERVAL_MS) {
                mFlushJob?.cancel()
                mFlushJob = null
                publishSnapshot(items = combined, bumpEpoch = false)
                return
            }
            if (mFlushJob != null) return
            // 在节流窗口尾部补发一次最新缓存，避免最后一批新命中长时间滞留在内存。
            val delayMs = PARTIAL_PUBLISH_INTERVAL_MS - elapsedMs
            mFlushJob = mScope.launch {
                delay(delayMs)
                mFlushJob = null
                if (mRequestGeneration != mGeneration || !enteredSearching) return@launch
                val latest = mPendingPartialHits ?: return@launch
                if (latest != mLastPublishedItems) {
                    publishSnapshot(items = latest, bumpEpoch = false)
                }
            }
        }

        /** 取消待执行的宽限计时器与节流补发协程。 */
        fun cancel() {
            mIndicatorJob?.cancel()
            mIndicatorJob = null
            mFlushJob?.cancel()
            mFlushJob = null
        }

        /**
         * 发布当前查询的搜索中快照，并记录节流时间与已发布结果。
         *
         * @param items 本次可展示的累计命中。
         * @param bumpEpoch 是否作为全新查询的首个快照触发列表回顶，分页和后续增量为 false。
         */
        private fun publishSnapshot(items: List<SearchHit>, bumpEpoch: Boolean) {
            mLastPublishMark = TimeSource.Monotonic.markNow()
            mLastPublishedItems = items
            publish(
                generation = mRequestGeneration,
                result = ContentSearchResultState.Searching(
                    items = items,
                    isAppending = mAppend,
                ),
                resultParentId = mParentId,
                bumpEpoch = bumpEpoch,
            )
        }
    }

    /** 读取当前查询真正归属的结果集，防止停止操作混入切换筛选前的旧条目。 */
    private fun latestQueryItems(state: ContentSearchUiState.Normal): List<SearchHit> =
        mPendingPartialHits
            ?: if (mSearchJob?.isActive == true) mActiveExisting else state.resultState.items()

    /** 索引维护期间拒绝查询，避免与构建及清理任务竞争数据库。 */
    private fun interactiveState(): ContentSearchUiState.Normal? =
        getOrNull<ContentSearchUiState.Normal>()
            ?.takeUnless { it.indexState.busy }

    /** 拦截宽限期的旧结果以及已从列表移除的延迟点击。 */
    private fun stateForResultAction(hit: SearchHit): ContentSearchUiState.Normal? {
        val state = interactiveState() ?: return null
        if (state.isReplacingResults || hit !in state.resultState.items()) return null
        return state
    }

    /**
     * 仅将仍属于当前请求的结果合并到最新页面状态，旧查询不得覆盖新筛选范围。
     *
     * @param generation 结果所属的请求代次。
     * @param result 要发布的检索结果状态。
     * @param resultParentId 结果所属的对象范围，null 表示全部对象。
     * @param bumpEpoch 是否开启新的结果批次，通知列表回到顶部。
     */
    private fun publish(
        generation: Long,
        result: ContentSearchResultState,
        resultParentId: Long?,
        bumpEpoch: Boolean = false,
    ) {
        if (generation != mGeneration) return
        val current = getOrNull<ContentSearchUiState.Normal>() ?: return
        val nextEpoch = if (bumpEpoch) current.resultEpoch + 1 else current.resultEpoch
        current.copy(
            resultState = result,
            resultParentId = resultParentId,
            resultEpoch = nextEpoch,
            isReplacingResults = false,
        ).setup()
    }

    /** 清除本次查询的部分结果与分页前缀，防止下一次查询停止时混入旧命中。 */
    private fun clearActiveTracking() {
        mPendingPartialHits = null
        mActiveExisting = emptyList()
    }

    /** 先使请求代次失效，再取消查询和目标验证任务，并释放当前结果追踪快照。 */
    private fun cancelQuery() {
        mGeneration++
        mSearchJob?.cancel()
        mSearchJob = null
        mOpenJob?.cancel()
        mOpenJob = null
        clearActiveTracking()
    }

    private companion object {
        const val MAX_SAVED_DRAFT_LENGTH = 4096
        const val SEARCH_INDICATOR_DELAY_MS = 120L
        const val PARTIAL_PUBLISH_INTERVAL_MS = 200L
    }
}
