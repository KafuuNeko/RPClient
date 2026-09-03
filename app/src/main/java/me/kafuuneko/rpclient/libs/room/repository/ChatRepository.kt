package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.withTransaction
import com.google.gson.Gson
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.model.ChatSessionOverview
import me.kafuuneko.rpclient.utils.takeIfNotBlank

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

/**
 * 单次总结请求使用的已有总结、待总结消息和写回目标。
 *
 * @property existingSummary 本次请求继承的上一条总结内容。
 * @property messages 本次实际需要重新总结或增量总结的普通消息。
 * @property summaryToUpdate 用户主动重新总结时需要原地更新的最新快照。
 */
data class ChatSummaryGenerationContext(
    val existingSummary: String,
    val messages: List<ChatMessage>,
    val summaryToUpdate: ChatMessage?
)

/**
 * 普通生成请求使用的摘要与最近历史窗口。
 *
 * @property summary 当前请求实际使用的摘要内容。
 * @property messages 按创建时间正序排列的最近历史消息。
 * @property totalMessageCount 排除待替换消息后的完整普通消息总数。
 */
data class ChatPromptHistoryContext(
    val summary: String,
    val messages: List<ChatMessage>,
    val totalMessageCount: Int
)

/**
 * 单聊页面按时间倒序查询后恢复为展示正序的一页消息。
 *
 * @property messages 当前页按创建时间正序排列的普通消息。
 * @property canLoadOlderMessages 当前页之前是否仍有更早的普通消息。
 * @property totalMessageCount 当前会话普通消息总数。
 */
data class ChatMessagePage(
    val messages: List<ChatMessage>,
    val canLoadOlderMessages: Boolean,
    val totalMessageCount: Int
)

/**
 * 单聊会话、消息和摘要的事务仓库。
 *
 * 编辑或删除普通消息时会同步清理覆盖该消息的摘要；创建消息和分支时负责维护
 * 会话活跃时间、摘要边界与世界书运行时状态的一致性。
 */
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

    /** 一次读取首页单聊列表需要的轻量会话概览。 */
    suspend fun getSessionOverviews(): List<ChatSessionOverview> {
        return mChatSessionDao.getSessionOverviews()
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
        userName: String,
        userDescription: String,
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
                    userName = userName,
                    userDescription = userDescription,
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
        userName: String,
        userDescription: String,
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
                userName = userName,
                userDescription = userDescription,
                creatorNotes = creatorNotes
            )
        )
    }

    /**
     * 从已有会话复制一段消息前缀，创建一个新的独立会话作为分支。
     *
     * 处理步骤：
     * - 加载源会话元数据与消息列表；
     * - 截取至指定消息截止的连续前缀；
     * - 提取指定消息位置有效的最新总结快照；
     * - 插入新的分支会话记录（重置时序状态为 `{}`）；
     * - 批量深拷贝历史消息并维护 ID 映射关系；
     * - 若存在有效总结快照，将其覆盖边界 ID 映射至新会话对应消息并插入。
     */
    suspend fun createBranchSession(
        sourceSessionId: Long,
        throughMessageId: Long,
        title: String,
        createTime: Long = System.currentTimeMillis()
    ): Long {
        return mAppDatabase.withTransaction {
            // 加载源会话与截断消息
            val sourceSession = mChatSessionDao.getSessionById(sourceSessionId) ?: return@withTransaction 0L
            val sourceMessages = mChatMessageDao.getMessagesBySessionId(sourceSessionId)
            val branchMessages = sourceMessages.takeWhileInclusive { it.id != throughMessageId }
            if (branchMessages.none { it.id == throughMessageId }) return@withTransaction 0L
            val sourceSummary = mChatMessageDao.getLatestSummaryAtOrBefore(sourceSessionId, throughMessageId)

            // 插入新的分支会话记录
            val branchSessionId = mChatSessionDao.insertOrReplace(
                sourceSession.copy(
                    id = 0L,
                    createTime = createTime,
                    latestTime = createTime,
                    title = title,
                    worldInfoStateJson = "{}"
                ).withNormalizedCreatorNotes()
            )
            // 复制前缀消息至新会话
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
            // 复制并重映射总结快照边界
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
     * 修改当前会话使用的用户名称。
     *
     * @param id 会话 id。
     * @param userName 新的用户名称。
     */
    suspend fun updateSessionUserName(id: Long, userName: String) {
        mChatSessionDao.updateSessionUserName(id, userName)
    }

    /**
     * 修改当前会话使用的用户描述。
     *
     * @param id 会话 id。
     * @param userDescription 新的用户描述。
     */
    suspend fun updateSessionUserDescription(id: Long, userDescription: String) {
        mChatSessionDao.updateSessionUserDescription(id, userDescription)
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

    /** 暂停或恢复当前单聊的自动总结。 */
    suspend fun updateAutoSummaryPaused(id: Long, paused: Boolean) {
        mChatSessionDao.updateAutoSummaryPaused(id, paused)
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
     * 获取会话末尾的一页普通消息。
     *
     * 多取一条只用于判断是否存在更早历史，不会暴露给页面。
     *
     * @param sessionId 会话 ID。
     * @param pageSize 页面实际接收的最大消息数量。
     * @return 最新消息页及会话总消息数。
     */
    suspend fun getLatestMessagePage(
        sessionId: Long,
        pageSize: Int
    ): ChatMessagePage {
        require(pageSize > 0) { "pageSize must be positive" }
        return mAppDatabase.withTransaction {
            val rows = mChatMessageDao.getLatestMessagePageBySessionId(
                sessionId = sessionId,
                limit = pageSize + 1
            )
            rows.toChatMessagePage(
                pageSize = pageSize,
                totalMessageCount = mChatMessageDao.getMessageCountBySessionId(sessionId)
            )
        }
    }

    /**
     * 获取稳定游标之前的一页普通消息。
     *
     * @param sessionId 会话 ID。
     * @param beforeCreateTime 当前最早已加载消息的创建时间。
     * @param beforeMessageId 当前最早已加载消息的 ID。
     * @param pageSize 页面实际接收的最大消息数量。
     * @return 更早消息页及会话总消息数。
     */
    suspend fun getMessagePageBefore(
        sessionId: Long,
        beforeCreateTime: Long,
        beforeMessageId: Long,
        pageSize: Int
    ): ChatMessagePage {
        require(pageSize > 0) { "pageSize must be positive" }
        return mAppDatabase.withTransaction {
            val rows = mChatMessageDao.getMessagePageBeforeBySessionId(
                sessionId = sessionId,
                beforeCreateTime = beforeCreateTime,
                beforeMessageId = beforeMessageId,
                limit = pageSize + 1
            )
            rows.toChatMessagePage(
                pageSize = pageSize,
                totalMessageCount = mChatMessageDao.getMessageCountBySessionId(sessionId)
            )
        }
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
     * 在同一事务中读取普通生成所需的摘要、最近历史窗口和完整消息总数。
     *
     * [maxHistoryMessages] 为 0 时保持原有无限制行为。重生成排除的消息如果恰好是
     * 最新摘要边界，则回退到上一份摘要，并在回退范围内继续应用相同的历史窗口限制。
     *
     * @param sessionId 会话 ID。
     * @param excludedMessageId 重生成时不应进入 Prompt 的待替换消息 ID。
     * @param maxHistoryMessages 最多读取的最近历史消息数；0 表示不限制。
     * @return 普通生成使用的历史上下文。
     */
    suspend fun getPromptHistoryContext(
        sessionId: Long,
        excludedMessageId: Long?,
        maxHistoryMessages: Int
    ): ChatPromptHistoryContext {
        require(maxHistoryMessages >= 0) { "maxHistoryMessages must not be negative" }
        return mAppDatabase.withTransaction {
            // 读取当前摘要边界与该边界之后的有限历史窗口
            val latestSummary = mChatMessageDao.getLatestSummaryBySessionId(sessionId)
            val messagesAfterSummary = loadPromptMessagesAfterId(
                sessionId = sessionId,
                coveredMessageId = latestSummary?.coveredMessageId ?: 0L,
                excludedMessageId = excludedMessageId,
                maxHistoryMessages = maxHistoryMessages
            )
            val totalMessageCount = (
                mChatMessageDao.getMessageCountBySessionId(sessionId) -
                    if (excludedMessageId == null) 0 else 1
            ).coerceAtLeast(0)
            // 待替换消息位于摘要边界时回退摘要，防止继续使用包含旧回复的摘要
            if (
                excludedMessageId != null &&
                latestSummary?.coveredMessageId == excludedMessageId &&
                messagesAfterSummary.isEmpty()
            ) {
                return@withTransaction loadRegenerationPromptHistory(
                    sessionId = sessionId,
                    latestSummary = latestSummary,
                    excludedMessageId = excludedMessageId,
                    maxHistoryMessages = maxHistoryMessages,
                    totalMessageCount = totalMessageCount
                )
            }
            ChatPromptHistoryContext(
                summary = latestSummary?.content.orEmpty(),
                messages = messagesAfterSummary,
                totalMessageCount = totalMessageCount
            )
        }
    }

    /**
     * 获取生成总结所需的消息范围。
     *
     * 默认返回最新总结之后的增量消息。用户主动总结且最新总结已经覆盖最后一条普通消息时，
     * 回退到上一条总结边界，重新生成并更新最新总结快照。
     */
    suspend fun getSummaryGenerationContext(
        sessionId: Long,
        allowRefreshLatest: Boolean
    ): ChatSummaryGenerationContext {
        return mAppDatabase.withTransaction {
            val latestSummary = mChatMessageDao.getLatestSummaryBySessionId(sessionId)
            val messagesAfterSummary = mChatMessageDao.getMessagesAfterId(
                sessionId = sessionId,
                coveredMessageId = latestSummary?.coveredMessageId ?: 0L
            )
            if (messagesAfterSummary.isNotEmpty() || !allowRefreshLatest) {
                return@withTransaction ChatSummaryGenerationContext(
                    existingSummary = latestSummary?.content.orEmpty(),
                    messages = messagesAfterSummary,
                    summaryToUpdate = null
                )
            }

            val latestMessage = mChatMessageDao.getLatestMessageBySessionId(sessionId)
            val refreshableSummary = latestSummary?.takeIf {
                it.content.isNotBlank() &&
                    it.coveredMessageId != 0L &&
                    it.coveredMessageId == latestMessage?.id
            }
            if (refreshableSummary == null) {
                return@withTransaction ChatSummaryGenerationContext(
                    existingSummary = latestSummary?.content.orEmpty(),
                    messages = emptyList(),
                    summaryToUpdate = null
                )
            }

            val refreshBoundaryId = requireNotNull(refreshableSummary.coveredMessageId)
            val previousSummary = mChatMessageDao.getPreviousSummaryBeforeBoundary(
                sessionId = sessionId,
                coveredMessageId = refreshBoundaryId
            )
            ChatSummaryGenerationContext(
                existingSummary = previousSummary?.content.orEmpty(),
                messages = mChatMessageDao.getMessagesInRange(
                    sessionId = sessionId,
                    afterMessageId = previousSummary?.coveredMessageId ?: 0L,
                    throughMessageId = refreshBoundaryId
                ),
                summaryToUpdate = refreshableSummary
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

    /** 获取指定会话最后一条角色消息。 */
    suspend fun getLatestCharacterMessageBySessionId(sessionId: Long): ChatMessage? {
        return mChatMessageDao.getLatestCharacterMessageBySessionId(sessionId)
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
     * 为流式新回复创建空占位消息，但不提前推进会话活跃时间。
     *
     * 只有实际收到并提交非空正文后，[commitGenerationResult] 才更新会话元数据。
     */
    suspend fun createGenerationPlaceholder(
        sessionId: Long,
        source: ChatMessage.Source,
        createTime: Long = System.currentTimeMillis()
    ): Long {
        require(source != ChatMessage.Source.Summary) { "Generation output cannot be a summary" }
        return mChatMessageDao.insertOrReplace(
            ChatMessage(
                sessionId = sessionId,
                createTime = createTime,
                source = source,
                content = ""
            )
        )
    }

    /**
     * 原子提交一次生成结果的正文、摘要失效、活跃时间与世界书运行时状态。
     *
     * 处理步骤：
     * - 若生成内容为空，根据选项清理未使用的空占位消息并退出；
     * - 若为新消息则插入记录，若为已有消息重新生成则更新正文并清理覆盖该位置的旧总结快照；
     * - 更新会话最近活跃时间戳与世界书运行时状态快照（Sticky/Cooldown 时序）。
     *
     * @return 已提交消息 id；没有接受正文时返回 null。
     */
    suspend fun commitGenerationResult(
        sessionId: Long,
        messageId: Long?,
        source: ChatMessage.Source,
        content: String,
        deleteEmptyPlaceholder: Boolean,
        worldInfoStateJson: String,
        commitTime: Long = System.currentTimeMillis()
    ): Long? {
        require(source != ChatMessage.Source.Summary) { "Generation output cannot be a summary" }
        return mAppDatabase.withTransaction {
            // 空正文处理：按需清理占位消息并直接返回
            if (content.isBlank()) {
                if (deleteEmptyPlaceholder && messageId != null) {
                    val placeholder = mChatMessageDao.getMessageById(messageId)
                    if (placeholder?.sessionId == sessionId && placeholder.content.isBlank()) {
                        mChatMessageDao.deleteMessageById(messageId)
                    }
                }
                return@withTransaction null
            }

            // 插入新消息或更新已有消息并失效旧总结
            val committedMessageId = if (messageId == null) {
                mChatMessageDao.insertOrReplace(
                    ChatMessage(
                        sessionId = sessionId,
                        createTime = commitTime,
                        source = source,
                        content = content
                    )
                )
            } else {
                val message = requireNotNull(mChatMessageDao.getMessageById(messageId)) {
                    "Generation target message does not exist"
                }
                require(message.sessionId == sessionId && message.source != ChatMessage.Source.Summary) {
                    "Generation target must be a regular message in the same session"
                }
                mChatMessageDao.updateMessageContent(messageId, content)
                mChatMessageDao.deleteSummariesCoveringMessage(sessionId, messageId)
                messageId
            }
            // 提交会话活跃时间与世界书运行时状态
            mChatSessionDao.updateGenerationMetadata(
                id = sessionId,
                latestTime = commitTime,
                worldInfoStateJson = worldInfoStateJson
            )
            committedMessageId
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
     * @param summaryIdToUpdate 需要原地更新的最新总结 id；null 表示插入新快照。
     * @param createTime 总结快照创建时间。
     * @return 新总结快照的消息 id。
     */
    suspend fun saveSummary(
        sessionId: Long,
        content: String,
        coveredMessageId: Long,
        summaryIdToUpdate: Long? = null,
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
            if (summaryIdToUpdate != null) {
                val currentSummary = mChatMessageDao.getLatestSummaryBySessionId(sessionId)
                require(
                    currentSummary?.id == summaryIdToUpdate
                ) {
                    "Summary to update is no longer the latest snapshot"
                }
                mChatMessageDao.updateSummary(summaryIdToUpdate, content, coveredMessageId)
                return@withTransaction summaryIdToUpdate
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
     * 清空总结时写入边界为 0 的空快照，防止更早的总结重新生效。非空内容仅在当前总结
     * 已覆盖最后一条普通消息时原地更新，否则插入覆盖到最新消息的新总结快照。
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
            } else {
                val latestMessageId = mChatMessageDao.getLatestMessageBySessionId(sessionId)?.id ?: 0L
                if (current != null && current.coveredMessageId == latestMessageId) {
                    mChatMessageDao.updateMessageContent(current.id, content)
                    return@withTransaction
                }
                mChatMessageDao.insertOrReplace(
                    ChatMessage(
                        sessionId = sessionId,
                        createTime = createTime,
                        source = ChatMessage.Source.Summary,
                        content = content,
                        coveredMessageId = latestMessageId
                    )
                )
            }
        }
    }

    /**
     * 删除最新总结快照，使上一份快照重新成为当前记忆。
     *
     * @return 存在上一份快照并成功恢复时为 true。
     */
    suspend fun restorePreviousSummary(sessionId: Long): Boolean {
        return mAppDatabase.withTransaction {
            val latest = mChatMessageDao.getLatestSummaryBySessionId(sessionId)
                ?: return@withTransaction false
            val previous = mChatMessageDao.getPreviousSummaryBeforeId(sessionId, latest.id)
                ?: return@withTransaction false
            mChatMessageDao.delete(latest)
            previous.id > 0L
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

    /** 读取摘要边界之后的普通生成历史，并恢复为正序后排除待替换消息。 */
    private suspend fun loadPromptMessagesAfterId(
        sessionId: Long,
        coveredMessageId: Long,
        excludedMessageId: Long?,
        maxHistoryMessages: Int
    ): List<ChatMessage> {
        val messages = if (maxHistoryMessages == 0) {
            mChatMessageDao.getMessagesAfterId(sessionId, coveredMessageId)
        } else {
            mChatMessageDao.getLatestMessagesAfterId(
                sessionId = sessionId,
                coveredMessageId = coveredMessageId,
                limit = promptHistoryReadLimit(maxHistoryMessages, excludedMessageId)
            ).asReversed()
        }
        return messages.toPromptHistoryWindow(excludedMessageId, maxHistoryMessages)
    }

    /** 回退最新摘要边界，并读取与旧行为一致的重生成历史范围。 */
    private suspend fun loadRegenerationPromptHistory(
        sessionId: Long,
        latestSummary: ChatMessage,
        excludedMessageId: Long,
        maxHistoryMessages: Int,
        totalMessageCount: Int
    ): ChatPromptHistoryContext {
        // 只有最新摘要完整覆盖最后一条消息时，才允许回退并重新构建该边界
        val latestMessage = mChatMessageDao.getLatestMessageBySessionId(sessionId)
        val refreshableSummary = latestSummary.takeIf {
            it.content.isNotBlank() &&
                it.coveredMessageId != 0L &&
                it.coveredMessageId == latestMessage?.id
        } ?: return ChatPromptHistoryContext(
            summary = latestSummary.content,
            messages = emptyList(),
            totalMessageCount = totalMessageCount
        )
        // 使用上一份摘要作为记忆，并在两份摘要边界之间读取最近历史窗口
        val refreshBoundaryId = requireNotNull(refreshableSummary.coveredMessageId)
        val previousSummary = mChatMessageDao.getPreviousSummaryBeforeBoundary(
            sessionId = sessionId,
            coveredMessageId = refreshBoundaryId
        )
        val messages = loadPromptMessagesInRange(
            sessionId = sessionId,
            afterMessageId = previousSummary?.coveredMessageId ?: 0L,
            throughMessageId = refreshBoundaryId,
            excludedMessageId = excludedMessageId,
            maxHistoryMessages = maxHistoryMessages
        )
        return ChatPromptHistoryContext(
            summary = previousSummary?.content.orEmpty(),
            messages = messages,
            totalMessageCount = totalMessageCount
        )
    }

    /** 读取两个摘要边界之间的普通生成历史窗口。 */
    private suspend fun loadPromptMessagesInRange(
        sessionId: Long,
        afterMessageId: Long,
        throughMessageId: Long,
        excludedMessageId: Long,
        maxHistoryMessages: Int
    ): List<ChatMessage> {
        val messages = if (maxHistoryMessages == 0) {
            mChatMessageDao.getMessagesInRange(sessionId, afterMessageId, throughMessageId)
        } else {
            mChatMessageDao.getLatestMessagesInRange(
                sessionId = sessionId,
                afterMessageId = afterMessageId,
                throughMessageId = throughMessageId,
                limit = promptHistoryReadLimit(maxHistoryMessages, excludedMessageId)
            ).asReversed()
        }
        return messages.toPromptHistoryWindow(excludedMessageId, maxHistoryMessages)
    }

    /** 为可能需要排除的消息多读取一行，保证最终窗口仍可包含指定数量。 */
    private fun promptHistoryReadLimit(
        maxHistoryMessages: Int,
        excludedMessageId: Long?
    ): Int {
        if (excludedMessageId == null || maxHistoryMessages == Int.MAX_VALUE) {
            return maxHistoryMessages
        }
        return maxHistoryMessages + 1
    }

    /** 排除待替换消息，并从剩余结果末尾保留配置允许的最近历史。 */
    private fun List<ChatMessage>.toPromptHistoryWindow(
        excludedMessageId: Long?,
        maxHistoryMessages: Int
    ): List<ChatMessage> {
        val filtered = filterNot { it.id == excludedMessageId }
        return if (maxHistoryMessages == 0) filtered else filtered.takeLast(maxHistoryMessages)
    }

    /** 将数据库倒序结果裁成页面需要的正序消息，并保留是否还有更早记录。 */
    private fun List<ChatMessage>.toChatMessagePage(
        pageSize: Int,
        totalMessageCount: Int
    ): ChatMessagePage {
        return ChatMessagePage(
            messages = take(pageSize).asReversed(),
            canLoadOlderMessages = size > pageSize,
            totalMessageCount = totalMessageCount
        )
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
