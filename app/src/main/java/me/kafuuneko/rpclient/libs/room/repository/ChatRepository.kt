package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.withTransaction
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession

class ChatRepository(private val mAppDatabase: AppDatabase) {
    private val mChatSessionDao = mAppDatabase.getChatSessionDao()
    private val mChatMessageDao = mAppDatabase.getChatMessageDao()

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
        if (session.id == 0L) {
            return mChatSessionDao.insertOrReplace(session)
        }
        mChatSessionDao.update(session)
        return session.id
    }

    /**
     * 更新已有会话。
     *
     * @param session 要更新的会话。
     */
    suspend fun updateSession(session: ChatSession) {
        mChatSessionDao.update(session)
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
     * 修改会话总结。
     *
     * @param id 会话 id。
     * @param summarize 新的会话总结。
     */
    suspend fun updateSessionSummarize(id: Long, summarize: String) {
        mChatSessionDao.updateSessionSummarize(id, summarize)
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
     * 删除会话。
     *
     * @param id 会话 id。
     */
    suspend fun deleteSession(id: Long) {
        mChatSessionDao.deleteSessionById(id)
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
     * 获取指定会话下尚未纳入总结的消息。
     *
     * @param sessionId 会话 id。
     * @return 按创建时间正序排列的消息列表。
     */
    suspend fun getUnsummarizedMessagesBySessionId(sessionId: Long): List<ChatMessage> {
        return mChatMessageDao.getUnsummarizedMessagesBySessionId(sessionId)
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
     * @param isSummarized 是否已被总结。
     * @return 新创建的消息 id。
     */
    suspend fun createMessage(
        sessionId: Long,
        source: ChatMessage.Source,
        content: String,
        createTime: Long = System.currentTimeMillis(),
        isSummarized: Boolean = false
    ): Long {
        return mAppDatabase.withTransaction {
            val messageId = mChatMessageDao.insertOrReplace(
                ChatMessage(
                    sessionId = sessionId,
                    createTime = createTime,
                    source = source,
                    content = content,
                    isSummarized = isSummarized
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
                isSummarized = message.isSummarized
            )
        }
        mChatMessageDao.update(message)
        return message.id
    }

    /**
     * 更新已有消息。
     *
     * @param message 要更新的消息。
     */
    suspend fun updateMessage(message: ChatMessage) {
        mChatMessageDao.update(message)
    }

    /**
     * 修改消息正文。
     *
     * @param id 消息 id。
     * @param content 新的消息正文。
     */
    suspend fun updateMessageContent(id: Long, content: String) {
        mChatMessageDao.updateMessageContent(id, content)
    }

    /**
     * 修改消息是否已被总结。
     *
     * @param id 消息 id。
     * @param isSummarized 是否已被总结。
     */
    suspend fun updateMessageSummarized(id: Long, isSummarized: Boolean) {
        mChatMessageDao.updateMessageSummarized(id, isSummarized)
    }

    /**
     * 保存会话总结，并批量标记已纳入总结的消息。
     *
     * @param sessionId 会话 id。
     * @param summarize 新的会话总结。
     * @param summarizedMessageIds 已纳入总结的消息 id 列表。
     */
    suspend fun saveSessionSummarize(
        sessionId: Long,
        summarize: String,
        summarizedMessageIds: List<Long>
    ) {
        mAppDatabase.withTransaction {
            mChatSessionDao.updateSessionSummarize(sessionId, summarize)
            if (summarizedMessageIds.isNotEmpty()) {
                mChatMessageDao.markMessagesSummarized(summarizedMessageIds)
            }
        }
    }

    /**
     * 删除消息。
     *
     * @param id 消息 id。
     */
    suspend fun deleteMessage(id: Long) {
        mChatMessageDao.deleteMessageById(id)
    }

    /**
     * 删除指定会话下的全部消息。
     *
     * @param sessionId 会话 id。
     */
    suspend fun deleteMessagesBySessionId(sessionId: Long) {
        mChatMessageDao.deleteMessagesBySessionId(sessionId)
    }
}
