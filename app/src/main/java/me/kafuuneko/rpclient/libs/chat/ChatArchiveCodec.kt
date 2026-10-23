package me.kafuuneko.rpclient.libs.chat

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import me.kafuuneko.rpclient.libs.defaults.DefaultNames
import me.kafuuneko.rpclient.libs.defaults.normalizedUserName
import java.io.Reader
import java.io.StringWriter
import java.io.Writer
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.CancellationException

/**
 * RPClient 与 SillyTavern JSONL 单聊文件之间的编解码器。
 *
 * 第一行遵循 SillyTavern 的 chat header，后续每行保存一条消息；RPClient 无法由酒馆表达的
 * 会话设置和总结放在 `chat_metadata.rpclient` 命名空间，酒馆可忽略这些扩展字段。
 */
class ChatArchiveCodec(
    private val mGson: Gson
) {
    /** 将小型单聊归档编码为可被 SillyTavern 导入的 JSONL 字符串。 */
    fun encode(archive: ChatArchive): String {
        val writtenHashes = mutableSetOf<String>()
        return StringWriter().also { writer ->
            encodeHeader(archive, writer)
            archive.messages.forEach { message ->
                encodeMessage(archive, message, writer, writtenHashes)
            }
        }.toString()
    }

    /**
     * 将归档头直接写入目标字符流。
     *
     * 调用方负责按顺序继续写入消息并管理 writer 生命周期；该边界允许 Repository 分页读取
     * Room，避免大型对话同时驻留为实体列表、完整 JSONL 字符串和 UTF-8 字节数组。
     */
    internal fun encodeHeader(archive: ChatArchive, writer: Writer) {
        writeJsonLine(buildHeader(archive), writer)
    }

    /** 将单条归档消息直接写入目标字符流，不关闭或刷新 writer。 */
    internal fun encodeMessage(
        archive: ChatArchive,
        message: ChatArchiveMessage,
        writer: Writer,
        writtenHashes: MutableSet<String> = mutableSetOf(),
    ) {
        writeJsonLine(buildMessage(archive, message, writtenHashes), writer)
    }

    /** 分段写出含图消息；图片字节由仓库在文件读取保护范围内编码。 */
    internal suspend fun encodeMessageWithImages(
        archive: ChatArchive,
        message: ChatArchiveMessage,
        writer: Writer,
        writeImages: suspend () -> Unit,
    ) {
        val json = buildMessage(archive, message.copy(images = emptyList()))
        val extra = json.remove(KEY_EXTRA).asJsonObject
        // 保留旁白等原有 extra 字段，仅将图片数组交给文件仓库分段写入。
        writer.write(json.toString().dropLast(1))
        writer.write(",\"extra\":")
        writer.write(extra.toString().dropLast(1))
        if (extra.size() > 0) writer.write(",")
        writer.write("\"rpclient\":{\"schema_version\":1,\"images\":[")
        writeImages()
        writer.write("]}}}\n")
    }

    private fun writeJsonLine(json: JsonObject, writer: Writer) {
        mGson.toJson(json, writer)
        writer.write("\n")
    }

    /**
     * 解析 SillyTavern JSONL 或 RPClient 导出的扩展 JSONL。
     *
     * [fallbackTitle] 通常来自系统文档名；[fallbackUserName] 由应用身份设置提供；
     * [fallbackTime] 仅在来源没有可识别时间时使用。
     */
    fun decode(
        jsonl: String,
        fallbackTitle: String,
        fallbackTime: Long = System.currentTimeMillis(),
        fallbackUserName: String = DefaultNames.USER,
    ): ChatArchive {
        return decode(
            reader = jsonl.reader(),
            fallbackTitle = fallbackTitle,
            fallbackTime = fallbackTime,
            fallbackUserName = fallbackUserName,
        )
    }

    /** 从字符流解析；图片回调可立即释放当前 Base64，返回不含大载荷的暂存描述。 */
    fun decode(
        reader: Reader,
        fallbackTitle: String,
        fallbackTime: Long = System.currentTimeMillis(),
        fallbackUserName: String = DefaultNames.USER,
        transformImage: (ChatArchiveImage) -> ChatArchiveImage = { it },
    ): ChatArchive {
        // 对外保持归档格式错误类型稳定；协程取消必须继续交给资源所有者处理。
        val parsed = try {
            parseLines(reader, fallbackTime, transformImage)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid chat archive JSON", error)
        }
        val header = parsed.header
        require(header.isChatHeader()) { "Chat archive header is missing" }
        val metadata = header.objectOrNull(KEY_CHAT_METADATA)
        val rpclient = metadata
            ?.objectOrNull(KEY_RPCLIENT)
            ?.supportedMetadataOrNull()
        val decodedMessages = parsed.messages.normalizeTimes()

        val customCreateTime = rpclient?.longOrNull(KEY_CREATE_TIME)
        val createTime = customCreateTime
            ?: decodedMessages.firstOrNull()?.message?.createTime
            ?: fallbackTime
        val customLatestTime = rpclient?.longOrNull(KEY_LATEST_TIME)
        val latestTime = customLatestTime
            ?: decodedMessages.lastOrNull()?.message?.createTime
            ?: createTime
        val characterObject = rpclient?.objectOrNull(KEY_CHARACTER)
        val characterNameHint = characterObject?.stringOrNull(KEY_NAME)
            .takeUnlessUnused()
            ?: header.stringOrNull(KEY_CHARACTER_NAME).takeUnlessUnused()
            ?: decodedMessages.characterNameHint()
        val userName = rpclient?.stringOrNull(KEY_USER_NAME)
            .takeUnlessUnused()
            ?: decodedMessages.firstOrNull { it.message.role == ChatArchiveMessageRole.User }
                ?.speakerName
                .takeUnlessUnused()
            ?: header.stringOrNull(KEY_USER_NAME).takeUnlessUnused()
            ?: fallbackUserName.normalizedUserName()

        return ChatArchive(
            title = rpclient?.stringOrNull(KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: fallbackTitle.ifBlank { DefaultNames.IMPORTED_CHAT },
            createTime = createTime,
            latestTime = maxOf(latestTime, decodedMessages.lastOrNull()?.message?.createTime ?: createTime),
            userName = userName,
            userDescription = rpclient?.stringOrNull(KEY_USER_DESCRIPTION).orEmpty(),
            userNote = rpclient?.stringOrNull(KEY_USER_NOTE).orEmpty(),
            creatorNotes = rpclient?.stringOrNull(KEY_CREATOR_NOTES),
            lorebookEntrySet = rpclient?.stringOrNull(KEY_LOREBOOK_ENTRY_SET) ?: "[]",
            worldInfoStateJson = rpclient?.stringOrNull(KEY_WORLD_INFO_STATE) ?: "{}",
            autoSummaryPaused = rpclient?.booleanOrNull(KEY_AUTO_SUMMARY_PAUSED) ?: false,
            characterNameHint = characterNameHint.orEmpty(),
            characterFingerprint = characterObject?.stringOrNull(KEY_FINGERPRINT),
            messages = decodedMessages.map { it.message },
            summary = rpclient
                ?.objectOrNull(KEY_SUMMARY)
                ?.toSummary(createTime)
                ?.takeIf { summary -> summary.hasValidBoundary(decodedMessages.size) },
        )
    }

    private fun buildHeader(archive: ChatArchive): JsonObject {
        val rpclient = JsonObject().apply {
            addProperty(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            addProperty(KEY_TITLE, archive.title)
            addProperty(KEY_CREATE_TIME, archive.createTime)
            addProperty(KEY_LATEST_TIME, archive.latestTime)
            addProperty(KEY_USER_NAME, archive.userName)
            addProperty(KEY_USER_DESCRIPTION, archive.userDescription)
            addProperty(KEY_USER_NOTE, archive.userNote)
            archive.creatorNotes?.let { text -> addProperty(KEY_CREATOR_NOTES, text) }
            addProperty(KEY_LOREBOOK_ENTRY_SET, archive.lorebookEntrySet)
            addProperty(KEY_WORLD_INFO_STATE, archive.worldInfoStateJson)
            addProperty(KEY_AUTO_SUMMARY_PAUSED, archive.autoSummaryPaused)
            add(
                KEY_CHARACTER,
                JsonObject().apply {
                    addProperty(KEY_NAME, archive.characterNameHint)
                    archive.characterFingerprint?.let { text -> addProperty(KEY_FINGERPRINT, text) }
                },
            )
            archive.summary?.let { summary ->
                add(
                    KEY_SUMMARY,
                    JsonObject().apply {
                        addProperty(KEY_CONTENT, summary.content)
                        addProperty(KEY_CREATE_TIME, summary.createTime)
                        addProperty(KEY_COVERED_MESSAGE_INDEX, summary.coveredMessageIndex)
                    },
                )
            }
        }
        return JsonObject().apply {
            addProperty(KEY_USER_NAME, UNUSED_NAME)
            addProperty(KEY_CHARACTER_NAME, UNUSED_NAME)
            add(
                KEY_CHAT_METADATA,
                JsonObject().apply {
                    add(KEY_RPCLIENT, rpclient)
                },
            )
        }
    }

    private fun buildMessage(
        archive: ChatArchive,
        message: ChatArchiveMessage,
        writtenHashes: MutableSet<String> = mutableSetOf(),
    ): JsonObject {
        val isUser = message.role == ChatArchiveMessageRole.User
        val isNarrator = message.role == ChatArchiveMessageRole.Narrator
        return JsonObject().apply {
            addProperty(
                KEY_NAME,
                when {
                    isUser -> archive.userName
                    isNarrator -> NARRATOR_NAME
                    else -> archive.characterNameHint
                },
            )
            addProperty(KEY_IS_USER, isUser)
            // SillyTavern 的 is_system 表示 UI 系统消息；旁白使用 extra.type 表达。
            addProperty(KEY_IS_SYSTEM, false)
            addProperty(KEY_SEND_DATE, Instant.ofEpochMilli(message.createTime).toString())
            addProperty(KEY_MESSAGE, message.content)
            add(
                KEY_EXTRA,
                JsonObject().apply {
                    if (isNarrator) addProperty(KEY_TYPE, NARRATOR_TYPE)
                    if (message.images.isNotEmpty()) add(
                        KEY_RPCLIENT,
                        ChatArchiveImageCodec.encode(
                            images = message.images,
                            writtenHashes = writtenHashes,
                        ),
                    )
                },
            )
        }
    }

    private fun parseLines(
        reader: Reader,
        fallbackTime: Long,
        transformImage: (ChatArchiveImage) -> ChatArchiveImage,
    ): ParsedArchiveLines {
        var header: JsonObject? = null
        val messages = mutableListOf<DecodedMessage>()
        var messageObjectCount = 0
        val resolver = ChatArchiveImageCodec.Resolver(transformImage)
        // Gson 按对象消费字符流，避免 readLine 先复制整条含图消息。
        val input = JsonReader(reader.buffered()).apply { strictness = Strictness.LENIENT }
        while (input.peek() != JsonToken.END_DOCUMENT) {
            if (header == null) {
                header = JsonParser.parseReader(input).asJsonObject
                continue
            }
            require(messageObjectCount < MAX_MESSAGE_COUNT) { "Chat archive has too many messages" }
            val (json, images) = readMessage(input, resolver::resolve)
            decodeMessage(json, fallbackTime + messageObjectCount)?.let { decoded ->
                messages.add(
                    element = decoded.copy(
                        message = decoded.message.copy(
                            images = images ?: decoded.message.images.map(resolver::resolve),
                        ),
                    ),
                )
            }
            messageObjectCount += 1
        }
        return ParsedArchiveLines(
            header = requireNotNull(header) { "Chat archive is empty" },
            messages = messages,
        )
    }

    /** 流式提取自有扩展图片，其他酒馆消息字段仍交给原有解码逻辑。 */
    private fun readMessage(
        reader: JsonReader,
        transformImage: (ChatArchiveImage) -> ChatArchiveImage,
    ): Pair<JsonObject, List<ChatArchiveImage>?> {
        var images: List<ChatArchiveImage>? = null
        // 仅遍历已知两层扩展；任意正文和未知扩展不被当作图片协议解释。
        fun readObject(isExtra: Boolean): JsonObject {
            val result = JsonObject()
            reader.beginObject()
            // 不接受重复键，避免后一个扩展覆盖已经暂存的图片及其归属。
            while (reader.hasNext()) {
                val name = reader.nextName()
                require(!result.has(name)) { "Duplicate archive message field" }
                val objectValue = reader.peek() == JsonToken.BEGIN_OBJECT
                val value = when {
                    !isExtra && name == KEY_EXTRA && objectValue -> readObject(isExtra = true)
                    isExtra && name == KEY_RPCLIENT && objectValue -> {
                        val extension = ChatArchiveImageCodec.readExtension(reader, transformImage)
                        images = extension.second
                        extension.first
                    }
                    else -> JsonParser.parseReader(reader)
                }
                result.add(name, value)
            }
            reader.endObject()
            return result
        }
        return readObject(isExtra = false) to images
    }

    private fun decodeMessage(json: JsonObject, fallbackTime: Long): DecodedMessage? {
        val content = json.stringOrNull(KEY_MESSAGE) ?: return null
        val extraType = json.objectOrNull(KEY_EXTRA)?.stringOrNull(KEY_TYPE)
        val role = when {
            extraType.equals(other = NARRATOR_TYPE, ignoreCase = true) -> ChatArchiveMessageRole.Narrator
            json.booleanOrNull(KEY_IS_USER) == true -> ChatArchiveMessageRole.User
            json.booleanOrNull(KEY_IS_SYSTEM) == true -> return null
            else -> ChatArchiveMessageRole.Character
        }
        return DecodedMessage(
            message = ChatArchiveMessage(
                createTime = json.elementOrNull(KEY_SEND_DATE).toTimestampOrNull() ?: fallbackTime,
                role = role,
                content = content,
                images = ChatArchiveImageCodec.decode(json.objectOrNull(KEY_EXTRA)),
            ),
            speakerName = json.stringOrNull(KEY_NAME).orEmpty(),
        )
    }

    private fun List<DecodedMessage>.normalizeTimes(): List<DecodedMessage> {
        var previousTime = Long.MIN_VALUE
        return map { decoded ->
            val normalizedTime = if (decoded.message.createTime > previousTime) {
                decoded.message.createTime
            } else {
                require(previousTime < Long.MAX_VALUE) {
                    "Chat archive message timestamps cannot be normalized"
                }
                previousTime + 1L
            }
            previousTime = normalizedTime
            decoded.copy(message = decoded.message.copy(createTime = normalizedTime))
        }
    }

    private fun List<DecodedMessage>.characterNameHint(): String? {
        return asSequence()
            .filter { it.message.role == ChatArchiveMessageRole.Character }
            .map { it.speakerName.trim() }
            .filter { it.isNotBlank() && !it.equals(other = UNUSED_NAME, ignoreCase = true) }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
    }

    private fun JsonObject.isChatHeader(): Boolean {
        if (has(KEY_CHAT_METADATA)) return true
        if (has(KEY_MESSAGE)) return false
        return has(KEY_USER_NAME) || has(KEY_CHARACTER_NAME)
    }

    private fun JsonObject.toSummary(defaultTime: Long): ChatArchiveSummary {
        return ChatArchiveSummary(
            content = stringOrNull(KEY_CONTENT).orEmpty(),
            createTime = longOrNull(KEY_CREATE_TIME) ?: defaultTime,
            coveredMessageIndex = intOrNull(KEY_COVERED_MESSAGE_INDEX) ?: -1,
        )
    }

    /** 仅解释当前协议元数据；未来主版本必须停止导入，旧式无版本扩展按普通酒馆文件处理。 */
    private fun JsonObject.supportedMetadataOrNull(): JsonObject? {
        val schemaVersion = intOrNull(KEY_SCHEMA_VERSION) ?: return null
        require(schemaVersion <= SCHEMA_VERSION) {
            "Unsupported RPClient chat archive schema version: $schemaVersion"
        }
        return takeIf { schemaVersion == SCHEMA_VERSION }
    }

    /** 摘要只允许指向已有普通消息，-1 表示没有覆盖任何消息。 */
    private fun ChatArchiveSummary.hasValidBoundary(messageCount: Int): Boolean {
        return coveredMessageIndex == -1 || coveredMessageIndex in 0 until messageCount
    }

    private fun JsonObject.elementOrNull(key: String): JsonElement? {
        return get(key)?.takeUnless { it.isJsonNull }
    }

    private fun JsonObject.objectOrNull(key: String): JsonObject? {
        return elementOrNull(key)?.takeIf { it.isJsonObject }?.asJsonObject
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        return elementOrNull(key)?.takeIf { it.isJsonPrimitive }?.asString
    }

    private fun JsonObject.longOrNull(key: String): Long? {
        return elementOrNull(key)?.takeIf { it.isJsonPrimitive }?.let { element ->
            runCatching { element.asLong }.getOrNull()
        }
    }

    private fun JsonObject.intOrNull(key: String): Int? {
        return elementOrNull(key)?.takeIf { it.isJsonPrimitive }?.let { element ->
            runCatching { element.asInt }.getOrNull()
        }
    }

    private fun JsonObject.booleanOrNull(key: String): Boolean? {
        return elementOrNull(key)?.takeIf { it.isJsonPrimitive }?.asBoolean
    }

    private fun JsonElement?.toTimestampOrNull(): Long? {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive.isNumber) {
            return primitive.asLong.normalizeEpoch()
        }
        val value = primitive.asString.trim()
        value.toLongOrNull()?.let { return it.normalizeEpoch() }
        runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()?.let { return it }
        runCatching { OffsetDateTime.parse(value)
            .toInstant()
            .toEpochMilli() }
            .getOrNull()
            ?.let { return it }
        runCatching {
            LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()?.let { return it }
        return LegacyTimestampRegex.matchEntire(value)?.destructured?.let { destructured ->
            val (year, month, day, hour, minute, second, millis) = destructured
            runCatching {
                LocalDateTime.of(
                    year.toInt(),
                    month.toInt(),
                    day.toInt(),
                    hour.toInt(),
                    minute.toInt(),
                    second.toInt(),
                    millis.toInt() * 1_000_000,
                )
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull()
        }
    }

    private fun Long.normalizeEpoch(): Long {
        return if (this in -SECONDS_EPOCH_THRESHOLD..SECONDS_EPOCH_THRESHOLD) {
            this * 1_000L
        } else {
            this
        }
    }

    private fun String?.takeUnlessUnused(): String? {
        return this?.trim()?.takeIf { text ->
            text.isNotBlank() && !text.equals(other = UNUSED_NAME, ignoreCase = true)
        }
    }

    private data class DecodedMessage(
        val message: ChatArchiveMessage,
        val speakerName: String
    )

    private data class ParsedArchiveLines(
        val header: JsonObject,
        val messages: List<DecodedMessage>
    )

    private companion object {
        const val SCHEMA_VERSION = 1
        const val MAX_MESSAGE_COUNT = 100_000
        const val SECONDS_EPOCH_THRESHOLD = 100_000_000_000L
        const val UNUSED_NAME = "unused"
        const val NARRATOR_NAME = "Narrator"
        const val NARRATOR_TYPE = "narrator"

        const val KEY_CHAT_METADATA = "chat_metadata"
        const val KEY_RPCLIENT = "rpclient"
        const val KEY_SCHEMA_VERSION = "schema_version"
        const val KEY_TITLE = "title"
        const val KEY_CREATE_TIME = "create_time"
        const val KEY_LATEST_TIME = "latest_time"
        const val KEY_USER_NAME = "user_name"
        const val KEY_USER_DESCRIPTION = "user_description"
        const val KEY_USER_NOTE = "user_note"
        const val KEY_CREATOR_NOTES = "creator_notes"
        const val KEY_LOREBOOK_ENTRY_SET = "lorebook_entry_set"
        const val KEY_WORLD_INFO_STATE = "world_info_state_json"
        const val KEY_AUTO_SUMMARY_PAUSED = "auto_summary_paused"
        const val KEY_CHARACTER = "character"
        const val KEY_CHARACTER_NAME = "character_name"
        const val KEY_FINGERPRINT = "fingerprint"
        const val KEY_SUMMARY = "summary"
        const val KEY_CONTENT = "content"
        const val KEY_COVERED_MESSAGE_INDEX = "covered_message_index"
        const val KEY_NAME = "name"
        const val KEY_IS_USER = "is_user"
        const val KEY_IS_SYSTEM = "is_system"
        const val KEY_SEND_DATE = "send_date"
        const val KEY_MESSAGE = "mes"
        const val KEY_EXTRA = "extra"
        const val KEY_TYPE = "type"

        val LegacyTimestampRegex = Regex(
            pattern = """^(\d{4})-(\d{1,2})-(\d{1,2})\s*@?(\d{1,2})h\s+(\d{1,2})m\s+(\d{1,2})s\s+(\d{1,3})ms$""",
        )
    }
}
