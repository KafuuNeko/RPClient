package me.kafuuneko.rpclient.libs.backup

import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.File
import java.io.Writer
import me.kafuuneko.rpclient.libs.character.CharacterCardMapper
import me.kafuuneko.rpclient.libs.character.LorebookCodec
import me.kafuuneko.rpclient.libs.chat.ChatArchive
import me.kafuuneko.rpclient.libs.chat.ChatArchiveCodec
import me.kafuuneko.rpclient.libs.chat.ChatArchiveMessage
import me.kafuuneko.rpclient.libs.chat.ChatArchiveMessageRole
import me.kafuuneko.rpclient.libs.chat.ChatArchiveSummary
import me.kafuuneko.rpclient.libs.chat.ChatCharacterMatcher
import me.kafuuneko.rpclient.libs.regex.RegexScript
import me.kafuuneko.rpclient.libs.regex.RegexScriptCodec
import me.kafuuneko.rpclient.libs.regex.toDomain
import me.kafuuneko.rpclient.libs.regex.toEntity
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.entity.RegexScriptEntity
import me.kafuuneko.rpclient.libs.story.ArchivedChapter
import me.kafuuneko.rpclient.libs.story.StoryArchiveCodec
import org.json.JSONArray
import org.json.JSONObject

/**
 * 可移植归档 v3 的共用约定。
 *
 * 设计原则：归档里每个可编辑文件只有一份事实来源。
 * - 角色、世界书、正则、故事、聊天文件使用各自菜单导入所用的格式（角色卡 V2、酒馆世界书、
 *   正则数组、.rpstory.json、酒馆 JSONL），文件内容本身就是恢复时读取的内容。
 * - 这些格式无法表达的列（数据库主键、头像文件 ID 等）放在顶层 [SIDECAR] 键下；
 *   各菜单的导入解码器会忽略这个未知键，因此文件仍可单独导入。
 * - 没有对应菜单导入器的表使用每行一个 JSON 对象的 JSONL，行内容即数据库行。
 */
internal object BackupV3 {
    /** 各文档中保存格式之外的本地列，如数据库主键。 */
    const val SIDECAR = "rpclient_backup"

    /** 数据库布尔列以 0/1 保存，行与实体互转时需要宽松读取。 */
    fun rowGson(base: Gson): Gson = base.newBuilder()
        .registerTypeAdapter(Boolean::class.javaPrimitiveType, LenientBoolean)
        .registerTypeAdapter(Boolean::class.javaObjectType, LenientBoolean)
        .create()

    /** 写入一行 JSON 并换行。 */
    fun writeJsonLine(json: JSONObject, writer: Writer) {
        writer.write(json.toString())
        writer.write("\n")
    }

    /** 流式导出整张表，返回行数；空表不生成文件。 */
    fun dumpTable(db: SupportSQLiteDatabase, table: String, file: File): Int {
        file.parentFile?.mkdirs()
        var count = 0
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            BackupDatabaseCodec.forEachRow(db, table) { row ->
                writeJsonLine(row, writer)
                count += 1
            }
        }
        if (count == 0) file.delete()
        return count
    }

    private object LenientBoolean : TypeAdapter<Boolean?>() {
        override fun write(out: JsonWriter, value: Boolean?) {
            if (value == null) out.nullValue() else out.value(value)
        }

        override fun read(reader: JsonReader): Boolean? = when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            JsonToken.NUMBER -> reader.nextLong() != 0L
            JsonToken.STRING -> reader.nextString().let { it == "1" || it.equals("true", ignoreCase = true) }
            else -> reader.nextBoolean()
        }
    }
}

/** SQLite 的布尔列在 JSON 行里是 0/1，而 org.json 的 optBoolean 不识别数字。 */
internal fun JSONObject.flag(name: String): Boolean = when (val value = opt(name)) {
    is Boolean -> value
    is Number -> value.toLong() != 0L
    is String -> value == "1" || value.equals("true", ignoreCase = true)
    else -> false
}

/** 读取可空文本列；SQL NULL 返回 null。 */
internal fun JSONObject.textOrNull(name: String): String? = if (isNull(name)) null else optString(name)

/**
 * 把当前数据库内容写成 v3 目录结构。调用方负责在同一个读事务里使用本类。
 */
internal class BackupV3Writer(
    private val mDatabase: SupportSQLiteDatabase,
    private val mTables: List<String>,
    private val mRoot: File,
    gson: Gson,
    private val mReport: BackupReport
) {
    private val mRowGson = BackupV3.rowGson(gson)
    private val mCardMapper = CharacterCardMapper(gson)
    private val mLorebookCodec = LorebookCodec(gson)
    private val mRegexCodec = RegexScriptCodec(gson)
    private val mChatCodec = ChatArchiveCodec(gson)

    /** 已由专用文档接管的表；其余表走通用 JSONL。 */
    private val mOwned = mutableSetOf<String>()

    fun writeAll() {
        writeCharacters()
        writeWorldbooks()
        writeRegex()
        writeStories()
        writeChats()
        writeGroups()
        writeOtherTables()
    }

    private fun own(vararg names: String) {
        names.filter { it in mTables }.forEach { mOwned.add(it) }
    }

    private fun rows(table: String): List<JSONObject> {
        return if (table in mTables) BackupDatabaseCodec.readRows(mDatabase, table).objects() else emptyList()
    }

    private fun docFile(dir: String, title: String, id: Long, extension: String): File {
        val base = title
            .map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
            .joinToString("")
            .trim('_')
            .take(MAX_NAME_LENGTH)
            .ifBlank { "item" }
        return File(mRoot, "$dir/$base-$id$extension")
    }

    private fun writeText(file: File, text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text, Charsets.UTF_8)
    }

    private fun writeCharacters() {
        own("character")
        val characters = rows("character")
        characters.forEach { row ->
            val id = row.optLong("id")
            val character = mRowGson.fromJson(row.toString(), Character::class.java)
            // 绑定的世界书与角色正则单独成文件，避免在角色卡里重复保存一份。
            val card = JSONObject(mCardMapper.toV2Json(character))
            card.put(
                BackupV3.SIDECAR,
                JSONObject()
                    .put("id", id)
                    .put("avatar", row.opt("avatar"))
                    .put("characterLorebookId", row.opt("characterLorebookId"))
                    .put("backgroundFileUuid", row.opt("backgroundFileUuid"))
            )
            writeText(docFile("characters", row.optString("name"), id, ".json"), card.toString(2))
        }
        mReport.info("characters: ${characters.size}")
    }

    private fun writeWorldbooks() {
        own("lorebooks", "lorebook_entries")
        val allEntries = rows("lorebook_entries")
        val books = rows("lorebooks")
        books.forEach { bookRow ->
            val id = bookRow.optLong("id")
            val lorebook = mRowGson.fromJson(bookRow.toString(), Lorebook::class.java)
            val entryRows = allEntries
                .filter { it.optLong("lorebookId") == id }
                .sortedWith(compareBy<JSONObject> { it.optInt("order") }.thenBy { it.optLong("id") })
            val entries = entryRows.map { mRowGson.fromJson(it.toString(), LorebookEntry::class.java) }
            val doc = JSONObject(mLorebookCodec.toLorebookJson(lorebook, entries))
            // 酒馆世界书的条目键是序号；这里记录“序号 -> 条目主键”，新增或删除条目不影响其他条目。
            val entryIds = JSONObject()
            entryRows.forEachIndexed { index, row -> entryIds.put(index.toString(), row.optLong("id")) }
            doc.put(BackupV3.SIDECAR, JSONObject().put("id", id).put("entryIds", entryIds))
            writeText(docFile("worldbooks", bookRow.optString("name"), id, ".json"), doc.toString(2))
        }
        mReport.info("worldbooks: ${books.size}, entries: ${allEntries.size}")
    }

    private fun writeRegex() {
        own("regex_scripts")
        val ordered = rows("regex_scripts")
            .sortedWith(compareBy<JSONObject> { it.optInt("sortOrder") }.thenBy { it.optLong("rowId") })

        fun scripts(list: List<JSONObject>): List<RegexScript> = list.map {
            mRowGson.fromJson(it.toString(), RegexScriptEntity::class.java).toDomain()
        }

        val global = ordered.filter { it.isNull("characterId") }
        writeText(File(mRoot, "regex/global-scripts.json"), mRegexCodec.toJson(scripts(global), true))
        ordered.filter { !it.isNull("characterId") }
            .groupBy { it.optLong("characterId") }
            .forEach { (characterId, list) ->
                writeText(File(mRoot, "regex/character-$characterId.json"), mRegexCodec.toJson(scripts(list), true))
            }
        mReport.info("regex scripts: ${ordered.size}")
    }

    private fun writeStories() {
        own("stories", "story_volumes", "story_chapters", "story_characters", "story_lorebook_entries")
        val volumeRows = rows("story_volumes")
        val chapterRows = rows("story_chapters")
        val characterRows = rows("story_characters")
        val loreRows = rows("story_lorebook_entries")
        val orderBy = compareBy<JSONObject> { it.optInt("sortOrder") }.thenBy { it.optLong("id") }

        fun chapterDoc(chapter: JSONObject) = JSONObject()
            .put("title", chapter.optString("title"))
            .put("content", chapter.optString("content"))
            .put("continuationGuidance", chapter.optString("continuationGuidance"))

        fun chapterSide(chapter: JSONObject) = JSONObject()
            .put("id", chapter.optLong("id"))
            .put("contentRevision", chapter.opt("contentRevision"))
            .put("createTime", chapter.opt("createTime"))
            .put("latestTime", chapter.opt("latestTime"))

        val stories = rows("stories")
        stories.forEach { story ->
            val id = story.optLong("id")
            val volumes = volumeRows.filter { it.optLong("storyId") == id }.sortedWith(orderBy)
            val chapters = chapterRows.filter { it.optLong("storyId") == id }.sortedWith(orderBy)
            val ungrouped = chapters.filter { it.isNull("volumeId") }

            val volumeDocs = JSONArray()
            val volumeSides = JSONArray()
            val volumeChapterSides = JSONArray()
            volumes.forEach { volume ->
                val inVolume = chapters.filter { !it.isNull("volumeId") && it.optLong("volumeId") == volume.optLong("id") }
                val chapterDocs = JSONArray()
                val chapterSides = JSONArray()
                inVolume.forEach {
                    chapterDocs.put(chapterDoc(it))
                    chapterSides.put(chapterSide(it))
                }
                volumeDocs.put(JSONObject().put("title", volume.optString("title")).put("chapters", chapterDocs))
                volumeSides.put(JSONObject().put("id", volume.optLong("id")))
                volumeChapterSides.put(chapterSides)
            }
            val ungroupedDocs = JSONArray()
            val ungroupedSides = JSONArray()
            ungrouped.forEach {
                ungroupedDocs.put(chapterDoc(it))
                ungroupedSides.put(chapterSide(it))
            }
            val characterArray = JSONArray()
            characterRows.filter { it.optLong("storyId") == id }.forEach { characterArray.put(it) }
            val loreArray = JSONArray()
            loreRows.filter { it.optLong("storyId") == id }.forEach { loreArray.put(it) }

            val doc = JSONObject()
                .put("format", "rpclient_story")
                .put("version", 2)
                .put(
                    "story",
                    JSONObject()
                        .put("title", story.optString("title"))
                        .put("memory", story.optString("memory"))
                        .put("summary", story.optString("summary"))
                        .put("authorNote", story.optString("authorNote"))
                        .put("includeUserPersona", story.flag("includeUserPersona"))
                        .put("ungroupedChapters", ungroupedDocs)
                        .put("volumes", volumeDocs)
                )
                .put("characterHints", JSONArray())
                .put("lorebookHints", JSONArray())
                .put(
                    BackupV3.SIDECAR,
                    JSONObject()
                        .put(
                            "story",
                            JSONObject()
                                .put("id", id)
                                .put("worldInfoGenerationStep", story.opt("worldInfoGenerationStep"))
                                .put("revision", story.opt("revision"))
                                .put("createTime", story.opt("createTime"))
                                .put("latestTime", story.opt("latestTime"))
                        )
                        .put("volumes", volumeSides)
                        .put("ungroupedChapters", ungroupedSides)
                        .put("volumeChapters", volumeChapterSides)
                        .put("characters", characterArray)
                        .put("lorebookStates", loreArray)
                )
            writeText(docFile("stories", story.optString("title"), id, ".rpstory.json"), doc.toString(2))
        }
        mReport.info("stories: ${stories.size}")
    }

    private fun writeChats() {
        own("chat_sessions", "chat_messages")
        val characters = rows("character").associate {
            it.optLong("id") to mRowGson.fromJson(it.toString(), Character::class.java)
        }
        val sessions = rows("chat_sessions")
        var messageCount = 0
        sessions.forEach { session ->
            val id = session.optLong("id")
            val character = characters[session.optLong("characterId")]
            val all = BackupDatabaseCodec.readRows(mDatabase, "chat_messages", "sessionId", id).objects()
                .sortedWith(compareBy<JSONObject> { it.optLong("createTime") }.thenBy { it.optLong("id") })
            val normal = all.filter { it.optString("source") != SOURCE_SUMMARY }
            val summaries = all.filter { it.optString("source") == SOURCE_SUMMARY }
            val latest = summaries.lastOrNull()
            val coveredIndex = latest?.let { summary ->
                val coveredId = summary.optLong("coveredMessageId")
                if (coveredId > 0L) normal.indexOfFirst { it.optLong("id") == coveredId } else -1
            } ?: -1
            val archive = ChatArchive(
                title = session.optString("title"),
                createTime = session.optLong("createTime"),
                latestTime = session.optLong("latestTime"),
                userName = session.optString("userName"),
                userDescription = session.optString("userDescription"),
                userNote = session.optString("userNote"),
                creatorNotes = session.textOrNull("creatorNotes"),
                lorebookEntrySet = session.optString("lorebookEntrySet"),
                worldInfoStateJson = session.optString("worldInfoStateJson"),
                autoSummaryPaused = session.flag("autoSummaryPaused"),
                characterNameHint = character?.name.orEmpty(),
                characterFingerprint = character?.let { ChatCharacterMatcher.fingerprintOf(it) },
                messages = normal.map {
                    ChatArchiveMessage(
                        createTime = it.optLong("createTime"),
                        role = roleOf(it.optString("source")),
                        content = it.optString("content")
                    )
                },
                summary = latest?.let {
                    ChatArchiveSummary(
                        content = it.optString("content"),
                        createTime = it.optLong("createTime"),
                        coveredMessageIndex = coveredIndex
                    )
                }
            )
            val lines = mChatCodec.encode(archive).lines().filter { it.isNotBlank() }
            check(lines.size == normal.size + 1) {
                "chat session $id: expected ${normal.size + 1} lines but encoder produced ${lines.size}"
            }
            val output = StringBuilder()
            lines.forEachIndexed { index, line ->
                val json = JSONObject(line)
                val side = JSONObject()
                if (index == 0) {
                    side.put("session", JSONObject().put("id", id).put("characterId", session.opt("characterId")))
                    if (latest != null) side.put("summaryId", latest.optLong("id"))
                    val older = JSONArray()
                    summaries.dropLast(1).forEach { older.put(it) }
                    side.put("olderSummaries", older)
                } else {
                    side.put("id", normal[index - 1].optLong("id"))
                }
                json.put(BackupV3.SIDECAR, side)
                output.append(json.toString()).append('\n')
            }
            writeText(docFile("chats", session.optString("title"), id, ".jsonl"), output.toString())
            messageCount += all.size
        }
        mReport.info("chats: ${sessions.size}, messages: $messageCount")
    }

    private fun roleOf(source: String): ChatArchiveMessageRole = when (source) {
        "User" -> ChatArchiveMessageRole.User
        "System" -> ChatArchiveMessageRole.Narrator
        else -> ChatArchiveMessageRole.Character
    }

    private fun writeGroups() {
        own("group_chat_sessions", "group_chat_members", "group_chat_messages", "group_chat_summaries")
        val sessions = rows("group_chat_sessions")
        sessions.forEach { session ->
            val id = session.optLong("id")
            val file = docFile("group-chats", session.optString("title"), id, ".jsonl")
            file.parentFile?.mkdirs()
            file.bufferedWriter(Charsets.UTF_8).use { writer ->
                fun line(type: String, row: JSONObject) {
                    BackupV3.writeJsonLine(JSONObject().put("type", type).put("row", row), writer)
                }
                line("session", session)
                fun related(table: String): List<JSONObject> = if (table in mTables) {
                    BackupDatabaseCodec.readRows(mDatabase, table, "sessionId", id).objects()
                } else {
                    emptyList()
                }
                related("group_chat_members").forEach { line("member", it) }
                related("group_chat_summaries").sortedBy { it.optLong("id") }.forEach { line("summary", it) }
                related("group_chat_messages")
                    .sortedWith(compareBy<JSONObject> { it.optLong("createTime") }.thenBy { it.optLong("id") })
                    .forEach { line("message", it) }
            }
        }
        mReport.info("group chats: ${sessions.size}")
    }

    private fun writeOtherTables() {
        (mTables - mOwned).sorted().forEach { table ->
            val count = BackupV3.dumpTable(mDatabase, table, File(mRoot, "tables/$table.jsonl"))
            if (count > 0) mReport.info("table $table: $count rows")
        }
    }

    private companion object {
        const val MAX_NAME_LENGTH = 60
        const val SOURCE_SUMMARY = "Summary"
    }
}

/**
 * 读取 v3 目录并生成数据库行。新增文件没有 [BackupV3.SIDECAR] 时自动分配主键；
 * 删除文件后指向它的子记录由外键检查移除。
 */
internal class BackupV3Reader(
    private val mRoot: File,
    private val mGson: Gson,
    private val mReport: BackupReport
) {
    private val mRowGson = BackupV3.rowGson(mGson)
    private val mCardMapper = CharacterCardMapper(mGson)
    private val mLorebookCodec = LorebookCodec(mGson)
    private val mRegexCodec = RegexScriptCodec(mGson)
    private val mStoryCodec = StoryArchiveCodec(mGson)
    private val mChatCodec = ChatArchiveCodec(mGson)

    private val mTables = linkedMapOf<String, MutableList<JSONObject>>()
    private val mCharacters = mutableListOf<JSONObject>()
    private val mLinks = mutableListOf<Link>()
    private val mNow = System.currentTimeMillis()

    /** 子行的外键字段在主键分配完成后再回填。 */
    private class Link(val child: JSONObject, val field: String, val parent: JSONObject)

    fun readPrimary(): Map<String, List<JSONObject>> {
        readRowFiles("tables", mTables)
        readCharacters()
        readWorldbooks()
        readRegex()
        readStories()
        readChats()
        readGroups()
        resolveIds()
        mLinks.forEach { it.child.put(it.field, it.parent.getLong("id")) }
        mTables.forEach { (table, list) -> mReport.info("rows to restore: $table = ${list.size}") }
        return mTables
    }

    fun readLogs(): Map<String, List<JSONObject>> {
        val logs = linkedMapOf<String, MutableList<JSONObject>>()
        readRowFiles("request-logs", logs)
        logs.forEach { (table, list) -> mReport.info("rows to restore: $table = ${list.size}") }
        return logs
    }

    private fun rel(file: File): String = file.relativeTo(mRoot).invariantSeparatorsPath

    private inline fun <T> at(file: File, line: Int? = null, block: () -> T): T {
        try {
            return block()
        } catch (error: Exception) {
            val where = rel(file) + if (line != null) " (line $line)" else ""
            throw IllegalArgumentException("Cannot read $where: ${error.message}", error)
        }
    }

    private fun files(dir: String, suffix: String): List<File> {
        val base = File(mRoot, dir)
        if (!base.isDirectory) return emptyList()
        return base.walkTopDown().filter { it.isFile && it.name.endsWith(suffix) }.sortedBy { it.path }.toList()
    }

    private fun add(table: String, row: JSONObject) {
        mTables.getOrPut(table) { mutableListOf() }.add(row)
    }

    private fun link(child: JSONObject, field: String, parent: JSONObject) {
        mLinks.add(Link(child, field, parent))
    }

    private fun entityRow(entity: Any): JSONObject = JSONObject(mRowGson.toJson(entity))

    private fun applyId(side: JSONObject?, row: JSONObject, column: String = "id") {
        val id = side?.optLong("id", 0L) ?: 0L
        if (id > 0L) row.put(column, id) else row.remove(column)
    }

    private fun copyFrom(side: JSONObject?, row: JSONObject, vararg keys: String) {
        if (side == null) return
        keys.forEach { key -> if (side.has(key)) row.put(key, side.get(key)) }
    }

    private fun readRowFiles(dir: String, target: MutableMap<String, MutableList<JSONObject>>) {
        files(dir, ".jsonl").forEach { file ->
            val table = file.nameWithoutExtension
            var lineNumber = 0
            file.forEachLine(Charsets.UTF_8) { line ->
                lineNumber += 1
                if (line.isNotBlank()) {
                    val row = at(file, lineNumber) { JSONObject(line) }
                    target.getOrPut(table) { mutableListOf() }.add(row)
                }
            }
        }
    }

    private fun readCharacters() {
        files("characters", ".json").forEach { file ->
            at(file) {
                val text = file.readText(Charsets.UTF_8)
                val side = JSONObject(text).optJSONObject(BackupV3.SIDECAR)
                val parsed = mCardMapper.parseCharacter(text)
                if (parsed.embeddedLorebook != null) {
                    mReport.warn("${rel(file)}: character_book is ignored; keep lorebooks under worldbooks/")
                }
                if (parsed.regexScripts.isNotEmpty()) {
                    mReport.warn("${rel(file)}: regex scripts inside the card are ignored; keep them under regex/")
                }
                val row = entityRow(parsed.character)
                copyFrom(side, row, "avatar", "characterLorebookId", "backgroundFileUuid")
                applyId(side, row)
                add("character", row)
                mCharacters.add(row)
            }
        }
    }

    private fun readWorldbooks() {
        files("worldbooks", ".json").forEach { file ->
            at(file) {
                val text = file.readText(Charsets.UTF_8)
                val doc = JSONObject(text)
                val side = doc.optJSONObject(BackupV3.SIDECAR)
                val parsed = mLorebookCodec.parseLorebook(text)
                val bookRow = entityRow(parsed.lorebook)
                applyId(side, bookRow)
                add("lorebooks", bookRow)
                // 解码器按文件顺序产出条目，这里取同一顺序的键来匹配条目主键。
                val keys = mutableListOf<String>()
                doc.optJSONObject("entries")?.let { entries ->
                    entries.keys().forEach { key -> if (entries.opt(key) is JSONObject) keys.add(key) }
                }
                check(keys.size == parsed.entries.size) { "entry count mismatch while reading entries" }
                val ids = side?.optJSONObject("entryIds")
                parsed.entries.forEachIndexed { index, entry ->
                    val row = entityRow(entry)
                    val entryId = ids?.optLong(keys[index], 0L) ?: 0L
                    if (entryId > 0L) row.put("id", entryId) else row.remove("id")
                    link(row, "lorebookId", bookRow)
                    add("lorebook_entries", row)
                }
            }
        }
    }

    private fun readRegex() {
        files("regex", ".json").forEach { file ->
            val characterId: Long? = if (file.name == GLOBAL_REGEX_FILE) {
                null
            } else {
                CHARACTER_REGEX_NAME.matchEntire(file.name)?.groupValues?.get(1)?.toLongOrNull()
            }
            if (characterId == null && file.name != GLOBAL_REGEX_FILE) {
                mReport.warn("${rel(file)}: ignored; regex files must be named $GLOBAL_REGEX_FILE or character-<id>.json")
                return@forEach
            }
            at(file) {
                val scripts = mRegexCodec.parseList(file.readText(Charsets.UTF_8))
                scripts.forEachIndexed { index, script ->
                    val row = entityRow(script.toEntity(characterId, index, mGson))
                    row.remove("rowId")
                    add("regex_scripts", row)
                }
            }
        }
    }

    private fun readStories() {
        files("stories", ".json").forEach { file ->
            at(file) {
                val text = file.readText(Charsets.UTF_8)
                val side = JSONObject(text).optJSONObject(BackupV3.SIDECAR)
                val archive = mStoryCodec.decode(text)
                val story = archive.story
                val storySide = side?.optJSONObject("story")
                val storyRow = JSONObject()
                    .put("title", story.title)
                    .put("memory", story.memory)
                    .put("summary", story.summary)
                    .put("authorNote", story.authorNote)
                    .put("includeUserPersona", story.includeUserPersona)
                    .put("worldInfoGenerationStep", 0)
                    .put("revision", 0L)
                    .put("createTime", mNow)
                    .put("latestTime", mNow)
                copyFrom(storySide, storyRow, "worldInfoGenerationStep", "revision", "createTime", "latestTime")
                applyId(storySide, storyRow)
                add("stories", storyRow)

                fun addChapter(
                    chapter: ArchivedChapter,
                    sortOrder: Int,
                    chapterSide: JSONObject?,
                    volumeRow: JSONObject?
                ) {
                    val row = JSONObject()
                        .put("title", chapter.title)
                        .put("content", chapter.content)
                        .put("continuationGuidance", chapter.continuationGuidance)
                        .put("sortOrder", sortOrder)
                        .put("contentRevision", 0L)
                        .put("createTime", mNow)
                        .put("latestTime", mNow)
                    copyFrom(chapterSide, row, "contentRevision", "createTime", "latestTime")
                    applyId(chapterSide, row)
                    link(row, "storyId", storyRow)
                    if (volumeRow != null) link(row, "volumeId", volumeRow)
                    add("story_chapters", row)
                }

                val volumeSides = side?.optJSONArray("volumes")
                val volumeChapterSides = side?.optJSONArray("volumeChapters")
                story.volumes.forEachIndexed { volumeIndex, volume ->
                    val volumeRow = JSONObject().put("title", volume.title).put("sortOrder", volumeIndex)
                    applyId(volumeSides?.optJSONObject(volumeIndex), volumeRow)
                    link(volumeRow, "storyId", storyRow)
                    add("story_volumes", volumeRow)
                    val chapterSides = volumeChapterSides?.optJSONArray(volumeIndex)
                    volume.chapters.forEachIndexed { index, chapter ->
                        addChapter(chapter, index, chapterSides?.optJSONObject(index), volumeRow)
                    }
                }
                val ungroupedSides = side?.optJSONArray("ungroupedChapters")
                story.ungroupedChapters.forEachIndexed { index, chapter ->
                    addChapter(chapter, index, ungroupedSides?.optJSONObject(index), null)
                }
                // 角色与世界书状态没有菜单级导入格式，按数据库行原样保存在附加键里。
                side?.optJSONArray("characters")?.objects()?.forEach { add("story_characters", it) }
                side?.optJSONArray("lorebookStates")?.objects()?.forEach { add("story_lorebook_entries", it) }
            }
        }
    }

    private fun JsonObject?.longValue(name: String): Long? {
        return this?.get(name)?.takeIf { it.isJsonPrimitive }?.asLong
    }

    private fun readJsonObjects(text: String): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        val reader = JsonReader(text.reader()).apply { strictness = Strictness.LENIENT }
        while (reader.peek() != JsonToken.END_DOCUMENT) {
            result.add(JsonParser.parseReader(reader).asJsonObject)
        }
        return result
    }

    /** 与 ChatArchiveCodec 解码消息的取舍一致，用来让附加键与解码后的消息一一对应。 */
    private fun isDecodedMessage(json: JsonObject): Boolean {
        val content = json.get("mes")
        if (content == null || !content.isJsonPrimitive || !content.asJsonPrimitive.isString) return false
        val extraType = json.getAsJsonObject("extra")?.get("type")
            ?.takeIf { it.isJsonPrimitive }?.asString
        if (extraType.equals("narrator", ignoreCase = true)) return true
        fun flag(name: String): Boolean {
            val value = json.get(name)
            return value != null && value.isJsonPrimitive && value.asJsonPrimitive.isBoolean && value.asBoolean
        }
        if (flag("is_user")) return true
        return !flag("is_system")
    }

    private fun readChats() {
        files("chats", ".jsonl").forEach { file ->
            at(file) {
                val text = file.readText(Charsets.UTF_8)
                val archive: ChatArchive = mChatCodec.decode(text, file.nameWithoutExtension)
                val objects = readJsonObjects(text)
                val headerSide = objects.firstOrNull()?.getAsJsonObject(BackupV3.SIDECAR)
                val messageSides = objects.drop(1).filter { isDecodedMessage(it) }
                    .map { it.getAsJsonObject(BackupV3.SIDECAR) }
                if (messageSides.size != archive.messages.size) {
                    mReport.warn("${rel(file)}: message count differs from decoded messages; message ids are reassigned")
                }
                val sessionSide = headerSide?.getAsJsonObject("session")
                val sessionRow = JSONObject()
                    .put("createTime", archive.createTime)
                    .put("latestTime", archive.latestTime)
                    .put("lorebookEntrySet", archive.lorebookEntrySet)
                    .put("title", archive.title)
                    .put("userNote", archive.userNote)
                    .put("userName", archive.userName)
                    .put("userDescription", archive.userDescription)
                    .put("worldInfoStateJson", archive.worldInfoStateJson)
                    .put("autoSummaryPaused", archive.autoSummaryPaused)
                archive.creatorNotes?.let { sessionRow.put("creatorNotes", it) }
                sessionSide.longValue("id")?.takeIf { it > 0L }?.let { sessionRow.put("id", it) }
                val characterId = sessionSide.longValue("characterId")
                if (characterId != null && characterId > 0L) {
                    sessionRow.put("characterId", characterId)
                } else {
                    val match = mCharacters.firstOrNull { it.optString("name") == archive.characterNameHint }
                        ?: throw IllegalArgumentException(
                            "No restored character is named '${archive.characterNameHint}'. " +
                                "Add the character file or set rpclient_backup.session.characterId in the header line."
                        )
                    link(sessionRow, "characterId", match)
                }
                add("chat_sessions", sessionRow)

                val messageRows = archive.messages.mapIndexed { index, message ->
                    val row = JSONObject()
                        .put("createTime", message.createTime)
                        .put("source", sourceOf(message.role))
                        .put("content", message.content)
                    messageSides.getOrNull(index).longValue("id")?.takeIf { it > 0L }?.let { row.put("id", it) }
                    link(row, "sessionId", sessionRow)
                    add("chat_messages", row)
                    row
                }
                archive.summary?.let { summary ->
                    val row = JSONObject()
                        .put("createTime", summary.createTime)
                        .put("source", "Summary")
                        .put("content", summary.content)
                    headerSide.longValue("summaryId")?.takeIf { it > 0L }?.let { row.put("id", it) }
                    val covered = messageRows.getOrNull(summary.coveredMessageIndex)
                    if (covered != null) link(row, "coveredMessageId", covered) else row.put("coveredMessageId", 0L)
                    link(row, "sessionId", sessionRow)
                    add("chat_messages", row)
                }
                headerSide?.getAsJsonArray("olderSummaries")?.forEach { element ->
                    val row = JSONObject(element.toString())
                    link(row, "sessionId", sessionRow)
                    add("chat_messages", row)
                }
            }
        }
    }

    private fun sourceOf(role: ChatArchiveMessageRole): String = when (role) {
        ChatArchiveMessageRole.User -> "User"
        ChatArchiveMessageRole.Character -> "Char"
        ChatArchiveMessageRole.Narrator -> "System"
    }

    private fun readGroups() {
        files("group-chats", ".jsonl").forEach { file ->
            var session: JSONObject? = null
            var lineNumber = 0
            file.forEachLine(Charsets.UTF_8) { line ->
                lineNumber += 1
                if (line.isNotBlank()) {
                    at(file, lineNumber) {
                        val json = JSONObject(line)
                        val row = json.getJSONObject("row")
                        val type = json.getString("type")
                        if (type == "session") {
                            session = row
                            add("group_chat_sessions", row)
                        } else {
                            val parent = session ?: throw IllegalArgumentException("the session line must come first")
                            val table = when (type) {
                                "member" -> "group_chat_members"
                                "summary" -> "group_chat_summaries"
                                "message" -> "group_chat_messages"
                                else -> throw IllegalArgumentException("unknown line type '$type'")
                            }
                            link(row, "sessionId", parent)
                            add(table, row)
                        }
                    }
                }
            }
        }
    }

    /** 重复主键视为复制出的新条目并重新分配；缺少主键的新增行从当前最大值之后顺序分配。 */
    private fun resolveIds() {
        listOf(
            "character" to "id",
            "lorebooks" to "id",
            "lorebook_entries" to "id",
            "regex_scripts" to "rowId",
            "stories" to "id",
            "story_volumes" to "id",
            "story_chapters" to "id",
            "chat_sessions" to "id",
            "chat_messages" to "id",
            "group_chat_sessions" to "id",
            "group_chat_messages" to "id",
            "group_chat_summaries" to "id"
        ).forEach { (table, column) ->
            val rows = mTables[table] ?: return@forEach
            val seen = hashSetOf<Long>()
            rows.forEach { row ->
                if (row.has(column) && !seen.add(row.getLong(column))) {
                    mReport.warn("$table: duplicate $column ${row.getLong(column)} was reassigned")
                    row.remove(column)
                }
            }
            var next = (rows.maxOfOrNull { it.optLong(column, 0L) } ?: 0L) + 1L
            rows.filter { !it.has(column) }.forEach {
                it.put(column, next)
                next += 1L
            }
        }
    }

    private companion object {
        const val GLOBAL_REGEX_FILE = "global-scripts.json"
        val CHARACTER_REGEX_NAME = Regex("character-(\\d+)\\.json")
    }
}
