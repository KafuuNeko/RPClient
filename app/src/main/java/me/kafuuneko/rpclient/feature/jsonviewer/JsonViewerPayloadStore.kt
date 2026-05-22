package me.kafuuneko.rpclient.feature.jsonviewer

import me.kafuuneko.rpclient.feature.jsonviewer.model.JsonViewerPayload
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object JsonViewerPayloadStore {
    private val mPayloads = ConcurrentHashMap<String, JsonViewerPayload>()

    fun put(title: String, json: String): String {
        val key = UUID.randomUUID().toString()
        mPayloads[key] = JsonViewerPayload(title = title, json = json)
        return key
    }

    fun get(key: String?): JsonViewerPayload? {
        if (key.isNullOrBlank()) return null
        return mPayloads[key]
    }

    fun remove(key: String?) {
        if (key.isNullOrBlank()) return
        mPayloads.remove(key)
    }
}

