package me.kafuuneko.rpclient.libs.backup

import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import android.net.Uri
import android.util.Base64
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.RequestLogDatabase
import org.json.JSONArray
import org.json.JSONObject

/**
 * Feature-oriented portable backup. Exact Room rows are embedded in JSON/JSONL documents so every
 * relationship can be reconstructed without including opaque SQLite database files.
 */
class PortableBackupRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val requestLogDatabase: RequestLogDatabase
) {
    companion object {
        private const val FORMAT = "rpclient-portable-data"
        private const val VERSION = 2
        private const val LEGACY_VERSION = 1
        private const val PRIMARY_DB = "primary.sqlite"
        private const val LOGS_DB = "request_logs.sqlite"
        private const val BUFFER_SIZE = 32 * 1024
        private const val MAX_ENTRIES = 50_000
        private const val MAX_BYTES = 2L * 1024 * 1024 * 1024
        private const val TABLE_ROWS_KEY = "_tableRows"
        private const val BLOB_KEY = "$" + "blobBase64"
    }

    suspend fun exportArchive(destination: Uri) = withContext(Dispatchers.IO) {
        val stage = File(context.cacheDir, "portable-export-" + System.nanoTime())
        check(stage.mkdirs()) { "Could not create temporary backup workspace." }
        try {
            createArchiveDirectories(stage)
            val primaryDb = database.openHelper.writableDatabase
            val primaryTables = tableNames(primaryDb)
            primaryDb.beginTransaction()
            try {
                writeText(File(stage, "app-data/database-schema.json"), schemaJson(primaryDb).toString(2))
                exportFeatureFiles(primaryDb, primaryTables, stage)
                primaryDb.setTransactionSuccessful()
            } finally {
                primaryDb.endTransaction()
            }

            val logsDb = requestLogDatabase.openHelper.writableDatabase
            logsDb.beginTransaction()
            try {
                writeText(File(stage, "app-data/request-log-schema.json"), schemaJson(logsDb).toString(2))
                exportRequestLogs(logsDb, stage)
                logsDb.setTransactionSuccessful()
            } finally {
                logsDb.endTransaction()
            }

            exportSettings(stage)
            copyDirectory(context.filesDir, File(stage, "app-data/private-files"))
            exportAttachments(primaryDb, primaryTables, stage)

            val fileIndex = JSONObject()
            stage.walkTopDown().filter { it.isFile }.toList().forEach { file ->
                fileIndex.put(file.relativeTo(stage).invariantSeparatorsPath, JSONObject()
                    .put("size", file.length()).put("sha256", sha256(file)))
            }
            val manifest = JSONObject()
                .put("format", FORMAT)
                .put("version", VERSION)
                .put("createdAt", System.currentTimeMillis())
                .put("appPackage", context.packageName)
                .put("encrypted", false)
                .put("restoreMode", "full-replacement")
                .put("structure", JSONArray()
                    .put("characters/character-<id>.json")
                    .put("stories/story-<id>.rpstory.json")
                    .put("chats/conversation-<id>.jsonl")
                    .put("group-chats/group-<id>.jsonl")
                    .put("worldbooks/worldbook-<id>.json")
                    .put("regex/global-scripts.json")
                    .put("regex/character-scripts/character-<id>.json")
                    .put("regex/character-authorizations.json")
                    .put("providers/provider-configurations.json")
                    .put("attachments/characters/")
                    .put("attachments/chat-images/")
                    .put("attachments/other-files/")
                    .put("app-data/token-usage.json"))
                .put("components", JSONObject()
                    .put("characters", true).put("stories", true).put("chats", true)
                    .put("groupChats", true).put("worldbooks", true).put("regex", true)
                    .put("providersAndApiKeys", true).put("settingsAndPrompts", true)
                    .put("tokenUsage", true).put("requestLogs", true)
                    .put("attachments", true).put("privateAppFiles", true))
                .put("recordCounts", countRecords(stage))
                .put("files", fileIndex)
                .put("notes", "Feature-oriented JSON/JSONL with exact relational rows. Not encrypted; may contain API keys and raw request/response logs.")
            writeText(File(stage, "manifest.json"), manifest.toString(2))

            val output = context.contentResolver.openOutputStream(destination, "wt")
                ?: error("Could not open the selected destination.")
            output.use { raw ->
                ZipOutputStream(raw.buffered()).use { zip ->
                    stage.walkTopDown().drop(1).forEach { file ->
                        val path = file.relativeTo(stage).invariantSeparatorsPath
                        if (file.isDirectory) {
                            zip.putNextEntry(ZipEntry(path.trimEnd('/') + "/"))
                            zip.closeEntry()
                        } else {
                            zip.putNextEntry(ZipEntry(path))
                            FileInputStream(file).use { it.copyTo(zip, BUFFER_SIZE) }
                            zip.closeEntry()
                        }
                    }
                }
            }
        } finally {
            stage.deleteRecursively()
        }
    }

    suspend fun restoreArchive(source: Uri) = withContext(Dispatchers.IO) {
        val stage = File(context.cacheDir, "portable-restore-" + System.nanoTime())
        val rollback = File(context.cacheDir, "portable-rollback-" + System.nanoTime())
        check(stage.mkdirs()) { "Could not create temporary restore workspace." }
        try {
            extractArchive(source, stage)
            val manifestFile = File(stage, "manifest.json")
            require(manifestFile.isFile) { "This ZIP is missing manifest.json." }
            val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
            if (manifest.optString("format") == "rpclient-backup") {
                BackupArchiveRepository(context, database).restoreArchive(source)
                return@withContext
            }
            require(manifest.optString("format") == FORMAT) { "This is not an RPClient portable data backup." }
            val version = manifest.optInt("version", -1)
            require(version == VERSION || version == LEGACY_VERSION) {
                "Unsupported portable backup version: " + version
            }
            validateChecksums(stage, manifest.optJSONObject("files")
                ?: error("The manifest does not list archive files."))

            if (version == LEGACY_VERSION && File(stage, "database/primary/schema.json").isFile) {
                val legacyPrimary = File(stage, "database/primary")
                val legacyLogs = File(stage, "database/request-logs")
                val stagedPrimary = File(stage, PRIMARY_DB)
                val stagedLogs = File(stage, LOGS_DB)
                reconstructDatabase(
                    JSONObject(File(legacyPrimary, "schema.json").readText(Charsets.UTF_8)),
                    readLegacyRows(legacyPrimary), stagedPrimary
                )
                reconstructDatabase(
                    JSONObject(File(legacyLogs, "schema.json").readText(Charsets.UTF_8)),
                    readLegacyRows(legacyLogs), stagedLogs
                )
                replaceLiveData(
                    rollback, stagedPrimary, stagedLogs,
                    File(stage, "settings/shared_prefs"),
                    File(stage, "attachments/repository"),
                    File(stage, "app-files")
                )
                return@withContext
            }

            require(version == VERSION) { "Legacy archive layout is incomplete." }
            val primarySchemaFile = File(stage, "app-data/database-schema.json")
            val logsSchemaFile = File(stage, "app-data/request-log-schema.json")
            require(primarySchemaFile.isFile) { "The primary database schema is missing." }
            require(logsSchemaFile.isFile) { "The request-log database schema is missing." }
            val primaryRows = collectTableRows(stage, excludePath = "app-data/request-logs.jsonl")
            val requestRows = collectTableRows(stage, includePath = "app-data/request-logs.jsonl")
            val stagedPrimary = File(stage, PRIMARY_DB)
            val stagedLogs = File(stage, LOGS_DB)
            reconstructDatabase(
                JSONObject(primarySchemaFile.readText(Charsets.UTF_8)), primaryRows, stagedPrimary
            )
            reconstructDatabase(
                JSONObject(logsSchemaFile.readText(Charsets.UTF_8)), requestRows, stagedLogs
            )

            replaceLiveData(
                rollback, stagedPrimary, stagedLogs,
                File(stage, "app-data/shared-prefs"),
                File(stage, "attachments/other-files"),
                File(stage, "app-data/private-files")
            )
        } finally {
            stage.deleteRecursively()
            rollback.deleteRecursively()
        }
    }

    private fun createArchiveDirectories(root: File) {
        listOf(
            "characters", "stories", "chats", "group-chats", "worldbooks",
            "regex/character-scripts", "providers", "attachments/characters",
            "attachments/chat-images", "attachments/other-files", "app-data/shared-prefs",
            "app-data/private-files"
        ).forEach { File(root, it).mkdirs() }
    }

    private fun exportSettings(root: File) {
        val sourcePrefs = File(context.dataDir, "shared_prefs")
        val targetPrefs = File(root, "app-data/shared-prefs")
        copyDirectory(sourcePrefs, targetPrefs)
        val preferences = JSONObject()
        targetPrefs.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".xml") }.forEach { xml ->
            val prefsName = xml.name.removeSuffix(".xml")
            preferences.put(prefsName, sharedPreferencesJson(
                context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            ))
        }
        writeText(File(root, "settings.json"),
            JSONObject()
                .put("format", FORMAT)
                .put("version", VERSION)
                .put("preferences", preferences)
                .put("providerCredentialsIncluded", true)
                .put("encrypted", false)
                .put("rawPreferenceXml", "app-data/shared-prefs/")
                .put("note", "Preferences are provided as readable JSON and preserved as original XML for exact restoration.")
                .toString(2))
    }

    private fun sharedPreferencesJson(preferences: SharedPreferences): JSONObject {
        val json = JSONObject()
        preferences.all.toSortedMap().forEach { (key, value) ->
            when (value) {
                null -> json.put(key, JSONObject.NULL)
                is String -> json.put(key, value)
                is Boolean -> json.put(key, value)
                is Int -> json.put(key, value)
                is Long -> json.put(key, value)
                is Float -> json.put(key, value.toDouble())
                is Set<*> -> json.put(key, JSONArray().apply {
                    value.filterIsInstance<String>().sorted().forEach { put(it) }
                })
                else -> json.put(key, value.toString())
            }
        }
        return json
    }

    private fun exportFeatureFiles(
        db: SupportSQLiteDatabase,
        tables: List<String>,
        root: File
    ) {
        val ownedTables = mutableSetOf<String>()
        fun own(vararg names: String) {
            names.filter { it in tables }.forEach { ownedTables.add(it) }
        }
        fun rows(table: String): JSONArray = if (table in tables) readRows(db, table) else JSONArray()
        fun filtered(table: String, column: String, value: Any?): JSONArray =
            if (table in tables) readRows(db, table, column, value) else JSONArray()

        val characterRows = rows("character")
        own("character")
        val charactersById = characterRows.objects().associateBy { it.optLong("id") }
        val fileRows = rows("files")
        own("files")
        characterRows.objects().forEach { character ->
            val id = character.optLong("id")
            val relations = filtered("character_llm_provider_associations", "characterId", id)
            writeText(File(root, "characters/character-" + id + ".json"),
                JSONObject()
                    .put("format", "rpclient-character")
                    .put("version", VERSION)
                    .put("character", character)
                    .put("providerAssociations", relations)
                    .put(TABLE_ROWS_KEY, tableRows("character" to JSONArray().put(character)))
                    .toString(2))
        }

        val storyRows = rows("stories")
        own("stories")
        val volumeRows = rows("story_volumes")
        val chapterRows = rows("story_chapters")
        val storyCharacterRows = rows("story_characters")
        val storyLorebookRows = rows("story_lorebook_entries")
        own("story_volumes", "story_chapters", "story_characters", "story_lorebook_entries")
        storyRows.objects().forEach { story ->
            val id = story.optLong("id")
            val volumes = volumeRows.objects().filter { it.optLong("storyId") == id }
                .sortedBy { it.optInt("sortOrder") }
            val chapters = chapterRows.objects().filter { it.optLong("storyId") == id }
                .sortedBy { it.optInt("sortOrder") }
            val characters = storyCharacterRows.objects().filter { it.optLong("storyId") == id }
                .sortedBy { it.optInt("sortOrder") }
            val loreStates = storyLorebookRows.objects().filter { it.optLong("storyId") == id }
            fun archivedChapter(chapter: JSONObject) = JSONObject()
                .put("title", chapter.optString("title"))
                .put("content", chapter.optString("content"))
                .put("continuationGuidance", chapter.optString("continuationGuidance"))
            val volumeDocs = JSONArray()
            volumes.forEach { volume ->
                val volumeId = volume.optLong("id")
                volumeDocs.put(JSONObject()
                    .put("title", volume.optString("title"))
                    .put("chapters", JSONArray().apply {
                        chapters.filter { !it.isNull("volumeId") && it.optLong("volumeId") == volumeId }
                            .forEach { put(archivedChapter(it)) }
                    }))
            }
            val ungrouped = JSONArray()
            chapters.filter { it.isNull("volumeId") }.forEach { ungrouped.put(archivedChapter(it)) }
            val archivedStory = JSONObject()
                .put("title", story.optString("title"))
                .put("memory", story.optString("memory"))
                .put("summary", story.optString("summary"))
                .put("authorNote", story.optString("authorNote"))
                .put("includeUserPersona", story.optBoolean("includeUserPersona"))
                .put("ungroupedChapters", ungrouped)
                .put("volumes", volumeDocs)
            val tableData = tableRows(
                "stories" to JSONArray().put(story),
                "story_volumes" to JSONArray().apply { volumes.forEach { put(it) } },
                "story_chapters" to JSONArray().apply { chapters.forEach { put(it) } },
                "story_characters" to JSONArray().apply { characters.forEach { put(it) } },
                "story_lorebook_entries" to JSONArray().apply { loreStates.forEach { put(it) } }
            )
            writeText(File(root, "stories/story-" + id + ".rpstory.json"),
                JSONObject()
                    .put("format", "rpclient_story")
                    .put("version", 2)
                    .put("story", archivedStory)
                    .put("characterHints", JSONArray())
                    .put("lorebookHints", JSONArray())
                    .put("characterRelations", JSONArray().apply { characters.forEach { put(it) } })
                    .put("worldBookEntryStates", JSONArray().apply { loreStates.forEach { put(it) } })
                    .put(TABLE_ROWS_KEY, tableData)
                    .toString(2))
        }

        val chatSessions = rows("chat_sessions")
        own("chat_sessions")
        chatSessions.objects().forEach { session ->
            val id = session.optLong("id")
            val messages = filtered("chat_messages", "sessionId", id).objects()
                .sortedWith(compareBy<JSONObject> { it.optLong("createTime") }.thenBy { it.optLong("id") })
            own("chat_messages")
            File(root, "chats/conversation-" + id + ".jsonl").bufferedWriter(Charsets.UTF_8).use { writer ->
                writeJsonLine(JSONObject()
                    .put("type", "header")
                    .put("format", "rpclient-conversation")
                    .put("session", session)
                    .put(TABLE_ROWS_KEY, tableRows("chat_sessions" to JSONArray().put(session))), writer)
                messages.forEach { message ->
                    writeJsonLine(JSONObject()
                        .put("type", "message")
                        .put("message", message)
                        .put(TABLE_ROWS_KEY, tableRows("chat_messages" to JSONArray().put(message))), writer)
                }
            }
        }

        val groupSessions = rows("group_chat_sessions")
        own("group_chat_sessions")
        groupSessions.objects().forEach { session ->
            val id = session.optLong("id")
            val members = filtered("group_chat_members", "sessionId", id).objects()
                .sortedBy { it.optInt("sortOrder") }.toJsonArray()
            val messages = filtered("group_chat_messages", "sessionId", id).objects()
                .sortedWith(compareBy<JSONObject> { it.optLong("createTime") }.thenBy { it.optLong("id") })
            val summaries = filtered("group_chat_summaries", "sessionId", id).objects()
                .sortedWith(compareBy<JSONObject> { it.optLong("createTime") }.thenBy { it.optLong("id") })
                .toJsonArray()
            own("group_chat_members", "group_chat_messages", "group_chat_summaries")
            val headerRows = tableRows(
                "group_chat_sessions" to JSONArray().put(session),
                "group_chat_members" to members,
                "group_chat_summaries" to summaries
            )
            File(root, "group-chats/group-" + id + ".jsonl").bufferedWriter(Charsets.UTF_8).use { writer ->
                writeJsonLine(JSONObject()
                    .put("type", "header")
                    .put("format", "rpclient-group-chat")
                    .put("session", session)
                    .put("members", members)
                    .put("summaries", summaries)
                    .put(TABLE_ROWS_KEY, headerRows), writer)
                messages.forEach { message ->
                    writeJsonLine(JSONObject()
                        .put("type", "message")
                        .put("message", message)
                        .put(TABLE_ROWS_KEY, tableRows("group_chat_messages" to JSONArray().put(message))), writer)
                }
            }
        }

        val worldbooks = rows("lorebooks")
        own("lorebooks")
        val worldbookEntries = rows("lorebook_entries")
        own("lorebook_entries")
        worldbooks.objects().forEach { book ->
            val id = book.optLong("id")
            val entries = worldbookEntries.objects().filter { it.optLong("lorebookId") == id }
                .sortedWith(compareBy<JSONObject> { it.optInt("order") }.thenBy { it.optLong("id") })
                .toJsonArray()
            writeText(File(root, "worldbooks/worldbook-" + id + ".json"),
                JSONObject()
                    .put("format", "rpclient-worldbook")
                    .put("version", VERSION)
                    .put("worldbook", book)
                    .put("entries", entries)
                    .put(TABLE_ROWS_KEY, tableRows(
                        "lorebooks" to JSONArray().put(book),
                        "lorebook_entries" to entries
                    )).toString(2))
        }

        val associations = rows("character_llm_provider_associations")
        own("character_llm_provider_associations")
        val providerRows = rows("llm_providers")
        own("llm_providers")
        writeText(File(root, "providers/provider-configurations.json"),
            JSONObject()
                .put("format", "rpclient-provider-configurations")
                .put("version", VERSION)
                .put("providers", providerRows)
                .put("characterAssociations", associations)
                .put(TABLE_ROWS_KEY, tableRows(
                    "llm_providers" to providerRows,
                    "character_llm_provider_associations" to associations
                )).toString(2))

        val scriptRows = rows("regex_scripts")
        own("regex_scripts")
        val globalScripts = scriptRows.objects().filter { it.isNull("characterId") }.toJsonArray()
        writeText(File(root, "regex/global-scripts.json"),
            JSONObject()
                .put("format", "rpclient-regex-scripts")
                .put("version", VERSION)
                .put("scripts", globalScripts)
                .put(TABLE_ROWS_KEY, tableRows("regex_scripts" to globalScripts))
                .toString(2))
        charactersById.keys.sorted().forEach { characterId ->
            val characterScripts = scriptRows.objects().filter {
                !it.isNull("characterId") && it.optLong("characterId") == characterId
            }.toJsonArray()
            writeText(File(root, "regex/character-scripts/character-" + characterId + ".json"),
                JSONObject()
                    .put("format", "rpclient-regex-scripts")
                    .put("version", VERSION)
                    .put("characterId", characterId)
                    .put("scripts", characterScripts)
                    .put(TABLE_ROWS_KEY, tableRows("regex_scripts" to characterScripts))
                    .toString(2))
        }
        val authorizations = rows("regex_character_authorizations")
        own("regex_character_authorizations")
        writeText(File(root, "regex/character-authorizations.json"),
            JSONObject()
                .put("format", "rpclient-regex-character-authorizations")
                .put("version", VERSION)
                .put("characterIds", JSONArray().apply {
                    authorizations.objects().forEach { put(it.optLong("characterId")) }
                })
                .put(TABLE_ROWS_KEY, tableRows("regex_character_authorizations" to authorizations))
                .toString(2))

        val tokenRows = rows("llm_token_usage_records")
        own("llm_token_usage_records")
        writeText(File(root, "app-data/token-usage.json"),
            JSONObject()
                .put("format", "rpclient-token-usage")
                .put("version", VERSION)
                .put("records", tokenRows)
                .put(TABLE_ROWS_KEY, tableRows("llm_token_usage_records" to tokenRows))
                .toString(2))

        val images = rows("message_images")
        own("message_images")
        writeText(File(root, "app-data/message-images.json"),
            JSONObject()
                .put("format", "rpclient-message-images")
                .put("version", VERSION)
                .put("images", images)
                .put(TABLE_ROWS_KEY, tableRows("message_images" to images))
                .toString(2))

        writeText(File(root, "app-data/files-index.json"),
            JSONObject()
                .put("format", "rpclient-files-index")
                .put("version", VERSION)
                .put("files", fileRows)
                .put(TABLE_ROWS_KEY, tableRows("files" to fileRows))
                .toString(2))

        File(root, "app-data/other-tables.jsonl").bufferedWriter(Charsets.UTF_8).use { writer ->
            (tables - ownedTables).sorted().forEach { table ->
                readRows(db, table).objects().forEach { row ->
                    writeJsonLine(JSONObject()
                        .put("table", table)
                        .put("row", row)
                        .put(TABLE_ROWS_KEY, tableRows(table to JSONArray().put(row))), writer)
                }
            }
        }
    }

    private fun exportAttachments(db: SupportSQLiteDatabase, tables: List<String>, root: File) {
        val fileRows = if ("files" in tables) readRows(db, "files").objects() else emptyList()
        val fileByUuid = fileRows.associateBy { it.optString("uuid") }
        val repository = context.getDir("repository", Context.MODE_PRIVATE)
        val otherFiles = File(root, "attachments/other-files")
        otherFiles.mkdirs()
        fileRows.forEach { row ->
            val hash = row.optString("hash")
            require(hash.matches(Regex("[0-9a-fA-F]{64}"))) {
                "Invalid stored attachment hash for " + row.optString("uuid")
            }
            val source = File(repository, hash)
            require(source.isFile) { "An attachment is missing from storage: " + hash }
            copyFile(source, File(otherFiles, hash))
        }
        repository.listFiles().orEmpty().filter {
            it.isFile && it.name.matches(Regex("[0-9a-fA-F]{64}"))
        }.forEach { copyFile(it, File(otherFiles, it.name)) }

        if ("character" in tables) {
            readRows(db, "character").objects().forEach { character ->
                val fileRecord = fileByUuid[character.optString("avatar")] ?: return@forEach
                val hash = fileRecord.optString("hash")
                val asset = File(repository, hash)
                if (asset.isFile) copyFile(asset,
                    File(root, "attachments/characters/character-" + character.optLong("id") + "-avatar-" + hash))
            }
        }
        if ("message_images" in tables) {
            readRows(db, "message_images").objects().forEach { image ->
                val fileRecord = fileByUuid[image.optString("imageUuid")] ?: return@forEach
                val hash = fileRecord.optString("hash")
                val asset = File(repository, hash)
                if (asset.isFile) {
                    val type = image.optString("messageType")
                    val messageId = image.optLong("messageId")
                    val position = image.optInt("position")
                    copyFile(asset, File(root,
                        "attachments/chat-images/" + type + "-" + messageId + "-" + position + "-" + hash))
                }
            }
        }
    }

    private fun exportRequestLogs(db: SupportSQLiteDatabase, root: File) {
        File(root, "app-data/request-logs.jsonl").bufferedWriter(Charsets.UTF_8).use { writer ->
            tableNames(db).forEach { table ->
                readRows(db, table).objects().forEach { row ->
                    val isRequestLog = table == "llm_request_logs"
                    writeJsonLine(JSONObject()
                        .put("type", if (isRequestLog) "request-log" else "internal-table")
                        .put("table", table)
                        .put("record", row)
                        .put(TABLE_ROWS_KEY, tableRows(table to JSONArray().put(row))), writer)
                }
            }
        }
    }

    private fun schemaJson(db: SupportSQLiteDatabase): JSONObject {
        val objects = JSONArray()
        db.query(
            "SELECT type, name, tbl_name, sql FROM sqlite_master " +
                "WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' " +
                "ORDER BY CASE type WHEN 'table' THEN 0 ELSE 1 END, name", emptyArray()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1)
                val tableName = cursor.getString(2)
                if (name != "android_metadata" && tableName != "android_metadata") {
                    objects.put(JSONObject()
                        .put("type", cursor.getString(0))
                        .put("name", name)
                        .put("table", tableName)
                        .put("sql", cursor.getString(3)))
                }
            }
        }
        return JSONObject().put("userVersion", db.version).put("objects", objects)
    }

    private fun tableNames(db: SupportSQLiteDatabase): List<String> {
        val result = mutableListOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' ORDER BY name", emptyArray()
        ).use { cursor -> while (cursor.moveToNext()) result += cursor.getString(0) }
        return result
    }

    private fun readRows(
        db: SupportSQLiteDatabase,
        table: String,
        whereColumn: String? = null,
        value: Any? = null
    ): JSONArray {
        val suffix = if (whereColumn == null) "" else " WHERE " + quote(whereColumn) + " = ?"
        val sql = "SELECT * FROM " + quote(table) + suffix
        val arguments: Array<Any?> = if (whereColumn == null) emptyArray() else arrayOf(value)
        return db.query(sql, arguments).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    val row = JSONObject()
                    for (index in 0 until cursor.columnCount) {
                        row.put(cursor.getColumnName(index), cursorValue(cursor, index))
                    }
                    put(row)
                }
            }
        }
    }

    private fun tableRows(vararg tables: Pair<String, JSONArray>): JSONObject {
        val result = JSONObject()
        tables.forEach { (name, rows) -> result.put(name, rows) }
        return result
    }

    private fun collectTableRows(
        root: File,
        includePath: String? = null,
        excludePath: String? = null
    ): Map<String, List<JSONObject>> {
        val result = linkedMapOf<String, MutableList<JSONObject>>()
        fun collect(document: JSONObject) {
            val tableRows = document.optJSONObject(TABLE_ROWS_KEY) ?: return
            val keys = tableRows.keys()
            while (keys.hasNext()) {
                val table = keys.next()
                val rows = tableRows.optJSONArray(table) ?: continue
                val target = result.getOrPut(table) { mutableListOf() }
                rows.objects().forEach { target.add(it) }
            }
        }
        root.walkTopDown().filter { it.isFile && (it.extension == "json" || it.extension == "jsonl") }
            .forEach { file ->
                val path = file.relativeTo(root).invariantSeparatorsPath
                if (includePath != null && path != includePath) return@forEach
                if (excludePath != null && path == excludePath) return@forEach
                if (path.startsWith("app-data/private-files/")) return@forEach
                if (file.extension == "json") {
                    runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull()?.let(::collect)
                } else {
                    file.forEachLine(Charsets.UTF_8) { line ->
                        if (line.isNotBlank()) runCatching { JSONObject(line) }.getOrNull()?.let(::collect)
                    }
                }
            }
        return result.mapValues { it.value.toList() }
    }

    private fun readLegacyRows(snapshot: File): Map<String, List<JSONObject>> {
        val schema = JSONObject(File(snapshot, "schema.json").readText(Charsets.UTF_8))
        val objects = schema.optJSONArray("objects") ?: JSONArray()
        val result = linkedMapOf<String, MutableList<JSONObject>>()
        File(snapshot, "tables").listFiles().orEmpty().filter { it.isFile && it.extension == "jsonl" }.forEach { file ->
            val table = tableNameForFile(objects, file.nameWithoutExtension)
            if (table == "android_metadata") return@forEach
            file.forEachLine(Charsets.UTF_8) { line ->
                if (line.isNotBlank()) result.getOrPut(table) { mutableListOf() }.add(JSONObject(line))
            }
        }
        return result.mapValues { it.value.toList() }
    }

    private fun reconstructDatabase(
        schema: JSONObject,
        tableRows: Map<String, List<JSONObject>>,
        destination: File
    ) {
        val objects = schema.optJSONArray("objects") ?: error("Database schema is invalid.")
        val allowedTables = mutableSetOf<String>()
        for (index in 0 until objects.length()) {
            val item = objects.getJSONObject(index)
            if (item.optString("type") == "table" && item.optString("name") != "android_metadata") {
                allowedTables.add(item.optString("name"))
            }
        }
        val unknownTable = tableRows.keys.firstOrNull { it !in allowedTables }
        require(unknownTable == null) { "Backup contains rows for an unknown table: " + unknownTable }
        destination.parentFile?.mkdirs()
        if (destination.exists()) destination.delete()
        val db = SQLiteDatabase.openOrCreateDatabase(destination, null)
        try {
            db.execSQL("PRAGMA foreign_keys=OFF")
            for (index in 0 until objects.length()) {
                val item = objects.getJSONObject(index)
                if (item.optString("type") != "table") continue
                val name = item.optString("name")
                // Android creates android_metadata when opening a database. It is intentionally
                // excluded from archives and must never be created a second time.
                if (name != "android_metadata" && !sqliteTableExists(db, name)) {
                    db.execSQL(item.getString("sql"))
                }
            }
            db.beginTransaction()
            try {
                tableRows.forEach { (table, rows) ->
                    rows.forEach { row ->
                        val columns = mutableListOf<String>()
                        val values = mutableListOf<Any?>()
                        val keys = row.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            columns.add(key)
                            values.add(jsonValue(row.get(key)))
                        }
                        if (columns.isNotEmpty()) {
                            val sql = "INSERT INTO " + quote(table) + " (" +
                                columns.joinToString(",") { quote(it) } + ") VALUES (" +
                                columns.joinToString(",") { "?" } + ")"
                            val statement = db.compileStatement(sql)
                            try {
                                values.forEachIndexed { index, value -> bind(statement, index + 1, value) }
                                statement.executeInsert()
                            } finally {
                                statement.close()
                            }
                        }
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            for (index in 0 until objects.length()) {
                val item = objects.getJSONObject(index)
                if (item.optString("type") != "table" && item.optString("table") != "android_metadata") {
                    db.execSQL(item.getString("sql"))
                }
            }
            db.version = schema.optInt("userVersion", 0)
            db.execSQL("PRAGMA foreign_keys=ON")
            db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                require(cursor.moveToFirst() && cursor.getString(0) == "ok") {
                    "Reconstructed database failed SQLite integrity validation."
                }
            }
            db.rawQuery("PRAGMA foreign_key_check", null).use { cursor ->
                require(!cursor.moveToFirst()) {
                    "Reconstructed database contains invalid foreign-key references."
                }
            }
        } finally {
            db.close()
        }
    }

    private fun replaceLiveData(
        rollback: File,
        stagedPrimary: File,
        stagedLogs: File,
        stagedPrefs: File,
        stagedRepository: File,
        stagedFiles: File
    ) {
        val livePrimary = context.getDatabasePath(PRIMARY_DB)
        val liveLogs = context.getDatabasePath(LOGS_DB)
        val livePrefs = File(context.dataDir, "shared_prefs")
        val liveRepository = context.getDir("repository", Context.MODE_PRIVATE)
        val liveFiles = context.filesDir
        rollback.mkdirs()
        val primaryExisted = livePrimary.isFile
        val logsExisted = liveLogs.isFile
        val prefsExisted = livePrefs.isDirectory
        val repositoryExisted = liveRepository.isDirectory
        val filesExisted = liveFiles.isDirectory
        copyFile(livePrimary, File(rollback, PRIMARY_DB))
        copyFile(liveLogs, File(rollback, LOGS_DB))
        copyDirectory(livePrefs, File(rollback, "shared-prefs"))
        copyDirectory(liveRepository, File(rollback, "repository"))
        copyDirectory(liveFiles, File(rollback, "private-files"))

        require(stagedPrefs.isDirectory) { "Backup preference files are missing." }
        require(stagedRepository.isDirectory) { "Backup attachments are missing." }
        require(stagedFiles.isDirectory) { "Backup app-private files are missing." }

        database.close()
        requestLogDatabase.close()
        try {
            replaceFile(stagedPrimary, livePrimary)
            replaceFile(stagedLogs, liveLogs)
            deleteSidecars(livePrimary)
            deleteSidecars(liveLogs)
            replaceDirectory(stagedPrefs, livePrefs)
            replaceDirectory(stagedRepository, liveRepository)
            replaceDirectory(stagedFiles, liveFiles)
        } catch (error: Throwable) {
            restoreFileFromRollback(File(rollback, PRIMARY_DB), livePrimary, primaryExisted)
            restoreFileFromRollback(File(rollback, LOGS_DB), liveLogs, logsExisted)
            restoreDirectoryFromRollback(File(rollback, "shared-prefs"), livePrefs, prefsExisted)
            restoreDirectoryFromRollback(File(rollback, "repository"), liveRepository, repositoryExisted)
            restoreDirectoryFromRollback(File(rollback, "private-files"), liveFiles, filesExisted)
            deleteSidecars(livePrimary)
            deleteSidecars(liveLogs)
            throw BackupRestoreException(
                "Portable restore failed. Previous data was recovered; restart the app to reopen its databases.",
                restartRequired = true,
                cause = error
            )
        }
    }

    private fun restoreFileFromRollback(source: File, target: File, existed: Boolean) {
        if (existed && source.isFile) copyFile(source, target)
        else if (!existed) target.delete()
    }

    private fun restoreDirectoryFromRollback(source: File, target: File, existed: Boolean) {
        if (target.exists()) target.deleteRecursively()
        if (existed && source.isDirectory) copyDirectory(source, target)
    }

    private fun validateChecksums(root: File, files: JSONObject) {
        val listed = mutableSetOf<String>()
        val keys = files.keys()
        while (keys.hasNext()) {
            val path = keys.next()
            listed.add(path)
            require(isSafePath(path)) { "Unsafe manifest path: " + path }
            val file = File(root, path).canonicalFile
            require(file.path.startsWith(root.canonicalPath + File.separator) && file.isFile) {
                "The archive is missing " + path
            }
            val info = files.getJSONObject(path)
            require(file.length() == info.getLong("size")) { "File size mismatch: " + path }
            require(sha256(file).equals(info.getString("sha256"), ignoreCase = true)) {
                "Checksum mismatch: " + path
            }
        }
        val actual = root.walkTopDown().filter { it.isFile && it.name != "manifest.json" }
            .map { it.relativeTo(root).invariantSeparatorsPath }.toSet()
        require(actual == listed) { "The archive contains files not listed in manifest.json." }
    }

    private fun extractArchive(source: Uri, stage: File) {
        val input = context.contentResolver.openInputStream(source)
            ?: error("Could not read the selected archive.")
        input.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                var entries = 0
                var totalBytes = 0L
                val seenPaths = mutableSetOf<String>()
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    require(entries <= MAX_ENTRIES) { "The archive contains too many files." }
                    require(isSafePath(entry.name)) { "Unsafe archive path: " + entry.name }
                    require(seenPaths.add(entry.name)) { "The archive contains a duplicate path: " + entry.name }
                    if (entry.isDirectory) {
                        val target = File(stage, entry.name).canonicalFile
                        require(target.path == stage.canonicalPath ||
                            target.path.startsWith(stage.canonicalPath + File.separator)) {
                            "Unsafe archive path: " + entry.name
                        }
                        target.mkdirs()
                    } else {
                        val target = File(stage, entry.name).canonicalFile
                        require(target.path.startsWith(stage.canonicalPath + File.separator)) {
                            "Unsafe archive path: " + entry.name
                        }
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                totalBytes += count
                                require(totalBytes <= MAX_BYTES) { "The archive exceeds the 2 GiB restore limit." }
                                output.write(buffer, 0, count)
                            }
                            output.fd.sync()
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
    }

    private fun isSafePath(name: String): Boolean {
        if (name.isBlank() || name.startsWith("/") || name.startsWith("\\") || name.contains('\\')) return false
        return name.removeSuffix("/").split('/').none { it.isBlank() || it == "." || it == ".." }
    }

    private fun tableNameForFile(objects: JSONArray, safeName: String): String {
        for (index in 0 until objects.length()) {
            val item = objects.getJSONObject(index)
            if (item.optString("type") == "table" && safeTableName(item.optString("name")) == safeName) {
                return item.optString("name")
            }
        }
        error("Archive references an unknown database table: " + safeName)
    }

    private fun sqliteTableExists(db: SQLiteDatabase, table: String): Boolean {
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use {
            return it.moveToFirst()
        }
    }

    private fun cursorValue(cursor: Cursor, index: Int): Any? = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
        Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
        Cursor.FIELD_TYPE_BLOB -> JSONObject().put(BLOB_KEY, Base64.encodeToString(cursor.getBlob(index), Base64.NO_WRAP))
        else -> JSONObject.NULL
    }

    private fun jsonValue(value: Any?): Any? {
        if (value == null || value === JSONObject.NULL) return null
        if (value is JSONObject && value.has(BLOB_KEY)) return Base64.decode(value.getString(BLOB_KEY), Base64.DEFAULT)
        return value
    }

    private fun bind(statement: SQLiteStatement, index: Int, value: Any?) {
        when (value) {
            null, JSONObject.NULL -> statement.bindNull(index)
            is ByteArray -> statement.bindBlob(index, value)
            is Boolean -> statement.bindLong(index, if (value) 1 else 0)
            is Int -> statement.bindLong(index, value.toLong())
            is Long -> statement.bindLong(index, value)
            is Double -> statement.bindDouble(index, value)
            is Float -> statement.bindDouble(index, value.toDouble())
            else -> statement.bindString(index, value.toString())
        }
    }

    private fun writeJsonLine(json: JSONObject, writer: java.io.Writer) {
        writer.write(json.toString())
        writer.write("\n")
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
    private fun List<JSONObject>.toJsonArray(): JSONArray = JSONArray().apply {
        this@toJsonArray.forEach { put(it) }
    }

    private fun countRecords(root: File): JSONObject {
        val counts = JSONObject()
        val folders = listOf(
            "characters" to "characters", "stories" to "stories",
            "chats" to "chats", "groupChats" to "group-chats",
            "worldbooks" to "worldbooks", "providers" to "providers"
        )
        folders.forEach { (key, path) ->
            counts.put(key, File(root, path).walkTopDown().count { it.isFile })
        }
        var requestLogs = 0
        File(root, "app-data/request-logs.jsonl").takeIf { it.isFile }?.forEachLine(Charsets.UTF_8) { line ->
            if (line.isNotBlank() && runCatching { JSONObject(line).optString("type") == "request-log" }.getOrDefault(false)) {
                requestLogs++
            }
        }
        counts.put("requestLogs", requestLogs)
        return counts
    }

    private fun safeTableName(name: String) = name.replace(Regex("[^A-Za-z0-9_.-]"), "_")
    private fun quote(value: String) = "\"" + value.replace("\"", "\"\"") + "\""

    private fun writeText(file: File, content: String) {
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun copyFile(source: File, destination: File) {
        if (!source.isFile) return
        destination.parentFile?.mkdirs()
        source.copyTo(destination, overwrite = true)
    }

    private fun copyDirectory(source: File, destination: File) {
        if (!source.isDirectory) return
        source.walkTopDown().forEach { file ->
            val target = File(destination, file.relativeTo(source).path)
            if (file.isDirectory) target.mkdirs()
            else {
                target.parentFile?.mkdirs()
                file.copyTo(target, overwrite = true)
            }
        }
    }

    private fun replaceFile(source: File, destination: File) {
        require(source.isFile) { "Restore archive is missing " + source.name }
        destination.parentFile?.mkdirs()
        if (destination.exists() && !destination.delete()) error("Could not replace " + destination.name)
        if (!source.renameTo(destination)) source.copyTo(destination, overwrite = true)
    }

    private fun replaceDirectory(source: File, destination: File) {
        require(source.isDirectory) { "Restore archive is missing " + source.path }
        if (destination.exists() && !destination.deleteRecursively()) error("Could not replace " + destination.name)
        destination.parentFile?.mkdirs()
        if (!source.renameTo(destination)) copyDirectory(source, destination)
    }
    /**
     * Remove SQLite sidecar files before/after replacing a database file. Keeping a stale WAL,
     * SHM, or rollback journal beside the restored database can corrupt or override its contents.
     */
    private fun deleteSidecars(databaseFile: File) {
        File(databaseFile.path + "-wal").delete()
        File(databaseFile.path + "-shm").delete()
        File(databaseFile.path + "-journal").delete()
    }
}
