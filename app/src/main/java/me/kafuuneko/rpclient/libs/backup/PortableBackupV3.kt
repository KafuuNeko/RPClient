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
import java.io.StringWriter
import java.io.Writer
import java.security.MessageDigest
import me.kafuuneko.rpclient.libs.character.CharacterBookImport
import me.kafuuneko.rpclient.libs.character.CharacterCardMapper
import me.kafuuneko.rpclient.libs.character.LorebookCodec
import me.kafuuneko.rpclient.libs.chat.ChatArchive
import me.kafuuneko.rpclient.libs.chat.ChatArchiveCodec
import me.kafuuneko.rpclient.libs.chat.ChatArchiveImage
import me.kafuuneko.rpclient.libs.chat.ChatArchiveImageCodec
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
import me.kafuuneko.rpclient.libs.room.entity.StoryCharacter
import me.kafuuneko.rpclient.libs.story.ArchivedChapter
import me.kafuuneko.rpclient.libs.story.ArchivedStory
import me.kafuuneko.rpclient.libs.story.ArchivedVolume
import me.kafuuneko.rpclient.libs.story.StoryArchive
import me.kafuuneko.rpclient.libs.story.StoryArchiveCodec
import me.kafuuneko.rpclient.libs.story.StoryCharacterHint
import me.kafuuneko.rpclient.libs.story.StoryLorebookHint
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

    /**
     * 为行与实体互转配置宽松布尔读取，兼容数据库中的 0/1。
     * @param base 应用已有的 Gson 配置。
     * @return 仅扩展布尔读取规则的新实例。
     */
    fun rowGson(base: Gson): Gson = base.newBuilder()
        .registerTypeAdapter(Boolean::class.javaPrimitiveType, LenientBoolean)
        .registerTypeAdapter(Boolean::class.javaObjectType, LenientBoolean)
        .create()

    /**
     * 写入一行 JSON 并换行。
     * @param json 当前行的数据对象。
     * @param writer 由调用方管理生命周期的输出流。
     */
    fun writeJsonLine(json: JSONObject, writer: Writer) {
        writer.write(json.toString())
        writer.write("\n")
    }

    /**
     * 流式导出整张表，空表不保留输出文件。
     * @param db 当前快照的数据库连接。
     * @param table 需要导出的表名。
     * @param file 当前表的 JSONL 目标文件。
     * @return 实际写出的数据行数。
     */
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
        /** 输出标准 JSON 布尔值，空值继续保留为空。 */
        override fun write(out: JsonWriter, value: Boolean?) {
            if (value == null) out.nullValue() else out.value(value)
        }

        /** 兼容 SQLite 原始行使用的 0/1 与文本布尔表示。 */
        override fun read(reader: JsonReader): Boolean? = when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }

            JsonToken.NUMBER -> reader.nextLong() != 0L
            JsonToken.STRING -> reader.nextString()
                .let { it == "1" || it.equals("true", ignoreCase = true) }

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
internal fun JSONObject.textOrNull(name: String): String? =
    if (isNull(name)) null else optString(name)

/**
 * 将数据库快照转换为 v3 功能文档。
 * - 复用各功能编解码器，技术关联信息写入 sidecar。
 * - 由调用方持有同一读事务，文档写入器不负责提交或关闭数据库。
 */
internal class BackupV3Writer(
    private val mDatabase: SupportSQLiteDatabase,
    private val mTables: List<String>,
    private val mRoot: File,
    private val mAttachmentDirectory: File,
    gson: Gson,
    private val mReport: BackupReport
) {
    private val mRowGson = BackupV3.rowGson(gson)
    private val mCardMapper = CharacterCardMapper(gson)
    private val mLorebookCodec = LorebookCodec(gson)
    private val mRegexCodec = RegexScriptCodec(gson)
    private val mChatCodec = ChatArchiveCodec(gson)
    private val mStoryCodec = StoryArchiveCodec(gson)

    /** 已由专用文档接管的表；其余表走通用 JSONL。 */
    private val mOwned = mutableSetOf<String>()

    /** 按功能写出快照，再输出尚未被功能文档接管的表。 */
    suspend fun writeAll() {
        stage("characters") { writeCharacters() }
        stage("worldbooks") { writeWorldbooks() }
        stage("regex scripts") { writeRegex() }
        stage("stories") { writeStories() }
        stage("chats") { writeChats() }
        stage("group chats") { writeGroups() }
        stage("other tables") { writeOtherTables() }
    }

    /** 记录阶段开始，失败时在异常信息里带上阶段名，页面详情能直接看出卡在哪一步。 */
    private suspend fun stage(name: String, block: suspend () -> Unit) {
        mReport.info("Exporting $name")
        try {
            block()
        } catch (error: Exception) {
            throw IllegalStateException(
                "Export failed while writing $name: ${error.message}",
                error
            )
        }
    }

    /** 失败时指出具体条目，便于定位到某一个角色或聊天。 */
    private inline fun <T> item(what: String, block: () -> T): T {
        try {
            return block()
        } catch (error: Exception) {
            throw IllegalStateException("$what: ${error.message}", error)
        }
    }

    private val mCheckedTypes = mutableSetOf<Class<*>>()

    /**
     * 按列名把数据库行映射成实体。Gson 依赖字段名，混淆后字段名与列名不一致会得到全为 null 的对象，
     * 后续才在无关位置崩溃；因此每种实体首次映射时校验字段名，立即给出明确原因。
     */
    private fun <T : Any> entity(row: JSONObject, type: Class<T>): T {
        val value = mRowGson.fromJson(row.toString(), type)
        if (mCheckedTypes.add(type)) {
            val keys = JSONObject(mRowGson.toJson(value)).keys().asSequence().toSet()
            val missing = row.keys().asSequence().filter { !row.isNull(it) && it !in keys }.toList()
            check(missing.isEmpty()) {
                "${type.simpleName} fields do not match database columns $missing; " +
                    "Room entity field names must be kept by R8 (see proguard-rules.pro)"
            }
        }
        return value
    }

    /** 标记已由功能文档接管的表，避免通用 JSONL 再次导出。 */
    private fun own(vararg names: String) {
        names.filter { it in mTables }.forEach { mOwned.add(it) }
    }

    /** 从当前快照读取指定表；旧结构中不存在的表按空集合处理。 */
    private fun rows(table: String): List<JSONObject> {
        return if (table in mTables) BackupDatabaseCodec.readRows(mDatabase, table)
            .objects() else emptyList()
    }

    /** 清理标题并附加主键，生成目录内稳定且不会因同名覆盖的文档路径。 */
    private fun docFile(dir: String, title: String, id: Long, extension: String): File {
        val base = title
            .map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
            .joinToString("")
            .trim('_')
            .take(MAX_NAME_LENGTH)
            .ifBlank { "item" }
        return File(mRoot, "$dir/$base-$id$extension")
    }

    /** 使用 UTF-8 写入功能文档，并补齐父目录。 */
    private fun writeText(file: File, text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text, Charsets.UTF_8)
    }

    /**
     * 与 CharacterCardRepository.exportJson 相同：角色卡内嵌绑定的世界书和角色作用域正则。
     * 恢复时 worldbooks/ 下的同一本世界书优先于卡内嵌入的副本。
     */
    private fun writeCharacters() {
        own("character")
        val books = rows("lorebooks").associateBy { it.optLong("id") }
        val entriesByBook = rows("lorebook_entries").groupBy { it.optLong("lorebookId") }
        val scriptsByCharacter = rows("regex_scripts")
            .filter { !it.isNull("characterId") }
            .sortedWith(compareBy<JSONObject> { it.optInt("sortOrder") }.thenBy { it.optLong("rowId") })
            .groupBy { it.optLong("characterId") }
        val characters = rows("character")
        characters.forEach { row ->
            val id = row.optLong("id")
            item("character $id '${row.optString("name")}'") {
                val character = entity(row, Character::class.java)
                val bookRow = books[row.optLong("characterLorebookId")]
                val lorebook = bookRow?.let { entity(it, Lorebook::class.java) }
                val entries = bookRow?.let { entriesByBook[it.optLong("id")] }.orEmpty()
                    .sortedWith(compareBy<JSONObject> { it.optInt("order") }.thenBy { it.optLong("id") })
                    .map { entity(it, LorebookEntry::class.java) }
                val scripts = scriptsByCharacter[id].orEmpty()
                    .map { entity(it, RegexScriptEntity::class.java).toDomain() }
                val card = JSONObject(mCardMapper.toV2Json(character, lorebook, entries, scripts))
                card.put(
                    BackupV3.SIDECAR,
                    JSONObject()
                        .put("id", id)
                        .put("avatar", row.opt("avatar"))
                        .put("characterLorebookId", row.opt("characterLorebookId"))
                        .put("backgroundFileUuid", row.opt("backgroundFileUuid"))
                )
                writeText(
                    docFile("characters", row.optString("name"), id, ".json"),
                    card.toString(2)
                )
            }
        }
        mReport.info("characters: ${characters.size}")
    }

    /** 写出世界书及条目来源 ID，供恢复阶段回填关联。 */
    private fun writeWorldbooks() {
        own("lorebooks", "lorebook_entries")
        // 先固定当前快照的条目集合，后续按世界书分组写出。
        val allEntries = rows("lorebook_entries")
        val books = rows("lorebooks")
        books.forEach { bookRow ->
            val id = bookRow.optLong("id")
            val lorebook = entity(bookRow, Lorebook::class.java)
            val entryRows = allEntries
                .filter { it.optLong("lorebookId") == id }
                .sortedWith(compareBy<JSONObject> { it.optInt("order") }.thenBy { it.optLong("id") })
            val entries = entryRows.map { entity(it, LorebookEntry::class.java) }
            val doc = JSONObject(mLorebookCodec.toLorebookJson(lorebook, entries))
            // 酒馆世界书的条目键是序号；这里记录“序号 -> 条目主键”，新增或删除条目不影响其他条目。
            val entryIds = JSONObject()
            entryRows.forEachIndexed { index, row ->
                entryIds.put(
                    index.toString(),
                    row.optLong("id")
                )
            }
            doc.put(BackupV3.SIDECAR, JSONObject().put("id", id).put("entryIds", entryIds))
            writeText(
                docFile("worldbooks", bookRow.optString("name"), id, ".json"),
                doc.toString(2)
            )
        }
        mReport.info("worldbooks: ${books.size}, entries: ${allEntries.size}")
    }

    /** 只导出全局正则；角色作用域正则已内嵌在各自的角色卡里，与单个角色导出一致。 */
    private fun writeRegex() {
        own("regex_scripts")
        val ordered = rows("regex_scripts")
            .sortedWith(compareBy<JSONObject> { it.optInt("sortOrder") }.thenBy { it.optLong("rowId") })
        val global = ordered.filter { it.isNull("characterId") }
        val scripts: List<RegexScript> =
            global.map { entity(it, RegexScriptEntity::class.java).toDomain() }
        writeText(File(mRoot, "regex/global-scripts.json"), mRegexCodec.toJson(scripts, true))
        mReport.info("global regex scripts: ${global.size}, character regex scripts (inside cards): ${ordered.size - global.size}")
    }

    /** 写出故事、分卷和章节正文，将本地修订信息及关系存入 sidecar。 */
    private fun writeStories() {
        own(
            "stories",
            "story_volumes",
            "story_chapters",
            "story_characters",
            "story_lorebook_entries"
        )
        // 同一读事务内准备各级关系，正文顺序按原有排序规则确定。
        val volumeRows = rows("story_volumes")
        val chapterRows = rows("story_chapters")
        val characterRows = rows("story_characters")
        val loreRows = rows("story_lorebook_entries")
        val characters = rows("character").associate { row ->
            row.optLong("id") to entity(row, Character::class.java)
        }
        val lorebookEntries = rows("lorebook_entries").associateBy { it.optLong("id") }
        val lorebooks = rows("lorebooks").associateBy { it.optLong("id") }
        val orderBy = compareBy<JSONObject> { it.optInt("sortOrder") }.thenBy { it.optLong("id") }
        val stories = rows("stories")

        stories.forEach { story ->
            val id = story.optLong("id")
            val volumes = volumeRows.filter { it.optLong("storyId") == id }.sortedWith(orderBy)
            val chapters = chapterRows.filter { it.optLong("storyId") == id }.sortedWith(orderBy)
            val ungrouped = chapters.filter { it.isNull("volumeId") }
            val archivedVolumes = volumes.map { volume ->
                val inVolume = chapters.filter {
                    !it.isNull("volumeId") && it.optLong("volumeId") == volume.optLong("id")
                }
                ArchivedVolume(
                    title = volume.optString("title"),
                    chapters = inVolume.map {
                        ArchivedChapter(
                            title = it.optString("title"),
                            content = it.optString("content"),
                            continuationGuidance = it.optString("continuationGuidance")
                        )
                    }
                )
            }
            val characterHints = characterRows
                .filter { it.optLong("storyId") == id }
                .mapNotNull { relation ->
                    val character =
                        characters[relation.optLong("characterId")] ?: return@mapNotNull null
                    StoryCharacterHint(
                        name = character.name,
                        fingerprint = ChatCharacterMatcher.fingerprintOf(character),
                        activationMode = when (relation.optInt(
                            "activationMode",
                            StoryCharacter.ACTIVATION_AUTO
                        )) {
                            StoryCharacter.ACTIVATION_PRIMARY -> StoryArchiveCodec.MODE_PRIMARY
                            StoryCharacter.ACTIVATION_ALWAYS -> StoryArchiveCodec.MODE_ALWAYS
                            else -> StoryArchiveCodec.MODE_AUTO
                        }
                    )
                }
            val lorebookHints = loreRows
                .filter { it.optLong("storyId") == id }
                .mapNotNull { relation ->
                    val entry = lorebookEntries[relation.optLong("lorebookEntryId")]
                        ?: return@mapNotNull null
                    val book = lorebooks[entry.optLong("lorebookId")]
                    StoryLorebookHint(
                        lorebookName = book?.optString("name").orEmpty(),
                        entryName = entry.optString("name"),
                        fingerprint = textFingerprint(entry.optString("content"))
                    )
                }
            // 菜单级格式承载正文与匹配提示，本地标识另外记录。
            val archive = StoryArchive(
                story = ArchivedStory(
                    title = story.optString("title"),
                    memory = story.optString("memory"),
                    summary = story.optString("summary"),
                    authorNote = story.optString("authorNote"),
                    includeUserPersona = story.flag("includeUserPersona"),
                    ungroupedChapters = ungrouped.map {
                        ArchivedChapter(
                            title = it.optString("title"),
                            content = it.optString("content"),
                            continuationGuidance = it.optString("continuationGuidance")
                        )
                    },
                    volumes = archivedVolumes
                ),
                characterHints = characterHints,
                lorebookHints = lorebookHints
            )
            val doc = JSONObject(mStoryCodec.encode(archive))
            // sidecar 保留章节修订和本地关系，恢复时再回填实际主键。
            val volumeSides = JSONArray()
            val volumeChapterSides = JSONArray()
            volumes.forEach { volume ->
                val inVolume = chapters.filter {
                    !it.isNull("volumeId") && it.optLong("volumeId") == volume.optLong("id")
                }
                volumeSides.put(JSONObject().put("id", volume.optLong("id")))
                volumeChapterSides.put(JSONArray().apply {
                    inVolume.forEach { put(chapterSide(it)) }
                })
            }
            val ungroupedSides = JSONArray().apply { ungrouped.forEach { put(chapterSide(it)) } }
            doc.put(
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
                    .put("characters", JSONArray().apply {
                        characterRows.filter { it.optLong("storyId") == id }.forEach { put(it) }
                    })
                    .put("lorebookStates", JSONArray().apply {
                        loreRows.filter { it.optLong("storyId") == id }.forEach { put(it) }
                    })
            )
            writeText(
                docFile("stories", story.optString("title"), id, ".rpstory.json"),
                doc.toString(2)
            )
        }
        mReport.info("stories: ${stories.size}")
    }

    /** 提取菜单级故事格式无法表达的章节标识、时间与修订信息。 */
    private fun chapterSide(chapter: JSONObject) = JSONObject()
        .put("id", chapter.optLong("id"))
        .put("contentRevision", chapter.opt("contentRevision"))
        .put("createTime", chapter.opt("createTime"))
        .put("latestTime", chapter.opt("latestTime"))

    /** 生成内容指纹，为独立故事导入提供世界书条目匹配提示。 */
    private fun textFingerprint(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** 写出单聊 JSONL，并以消息来源 ID 保留本地关联和摘要覆盖边界。 */
    private suspend fun writeChats() {
        own("chat_sessions", "chat_messages")
        val characters =
            rows("character").associate { it.optLong("id") to entity(it, Character::class.java) }
        val fileRows = rows("files").associateBy { it.optString("uuid") }
        // 预先分组现有图片关系，消息正文仍按会话内的时间与 ID 排序。
        val imagesByMessage = rows("message_images")
            .filter { it.optString("messageType") == "single" }
            .groupBy { it.optLong("messageId") }
            .mapValues { (_, images) -> images.sortedBy { it.optInt("position") } }
        val sessions = rows("chat_sessions")
        var messageCount = 0
        sessions.forEach { session ->
            val id = session.optLong("id")
            val character = characters[session.optLong("characterId")]
            val all =
                BackupDatabaseCodec.readRows(mDatabase, "chat_messages", "sessionId", id).objects()
                    .sortedWith(compareBy<JSONObject> { it.optLong("createTime") }.thenBy {
                        it.optLong(
                            "id"
                        )
                    })
            // 归档格式只承载最新摘要，较早快照保留在技术附加信息中。
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
                includeCharacterExamples = if (session.has("includeCharacterExamples")) {
                    session.flag("includeCharacterExamples")
                } else {
                    true
                },
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
            // 先写头部，再逐条附加消息来源 ID，保证 sidecar 顺序与消息一致。
            val output = StringBuilder()
            val headerWriter = StringWriter()
            mChatCodec.encodeHeader(archive, headerWriter)
            val header = JSONObject(headerWriter.toString().trimEnd())
            val headerSide = JSONObject()
                .put(
                    "session",
                    JSONObject().put("id", id).put("characterId", session.opt("characterId"))
                )
            if (latest != null) headerSide.put("summaryId", latest.optLong("id"))
            headerSide.put("olderSummaries", JSONArray().apply {
                summaries.dropLast(1).forEach { put(it) }
            })
            header.put(BackupV3.SIDECAR, headerSide)
            output.append(header.toString()).append('\n')

            // 同一会话内的重复图片使用已有哈希引用，避免重复写入载荷。
            val writtenHashes = mutableSetOf<String>()
            normal.forEachIndexed { index, messageRow ->
                val originalId = messageRow.optLong("id")
                val attachments = imagesByMessage[originalId].orEmpty().mapNotNull { relation ->
                    val fileRow =
                        fileRows[relation.optString("imageUuid")] ?: return@mapNotNull null
                    val hash = fileRow.optString("hash")
                    val file =
                        File(mAttachmentDirectory, hash).takeIf { hash.isNotBlank() && it.isFile }
                            ?: return@mapNotNull null
                    val mimeType = fileRow.optString("mimeType")
                        .takeIf { it.startsWith("image/", ignoreCase = true) }
                        ?: "image/jpeg"
                    Triple(file, hash, mimeType)
                }
                val message = ChatArchiveMessage(
                    createTime = messageRow.optLong("createTime"),
                    role = roleOf(messageRow.optString("source")),
                    content = messageRow.optString("content"),
                    images = attachments.map { (_, hash, mime) ->
                        ChatArchiveImage(
                            mimeType = mime,
                            hash = hash
                        )
                    }
                )
                val lineWriter = StringWriter()
                if (attachments.isEmpty()) {
                    mChatCodec.encodeMessage(archive, message, lineWriter, writtenHashes)
                } else {
                    mChatCodec.encodeMessageWithImages(archive, message, lineWriter) {
                        var first = true
                        attachments.forEach { (file, hash, mime) ->
                            if (!first) lineWriter.write(",")
                            first = false
                            if (hash in writtenHashes) {
                                ChatArchiveImageCodec.writeImage(
                                    ChatArchiveImage(hash = hash),
                                    lineWriter
                                )
                            } else {
                                ChatArchiveImageCodec.writeImage(
                                    ChatArchiveImage(mimeType = mime, hash = hash),
                                    lineWriter
                                ) { encoded ->
                                    file.inputStream().use { input -> input.copyTo(encoded) }
                                }
                                writtenHashes.add(hash)
                            }
                        }
                    }
                }
                val json = JSONObject(lineWriter.toString().trimEnd())
                json.put(BackupV3.SIDECAR, JSONObject().put("id", originalId))
                output.append(json.toString()).append('\n')
            }
            writeText(docFile("chats", session.optString("title"), id, ".jsonl"), output.toString())
            messageCount += all.size
        }
        mReport.info("chats: ${sessions.size}, messages: $messageCount")
    }

    /** 将本地消息来源映射为单聊归档角色。 */
    private fun roleOf(source: String): ChatArchiveMessageRole = when (source) {
        "User" -> ChatArchiveMessageRole.User
        "System" -> ChatArchiveMessageRole.Narrator
        else -> ChatArchiveMessageRole.Character
    }

    /** 将群聊会话及成员、摘要和消息按类型写入同一 JSONL。 */
    private fun writeGroups() {
        own(
            "group_chat_sessions",
            "group_chat_members",
            "group_chat_messages",
            "group_chat_summaries"
        )
        // 会话行必须先于关联行写入，读取端以它作为当前文件的父记录。
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
                related("group_chat_summaries").sortedBy { it.optLong("id") }
                    .forEach { line("summary", it) }
                related("group_chat_messages")
                    .sortedWith(compareBy<JSONObject> { it.optLong("createTime") }.thenBy {
                        it.optLong(
                            "id"
                        )
                    })
                    .forEach { line("message", it) }
            }
        }
        mReport.info("group chats: ${sessions.size}")
    }

    /** 为没有菜单级归档格式的表写出原始 JSONL 行。 */
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
 * 读取 v3 功能文档并生成恢复所需的数据库行。
 * - 没有 [BackupV3.SIDECAR] 的新增文档在全部读取完成后分配主键。
 * - 外键先记录对象关系，主键分配完成后统一回填。
 * - 缺少父记录的孤立行由调用方的数据库重建阶段检查。
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
    private val mOriginalCharacterRows = mutableMapOf<Long, MutableList<JSONObject>>()
    private val mOriginalLorebookRows = mutableMapOf<Long, MutableList<JSONObject>>()
    private val mOriginalLorebookEntryRows = mutableMapOf<Long, MutableList<JSONObject>>()
    private val mOriginalMessageRows = mutableMapOf<Long, MutableList<JSONObject>>()
    private val mOriginalGroupMessageRows = mutableMapOf<Long, MutableList<JSONObject>>()
    private val mPendingCards = mutableListOf<PendingCard>()
    private val mLinks = mutableListOf<Link>()
    private val mNow = System.currentTimeMillis()

    /** 角色卡内嵌的世界书要等 worldbooks/ 读完后才能决定是否使用。 */
    private class PendingCard(
        val file: File,
        val row: JSONObject,
        val embedded: CharacterBookImport?
    )

    /** 子行的外键字段在主键分配完成后再回填。 */
    private class Link(val child: JSONObject, val field: String, val parent: JSONObject)

    /**
     * 读取业务文档、分配主键并回填已登记的对象关联。
     * @return 按表名分组的业务行，数据库结构校验由调用方执行。
     */
    fun readPrimary(): Map<String, List<JSONObject>> {
        readRowFiles("tables", mTables)
        readCharacters()
        readWorldbooks()
        resolveCharacterLorebooks()
        readRegex()
        readStories()
        readChats()
        readGroups()
        resolveIds()
        mLinks.forEach { it.child.put(it.field, it.parent.getLong("id")) }
        mTables.forEach { (table, list) -> mReport.info("rows to restore: $table = ${list.size}") }
        return mTables
    }

    /**
     * 读取独立请求日志库的原始行。
     * @return 按日志表名分组的数据行，不合并到主库。
     */
    fun readLogs(): Map<String, List<JSONObject>> {
        val logs = linkedMapOf<String, MutableList<JSONObject>>()
        readRowFiles("request-logs", logs)
        logs.forEach { (table, list) -> mReport.info("rows to restore: $table = ${list.size}") }
        return logs
    }

    /** 只返回归档内相对路径，避免技术摘要暴露本地缓存目录。 */
    private fun rel(file: File): String = file.relativeTo(mRoot).invariantSeparatorsPath

    /** 为内部解析异常附加归档位置；界面必须通过安全报告生成错误摘要。 */
    private inline fun <T> at(file: File, line: Int? = null, block: () -> T): T {
        try {
            return block()
        } catch (error: Exception) {
            val where = rel(file) + if (line != null) " (line $line)" else ""
            throw IllegalArgumentException("Cannot read $where: ${error.message}", error)
        }
    }

    /** 按稳定路径顺序枚举指定类型文档，缺失目录按空集合处理。 */
    private fun files(dir: String, suffix: String): List<File> {
        val base = File(mRoot, dir)
        if (!base.isDirectory) return emptyList()
        return base.walkTopDown().filter { it.isFile && it.name.endsWith(suffix) }
            .sortedBy { it.path }.toList()
    }

    /** 暂存业务行，读取阶段不直接写入数据库。 */
    private fun add(table: String, row: JSONObject) {
        mTables.getOrPut(table) { mutableListOf() }.add(row)
    }

    /** 登记对象关联，主键重新分配后再填入最终外键。 */
    private fun link(child: JSONObject, field: String, parent: JSONObject) {
        mLinks.add(Link(child, field, parent))
    }

    /** 按 Room 字段名生成业务行，布尔值由共享行编码器转换。 */
    private fun entityRow(entity: Any): JSONObject = JSONObject(mRowGson.toJson(entity))

    /** 有效来源 ID 先保留，其余留待统一分配。 */
    private fun applyId(side: JSONObject?, row: JSONObject, column: String = "id") {
        val id = side?.optLong("id", 0L) ?: 0L
        if (id > 0L) row.put(column, id) else row.remove(column)
    }

    /** 仅从 sidecar 回填指定的本地列，避免覆盖功能文档中的正文。 */
    private fun copyFrom(side: JSONObject?, row: JSONObject, vararg keys: String) {
        if (side == null) return
        keys.forEach { key -> if (side.has(key)) row.put(key, side.get(key)) }
    }

    /** 逐行读取通用表文档，解析失败时保留相对路径与行号供内部定位。 */
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

    /** 读取角色卡，登记来源 ID、角色正则和待解析的内嵌世界书关系。 */
    private fun readCharacters() {
        files("characters", ".json").forEach { file ->
            at(file) {
                // 文档正文经过菜单级解码，sidecar 只回填本地技术列。
                val text = file.readText(Charsets.UTF_8)
                val side = JSONObject(text).optJSONObject(BackupV3.SIDECAR)
                val parsed = mCardMapper.parseCharacter(text)
                val row = entityRow(parsed.character)
                copyFrom(side, row, "avatar", "characterLorebookId", "backgroundFileUuid")
                applyId(side, row)
                add("character", row)
                mCharacters.add(row)
                side?.optLong("id", 0L)?.takeIf { it > 0L }?.let { sourceId ->
                    mOriginalCharacterRows.getOrPut(sourceId) { mutableListOf() }.add(row)
                }
                // 角色作用域正则只来自角色卡本身，与单个角色导入一致。
                parsed.regexScripts.forEachIndexed { index, script ->
                    val regexRow = entityRow(script.toEntity(0L, index, mGson))
                    regexRow.remove("rowId")
                    link(regexRow, "characterId", row)
                    add("regex_scripts", regexRow)
                }
                mPendingCards.add(PendingCard(file, row, parsed.embeddedLorebook))
            }
        }
    }

    /** 根据来源世界书 ID 关联本次导入的对象，避免误用重新分配后的数字 ID。 */
    private fun resolveCharacterLorebooks() {
        // 独立世界书先按来源索引匹配，缺失或歧义时才沿用既有内嵌副本策略。
        mPendingCards.forEach { pending ->
            val linkedId = pending.row.optLong("characterLorebookId", 0L)
            val embedded = pending.embedded
            val sourceBooks = mOriginalLorebookRows[linkedId].orEmpty()
            if (linkedId > 0L && sourceBooks.size == 1) {
                link(pending.row, "characterLorebookId", sourceBooks.single())
                return@forEach
            }
            if (linkedId > 0L && sourceBooks.size > 1) {
                mReport.warn("${rel(pending.file)}: worldbook reference $linkedId is ambiguous; using the embedded copy when available")
            } else if (linkedId > 0L && sourceBooks.isEmpty()) {
                mReport.warn("${rel(pending.file)}: worldbook $linkedId is not available by source ID; using the embedded copy when available")
            }
            if (embedded != null) {
                link(pending.row, "characterLorebookId", addLorebook(embedded, null, emptyList()))
            } else if (linkedId > 0L) {
                mReport.warn("${rel(pending.file)}: worldbook $linkedId is missing or ambiguous and the card embeds none; the link was cleared")
                pending.row.put("characterLorebookId", 0L)
            }
        }
    }

    /** 写入一本世界书及其条目；sidecar 提供时按序号键恢复条目主键。 */
    private fun addLorebook(
        imported: CharacterBookImport,
        side: JSONObject?,
        keys: List<String>
    ): JSONObject {
        // 先保留来源标识，条目外键使用对象关系登记而非立即写入数字 ID。
        val bookRow = entityRow(imported.lorebook)
        applyId(side, bookRow)
        add("lorebooks", bookRow)
        side?.optLong("id", 0L)?.takeIf { it > 0L }?.let { sourceId ->
            mOriginalLorebookRows.getOrPut(sourceId) { mutableListOf() }.add(bookRow)
        }
        val ids = side?.optJSONObject("entryIds")
        imported.entries.forEachIndexed { index, entry ->
            val row = entityRow(entry)
            val entryId = keys.getOrNull(index)?.let { ids?.optLong(it, 0L) } ?: 0L
            if (entryId > 0L) {
                row.put("id", entryId)
                mOriginalLorebookEntryRows.getOrPut(entryId) { mutableListOf() }.add(row)
            } else row.remove("id")
            link(row, "lorebookId", bookRow)
            add("lorebook_entries", row)
        }
        return bookRow
    }

    /** 读取独立世界书，按编解码器的条目顺序恢复 sidecar 来源 ID。 */
    private fun readWorldbooks() {
        files("worldbooks", ".json").forEach { file ->
            at(file) {
                val text = file.readText(Charsets.UTF_8)
                val doc = JSONObject(text)
                val parsed = mLorebookCodec.parseLorebook(text)
                // 解码器按文件顺序产出条目，这里取同一顺序的键来匹配条目主键。
                val keys = mutableListOf<String>()
                doc.optJSONObject("entries")?.let { entries ->
                    entries.keys()
                        .forEach { key -> if (entries.opt(key) is JSONObject) keys.add(key) }
                }
                check(keys.size == parsed.entries.size) { "entry count mismatch while reading entries" }
                addLorebook(parsed, doc.optJSONObject(BackupV3.SIDECAR), keys)
            }
        }
    }

    /** 读取全局正则；角色正则由角色卡入口提取。 */
    private fun readRegex() {
        files("regex", ".json").forEach { file ->
            if (file.name != GLOBAL_REGEX_FILE) {
                mReport.warn("${rel(file)}: ignored; only $GLOBAL_REGEX_FILE is read (character regex lives inside character cards)")
                return@forEach
            }
            at(file) {
                mRegexCodec.parseList(file.readText(Charsets.UTF_8))
                    .forEachIndexed { index, script ->
                        val row = entityRow(script.toEntity(null, index, mGson))
                        row.remove("rowId")
                        add("regex_scripts", row)
                    }
            }
        }
    }

    /** 还原故事正文结构，并将角色、世界书及章节关系登记为待回填关联。 */
    private fun readStories() {
        files("stories", ".json").forEach { file ->
            at(file) {
                // 文档正文经过菜单级解码，sidecar 只回填本地技术列。
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
                copyFrom(
                    storySide,
                    storyRow,
                    "worldInfoGenerationStep",
                    "revision",
                    "createTime",
                    "latestTime"
                )
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

                // 分卷和章节按文档顺序创建，本地时间与修订值从附加信息补齐。
                val volumeSides = side?.optJSONArray("volumes")
                val volumeChapterSides = side?.optJSONArray("volumeChapters")
                story.volumes.forEachIndexed { volumeIndex, volume ->
                    val volumeRow =
                        JSONObject().put("title", volume.title).put("sortOrder", volumeIndex)
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
                side?.optJSONArray("characters")?.objects()?.forEach { relation ->
                    val sourceCharacters =
                        mOriginalCharacterRows[relation.optLong("characterId")].orEmpty()
                    if (sourceCharacters.size == 1) {
                        link(relation, "storyId", storyRow)
                        link(relation, "characterId", sourceCharacters.single())
                        add("story_characters", relation)
                    } else {
                        mReport.warn("${rel(file)}: story character reference ${relation.optLong("characterId")} is ambiguous or missing; relation skipped")
                    }
                }
                side?.optJSONArray("lorebookStates")?.objects()?.forEach { relation ->
                    val sourceEntries =
                        mOriginalLorebookEntryRows[relation.optLong("lorebookEntryId")].orEmpty()
                    if (sourceEntries.size == 1) {
                        link(relation, "storyId", storyRow)
                        link(relation, "lorebookEntryId", sourceEntries.single())
                        add("story_lorebook_entries", relation)
                    } else {
                        mReport.warn(
                            "${rel(file)}: story lorebook entry reference ${
                                relation.optLong(
                                    "lorebookEntryId"
                                )
                            } is ambiguous or missing; relation skipped"
                        )
                    }
                }
            }
        }
    }

    /** 从可空 Gson 对象读取技术标识，缺失值保持为空。 */
    private fun JsonObject?.longValue(name: String): Long? {
        return this?.get(name)?.takeIf { it.isJsonPrimitive }?.asLong
    }

    /** 按 JSONL 对象顺序提取 sidecar，使其与功能编解码器产出的消息对齐。 */
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

    /** 还原单聊和摘要关系，消息读取完毕后统一解析已有附件的来源 ID。 */
    private fun readChats() {
        files("chats", ".jsonl").forEach { file ->
            at(file) {
                val text = file.readText(Charsets.UTF_8)
                // 先复用单聊解码，再按相同消息过滤规则提取附加来源 ID。
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
                    .put("includeCharacterExamples", archive.includeCharacterExamples)
                archive.creatorNotes?.let { sessionRow.put("creatorNotes", it) }
                sessionSide.longValue("id")?.takeIf { it > 0L }?.let { sessionRow.put("id", it) }
                // 角色匹配沿用当前规则；已登记的对象关联在统一分配主键后回填。
                val sourceCharacterId = sessionSide.longValue("characterId")
                val sourceCharacterRows =
                    sourceCharacterId?.let { mOriginalCharacterRows[it] }.orEmpty()
                val characterRow = sourceCharacterRows.singleOrNull()
                    ?: mCharacters.singleOrNull { it.optString("name") == archive.characterNameHint }
                if (characterRow == null) {
                    mReport.warn(
                        "${rel(file)}: chat skipped because no unambiguous character match was restored. " +
                            "Restore the character or import this chat separately using the existing chat import flow."
                    )
                    return@at
                }
                link(sessionRow, "characterId", characterRow)
                add("chat_sessions", sessionRow)

                // 普通消息先建立来源索引，摘要覆盖边界才能定位到本次恢复对象。
                val messageRowsBySourceId = mutableMapOf<Long, MutableList<JSONObject>>()
                val messageRows = archive.messages.mapIndexed { index, message ->
                    val row = JSONObject()
                        .put("createTime", message.createTime)
                        .put("source", sourceOf(message.role))
                        .put("content", message.content)
                    messageSides.getOrNull(index).longValue("id")?.takeIf { it > 0L }?.let {
                        row.put("id", it)
                        messageRowsBySourceId.getOrPut(it) { mutableListOf() }.add(row)
                        mOriginalMessageRows.getOrPut(it) { mutableListOf() }.add(row)
                    }
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
                    if (covered != null) link(
                        row,
                        "coveredMessageId",
                        covered
                    ) else row.put("coveredMessageId", 0L)
                    link(row, "sessionId", sessionRow)
                    add("chat_messages", row)
                }
                headerSide?.getAsJsonArray("olderSummaries")?.forEach { element ->
                    val row = JSONObject(element.toString())
                    val sourceCoveredId = row.optLong("coveredMessageId", 0L)
                    if (sourceCoveredId > 0L) {
                        val coveredRows = messageRowsBySourceId[sourceCoveredId].orEmpty()
                        if (coveredRows.size == 1) {
                            link(row, "coveredMessageId", coveredRows.single())
                        } else {
                            // 丢失或歧义的覆盖边界不能保留旧 ID，避免误指向其他恢复消息。
                            row.put("coveredMessageId", 0L)
                            mReport.warn(
                                "${rel(file)}: older summary references message $sourceCoveredId that is ambiguous or missing; coverage was cleared"
                            )
                        }
                    }
                    link(row, "sessionId", sessionRow)
                    add("chat_messages", row)
                }
            }
        }
        // 全部聊天读完后再解析附件来源 ID，将跨文件的重复来源视为歧义，避免后读覆盖前读。
        val imageRelations = mTables["message_images"] ?: return
        imageRelations.toList().forEach { relation ->
            if (relation.optString("messageType") == "single") {
                val sourceId = relation.optLong("messageId", 0L)
                val messageRow = mOriginalMessageRows[sourceId].orEmpty().singleOrNull()
                if (messageRow != null) {
                    link(relation, "messageId", messageRow)
                } else {
                    mReport.warn("message_images references missing or ambiguous message $sourceId; relation was removed")
                    imageRelations.remove(relation)
                }
            }
        }
    }

    /** 将归档角色映射回数据库消息来源标识。 */
    private fun sourceOf(role: ChatArchiveMessageRole): String = when (role) {
        ChatArchiveMessageRole.User -> "User"
        ChatArchiveMessageRole.Character -> "Char"
        ChatArchiveMessageRole.Narrator -> "System"
    }

    /** 按行类型还原群聊关系，并在全部消息建立索引后解析摘要覆盖边界。 */
    private fun readGroups() {
        // 关联行依赖同一文件中先出现的会话行；来源消息索引跨文件共享。
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
                            val parent = session
                                ?: throw IllegalArgumentException("the session line must come first")
                            val table = when (type) {
                                "member" -> "group_chat_members"
                                "summary" -> "group_chat_summaries"
                                "message" -> "group_chat_messages"
                                else -> throw IllegalArgumentException("unknown line type '$type'")
                            }
                            link(row, "sessionId", parent)
                            when (type) {
                                "member" -> {
                                    val sourceCharacterId = row.optLong("characterId", 0L)
                                    val candidates =
                                        mOriginalCharacterRows[sourceCharacterId].orEmpty()
                                    if (candidates.size != 1) {
                                        mReport.warn(
                                            "${rel(file)} (line $lineNumber): group member references missing or ambiguous character $sourceCharacterId; member skipped"
                                        )
                                        return@at
                                    }
                                    link(row, "characterId", candidates.single())
                                }

                                "message" -> {
                                    val sourceId = row.optLong("id", 0L)
                                    if (sourceId > 0L) {
                                        mOriginalGroupMessageRows.getOrPut(sourceId) { mutableListOf() }
                                            .add(row)
                                    }
                                    val sourceSpeakerId = row.optLong("speakerCharacterId", 0L)
                                    if (sourceSpeakerId > 0L) {
                                        val candidates =
                                            mOriginalCharacterRows[sourceSpeakerId].orEmpty()
                                        if (candidates.size == 1) {
                                            link(row, "speakerCharacterId", candidates.single())
                                        } else {
                                            row.put("speakerCharacterId", JSONObject.NULL)
                                            mReport.warn(
                                                "${rel(file)} (line $lineNumber): group message speaker references missing or ambiguous character $sourceSpeakerId; speaker link cleared"
                                            )
                                        }
                                    }
                                }
                            }
                            add(table, row)
                        }
                    }
                }
            }
        }
        // 群聊摘要在文件中先于消息，需等待所有消息完成索引后再解析覆盖边界。
        mTables["group_chat_summaries"].orEmpty().forEach { row ->
            val sourceMessageId = row.optLong("coveredMessageId", 0L)
            if (sourceMessageId <= 0L) {
                row.put("coveredMessageId", 0L)
                return@forEach
            }
            val candidates = mOriginalGroupMessageRows[sourceMessageId].orEmpty()
            if (candidates.size == 1) {
                link(row, "coveredMessageId", candidates.single())
            } else {
                row.put("coveredMessageId", 0L)
                mReport.warn(
                    "group_chat_summaries: covered message $sourceMessageId is missing or ambiguous; coverage was cleared"
                )
            }
        }
    }

    /** 重复主键视为复制出的新条目并重新分配；缺少主键的新增行从当前最大值之后顺序分配。 */
    private fun resolveIds() {
        // 保留首次出现的有效标识；重复和缺失标识统一从最大值之后分配。
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
    }
}
