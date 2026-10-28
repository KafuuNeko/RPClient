package me.kafuuneko.rpclient.libs.character

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.gson.Gson
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.regex.RegexScriptCodec
import me.kafuuneko.rpclient.libs.regex.RegexScriptRepository
import me.kafuuneko.rpclient.libs.regex.RegexScriptScope
import me.kafuuneko.rpclient.libs.regex.RegexScriptTarget
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository

/**
 * 角色卡文件导入导出的应用层协调器。
 *
 * 负责 Android URI 读取、头像文件保存、嵌入世界书落库，以及 JSON/PNG 两种导出形式；
 * 格式映射和 PNG chunk 操作分别委托给 [CharacterCardMapper] 与 [CharacterCardPngCodec]。
 */
class CharacterCardRepository(
    private val mContext: Context,
    private val mGson: Gson,
    private val mCharacterRepository: CharacterRepository,
    private val mLorebookRepository: LorebookRepository,
    private val mFileRepository: FileRepository,
    private val mRegexCodec: RegexScriptCodec,
    private val mRegexRepository: RegexScriptRepository
) {
    /** 无状态格式映射器，在 Repository 生命周期内复用。 */
    private val mMapper = CharacterCardMapper(mGson, mRegexCodec)

    /** 从 URI 读取并解析 JSON 或 PNG 角色卡，但不保存头像或业务实体。 */
    suspend fun readImportFromUri(uri: Uri): CharacterCardImportDraft =
        withContext(Dispatchers.IO) {
            val bytes =
                mContext.contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
                    ?: error("Cannot read character card")
            val mime = mContext.contentResolver.getType(uri).orEmpty()
            val isPng = CharacterCardPngCodec.isPng(bytes)
            val json = when {
                isPng -> CharacterCardPngCodec.readCharacterJson(bytes)
                else -> bytes.toString(Charsets.UTF_8)
            }
            CharacterCardImportDraft(
                card = mMapper.parseCharacter(json),
                avatarSourceUri = uri.takeIf { isPng || mime.startsWith("image/") },
                avatarMimeType = mime.ifBlank { "image/png" },
            )
        }

    /**
     * 读取单张角色卡或批量导出的 ZIP，不将压缩条目解包到文件系统。
     * - 先检查文件签名，普通卡片继续使用既有 JSON/PNG 解码入口。
     * - 按 ZIP 元数据跳过非 JSON 和不安全路径，单个损坏条目不阻止后续卡片解析。
     * - 当前仅限制条目数量，单个 JSON 的大小边界由后续资源限制修复处理。
     *
     * @param uri 用户选择的角色卡或 ZIP 文档。
     * @return 当前文档解析出的草稿与失败数量。
     */
    suspend fun readImportItemsFromUri(uri: Uri): CharacterCardArchiveReadResult =
        withContext(Dispatchers.IO) {
            // 签名检查只负责分流，普通角色卡仍由原有入口处理。
            val source = mContext.contentResolver.openInputStream(uri)
                ?: error("Cannot read character card")
            val pushback = PushbackInputStream(BufferedInputStream(source), ZIP_SIGNATURE.size)
            val signature = ByteArray(ZIP_SIGNATURE.size)
            val signatureSize = pushback.read(signature)
            if (signatureSize > 0) pushback.unread(signature, 0, signatureSize)
            if (signatureSize != ZIP_SIGNATURE.size || !signature.contentEquals(ZIP_SIGNATURE)) {
                pushback.close()
                return@withContext try {
                    CharacterCardArchiveReadResult(
                        drafts = listOf(readImportFromUri(uri)),
                        failureCount = 0,
                        totalCount = 1
                    )
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    CharacterCardArchiveReadResult(emptyList(), failureCount = 1, totalCount = 1)
                }
            }

            val drafts = mutableListOf<CharacterCardImportDraft>()
            var failureCount = 0
            var candidateCount = 0
            var entryCount = 0
            // ZIP 需要随机访问目录，先复制到缓存并保证所有退出路径清理临时文件。
            val archiveFile =
                File.createTempFile("rpclient-character-import-", ".zip", mContext.cacheDir)
            try {
                pushback.use { input ->
                    archiveFile.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                }
                // 逐项独立解析，路径过滤只影响当前条目，不提前提交任何业务实体。
                ZipFile(archiveFile).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        currentCoroutineContext().ensureActive()
                        val entry = entries.nextElement()
                        entryCount++
                        if (entryCount > MAX_ARCHIVE_ENTRIES) {
                            throw ArchiveLimitException("Character archive contains too many entries")
                        }
                        val name = entry.name
                        val unsafePath = name.startsWith("/") ||
                            name.startsWith("\\") ||
                            name.split('/', '\\').any { it == ".." } ||
                            name.contains('\u0000')
                        if (entry.isDirectory || !name.endsWith(
                                ".json",
                                ignoreCase = true
                            ) || unsafePath
                        ) {
                            continue
                        }

                        candidateCount++
                        try {
                            val bytes =
                                zip.getInputStream(entry).use { it.readUnboundedWithCancellation() }
                            val json = bytes.toString(Charsets.UTF_8)
                            drafts += CharacterCardImportDraft(
                                card = mMapper.parseCharacter(json),
                                avatarSourceUri = null,
                                avatarMimeType = "application/json"
                            )
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            failureCount++
                        }
                    }
                }
            } finally {
                archiveFile.delete()
            }
            if (candidateCount == 0) failureCount++
            CharacterCardArchiveReadResult(
                drafts = drafts,
                failureCount = failureCount,
                totalCount = maxOf(candidateCount, 1)
            )
        }

    /** 分块读取当前条目，并在每次读取前检查批量导入是否已取消。 */
    private suspend fun InputStream.readUnboundedWithCancellation(): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private class ArchiveLimitException(message: String) : Exception(message)

    /**
     * 保存已解析的角色卡，并返回新角色 ID。
     *
     * 头像与内嵌世界书先保存以建立有效引用；后续步骤失败时会清理本次新增资源。
     */
    suspend fun saveImport(draft: CharacterCardImportDraft): Long = withContext(Dispatchers.IO) {
        var avatar = ""
        var lorebookId = 0L
        try {
            avatar = draft.avatarSourceUri?.let { uri ->
                mFileRepository.saveFile(uri, draft.avatarMimeType)
            }.orEmpty()
            val parsed = draft.card
            lorebookId = parsed.embeddedLorebook?.let { book ->
                mLorebookRepository.saveImport(
                    parsed = book.copy(
                        lorebook = book.lorebook.copy(
                            name = book.lorebook.name.ifBlank {
                                "${parsed.character.name}'s Lorebook"
                            },
                        ),
                    ),
                )
            } ?: 0L
            mCharacterRepository.saveCharacter(
                character = parsed.character.copy(
                    avatar = avatar,
                    characterLorebookId = lorebookId,
                    extensionsJson = mRegexCodec.injectIntoCharacterExtensions(
                        json = parsed.character.extensionsJson,
                        scripts = parsed.regexScripts,
                    ),
                ),
            )
        } catch (error: Exception) {
            // 导入任务可能已被取消；补偿清理必须脱离已取消的 Job 才能执行挂起操作。
            withContext(NonCancellable) {
                if (lorebookId != 0L) {
                    runCatching { mLorebookRepository.deleteLorebook(lorebookId) }
                }
                if (avatar.isNotBlank()) {
                    runCatching { mFileRepository.deleteFile(avatar) }
                }
            }
            throw error
        }
    }

    /** 读取并导入角色卡；无需在写入前检查解析结果时使用。 */
    suspend fun importFromUri(uri: Uri): Long = withContext(Dispatchers.IO) {
        saveImport(readImportFromUri(uri))
    }

    /** 导出 Character Card V2 JSON，并在角色已绑定世界书时一并嵌入。 */
    suspend fun exportJson(characterId: Long): String = withContext(Dispatchers.IO) {
        val character =
            mCharacterRepository.getCharacterById(characterId) ?: error("Character not found")
        val regexScripts = mRegexRepository.getScripts(
            target = RegexScriptTarget(RegexScriptScope.Character, characterId),
        )
        mMapper.toV2Json(
            character = character,
            lorebook = character.characterLorebookId.takeIf { id -> id != 0L }
                ?.let { id -> mLorebookRepository.getLorebookById(id) },
            entries = character.characterLorebookId.takeIf { id -> id != 0L }
                ?.let { id -> mLorebookRepository.getEntriesByLorebookId(id) }.orEmpty(),
            regexScripts = regexScripts,
        )
    }

    /** 将角色卡 JSON 直接写入用户选择的文档 URI。 */
    suspend fun exportJsonToUri(characterId: Long, uri: Uri) = withContext(Dispatchers.IO) {
        val json = exportJson(characterId)
        mContext.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(json.toByteArray(Charsets.UTF_8))
        } ?: error("Cannot open character export destination")
    }

    /**
     * 将所选角色卡作为独立 Character Card V2 JSON 条目写入 ZIP。
     * @param characterIds 用户选中的角色 ID，重复项只导出一次。
     * @param uri 系统文件创建器返回的 ZIP 文档位置。
     */
    suspend fun exportJsonArchiveToUri(characterIds: List<Long>, uri: Uri) =
        withContext(Dispatchers.IO) {
            // 去重后再打开目标文件，避免空选择产生无效归档。
            val ids = characterIds.distinct()
            require(ids.isNotEmpty()) { "No characters selected" }
            val output = mContext.contentResolver.openOutputStream(uri)
                ?: error("Cannot open character archive destination")
            output.buffered().use { buffered ->
                ZipOutputStream(buffered).use { zip ->
                    // 名称按忽略大小写去重，兼容对文件名大小写不敏感的解压工具。
                    val usedNames = mutableSetOf<String>()
                    ids.forEach { characterId ->
                        currentCoroutineContext().ensureActive()
                        val character = mCharacterRepository.getCharacterById(characterId)
                            ?: error("Character not found: $characterId")
                        val json = exportJson(characterId)
                        val baseName = safeArchiveEntryName(character.name, characterId)
                        var entryName = "$baseName.json"
                        var suffix = 2
                        while (!usedNames.add(entryName.lowercase(Locale.ROOT))) {
                            entryName = "$baseName-$characterId-$suffix.json"
                            suffix++
                        }
                        zip.putNextEntry(ZipEntry(entryName))
                        zip.write(json.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }
                    zip.finish()
                }
            }
        }

    /** 清理路径分隔符与文件系统保留字符，生成受长度约束的 ZIP 条目名称。 */
    private fun safeArchiveEntryName(name: String, characterId: Long): String {
        // 限制名称只影响归档条目，不回写角色实体名称。
        val sanitized = name
            .trim()
            .map { character ->
                if (
                    character == '/' || character == '\\' || character == ':' ||
                    character == '*' || character == '?' || character == '"' ||
                    character == '<' || character == '>' || character == '|' ||
                    character.code < 0x20
                ) '_' else character
            }
            .joinToString("")
            .trim('.', ' ')
            .take(100)
        return sanitized.ifBlank { "character-$characterId" }
    }

    /**
     * 将角色导出为带角色卡元数据的 PNG。
     *
     * 非 PNG 头像会先转换格式；无有效头像时使用最小占位图承载元数据。
     */
    suspend fun exportPng(characterId: Long): ByteArray = withContext(Dispatchers.IO) {
        val character =
            mCharacterRepository.getCharacterById(characterId) ?: error("Character not found")
        val json = exportJson(characterId)
        val avatarBytes = character.avatar
            .takeIf { text -> text.isNotBlank() }
            ?.let { text -> mFileRepository.getFile(text) }
            ?.takeIf { file -> file.exists() }
            ?.readBytes()
            ?: ByteArray(0)
        val pngBytes = avatarBytes.toPngOrFallback()
        CharacterCardPngCodec.writeCharacterJson(pngBytes, json)
    }

    private fun ByteArray.toPngOrFallback(): ByteArray {
        if (CharacterCardPngCodec.isPng(this)) return this
        val bitmap = runCatching { BitmapFactory.decodeByteArray(this, 0, size) }.getOrNull()
        if (bitmap != null) {
            return ByteArrayOutputStream().use { output ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
                output.toByteArray()
            }
        }
        return OnePixelPng
    }

    private companion object {
        val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        const val MAX_ARCHIVE_ENTRIES = 1_000

        val OnePixelPng = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
            0x00,
            0x00,
            0x00,
            0x0D,
            0x49,
            0x48,
            0x44,
            0x52,
            0x00,
            0x00,
            0x00,
            0x01,
            0x00,
            0x00,
            0x00,
            0x01,
            0x08,
            0x06,
            0x00,
            0x00,
            0x00,
            0x1F,
            0x15,
            0xC4.toByte(),
            0x89.toByte(),
            0x00,
            0x00,
            0x00,
            0x0A,
            0x49,
            0x44,
            0x41,
            0x54,
            0x78,
            0x9C.toByte(),
            0x63,
            0x00,
            0x01,
            0x00,
            0x00,
            0x05,
            0x00,
            0x01,
            0x0D,
            0x0A,
            0x2D,
            0xB4.toByte(),
            0x00,
            0x00,
            0x00,
            0x00,
            0x49,
            0x45,
            0x4E,
            0x44,
            0xAE.toByte(),
            0x42,
            0x60,
            0x82.toByte(),
        )
    }
}

/** 从文件解析出的、尚未持久化的角色卡及头像来源。 */
data class CharacterCardImportDraft(
    val card: CharacterCardImport,
    val avatarSourceUri: Uri?,
    val avatarMimeType: String
)

/** 单个导入文档的解析结果，失败数量包含 ZIP 中被跳过的无效卡片。 */
data class CharacterCardArchiveReadResult(
    val drafts: List<CharacterCardImportDraft>,
    val failureCount: Int,
    val totalCount: Int
)
