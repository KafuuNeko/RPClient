package me.kafuuneko.rpclient.libs.json

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** 只向宿主暴露随机任务标识和文件元数据，不暴露私有路径或正文。 */
data class PreparedJsonExport(val token: String, val fileName: String, val mimeType: String)

/**
 * JSON 导出文件仓库。
 *
 * - 在私有缓存中冻结并格式化载荷，非法或超预算文档保留原文；
 * - 系统选择器返回后才向用户 URI 复制，完成或取消后清理快照；
 * - 缓存不参与系统备份，遗留文件在下次准备导出时过期清理。
 */
class JsonExportRepository(private val mContext: Context) {
    private val mDirectory: File get() = File(mContext.cacheDir, "json-exports")

    /**
     * 在 IO 线程冻结单次导出，返回系统选择器所需的小型元数据。
     *
     * @param source 已保存的 JSON 原文
     * @param name 不含路径和敏感内容的文件名主体
     * @return 准备好的 JSON 或原文文本快照
     */
    suspend fun prepare(source: String, name: String): PreparedJsonExport {
        val token = UUID.randomUUID().toString()
        val file = exportFile(token)
        return try {
            withContext(Dispatchers.IO) {
                clearExpired()
                val context = currentCoroutineContext()
                // 先生成私有快照，避免语法错误在目标 URI 留下半份格式化内容。
                val formatted = try {
                    file.bufferedWriter(Charsets.UTF_8).use { writer ->
                        JsonDocumentCodec.format(
                            source,
                            writer,
                            checkActive = context::ensureActive
                        )
                    }
                    true
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    context.ensureActive()
                    file.writeText(source, Charsets.UTF_8)
                    false
                }
                context.ensureActive()
                PreparedJsonExport(
                    token = token,
                    fileName = name + if (formatted) ".json" else ".raw.txt",
                    mimeType = if (formatted) "application/json" else "text/plain",
                )
            }
        } catch (error: Throwable) {
            // 覆盖 withContext 完成但取消先于结果交付的窗口，避免遗留无人持有的快照。
            withContext(NonCancellable + Dispatchers.IO) { file.delete() }
            throw error
        }
    }

    /**
     * 复制冻结快照至系统返回的文档，成功仅在输出流关闭后返回。
     *
     * @param token 本次任务标识
     * @param destination 用户选择的目标 URI
     */
    suspend fun write(token: String, destination: Uri) = withContext(Dispatchers.IO) {
        val file = exportFile(token)
        try {
            // 分块检查取消，避免用户退出后继续写入大文件。
            file.inputStream().use { input ->
                val output = mContext.contentResolver.openOutputStream(destination, "wt")
                    ?: error("Cannot open export destination.")
                output.use { stream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        stream.write(buffer, 0, count)
                    }
                }
            }
        } catch (error: Throwable) {
            runCatching { DocumentsContract.deleteDocument(mContext.contentResolver, destination) }
            throw error
        } finally {
            file.delete()
        }
    }

    /** 删除取消或已失效的快照，只涉及已校验名称的私有缓存文件。 */
    fun discard(token: String) {
        exportFile(token).delete()
    }

    /** 准备新导出时清理过期文件，系统缓存目录不参与备份。 */
    private fun clearExpired() {
        mDirectory.apply { mkdirs() }.listFiles()
            ?.filter { System.currentTimeMillis() - it.lastModified() > MAX_AGE }
            ?.forEach { it.delete() }
    }

    /** 仅接受本仓库产生的 UUID，禁止任务标识变成任意文件路径。 */
    private fun exportFile(token: String): File {
        require(UUID.fromString(token).toString() == token) { "Invalid export token." }
        return File(mDirectory, token)
    }

    private companion object {
        const val MAX_AGE = 24 * 60 * 60 * 1000L
    }
}
