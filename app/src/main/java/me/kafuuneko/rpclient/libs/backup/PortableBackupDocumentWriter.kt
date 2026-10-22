package me.kafuuneko.rpclient.libs.backup

import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFiles.writeJsonLine
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFiles.writeText
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFormat.TABLE_ROWS_KEY
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFormat.VERSION
import me.kafuuneko.rpclient.libs.backup.BackupDatabaseCodec.readRows
import me.kafuuneko.rpclient.libs.backup.BackupDatabaseCodec.tableRows
import org.json.JSONArray
import org.json.JSONObject

/** 单次导出的功能文档写入器，只持有当前数据库快照和暂存目录。 */
internal class PortableBackupDocumentWriter(
    private val mDatabase: SupportSQLiteDatabase,
    private val mTables: List<String>,
    private val mRoot: File
) {
    private val mOwnedTables = mutableSetOf<String>()
    private fun own(vararg names: String) {
        names.filter { it in mTables }.forEach { mOwnedTables.add(it) }
    }
    private fun rows(table: String): JSONArray = if (table in mTables) readRows(mDatabase, table) else JSONArray()
    private fun filtered(table: String, column: String, value: Any?): JSONArray =
        if (table in mTables) readRows(mDatabase, table, column, value) else JSONArray()

    /** 按功能输出完整数据行，并将剩余表写入兼容文档。 */
    fun writeAll() {
        writeCharacters()
        writeStories()
        writeChats()
        writeGroups()
        writeWorldbooks()
        writeProviders()
        writeRegex()
        writeUsageAndFiles()
        writeRemainingTables()
    }

    /** 保存角色卡和模型关联的可读文档。 */
    private fun writeCharacters() {
        // 角色可读字段与无损原始行同时输出，保留模型关联。
        val characterRows = rows("character")
        own("character")
        characterRows.objects().forEach { character ->
            val id = character.optLong("id")
            val relations = filtered("character_llm_provider_associations", "characterId", id)
            writeText(File(mRoot, "characters/character-" + id + ".json"),
                JSONObject()
                    .put("format", "rpclient-character")
                    .put("version", VERSION)
                    .put("character", character)
                    .put("providerAssociations", relations)
                    .put(TABLE_ROWS_KEY, tableRows("character" to JSONArray().put(character)))
                    .toString(2))
        }
    }

    /** 保存故事结构和无损关系扩展，维持 V2 故事归档格式。 */
    private fun writeStories() {
        // 章节按故事和分卷整理，关系行仍按原表保存以支持精确恢复。
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
            writeText(File(mRoot, "stories/story-" + id + ".rpstory.json"),
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
    }

    /** 逐会话保存单聊消息，沿用时间和 ID 的稳定顺序。 */
    private fun writeChats() {
        // JSONL 先写会话元数据，再写每条消息及其原始行。
        val chatSessions = rows("chat_sessions")
        own("chat_sessions")
        chatSessions.objects().forEach { session ->
            val id = session.optLong("id")
            val messages = filtered("chat_messages", "sessionId", id).objects()
                .sortedWith(compareBy<JSONObject> { it.optLong("createTime") }.thenBy { it.optLong("id") })
            own("chat_messages")
            File(mRoot, "chats/conversation-" + id + ".jsonl").bufferedWriter(Charsets.UTF_8).use { writer ->
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
    }

    /** 保存群聊成员、消息与摘要覆盖关系。 */
    private fun writeGroups() {
        // 群成员与消息快照保持独立原始行，避免展示结构丢失关联信息。
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
            File(mRoot, "group-chats/group-" + id + ".jsonl").bufferedWriter(Charsets.UTF_8).use { writer ->
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
    }

    /** 保存世界书和条目，保持排序规则。 */
    private fun writeWorldbooks() {
        // 按世界书归集条目，仍保存完整表字段。
        val worldbooks = rows("lorebooks")
        own("lorebooks")
        val worldbookEntries = rows("lorebook_entries")
        own("lorebook_entries")
        worldbooks.objects().forEach { book ->
            val id = book.optLong("id")
            val entries = worldbookEntries.objects().filter { it.optLong("lorebookId") == id }
                .sortedWith(compareBy<JSONObject> { it.optInt("order") }.thenBy { it.optLong("id") })
                .toJsonArray()
            writeText(File(mRoot, "worldbooks/worldbook-" + id + ".json"),
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
    }

    /** 保存模型配置和角色关联，归档明确包含凭据。 */
    private fun writeProviders() {
        // 供应商凭据属于明文归档内容，入口和文档已提示此边界。
        val associations = rows("character_llm_provider_associations")
        own("character_llm_provider_associations")
        val providerRows = rows("llm_providers")
        own("llm_providers")
        writeText(File(mRoot, "providers/provider-configurations.json"),
            JSONObject()
                .put("format", "rpclient-provider-configurations")
                .put("version", VERSION)
                .put("providers", providerRows)
                .put("characterAssociations", associations)
                .put(TABLE_ROWS_KEY, tableRows(
                    "llm_providers" to providerRows,
                    "character_llm_provider_associations" to associations
                )).toString(2))
    }

    /** 保存全局与角色脚本，以及角色授权记录。 */
    private fun writeRegex() {
        // 授权关系与脚本作用域分别保存，恢复时不重新推导授权。
        val scriptRows = rows("regex_scripts")
        own("regex_scripts")
        val globalScripts = scriptRows.objects().filter { it.isNull("characterId") }.toJsonArray()
        writeText(File(mRoot, "regex/global-scripts.json"),
            JSONObject()
                .put("format", "rpclient-regex-scripts")
                .put("version", VERSION)
                .put("scripts", globalScripts)
                .put(TABLE_ROWS_KEY, tableRows("regex_scripts" to globalScripts))
                .toString(2))
        rows("character").objects().map { it.optLong("id") }.sorted().forEach { characterId ->
            val characterScripts = scriptRows.objects().filter {
                !it.isNull("characterId") && it.optLong("characterId") == characterId
            }.toJsonArray()
            writeText(File(mRoot, "regex/character-scripts/character-" + characterId + ".json"),
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
        writeText(File(mRoot, "regex/character-authorizations.json"),
            JSONObject()
                .put("format", "rpclient-regex-character-authorizations")
                .put("version", VERSION)
                .put("characterIds", JSONArray().apply {
                    authorizations.objects().forEach { put(it.optLong("characterId")) }
                })
                .put(TABLE_ROWS_KEY, tableRows("regex_character_authorizations" to authorizations))
                .toString(2))
    }

    /** 保存用量、图片引用和文件索引。 */
    private fun writeUsageAndFiles() {
        // 附件内容另行复制，此处保存引用关系和用量记录。
        val fileRows = rows("files")
        own("files")
        val tokenRows = rows("llm_token_usage_records")
        own("llm_token_usage_records")
        writeText(File(mRoot, "app-data/token-usage.json"),
            JSONObject()
                .put("format", "rpclient-token-usage")
                .put("version", VERSION)
                .put("records", tokenRows)
                .put(TABLE_ROWS_KEY, tableRows("llm_token_usage_records" to tokenRows))
                .toString(2))

        val images = rows("message_images")
        own("message_images")
        writeText(File(mRoot, "app-data/message-images.json"),
            JSONObject()
                .put("format", "rpclient-message-images")
                .put("version", VERSION)
                .put("images", images)
                .put(TABLE_ROWS_KEY, tableRows("message_images" to images))
                .toString(2))

        writeText(File(mRoot, "app-data/files-index.json"),
            JSONObject()
                .put("format", "rpclient-files-index")
                .put("version", VERSION)
                .put("files", fileRows)
                .put(TABLE_ROWS_KEY, tableRows("files" to fileRows))
                .toString(2))
    }

    /** 保留未单独映射的表，兼容目标新增的背景与搜索数据。 */
    private fun writeRemainingTables() {
        // 显式归属之外的表也必须导出，兼容新增搜索和页面背景数据。
        File(mRoot, "app-data/other-tables.jsonl").bufferedWriter(Charsets.UTF_8).use { writer ->
            (mTables - mOwnedTables).sorted().forEach { table ->
                readRows(mDatabase, table).objects().forEach { row ->
                    writeJsonLine(JSONObject()
                        .put("table", table)
                        .put("row", row)
                        .put(TABLE_ROWS_KEY, tableRows(table to JSONArray().put(row))), writer)
                }
            }
        }
    }
}
