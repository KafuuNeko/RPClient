package me.kafuuneko.rpclient.feature.jsonviewer.presentation

import me.kafuuneko.rpclient.feature.jsonviewer.model.JsonViewerEntry
import me.kafuuneko.rpclient.feature.jsonviewer.model.JsonViewerNodeType
import me.kafuuneko.rpclient.feature.jsonviewer.model.JsonSourceState

/**
 * JSON 查看器页面状态。
 *
 * Normal 仅保存当前层级的扁平条目，完整 JSON 树由 ViewModel 持有，避免状态对象重复复制。
 */
sealed class JsonViewerUiState {
    data object None : JsonViewerUiState()

    data class Loading(val title: String) : JsonViewerUiState()

    data class Normal(
        /** 供界面展示或持久化的标题。 */
        val title: String,
        /** 当前 JSON 节点在文档中的访问路径。 */
        val path: List<String>,
        /** 当前 JSON 节点对应的数据类型。 */
        val currentType: JsonViewerNodeType,
        /** 当前 JSON 节点或结构项包含的直接子项数量。 */
        val childCount: Int,
        /** 当前分组、请求或结果包含的条目列表。 */
        val entries: List<JsonViewerEntry>,
        /** 当前 JSON 节点是否存在可返回的父节点。 */
        val canNavigateUp: Boolean,
        /** 顶部视图切换不改变树导航和冻结载荷。 */
        val selectedTab: JsonViewerTab = JsonViewerTab.Tree,
        /** 首次选择源码后才创建高亮内容。 */
        val sourceState: JsonSourceState = JsonSourceState.NotLoaded,
        /** 解析失败仍保留双 Tab，允许查看受预算约束的原文。 */
        val treeError: JsonViewerErrorReason? = null,
        /** 当前层可继续按批映射，避免一次生成宽数组的全部条目。 */
        val canLoadMore: Boolean = false,
    ) : JsonViewerUiState()

    data class Error(
        /** 供界面展示或持久化的标题。 */
        val title: String,
        /** 当前状态或取舍产生的原因。 */
        val reason: JsonViewerErrorReason,
        /** 经过长度限制且可安全展示的原始内容预览。 */
        val rawPreview: String
    ) : JsonViewerUiState()

    data class Finished(val previous: JsonViewerUiState) : JsonViewerUiState()

    companion object {
        fun finished(previous: JsonViewerUiState): JsonViewerUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

enum class JsonViewerErrorReason {
    PayloadUnavailable,

    InvalidJson,

    TooLarge,
}

/** 文档的两种互补展示形式。 */
enum class JsonViewerTab { Tree, Source }
