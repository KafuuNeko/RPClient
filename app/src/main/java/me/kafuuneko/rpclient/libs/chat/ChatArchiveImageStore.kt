package me.kafuuneko.rpclient.libs.chat

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.Writer
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 归档读写阶段的私有图片暂存及用户授权目录解析。
 * - 页面只持随机资源键，不持 Base64 或文件路径。
 * - 外部 URL 仅作为匹配线索，不发起网络下载，不访问授权目录以外的文件。
 */
class ChatArchiveImageStore(private val mContext: Context) {
    private val mRoot = File(mContext.cacheDir, "chat-archive-images")
    private val mActiveOwners = ConcurrentHashMap.newKeySet<String>()

    /** 创建一次导入的独立目录，并清理旧进程留下的过期资源。 */
    fun createOwner(): String {
        mRoot.mkdirs()
        mRoot.listFiles().orEmpty().filter { file ->
            file.name !in mActiveOwners && System.currentTimeMillis() - file.lastModified() > 86_400_000L
        }
            .forEach { it.deleteRecursively() }
        return UUID.randomUUID().toString().also { text ->
            check(File(mRoot, text).mkdirs()) { "Cannot create archive image directory" }
            mActiveOwners.add(text)
        }
    }

    /** 将内嵌数据解码到暂存，返回移除 Base64 后的描述；损坏数据使整个导入失败。 */
    fun stage(owner: String, image: ChatArchiveImage, checkActive: () -> Unit = {}): ChatArchiveImage {
        if (image.data == null) return image
        val key = UUID.randomUUID().toString()
        val target = file(owner, key)
        try {
            // 严格解码，不把损坏的内嵌图片降级为静默丢失。
            val hash = ChatArchiveImagePayload.open(image).use { input ->
                target.outputStream().use { output ->
                    ChatArchiveImagePayload.copyOriginal(input, output, image.hash, checkActive)
                }
            }
            return image.copy(data = null, resourceKey = key, hash = hash, compression = "none")
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    /** 尝试无损压缩后分块写出图片，只有原始字节参与 hash 校验。 */
    fun writeExportImage(
        owner: String,
        source: File,
        hash: String,
        mimeType: String,
        writer: Writer,
        checkActive: () -> Unit,
    ) {
        val temporary = file(owner, UUID.randomUUID().toString())
        try {
            val useGzip = ChatArchiveImagePayload.compress(source, temporary, hash, checkActive)
            val image = ChatArchiveImage(
                mimeType = mimeType,
                hash = hash,
                compression = if (useGzip) "gzip" else "none",
            )
            // 试压缩使用磁盘暂存；不把原图或压缩结果整体放入内存。
            ChatArchiveImageCodec.writeImage(image, writer) { output ->
                (if (useGzip) temporary else source).inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        checkActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
        } finally {
            temporary.delete()
        }
    }

    /** 在导入确认时读取当前归档拥有的字节，不接受外部路径或任意文件 URI。 */
    fun open(owner: String, key: String) = file(owner, key).inputStream()

    /** 取消、成功或失败时释放本次归档暂存；调用方在 IO 上下文执行。 */
    fun release(owner: String) {
        require(UUID.fromString(owner).toString() == owner) { "Invalid archive owner" }
        File(mRoot, owner).deleteRecursively()
        mActiveOwners.remove(owner)
    }

    private fun file(owner: String, key: String): File {
        require(UUID.fromString(owner).toString() == owner && UUID.fromString(key).toString() == key) {
            "Invalid archive resource key"
        }
        return File(File(mRoot, owner), key)
    }

    /** 扫描用户选择的目录，按相对路径匹配酒馆资源；同名歧义不自动选取。 */
    suspend fun resolveDirectory(archive: ChatArchive, tree: Uri): Map<String, Uri> = withContext(Dispatchers.IO) {
        val documents = mutableMapOf<String, Uri?>()
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        scan(tree = tree, parentId = rootId, prefix = "", result = documents, visited = mutableSetOf(), depth = 0)
        archive.messages.flatMap { chatArchiveMessage -> chatArchiveMessage.images }.mapNotNull { chatArchiveImage -> chatArchiveImage.sourceUrl }.distinct().mapNotNull { source ->
            matchingPath(source, documents.keys)?.let { path -> documents[path]?.let { uri -> source to uri } }
        }.toMap()
    }

    /** 仅遍历 DocumentsProvider 授权的树，限制深度和条目数量并响应取消。 */
    private suspend fun scan(
        tree: Uri,
        parentId: String,
        prefix: String,
        result: MutableMap<String, Uri?>,
        visited: MutableSet<String>,
        depth: Int,
    ) {
        currentCoroutineContext().ensureActive()
        require(depth <= 32 && visited.size < 50_000) { "Image directory is too large" }
        if (!visited.add(parentId)) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val folders = mutableListOf<Pair<String, String>>()
        mContext.contentResolver.query(children, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                require(result.size + visited.size < 50_000) { "Image directory is too large" }
                val id = cursor.getString(0)
                val path = prefix + cursor.getString(1)
                if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) folders += id to "$path/"
                else {
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    // 授权目录出现同名不同文件时保留歧义，不静默取最后一个。
                    result[path] = if (result.containsKey(path) && result[path] != uri) null else uri
                }
            }
        } ?: throw IOException("Cannot read image directory")
        for ((id, path) in folders) scan(tree, id, path, result, visited, depth + 1)
    }

    internal companion object {
        /** 优先完整后缀路径；解码 URL 后拒绝路径穿越，绝不只按文件名跨目录猜测。 */
        fun matchingPath(source: String, paths: Set<String>): String? {
            val path = runCatching {
                val decoded = URI(source.replace(" ", "%20")).path ?: return null
                val parts = decoded.trimStart('/').split('/')
                val hasUnsafePathSegment = parts.any { text -> text == ".." || text == "." || '\\' in text || '\u0000' in text }
                if (hasUnsafePathSegment) return null
                parts.joinToString("/")
            }.getOrNull() ?: return null
            return paths.filter { path == it || path.endsWith("/$it") }.maxByOrNull { it.length }
        }
    }
}
