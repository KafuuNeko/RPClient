package me.kafuuneko.rpclient.feature.contentsearch

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import me.kafuuneko.rpclient.feature.groupchat.GroupChatActivity
import me.kafuuneko.rpclient.feature.storyeditor.StoryEditorActivity
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.repository.ContentSearchRepository
import me.kafuuneko.rpclient.libs.search.SearchChangedException
import me.kafuuneko.rpclient.libs.search.SearchKind
import me.kafuuneko.rpclient.libs.search.SearchNavigation
import me.kafuuneko.rpclient.libs.search.SearchRequest
import me.kafuuneko.rpclient.libs.search.SearchScope
import me.kafuuneko.rpclient.libs.search.SearchTextCodec
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 独立搜索页的 MVI 状态持有者。
 * - 输入草稿与已提交查询分离，长查询不占用串行 Intent 消费器。
 * - 每次提交、编辑及取消递增请求代次，阻止过期查询覆盖新状态。
 * - 保存轻量草稿与范围，正文与结果不写入 SavedStateHandle。
 */
class ContentSearchViewModel(private val mSavedState: SavedStateHandle) :
    CoreViewModelWithEvent<ContentSearchUiIntent, ContentSearchUiState>(ContentSearchUiState.None), KoinComponent {
    private val mRepository by inject<ContentSearchRepository>()
    private var mSearchJob: Job? = null
    private var mGeneration = 0L
    private var mDirectScan = false
    private var mRefreshOnResume = false

    /** 初始化页面并观察工作器；恢复后等待显式提交，不自动发起昂贵查询。 */
    @UiIntentObserver(ContentSearchUiIntent.Init::class)
    private fun onInit(intent: ContentSearchUiIntent.Init) {
        if (!isStateOf<ContentSearchUiState.None>()) return
        val kind = SearchKind.entries.firstOrNull { it.code == mSavedState.get<Int>("kind") } ?: intent.kind
        val scope = SearchScope.entries.firstOrNull { it.name == mSavedState.get<String>("scope") } ?: SearchScope.All
        ContentSearchUiState.Normal(
            inputState = ContentSearchInputState(draft = mSavedState["query"] ?: ""),
            scopeState = ContentSearchScopeState(kind, scope)
        ).setup()
        // 工作器属于应用，离开搜索页只取消查询，不终止可恢复补建。
        mRepository.start()
        viewModelScope.launch {
            mRepository.indexState.collect { index ->
                val current = getOrNull<ContentSearchUiState.Normal>() ?: return@collect
                current.copy(indexState = index).setup()
            }
        }
    }

    /** 从目标页面返回后提示刷新，保留输入、结果和滚动位置。 */
    @UiIntentObserver(ContentSearchUiIntent.Resume::class)
    private fun onResume() {
        val state = getOrNull<ContentSearchUiState.Normal>() ?: return
        if (!mRefreshOnResume) return
        mRefreshOnResume = false
        state.copy(resultState = ContentSearchResultState.Error(state.resultState.items(), R.string.content_search_changed)).setup()
    }

    /** 编辑立即使旧请求失效，但不在按键时扫描正文。 */
    @UiIntentObserver(ContentSearchUiIntent.ChangeQuery::class)
    private fun onChangeQuery(intent: ContentSearchUiIntent.ChangeQuery) {
        val state = getOrNull<ContentSearchUiState.Normal>() ?: return
        cancelQuery()
        mSavedState["query"] = intent.value.takeIf { it.length <= 4096 }.orEmpty()
        state.copy(inputState = state.inputState.copy(draft = intent.value), resultState = ContentSearchResultState.Idle).setup()
    }

    /** 已提交且未编辑时切分类自动重查，其余情况只更新草稿范围。 */
    @UiIntentObserver(ContentSearchUiIntent.SelectKind::class)
    private fun onSelectKind(intent: ContentSearchUiIntent.SelectKind) {
        val state = getOrNull<ContentSearchUiState.Normal>() ?: return
        updateScope(state.scopeState.copy(kind = intent.kind, parentId = null, parentTitle = ""))
    }

    /** 切换标题／正文范围保留查询，避免无意触发未提交文本。 */
    @UiIntentObserver(ContentSearchUiIntent.SelectScope::class)
    private fun onSelectScope(intent: ContentSearchUiIntent.SelectScope) {
        val state = getOrNull<ContentSearchUiState.Normal>() ?: return
        updateScope(state.scopeState.copy(scope = intent.scope, parentId = if (intent.scope == SearchScope.Body) state.scopeState.parentId else null))
    }

    /** 搜索按钮与键盘 Search 共用提交入口。 */
    @UiIntentObserver(ContentSearchUiIntent.Submit::class)
    private fun onSubmit() { mDirectScan = false; search() }

    /** 明确选择慢速全文扫描时才绕过索引，保留停止和部分结果。 */
    @UiIntentObserver(ContentSearchUiIntent.SearchDirectly::class)
    private fun onSearchDirectly() { mDirectScan = true; search() }

    /** 使用当前游标请求下一页，不允许重复分页任务。 */
    @UiIntentObserver(ContentSearchUiIntent.More::class)
    private fun onMore() { search(append = true) }

    /** 停止后保留真实结果，完整性明确标为未完成。 */
    @UiIntentObserver(ContentSearchUiIntent.Stop::class)
    private fun onStop() {
        val state = getOrNull<ContentSearchUiState.Normal>() ?: return
        cancelQuery()
        state.copy(resultState = ContentSearchResultState.Content(state.resultState.items(), null, false, stopped = true)).setup()
    }

    /** 返回对象范围的上一层，或结束整个搜索页。 */
    @UiIntentObserver(ContentSearchUiIntent.Back::class)
    private fun onBack() {
        val state = getOrNull<ContentSearchUiState.Normal>() ?: return
        if (state.scopeState.parentId != null) { onClearObjectScope(); return }
        cancelQuery()
        ContentSearchUiState.Finished(state).setup()
    }

    /** 用户显式恢复损坏或失败的索引，不影响当前元数据结果。 */
    @UiIntentObserver(ContentSearchUiIntent.RetryIndex::class)
    private fun onRetryIndex() {
        if (!isStateOf<ContentSearchUiState.Normal>()) return
        viewModelScope.launch {
            try { mRepository.retry() } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { AppViewEvent.PopupToastMessageByResId(R.string.content_search_error).tryEmit() }
        }
    }

    /** 在同一页面内按单个对象展示命中消息／章节。 */
    @UiIntentObserver(ContentSearchUiIntent.SearchWithin::class)
    private fun onSearchWithin(intent: ContentSearchUiIntent.SearchWithin) {
        val state = getOrNull<ContentSearchUiState.Normal>() ?: return
        val kind = SearchKind.entries.first { it.code == intent.hit.kind }
        updateScope(state.scopeState.copy(kind = kind, scope = SearchScope.Body, parentId = intent.hit.parentId, parentTitle = intent.hit.title))
    }

    /** 回到当前分类中的全部对象。 */
    @UiIntentObserver(ContentSearchUiIntent.ClearObjectScope::class)
    private fun onClearObjectScope() {
        val state = getOrNull<ContentSearchUiState.Normal>() ?: return
        updateScope(state.scopeState.copy(parentId = null, parentTitle = ""))
    }

    /** 验证目标后分发导航事件；点击标题保留普通打开行为。 */
    @UiIntentObserver(ContentSearchUiIntent.Open::class)
    private fun onOpen(intent: ContentSearchUiIntent.Open) {
        val state = getOrNull<ContentSearchUiState.Normal>() ?: return
        val hit = intent.hit
        cancelQuery()
        val generation = mGeneration
        if (state.resultState is ContentSearchResultState.Searching) {
            state.copy(resultState = ContentSearchResultState.Content(state.resultState.items(), null, false)).setup()
        }
        mSearchJob = viewModelScope.launch {
            try {
                val targetHit = if (intent.locate) hit else hit.copy(sourceId = 0)
                val available = mRepository.queries.exists(targetHit, state.inputState.submitted)
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
                val target = when (hit.kind) {
                    1 -> { extras.putString(ChatActivity.EXTRA_SESSION_ID, hit.parentId.toString()); ChatActivity::class.java }
                    2 -> { extras.putString(GroupChatActivity.EXTRA_SESSION_ID, hit.parentId.toString()); GroupChatActivity::class.java }
                    else -> { extras.putLong(StoryEditorActivity.EXTRA_STORY_ID, hit.parentId); StoryEditorActivity::class.java }
                }
                mRefreshOnResume = true
                AppViewEvent.StartActivity(target, extras).tryEmit()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == mGeneration) AppViewEvent.PopupToastMessageByResId(R.string.content_search_error).tryEmit()
            }
        }
    }

    private fun updateScope(scope: ContentSearchScopeState) {
        val state = getOrNull<ContentSearchUiState.Normal>() ?: return
        cancelQuery()
        mSavedState["kind"] = scope.kind.code
        mSavedState["scope"] = scope.scope.name
        state.copy(scopeState = scope, resultState = ContentSearchResultState.Idle).setup()
        if (state.inputState.draft.isNotBlank() && state.inputState.draft == state.inputState.submitted) search()
    }

    /** 启动独立任务，所有回写都使用代次守卫和最新父状态。 */
    private fun search(append: Boolean = false) {
        val state = getOrNull<ContentSearchUiState.Normal>() ?: return
        if (state.resultState is ContentSearchResultState.Searching) return
        if (state.inputState.draft.length > 4096) {
            state.copy(resultState = ContentSearchResultState.Error(emptyList(), R.string.content_search_too_long)).setup()
            return
        }
        val query = SearchTextCodec.normalize(state.inputState.draft)
        if (query.isBlank()) return
        if (query.codePointCount(0, query.length) > SearchTextCodec.MAX_QUERY_POINTS) {
            state.copy(resultState = ContentSearchResultState.Error(emptyList(), R.string.content_search_too_long)).setup()
            return
        }
        val cursor = if (append) (state.resultState as? ContentSearchResultState.Content)?.next ?: return else null
        cancelQuery()
        val generation = mGeneration
        val existing = if (append) state.resultState.items() else emptyList()
        val request = SearchRequest(query, state.scopeState.kind, state.scopeState.scope, state.scopeState.parentId, mDirectScan)
        state.copy(inputState = state.inputState.copy(submitted = state.inputState.draft),
            resultState = ContentSearchResultState.Searching(existing)).setup()
        // 长任务退出 Intent 消费器，取消和输入才能立即得到处理。
        mSearchJob = viewModelScope.launch {
            try {
                val page = mRepository.queries.search(request, cursor) { metadata ->
                    withContext(Dispatchers.Main) {
                        publish(generation, ContentSearchResultState.Searching(existing + metadata))
                    }
                }
                publish(generation, ContentSearchResultState.Content(existing + page.hits, page.next, page.complete))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                val message = if (failure is SearchChangedException) R.string.content_search_changed else R.string.content_search_error
                val current = getOrNull<ContentSearchUiState.Normal>() ?: return@launch
                publish(generation, ContentSearchResultState.Error(current.resultState.items(), message))
            }
        }
    }

    private fun publish(generation: Long, result: ContentSearchResultState) {
        if (generation != mGeneration) return
        val current = getOrNull<ContentSearchUiState.Normal>() ?: return
        current.copy(resultState = result).setup()
    }

    private fun cancelQuery() {
        mGeneration++
        mSearchJob?.cancel()
        mSearchJob = null
    }
}
