package me.kafuuneko.rpclient.libs.character

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.gson.Gson
import java.io.BufferedInputStream
import java.io.File
import java.io.OutputStream
import java.io.PushbackInputStream
import java.util.Locale
import java.util.UUID
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
import me.kafuuneko.rpclient.libs.room.model.PreparedFile
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
    private val mArchiveReader = CharacterCardArchiveReader(
        mMapper = mMapper,
        mStageAvatar = { bytes ->
            bytes.inputStream().use { input ->
                mFileRepository.prepareStream(
                    ownerId = UUID.randomUUID().toString(),
                    input = input,
                    mimeType = "image/png",
                    maxBytes = CharacterCardArchiveLimits().entryBytes.toLong(),
                )
            }
        },
        mReleaseAvatar = mFileRepository::releasePrepared,
    )

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
     * 读取单张角色卡或批量导出的 ZIP，归档头像暂存于文件仓库。
     * - 先检查文件签名，普通卡片继续使用既有 JSON/PNG 解码入口。
     * - 按 ZIP 元数据跳过非角色卡文件和不安全路径，单个损坏条目不阻止后续卡片解析。
     * - 压缩数据、单卡和累计解压字节均受预算限制，超限不会提交半成品实体。
     *
     * @param uri 用户选择的角色卡或 ZIP 文档。
     * @return 当前文档解析出的草稿与失败数量；调用方须保存或通过 [releaseImports] 释放草稿。
     */
    suspend fun readImportItemsFromUri(uri: Uri): CharacterCardArchiveReadResult {
        var completed: CharacterCardArchiveReadResult? = null
        try {
            return withContext(Dispatchers.IO) {
                // 签名检查只负责分流，普通角色卡仍由原有入口处理。
                val source = mContext.contentResolver.openInputStream(uri)
                    ?: error("Cannot read character card")
                PushbackInputStream(BufferedInputStream(source), ZIP_SIGNATURE.size).use { input ->
                    val signature = ByteArray(ZIP_SIGNATURE.size)
                    var signatureSize = 0
                    while (signatureSize < signature.size) {
                        val count =
                            input.read(signature, signatureSize, signature.size - signatureSize)
                        if (count < 0) break
                        signatureSize += count
                    }
                    if (signatureSize > 0) input.unread(signature, 0, signatureSize)
                    if (signatureSize == ZIP_SIGNATURE.size && signature.contentEquals(ZIP_SIGNATURE)) {
                        return@withContext mArchiveReader.read(input, mContext.cacheDir)
                            .also { completed = it }
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
                        CharacterCardArchiveReadResult(
                            emptyList(),
                            failureCount = 1,
                            totalCount = 1
                        )
                    }
                }
            }
        } catch (error: Throwable) {
            // IO 已解析完毕但尚未交还调用方时也可能取消，暂存凭据仍由此处负责回收。
            completed?.let { releaseImports(it.drafts) }
            throw error
        }
    }

    /**
     * 释放尚未提交的导入头像；已经保存的角色文件不会被删除。
     * @param drafts 本批次解析出的全部草稿，允许包含已经成功保存的条目。
     */
    suspend fun releaseImports(drafts: List<CharacterCardImportDraft>) {
        withContext(NonCancellable + Dispatchers.IO) {
            drafts.mapNotNull { it.stagedAvatar }.forEach { mFileRepository.releasePrepared(it) }
        }
    }

    /**
     * 保存已解析的角色卡，并返回新角色 ID。
     *
     * 原图先暂存，头像索引、内嵌世界书和角色在同一事务中提交。
     * @param draft 已解析的角色卡与待提交头像。
     * @return 新角色 ID。
     */
    suspend fun saveImport(draft: CharacterCardImportDraft): Long = withContext(Dispatchers.IO) {
        // ZIP 已提供暂存凭据，单卡 URI 也使用相同的文件事务入口。
        val avatar = draft.stagedAvatar ?: draft.avatarSourceUri?.let { uri ->
            mFileRepository.prepareFile(
                ownerId = UUID.randomUUID().toString(),
                uri = uri,
                mimeType = draft.avatarMimeType,
                maxBytes = Long.MAX_VALUE,
            )
        }
        try {
            // 内层仓库继续复用各自的写入规则，由外层事务统一决定是否提交。
            mFileRepository.mutate(listOfNotNull(avatar)) {
                persistImportedCharacter(
                    card = draft.card,
                    avatarUuid = avatar?.file?.uuid.orEmpty(),
                )
            }
        } finally {
            avatar?.let { mFileRepository.releasePrepared(it) }
        }
    }

    /** 在文件事务内保存内嵌世界书和角色，正则脚本随角色写入。 */
    private suspend fun persistImportedCharacter(
        card: CharacterCardImport,
        avatarUuid: String
    ): Long {
        // 先生成世界书 ID，角色引用与世界书条目在同一事务中可见。
        val lorebookId = card.embeddedLorebook?.let { book ->
            val name = book.lorebook.name.ifBlank { "${card.character.name}'s Lorebook" }
            mLorebookRepository.saveImport(
                parsed = book.copy(lorebook = book.lorebook.copy(name = name)),
            )
        } ?: 0L
        return mCharacterRepository.saveCharacter(
            character = card.character.copy(
                avatar = avatarUuid,
                characterLorebookId = lorebookId,
                extensionsJson = mRegexCodec.injectIntoCharacterExtensions(
                    json = card.character.extensionsJson,
                    scripts = card.regexScripts,
                ),
            ),
        )
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

    /** JSON、PNG 与 ZIP 从同一角色快照生成正文，确保文件名与角色名一致。 */
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

    /**
     * 按选择的格式写入文档，PNG 必须使用实际头像，不能向图片文档写入 JSON。
     * @param characterId 待导出的角色 ID。
     * @param format 文件创建器使用的格式。
     * @param uri 用户选择的目标文档。
     */
    suspend fun exportToUri(characterId: Long, format: CharacterCardExportFormat, uri: Uri) =
        withContext(Dispatchers.IO) {
            val character = mCharacterRepository.getCharacterById(characterId)
                ?: error("Character not found")
            val json = encodeCharacter(character)
            if (format == CharacterCardExportFormat.Json) {
                val target = mContext.contentResolver.openOutputStream(uri, "wt")
                    ?: error("Cannot open character export destination")
                target.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
                return@withContext
            }

            // 完成 PNG 校验及元数据写入后再打开目标文档，失败时只清理应用缓存。
            val png = File.createTempFile("rpclient-character-export-", ".png", mContext.cacheDir)
            try {
                png.outputStream().buffered().use { writePng(character.avatar, json, it) }
                copyExportToUri(png, uri)
            } finally {
                png.delete()
            }
        }

    /**
     * 将所选角色卡写入 ZIP，选择 PNG 时未设置头像的角色退回 JSON 条目。
     * @param characterIds 用户选中的角色 ID，重复项只导出一次。
     * @param format 有头像角色使用的导出格式。
     * @param uri 系统文件创建器返回的 ZIP 文档位置。
     */
    suspend fun exportArchiveToUri(
        characterIds: List<Long>,
        format: CharacterCardExportFormat,
        uri: Uri
    ) =
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
                    writeArchiveCards(ids, format, zip, limits)
                }
                if (archive.length() > limits.archiveBytes) throw CharacterArchiveExportLimitException()
                copyExportToUri(archive, uri)
            } finally {
                archive.delete()
            }
        }

    /** 以与导入相同的实际文件字节预算写卡，条目名按忽略大小写去重。 */
    private suspend fun writeArchiveCards(
        ids: List<Long>,
        format: CharacterCardExportFormat,
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
            // 只对未配置头像的角色回退；已配置但损坏的头像应报错，不能静默生成占位图。
            val entryFormat =
                if (character.avatar.isBlank()) CharacterCardExportFormat.Json else format
            // 同名角色仍分别占用独立条目，文件名变化不会修改角色本身。
            val baseName = safeArchiveEntryName(character.name, id)
            var entryName = "$baseName.${entryFormat.extension}"
            var suffix = 2
            while (!usedNames.add(entryName.lowercase(Locale.ROOT))) {
                entryName = "$baseName-$id-$suffix.${entryFormat.extension}"
                suffix++
            }
            zip.putNextEntry(ZipEntry(entryName))
            // 计量 PNG 重写后的实际字节，条目和整包均在转发给 ZIP 前校验。
            val entry = ArchiveEntryOutput(
                mTarget = zip,
                mMaxBytes = minOf(limits.entryBytes.toLong(), limits.totalEntryBytes - totalBytes),
            )
            when (entryFormat) {
                CharacterCardExportFormat.Png -> writePng(character.avatar, json, entry)
                CharacterCardExportFormat.Json -> entry.write(json.toByteArray(Charsets.UTF_8))
            }
            totalBytes += entry.byteCount
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

    /** 以小缓冲向用户选定的文档写入已生成的临时文件。 */
    private suspend fun copyExportToUri(file: File, uri: Uri) {
        currentCoroutineContext().ensureActive()
        val target = mContext.contentResolver.openOutputStream(uri, "wt")
            ?: error("Cannot open character export destination")
        target.use { output -> file.inputStream().use { input -> input.copyTo(output) } }
    }

    /** 原生 PNG 保留像素及附加块，非 PNG 头像先进行有界解码。 */
    private suspend fun writePng(avatarUuid: String, json: String, output: OutputStream) {
        require(avatarUuid.isNotBlank()) { "Character has no avatar" }
        val context = currentCoroutineContext()
        val written = mFileRepository.withFileLease(avatarUuid) { avatar ->
            avatar.inputStream().buffered().use { input ->
                val signature = ByteArray(PNG_SIGNATURE_BYTES)
                input.mark(signature.size)
                val size = input.read(signature)
                if (size != signature.size || !CharacterCardPngCodec.isPng(signature)) {
                    return@use false
                }
                // 在文件租约内写出原图，删除角色时也不会回收正在导出的文件。
                input.reset()
                CharacterCardPngCodec.writeCharacterJson(input, output, json, context::ensureActive)
                true
            }
        }
        if (!written) writeConvertedAvatar(avatarUuid, json, output)
    }

    /** 非 PNG 头像使用有界位图转码，缓存文件和位图随本次操作释放。 */
    private suspend fun writeConvertedAvatar(
        avatarUuid: String,
        json: String,
        output: OutputStream
    ) {
        val converted = File.createTempFile("rpclient-character-avatar-", ".png", mContext.cacheDir)
        try {
            // 转码位图仅在写出像素时持有，注入角色元数据时只需文件输入流。
            convertAvatarToPng(avatarUuid, converted)
            val context = currentCoroutineContext()
            converted.inputStream().buffered().use { input ->
                CharacterCardPngCodec.writeCharacterJson(input, output, json, context::ensureActive)
            }
        } finally {
            converted.delete()
        }
    }

    /** 将普通头像转为内存占用可预期的 PNG。 */
    private suspend fun convertAvatarToPng(avatarUuid: String, destination: File) {
        val bitmap = mFileRepository.loadSampledBitmap(
            uuid = avatarUuid,
            requestedWidthPx = MAX_CONVERTED_AVATAR_EDGE,
            requestedHeightPx = MAX_CONVERTED_AVATAR_EDGE,
        )
            ?: error("Cannot decode character avatar")
        try {
            destination.outputStream().buffered().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Cannot encode character avatar"
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** 逐条目计量解压大小，整个归档共享总字节预算。 */
    private class ArchiveEntryOutput(
        private val mTarget: OutputStream,
        private val mMaxBytes: Long,
    ) : OutputStream() {
        var byteCount: Long = 0
            private set

        /** 校验单字节写入后转发给当前 ZIP 条目。 */
        override fun write(value: Int) {
            reserve(1)
            mTarget.write(value)
        }

        /** 校验数据块后转发，限制以磁盘上的未压缩内容计量。 */
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            reserve(length)
            mTarget.write(bytes, offset, length)
        }

        /** 超限时由外层丢弃整个临时归档。 */
        private fun reserve(length: Int) {
            if (length > mMaxBytes - byteCount) throw CharacterArchiveExportLimitException()
            byteCount += length
        }
    }

    private companion object {
        val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        const val PNG_SIGNATURE_BYTES = 8
        const val MAX_CONVERTED_AVATAR_EDGE = 2048
    }
}

/** 从文件解析出的、尚未持久化的角色卡及头像来源。 */
data class CharacterCardImportDraft(
    val card: CharacterCardImport,
    val avatarSourceUri: Uri?,
    val avatarMimeType: String,
    /** ZIP 头像的磁盘暂存凭据，避免多个归档的原图累积占用堆内存。 */
    val stagedAvatar: PreparedFile? = null
)

/** 单个导入文档的解析结果，失败数量包含 ZIP 中被跳过的无效卡片。 */
data class CharacterCardArchiveReadResult(
    val drafts: List<CharacterCardImportDraft>,
    val failureCount: Int,
    val totalCount: Int
)
