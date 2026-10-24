package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonObject

/** 日志及错误回显统一清理图片载荷；只处理副本，不修改实际发送对象。 */
object ImageLogSanitizer {
    private val mDataUrl = Regex("data:image/[^;\\s\"']+;base64,[A-Za-z0-9+/=\\\\\\r\\n]+")
    private val mEncodedRun = Regex("[A-Za-z0-9+/]{256,}={0,2}")
    private val mDataField = Regex("""(["\\]+data["\\]+\s*:\s*["\\]+)[A-Za-z0-9+/=]+""")

    /** 限制持久日志大小，并清理嵌套错误字符串中的 Base64；截断也不会保留载荷前缀。 */
    fun sanitize(value: String): String {
        val redacted = redact(value)
        return if (redacted.length > 65_536) redacted.take(65_536) + "\n$LOG_TRUNCATED" else redacted
    }

    /** 只清除图片与编码载荷，不把错误提示的长度限制施加到调试日志。 */
    fun redact(value: String): String {
        val fields = mDataField.replace(value) { it.groupValues[1] + IMAGE_DATA_OMITTED }
        return mEncodedRun.replace(mDataUrl.replace(fields, IMAGE_DATA_OMITTED), PAYLOAD_OMITTED)
    }

    /** 大日志使用独立预算；达到预算时输出合法诊断对象而非切断 JSON 结构。 */
    fun sanitizeLog(value: String): String {
        val redacted = redact(value)
        if (redacted.length <= MAX_LOG_CHARACTERS) return redacted
        return JsonObject().apply {
            addProperty("_rpclientLog", "truncated")
            addProperty("reason", "Log capture size limit exceeded.")
            addProperty("redactedCharacters", redacted.length)
            addProperty("preview", redacted.take(65_536))
        }.toString()
    }

    /** 每个持久化正文最多保留约四百万字符，界面预览使用更小的独立预算。 */
    const val MAX_LOG_CHARACTERS = 4_194_304

    /** 占位标记是写入请求日志的诊断文案，不面向界面本地化，统一保持英文。 */
    private const val IMAGE_DATA_OMITTED = "[image data omitted; cannot be replayed directly]"
    private const val PAYLOAD_OMITTED = "[encoded payload omitted; cannot be replayed directly]"
    private const val LOG_TRUNCATED = "[log truncated]"
}
