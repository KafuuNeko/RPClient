package me.kafuuneko.rpclient.libs.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.defaults.DefaultNames
import me.kafuuneko.rpclient.libs.defaults.normalizedUserName
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.model.MessageType
import java.io.FilterInputStream
import java.io.InputStream
import java.io.Writer

/**
 * 单聊归档文件的应用层协调器。
 *
 * 负责 Android URI 读写、数据库快照和原子导入；JSONL 协议细节由 [ChatArchiveCodec]
 * 处理。导入解析与角色选择阶段不写数据库，只有用户确认后 [saveImport] 才提交事务。
 */
class ChatArchiveRepository(
    private val mContext: Context,
    private val mAppDatabase: AppDatabase,
    private val mCodec: ChatArchiveCodec
) {
    private val mCharacterDao = mAppDatabase.getCharacterDao()
    private val mChatSessionDao = mAppDatabase.getChatSessionDao()
    private val mChatMessageDao = mAppDatabase.getChatMessageDao()

    /** 文字导出前检查是否需要提示用户图片遗漏。 */
    suspend fun hasImages(sessionId: Long): Boolean = mAppDatabase.getMessageImageDao().hasSingleSessionImages(sessionId)

    /** 将指定会话的原始 Room 数据导出到用户选择的文档 URI。 */
    suspend fun exportToUri(sessionId: Long, uri: Uri) = withContext(Dispatchers.IO) {
        mContext.contentResolver.openOutputStream(uri)
            ?.bufferedWriter(Charsets.UTF_8)
            ?.use { writer ->
                mAppDatabase.withTransaction {
                    writeArchive(sessionId, writer)
                }
                writer.flush()
            }
            ?: error("Cannot open chat export destination")
    }

    /**
     * 在同一 Room 读取事务中分页写出归档。
     *
     * 事务保证分页之间不会混入并发更新；每次只保留一页消息，writer 则直接接收每一行 JSON，
     * 因此内存峰值不再随整份导出文件额外复制。
     */
    private suspend fun writeArchive(sessionId: Long, writer: Writer) {
        val archive = loadArchiveMetadata(sessionId)
        mCodec.encodeHeader(archive, writer)
        var afterCreateTime = Long.MIN_VALUE
        var afterMessageId = Long.MIN_VALUE
        while (true) {
            val messages = mChatMessageDao.getMessagePageBySessionId(
                sessionId = sessionId,
                afterCreateTime = afterCreateTime,
                afterMessageId = afterMessageId,
                limit = EXPORT_PAGE_SIZE
            )
            if (messages.isEmpty()) return
            val images = mAppDatabase.getMessageImageDao().getByMessages(
                MessageType.Single, messages.map { it.id }).groupBy { it.messageId }
            messages.forEach { message ->
                val count = images[message.id].orEmpty().size
                val text = if (count == 0) message.content else message.content + "\n[Images omitted: $count]"
                mCodec.encodeMessage(archive, message.copy(content = text).toArchiveMessage(), writer)
            }
            val lastMessage = messages.last()
            afterCreateTime = lastMessage.createTime
            afterMessageId = lastMessage.id
            if (messages.size < EXPORT_PAGE_SIZE) return
        }
    }

    /** 从 URI 读取并解析对话，但不创建会话或消息；来源缺少用户名时使用调用方身份。 */
    suspend fun readImportFromUri(
        uri: Uri,
        fallbackUserName: String = DefaultNames.USER
    ): ChatArchive = withContext(Dispatchers.IO) {
        val fallbackTitle = resolveDisplayTitle(uri)
        mContext.contentResolver.openInputStream(uri)?.use { input ->
            SizeLimitedInputStream(input, MAX_IMPORT_BYTES.toLong()).reader(Charsets.UTF_8).use {
                mCodec.decode(
                    reader = it,
                    fallbackTitle = fallbackTitle,
                    fallbackUserName = fallbackUserName
                )
            }
        } ?: error("Cannot read chat archive")
    }

    /**
     * 将已解析归档绑定到用户选定的角色，并在一个 Room 事务中保存。
     *
     * 来源文件中的世界书条目和 timed-effect 状态使用本地自增 ID，无法跨安装验证，
     * 因此不会自动恢复，避免碰巧同号的资源被误绑定。
     */
    suspend fun saveImport(archive: ChatArchive, characterId: Long): Long =
        withContext(Dispatchers.IO) {
            mAppDatabase.withTransaction {
                requireNotNull(mCharacterDao.getCharacterById(characterId)) {
                    "Selected character no longer exists"
                }
                val sessionId = mChatSessionDao.insertOrReplace(
                    ChatSession(
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
                        autoSummaryPaused = archive.autoSummaryPaused
                    ).withNormalizedCreatorNotes()
                )
                val insertedMessageIds = if (archive.messages.isEmpty()) {
                    emptyList()
                } else {
                    mChatMessageDao.insertOrReplaceAll(
                        archive.messages.map { message ->
                            ChatMessage(
                                sessionId = sessionId,
                                createTime = message.createTime,
                                source = message.role.toEntitySource(),
                                content = message.content
                            )
                        }
                    )
                }
                archive.summary?.let { summary ->
                    mChatMessageDao.insertOrReplace(
                        ChatMessage(
                            sessionId = sessionId,
                            createTime = summary.createTime,
                            source = ChatMessage.Source.Summary,
                            content = summary.content,
                            coveredMessageId = insertedMessageIds
                                .getOrNull(summary.coveredMessageIndex)
                                ?: 0L
                        )
                    )
                }
                sessionId
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
            characterNameHint = character.name,
            characterFingerprint = ChatCharacterMatcher.fingerprintOf(character),
            messages = emptyList(),
            summary = summary?.let {
                ChatArchiveSummary(
                    content = it.content,
                    createTime = it.createTime,
                    coveredMessageIndex = coveredMessageIndex
                )
            }
        )
    }

    private fun ChatMessage.toArchiveMessage(): ChatArchiveMessage {
        return ChatArchiveMessage(
            createTime = createTime,
            role = source.toArchiveRole(),
            content = content
        )
    }

    private fun resolveDisplayTitle(uri: Uri): String {
        val displayName = runCatching {
            mContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
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
                if (value >= 0) recordBytes(1)
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
        const val MAX_IMPORT_BYTES = 32 * 1024 * 1024
    }
}
