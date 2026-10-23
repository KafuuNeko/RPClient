package me.kafuuneko.rpclient.feature.jsonviewer

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.feature.jsonviewer.model.JsonPathStep
import me.kafuuneko.rpclient.feature.jsonviewer.model.childCount
import me.kafuuneko.rpclient.feature.jsonviewer.model.nodeType
import me.kafuuneko.rpclient.feature.jsonviewer.model.parseJson
import me.kafuuneko.rpclient.feature.jsonviewer.model.toEntries
import me.kafuuneko.rpclient.feature.jsonviewer.presentation.JsonViewerErrorReason
import me.kafuuneko.rpclient.feature.jsonviewer.presentation.JsonViewerUiIntent
import me.kafuuneko.rpclient.feature.jsonviewer.presentation.JsonViewerUiState
import me.kafuuneko.rpclient.libs.core.CoreViewModel
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.utils.toPreview
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
        JsonViewerUiState.Loading(title = mTitle).setup()
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.Default) {
                parseJson(payload.json)
            }
            if (!isStateOf<JsonViewerUiState.Loading>()) return@launch
            if (parsed.isFailure) {
                JsonViewerUiState.Error(
                    title = mTitle,
                    reason = JsonViewerErrorReason.InvalidJson,
                    rawPreview = payload.json.toPreview(RAW_PREVIEW_LENGTH),
                ).setup()
                return@launch
            }

            mRoot = parsed.getOrNull()
            mPath = emptyList()
            val normalState = withContext(Dispatchers.Default) { buildNormalState() }
            if (isStateOf<JsonViewerUiState.Loading>()) normalState.setup()
        }
    }

    /** 处理返回操作，若处于子层级则返回上一层，处于根层级则退出查看器。 */
    @UiIntentObserver(JsonViewerUiIntent.Back::class)
    private suspend fun onBack() {
        if (getOrNull<JsonViewerUiState.Normal>()?.canNavigateUp == true) {
            mPath = mPath.dropLast(1)
            withContext(Dispatchers.Default) { buildNormalState() }.setup()
            return
        }
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
        withContext(Dispatchers.Default) { buildNormalState() }.setup()
    }

    /** 根据当前导航路径构建当前层级的 Normal 状态。 */
    private fun buildNormalState(): JsonViewerUiState.Normal {
        val current = currentNode()
        return JsonViewerUiState.Normal(
            title = mTitle,
            path = listOf("Root") + mPath.map { it.label },
            currentType = current.nodeType(),
            childCount = current.childCount(),
            entries = current.toEntries(),
            canNavigateUp = mPath.isNotEmpty(),
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
        const val RAW_PREVIEW_LENGTH = 2_000
    }

}
