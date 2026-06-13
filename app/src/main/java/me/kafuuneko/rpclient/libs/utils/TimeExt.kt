package me.kafuuneko.rpclient.libs.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 将毫秒时间戳按指定格式转换为本地时间文本。 */
fun Long.formatTimestamp(
    pattern: String,
    locale: Locale = Locale.getDefault()
): String {
    return SimpleDateFormat(pattern, locale).format(Date(this))
}

/** 使用创建时间生成稳定的默认聊天标题。 */
fun Long.toDefaultChatTitle(): String {
    return formatTimestamp("yyyyMMdd-HHmm", Locale.ROOT)
}
