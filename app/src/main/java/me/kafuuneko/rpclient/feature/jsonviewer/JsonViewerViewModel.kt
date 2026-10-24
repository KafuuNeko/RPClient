package me.kafuuneko.rpclient.feature.jsonviewer

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.feature.jsonviewer.model.JsonPathStep
import me.kafuuneko.rpclient.feature.jsonviewer.model.JsonSourceBuilder
import me.kafuuneko.rpclient.feature.jsonviewer.model.JsonSourceState
import me.kafuuneko.rpclient.feature.jsonviewer.model.childCount
import me.kafuuneko.rpclient.feature.jsonviewer.model.nodeType
import me.kafuuneko.rpclient.feature.jsonviewer.model.parseJson
import me.kafuuneko.rpclient.feature.jsonviewer.model.toEntries
import me.kafuuneko.rpclient.feature.jsonviewer.presentation.JsonViewerErrorReason
import me.kafuuneko.rpclient.feature.jsonviewer.presentation.JsonViewerUiIntent
import me.kafuuneko.rpclient.feature.jsonviewer.presentation.JsonViewerUiState
import me.kafuuneko.rpclient.feature.jsonviewer.presentation.JsonViewerTab
import me.kafuuneko.rpclient.libs.core.CoreViewModel
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.json.JsonDocumentCodec
import me.kafuuneko.rpclient.libs.json.JsonDocumentLimits
import me.kafuuneko.rpclient.libs.json.JsonResourceLimitException
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON 树查看器状态持有者。
 *
 * 核心职责：
 * - 解析并维护内存中的 JSON 对象/数组语法树；
 * - 跟踪由根至当前层级的导航路径栈（Path Stack）；
 * - 仅将当前视口可见的层级节点映射为列表 UI 数据，避免大 JSON 树在重组时产生大量深拷贝开销；
 * - 支持父子层级间逐级下钻浏览与逐级返回。
 */
class JsonViewerViewModel : CoreViewModel<JsonViewerUiIntent, JsonViewerUiState>(
    JsonViewerUiState.None
) {
    /** 当前载荷标题、根节点与从根到当前节点的导航路径。 */
    private var mTitle: String = ""
    private var mRoot: Any? = null
    private var mPath: List<JsonPathStep> = emptyList()
    private var mRaw: String = ""
    private var mVisibleCount = PAGE_SIZE
    private var mSourceJob: Job? = null
    private var mTreeJob: Job? = null

    /** 从载荷存储区拉取 JSON 原文并进行语法解析与初始展示。 */
    @UiIntentObserver(JsonViewerUiIntent.Init::class)
    private fun onInit(intent: JsonViewerUiIntent.Init) {
        if (!isStateOf<JsonViewerUiState.None>()) return

        // 从内存暂存区检索 Payload
        val payload = JsonViewerPayloadStore.take(intent.payloadKey)
        if (payload == null) {
            JsonViewerUiState.Error(
                title = "",
                reason = JsonViewerErrorReason.PayloadUnavailable,
                rawPreview = "",
            ).setup()
            return
        }

        mTitle = payload.title
        mRaw = payload.json
        JsonViewerUiState.Loading(title = mTitle).setup()
        mTreeJob = viewModelScope.launch {
            val state = withContext(Dispatchers.Default) { buildInitialTree() }
            if (isStateOf<JsonViewerUiState.Loading>()) state.setup()
        }
    }

    /** 构建 DOM 前先检查输入、深度和节点预算；失败时仍保留源码 Tab。 */
    private suspend fun buildInitialTree(): JsonViewerUiState.Normal {
        return try {
            val context = currentCoroutineContext()
            JsonDocumentCodec.validate(
                mRaw,
                JsonDocumentLimits(tokens = 200_000),
                context::ensureActive
            )
            // 只有通过预扫描的文档才交给 Android JSONTokener 构建树。
            mRoot = parseJson(mRaw).getOrThrow()
            mPath = emptyList()
            buildNormalState()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            JsonViewerUiState.Normal(
                title = mTitle,
                path = listOf("$"),
                currentType = null.nodeType(),
                childCount = 0,
                entries = emptyList(),
                canNavigateUp = false,
                treeError = if (error is JsonResourceLimitException) JsonViewerErrorReason.TooLarge else JsonViewerErrorReason.InvalidJson,
            )
        }
    }

    /** 处理返回操作，若处于子层级则返回上一层，处于根层级则退出查看器。 */
    @UiIntentObserver(JsonViewerUiIntent.Back::class)
    private suspend fun onBack() {
        val state = getOrNull<JsonViewerUiState.Normal>()
        if (state?.selectedTab == JsonViewerTab.Source) {
            onSelectTab(JsonViewerUiIntent.SelectTab(JsonViewerTab.Tree))
            return
        }
        if (getOrNull<JsonViewerUiState.Normal>()?.canNavigateUp == true) {
            mPath = mPath.dropLast(1)
            mVisibleCount = PAGE_SIZE
            publishTreeState()
            return
        }
        mSourceJob?.cancel()
        mTreeJob?.cancel()
        JsonViewerUiState.finished(uiStateFlow.value).setup()
    }

    /** 选中包含子节点的条目，下钻进入其下属层级。 */
    @UiIntentObserver(JsonViewerUiIntent.EntrySelected::class)
    private suspend fun onEntrySelected(intent: JsonViewerUiIntent.EntrySelected) {
        val uiState = getOrNull<JsonViewerUiState.Normal>() ?: return
        val entry = uiState.entries.firstOrNull { it.id == intent.entryId } ?: return
        if (!entry.hasChildren) return

        mPath = mPath + JsonPathStep(
            label = entry.name,
            objectKey = entry.sourceKey,
            arrayIndex = entry.sourceIndex,
        )
        mVisibleCount = PAGE_SIZE
        publishTreeState()
    }

    /** 首次选中源码后才启动可取消任务，切回树视图保留成功缓存。 */
    @UiIntentObserver(JsonViewerUiIntent.SelectTab::class)
    private fun onSelectTab(intent: JsonViewerUiIntent.SelectTab) {
        val state = getOrNull<JsonViewerUiState.Normal>() ?: return
        if (intent.tab == JsonViewerTab.Tree) {
            mSourceJob?.cancel()
            val source = if (state.sourceState == JsonSourceState.Loading) {
                JsonSourceState.NotLoaded
            } else state.sourceState
            state.copy(selectedTab = intent.tab, sourceState = source).setup()
            return
        }
        state.copy(selectedTab = intent.tab).setup()
        if (state.sourceState != JsonSourceState.NotLoaded) return
        state.copy(selectedTab = intent.tab, sourceState = JsonSourceState.Loading).setup()
        // 独立协程不阻塞 MVI Intent 收集；后台循环持续检查页面任务取消。
        mSourceJob = viewModelScope.launch {
            val source = withContext(Dispatchers.Default) {
                val context = currentCoroutineContext()
                JsonSourceBuilder.build(mRaw, context::ensureActive)
            }
            val latest = getOrNull<JsonViewerUiState.Normal>() ?: return@launch
            if (latest.selectedTab == JsonViewerTab.Source) {
                latest.copy(sourceState = source).setup()
            }
        }
    }

    /** 仅追加当前层下一批条目，不展开或复制其他层级。 */
    @UiIntentObserver(JsonViewerUiIntent.LoadMore::class)
    private suspend fun onLoadMore() {
        val state = getOrNull<JsonViewerUiState.Normal>() ?: return
        if (!state.canLoadMore || state.selectedTab != JsonViewerTab.Tree) return
        mVisibleCount += PAGE_SIZE
        publishTreeState()
    }

    /** 回到主线程后合并最新源码子状态，避免映射期间的生成结果被旧快照覆盖。 */
    private suspend fun publishTreeState() {
        val tree = withContext(Dispatchers.Default) { buildNormalState() }
        val latest = getOrNull<JsonViewerUiState.Normal>() ?: return
        tree.copy(selectedTab = latest.selectedTab, sourceState = latest.sourceState).setup()
    }

    /** 根据当前导航路径构建当前层级的 Normal 状态。 */
    private fun buildNormalState(): JsonViewerUiState.Normal {
        val current = currentNode()
        val previous = getOrNull<JsonViewerUiState.Normal>()
        return JsonViewerUiState.Normal(
            title = mTitle,
            path = listOf("$") + mPath.map { it.label },
            currentType = current.nodeType(),
            childCount = current.childCount(),
            entries = current.toEntries(mVisibleCount),
            canNavigateUp = mPath.isNotEmpty(),
            selectedTab = previous?.selectedTab ?: JsonViewerTab.Tree,
            sourceState = previous?.sourceState ?: JsonSourceState.NotLoaded,
            canLoadMore = current.childCount() > mVisibleCount,
        )
    }

    /** 沿路径栈下钻定位当前聚焦的 JSON 节点。 */
    private fun currentNode(): Any? {
        var current = mRoot
        for (step in mPath) {
            current = when {
                step.objectKey != null && current is JSONObject -> current.opt(step.objectKey)
                step.arrayIndex != null && current is JSONArray -> current.opt(step.arrayIndex)
                else -> null
            }
        }
        return current
    }

    private companion object {
        const val PAGE_SIZE = 100
    }

}
