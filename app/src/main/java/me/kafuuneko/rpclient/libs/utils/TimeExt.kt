package me.kafuuneko.rpclient.libs.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Long.formatTimestamp(
    pattern: String,
    locale: Locale = Locale.getDefault()
): String {
    return SimpleDateFormat(pattern, locale).format(Date(this))
}
