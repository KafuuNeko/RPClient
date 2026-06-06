package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.withTransaction
import com.google.gson.Gson
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.utils.takeIfNotBlank

/**
 * 构建对话上下文时使用的总结快照及其后的普通消息。
 *
 * @property summary 当前生效的最新总结快照。
 * @property messagesAfterSummary 未被该总结覆盖的普通消息。
 * @property totalMessageCount 会话中的普通消息总数。
 */
data class ChatSummaryContext(
    val summary: ChatMessage?,
    val messagesAfterSummary: List<ChatMessage>,
    val totalMessageCount: Int
)

class ChatRepository(
    private val mAppDatabase: AppDatabase,
    private val mGson: Gson
) {
    private val mChatSessionDao = mAppDatabase.getChatSessionDao()
    private val mChatMessageDao = mAppDatabase.getChatMessageDao()
    private val mCharacterDao = mAppDatabase.getCharacterDao()

    /**
     * 获取所有会话。
     *
     * @return 按最近活跃时间倒序排列的会话列表。
     */
    suspend fun getAllSessions(): List<ChatSession> {
        return mChatSessionDao.getAllSessions()
    }

    /**
     * 根据角色 id 获取该角色下的所有会话。
     *
     * @param characterId 角色 id。
     * @return 按最近活跃时间倒序排列的会话列表。
     */
    suspend fun getSessionsByCharacterId(characterId: Long): List<ChatSession> {
        return mChatSessionDao.getSessionsByCharacterId(characterId)
    }

    /**
     * 根据会话 id 获取会话详情。
     *
     * @param id 会话 id。
     * @return 匹配的会话；如果不存在则返回 null。
     */
    suspend fun getSessionById(id: Long): ChatSession? {
        return mChatSessionDao.getSessionById(id)
    }

    /**
     * 保存会话。
     *
     * 当 id 为 0 时创建新会话；否则更新已有会话。
     *
     * @param session 要保存的会话。
     * @return 保存后的会话 id。
     */
    suspend fun saveSession(session: ChatSession): Long {
        val normalizedSession = session.withNormalizedCreatorNotes()
        if (session.id == 0L) {
            return mChatSessionDao.insertOrReplace(normalizedSession)
        }
        mChatSessionDao.update(normalizedSession)
        return session.id
    }

    /**
     * 创建新的聊天会话，同时附带开场白消息。
     */
    suspend fun createSessionWithFirstMessage(
        characterId: Long,
        title: String,
        userNote: String,
        lorebookEntryIds: List<Long>,
        firstMessageContent: String?,
        creatorNotes: String? = null,
        createTime: Long = System.currentTimeMillis()
    ): Long {
        return mAppDatabase.withTransaction {
            val sessionId = mChatSessionDao.insertOrReplace(
                ChatSession(
                    characterId = characterId,
                    createTime = createTime,
                    latestTime = createTime,
                    lorebookEntrySet = lorebookEntryIds.toLorebookEntrySetJson(),
                    title = title,
                    userNote = userNote,
                    creatorNotes = creatorNotes
                )
            )
            firstMessageContent?.takeIf { it.isNotBlank() }?.let {
                mChatMessageDao.insertOrReplace(
                    ChatMessage(
                        sessionId = sessionId,
                        createTime = createTime,
                        source = ChatMessage.Source.Char,
                        content = it
                    )
                )
            }
            sessionId
        }
    }

    /**
     * 创建新的聊天会话。
     *
     * 世界书条目通过 id 列表传入，由仓库统一序列化为持久化字段。
     */
    suspend fun createSession(
        characterId: Long,
        title: String,
        userNote: String,
        lorebookEntryIds: List<Long>,
        creatorNotes: String? = null,
        createTime: Long = System.currentTimeMillis()
    ): Long {
        return saveSession(
            ChatSession(
                characterId = characterId,
                createTime = createTime,
                latestTime = createTime,
                lorebookEntrySet = lorebookEntryIds.toLorebookEntrySetJson(),
                title = title,
                userNote = userNote,
                creatorNotes = creatorNotes
            )
        )
    }

    /**
     * 从已有会话复制一段消息前缀，创建一个新的独立会话作为分支。
     *
     * 新分支只复制到指定消息为止的历史，并复制该位置有效的最新摘要快照。
     */
    suspend fun createBranchSession(
        sourceSessionId: Long,
        throughMessageId: Long,
        title: String,
        createTime: Long = System.currentTimeMillis()
    ): Long {
        return mAppDatabase.withTransaction {
            val sourceSession = mChatSessionDao.getSessionById(sourceSessionId) ?: return@withTransaction 0L
            val sourceMessages = mChatMessageDao.getMessagesBySessionId(sourceSessionId)
            val branchMessages = sourceMessages.takeWhileInclusive { it.id != throughMessageId }
            if (branchMessages.none { it.id == throughMessageId }) return@withTransaction 0L
            val sourceSummary = mChatMessageDao.getLatestSummaryAtOrBefore(sourceSessionId, throughMessageId)

            val branchSessionId = mChatSessionDao.insertOrReplace(
                sourceSession.copy(
                    id = 0L,
                    createTime = createTime,
                    latestTime = createTime,
                    title = title,
                    worldInfoStateJson = "{}"
                ).withNormalizedCreatorNotes()
            )
            val copiedMessages = branchMessages.mapIndexed { index, message ->
                message.copy(
                    id = 0L,
                    sessionId = branchSessionId,
                    createTime = createTime + index,
                    coveredMessageId = null
                )
            }
            val insertedMessageIds = if (copiedMessages.isNotEmpty()) {
                mChatMessageDao.insertOrReplaceAll(copiedMessages)
            } else {
                emptyList()
            }
            val copiedIdBySourceId = branchMessages.map { it.id }.zip(insertedMessageIds).toMap()
            if (sourceSummary != null) {
                val copiedBoundaryId = sourceSummary.coveredMessageId
                    ?.takeIf { it != 0L }
                    ?.let(copiedIdBySourceId::get)
                    ?: 0L
                mChatMessageDao.insertOrReplace(
                    sourceSummary.copy(
                        id = 0L,
                        sessionId = branchSessionId,
                        createTime = createTime + copiedMessages.size,
                        coveredMessageId = copiedBoundaryId
                    )
                )
            }
            branchSessionId
        }
    }

    /**
     * 更新已有会话。
     *
     * @param session 要更新的会话。
     */
    suspend fun updateSession(session: ChatSession) {
        mChatSessionDao.update(session.withNormalizedCreatorNotes())
    }

    /**
     * 修改会话标题。
     *
     * @param id 会话 id。
     * @param title 新标题。
     */
    suspend fun updateSessionTitle(id: Long, title: String) {
        mChatSessionDao.updateSessionTitle(id, title)
    }

    /**
     * 修改会话最近活跃时间。
     *
     * @param id 会话 id。
     * @param latestTime 最近活跃时间。
     */
    suspend fun updateSessionLatestTime(id: Long, latestTime: Long = System.currentTimeMillis()) {
        mChatSessionDao.updateSessionLatestTime(id, latestTime)
    }

    /**
     * 修改当前会话启用的世界书条目集。
     *
     * @param id 会话 id。
     * @param lorebookEntrySet 世界书条目 id 集合字符串。
     */
    suspend fun updateSessionLorebookEntrySet(id: Long, lorebookEntrySet: String) {
        mChatSessionDao.updateSessionLorebookEntrySet(id, lorebookEntrySet)
    }

    /**
     * 获取指定会话启用的世界书条目 id 列表。
     */
    suspend fun getSessionLorebookEntryIds(id: Long): List<Long>? {
        val session = mChatSessionDao.getSessionById(id) ?: return null
        return getSessionLorebookEntryIds(session)
    }

    /**
     * 获取指定会话启用的世界书条目 id 列表。
     */
    fun getSessionLorebookEntryIds(session: ChatSession): List<Long> {
        return session.lorebookEntrySet.toLorebookEntryIds()
    }

    /**
     * 设置指定会话启用的世界书条目 id 列表。
     */
    suspend fun updateSessionLorebookEntryIds(id: Long, lorebookEntryIds: List<Long>) {
        mChatSessionDao.updateSessionLorebookEntrySet(id, lorebookEntryIds.toLorebookEntrySetJson())
    }

    /**
     * 修改用户笔记。
     *
     * @param id 会话 id。
     * @param userNote 新的用户笔记。
     */
    suspend fun updateSessionUserNote(id: Long, userNote: String) {
        mChatSessionDao.updateSessionUserNote(id, userNote)
    }

    /**
     * 获取指定会话实际生效的角色备注。
     *
     * 如果会话没有覆盖值，则回退到关联角色的 creatorNotes。
     */
    suspend fun getSessionCreatorNotes(id: Long): String? {
        val session = mChatSessionDao.getSessionById(id) ?: return null
        return getSessionCreatorNotes(session)
    }

    /**
     * 获取指定会话实际生效的角色备注。
     *
     * 如果会话没有覆盖值，则回退到关联角色的 creatorNotes。
     */
    suspend fun getSessionCreatorNotes(session: ChatSession): String {
        return session.creatorNotes.takeIfNotBlank()
            ?: mCharacterDao.getCharacterById(session.characterId)?.creatorNotes.orEmpty()
    }

    /**
     * 设置当前会话的角色备注覆盖值。
     *
     * 传入 null 或空白字符串会清空覆盖值，后续读取时继承 Character.creatorNotes。
     */
    suspend fun updateSessionCreatorNotes(id: Long, creatorNotes: String?) {
        mChatSessionDao.updateSessionCreatorNotes(id, creatorNotes.takeIfNotBlank())
    }

    /**
     * 保存世界书 sticky/cooldown 等运行时状态。
     *
     * 只由 prompt 构建流程调用，避免 UI 层误把它当作用户设置。
     */
    suspend fun updateSessionWorldInfoState(id: Long, worldInfoStateJson: String) {
        mChatSessionDao.updateSessionWorldInfoState(id, worldInfoStateJson)
    }

    /**
     * 删除会话。
     *
     * @param id 会话 id。
     */
    suspend fun deleteSession(id: Long) {
        mAppDatabase.withTransaction {
            mChatMessageDao.deleteMessagesBySessionId(id)
            mChatSessionDao.deleteSessionById(id)
        }
    }

    /**
     * 获取指定会话下的全部聊天消息。
     *
     * @param sessionId 会话 id。
     * @return 按创建时间正序排列的消息列表。
     */
    suspend fun getAllChatMessagesBySessionId(sessionId: Long): List<ChatMessage> {
        return mChatMessageDao.getMessagesBySessionId(sessionId)
    }

    /**
     * 获取指定会话下的全部消息。
     *
     * @param sessionId 会话 id。
     * @return 按创建时间正序排列的消息列表。
     */
    suspend fun getMessagesBySessionId(sessionId: Long): List<ChatMessage> {
        return mChatMessageDao.getMessagesBySessionId(sessionId)
    }

    /**
     * 获取会话当前生效的最新总结快照。
     *
     * @param sessionId 会话 id。
     * @return 最新总结快照；不存在时返回 null。
     */
    suspend fun getLatestSummary(sessionId: Long): ChatMessage? {
        return mChatMessageDao.getLatestSummaryBySessionId(sessionId)
    }

    /**
     * 获取在指定普通消息位置仍然有效的最新总结快照。
     *
     * @param sessionId 会话 id。
     * @param messageId 分支或回溯位置的普通消息 id。
     * @return 覆盖边界不晚于指定消息的最新总结快照；不存在时返回 null。
     */
    suspend fun getLatestSummaryAtOrBefore(sessionId: Long, messageId: Long): ChatMessage? {
        return mChatMessageDao.getLatestSummaryAtOrBefore(sessionId, messageId)
    }

    /**
     * 获取最新总结之后尚未被覆盖的普通消息。
     *
     * @param sessionId 会话 id。
     * @return 按时间正序排列的普通消息。
     */
    suspend fun getMessagesAfterLatestSummary(sessionId: Long): List<ChatMessage> {
        return getSummaryContext(sessionId).messagesAfterSummary
    }

    /**
     * 在同一事务中读取最新总结及其后的普通消息，避免两次查询间快照发生变化。
     *
     * @param sessionId 会话 id。
     * @return 当前总结上下文。
     */
    suspend fun getSummaryContext(sessionId: Long): ChatSummaryContext {
        return mAppDatabase.withTransaction {
            val summary = mChatMessageDao.getLatestSummaryBySessionId(sessionId)
            ChatSummaryContext(
                summary = summary,
                messagesAfterSummary = mChatMessageDao.getMessagesAfterId(
                    sessionId = sessionId,
                    coveredMessageId = summary?.coveredMessageId ?: 0L
                ),
                totalMessageCount = mChatMessageDao.getMessageCountBySessionId(sessionId)
            )
        }
    }

    /**
     * 根据消息 id 获取消息详情。
     *
     * @param id 消息 id。
     * @return 匹配的消息；如果不存在则返回 null。
     */
    suspend fun getMessageById(id: Long): ChatMessage? {
        return mChatMessageDao.getMessageById(id)
    }

    /**
     * 获取指定会话下的消息数量。
     *
     * @param sessionId 会话 id。
     * @return 消息数量。
     */
    suspend fun getMessageCountBySessionId(sessionId: Long): Int {
        return mChatMessageDao.getMessageCountBySessionId(sessionId)
    }

    /**
     * 获取指定会话下的最后一条消息。
     *
     * @param sessionId 会话 id。
     * @return 最新消息；如果没有消息则返回 null。
     */
    suspend fun getLatestMessageBySessionId(sessionId: Long): ChatMessage? {
        return mChatMessageDao.getLatestMessageBySessionId(sessionId)
    }

    /**
     * 创建新的聊天消息，并同步刷新会话最近活跃时间。
     *
     * @param sessionId 所属会话 id。
     * @param source 消息来源。
     * @param content 消息正文。
     * @param createTime 消息创建时间。
     * @param coveredMessageId Summary 消息的覆盖边界；普通消息应传入 null。
     * @return 新创建的消息 id。
     */
    suspend fun createMessage(
        sessionId: Long,
        source: ChatMessage.Source,
        content: String,
        createTime: Long = System.currentTimeMillis(),
        coveredMessageId: Long? = null
    ): Long {
        require(source != ChatMessage.Source.Summary || coveredMessageId != null) {
            "Summary messages require a covered message id"
        }
        return mAppDatabase.withTransaction {
            val messageId = mChatMessageDao.insertOrReplace(
                ChatMessage(
                    sessionId = sessionId,
                    createTime = createTime,
                    source = source,
                    content = content,
                    coveredMessageId = coveredMessageId
                )
            )
            mChatSessionDao.updateSessionLatestTime(sessionId, createTime)
            messageId
        }
    }

    /**
     * 保存消息。
     *
     * 当 id 为 0 时创建新消息；否则更新已有消息。
     *
     * @param message 要保存的消息。
     * @return 保存后的消息 id。
     */
    suspend fun saveMessage(message: ChatMessage): Long {
        if (message.id == 0L) {
            return createMessage(
                sessionId = message.sessionId,
                source = message.source,
                content = message.content,
                createTime = message.createTime,
                coveredMessageId = message.coveredMessageId
            )
        }
        updateMessage(message)
        return message.id
    }

    /**
     * 更新已有消息。
     *
     * @param message 要更新的消息。
     */
    suspend fun updateMessage(message: ChatMessage) {
        mAppDatabase.withTransaction {
            val current = mChatMessageDao.getMessageById(message.id)
            mChatMessageDao.update(message)
            if (current != null && current.source != ChatMessage.Source.Summary) {
                mChatMessageDao.deleteSummariesCoveringMessage(current.sessionId, current.id)
            }
        }
    }

    /**
     * 修改消息正文。
     *
     * @param id 消息 id。
     * @param content 新的消息正文。
     */
    suspend fun updateMessageContent(id: Long, content: String) {
        mAppDatabase.withTransaction {
            val message = mChatMessageDao.getMessageById(id) ?: return@withTransaction
            mChatMessageDao.updateMessageContent(id, content)
            if (message.source != ChatMessage.Source.Summary) {
                mChatMessageDao.deleteSummariesCoveringMessage(message.sessionId, message.id)
            }
        }
    }

    /**
     * 新增一条总结快照，并记录其覆盖到的最后一条普通消息。
     *
     * @param sessionId 会话 id。
     * @param content 总结正文。
     * @param coveredMessageId 总结覆盖到的最后一条普通消息 id。
     * @param createTime 总结快照创建时间。
     * @return 新总结快照的消息 id。
     */
    suspend fun saveSummary(
        sessionId: Long,
        content: String,
        coveredMessageId: Long,
        createTime: Long = System.currentTimeMillis()
    ): Long {
        return mAppDatabase.withTransaction {
            val coveredMessage = mChatMessageDao.getMessageById(coveredMessageId)
            require(
                coveredMessage?.sessionId == sessionId &&
                    coveredMessage.source != ChatMessage.Source.Summary
            ) {
                "Summary boundary must reference a regular message in the same session"
            }
            mChatMessageDao.insertOrReplace(
                ChatMessage(
                    sessionId = sessionId,
                    createTime = createTime,
                    source = ChatMessage.Source.Summary,
                    content = content,
                    coveredMessageId = coveredMessageId
                )
            )
        }
    }

    /**
     * 更新用户当前看到的总结。
     *
     * 清空总结时写入边界为 0 的空快照，防止更早的总结重新生效；首次手动填写总结时，
     * 默认覆盖到会话当前最后一条普通消息。
     *
     * @param sessionId 会话 id。
     * @param content 新的总结正文。
     * @param createTime 新建快照时使用的创建时间。
     */
    suspend fun updateCurrentSummary(
        sessionId: Long,
        content: String,
        createTime: Long = System.currentTimeMillis()
    ) {
        mAppDatabase.withTransaction {
            val current = mChatMessageDao.getLatestSummaryBySessionId(sessionId)
            if (content.isBlank()) {
                mChatMessageDao.insertOrReplace(
                    ChatMessage(
                        sessionId = sessionId,
                        createTime = createTime,
                        source = ChatMessage.Source.Summary,
                        content = "",
                        coveredMessageId = 0L
                    )
                )
            } else if (current != null && current.coveredMessageId != 0L) {
                mChatMessageDao.updateMessageContent(current.id, content)
            } else {
                val coveredMessageId = mChatMessageDao.getLatestMessageBySessionId(sessionId)?.id ?: 0L
                mChatMessageDao.insertOrReplace(
                    ChatMessage(
                        sessionId = sessionId,
                        createTime = createTime,
                        source = ChatMessage.Source.Summary,
                        content = content,
                        coveredMessageId = coveredMessageId
                    )
                )
            }
        }
    }

    /**
     * 删除消息。
     *
     * @param id 消息 id。
     */
    suspend fun deleteMessage(id: Long) {
        mAppDatabase.withTransaction {
            val message = mChatMessageDao.getMessageById(id) ?: return@withTransaction
            if (message.source != ChatMessage.Source.Summary) {
                mChatMessageDao.deleteSummariesCoveringMessage(message.sessionId, message.id)
            }
            mChatMessageDao.deleteMessageById(id)
        }
    }

    /**
     * 删除指定会话下的全部消息。
     *
     * @param sessionId 会话 id。
     */
    suspend fun deleteMessagesBySessionId(sessionId: Long) {
        mChatMessageDao.deleteMessagesBySessionId(sessionId)
    }

    private fun String.toLorebookEntryIds(): List<Long> {
        if (isBlank()) return emptyList()
        return runCatching {
            mGson.fromJson(this, Array<Long>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun List<Long>.toLorebookEntrySetJson(): String {
        return mGson.toJson(distinct())
    }

    private fun <T> List<T>.takeWhileInclusive(predicate: (T) -> Boolean): List<T> {
        val result = mutableListOf<T>()
        for (item in this) {
            result += item
            if (!predicate(item)) break
        }
        return result
    }
}

