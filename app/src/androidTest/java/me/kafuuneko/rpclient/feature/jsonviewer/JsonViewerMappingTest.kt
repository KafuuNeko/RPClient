package me.kafuuneko.rpclient.feature.jsonviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import me.kafuuneko.rpclient.feature.jsonviewer.model.parseJson
import me.kafuuneko.rpclient.feature.jsonviewer.model.toEntries
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JsonViewerMappingTest {
    /** 普通长文不能再被旧摘要截断，超长值则显式标记并保护代理对。 */
    @Test
    fun strings_showFullContentUntilProtectionThreshold() {
        val ordinary = "中😀".repeat(30_000)
        val entry = JSONObject().put("content", ordinary).toEntries().single()
        assertFalse(entry.isTruncated)
        assertEquals(ordinary, entry.valueBlocks.joinToString(""))
        assertTrue(entry.valueBlocks.all { it.length <= 2048 })
        val huge = "a".repeat(4095) + "😀" + "中".repeat(100_000)
        val protected = JSONObject().put("content", huge).toEntries().single()
        assertTrue(protected.isTruncated)
        assertEquals(huge.length, protected.originalLength)
        assertFalse(protected.valueBlocks.last().last().isHighSurrogate())
    }

    /** 根标量有真实正文，宽数组仅映射请求的一批，导航仍保留原 key。 */
    @Test
    fun scalarAndWideContainers_haveBoundedEntries() {
        assertEquals(
            "root",
            parseJson("\"root\"").getOrThrow().toEntries().single().valueBlocks.single()
        )
        val array = JSONArray()
        repeat(500) { array.put(it) }
        assertEquals(100, array.toEntries().size)
        assertEquals(200, array.toEntries(limit = 200).size)
        val key = "k".repeat(500)
        val entry = JSONObject().put(key, JSONObject()).toEntries().single()
        assertEquals(key, entry.sourceKey)
        assertTrue(entry.name.length < key.length)
    }
}
