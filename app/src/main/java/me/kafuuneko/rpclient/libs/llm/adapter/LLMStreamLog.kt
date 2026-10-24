package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/**
 * 有界的 SSE 调试记录器。
 *
 * - 以序列化后的实际字符数计入预算，保留完整行和合法数组结构；
 * - 采集超限后停止追加正文，但继续消费实际生成流；
 * - 通过结构化标记记录省略及中断，不伪装成完整网络响应。
 */
internal class LLMStreamLog {
    private val mLines = JsonArray()
    private var mCharacters = 2
    private var mTruncated = false

    /** 追加完整且脱敏的 SSE 行，超预算时不保存行的一部分。 */
    fun append(line: String) {
        if (mTruncated) return
        val safe = ImageLogSanitizer.redact(line)
        if (safe.length > MAX_CHARACTERS) {
            mTruncated = true
            return
        }
        val value = JsonPrimitive(safe)
        val size = value.toString().length + 1
        if (mCharacters.toLong() + size > MAX_CHARACTERS) {
            mTruncated = true
            return
        }
        mLines.add(value)
        mCharacters += size
    }

    /** 中断记录单独保存诊断对象，错误正文继续遵守短提示的脱敏预算。 */
    fun addError(message: String?) {
        mLines.add(JsonObject().apply {
            addProperty("_rpclientLog", "interrupted")
            addProperty("error", ImageLogSanitizer.sanitize(message.orEmpty()).take(4096))
        })
    }

    /** 返回合法 JSON 数组；只在最终序列化时附加一次超限标记。 */
    fun toJson(): String {
        val result = mLines.deepCopy()
        if (mTruncated) {
            result.add(JsonObject().apply {
                addProperty("_rpclientLog", "truncated")
                addProperty("reason", "Stream capture size limit exceeded.")
            })
        }
        return result.toString()
    }

    private companion object {
        // 为数组标点、诊断对象及错误信息预留空间，不复用页面的源码展示预算。
        const val MAX_CHARACTERS = ImageLogSanitizer.MAX_LOG_CHARACTERS - 262_144
    }
}
