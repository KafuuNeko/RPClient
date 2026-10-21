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
import me.kafuuneko.rpclient.feature.contentsearch.presentation.ContentSearchDialogState
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
import me.kafuuneko.rpclient.libs.search.SearchIndexState
import me.kafuuneko.rpclient.libs.search.SearchKind
import me.kafuuneko.rpclient.libs.search.SearchNavigation
import me.kafuuneko.rpclient.libs.search.SearchQueryValidation
import me.kafuuneko.rpclient.libs.search.SearchRequest
import me.kafuuneko.rpclient.libs.search.SearchScope
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
        // 仅恢复持久状态；进入搜索页不代表用户确认开启。
        mRepository.restore()
        viewModelScope.launch {
            mRepository.indexState.collect { index ->
                val current = getOrNull<ContentSearchUiState.Normal>() ?: return@collect
                val changed = current.indexState.enabled != index.enabled || current.indexState.busy != index.busy
                if (changed) { cancelQuery(); mDirectScan = false }
                current.copy(indexState = index,
                    dialogState = if (index.busy) ContentSearchDialogState.None else current.dialogState,
                    resultState = if (changed) ContentSearchResultState.Idle else current.resultState).setup()
            }
        }
    }

    /** 从目标页面返回后提示刷新，保留输入、结果和滚动位置。 */
    @UiIntentObserver(ContentSearchUiIntent.Resume::class)
    private fun onResume() {
        val state = interactiveState() ?: return
        if (!mRefreshOnResume) return
        mRefreshOnResume = false
        state.copy(resultState = ContentSearchResultState.Error(state.resultState.items(), R.string.content_search_changed)).setup()
    }

    /** 编辑立即使旧请求失效，但不在按键时扫描正文。 */
    @UiIntentObserver(ContentSearchUiIntent.ChangeQuery::class)
    private fun onChangeQuery(intent: ContentSearchUiIntent.ChangeQuery) {
        val state = interactiveState() ?: return
        cancelQuery()
        // 恢复草稿的体积预算独立于查询有效性，尚未提交的无效输入也可以恢复。
        mSavedState["query"] = intent.value.takeIf { it.length <= MAX_SAVED_DRAFT_LENGTH }.orEmpty()
        state.copy(inputState = state.inputState.copy(draft = intent.value), resultState = ContentSearchResultState.Idle).setup()
    }

    /** 已提交且未编辑时切分类自动重查，其余情况只更新草稿范围。 */
    @UiIntentObserver(ContentSearchUiIntent.SelectKind::class)
    private fun onSelectKind(intent: ContentSearchUiIntent.SelectKind) {
        val state = interactiveState() ?: return
        updateScope(state.scopeState.copy(kind = intent.kind, parentId = null, parentTitle = ""))
    }

    /** 切换标题／正文范围保留查询，避免无意触发未提交文本。 */
    @UiIntentObserver(ContentSearchUiIntent.SelectScope::class)
    private fun onSelectScope(intent: ContentSearchUiIntent.SelectScope) {
        val state = interactiveState() ?: return
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
        val state = interactiveState() ?: return
        cancelQuery()
        state.copy(resultState = ContentSearchResultState.Content(state.resultState.items(), null, false, stopped = true)).setup()
    }

    /** 返回对象范围的上一层，或结束整个搜索页。 */
    @UiIntentObserver(ContentSearchUiIntent.Back::class)
    private fun onBack() {
        val state = getOrNull<ContentSearchUiState.Normal>() ?: return
        if (state.dialogState != ContentSearchDialogState.None) { onDismissIndexDialog(); return }
        if (state.indexState is SearchIndexState.Building) { onCancelIndexBuild(); return }
        if (state.indexState.busy && state.indexState != SearchIndexState.CleanupFailed) return
        if (state.scopeState.parentId != null && !state.indexState.busy) { onClearObjectScope(); return }
        cancelQuery()
        ContentSearchUiState.Finished(state).setup()
    }

    /** 开关关闭立即执行清理，开启则先显示用户确认。 */
    @UiIntentObserver(ContentSearchUiIntent.ChangeIndexMode::class)
    private fun onChangeIndexMode() {
        val state = interactiveState() ?: return
        cancelQuery()
        if (state.indexState.enabled) {
            state.copy(indexState = SearchIndexState.Cleaning, resultState = ContentSearchResultState.Idle).setup()
            viewModelScope.launch { mRepository.disable() }
        } else state.copy(dialogState = ContentSearchDialogState.EnableIndex,
            resultState = stoppedResult(state.resultState)).setup()
    }

    /** 确认后才进入阻断交互的构建状态，不提前展示已经启用。 */
    @UiIntentObserver(ContentSearchUiIntent.ConfirmEnableIndex::class)
    private fun onConfirmEnableIndex() {
        val state = getOrNull<ContentSearchUiState.Normal>() ?: return
        if (state.dialogState != ContentSearchDialogState.EnableIndex || state.indexState.busy) return
        cancelQuery()
        state.copy(dialogState = ContentSearchDialogState.None, indexState = SearchIndexState.Building(),
            resultState = ContentSearchResultState.Idle).setup()
        viewModelScope.launch {
            if (state.indexState.enabled) mRepository.retry() else mRepository.enable()
        }
    }

    /** 取消确认不会修改持久开关。 */
    @UiIntentObserver(ContentSearchUiIntent.DismissIndexDialog::class)
    private fun onDismissIndexDialog() {
        val state = getOrNull<ContentSearchUiState.Normal>() ?: return
        state.copy(dialogState = ContentSearchDialogState.None).setup()
    }

    /** 返回与取消按钮共用关闭路径，不能仅隐藏加载对话框。 */
    @UiIntentObserver(ContentSearchUiIntent.CancelIndexBuild::class)
    private fun onCancelIndexBuild() {
        val state = getOrNull<ContentSearchUiState.Normal>() ?: return
        if (state.indexState !is SearchIndexState.Building) return
        state.copy(indexState = SearchIndexState.Cleaning).setup()
        viewModelScope.launch { mRepository.disable() }
    }

    /** 清理失败只重试清理，不能隐式重新启用。 */
    @UiIntentObserver(ContentSearchUiIntent.RetryCleanup::class)
    private fun onRetryCleanup() {
        val state = getOrNull<ContentSearchUiState.Normal>() ?: return
        if (state.indexState != SearchIndexState.CleanupFailed) return
        state.copy(indexState = SearchIndexState.Cleaning).setup()
        viewModelScope.launch { mRepository.disable() }
    }

    /** 损坏或格式变化后重建同样必须确认并展示可取消进度。 */
    @UiIntentObserver(ContentSearchUiIntent.RetryIndex::class)
    private fun onRetryIndex() {
        val state = interactiveState() ?: return
        cancelQuery()
        state.copy(dialogState = ContentSearchDialogState.EnableIndex,
            resultState = stoppedResult(state.resultState)).setup()
    }

    /** 停止的查询保留已取得结果，取消确认后不能留下无人执行的 Loading。 */
    private fun stoppedResult(result: ContentSearchResultState): ContentSearchResultState =
        if (result is ContentSearchResultState.Searching) ContentSearchResultState.Content(result.items, null, false, stopped = true) else result

    /** 在同一页面内按单个对象展示命中消息／章节。 */
    @UiIntentObserver(ContentSearchUiIntent.SearchWithin::class)
    private fun onSearchWithin(intent: ContentSearchUiIntent.SearchWithin) {
        val state = interactiveState() ?: return
        val kind = SearchKind.entries.first { it.code == intent.hit.kind }
        updateScope(state.scopeState.copy(kind = kind, scope = SearchScope.Body, parentId = intent.hit.parentId, parentTitle = intent.hit.title))
    }

    /** 回到当前分类中的全部对象。 */
    @UiIntentObserver(ContentSearchUiIntent.ClearObjectScope::class)
    private fun onClearObjectScope() {
        val state = interactiveState() ?: return
        updateScope(state.scopeState.copy(parentId = null, parentTitle = ""))
    }

    /** 验证目标后分发导航事件；点击标题保留普通打开行为。 */
    @UiIntentObserver(ContentSearchUiIntent.Open::class)
    private fun onOpen(intent: ContentSearchUiIntent.Open) {
        val state = interactiveState() ?: return
        val hit = intent.hit
        cancelQuery()
        val generation = mGeneration
        if (state.resultState is ContentSearchResultState.Searching) {
            state.copy(resultState = ContentSearchResultState.Content(state.resultState.items(), null, false)).setup()
        }
        mSearchJob = viewModelScope.launch {
            try {
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
        val state = interactiveState() ?: return
        cancelQuery()
        mSavedState["kind"] = scope.kind.code
        mSavedState["scope"] = scope.scope.name
        state.copy(scopeState = scope, resultState = ContentSearchResultState.Idle).setup()
        if (state.inputState.draft.isNotBlank() && state.inputState.draft == state.inputState.submitted) search()
    }

    /** 启动独立任务，所有回写都使用代次守卫和最新父状态。 */
    private fun search(append: Boolean = false) {
        val state = interactiveState() ?: return
        if (state.resultState is ContentSearchResultState.Searching) return
        when (mRepository.validateQuery(state.inputState.draft)) {
            SearchQueryValidation.Empty -> return
            SearchQueryValidation.TooLong -> {
                state.copy(resultState = ContentSearchResultState.Error(emptyList(), R.string.content_search_too_long)).setup()
                return
            }
            is SearchQueryValidation.Valid -> Unit
        }
        val cursor = if (append) (state.resultState as? ContentSearchResultState.Content)?.next ?: return else null
        cancelQuery()
        val generation = mGeneration
        val existing = if (append) state.resultState.items() else emptyList()
        val request = SearchRequest(state.inputState.draft, state.scopeState.kind, state.scopeState.scope, state.scopeState.parentId, mDirectScan)
        state.copy(inputState = state.inputState.copy(submitted = state.inputState.draft),
            resultState = ContentSearchResultState.Searching(existing)).setup()
        // 长任务退出 Intent 消费器，取消和输入才能立即得到处理。
        mSearchJob = viewModelScope.launch {
            try {
                val page = mRepository.search(request, cursor) { partial ->
                    withContext(Dispatchers.Main) {
                        publish(generation, ContentSearchResultState.Searching(existing + partial))
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

    /** 忙碌与确认阶段拒绝排队中的旧 Intent，界面遮罩不是唯一守卫。 */
    private fun interactiveState(): ContentSearchUiState.Normal? = getOrNull<ContentSearchUiState.Normal>()
        ?.takeUnless { it.indexState.busy || it.dialogState != ContentSearchDialogState.None }

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

    private companion object {
        const val MAX_SAVED_DRAFT_LENGTH = 4096
    }
}
