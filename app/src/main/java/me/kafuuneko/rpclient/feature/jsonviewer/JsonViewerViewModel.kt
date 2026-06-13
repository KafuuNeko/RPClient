package me.kafuuneko.rpclient.feature.jsonviewer

import me.kafuuneko.rpclient.feature.jsonviewer.model.JsonViewerEntry
import me.kafuuneko.rpclient.feature.jsonviewer.model.JsonViewerNodeType
import me.kafuuneko.rpclient.feature.jsonviewer.presentation.JsonViewerUiIntent
import me.kafuuneko.rpclient.feature.jsonviewer.presentation.JsonViewerUiState
import me.kafuuneko.rpclient.libs.core.CoreViewModel
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.utils.toPreview
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * JSON 树查看器状态持有者。
 *
 * 完整解析树与路径保留在 ViewModel，UiState 只发布当前层级，避免大 JSON 在重组时反复复制。
 */
class JsonViewerViewModel : CoreViewModel<JsonViewerUiIntent, JsonViewerUiState>(
    JsonViewerUiState.None
) {
    /** 当前载荷标题、根节点与从根到当前节点的导航路径。 */
    private var mTitle: String = ""
    private var mRoot: Any? = null
    private var mPath: List<JsonPathStep> = emptyList()

    @UiIntentObserver(JsonViewerUiIntent.Init::class)
    private fun onInit(intent: JsonViewerUiIntent.Init) {
        if (!isStateOf<JsonViewerUiState.None>()) return

        val payload = JsonViewerPayloadStore.get(intent.payloadKey)
        if (payload == null) {
            JsonViewerUiState.Error(
                title = "",
                message = "JSON payload is not available.",
                rawPreview = ""
            ).setup()
            return
        }

        mTitle = payload.title
        val parsed = parseJson(payload.json)
        if (parsed.isFailure) {
            JsonViewerUiState.Error(
                title = mTitle,
                message = parsed.exceptionOrNull()?.message.orEmpty().ifBlank { "Invalid JSON." },
                rawPreview = payload.json.toPreview()
            ).setup()
            return
        }

        mRoot = parsed.getOrNull()
        mPath = emptyList()
        buildNormalState().setup()
    }

    @UiIntentObserver(JsonViewerUiIntent.Back::class)
    private fun onBack() {
        if (getOrNull<JsonViewerUiState.Normal>()?.canNavigateUp == true) {
            mPath = mPath.dropLast(1)
            buildNormalState().setup()
            return
        }
        JsonViewerUiState.Finished.setup()
    }

    @UiIntentObserver(JsonViewerUiIntent.EntrySelected::class)
    private fun onEntrySelected(intent: JsonViewerUiIntent.EntrySelected) {
        val uiState = getOrNull<JsonViewerUiState.Normal>() ?: return
        val entry = uiState.entries.firstOrNull { it.id == intent.entryId } ?: return
        if (!entry.hasChildren) return

        mPath = mPath + JsonPathStep(
            label = entry.name,
            objectKey = entry.sourceKey,
            arrayIndex = entry.sourceIndex
        )
        buildNormalState().setup()
    }

    private fun parseJson(json: String): Result<Any?> {
        return runCatching {
            val tokener = JSONTokener(json)
            val value = tokener.nextValue()
            val next = tokener.nextClean()
            if (next.code != 0) error("Unexpected data after JSON value.")
            value
        }
    }

    private fun buildNormalState(): JsonViewerUiState.Normal {
        val current = currentNode()
        return JsonViewerUiState.Normal(
            title = mTitle,
            path = listOf("Root") + mPath.map { it.label },
            currentType = current.nodeType(),
            childCount = current.childCount(),
            entries = current.toEntries(),
            canNavigateUp = mPath.isNotEmpty()
        )
    }

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

    private fun Any?.toEntries(): List<JsonViewerEntry> {
        return when (this) {
            is JSONObject -> keys().asSequence().toList().mapIndexed { index, key ->
                val value = opt(key)
                value.toEntry(
                    id = index,
                    name = key,
                    sourceKey = key,
                    sourceIndex = null
                )
            }

            is JSONArray -> (0 until length()).map { index ->
                val value = opt(index)
                value.toEntry(
                    id = index,
                    name = "[$index]",
                    sourceKey = null,
                    sourceIndex = index
                )
            }

            else -> emptyList()
        }
    }

    private fun Any?.toEntry(
        id: Int,
        name: String,
        sourceKey: String?,
        sourceIndex: Int?
    ): JsonViewerEntry {
        return JsonViewerEntry(
            id = id,
            name = name,
            type = nodeType(),
            preview = valuePreview(),
            childCount = childCount(),
            sourceKey = sourceKey,
            sourceIndex = sourceIndex
        )
    }

    private fun Any?.nodeType(): JsonViewerNodeType {
        return when (this) {
            is JSONObject -> JsonViewerNodeType.Object
            is JSONArray -> JsonViewerNodeType.Array
            is Boolean -> JsonViewerNodeType.Boolean
            is Number -> JsonViewerNodeType.Number
            JSONObject.NULL, null -> JsonViewerNodeType.Null
            else -> JsonViewerNodeType.String
        }
    }

    private fun Any?.childCount(): Int {
        return when (this) {
            is JSONObject -> length()
            is JSONArray -> length()
            else -> 0
        }
    }

    private fun Any?.valuePreview(): String {
        return when (this) {
            is JSONObject -> toString().toPreview(180)
            is JSONArray -> toString().toPreview(180)
            is String -> toPreview()
            is Boolean, is Number -> toString()
            JSONObject.NULL, null -> "null"
            else -> toString().toPreview()
        }
    }

    private data class JsonPathStep(
        val label: String,
        val objectKey: String?,
        val arrayIndex: Int?
    )
}
