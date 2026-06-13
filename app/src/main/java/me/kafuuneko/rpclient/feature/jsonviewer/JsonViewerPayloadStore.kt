package me.kafuuneko.rpclient.feature.jsonviewer

import me.kafuuneko.rpclient.feature.jsonviewer.model.JsonViewerPayload
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * JSON 查看器的进程内临时载荷仓库。
 *
 * 避免把可能很大的请求或响应 JSON 放入 Intent；页面结束后使用 key 主动删除载荷。
 */
object JsonViewerPayloadStore {
    /** 支持配置变化期间并发读取的载荷表。 */
    private val mPayloads = ConcurrentHashMap<String, JsonViewerPayload>()

    /** 使用随机 key 保存载荷并返回导航参数。 */
    fun put(title: String, json: String): String {
        val key = UUID.randomUUID().toString()
        mPayloads[key] = JsonViewerPayload(title = title, json = json)
        return key
    }

    /** 读取载荷但不移除，以支持页面重建。 */
    fun get(key: String?): JsonViewerPayload? {
        if (key.isNullOrBlank()) return null
        return mPayloads[key]
    }

    /** 页面真正结束时释放载荷。 */
    fun remove(key: String?) {
        if (key.isNullOrBlank()) return
        mPayloads.remove(key)
    }
}
