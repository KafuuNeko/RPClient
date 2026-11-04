package me.kafuuneko.rpclient.libs.character

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.gson.Gson
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PushbackInputStream
import java.util.Locale
import java.util.zip.ZipEntry
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
import me.kafuuneko.rpclient.libs.room.entity.Character
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
    private val mArchiveReader = CharacterCardArchiveReader(mMapper)

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
     * - 压缩数据、单卡和累计解压字节均受预算限制，超限不会提交半成品实体。
     *
     * @param uri 用户选择的角色卡或 ZIP 文档。
     * @return 当前文档解析出的草稿与失败数量。
     */
    suspend fun readImportItemsFromUri(uri: Uri): CharacterCardArchiveReadResult =
        withContext(Dispatchers.IO) {
            // 签名检查只负责分流，普通角色卡仍由原有入口处理。
            val source = mContext.contentResolver.openInputStream(uri)
                ?: error("Cannot read character card")
            PushbackInputStream(BufferedInputStream(source), ZIP_SIGNATURE.size).use { input ->
                val signature = ByteArray(ZIP_SIGNATURE.size)
                var signatureSize = 0
                while (signatureSize < signature.size) {
                    val count = input.read(signature, signatureSize, signature.size - signatureSize)
                    if (count < 0) break
                    signatureSize += count
                }
                if (signatureSize > 0) input.unread(signature, 0, signatureSize)
                if (signatureSize == ZIP_SIGNATURE.size && signature.contentEquals(ZIP_SIGNATURE)) {
                    return@withContext mArchiveReader.read(input, mContext.cacheDir)
                }
                // 普通 JSON/PNG 沿用原有入口；重开 URI 前释放签名检查占用的流。
                input.close()
                try {
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
        }

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
        encodeCharacter(character)
    }

    /** JSON 与 ZIP 从同一角色快照生成正文，确保文件名与角色名一致。 */
    private suspend fun encodeCharacter(character: Character): String {
        val regexScripts = mRegexRepository.getScripts(
            target = RegexScriptTarget(RegexScriptScope.Character, character.id),
        )
        return mMapper.toV2Json(
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
            val ids = characterIds.distinct()
            val limits = CharacterCardArchiveLimits()
            require(ids.isNotEmpty()) { "No characters selected" }
            if (ids.size > limits.entries) throw CharacterArchiveExportLimitException()

            // 先封包并计量，避免发现后续卡片超限时目标文件已有部分内容。
            val archive =
                File.createTempFile("rpclient-character-export-", ".zip", mContext.cacheDir)
            try {
                ZipOutputStream(archive.outputStream().buffered()).use { zip ->
                    writeArchiveCards(ids, zip, limits)
                }
                if (archive.length() > limits.archiveBytes) throw CharacterArchiveExportLimitException()
                currentCoroutineContext().ensureActive()
                val output = mContext.contentResolver.openOutputStream(uri, "wt")
                    ?: error("Cannot open character archive destination")
                output.use { target -> archive.inputStream().use { it.copyTo(target) } }
                Unit
            } finally {
                archive.delete()
            }
        }

    /** 以与导入相同的 UTF-8 字节预算写卡，条目名按忽略大小写去重。 */
    private suspend fun writeArchiveCards(
        ids: List<Long>,
        zip: ZipOutputStream,
        limits: CharacterCardArchiveLimits
    ) {
        val usedNames = mutableSetOf<String>()
        var totalBytes = 0L
        for (id in ids) {
            currentCoroutineContext().ensureActive()
            val character =
                mCharacterRepository.getCharacterById(id) ?: error("Character not found")
            val json = encodeCharacter(character)
            if (json.length > limits.entryBytes) throw CharacterArchiveExportLimitException()
            val bytes = json.toByteArray(Charsets.UTF_8)
            totalBytes += bytes.size
            if (bytes.size > limits.entryBytes || totalBytes > limits.totalJsonBytes) {
                throw CharacterArchiveExportLimitException()
            }
            // 同名角色仍分别占用独立条目，文件名变化不会修改角色本身。
            val baseName = safeArchiveEntryName(character.name, id)
            var entryName = "$baseName.json"
            var suffix = 2
            while (!usedNames.add(entryName.lowercase(Locale.ROOT))) {
                entryName = "$baseName-$id-$suffix.json"
                suffix++
            }
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(bytes)
            zip.closeEntry()
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
