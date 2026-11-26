package me.kafuuneko.rpclient.libs.agent

import com.google.gson.JsonObject
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.agent.runtime.FileAgentCheckpointStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 使用真实文件保护恢复载荷的完整性与路径隔离。 */
class AgentCheckpointStoreTest {
    @get:Rule
    val directory = TemporaryFolder()

    /** 内容指纹同时防止损坏载荷和旧数据库引用误续接。 */
    @Test
    fun readsOnlyMatchingCompletePayloads() = runBlocking {
        // 同一目录重建存储对象模拟进程恢复，仍需匹配内容指纹。
        val root = directory.newFolder("checkpoints")
        val store = FileAgentCheckpointStore(root)
        val payload = JsonObject().apply { addProperty("signature", "opaque-signature") }
        val digest = store.write("run/step", payload)
        assertEquals(payload, FileAgentCheckpointStore(root).read("run/step", digest))
        assertNull(store.read("run/step", "0".repeat(64)))
        root.listFiles()!!.single().writeText("{broken")
        assertNull(store.read("run/step", digest))
    }

    /** 同键并发发布后只能读到某个完整版本，键不能穿越专用目录。 */
    @Test
    fun atomicallyReplacesFilesAndClearsOnlyOwnedDirectory() = runBlocking {
        // 并发发布同键载荷，读取只能匹配完整的某一版本。
        val root = directory.newFolder("checkpoints")
        val sibling = File(directory.root, "keep.txt").apply { writeText("keep") }
        val store = FileAgentCheckpointStore(root)
        val first = JsonObject().apply { addProperty("value", "a".repeat(1000)) }
        val second = JsonObject().apply { addProperty("value", "b".repeat(1000)) }
        val a = async { store.write("../../outside", first) }
        val b = async { store.write("../../outside", second) }
        val aDigest = a.await()
        val bDigest = b.await()
        val present = listOfNotNull(
            store.read("../../outside", aDigest),
            store.read("../../outside", bDigest)
        )
        assertEquals(1, present.size)
        assertTrue(present.single() == first || present.single() == second)
        assertEquals(1, root.listFiles()!!.size)
        store.clear()
        assertTrue(root.listFiles()!!.isEmpty())
        assertEquals("keep", sibling.readText())
    }
}
