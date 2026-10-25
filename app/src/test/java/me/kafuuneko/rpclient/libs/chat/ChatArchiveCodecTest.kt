package me.kafuuneko.rpclient.libs.chat

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Reader

class ChatArchiveCodecTest {
    private val codec = ChatArchiveCodec(Gson())

    /** 图片扩展只属于 RPClient，正文及酒馆字段不能混入图片载荷。 */
    @Test
    fun embeddedImagesUsePrivateNamespaceAndSurviveRoundTrip() {
        val source = codec.decode(
            jsonl = """
            {"chat_metadata":{}}
            {"name":"Alice","is_user":true,"mes":"","extra":{"rpclient":{"schema_version":1,"images":[{"mime_type":"image/png","data":"AQID"},{"mime_type":"image/jpeg","data":"BAU="}]}}}
        """.trimIndent(),
            fallbackTitle = "Images",
        )
        val encoded = codec.encode(source)
        val message = JsonParser.parseString(
            encoded
                .lineSequence()
                .drop(1)
                .first(),
        ).asJsonObject
        val extra = message.getAsJsonObject("extra")
        assertEquals("", message["mes"].asString)
        assertFalse(extra.has("image"))
        assertFalse(extra.has("media"))
        assertEquals(
            2,
            extra
                .getAsJsonObject("rpclient")
                .getAsJsonArray("images")
                .size(),
        )
        val restored = codec.decode(encoded, "Images")
        assertEquals(
            source.messages.single().images.map { it.data },
            restored.messages.single().images.map { it.data })
        assertTrue(restored.messages.single().images.all { it.hash?.length == 64 })
        assertEquals(encoded, codec.encode(restored))
    }

    /** 新版数组、旧版单图及滑动列表分别解析，非图片附件不混入。 */
    @Test
    fun nativeSillyTavernImagesKeepOrderAndCustomImagesTakePrecedence() {
        val archive = codec.decode(
            jsonl = """
            {"chat_metadata":{}}
            {"mes":"new","extra":{"media":[{"type":"image","url":"user/images/A/a.png"},{"type":"audio","url":"a.wav"},{"type":"image","url":"user/images/A/b.png"}],"image":"ignored.png"}}
            {"mes":"old","extra":{"image":"a.png","image_swipes":["a.png","b.png"]}}
            {"mes":"single","extra":{"image":"one.png"}}
            {"mes":"inline","extra":{"media":[{"type":"image","url":"data:image/png;base64,AQID"}]}}
            {"mes":"custom","extra":{"image":"ignored.png","rpclient":{"schema_version":1,"images":[]}}}
        """.trimIndent(),
            fallbackTitle = "Images",
        )
        assertEquals(
            listOf("user/images/A/a.png", "user/images/A/b.png"),
            archive.messages[0].images.map { it.sourceUrl },
        )
        assertEquals(listOf("a.png", "b.png"), archive.messages[1].images.map { it.sourceUrl })
        assertEquals("one.png", archive.messages[2].images.single().sourceUrl)
        assertEquals(ChatArchiveImage("image/png", "AQID"), archive.messages[3].images.single())
        assertTrue(archive.messages[4].images.isEmpty())
    }

    /** 未知主版本或不完整的自有图片不能悄悄降级为纯文字导入。 */
    @Test
    fun malformedAndFutureImageExtensionsFailExplicitly() {
        for (extension in listOf(
            """{"schema_version":2,"images":[]}""",
            """{"schema_version":1,"images":[{"mime_type":"image/png"}]}""",
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                codec.decode(
                    "{\"chat_metadata\":{}}\n{\"mes\":\"\",\"extra\":{\"rpclient\":$extension}}",
                    "Images"
                )
            }
        }
    }

    /** 第二张大图尚未读完时，第一张必须已交给暂存回调，避免整行图片常驻内存。 */
    @Test
    fun customImagePayloadsAreTransformedIncrementallyRegardlessOfFieldOrder() {
        val data = "A".repeat(8192)
        val json = """{"chat_metadata":{}}
            {"extra":{"rpclient":{"images":[{"data":"$data","mime_type":"image/png"},{"mime_type":"image/png","data":"$data"}],"schema_version":1}},"mes":""}
        """.trimIndent()
        var transformed = 0
        var position = 0
        val reader = object : Reader() {
            override fun read(buffer: CharArray, offset: Int, length: Int): Int {
                if (position >= json.length) return -1
                if (position > 12_000) assertTrue(
                    "First image must already be staged",
                    transformed > 0
                )
                val count = minOf(a = length, b = 128, c = json.length - position)
                json.toCharArray(buffer, offset, position, position + count)
                position += count
                return count
            }

            override fun close() = Unit
        }
        val archive = codec.decode(
            reader = reader,
            fallbackTitle = "Images",
            transformImage = { image ->
                transformed++
                image.copy(data = null, resourceKey = "staged-$transformed")
            },
        )
        assertEquals(2, transformed)
        assertEquals(
            listOf("staged-1", "staged-2"),
            archive.messages.single().images.map { it.resourceKey })
        assertTrue(archive.messages.single().images.all { it.data == null })
    }

    /** URL 解码与目录层级共同参与匹配；拒绝穿越和跨角色同名误配。 */
    @Test
    fun externalImagePathsMatchSelectedRootsWithoutGuessingAcrossCharacters() {
        val paths = setOf("images/A/a b.png", "A/a b.png", "a b.png")
        assertEquals(
            "images/A/a b.png",
            ChatArchiveImageStore.matchingPath("/user/images/A/a%20b.png?x=1", paths)
        )
        assertEquals(
            "A/a b.png",
            ChatArchiveImageStore.matchingPath(
                source = "https://example.invalid/user/images/A/a b.png",
                paths = paths - "images/A/a b.png",
            ),
        )
        assertNull(ChatArchiveImageStore.matchingPath("/user/images/B/a b.png", setOf("A/a b.png")))
        assertNull(ChatArchiveImageStore.matchingPath("/user/%2e%2e/a.png", setOf("a.png")))
        assertNull(ChatArchiveImageStore.matchingPath("/user/A%5ca.png", setOf("a.png")))
    }

    @Test
    fun rpclientArchiveRoundTripsThroughSillyTavernJsonl() {
        val archive = ChatArchive(
            title = "Investigation",
            createTime = 1_000L,
            latestTime = 3_000L,
            userName = "Alice",
            userDescription = "Detective",
            userNote = "Private note",
            creatorNotes = "Session notes",
            lorebookEntrySet = "[1,2]",
            worldInfoStateJson = """{"sticky":true}""",
            autoSummaryPaused = true,
            includeCharacterExamples = false,
            characterNameHint = "Seraphina",
            characterFingerprint = "fingerprint",
            messages = listOf(
                ChatArchiveMessage(
                    createTime = 1_000L,
                    role = ChatArchiveMessageRole.User,
                    content = "Hello\n\"there\"",
                ),
                ChatArchiveMessage(
                    createTime = 2_000L,
                    role = ChatArchiveMessageRole.Character,
                    content = "Welcome.",
                ),
                ChatArchiveMessage(
                    createTime = 3_000L,
                    role = ChatArchiveMessageRole.Narrator,
                    content = "The lights dim.",
                ),
            ),
            summary = ChatArchiveSummary(
                content = "Alice arrived.",
                createTime = 4_000L,
                coveredMessageIndex = 1,
            ),
        )

        val encoded = codec.encode(archive)
        val lines = encoded.lineSequence().filter { it.isNotBlank() }.toList()
        val header = JsonParser.parseString(lines.first()).asJsonObject
        val narrator = JsonParser.parseString(lines.last()).asJsonObject

        assertEquals("unused", header["user_name"].asString)
        assertTrue(header["chat_metadata"].asJsonObject.has("rpclient"))
        assertFalse(narrator["is_system"].asBoolean)
        assertEquals("narrator", narrator["extra"].asJsonObject["type"].asString)

        val decoded =
            codec.decode(jsonl = encoded, fallbackTitle = "Fallback", fallbackTime = 9_000L)

        assertEquals(archive, decoded)
    }

    @Test
    fun sillyTavernArchiveUsesMessageNamesAndSkipsSystemUiMessages() {
        val jsonl = """
            ﻿{"user_name":"unused","character_name":"unused","chat_metadata":{}}
            {"name":"Alice","is_user":true,"is_system":false,"send_date":"2026-07-28T10:00:00Z","mes":"Hello"}
            {"name":"Seraphina","is_user":false,"is_system":false,"send_date":1785232801,"mes":"Welcome"}
            {"name":"System","is_user":false,"is_system":true,"mes":"Internal notice"}
            {"name":"Narrator","is_user":false,"is_system":false,"mes":"Rain falls","extra":{"type":"narrator"}}
        """.trimIndent()

        val decoded = codec.decode(
            jsonl = jsonl,
            fallbackTitle = "Imported file",
            fallbackTime = 10_000L,
        )

        assertEquals("Imported file", decoded.title)
        assertTrue(decoded.includeCharacterExamples)
        assertEquals("Alice", decoded.userName)
        assertEquals("Seraphina", decoded.characterNameHint)
        assertEquals(
            listOf(
                ChatArchiveMessageRole.User,
                ChatArchiveMessageRole.Character,
                ChatArchiveMessageRole.Narrator,
            ),
            decoded.messages.map { it.role },
        )
        assertEquals(listOf("Hello", "Welcome", "Rain falls"), decoded.messages.map { it.content })
        assertTrue(
            decoded.messages.zipWithNext().all { (first, second) ->
                first.createTime < second.createTime
            },
        )
    }

    @Test
    fun missingImportedUserNameUsesCallerFallback() {
        val decoded = codec.decode(
            jsonl = """{"chat_metadata":{}}""",
            fallbackTitle = "Fallback",
            fallbackUserName = " Local user ",
        )

        assertEquals("Local user", decoded.userName)
    }

    @Test
    fun malformedOrHeaderlessFileIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            codec.decode("""{"name":"Alice","mes":"Hello","is_user":true}""", "Fallback")
        }
        assertThrows(IllegalArgumentException::class.java) {
            codec.decode("""{"chat_metadata":{}}\nnot-json""", "Fallback")
        }
    }

    @Test
    fun messageLimitIsEnforcedWhileReadingLines() {
        val jsonl = buildString {
            appendLine("""{"chat_metadata":{}}""")
            repeat(times = 100_000) {
                appendLine("{}")
            }
            appendLine("not-json")
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            codec.decode(jsonl.reader(), "Fallback")
        }
        assertEquals("Chat archive has too many messages", error.message)
    }

    @Test
    fun timestampNormalizationRejectsLongOverflow() {
        val jsonl = """
            {"chat_metadata":{}}
            {"name":"Alice","is_user":true,"send_date":9223372036854775807,"mes":"Hello"}
            {"name":"Seraphina","is_user":false,"send_date":0,"mes":"Welcome"}
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(jsonl, "Fallback")
        }
    }

    @Test
    fun futureRpclientSchemaIsRejected() {
        val jsonl = """
            {"chat_metadata":{"rpclient":{"schema_version":2}}}
        """.trimIndent()

        val error = assertThrows(IllegalArgumentException::class.java) {
            codec.decode(jsonl, "Fallback")
        }

        assertTrue(error.message?.contains("schema version") == true)
    }

    @Test
    fun outOfRangeSummaryBoundaryIsIgnored() {
        val jsonl = """
            {"chat_metadata":{"rpclient":{"schema_version":1,"summary":{"content":"Wrong boundary","covered_message_index":1}}}}
            {"name":"Alice","is_user":true,"mes":"Hello"}
        """.trimIndent()

        val decoded = codec.decode(jsonl, "Fallback")

        assertNull(decoded.summary)
    }

}
