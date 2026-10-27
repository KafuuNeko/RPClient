package me.kafuuneko.rpclient.libs.backup

import org.json.JSONArray
import org.json.JSONObject

/** 归档格式常量和 JSON 数组转换；版本与字段名保持既有兼容协议。 */
internal object BackupArchiveFormat {
    const val FORMAT = "rpclient-portable-data"
    const val VERSION = 2
    const val VERSION_3 = 3
    const val LEGACY_VERSION = 1
    const val PRIMARY_DB = "primary.sqlite"
    const val LOGS_DB = "request_logs.sqlite"
    const val BUFFER_SIZE = 32 * 1024
    const val MAX_ENTRIES = 50_000
    const val MAX_BYTES = 2L * 1024 * 1024 * 1024
    const val TABLE_ROWS_KEY = "_tableRows"
    const val BLOB_KEY = "$" + "blobBase64"
}

/** 读取 JSON 数组中的对象，保持归档中的原始顺序。 */
internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

/** 保持既有行顺序写入 JSON 数组。 */
internal fun List<JSONObject>.toJsonArray(): JSONArray = JSONArray().apply {
    this@toJsonArray.forEach { put(it) }
}
