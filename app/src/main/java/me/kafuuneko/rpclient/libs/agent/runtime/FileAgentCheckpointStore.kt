package me.kafuuneko.rpclient.libs.agent.runtime

import com.google.gson.JsonObject
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.agent.model.AgentJson

/**
 * noBackupFilesDir 中的本机恢复载荷。
 * - 临时文件、fsync 与同目录原子替换保证读者只看到完整文件。
 * - 文件名来自客户端键的摘要，不接受载荷路径；正文不写日志。
 * - JSON 使用显式字段编码，不依赖 R8 下的反射属性名。
 */
class FileAgentCheckpointStore(private val mRoot: File) : AgentCheckpointStore {
    private val mMutex = Mutex()

    /** 持久化完整信封后返回指纹，供 Room 事务保存引用。 */
    override suspend fun write(key: String, payload: JsonObject): String =
        withContext(Dispatchers.IO) {
            require(key.isNotBlank() && key.length <= 256) { "Invalid checkpoint key" }
            val body = AgentJson.canonical(payload)
            val digest = AgentJson.digest(body)
            val envelope = JsonObject().apply {
                addProperty("version", 1)
                addProperty("key", key)
                addProperty("digest", digest)
                add("payload", payload.deepCopy())
            }.toString().toByteArray(Charsets.UTF_8)
            require(envelope.size <= MAX_BYTES) { "Checkpoint payload is too large" }
            // 同一实例串行发布，临时文件在任何失败下均可清理。
            mMutex.withLock {
                check(mRoot.isDirectory || mRoot.mkdirs()) { "Cannot create checkpoint directory" }
                val temporary = File(mRoot, "${UUID.randomUUID()}.tmp")
                try {
                    FileOutputStream(temporary).use { output -> output.write(envelope); output.fd.sync() }
                    Files.move(
                        temporary.toPath(),
                        file(key).toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } finally {
                    temporary.delete()
                }
            }
            digest
        }

    /** 对损坏或被替换的本机文件安全失败，绝不按缺失结果重新执行副作用。 */
    override suspend fun read(key: String, expectedDigest: String): JsonObject? =
        withContext(Dispatchers.IO) {
            mMutex.withLock {
                val file = file(key)
                if (!file.isFile || file.length() > MAX_BYTES) return@withLock null
                // 有界读取也覆盖文件在 length 检查后被替换的情况。
                val bytes = file.inputStream().use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (output.size() <= MAX_BYTES) {
                        val count =
                            input.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - output.size()))
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                if (bytes.size > MAX_BYTES) return@withLock null
                runCatching {
                    val decoder =
                        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                    val envelope = AgentJson.parse(
                        decoder.decode(ByteBuffer.wrap(bytes)).toString(),
                        MAX_BYTES,
                        32
                    ).asJsonObject
                    val payload = envelope.getAsJsonObject("payload")
                    require(envelope["version"].asInt == 1 && envelope["key"].asString == key && envelope["digest"].asString == expectedDigest) { "Checkpoint identity mismatch" }
                    require(AgentJson.digest(AgentJson.canonical(payload)) == expectedDigest) { "Checkpoint digest mismatch" }
                    payload
                }.getOrNull()
            }
        }

    /** 只删除哈希命名的受管文件。 */
    override suspend fun delete(key: String) = withContext(Dispatchers.IO) {
        mMutex.withLock { Files.deleteIfExists(file(key).toPath()); Unit }
    }

    /** 恢复后清理整个专用目录，包括崩溃遗留的临时文件。 */
    override suspend fun clear() = withContext(Dispatchers.IO) {
        mMutex.withLock {
            mRoot.listFiles()
                ?.forEach { check(it.isFile && it.delete()) { "Cannot clear checkpoint file" } }
            Unit
        }
    }

    private fun file(key: String) = File(mRoot, "${AgentJson.digest(key)}.json")

    companion object {
        private const val MAX_BYTES = 8 * 1024 * 1024
    }
}
