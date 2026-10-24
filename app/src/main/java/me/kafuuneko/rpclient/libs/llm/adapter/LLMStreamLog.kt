package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonObject
import com.google.gson.stream.JsonWriter
import me.kafuuneko.rpclient.libs.json.JsonDocumentCodec
import me.kafuuneko.rpclient.libs.json.JsonDocumentLimits
import me.kafuuneko.rpclient.libs.json.JsonResourceLimitException
import java.io.Writer

/**
 * 按 SSE 事件采集结构化调试日志，不参与实际模型响应的解析。
 *
 * - 普通 JSON 对象和数组直接入列，保留数字词法和重复键；
 * - 元数据、非 JSON 正文、结束及中断使用明确的日志记录；
 * - 待完成事件和序列化输出均受预算限制，空行及心跳不形成空条目。
 */
internal class LLMStreamLog {
    private val mEntries = mutableListOf<String>()
    private val mData = StringBuilder()
    private val mRaw = StringBuilder()
    private val mFields = linkedMapOf<String, String>()
    private var mHasData = false
    private var mFirstLine = true
    private var mCharacters = 2
    private var mFinished = false
    private var mTruncated = false

    /**
     * 消费一行，仅在空行或收尾时提交完整事件；冒号后只移除一个协议空格。
     * @param line 网络层按行拆分的 SSE 原文
     */
    fun append(line: String) {
        if (mFinished || mTruncated) return
        val safe = ImageLogSanitizer.redact(if (mFirstLine) line.removePrefix("\uFEFF") else line)
        mFirstLine = false
        if (safe.isEmpty()) {
            finishEvent()
            return
        }
        if (safe.startsWith(':')) return
        // 输入先受限，避免异常服务在事件分隔符到达前无限堆积。
        val pending = mData.length.toLong() + mRaw.length + mFields.values.sumOf { it.length }
        if (mCharacters + pending + safe.length + 1 > MAX_CHARACTERS) {
            truncate()
            return
        }
        val field = safe.substringBefore(':')
        val value = if (':' in safe) safe.substringAfter(':').removePrefix(" ") else ""
        when (field) {
            "data" -> {
                if (mHasData) mData.append('\n')
                mData.append(value)
                mHasData = true
            }

            "event", "id", "retry" -> mFields[field] = value
            else -> {
                if (mRaw.isNotEmpty()) mRaw.append('\n')
                mRaw.append(safe)
            }
        }
    }

    /**
     * 中断前提交已接收事件，异常正文继续遵守脱敏和短提示预算。
     * @param message 内部异常信息，仅保存脱敏后的短摘要
     */
    fun addError(message: String?) {
        if (mFinished) return
        finishEvent()
        mFinished = true
        // 诊断使用独立预留空间，采集超限后仍能说明连接为何结束。
        mEntries.add(JsonObject().apply {
            addProperty("_rpclientLog", "interrupted")
            addProperty("error", ImageLogSanitizer.sanitize(message.orEmpty()).take(4096))
        }.toString())
    }

    /**
     * 提交没有末尾空行的尾帧，完成后的重复读取不会重复附加事件或标记。
     * @return 受预算约束的 JSON 数组文本
     */
    fun toJson(): String {
        if (!mFinished) {
            finishEvent()
            mFinished = true
        }
        // 超限标记只在输出时拼接，不改写已经冻结的事件列表。
        val entries =
            if (mTruncated) mEntries.asSequence() + sequenceOf(TRUNCATED) else mEntries.asSequence()
        return entries.joinToString(separator = ",", prefix = "[", postfix = "]")
    }

    /** 将同一事件的多行 data 合并后校验；合法载荷不经过 DOM，避免重复键丢失。 */
    private fun finishEvent() {
        if (mTruncated || (!mHasData && mFields.isEmpty() && mRaw.isEmpty())) return
        val data = ImageLogSanitizer.redact(mData.toString()).removePrefix("\uFEFF")
        val done = mHasData && data.trim() == "[DONE]"
        val validJson = mHasData && !done && isJson(data)
        val metadata = mFields.isNotEmpty() || mRaw.isNotEmpty()
        // 常见 JSON 载荷直接入列，不再把整个对象包成字符串。
        if (validJson && !metadata) {
            commit(data)
        } else {
            try {
                commit(encodeEvent(data = data, validJson = validJson, done = done))
            } catch (_: JsonResourceLimitException) {
                truncate()
            }
        }
        clearEvent()
    }

    /** 包装只添加传输元数据，payload 中合法的 JSON 使用原始词法写入。 */
    private fun encodeEvent(data: String, validJson: Boolean, done: Boolean): String {
        val output = EventWriter(MAX_CHARACTERS - mCharacters)
        JsonWriter(output).use { writer ->
            writer.beginObject()
            writer.name("_rpclientLog").value(
                when {
                    done -> "done"
                    validJson -> "event"
                    mHasData || mRaw.isNotEmpty() -> "raw"
                    else -> "metadata"
                }
            )
            // 元数据与服务端对象分层，避免 event、id 等字段覆盖模型载荷。
            mFields.forEach { (name, value) -> writer.name(name).value(value) }
            if (mHasData && !done) {
                // 避开图片附件专用的 data 脱敏字段，原文和字符串载荷不能被误当成图片数据。
                writer.name(if (validJson) "payload" else "rawData")
                if (validJson) writer.jsonValue(data) else writer.value(data)
            }
            if (mRaw.isNotEmpty()) writer.name("raw").value(mRaw.toString())
            writer.endObject()
        }
        return output.toString()
    }

    /** 深度和 Token 校验先于输出，异常内容降级为文本，不递归构造任意大小的对象树。 */
    private fun isJson(data: String): Boolean {
        return try {
            JsonDocumentCodec.validate(data, PAYLOAD_LIMITS)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 按最终 JSON 字符数计入数组预算，只提交整条事件。 */
    private fun commit(value: String) {
        if (mTruncated) return
        val characters = value.length.toLong() + if (mEntries.isEmpty()) 0 else 1
        if (mCharacters + characters > MAX_CHARACTERS) {
            truncate()
            return
        }
        mEntries.add(value)
        mCharacters += characters.toInt()
    }

    /** 超限只影响调试采集，不终止网络消费或向实际生成解析器抛错。 */
    private fun truncate() {
        mTruncated = true
        clearEvent()
    }

    /** 事件结束后释放当前字段内容，下一帧不能继承上一帧的显式元数据。 */
    private fun clearEvent() {
        mData.clear()
        mRaw.clear()
        mFields.clear()
        mHasData = false
    }

    /** 限制原文转义后的实际输出，控制字符不能绕过事件预算。 */
    private class EventWriter(private val mLimit: Int) : Writer() {
        private val mOutput = StringBuilder()

        /** 写入之前校验，失败时不会先构建超大转义字符串。 */
        override fun write(buffer: CharArray, offset: Int, length: Int) {
            if (mOutput.length.toLong() + length > mLimit) throw JsonResourceLimitException()
            mOutput.append(buffer, offset, length)
        }

        /** 内存缓冲不需要刷新。 */
        override fun flush() = Unit

        /** 字符流不持有系统资源。 */
        override fun close() = Unit

        /** 返回已经受预算约束的事件文本。 */
        override fun toString(): String = mOutput.toString()
    }

    private companion object {
        const val MAX_CHARACTERS = ImageLogSanitizer.MAX_LOG_CHARACTERS - 262_144
        const val TRUNCATED =
            "{\"_rpclientLog\":\"truncated\",\"reason\":\"Stream capture size limit exceeded.\"}"
        val PAYLOAD_LIMITS =
            JsonDocumentLimits(inputCharacters = MAX_CHARACTERS, tokens = 200_000, depth = 126)
    }
}
