package me.kafuuneko.rpclient.libs.backup

import android.content.Context
import android.util.Xml
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.prompt.PromptTemplateSnapshot
import me.kafuuneko.rpclient.libs.upgrade.LegacyPromptSnapshot
import me.kafuuneko.rpclient.libs.upgrade.PromptPresetMigration
import me.kafuuneko.rpclient.libs.upgrade.UserPersonaUpgrade
import org.xmlpull.v1.XmlPullParser

/**
 * 在备份暂存区迁入旧预设并修复 SP 选择。
 * - 不读取或修改设备在线偏好。
 * - 默认预设不入库；丢失的自定义选择回到 -1。
 */
internal object PromptPresetRestore {
    /** 数据库与偏好都准备成功后，外层才将它们一起替换到在线目录。 */
    fun prepare(
        context: Context,
        db: SupportSQLiteDatabase,
        sourceVersion: Int,
        preferences: File?
    ) {
        val file = preferences?.let { File(it, AppModel.kotprefName + ".xml") }
        val values = readPreferences(file)
        val selected = if (sourceVersion < 6) {
            val checkpoint = if ("lastMigratedVersionCode" in values) {
                requireNotNull(values["lastMigratedVersionCode"] as? Int) { "Invalid backup migration checkpoint" }
            } else 0
            check(checkpoint < 20260404) { "Backup prompt migration checkpoint contradicts schema" }
            PromptPresetMigration.migrate(
                db,
                { LegacyPromptSnapshot.read(values) },
                context.getString(R.string.prompt_template_migrated)
            )
        } else {
            if ("activePromptPresetId" in values) {
                requireNotNull(values["activePromptPresetId"] as? Long) { "Invalid active prompt preset preference" }
            } else PromptTemplateSnapshot.BUILTIN_ID
        }
        // 可编辑归档可能删除了当前预设；选择独立保存在 SP，不需要额外状态表。
        val exists = selected == PromptTemplateSnapshot.BUILTIN_ID || db.query(
            "SELECT id FROM prompt_presets WHERE id = ?",
            arrayOf(selected)
        ).use { it.moveToFirst() }
        if (file != null) writeSelection(
            file,
            if (exists) selected else PromptTemplateSnapshot.BUILTIN_ID
        )
    }

    /** 在还原区迁入人设，默认指针提交后从暂存 XML 移除旧身份键。 */
    fun preparePersonas(db: SupportSQLiteDatabase, preferences: File?) {
        val file = preferences?.let { File(it, AppModel.kotprefName + ".xml") }
        val values = readPreferences(file)
        UserPersonaUpgrade.migrate(db, values)
        UserPersonaUpgrade.validate(db)
        if (file != null && UserPersonaUpgrade.keys.any(values::containsKey)) {
            writeSelection(
                file,
                values["activePromptPresetId"] as? Long ?: PromptTemplateSnapshot.BUILTIN_ID,
                UserPersonaUpgrade.keys
            )
        }
    }

    /** 校验固定旧键与新选择；未知偏好由原始 XML 原样转写，不参与预设解析。 */
    private fun readPreferences(file: File?): Map<String, Any> {
        if (file == null || !file.exists()) return emptyMap()
        val result = mutableMapOf<String, Any>()
        file.inputStream().use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, "UTF-8")
            var hasRoot = false
            // 禁止文档类型，防止外部实体参与偏好解析。
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                check(parser.eventType != XmlPullParser.DOCDECL) { "Preference XML document types are not supported" }
                if (parser.eventType == XmlPullParser.START_TAG && parser.depth == 1) {
                    check(parser.name == "map") { "Invalid preference XML root" }
                    hasRoot = true
                }
                if (parser.eventType == XmlPullParser.START_TAG && parser.depth == 2) {
                    val key = parser.getAttributeValue(null, "name")
                    if (key in UserPersonaUpgrade.keys || key in LegacyPromptSnapshot.keys || key == "lastMigratedVersionCode" || key == "activePromptPresetId") {
                        check(key !in result) { "Duplicate prompt preference" }
                        result[key] = when (parser.name) {
                            "string" -> parser.nextText()
                            "int" -> parser.getAttributeValue(null, "value").toInt()
                            "long" -> parser.getAttributeValue(null, "value").toLong()
                            else -> false
                        }
                    }
                }
                parser.nextToken()
            }
            check(hasRoot) { "Missing preference XML root" }
        }
        return result
    }

    /** 只替换选择键，保留其他偏好的类型、文本和集合；失败只影响暂存文件。 */
    private fun writeSelection(file: File, selected: Long, remove: Set<String> = emptySet()) {
        file.parentFile?.mkdirs()
        val replacedKeys = remove + "activePromptPresetId"
        val temporary = File(file.parentFile, file.name + ".prompt-tmp")
        try {
            temporary.outputStream().use { output ->
                val writer = Xml.newSerializer().apply {
                    setOutput(output, "UTF-8")
                    startDocument("UTF-8", true)
                    startTag(null, "map")
                }
                if (file.exists()) file.inputStream().use { input ->
                    val parser = Xml.newPullParser().apply { setInput(input, "UTF-8") }
                    var skipDepth = 0
                    // 复制根下的完整元素；只跳过旧选择，字符串正文不做 trim。
                    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                        when (parser.eventType) {
                            XmlPullParser.START_TAG -> if (parser.depth > 1) {
                                if (parser.depth == 2 && parser.getAttributeValue(
                                        null,
                                        "name"
                                    ) in replacedKeys
                                ) skipDepth = parser.depth
                                if (skipDepth == 0) {
                                    writer.startTag(null, parser.name)
                                    for (index in 0 until parser.attributeCount) writer.attribute(
                                        null,
                                        parser.getAttributeName(index),
                                        parser.getAttributeValue(index)
                                    )
                                }
                            }

                            XmlPullParser.TEXT -> if (skipDepth == 0 && parser.depth > 1) {
                                // CR 必须写成字符引用，否则下次解析 XML 会归一化为 LF。
                                parser.text.split('\r').forEachIndexed { index, part ->
                                    if (index > 0) writer.entityRef("#13")
                                    writer.text(part)
                                }
                            }

                            XmlPullParser.END_TAG -> if (parser.depth > 1) {
                                if (skipDepth == 0) writer.endTag(null, parser.name)
                                else if (parser.depth == skipDepth) skipDepth = 0
                            }
                        }
                        parser.next()
                    }
                }
                writer.startTag(null, "long").attribute(null, "name", "activePromptPresetId")
                    .attribute(null, "value", selected.toString()).endTag(null, "long")
                writer.endTag(null, "map").endDocument()
            }
            check(temporary.renameTo(file)) { "Failed to persist restored prompt selection" }
        } finally {
            temporary.delete()
        }
    }
}
