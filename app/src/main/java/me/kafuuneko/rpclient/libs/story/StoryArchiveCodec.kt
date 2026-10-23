package me.kafuuneko.rpclient.libs.story

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.kafuuneko.rpclient.libs.defaults.DefaultNames
import java.io.Reader
import java.io.Writer

/** `.rpstory.json` 的稳定 V2 编解码器，同时兼容缺少章节引导的历史归档。 */
class StoryArchiveCodec(private val mGson: Gson) {
    fun encode(archive: StoryArchive): String {
        validateArchive(archive)
        return mGson.toJson(archive)
    }

    fun encode(archive: StoryArchive, writer: Writer) {
        validateArchive(archive)
        mGson.toJson(archive, writer)
    }

    fun decode(json: String): StoryArchive = decode(json.reader())

    fun decode(reader: Reader): StoryArchive {
        val root = runCatching { JsonParser.parseReader(reader).asJsonObject }
            .getOrElse { throw IllegalArgumentException("Invalid story archive") }
        require(root.requiredString("format") == StoryArchive.FORMAT) {
            "Unsupported story archive format"
        }
        val version = root.requiredInt("version")
        require(version == LEGACY_VERSION || version == StoryArchive.VERSION) {
            "Unsupported story archive version"
        }
        val archive = StoryArchive(
            story = when (version) {
                LEGACY_VERSION -> decodeLegacyStory(root.requiredObject("story"))
                else -> decodeStory(root.requiredObject("story"))
            },
            characterHints = decodeCharacterHints(root),
            lorebookHints = decodeLorebookHints(root),
        )
        validateArchive(archive)
        return archive
    }

    private fun decodeLegacyStory(story: JsonObject): ArchivedStory {
        return ArchivedStory(
            title = story.normalizedTitle("title", DefaultNames.IMPORTED_STORY),
            memory = story.optionalString("memory"),
            summary = story.optionalString("summary"),
            authorNote = story.optionalString("authorNote"),
            includeUserPersona = story.optionalBoolean("includeUserPersona"),
            ungroupedChapters = listOf(
                element = ArchivedChapter(
                    title = DefaultNames.STORY_CHAPTER,
                    content = story.requiredString("content"),
                ),
            ),
        )
    }

    private fun decodeStory(story: JsonObject): ArchivedStory {
        return ArchivedStory(
            title = story.normalizedTitle("title", DefaultNames.IMPORTED_STORY),
            memory = story.optionalString("memory"),
            summary = story.optionalString("summary"),
            authorNote = story.optionalString("authorNote"),
            includeUserPersona = story.optionalBoolean("includeUserPersona"),
            ungroupedChapters = story.requiredArrayObjects("ungroupedChapters")
                .map { it.decodeChapter() },
            volumes = story.requiredArrayObjects("volumes").map { volume ->
                ArchivedVolume(
                    title = volume.requiredNonBlankTitle("title"),
                    chapters = volume.requiredArrayObjects("chapters").map { jsonObject ->
                        jsonObject.decodeChapter()
                    },
                )
            },
        )
    }

    private fun JsonObject.decodeChapter(): ArchivedChapter {
        return ArchivedChapter(
            title = requiredNonBlankTitle("title"),
            content = requiredString("content"),
            continuationGuidance = optionalString("continuationGuidance"),
        )
    }

    private fun decodeCharacterHints(root: JsonObject): List<StoryCharacterHint> {
        return root.arrayObjects("characterHints").map { hint ->
            val mode = hint.requiredString("activationMode").lowercase()
            require(mode == MODE_ALWAYS || mode == MODE_AUTO || mode == MODE_PRIMARY) {
                "Unsupported character activation mode"
            }
            StoryCharacterHint(
                name = hint.requiredString("name"),
                fingerprint = hint.requiredString("fingerprint"),
                activationMode = mode,
            )
        }
    }

    private fun decodeLorebookHints(root: JsonObject): List<StoryLorebookHint> {
        return root.arrayObjects("lorebookHints").map { hint ->
            StoryLorebookHint(
                lorebookName = hint.requiredString("lorebookName"),
                entryName = hint.requiredString("entryName"),
                fingerprint = hint.requiredString("fingerprint"),
            )
        }
    }

    private fun validateArchive(archive: StoryArchive) {
        require(archive.format == StoryArchive.FORMAT && archive.version == StoryArchive.VERSION) {
            "Unsupported story archive version"
        }
        val story = archive.story
        require(story.title.isNotBlank()) { "Story archive contains a blank title" }
        require(story.volumes.size <= MAX_VOLUMES) {
            "Story archive contains too many volumes"
        }
        require(story.chapterCount in 1..MAX_CHAPTERS) {
            "Story archive contains an invalid number of chapters"
        }
        require(story.ungroupedChapters.all { it.title.isNotBlank() }) {
            "Story archive contains a blank chapter title"
        }
        require(
            value = story.volumes.all { volume ->
                volume.title.isNotBlank() && volume.chapters.all { archivedChapter -> archivedChapter.title.isNotBlank() }
            },
        ) {
            "Story archive contains a blank volume or chapter title"
        }
        require(archive.characterHints.size <= MAX_HINTS && archive.lorebookHints.size <= MAX_HINTS) {
            "Story archive contains too many references"
        }
        require(archive.characterHints.count { it.activationMode == MODE_PRIMARY } <= 1) {
            "Story archive contains multiple primary characters"
        }
    }

    private fun JsonObject.requiredObject(key: String): JsonObject {
        return get(key)?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalArgumentException("Story archive is missing $key")
    }

    private fun JsonObject.requiredString(key: String): String {
        return get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            ?: throw IllegalArgumentException("Story archive is missing $key")
    }

    private fun JsonObject.normalizedTitle(key: String, fallback: String): String {
        return requiredString(key).trim().ifBlank { fallback }
    }

    private fun JsonObject.requiredNonBlankTitle(key: String): String {
        return requiredString(key).trim().also { text ->
            require(text.isNotEmpty()) { "Story archive contains a blank $key" }
        }
    }

    private fun JsonObject.optionalString(key: String): String {
        val value = get(key) ?: return ""
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            "Story archive contains invalid $key"
        }
        return value.asString
    }

    private fun JsonObject.requiredInt(key: String): Int {
        return get(key)?.takeIf { element ->
            element.isJsonPrimitive && element.asJsonPrimitive.isNumber
        }?.let { element ->
            runCatching { element.asInt }.getOrNull()
        } ?: throw IllegalArgumentException("Story archive is missing $key")
    }

    private fun JsonObject.optionalBoolean(key: String): Boolean {
        val value = get(key) ?: return false
        require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) {
            "Story archive contains invalid $key"
        }
        return value.asBoolean
    }

    private fun JsonObject.requiredArrayObjects(key: String): List<JsonObject> {
        val value = get(key) ?: throw IllegalArgumentException("Story archive is missing $key")
        return arrayObjects(key, value)
    }

    private fun JsonObject.arrayObjects(key: String): List<JsonObject> {
        val value = get(key) ?: return emptyList()
        return arrayObjects(key, value)
    }

    private fun arrayObjects(key: String, value: JsonElement): List<JsonObject> {
        require(value.isJsonArray) { "Story archive contains invalid $key" }
        return value.asJsonArray.map { element ->
            require(element.isJsonObject) { "Story archive contains invalid $key" }
            element.asJsonObject
        }
    }

    companion object {
        /** 常驻激活模式标识。 */
        const val MODE_ALWAYS = "always"

        /** 自动匹配激活模式标识。 */
        const val MODE_AUTO = "auto"

        /** 主角常驻激活模式标识。 */
        const val MODE_PRIMARY = "primary"

        /** 旧版连续正文归档的协议版本号。 */
        private const val LEGACY_VERSION = 1

        /** 单篇归档允许包含的最大分卷数。 */
        private const val MAX_VOLUMES = 1_000

        /** 单篇归档允许包含的最大章节数。 */
        private const val MAX_CHAPTERS = 10_000

        /** 单篇归档允许包含的最大引用提示数。 */
        private const val MAX_HINTS = 10_000
    }
}
