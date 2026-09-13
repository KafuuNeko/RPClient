package me.kafuuneko.rpclient.libs.llm.adapter

/** 日志及错误回显统一清理图片载荷；只处理副本，不修改实际发送对象。 */
object ImageLogSanitizer {
    private val mDataUrl = Regex("data:image/[^;\\s\"']+;base64,[A-Za-z0-9+/=\\\\\\r\\n]+")
    private val mEncodedRun = Regex("[A-Za-z0-9+/]{256,}={0,2}")
    private val mDataField = Regex("""(["\\]+data["\\]+\s*:\s*["\\]+)[A-Za-z0-9+/=]+""")

    /** 限制持久日志大小，并清理嵌套错误字符串中的 Base64；截断也不会保留载荷前缀。 */
    fun sanitize(value: String): String {
        val fields = mDataField.replace(value) { it.groupValues[1] + "[图片数据已省略，不能直接重放]" }
        val redacted = mEncodedRun.replace(mDataUrl.replace(fields, "[图片数据已省略，不能直接重放]"),
            "[编码载荷已省略，不能直接重放]")
        return if (redacted.length > 65_536) redacted.take(65_536) + "\n[日志已截断]" else redacted
    }
}
