package me.kafuuneko.rpclient.libs.chat

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Reader
import java.io.StringWriter

class ChatArchiveCodecTest {
    private val codec = ChatArchiveCodec(Gson())

    /** 消息数刚好达到上限时仍可回导旧版末尾兼容项，其他多余消息不能绕过限制。 */
    @Test
    fun acceptsOnlyOneFinalCompatibilityTailBeyondMessageLimit() {
        // 使用小上限覆盖边界，避免为单个协议条件构造十万条消息。
        val header = """{"chat_metadata":{}}"""
        val message = """{"mes":"message","extra":{"memory":"summary"}}"""
        val tail = """{"mes":"","is_system":true,"extra":{"type":"rpclient_summary_tail"}}"""
        val decoded = codec.decodeTo(
            reader = "$header\n$message\n$tail".reader(), fallbackTitle = "Limited",
            maxMessageCount = 1, transformImage = { it }, onMessage = { _, _ -> },
        )
        assertEquals(0, decoded.summary?.coveredMessageIndex)
        val disguisedUser =
            tail.replace("\"is_system\":true", "\"is_system\":true,\"is_user\":true")
        assertThrows(IllegalArgumentException::class.java) {
            codec.decodeTo(
                reader = "$header\n$message\n$disguisedUser".reader(), fallbackTitle = "Limited",
                maxMessageCount = 1, transformImage = { it }, onMessage = { _, _ -> },
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            codec.decodeTo(
                reader = "$header\n$message\n$tail\n$tail".reader(), fallbackTitle = "Limited",
                maxMessageCount = 1, transformImage = { it }, onMessage = { _, _ -> },
            )
        }
    }

    /** 酒馆边界按保留消息重映射，过滤的系统行不能造成索引偏移。 */
    @Test
    fun importsLatestMemoryAndMapsFilteredSystemMessages() {
        // 被忽略的系统行仍保留来源摘要位置，末条候选则不能直接生效。
        val archive = codec.decode(
            """
            {"chat_metadata":{}}
            {"mes":"system before chat","is_system":true,"extra":{"memory":"no boundary"}}
            {"mes":"first","extra":{"memory":"old"}}
            {"mes":"second"}
            {"mes":"system note","is_system":true,"extra":{"memory":"current"}}
            {"mes":"latest","extra":{"memory":"ignored final memory"}}
            """.trimIndent(),
            "Memory", fallbackTime = 1000L,
        )
        assertEquals(listOf("first", "second", "latest"), archive.messages.map { it.content })
        assertEquals("current", archive.summary?.content)
        assertEquals(1, archive.summary?.coveredMessageIndex)
    }

    /** 与酒馆一致排除末条摘要；非字符串与空白标记不能覆盖已有有效记忆。 */
    @Test
    fun ignoresFinalBlankAndMalformedMemory() {
        // 不完整的外部字段不能替换之前已经生效的字符串摘要。
        val archive = codec.decode(
            """
            {"chat_metadata":{}}
            {"mes":"first","extra":{"memory":"valid"}}
            {"mes":"second","extra":{"memory":{"text":"invalid"}}}
            {"mes":"third","extra":{"memory":123}}
            {"mes":"fourth","extra":{"memory":"  "}}
            {"mes":"last","extra":{"memory":"inactive"}}
            """.trimIndent(),
            "Memory",
        )
        assertEquals("valid", archive.summary?.content)
        assertEquals(0, archive.summary?.coveredMessageIndex)
        assertNull(
            codec.decode(
                "{\"chat_metadata\":{}}\n{\"mes\":\"last\",\"extra\":{\"memory\":\"inactive\"}}",
                "Memory",
            ).summary
        )
    }

    /** 标准消息字段优先于过时头部，酒馆清除摘要后不得复活导出时的旧副本。 */
    @Test
    fun honorsSillyTavernUpdatesAndDeletionAfterExport() {
        val archive = codec.decode(
            """
            {"chat_metadata":{}}
            {"mes":"first","extra":{"memory":"original"}}
            {"mes":"last"}
            """.trimIndent(),
            "Memory",
        )
        val lines = codec.encode(archive).lineSequence().filter { it.isNotBlank() }
            .map { JsonParser.parseString(it).asJsonObject }.toMutableList()
        lines[1].getAsJsonObject("extra").addProperty("memory", "edited in SillyTavern")
        val updated = codec.decode(lines.joinToString("\n"), "Memory")
        assertEquals("edited in SillyTavern", updated.summary?.content)
        // 删除标准字段模拟酒馆清除当前摘要，自有头部仍保留原始副本。
        lines[1].getAsJsonObject("extra").remove("memory")
        assertNull(codec.decode(lines.joinToString("\n"), "Memory").summary)
    }

    /** 末条摘要不能改变酒馆的最后一条消息，原样回导仍保留摘要与准确边界。 */
    @Test
    fun exportsLatestSummaryWithoutAddingMessages() {
        val archive = codec.decode(
            """
            {"chat_metadata":{"rpclient":{"schema_version":1,"summary":{"content":"complete","create_time":1234,"covered_message_index":1}}}}
            {"mes":"first","is_user":true}
            {"mes":"last"}
            """.trimIndent(),
            "Memory",
        )
        val encoded = codec.encode(archive)
        val messages = encoded.lineSequence().filter { it.isNotBlank() }.drop(1)
            .map { JsonParser.parseString(it).asJsonObject }.toList()
        assertEquals(2, messages.size)
        assertEquals("complete", messages[1].getAsJsonObject("extra")["memory"].asString)
        assertTrue(messages.first()["is_user"].asBoolean)
        assertFalse(messages.last()["is_system"].asBoolean)
        assertFalse(messages.last()["is_user"].asBoolean)
        assertEquals("last", messages.last()["mes"].asString)
        // 酒馆暂不启用末条摘要，重新生成时仍能直接删除真实的角色回复。
        assertNull(messages.dropLast(1).asReversed().firstNotNullOfOrNull {
            it.getAsJsonObject("extra")?.get("memory")?.asString
        })
        val restored = codec.decode(encoded, "Memory")
        assertEquals(archive.messages, restored.messages)
        assertEquals(archive.summary, restored.summary)
        assertEquals(encoded, codec.encode(restored))
    }

    /** 无尾项的末条摘要仅在消息字段与头部一致时恢复，清除或重生成后不得复活。 */
    @Test
    fun finalSummaryDoesNotRestoreStaleHeaderAfterExternalChanges() {
        val archive = codec.decode(
            """
            {"chat_metadata":{"rpclient":{"schema_version":1,"summary":{"content":"complete","create_time":1234,"covered_message_index":1}}}}
            {"mes":"first","is_user":true}
            {"mes":"last"}
            """.trimIndent(),
            "Memory",
        )
        val lines = codec.encode(archive).lineSequence().filter { it.isNotBlank() }
            .map { JsonParser.parseString(it).asJsonObject }.toList()
        // 继续聊天后，原末条记忆按酒馆规则生效，仍保持原有覆盖位置与时间。
        val continued = lines.joinToString("\n") + "\n" + """{"mes":"next","is_user":true}"""
        assertEquals(archive.summary, codec.decode(continued, "Memory").summary)
        // 末条字段被清空或改写时，不从文件头恢复过时摘要。
        val extra = lines.last().getAsJsonObject("extra")
        for (memory in listOf("", "edited final memory")) {
            extra.addProperty("memory", memory)
            assertNull(codec.decode(lines.joinToString("\n"), "Memory").summary)
        }
        extra.remove("memory")
        assertNull(codec.decode(lines.joinToString("\n"), "Memory").summary)
        // 酒馆重新生成会用新回复替换末条；新回复没有旧 memory 时也不能复活头部副本。
        lines.last().addProperty("mes", "regenerated reply")
        assertNull(codec.decode(lines.joinToString("\n"), "Memory").summary)
    }

    /** 分段图文导出不能覆盖 memory，也不能错用分页内索引。 */
    @Test
    fun streamedImageExportPreservesMemoryAndNarratorType() = runBlocking {
        // 逐段写图绕过完整 JSON 序列化，但必须保留同一条消息的其他扩展。
        val archive = codec.decode(
            """
            {"chat_metadata":{}}
            {"mes":"narration","extra":{"type":"narrator","memory":"image memory"}}
            {"mes":"last"}
            """.trimIndent(),
            "Memory",
        )
        val writer = StringWriter()
        codec.encodeHeader(archive, writer, summaryInMessages = true)
        codec.encodeMessageWithImages(archive, archive.messages[0], writer, messageIndex = 0) {
            writer.write("""{"mime_type":"image/png","data":"AQID"}""")
        }
        codec.encodeMessage(archive, archive.messages[1], writer, messageIndex = 1)
        val restored = codec.decode(writer.toString(), "Memory")
        assertEquals("image memory", restored.summary?.content)
        assertEquals(ChatArchiveMessageRole.Narrator, restored.messages[0].role)
        assertEquals("AQID", restored.messages[0].images.single().data)
    }

    /** 没有覆盖位置的手工记忆不能因标准字段缺失而丢失，备份头部也继续独立生效。 */
    @Test
    fun preservesUnboundManualSummaryAndHeaderOnlyBackups() {
        val manual = codec.decode(
            """{"chat_metadata":{"rpclient":{"schema_version":1,"summary":{"content":"manual","covered_message_index":-1}}}}""",
            "Manual",
        )
        assertEquals(manual, codec.decode(codec.encode(manual), "Manual"))
        val bound = manual.copy(
            messages = listOf(ChatArchiveMessage(1000L, ChatArchiveMessageRole.User, "first")),
            summary = ChatArchiveSummary("backup", 2000L, 0),
        )
        // 完整备份没有逐消息 memory，继续独立使用自有头部恢复。
        val writer = StringWriter()
        codec.encodeHeader(bound, writer)
        codec.encodeMessage(bound, bound.messages.single(), writer)
        assertEquals(bound.summary, codec.decode(writer.toString(), "Backup").summary)
    }

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
