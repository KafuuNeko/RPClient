package me.kafuuneko.rpclient.libs.llm.adapter

import java.io.IOException

/** 一个完整 SSE 帧；event 名称可省略，data 保留多行之间的换行。 */
internal data class SseEvent(val event: String, val data: String)

/**
 * 按 SSE 空行边界组装事件。
 * - 忽略心跳注释和传输元数据，支持多行 data 及首行 BOM。
 * - 限制未完成帧大小，防止异常服务无限堆积内存。
 */
internal class SseEventDecoder {
    private val mData = StringBuilder()
    private var mEvent = ""
    private var mHasData = false
    private var mFirstLine = true

    /** 消费一行；只有到达帧边界时才返回完整事件。 */
    fun accept(rawLine: String): SseEvent? {
        val line = if (mFirstLine) rawLine.removePrefix("\uFEFF") else rawLine
        mFirstLine = false
        if (line.isEmpty()) return finish()
        if (line.startsWith(':')) return null
        val field = line.substringBefore(':')
        val value = if (':' in line) line.substringAfter(':').removePrefix(" ") else ""
        // SSE 只移除冒号后的一个空格，正文自己的空白必须保留。
        when (field) {
            "event" -> mEvent = value
            "data" -> {
                if (mData.length.toLong() + value.length + 1 > MAX_EVENT_CHARACTERS) {
                    throw IOException("The server sent an oversized streaming event.")
                }
                if (mHasData) mData.append('\n')
                mData.append(value)
                mHasData = true
            }
        }
        return null
    }

    /** 取出待处理帧并清空状态；也允许读取连接结束前最后一个完整 JSON 载荷。 */
    fun finish(): SseEvent? {
        val result = if (mHasData) SseEvent(mEvent, mData.toString()) else null
        mData.clear()
        mEvent = ""
        mHasData = false
        return result
    }

    private companion object {
        const val MAX_EVENT_CHARACTERS = 16 * 1024 * 1024
    }
}
