package me.kafuuneko.rpclient.libs.chat

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.Reader
import java.io.StringWriter
import java.io.Writer
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.CancellationException
import me.kafuuneko.rpclient.libs.defaults.DefaultNames
import me.kafuuneko.rpclient.libs.defaults.normalizedUserName

/**
 * RPClient 与 SillyTavern JSONL 单聊文件之间的编解码器。
 *
 * 第一行遵循 SillyTavern 的 chat header，后续每行保存一条消息；RPClient 无法由酒馆表达的
 * 会话设置放在 `chat_metadata.rpclient` 命名空间；总结同时使用酒馆的 `extra.memory`
 * 表达覆盖位置，自有头部保留兼容旧归档与完整备份所需的信息。
 * 末条覆盖的摘要遵循酒馆暂不启用的规则，不追加会干扰重新生成的空消息。
 */
class ChatArchiveCodec(
    private val mGson: Gson
) {
    /** 将小型单聊归档编码为可被 SillyTavern 导入的 JSONL 字符串。 */
    fun encode(archive: ChatArchive): String {
        val writtenHashes = mutableSetOf<String>()
        return StringWriter().also { writer ->
            encodeHeader(archive, writer, summaryInMessages = true)
            archive.messages.forEachIndexed { index, message ->
                encodeMessage(archive, message, writer, writtenHashes, messageIndex = index)
            }
        }.toString()
    }

    /**
     * 将归档头直接写入目标字符流。
     *
     * 调用方负责按顺序继续写入消息并管理 writer 生命周期；该边界允许 Repository 分页读取
     * Room，避免大型对话同时驻留为实体列表、完整 JSONL 字符串和 UTF-8 字节数组。
     */
    internal fun encodeHeader(
        archive: ChatArchive,
        writer: Writer,
        summaryInMessages: Boolean = false,
    ) {
        writeJsonLine(buildHeader(archive, summaryInMessages), writer)
    }

    /** 将单条归档消息直接写入目标字符流，不关闭或刷新 writer。 */
    internal fun encodeMessage(
        archive: ChatArchive,
        message: ChatArchiveMessage,
        writer: Writer,
        writtenHashes: MutableSet<String> = mutableSetOf(),
        messageIndex: Int? = null,
        addFields: JsonObject.() -> Unit = {},
    ) {
        val json = buildMessage(archive, message, writtenHashes)
        attachSummary(json, archive, messageIndex)
        writeJsonLine(json.apply(addFields), writer)
    }

    /** 分段写出含图消息；图片字节由仓库在文件读取保护范围内编码。 */
    internal suspend fun encodeMessageWithImages(
        archive: ChatArchive,
        message: ChatArchiveMessage,
        writer: Writer,
        messageIndex: Int? = null,
        addFields: JsonObject.() -> Unit = {},
        writeImages: suspend () -> Unit,
    ) {
        val json = buildMessage(archive, message.copy(images = emptyList())).apply(addFields)
        attachSummary(json, archive, messageIndex)
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

    /** 仅在真实覆盖消息上写入酒馆摘要，旁白及图片扩展继续保留。 */
    private fun attachSummary(json: JsonObject, archive: ChatArchive, messageIndex: Int?) {
        val summary = archive.summary ?: return
        if (messageIndex == null || summary.coveredMessageIndex != messageIndex
            || summary.content.isBlank()
        ) return
        json.getAsJsonObject(KEY_EXTRA).addProperty(KEY_MEMORY, summary.content)
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
        val messages = mutableListOf<ChatArchiveMessage>()
        val archive = decodeTo(
            reader = reader, fallbackTitle = fallbackTitle, fallbackTime = fallbackTime,
            fallbackUserName = fallbackUserName, maxMessageCount = MAX_MESSAGE_COUNT,
            transformImage = transformImage,
            onMessage = { message, _ -> messages.add(message) }
        )
        return archive.copy(messages = messages)
    }

    /**
     * 逐条交付消息与对应的原始字段，返回只含会话设置和摘要的归档头。
     * - 单聊菜单按对象数限制输入；完整备份由解包阶段限制总字节，并直接消费数据库行。
     * - 角色过滤、时间归一化和摘要边界共用同一轮解析，备份附加字段不会与消息错位。
     */
    internal fun decodeTo(
        reader: Reader,
        fallbackTitle: String,
        transformImage: (ChatArchiveImage) -> ChatArchiveImage,
        fallbackTime: Long = System.currentTimeMillis(),
        fallbackUserName: String = DefaultNames.USER,
        maxMessageCount: Int?,
        onHeader: (JsonObject) -> Unit = {},
        onMessage: (ChatArchiveMessage, JsonObject) -> Unit,
    ): ChatArchive {
        // 对外保持归档格式错误类型稳定；协程取消必须继续交给资源所有者处理。
        val parsed = try {
            parseLines(reader, fallbackTime, transformImage, maxMessageCount, onMessage)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid chat archive JSON", error)
        }
        val header = parsed.header
        onHeader(header)
        val metadata = header.objectOrNull(KEY_CHAT_METADATA)
        val rpclient = metadata
            ?.objectOrNull(KEY_RPCLIENT)
            ?.supportedMetadataOrNull()

        val customCreateTime = rpclient?.longOrNull(KEY_CREATE_TIME)
        val createTime = customCreateTime
            ?: parsed.firstTime
            ?: fallbackTime
        val customLatestTime = rpclient?.longOrNull(KEY_LATEST_TIME)
        val latestTime = customLatestTime
            ?: parsed.lastTime
            ?: createTime
        val characterObject = rpclient?.objectOrNull(KEY_CHARACTER)
        val characterNameHint = characterObject?.stringOrNull(KEY_NAME)
            .takeUnlessUnused()
            ?: header.stringOrNull(KEY_CHARACTER_NAME).takeUnlessUnused()
            ?: parsed.characterNames.maxByOrNull { it.value }?.key
        val userName = rpclient?.stringOrNull(KEY_USER_NAME)?.takeIf(String::isNotEmpty)
            ?: parsed.firstUserName.takeUnlessUnused()
            ?: header.stringOrNull(KEY_USER_NAME).takeUnlessUnused()


        return ChatArchive(
            title = rpclient?.stringOrNull(KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: fallbackTitle.ifBlank { DefaultNames.IMPORTED_CHAT },
            createTime = createTime,
            latestTime = maxOf(
                latestTime,
                parsed.lastTime ?: createTime
            ),
            hasUserName = userName != null,
            userName = userName ?: fallbackUserName.normalizedUserName(),
            userDescription = rpclient?.stringOrNull(KEY_USER_DESCRIPTION).orEmpty(),
            userNote = rpclient?.stringOrNull(KEY_USER_NOTE).orEmpty(),
            creatorNotes = rpclient?.stringOrNull(KEY_CREATOR_NOTES),
            lorebookEntrySet = rpclient?.stringOrNull(KEY_LOREBOOK_ENTRY_SET) ?: "[]",
            worldInfoStateJson = rpclient?.stringOrNull(KEY_WORLD_INFO_STATE) ?: "{}",
            autoSummaryPaused = rpclient?.booleanOrNull(KEY_AUTO_SUMMARY_PAUSED) ?: false,
            includeCharacterExamples = rpclient?.booleanOrNull(KEY_INCLUDE_CHARACTER_EXAMPLES)
                ?: true,
            characterNameHint = characterNameHint.orEmpty(),
            characterFingerprint = characterObject?.stringOrNull(KEY_FINGERPRINT),
            messages = emptyList(),
            summary = resolveSummary(rpclient, parsed, createTime),
        )
    }

    /** 酒馆修改后的逐消息摘要优先；新导出的标记避免清除摘要后又复活旧头部副本。 */
    private fun resolveSummary(
        rpclient: JsonObject?,
        parsed: ParsedArchiveLines,
        createTime: Long,
    ): ChatArchiveSummary? {
        // 旧归档及完整备份仍可从自有头部恢复，标准字段有变化时采用酒馆的当前值。
        val native = rpclient?.objectOrNull(KEY_SUMMARY)?.toSummary(createTime)
            ?.takeIf { it.hasValidBoundary(parsed.messageCount) }
        val memory = parsed.summary
        if (memory != null) {
            return native?.takeIf {
                it.content == memory.content && it.coveredMessageIndex == memory.coveredMessageIndex
            } ?: memory
        }
        if (rpclient?.booleanOrNull(KEY_SUMMARY_IN_MESSAGES) == true
            && native?.content?.isNotBlank() == true
        ) {
            // 自有导出的末条摘要需双份正文和边界一致才恢复；标准字段被清除后不能复活旧头部。
            return native.takeIf { parsed.matchesFinalMessageSummary(it) }
        }
        return native
    }

    private fun buildHeader(archive: ChatArchive, summaryInMessages: Boolean): JsonObject {
        // 本地附加信息保留字段是否显式存在，导入时据此判断覆盖或继承。
        val rpclient = JsonObject().apply {
            addProperty(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            val hasMessageSummary = archive.summary?.let {
                it.content.isNotBlank() && it.coveredMessageIndex >= 0
            } == true
            if (summaryInMessages && hasMessageSummary) addProperty(KEY_SUMMARY_IN_MESSAGES, true)
            addProperty(KEY_TITLE, archive.title)
            addProperty(KEY_CREATE_TIME, archive.createTime)
            addProperty(KEY_LATEST_TIME, archive.latestTime)
            if (archive.hasUserName) addProperty(KEY_USER_NAME, archive.userName)
            addProperty(KEY_USER_DESCRIPTION, archive.userDescription)
            addProperty(KEY_USER_NOTE, archive.userNote)
            archive.creatorNotes?.let { text -> addProperty(KEY_CREATOR_NOTES, text) }
            addProperty(KEY_LOREBOOK_ENTRY_SET, archive.lorebookEntrySet)
            addProperty(KEY_WORLD_INFO_STATE, archive.worldInfoStateJson)
            addProperty(KEY_AUTO_SUMMARY_PAUSED, archive.autoSummaryPaused)
            addProperty(KEY_INCLUDE_CHARACTER_EXAMPLES, archive.includeCharacterExamples)
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
                    isUser -> if (archive.hasUserName) archive.userName else UNUSED_NAME
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
        maxMessageCount: Int?,
        onMessage: (ChatArchiveMessage, JsonObject) -> Unit,
    ): ParsedArchiveLines {
        val input = JsonReader(reader.buffered()).apply { strictness = Strictness.LENIENT }
        require(input.peek() != JsonToken.END_DOCUMENT) { "Chat archive is empty" }
        val header = JsonParser.parseReader(input).asJsonObject
        require(header.isChatHeader()) { "Chat archive header is missing" }
        val parsed = ParsedArchiveLines(header)
        val resolver = ChatArchiveImageCodec.Resolver(transformImage)
        var objectCount = 0
        // 只保留推断会话默认值所需的统计量；正文、图片和来源 ID 在同一回调交付。
        while (input.peek() != JsonToken.END_DOCUMENT) {
            val (json, images) = try {
                readMessage(input) { image ->
                    require(maxMessageCount == null || objectCount < maxMessageCount) {
                        "Chat archive has too many messages"
                    }
                    resolver.resolve(image)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                require(maxMessageCount == null || objectCount < maxMessageCount) {
                    "Chat archive has too many messages"
                }
                throw error
            }
            // 兼容旧版导出的单个末尾系统项，不能放宽普通消息或系统行总量。
            val isFinalSummaryTail = objectCount == maxMessageCount
                && json.booleanOrNull(KEY_IS_SYSTEM) == true
                && json.booleanOrNull(KEY_IS_USER) != true
                && json.stringOrNull(KEY_MESSAGE) == ""
                && json.objectOrNull(KEY_EXTRA)?.stringOrNull(KEY_TYPE) == SUMMARY_TAIL_TYPE
                && input.peek() == JsonToken.END_DOCUMENT
            require(maxMessageCount == null || objectCount < maxMessageCount || isFinalSummaryTail) {
                "Chat archive has too many messages"
            }
            val messageTime = fallbackTime + objectCount
            objectCount++
            val decoded = decodeMessage(json, messageTime)
            val time = decoded?.let(parsed::record) ?: messageTime
            // 被过滤的系统行仍可能携带摘要，边界映射到此前最后一条保留消息。
            parsed.recordMemory(json, time)
            if (decoded == null) continue
            val message = decoded.message.copy(
                createTime = time, images = images ?: decoded.message.images.map(resolver::resolve)
            )
            onMessage(message, json)
        }
        return parsed
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
            extraType.equals(
                other = NARRATOR_TYPE,
                ignoreCase = true
            ) -> ChatArchiveMessageRole.Narrator

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
        runCatching {
            OffsetDateTime.parse(value)
                .toInstant()
                .toEpochMilli()
        }
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

    /** 从已接收消息推断酒馆头部缺省值，名称计数保持首次出现的顺序。 */
    private class ParsedArchiveLines(val header: JsonObject) {
        var messageCount = 0
        var firstTime: Long? = null
        var lastTime: Long? = null
        var firstUserName: String? = null
        val characterNames = mutableMapOf<String, Int>()
        var summary: ChatArchiveSummary? = null
            private set
        private var mPendingSummary: ChatArchiveSummary? = null

        /** 延后一行接纳摘要，与酒馆始终忽略最后一条消息上的摘要保持一致。 */
        fun recordMemory(json: JsonObject, time: Long) {
            // 上一行已确定不是文件末条，现在才允许其摘要成为当前记忆。
            mPendingSummary?.let { summary = it }
            mPendingSummary = null
            val extra = json.get(KEY_EXTRA)?.takeIf { it.isJsonObject }?.asJsonObject
            val memory = extra?.get(KEY_MEMORY)?.takeIf {
                it.isJsonPrimitive && it.asJsonPrimitive.isString
            }?.asString?.takeIf { it.isNotBlank() } ?: return
            if (messageCount == 0) return
            mPendingSummary = ChatArchiveSummary(memory, time, messageCount - 1)
        }

        /**
         * 校验末条消息上的摘要是否仍与自有头部一致，供无尾项归档原样回导。
         * @param nativeSummary 已校验覆盖边界的自有摘要。
         * @return 末条消息仍保留相同正文与覆盖位置时为 true。
         */
        fun matchesFinalMessageSummary(nativeSummary: ChatArchiveSummary): Boolean {
            val pending = mPendingSummary ?: return false
            return nativeSummary.coveredMessageIndex == messageCount - 1
                && pending.coveredMessageIndex == nativeSummary.coveredMessageIndex
                && pending.content == nativeSummary.content
        }

        /** 归一化消息时间，并更新角色与用户名称候选。 */
        fun record(decoded: DecodedMessage): Long {
            val previous = lastTime ?: Long.MIN_VALUE
            val time = if (decoded.message.createTime > previous) decoded.message.createTime else {
                require(previous < Long.MAX_VALUE) { "Chat archive message timestamps cannot be normalized" }
                previous + 1L
            }
            if (firstTime == null) firstTime = time
            lastTime = time
            messageCount++
            // 第一个用户的空名称也占位，保持旧文件的身份回退顺序。
            when (decoded.message.role) {
                ChatArchiveMessageRole.User -> if (firstUserName == null) firstUserName =
                    decoded.speakerName

                ChatArchiveMessageRole.Character -> {
                    val name = decoded.speakerName.trim()
                    if (name.isNotBlank() && !name.equals(UNUSED_NAME, ignoreCase = true)) {
                        characterNames[name] = (characterNames[name] ?: 0) + 1
                    }
                }

                ChatArchiveMessageRole.Narrator -> Unit
            }
            return time
        }
    }

    private companion object {
        const val SCHEMA_VERSION = 1
        const val MAX_MESSAGE_COUNT = 100_000
        const val SECONDS_EPOCH_THRESHOLD = 100_000_000_000L
        const val UNUSED_NAME = "unused"
        const val NARRATOR_NAME = "Narrator"
        const val NARRATOR_TYPE = "narrator"
        const val SUMMARY_TAIL_TYPE = "rpclient_summary_tail"

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
        const val KEY_INCLUDE_CHARACTER_EXAMPLES = "include_character_examples"
        const val KEY_CHARACTER = "character"
        const val KEY_CHARACTER_NAME = "character_name"
        const val KEY_FINGERPRINT = "fingerprint"
        const val KEY_SUMMARY = "summary"
        const val KEY_SUMMARY_IN_MESSAGES = "summary_in_messages"
        const val KEY_MEMORY = "memory"
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
