package me.kafuuneko.rpclient.libs.backup

import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.File
import java.io.StringWriter
import java.io.Writer
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
import me.kafuuneko.rpclient.libs.room.entity.StoryLorebookEntry
import me.kafuuneko.rpclient.libs.story.ArchivedChapter
import me.kafuuneko.rpclient.libs.story.ArchivedStory
import me.kafuuneko.rpclient.libs.story.ArchivedVolume
import me.kafuuneko.rpclient.libs.story.StoryArchive
import me.kafuuneko.rpclient.libs.story.StoryArchiveCodec
import me.kafuuneko.rpclient.libs.story.StoryArchiveMatcher
import me.kafuuneko.rpclient.libs.story.StoryCharacterHint
import me.kafuuneko.rpclient.libs.story.StoryLorebookHint
import me.kafuuneko.rpclient.libs.story.storyTextHash
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

    private val mPersonas by lazy { rows("user_personas").associateBy { it.getLong("id") } }

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
     * 复用 CharacterCardRepository.exportJson 的格式，内嵌绑定世界书和角色正则，并补齐本地开场白。
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
                // 首条仍由标准字段承载；附加键只保存其余开场白，避免完整备份丢失本地多开场白。
                val firstMessages = character.firstMessages.split("<START>")
                card.getJSONObject("data").put("first_mes", firstMessages.first())
                card.put(
                    BackupV3.SIDECAR,
                    JSONObject()
                        .put("id", id)
                        .put("avatar", row.opt("avatar"))
                        .put("characterLorebookId", row.opt("characterLorebookId"))
                        .put("backgroundFileUuid", row.opt("backgroundFileUuid"))
                        .put("additionalFirstMessages", JSONArray(firstMessages.drop(1)))
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
            val entryCategories = JSONObject()
            entryRows.forEachIndexed { index, row ->
                val key = index.toString()
                entryIds.put(key, row.optLong("id"))
                // 本地分类不属于酒馆协议，与来源 ID 使用相同条目键，保留原始分类 JSON。
                entryCategories.put(key, row.getString("category"))
            }
            doc.put(
                BackupV3.SIDECAR,
                JSONObject().put("id", id).put("entryIds", entryIds)
                    .put("entryCategories", entryCategories)
            )
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
                        fingerprint = storyTextHash(entry.optString("content"))
                    )
                }
            // 菜单级格式承载正文与匹配提示，本地标识另外记录。
            val archive = StoryArchive(
                story = ArchivedStory(
                    title = story.optString("title"),
                    memory = story.optString("memory"),
                    summary = story.optString("summary"),
                    authorNote = story.optString("authorNote"),
                    includeUserPersona = story.getLong("userPersonaId") > 0,
                    personaNameHint = mPersonas[story.getLong("userPersonaId")]?.getString("name"),
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
                            .put("userPersonaId", story.getLong("userPersonaId"))
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
            val summaries = mDatabase.query(
                "SELECT * FROM chat_messages WHERE sessionId = ? AND source = ? ORDER BY createTime, id",
                arrayOf<Any>(id, SOURCE_SUMMARY)
            ).use { cursor ->
                buildList { while (cursor.moveToNext()) add(BackupDatabaseCodec.rowJson(cursor)) }
            }
            val archive = chatHeader(session, character, summaries.lastOrNull())
            val file = docFile("chats", session.optString("title"), id, ".jsonl")
            file.parentFile?.mkdirs()
            file.bufferedWriter().use { writer ->
                writeChatHeader(writer, archive, session, summaries)
                val hashes = mutableSetOf<String>()
                // 有序游标随 CursorWindow 分段读取正文，不在内存聚合完整会话或图片。
                mDatabase.query(
                    "SELECT id, createTime, source, content FROM chat_messages " +
                        "WHERE sessionId = ? AND source != ? ORDER BY createTime, id",
                    arrayOf<Any>(id, SOURCE_SUMMARY)
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val messageId = cursor.getLong(0)
                        val message = ChatArchiveMessage(
                            cursor.getLong(1),
                            roleOf(cursor.getString(2)),
                            cursor.getString(3)
                        )
                        val files = imagesByMessage[messageId].orEmpty()
                            .mapNotNull { fileRows[it.optString("imageUuid")] }
                        writeChatMessage(writer, archive, messageId, message, files, hashes)
                        messageCount++
                    }
                }
            }
            messageCount += summaries.size
        }
        mReport.info("chats: ${sessions.size}, messages: $messageCount")
    }

    /** 构造酒馆会话头；历史正文由游标单独写出。 */
    private fun chatHeader(
        session: JSONObject,
        character: Character?,
        latest: JSONObject?
    ): ChatArchive {
        val persona = mPersonas.getValue(session.getLong("userPersonaId"))
        // 头部只保留会话状态和最新摘要，普通消息由同一读事务内的游标继续写出。
        return ChatArchive(
            title = session.optString("title"),
            createTime = session.optLong("createTime"),
            latestTime = session.optLong("latestTime"),
            userName = session.textOrNull("userName")?.takeIf(String::isNotEmpty)
                ?: persona.getString("name"),
            userDescription = session.textOrNull("userDescription")?.takeIf(String::isNotEmpty)
                ?: persona.getString("description"),
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
            messages = emptyList(),
            summary = latest?.let {
                ChatArchiveSummary(
                    content = it.optString("content"),
                    createTime = it.optLong("createTime"),
                    coveredMessageIndex = coveredMessageIndex(
                        session.getLong("id"),
                        it.optLong("coveredMessageId")
                    )
                )
            }
        )
    }

    /** 按与正文相同的顺序计算摘要边界；跨会话、缺失或摘要行都视作空边界。 */
    private fun coveredMessageIndex(sessionId: Long, coveredId: Long): Int {
        if (coveredId <= 0L) return -1
        return mDatabase.query(
            """SELECT (SELECT COUNT(*) FROM chat_messages m WHERE m.sessionId = b.sessionId
                AND m.source != ? AND (m.createTime < b.createTime OR (m.createTime = b.createTime AND m.id < b.id)))
                FROM chat_messages b WHERE b.sessionId = ? AND b.id = ? AND b.source != ?""",
            arrayOf<Any>(SOURCE_SUMMARY, sessionId, coveredId, SOURCE_SUMMARY)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else -1 }
    }

    /** 摘要与来源标识仍保存在旧版读取器支持的头部附加字段中。 */
    private fun writeChatHeader(
        writer: Writer,
        archive: ChatArchive,
        session: JSONObject,
        summaries: List<JSONObject>
    ) {
        val headerWriter = StringWriter()
        mChatCodec.encodeHeader(archive, headerWriter)
        // 公共头部保存有效值，附加信息保存本地绑定和两个字段各自的继承状态。
        val side = JSONObject().put(
            "session",
            JSONObject().put("id", session.getLong("id"))
                .put("characterId", session.opt("characterId"))
                .put("userPersonaId", session.getLong("userPersonaId"))
                .put("userNameOverride", overrideState(session, "userName"))
                .put("userDescriptionOverride", overrideState(session, "userDescription"))
        )
        summaries.lastOrNull()?.let { side.put("summaryId", it.getLong("id")) }
        side.put("olderSummaries", JSONArray(summaries.dropLast(1)))
        BackupV3.writeJsonLine(
            JSONObject(headerWriter.toString()).put(BackupV3.SIDECAR, side),
            writer
        )
    }

    /** 覆盖正文仍由可编辑的聊天文档持有，附加信息只标记其继承语义。 */
    private fun overrideState(session: JSONObject, key: String) = when (session.textOrNull(key)) {
        null -> "inherit"
        "" -> "empty"
        else -> "value"
    }

    /** 将消息 ID 与正文一同写入；同一会话的重复图片只写哈希。 */
    private suspend fun writeChatMessage(
        writer: Writer, archive: ChatArchive, id: Long, message: ChatArchiveMessage,
        files: List<JSONObject>, hashes: MutableSet<String>
    ) {
        val addSourceId: JsonObject.() -> Unit =
            { add(BackupV3.SIDECAR, JsonObject().apply { addProperty("id", id) }) }
        if (files.isEmpty()) {
            mChatCodec.encodeMessage(
                archive,
                message,
                writer,
                writtenHashes = hashes,
                addFields = addSourceId
            )
            return
        }
        // 图片直接编码到目标文件，不能回读整条 JSON 来追加来源字段。
        mChatCodec.encodeMessageWithImages(archive, message, writer, addFields = addSourceId) {
            var first = true
            for (row in files) {
                val hash = row.optString("hash")
                val file = File(mAttachmentDirectory, hash)
                if (hash.isBlank() || !file.isFile) continue
                if (!first) writer.write(",")
                first = false
                if (hashes.add(hash)) {
                    val mime = row.optString("mimeType")
                        .takeIf { it.startsWith("image/", ignoreCase = true) } ?: "image/jpeg"
                    ChatArchiveImageCodec.writeImage(
                        ChatArchiveImage(mimeType = mime, hash = hash),
                        writer
                    ) { encoded ->
                        file.inputStream().use { it.copyTo(encoded) }
                    }
                } else {
                    ChatArchiveImageCodec.writeImage(ChatArchiveImage(hash = hash), writer)
                }
            }
        }
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
    private val mReport: BackupReport,
    private val mHasPersonas: Boolean,
) {
    private val mRowGson = BackupV3.rowGson(mGson)
    private val mCardMapper = CharacterCardMapper(mGson)
    private val mLorebookCodec = LorebookCodec(mGson)
    private val mRegexCodec = RegexScriptCodec(mGson)
    private val mStoryCodec = StoryArchiveCodec(mGson)
    private val mChatCodec = ChatArchiveCodec(mGson)

    private val mTables = linkedMapOf<String, MutableList<JSONObject>>()
    private val mPersonas by lazy {
        mTables["user_personas"].orEmpty().associateBy { it.getLong("id") }
    }
    private val mDefaultPersona by lazy {
        val settings = mTables["user_persona_settings"].orEmpty().single()
        require(settings.getInt("id") == 1)
        mPersonas.getValue(settings.getLong("defaultPersonaId"))
    }
    private val mCharacters = mutableListOf<JSONObject>()
    private val mOriginalCharacterRows = mutableMapOf<Long, MutableList<JSONObject>>()
    private val mOriginalLorebookRows = mutableMapOf<Long, MutableList<JSONObject>>()
    private val mOriginalLorebookEntryRows = mutableMapOf<Long, MutableList<JSONObject>>()
    private val mOriginalGroupMessageRows = mutableMapOf<Long, MutableList<JSONObject>>()
    private val mPendingCards = mutableListOf<PendingCard>()
    private val mLinks = mutableListOf<Link>()
    private val mNow = System.currentTimeMillis()
    private val mImageStore = BackupImageStore(File(mRoot, "attachments"))
    private val mOriginalSingleImages = mutableMapOf<Long, List<JSONObject>>()
    private val mFilesByUuid = mutableMapOf<String, JSONObject>()
    private val mUsedImageUuids = mutableSetOf<String>()

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
        if (mHasPersonas) {
            // 新版完整备份必须自带默认指针，包括没有会话的空库。
            require(mDefaultPersona.getLong("id") > 0)
        }
        readCharacters()
        readWorldbooks()
        resolveCharacterLorebooks()
        readRegex()
        readStories()
        readChats()
        readGroups()
        resolveGroupImages()
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
            if (BackupDatabaseCodec.isSearchDataTable(table)) return@forEach
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
                val document = JSONObject(text)
                val side = document.optJSONObject(BackupV3.SIDECAR)
                val parsed = mCardMapper.parseCharacter(text)
                val row = entityRow(parsed.character)
                // 旧 v3 没有附加开场白时沿用角色卡规则，新备份保留分隔符、空段与原始空白。
                if (side?.has("additionalFirstMessages") == true) {
                    val additional = side.getJSONArray("additionalFirstMessages")
                    val first = document.getJSONObject("data").getString("first_mes")
                    val messages = listOf(first) + (0 until additional.length()).map {
                        additional.getString(it)
                    }
                    row.put("firstMessages", messages.joinToString("<START>"))
                }
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
        val categories = side?.optJSONObject("entryCategories")
        imported.entries.forEachIndexed { index, entry ->
            val row = entityRow(entry)
            val key = keys.getOrNull(index)
            val entryId = key?.let { ids?.optLong(it, 0L) } ?: 0L
            if (key != null && categories?.has(key) == true) {
                row.put("category", categories.getString(key))
            }
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
                if (mHasPersonas) {
                    val id = when {
                        storySide?.has("userPersonaId") == true -> storySide.getLong("userPersonaId")
                        !story.includeUserPersona -> -1L
                        else -> mPersonas.values.singleOrNull { it.getString("name") == story.personaNameHint }
                            ?.getLong("id") ?: mDefaultPersona.getLong("id")
                    }
                    require(id == -1L || id in mPersonas)
                    storyRow.put("userPersonaId", id)
                } else {
                    storyRow.put("includeUserPersona", story.includeUserPersona)
                }
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
                restoreStoryRelations(file, side, archive, storyRow)
            }
        }
    }

    /** 已有本地关系严格按来源 ID 恢复；没有关系附加信息的文档使用菜单提示规则。 */
    private fun restoreStoryRelations(
        file: File,
        side: JSONObject?,
        archive: StoryArchive,
        story: JSONObject
    ) {
        if (side?.has("characters") == true) {
            restoreStoryCharacterIds(file, side.getJSONArray("characters"), story)
        } else {
            restoreStoryCharacterHints(file, archive.characterHints, story)
        }
        if (side?.has("lorebookStates") == true) {
            restoreStoryEntryIds(file, side.getJSONArray("lorebookStates"), story)
        } else {
            restoreStoryEntryHints(file, archive.lorebookHints, story)
        }
    }

    /** 来源角色缺失或歧义时移除关系，不把明确的旧主键改绑到另一个候选。 */
    private fun restoreStoryCharacterIds(file: File, relations: JSONArray, story: JSONObject) {
        relations.objects().forEach { relation ->
            val sourceId = relation.optLong("characterId")
            val character = mOriginalCharacterRows[sourceId].orEmpty().singleOrNull()
            if (character == null) {
                mReport.warn("${rel(file)}: story character reference $sourceId is missing or ambiguous; relation skipped")
                return@forEach
            }
            link(relation, "storyId", story)
            link(relation, "characterId", character)
            add("story_characters", relation)
        }
    }

    /** 保留已有世界书条目的运行状态，但不保留无法解析的旧主键。 */
    private fun restoreStoryEntryIds(file: File, relations: JSONArray, story: JSONObject) {
        relations.objects().forEach { relation ->
            val sourceId = relation.optLong("lorebookEntryId")
            val entry = mOriginalLorebookEntryRows[sourceId].orEmpty().singleOrNull()
            if (entry == null) {
                mReport.warn("${rel(file)}: story lorebook entry reference $sourceId is missing or ambiguous; relation skipped")
                return@forEach
            }
            link(relation, "storyId", story)
            link(relation, "lorebookEntryId", entry)
            add("story_lorebook_entries", relation)
        }
    }

    /** 新增故事按唯一指纹或名称恢复候选角色，顺序与激活方式来自文档提示。 */
    private fun restoreStoryCharacterHints(
        file: File,
        hints: List<StoryCharacterHint>,
        story: JSONObject
    ) {
        if (hints.isEmpty()) return
        val candidates =
            mCharacters.map { row -> mRowGson.fromJson(row.toString(), Character::class.java) }
        val matcher = StoryArchiveMatcher(characters = candidates)
        val selected = mutableSetOf<JSONObject>()
        hints.forEach { hint ->
            val match = matcher.matchCharacter(hint)
            val index = candidates.indexOfFirst { candidate -> candidate === match }
            if (index < 0) {
                mReport.warn("${rel(file)}: story character hint could not be matched uniquely; relation skipped")
                return@forEach
            }
            val character = mCharacters[index]
            if (!selected.add(character)) return@forEach
            // 主键在后续统一分配，此处只记录与候选对象的关系。
            val mode = when (hint.activationMode) {
                StoryArchiveCodec.MODE_PRIMARY -> StoryCharacter.ACTIVATION_PRIMARY
                StoryArchiveCodec.MODE_ALWAYS -> StoryCharacter.ACTIVATION_ALWAYS
                else -> StoryCharacter.ACTIVATION_AUTO
            }
            val relation = entityRow(
                StoryCharacter(
                    storyId = 0L,
                    characterId = 0L,
                    sortOrder = selected.size - 1,
                    activationMode = mode
                )
            )
            link(relation, "storyId", story)
            link(relation, "characterId", character)
            add("story_characters", relation)
        }
    }

    /** 新增故事匹配世界书提示，只创建选择关系，不沿用其他故事的时序状态。 */
    private fun restoreStoryEntryHints(
        file: File,
        hints: List<StoryLorebookHint>,
        story: JSONObject
    ) {
        if (hints.isEmpty()) return
        val bookRows = mTables["lorebooks"].orEmpty()
        val entryRows = mTables["lorebook_entries"].orEmpty()
        // 候选仍可能没有主键；临时书籍 ID 只供名称匹配，实际关联继续引用对象。
        val bookIds = bookRows.withIndex().associate { (index, row) -> row to index + 1L }
        val parents = mLinks.filter { relation -> relation.field == "lorebookId" }
            .associate { relation -> relation.child to relation.parent }
        val books = bookRows.map { row ->
            mRowGson.fromJson(row.toString(), Lorebook::class.java).copy(id = bookIds.getValue(row))
        }
        val entries = entryRows.map { row ->
            mRowGson.fromJson(row.toString(), LorebookEntry::class.java)
                .copy(lorebookId = parents[row]?.let(bookIds::get) ?: 0L)
        }
        val matcher = StoryArchiveMatcher(entries = entries, lorebooks = books)
        val selected = mutableSetOf<JSONObject>()
        hints.forEach { hint ->
            val match = matcher.matchLorebookEntry(hint)
            val index = entries.indexOfFirst { candidate -> candidate === match }
            if (index < 0) {
                mReport.warn("${rel(file)}: story lorebook hint could not be matched uniquely; relation skipped")
                return@forEach
            }
            val entry = entryRows[index]
            if (!selected.add(entry)) return@forEach
            val relation = entityRow(StoryLorebookEntry(storyId = 0L, lorebookEntryId = 0L))
            link(relation, "storyId", story)
            link(relation, "lorebookEntryId", entry)
            add("story_lorebook_entries", relation)
        }
    }

    /** 从可空 Gson 对象读取技术标识，缺失值保持为空。 */
    private fun JsonObject?.longValue(name: String): Long? {
        return this?.get(name)?.takeIf { it.isJsonPrimitive }?.asLong
    }

    /** 还原单聊，以文档里的图片内容重建关系，不再使用旧单聊图片表作为正文来源。 */
    private fun readChats() {
        prepareChatImages()
        files("chats", ".jsonl").forEach { file ->
            at(file) {
                var side: JsonObject? = null
                val messages = mutableListOf<JSONObject>()
                val images = mutableMapOf<JSONObject, List<ChatArchiveImage>>()
                val archive = file.bufferedReader().use { reader ->
                    mChatCodec.decodeTo(
                        reader = reader, fallbackTitle = file.nameWithoutExtension,
                        maxMessageCount = null,
                        transformImage = mImageStore::stage,
                        onHeader = { side = it.getAsJsonObject(BackupV3.SIDECAR) },
                        onMessage = { message, json ->
                            val row = JSONObject().put("createTime", message.createTime)
                                .put("source", sourceOf(message.role))
                                .put("content", message.content)
                            json.getAsJsonObject(BackupV3.SIDECAR).longValue("id")
                                ?.takeIf { it > 0L }?.let { row.put("id", it) }
                            messages.add(row)
                            if (message.images.isNotEmpty()) images[row] = message.images
                        }
                    )
                }
                // 编辑过的酒馆文件可能只能靠正文匹配角色；确认父记录后才登记消息及图片。
                val character = resolveChatCharacter(archive, side)
                if (character == null) {
                    mReport.warn("${rel(file)}: chat skipped because its character reference is missing or ambiguous")
                    return@at
                }
                val session = restoreChatSession(archive, side)
                link(session, "characterId", character)
                add("chat_sessions", session)
                messages.forEach { row ->
                    link(row, "sessionId", session)
                    add("chat_messages", row)
                }
                images.forEach { (row, attachments) -> restoreChatImages(file, attachments, row) }
                restoreChatSummaries(file, archive, session, messages, side)
            }
        }
    }

    /** 保存旧图片索引供稳定 UUID 复用；实际单聊关系全部由文档重新建立。 */
    private fun prepareChatImages() {
        val relations = mTables["message_images"].orEmpty()
        val original = relations.filter { row -> row.optString("messageType") == "single" }
        mOriginalSingleImages.putAll(original.groupBy { row -> row.optLong("messageId") })
        mTables["message_images"]?.removeAll(original.toSet())
        // 群聊索引继续保留，单聊不能抢占其他关系拥有的文件 UUID。
        mUsedImageUuids.addAll(
            mTables["message_images"].orEmpty().map { row -> row.optString("imageUuid") })
        mFilesByUuid.putAll(mTables["files"].orEmpty().associateBy { row -> row.optString("uuid") })
    }

    /** 明确来源 ID 缺失或歧义时不能改绑；无来源 ID 的新增文档才使用菜单匹配规则。 */
    private fun resolveChatCharacter(archive: ChatArchive, header: JsonObject?): JSONObject? {
        val session = header?.getAsJsonObject("session")
        if (session?.has("characterId") == true) {
            val sourceId = session.longValue("characterId")
            return sourceId?.let { mOriginalCharacterRows[it] }.orEmpty().singleOrNull()
        }
        // 临时 ID 只定位候选位置，不参与指纹或最终数据库关系。
        val candidates = mCharacters.mapIndexed { index, row ->
            mRowGson.fromJson(row.toString(), Character::class.java).copy(id = index + 1L)
        }
        val suggested = ChatCharacterMatcher.suggestCharacterId(archive, candidates) ?: return null
        return mCharacters[(suggested - 1L).toInt()]
    }

    /** 还原会话设置，仅从附加信息读取本地主键。 */
    private fun restoreChatSession(archive: ChatArchive, header: JsonObject?): JSONObject {
        val row = JSONObject()
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
        // 可空作者备注缺省时继续继承角色卡，不写入字符串形式的 null。
        archive.creatorNotes?.let { row.put("creatorNotes", it) }
        val session = header?.getAsJsonObject("session")
        if (mHasPersonas) {
            val id = session.longValue("userPersonaId") ?: mDefaultPersona.getLong("id")
            val persona = mPersonas.getValue(id)
            row.put("userPersonaId", id)
            fun restoreOverride(key: String, value: String?, inherited: String) {
                val override = when (session?.get(key + "Override")?.asString) {
                    "inherit" -> null
                    "empty" -> ""
                    "value" -> value
                    null -> value?.takeUnless { it.isEmpty() || it == inherited }
                    else -> error("Invalid $key override state")
                }
                row.put(key, override ?: JSONObject.NULL)
            }
            restoreOverride(
                "userName",
                archive.userName.takeIf { archive.hasUserName },
                persona.getString("name")
            )
            restoreOverride(
                "userDescription",
                archive.userDescription,
                persona.getString("description")
            )
        }
        session.longValue("id")?.takeIf { it > 0L }
            ?.let { row.put("id", it) }
        return row
    }

    /** 图片内容以 JSONL 为准；未修改的原始关系尽量复用文件索引，避免反复恢复产生新索引。 */
    private fun restoreChatImages(file: File, images: List<ChatArchiveImage>, row: JSONObject) {
        val originals = mOriginalSingleImages[row.optLong("id", 0L)].orEmpty()
        images.forEachIndexed { position, image ->
            val restored = mImageStore.restoredFile(mImageStore.stage(image))
            if (restored == null) {
                mReport.warn("${rel(file)}: an image has no local bytes and was skipped")
                return@forEachIndexed
            }
            // 同消息、同位置、同内容才复用；重复文件仍必须各自持有独立 UUID。
            val original = originals.singleOrNull { relation ->
                relation.optInt("position") == position
                    && mFilesByUuid[relation.optString("imageUuid")]?.optString("hash") == restored.hash
            }
            val reusedUuid = original?.optString("imageUuid")?.takeIf(mUsedImageUuids::add)
            val uuid = reusedUuid ?: restored.uuid.also { newUuid ->
                val fileRow = entityRow(restored)
                add("files", fileRow)
                mFilesByUuid[newUuid] = fileRow
                mUsedImageUuids.add(newUuid)
            }
            mFilesByUuid.getValue(uuid).put("mimeType", restored.mimeType)
            val relation = JSONObject().put("messageType", "single")
                .put("position", position).put("imageUuid", uuid)
            link(relation, "messageId", row)
            add("message_images", relation)
        }
    }

    /** 还原最新与历史摘要，将覆盖边界限定为当前会话中唯一的来源消息。 */
    private fun restoreChatSummaries(
        file: File,
        archive: ChatArchive,
        session: JSONObject,
        messages: List<JSONObject>,
        header: JsonObject?
    ) {
        archive.summary?.let { summary ->
            val row = JSONObject().put("createTime", summary.createTime)
                .put("source", "Summary").put("content", summary.content)
            header.longValue("summaryId")?.takeIf { it > 0L }?.let { row.put("id", it) }
            val covered = messages.getOrNull(summary.coveredMessageIndex)
            if (covered == null) row.put("coveredMessageId", 0L)
            else link(row, "coveredMessageId", covered)
            link(row, "sessionId", session)
            add("chat_messages", row)
        }
        // 较早摘要保留原有快照，但不能把缺失或歧义的来源 ID 留给新数据库复用。
        val older = header?.getAsJsonArray("olderSummaries") ?: return
        if (older.size() == 0) return
        val bySourceId =
            messages.filter { row -> row.has("id") }.groupBy { row -> row.getLong("id") }
        older.forEach { element ->
            val row = JSONObject(element.toString())
            val sourceId = row.optLong("coveredMessageId", 0L)
            val covered = bySourceId[sourceId].orEmpty().singleOrNull()
            if (covered != null) link(row, "coveredMessageId", covered)
            else {
                row.put("coveredMessageId", 0L)
                if (sourceId > 0L) mReport.warn("${rel(file)}: older summary coverage was missing or ambiguous and was cleared")
            }
            link(row, "sessionId", session)
            add("chat_messages", row)
        }
    }

    /** 群聊图片没有消息侧外键，必须显式解析来源并移除缺失或歧义关系。 */
    private fun resolveGroupImages() {
        val relations = mTables["message_images"] ?: return
        relations.toList().forEach { relation ->
            if (relation.optString("messageType") != "group") return@forEach
            val sourceId = relation.optLong("messageId", 0L)
            val message = mOriginalGroupMessageRows[sourceId].orEmpty().singleOrNull()
            // 只登记唯一来源，避免删除群聊后遗留关系附着到复用相同数字的新消息。
            if (message == null) {
                relations.remove(relation)
                mReport.warn("message_images: missing or ambiguous group message $sourceId; relation removed")
            } else link(relation, "messageId", message)
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
