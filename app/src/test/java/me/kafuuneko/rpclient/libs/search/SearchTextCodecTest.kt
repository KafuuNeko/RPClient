package me.kafuuneko.rpclient.libs.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 覆盖分块、Unicode 映射和查询编码的边界，防止全文搜索静默漏项。 */
class SearchTextCodecTest {
    /** 码点可逆性覆盖补充平面、孤立代理项与看似转义的普通文字。 */
    @Test
    fun tokensRoundTripUnicodeWithoutChangingSnippetOffsets() {
        val random = Random(21)
        val text = buildString {
            append("雪乃 x20 100%_ café 👩🏽‍💻 \u0000\uD800")
            repeat(times = 20_000) { appendCodePoint(random.nextInt(until = 0x110000)) }
        }
        for ((_, chunk) in SearchTextCodec.chunks(text)) {
            assertEquals(chunk, SearchTextCodec.decodeTokens(SearchTextCodec.tokens(chunk)))
        }
        assertEquals("", SearchTextCodec.decodeTokens(""))
    }

    /** 不允许坏编码静默丢字或变成错误片段，交由搜索错误恢复路径处理。 */
    @Test
    fun corruptAndNoncanonicalTokensAreRejected() {
        for (encoded in listOf(
            " ",
            "a ",
            " a",
            "a  b",
            "x41",
            "x010",
            "x110000",
            "xGG",
            "hello",
            "%",
            "👩"
        )) {
            try {
                SearchTextCodec.decodeTokens(encoded)
                throw AssertionError("Corrupt token accepted: $encoded")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Test
    fun phraseTreatsOperatorsAndEncodedLookingTextAsLiterals() {
        assertEquals("\"雪 乃 x20 1 0 0 x25 x5f\"", SearchTextCodec.phrase("雪乃 100%_"))
        assertFalse(SearchTextCodec.tokens("x25") == SearchTextCodec.tokens("%"))
        assertEquals(
            "\"o r x20 x22 x2a x22\"",
            SearchTextCodec.phrase(SearchTextCodec.normalize("OR \"*\""))
        )
    }

    @Test
    fun everySupportedCrossBoundaryQueryIsContainedInAChunk() {
        val random = Random(21)
        val alphabet = listOf("雪", "乃", "a", " ", "👩", "🏽", "\u200D", "%", "é")
        val points =
            List(SearchTextCodec.BLOCK_POINTS * 3 + 128) { alphabet[random.nextInt(alphabet.size)] }
        val text = points.joinToString("")
        val chunks = SearchTextCodec.chunks(text).toList()
        for (length in listOf(1, 2, 3, 127, 128)) {
            val stride = SearchTextCodec.BLOCK_POINTS - 127
            for (start in listOf(
                stride - 1,
                stride,
                stride + 1,
                SearchTextCodec.BLOCK_POINTS - 1,
                stride * 2
            )) {
                val query = points.subList(start, start + length).joinToString("")
                assertTrue(
                    "Boundary query must be contained",
                    chunks.any { it.second.contains(query) })
            }
        }
        assertEquals(
            text,
            chunks.first().second + chunks.drop(1).joinToString("") { pair ->
                pair.second.substring(
                    pair.second.offsetByCodePoints(
                        index = 0,
                        codePointOffset = 127
                    )
                )
            },
        )
    }

    @Test
    fun projectionSearchesVisibleMarkdownAndExcludesInternalThought() {
        val projected =
            SearchTextCodec.normalize(text = SearchTextCodec.project("<think>private</think>\n# Hello **world**\n\n[雪乃](https://example.invalid)\n\n```kt\nx_1 = 10%\n```"))
        assertTrue(projected.contains("hello world"))
        assertTrue(projected.contains("雪乃"))
        assertTrue(projected.contains("x_1 = 10%"))
        assertFalse(projected.contains("private"))
        assertFalse(projected.contains("https"))
    }

    @Test
    fun originalRangeNeverSelectsHiddenThoughtOrMarkdownUrl() {
        val text = "<think>needle</think> aaaaab needle"
        assertEquals(
            text.lastIndexOf("needle") until text.length,
            SearchTextCodec.literalRange(text, "needle")
        )
        assertEquals(
            text.indexOf("aaaaab") until text.indexOf("aaaaab") + 6,
            SearchTextCodec.literalRange(
                text = text,
                query = "aaaaab",
            ),
        )
        assertNull(SearchTextCodec.literalRange("[link](https://needle.invalid) needle", "needle"))
    }

    @Test
    fun normalizationAndOriginalOffsetsSurviveWidthAccentsAndSurrogatePairs() {
        val text = "👩🏽‍💻 Ａlice cafe\u0301 雪乃"
        assertEquals("alice café", SearchTextCodec.normalize("Ａlice  CAFE\u0301"))
        val range = SearchTextCodec.literalRange(text, "ALICE CAFÉ")!!
        assertEquals("Ａlice cafe\u0301", text.substring(range))
        val emoji = SearchTextCodec.literalRange(text, "👩🏽‍💻")!!
        assertEquals("👩🏽‍💻", text.substring(emoji))
    }
}
