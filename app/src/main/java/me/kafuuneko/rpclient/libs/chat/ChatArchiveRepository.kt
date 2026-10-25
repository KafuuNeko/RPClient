package me.kafuuneko.rpclient.libs.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.defaults.DefaultNames
import me.kafuuneko.rpclient.libs.defaults.normalizedUserName
import me.kafuuneko.rpclient.libs.llm.ImageRequestException
import me.kafuuneko.rpclient.libs.media.MessageImageRuntime
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.MessageImageEntity
import me.kafuuneko.rpclient.libs.room.model.MessageType
import me.kafuuneko.rpclient.libs.room.model.PreparedFile
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.Writer
import java.util.UUID

/**
 * 单聊归档文件的应用层协调器。
 *
 * 负责 Android URI 读写、数据库快照和原子导入；JSONL 协议细节由 [ChatArchiveCodec]
 * 处理。导入解析与角色选择阶段不写数据库，只有用户确认后 [saveImport] 才提交事务。
 */
class ChatArchiveRepository(
    private val mContext: Context,
    private val mAppDatabase: AppDatabase,
    private val mCodec: ChatArchiveCodec,
    private val mFiles: FileRepository,
    private val mImages: MessageImageRuntime,
    private val mImageStore: ChatArchiveImageStore
) {
    private val mCharacterDao = mAppDatabase.getCharacterDao()
    private val mChatSessionDao = mAppDatabase.getChatSessionDao()
    private val mChatMessageDao = mAppDatabase.getChatMessageDao()

    /**
     * 检查单聊是否包含图片附件。
     *
     * @param sessionId 单聊会话 ID。
     * @return 是否存在已保存的图片附件。
     */
    suspend fun hasImages(sessionId: Long): Boolean =
        mAppDatabase.getMessageImageDao().hasSingleSessionImages(sessionId, MessageType.Single)

    /** 将指定会话的原始 Room 数据导出到用户选择的文档 URI。 */
    suspend fun exportToUri(sessionId: Long, uri: Uri) = withContext(Dispatchers.IO) {
        val owner = mImageStore.createOwner()
        try {
            mContext.contentResolver.openOutputStream(uri)
                ?.bufferedWriter(Charsets.UTF_8)
                ?.use { writer ->
                    mFiles.withReadSnapshot {
                        writeArchive(sessionId, writer, this, owner)
                    }.also { writer.flush() }
                }
                ?: error("Cannot open chat export destination")
        } finally {
            // 包含压缩、写出失败和协程取消，不遗留试压缩的图片文件。
            mImageStore.release(owner)
        }
    }

    /**
     * 在同一 Room 读取事务中分页写出归档。
     *
     * 事务保证分页之间不会混入并发更新；每次只保留一页消息，writer 则直接接收每一行 JSON，
     * 因此内存峰值不再随整份导出文件额外复制。
     */
    private suspend fun writeArchive(
        sessionId: Long,
        writer: Writer,
        snapshot: FileRepository.ReadSnapshot,
        owner: String,
    ): Int {
        var skipped = 0
        val writtenHashes = mutableSetOf<String>()
        val archive = loadArchiveMetadata(sessionId)
        mCodec.encodeHeader(archive, writer)
        var afterCreateTime = Long.MIN_VALUE
        var afterMessageId = Long.MIN_VALUE
        while (true) {
            val messages = mChatMessageDao.getMessagePageBySessionId(
                sessionId = sessionId,
                afterCreateTime = afterCreateTime,
                afterMessageId = afterMessageId,
                limit = EXPORT_PAGE_SIZE,
            )
            if (messages.isEmpty()) return skipped
            val images = mAppDatabase.getMessageImageDao().getByMessages(
                messageType = MessageType.Single,
                messageIds = messages.map { it.id },
            ).groupBy { it.messageId }
            // 每条消息直接写出图片字节，既不改正文，也不把整批 Base64 留在内存。
            for (message in messages) {
                val attachments = images[message.id].orEmpty()
                if (attachments.isEmpty()) mCodec.encodeMessage(
                    archive,
                    message.toArchiveMessage(),
                    writer
                )
                else mCodec.encodeMessageWithImages(archive, message.toArchiveMessage(), writer) {
                    skipped += writeAttachments(attachments, snapshot, writer, owner, writtenHashes)
                }
            }
            val lastMessage = messages.last()
            afterCreateTime = lastMessage.createTime
            afterMessageId = lastMessage.id
            if (messages.size < EXPORT_PAGE_SIZE) return skipped
        }
    }

    /** 缺失文件只计数，成功写出的附件保持原顺序和实际 MIME。 */
    private suspend fun writeAttachments(
        attachments: List<MessageImageEntity>,
        snapshot: FileRepository.ReadSnapshot,
        writer: Writer,
        owner: String,
        writtenHashes: MutableSet<String>,
    ): Int {
        val context = currentCoroutineContext()
        var written = false
        var skipped = 0
        for (attachment in attachments) {
            // 快照持有文件锁，检查与复制之间不会被并发回收。
            snapshot.withFile(attachment.imageUuid) { file, entity ->
                if (file == null) skipped++
                else {
                    if (written) writer.write(",")
                    val hash = requireNotNull(entity).hash
                    if (hash in writtenHashes) {
                        ChatArchiveImageCodec.writeImage(ChatArchiveImage(hash = hash), writer)
                    } else {
                        mImageStore.writeExportImage(
                            owner,
                            file,
                            hash,
                            mImages.archiveMimeType(file),
                            writer
                        ) {
                            context.ensureActive()
                        }
                        writtenHashes.add(hash)
                    }
                    written = true
                }
            }
        }
        return skipped
    }

    /** 从 URI 读取并解析对话，但不创建会话或消息；来源缺少用户名时使用调用方身份。 */
    suspend fun readImportFromUri(
        uri: Uri,
        fallbackUserName: String = DefaultNames.USER,
    ): ChatArchive {
        var owner: String? = null
        try {
            return withContext(Dispatchers.IO) {
                val activeOwner = mImageStore.createOwner().also { text -> owner = text }
                val context = currentCoroutineContext()
                val fallbackTitle = resolveDisplayTitle(uri)
                mContext.contentResolver.openInputStream(uri)?.use { input ->
                    SizeLimitedInputStream(input, MAX_IMPORT_BYTES).reader(Charsets.UTF_8)
                        .use { reader ->
                            mCodec.decode(
                                reader = reader,
                                fallbackTitle = fallbackTitle,
                                fallbackUserName = fallbackUserName,
                                transformImage = { image ->
                                    mImageStore.stage(activeOwner, image) { context.ensureActive() }
                                },
                            ).copy(importOwner = activeOwner)
                        }
                } ?: error("Cannot read chat archive")
            }
        } catch (error: Throwable) {
            // 包含切回调用者时取消的窗口，已落盘的解析暂存也必须释放。
            withContext(NonCancellable + Dispatchers.IO) { owner?.let(mImageStore::release) }
            throw error
        }
    }

    /** 解析授权目录中的图片位置，不在选择目录时提前写入消息或文件索引。 */
    suspend fun resolveImageDirectory(archive: ChatArchive, tree: Uri): Map<String, Uri> =
        mImageStore.resolveDirectory(archive, tree)

    /** 释放不再使用的导入快照资源，取消也必须完成清理。 */
    suspend fun releaseImport(archive: ChatArchive) = withContext(NonCancellable + Dispatchers.IO) {
        archive.importOwner?.let(mImageStore::release)
    }

    /**
     * 恢复图片字节后原子提交会话、消息和附件；失败不留下半个会话或孤立文件。
     * @param archive 已解析归档，Base64 暂存只属于本次导入。
     * @param characterId 用户选定的本地角色。
     * @param imageSources 用户授权目录中解析出的资源，无法读取的外部图片跳过。
     * @return 新会话 ID 及跳过图片数。
     */
    suspend fun saveImport(
        archive: ChatArchive,
        characterId: Long,
        imageSources: Map<String, Uri> = emptyMap(),
    ): ChatArchiveImportResult = withContext(Dispatchers.IO) {
        val owner = UUID.randomUUID().toString()
        val preparation = ImportImagePreparation()
        var skipped = 0
        try {
            // 图片恢复在事务外完成；内嵌数据损坏应失败，外部资源缺失只跳过该图片。
            val attachments = archive.messages.map { message ->
                message.images.mapNotNull { image ->
                    prepareUniqueImage(archive, owner, image, imageSources, preparation)
                        ?: run {
                            skipped++
                            null
                        }
                }
            }
            mFiles.mutate(preparation.byHash.values.toList()) {
                val sessionId = insertSession(archive, characterId)
                insertMessages(archive, sessionId, attachments)
                ChatArchiveImportResult(sessionId, skipped)
            }
        } finally {
            preparation.all.forEach { preparedFile -> mFiles.releasePrepared(preparedFile) }
        }
    }

    /** 一次导入的唯一文件凭据和清理账本；引用解析不能读取其他归档的缓存。 */
    private class ImportImagePreparation {
        val all = mutableListOf<PreparedFile>()
        val byHash = mutableMapOf<String, PreparedFile>()
        val byResource = mutableMapOf<String, PreparedFile>()
    }

    /** 按消息和附件顺序准备唯一文件；未命中的纯 hash 引用不会被后续数据回填。 */
    private suspend fun prepareUniqueImage(
        archive: ChatArchive,
        owner: String,
        image: ChatArchiveImage,
        sources: Map<String, Uri>,
        preparation: ImportImagePreparation,
    ): PreparedFile? {
        image.resourceKey?.let { key -> preparation.byResource[key]?.let { preparedFile -> return preparedFile } }
        if (image.data == null && image.resourceKey == null && image.sourceUrl == null) {
            return preparation.byHash[image.hash]
        }
        val candidate = prepareImage(archive, owner, image, sources) ?: return null
        // 先登记清理责任再校验；伪造摘要不能泄漏已准备的文件或复用错误内容。
        preparation.all += candidate
        require(image.hash == null || candidate.file.hash == image.hash) { "Archive image hash mismatch" }
        val unique = preparation.byHash.getOrPut(candidate.file.hash) { candidate }
        image.resourceKey?.let { preparation.byResource[it] = unique }
        return unique
    }

    /** 内嵌图片优先，外部路径只能使用已经解析出的 DocumentsProvider URI。 */
    private suspend fun prepareImage(
        archive: ChatArchive,
        owner: String,
        image: ChatArchiveImage,
        sources: Map<String, Uri>,
    ): PreparedFile? {
        image.resourceKey?.let { key ->
            return mImages.prepareArchive(
                owner,
                mImageStore.open(requireNotNull(archive.importOwner), key)
            )
        }
        // 小型程序化导入也支持直接提供 Base64，不经过页面暂存。
        if (image.data != null) {
            return mImages.prepareArchive(owner, ChatArchiveImagePayload.open(image))
        }
        val uri = sources[image.sourceUrl] ?: return null
        return try {
            mContext.contentResolver.openInputStream(uri)?.let { mImages.prepareArchive(owner, it) }
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            null
        } catch (error: SecurityException) {
            currentCoroutineContext().ensureActive()
            null
        } catch (error: ImageRequestException) {
            currentCoroutineContext().ensureActive()
            null
        }
    }

    /** 创建会话时不复用来源安装的世界书及状态主键，避免同号误关联。 */
    private suspend fun insertSession(archive: ChatArchive, characterId: Long): Long {
        requireNotNull(mCharacterDao.getCharacterById(characterId)) { "Selected character no longer exists" }
        // 只恢复可移植设置；外部数据库的世界书主键及运行状态必须重新建立。
        return mChatSessionDao.insertOrReplace(
            data = ChatSession(
                characterId = characterId,
                createTime = archive.createTime,
                latestTime = maxOf(
                    archive.latestTime,
                    archive.messages.lastOrNull()?.createTime ?: archive.createTime
                ),
                lorebookEntrySet = "[]",
                title = archive.title.ifBlank { DefaultNames.IMPORTED_CHAT },
                userNote = archive.userNote,
                userName = archive.userName.normalizedUserName(),
                userDescription = archive.userDescription,
                creatorNotes = archive.creatorNotes,
                worldInfoStateJson = "{}",
                autoSummaryPaused = archive.autoSummaryPaused,
                includeCharacterExamples = archive.includeCharacterExamples,
            ).withNormalizedCreatorNotes(),
        )
    }

    /** 在文件事务内恢复普通消息及有序附件；导入允许酒馆角色消息携带图片。 */
    private suspend fun FileRepository.Mutation.insertMessages(
        archive: ChatArchive,
        sessionId: Long,
        images: List<List<PreparedFile>>,
    ) {
        val ids = mutableListOf<Long>()
        val usedUuids = mutableSetOf<String>()
        for ((index, message) in archive.messages.withIndex()) {
            val id = mChatMessageDao.insertOrReplace(
                data = ChatMessage(
                    sessionId = sessionId,
                    createTime = message.createTime,
                    source = message.role.toEntitySource(),
                    content = message.content,
                ),
            )
            ids += id
            // 所有 UUID 均来自同一文件事务已提交的凭据，不信任归档提供的本地主键。
            mAppDatabase.getMessageImageDao().insertAll(
                images = images[index].mapIndexed { position, image ->
                    // 每个附件持独立 UUID，重复图片只共享 hash 对应的物理文件。
                    val uuid = if (usedUuids.add(image.file.uuid)) image.file.uuid
                    else copyReference(image.file.uuid).uuid
                    MessageImageEntity(
                        messageType = MessageType.Single,
                        messageId = id,
                        position = position,
                        imageUuid = uuid,
                    )
                },
            )
        }
        archive.summary?.let { summary ->
            mChatMessageDao.insertOrReplace(
                data = ChatMessage(
                    sessionId = sessionId,
                    createTime = summary.createTime,
                    source = ChatMessage.Source.Summary,
                    content = summary.content,
                    coveredMessageId = ids.getOrNull(summary.coveredMessageIndex) ?: 0L,
                ),
            )
        }
    }

    private suspend fun loadArchiveMetadata(sessionId: Long): ChatArchive {
        val session = requireNotNull(mChatSessionDao.getSessionById(sessionId)) {
            "Chat session not found"
        }
        val character = requireNotNull(mCharacterDao.getCharacterById(session.characterId)) {
            "Chat character not found"
        }
        val summary = mChatMessageDao.getLatestSummaryBySessionId(sessionId)
        val coveredMessageIndex = summary?.coveredMessageId
            ?.takeIf { it > 0L }
            ?.let { messageId ->
                mChatMessageDao.getMessageIndexBySessionId(sessionId, messageId)
            }
            ?: -1
        return ChatArchive(
            title = session.title,
            createTime = session.createTime,
            latestTime = session.latestTime,
            userName = session.userName,
            userDescription = session.userDescription,
            userNote = session.userNote,
            creatorNotes = session.creatorNotes,
            lorebookEntrySet = session.lorebookEntrySet,
            worldInfoStateJson = session.worldInfoStateJson,
            autoSummaryPaused = session.autoSummaryPaused,
            includeCharacterExamples = session.includeCharacterExamples,
            characterNameHint = character.name,
            characterFingerprint = ChatCharacterMatcher.fingerprintOf(character),
            messages = emptyList(),
            summary = summary?.let { message ->
                ChatArchiveSummary(
                    content = message.content,
                    createTime = message.createTime,
                    coveredMessageIndex = coveredMessageIndex,
                )
            },
        )
    }

    private fun ChatMessage.toArchiveMessage(): ChatArchiveMessage {
        return ChatArchiveMessage(
            createTime = createTime,
            role = source.toArchiveRole(),
            content = content,
        )
    }

    private fun resolveDisplayTitle(uri: Uri): String {
        val displayName = runCatching {
            mContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        }.getOrNull()
        return displayName
            ?.removeSuffix(".jsonl")
            ?.removeSuffix(".json")
            ?.takeIf { it.isNotBlank() }
            ?: DefaultNames.IMPORTED_CHAT
    }

    private fun ChatMessage.Source.toArchiveRole(): ChatArchiveMessageRole {
        return when (this) {
            ChatMessage.Source.User -> ChatArchiveMessageRole.User
            ChatMessage.Source.Char -> ChatArchiveMessageRole.Character
            ChatMessage.Source.System -> ChatArchiveMessageRole.Narrator
            ChatMessage.Source.Summary -> error("Summary is stored in archive metadata")
        }
    }

    private fun ChatArchiveMessageRole.toEntitySource(): ChatMessage.Source {
        return when (this) {
            ChatArchiveMessageRole.User -> ChatMessage.Source.User
            ChatArchiveMessageRole.Character -> ChatMessage.Source.Char
            ChatArchiveMessageRole.Narrator -> ChatMessage.Source.System
        }
    }

    private class SizeLimitedInputStream(
        input: InputStream,
        private val mMaxBytes: Long
    ) : FilterInputStream(input) {
        private var mTotalBytes = 0L

        override fun read(): Int {
            return super.read().also { value ->
                if (value >= 0) recordBytes(count = 1)
            }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            return super.read(buffer, offset, length).also { count ->
                if (count > 0) recordBytes(count)
            }
        }

        private fun recordBytes(count: Int) {
            mTotalBytes += count
            require(mTotalBytes <= mMaxBytes) { "Chat archive is too large" }
        }
    }

    private companion object {
        const val EXPORT_PAGE_SIZE = 256
        const val MAX_IMPORT_BYTES = 512L * 1024 * 1024
    }
}
