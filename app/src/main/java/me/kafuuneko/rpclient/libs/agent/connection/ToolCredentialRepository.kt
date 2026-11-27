package me.kafuuneko.rpclient.libs.agent.connection

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * HTTP 工具凭据的本机存储边界。
 * - Keystore 生成随机 IV 的 AES-GCM 密钥，引用作为 AAD 防止密文调包。
 * - 密文仅写 noBackupFilesDir；配置、导出和运行快照只保存不可重用的引用。
 */
class ToolCredentialRepository(context: Context) {
    private val mDirectory = File(context.noBackupFilesDir, "tool_credentials")
    private val mMutex = Mutex()

    /**
     * 保存新凭据并返回随机引用；不覆盖旧引用以免改变等待确认的请求。
     *
     * @param secret 仅用于本机加密或请求认证的凭据正文。
     * @return 新建的随机引用。
     */
    suspend fun save(secret: String): String = withContext(Dispatchers.IO) {
        requireConfig(
            secret.isNotBlank() && secret.toByteArray().size <= 8192 && '\r' !in secret && '\n' !in secret,
            "invalid_credential",
            "Invalid tool credential",
        )
        mMutex.withLock {
            val reference = UUID.randomUUID().toString()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
            cipher.updateAAD(reference.toByteArray(Charsets.UTF_8))
            val encrypted = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
            // 完整密文原子替换，进程退出不能留下半个可见凭据。
            check(mDirectory.isDirectory || mDirectory.mkdirs()) { "Cannot create credential store" }
            val target = AtomicFile(file(reference))
            val output = target.startWrite()
            try {
                output.write(byteArrayOf(1, cipher.iv.size.toByte()))
                output.write(cipher.iv)
                output.write(encrypted)
                target.finishWrite(output)
            } catch (error: Exception) {
                target.failWrite(output)
                throw error
            }
            reference
        }
    }

    /**
     * 读取失败只返回缺失态，调用方不能退回无认证发送。
     *
     * @param reference 本机随机凭据引用。
     * @return 凭据正文，缺失或校验失败时为空。
     */
    suspend fun read(reference: String): String? = withContext(Dispatchers.IO) {
        mMutex.withLock {
            runCatching {
                val target = AtomicFile(file(reference))
                if (!target.baseFile.exists()) return@withLock null
                require(target.baseFile.length() <= 16_384) { "Invalid credential size" }
                val bytes = target.readFully()
                require(bytes.size >= 30 && bytes[0].toInt() == 1 && bytes[1].toInt() == 12) { "Invalid credential envelope" }
                // 读取不生成替代密钥，重装或整库恢复后必须由用户重新绑定。
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    key(create = false),
                    GCMParameterSpec(128, bytes.copyOfRange(2, 14))
                )
                cipher.updateAAD(reference.toByteArray(Charsets.UTF_8))
                String(cipher.doFinal(bytes.copyOfRange(14, bytes.size)), Charsets.UTF_8)
            }.getOrNull()
        }
    }

    /**
     * 删除本机引用及其原子写入备份。
     *
     * @param reference 本机随机凭据引用。
     */
    suspend fun delete(reference: String) = withContext(Dispatchers.IO) {
        mMutex.withLock { AtomicFile(file(reference)).delete() }
    }

    /**
     * 清理不再被任何连接引用的孤立凭据密文文件。
     *
     * @param activeReferences 当前权威连接仍在使用的凭据引用集合。
     */
    suspend fun prune(activeReferences: Set<String>) = withContext(Dispatchers.IO) {
        mMutex.withLock {
            val files = mDirectory.listFiles().orEmpty()
            // 仅删除符合 UUID 命名的孤立密文，避免误删临时或外部文件。
            files.filter { it.isFile && it.name.matches(Regex("[0-9a-f-]{36}")) && it.name !in activeReferences }
                .forEach { AtomicFile(it).delete() }
        }
    }

    private fun file(reference: String): File {
        require(reference.matches(Regex("[0-9a-f-]{36}"))) { "Invalid credential reference" }
        return File(mDirectory, reference)
    }

    private fun key(create: Boolean): SecretKey {
        // 丢失密钥时读取必须失败，只有主动绑定凭据才允许生成新密钥。
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        check(create) { "Tool credential key is missing" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "rpclient.http.tools.v1"
    }
}
