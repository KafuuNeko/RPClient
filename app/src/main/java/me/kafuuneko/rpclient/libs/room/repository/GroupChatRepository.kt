package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.withTransaction
import com.google.gson.Gson
import me.kafuuneko.rpclient.libs.groupchat.GroupChatOpeningMessage
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMember
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSummary
import me.kafuuneko.rpclient.libs.room.model.GroupChatSessionOverview

/** 群成员关系及其对应角色卡的聚合数据。 */
data class GroupChatMemberData(
    /** 当前角色、成员或世界书条目的关联数据。 */
    val relation: GroupChatMember,
    /** 当前状态或操作关联的角色数据。 */
    val character: Character
)

/** 群聊页面和生成流程所需的会话聚合数据。 */
data class GroupChatData(
    /** 当前页面展示或编辑的会话数据。 */
    val session: GroupChatSession,
    /** 当前群聊包含的成员列表。 */
    val members: List<GroupChatMemberData>,
    /** 当前状态或请求包含的消息列表。 */
    val messages: List<GroupChatMessage>,
    /** 当前会话或故事使用的摘要内容。 */
    val summary: GroupChatSummary?
)

/**
 * 群聊生成请求使用的聚合数据与完整消息计数。
 *
 * @property data 会话、成员、最近历史窗口和摘要组成的生成快照。
 * @property totalMessageCount 当前群聊的完整消息总数。
 */
data class GroupChatPromptData(
    val data: GroupChatData,
    val totalMessageCount: Int
)

/**
 * 群聊摘要构建使用的有限候选窗口。
 *
 * @property data 会话、成员、摘要及保留末尾排除哨兵的消息窗口。
 * @property hasMoreCandidateMessages 当前窗口之后是否还有可继续扩展的候选消息。
 */
data class GroupChatSummaryGenerationData(
    val data: GroupChatData,
    val hasMoreCandidateMessages: Boolean
)

/**
 * 群聊发言者选择所需的最小历史投影。
 *
 * @property session 当前群聊会话。
 * @property members 当前群聊成员及角色卡。
 * @property latestNonSystemContent 最近一条非系统消息正文。
 * @property spokenCharacterIdsSinceLastUserMessage 上一条用户消息之后发言过的角色 ID。
 * @property lastCharacterSpeakerId 最近一条角色消息的发言者 ID。
 */
data class GroupChatSpeakerSelectionData(
    val session: GroupChatSession,
    val members: List<GroupChatMemberData>,
    val latestNonSystemContent: String,
    val spokenCharacterIdsSinceLastUserMessage: Set<Long>,
    val lastCharacterSpeakerId: Long?
)

/**
 * 群聊页面使用的最近消息窗口及其聚合元数据。
 *
 * @property data 会话、成员、当前消息窗口和摘要组成的聚合数据。
 * @property canLoadOlderMessages 当前窗口之前是否仍有更早消息。
 * @property hasCharacterMessage 完整群聊历史中是否存在角色消息。
 */
data class GroupChatPageData(
    val data: GroupChatData,
    val canLoadOlderMessages: Boolean,
    val hasCharacterMessage: Boolean
)

/**
 * 群聊页面向前加载的一页历史消息。
 *
 * @property messages 当前页按创建时间正序排列的消息。
 * @property canLoadOlderMessages 当前页之前是否仍有更早消息。
 */
data class GroupChatMessagePage(
    val messages: List<GroupChatMessage>,
    val canLoadOlderMessages: Boolean
)

/**
 * 群聊会话聚合仓库。
 *
 * 负责跨会话、成员、角色、消息和摘要表的事务操作，并保证成员排序、会话活跃时间
 * 及摘要覆盖边界保持一致。
 */
class GroupChatRepository(
    private val mAppDatabase: AppDatabase,
    private val mGson: Gson
) {
    /** 群聊会话基本信息。 */
    private val mSessionDao = mAppDatabase.getGroupChatSessionDao()
    /** 成员关系、静音和顺序。 */
    private val mMemberDao = mAppDatabase.getGroupChatMemberDao()
    /** 群聊历史消息。 */
    private val mMessageDao = mAppDatabase.getGroupChatMessageDao()
    /** 将成员关系补全为角色卡数据。 */
    private val mCharacterDao = mAppDatabase.getCharacterDao()
    /** 群聊摘要及覆盖边界。 */
    private val mSummaryDao = mAppDatabase.getGroupChatSummaryDao()

    /** 按最近活跃时间读取全部群聊会话。 */
    suspend fun getAllSessions(): List<GroupChatSession> {
        return mSessionDao.getAllSessions()
    }

    /** 一次读取首页群聊列表需要的轻量会话概览。 */
    suspend fun getSessionOverviews(): List<GroupChatSessionOverview> {
        return mSessionDao.getSessionOverviews()
    }

    /** 根据主键读取群聊会话。 */
    suspend fun getSessionById(id: Long): GroupChatSession? {
        return mSessionDao.getSessionById(id)
    }

    /** 读取并补全当前群聊的有序成员列表。 */
    suspend fun getMembers(sessionId: Long): List<GroupChatMemberData> {
        return getMemberData(sessionId)
    }

    /** 在同一事务中读取会话、成员、消息和最新摘要。 */
    suspend fun getGroupChatData(sessionId: Long): GroupChatData? {
        return mAppDatabase.withTransaction {
            val session = mSessionDao.getSessionById(sessionId) ?: return@withTransaction null
            GroupChatData(
                session = session,
                members = getMemberData(sessionId),
                messages = mMessageDao.getMessages(sessionId),
                summary = mSummaryDao.getLatest(sessionId)
            )
        }
    }

    /**
     * 在同一事务中读取群聊摘要所需的会话数据和最早候选窗口。
     *
     * 最新未覆盖消息始终附在窗口末尾，供 Builder 继续按旧规则排除；有限窗口额外查询
     * 一条候选消息，仅用于判断 Token 预算允许时是否需要扩展。
     *
     * @param sessionId 群聊会话 ID。
     * @param maxCandidateMessages 本轮最多读取的可摘要消息数；0 表示读取完整范围。
     * @return 群聊摘要聚合数据；会话不存在时返回 null。
     */
    suspend fun getGroupChatSummaryData(
        sessionId: Long,
        maxCandidateMessages: Int = 0
    ): GroupChatSummaryGenerationData? {
        require(maxCandidateMessages >= 0) { "maxCandidateMessages must not be negative" }
        return mAppDatabase.withTransaction {
            // 会话、成员和摘要必须与候选消息共享同一数据库快照
            val session = mSessionDao.getSessionById(sessionId) ?: return@withTransaction null
            val summary = mSummaryDao.getLatest(sessionId)
            val messageWindow = loadSummaryMessagesAfterId(
                sessionId = sessionId,
                coveredMessageId = summary?.coveredMessageId ?: 0L,
                maxCandidateMessages = maxCandidateMessages
            )
            GroupChatSummaryGenerationData(
                data = GroupChatData(
                    session = session,
                    members = getMemberData(sessionId),
                    messages = messageWindow.messages,
                    summary = summary
                ),
                hasMoreCandidateMessages = messageWindow.hasMoreCandidateMessages
            )
        }
    }

    /**
     * 在同一事务中读取群聊生成所需的最近历史窗口和完整消息总数。
     *
     * @param sessionId 群聊会话 ID。
     * @param maxHistoryMessages 最多读取的最近历史消息数；0 表示不限制。
     * @return 群聊生成快照；会话不存在时返回 null。
     */
    suspend fun getGroupChatPromptData(
        sessionId: Long,
        maxHistoryMessages: Int
    ): GroupChatPromptData? {
        require(maxHistoryMessages >= 0) { "maxHistoryMessages must not be negative" }
        return mAppDatabase.withTransaction {
            // 在事务快照中读取会话及配置允许的最近消息
            val session = mSessionDao.getSessionById(sessionId) ?: return@withTransaction null
            val messages = if (maxHistoryMessages == 0) {
                mMessageDao.getMessages(sessionId)
            } else {
                mMessageDao.getLatestMessagePage(sessionId, maxHistoryMessages).asReversed()
            }
            // 完整计数独立保留给世界书时序，不能被 Prompt 消息窗口替代
            GroupChatPromptData(
                data = GroupChatData(
                    session = session,
                    members = getMemberData(sessionId),
                    messages = messages,
                    summary = mSummaryDao.getLatest(sessionId)
                ),
                totalMessageCount = mMessageDao.getMessageCount(sessionId)
            )
        }
    }

    /**
     * 读取发言者选择需要的最小数据，避免为一次调度反序列化完整群聊历史。
     *
     * Pooled 模式只投影上一条用户消息之后出现过的发言者 ID；其他模式不需要该集合。
     * 最近角色和非系统消息通过末尾索引查询，保持与完整正序列表查找相同的结果。
     *
     * @param sessionId 群聊会话 ID。
     * @param includeSpeakerPool 是否读取 Pooled 模式当前轮次已经发言的角色集合。
     * @return 发言者选择数据；会话不存在时返回 null。
     */
    suspend fun getSpeakerSelectionData(
        sessionId: Long,
        includeSpeakerPool: Boolean = true
    ): GroupChatSpeakerSelectionData? {
        return mAppDatabase.withTransaction {
            val session = mSessionDao.getSessionById(sessionId) ?: return@withTransaction null
            val members = getMemberData(sessionId)
            // Pooled 模式只读取轮次边界后的去重发言者 ID，不加载消息正文
            val spokenCharacterIds = if (
                includeSpeakerPool &&
                session.activationStrategy == GroupChatSession.ActivationStrategy.Pooled
            ) {
                val latestUserMessage = mMessageDao.getLatestUserMessage(sessionId)
                if (latestUserMessage == null) {
                    mMessageDao.getSpeakerIds(sessionId)
                } else {
                    mMessageDao.getSpeakerIdsAfter(
                        sessionId = sessionId,
                        afterCreateTime = latestUserMessage.createTime,
                        afterMessageId = latestUserMessage.id
                    )
                }
            } else {
                emptyList()
            }
            // 独立读取末尾投影，分别供空输入激活和连续发言限制使用
            GroupChatSpeakerSelectionData(
                session = session,
                members = members,
                latestNonSystemContent = mMessageDao.getLatestNonSystemMessage(sessionId)
                    ?.content
                    .orEmpty(),
                spokenCharacterIdsSinceLastUserMessage = spokenCharacterIds.toSet(),
                lastCharacterSpeakerId = mMessageDao.getLatestCharacterMessage(sessionId)
                    ?.speakerCharacterId
            )
        }
    }

    /**
     * 在同一事务中读取群聊页面元数据和末尾消息窗口。
     *
     * 摘要与普通生成分别使用独立的有限历史接口，页面窗口不参与 Prompt 构建。
     *
     * @param sessionId 群聊会话 ID。
     * @param pageSize 页面实际接收的最大消息数量。
     * @return 群聊页面数据；会话不存在时返回 null。
     */
    suspend fun getGroupChatPageData(
        sessionId: Long,
        pageSize: Int
    ): GroupChatPageData? {
        require(pageSize > 0) { "pageSize must be positive" }
        return mAppDatabase.withTransaction {
            // 页面聚合必须共享同一快照，避免会话元数据与消息窗口不一致
            val session = mSessionDao.getSessionById(sessionId) ?: return@withTransaction null
            val page = mMessageDao.getLatestMessagePage(sessionId, pageSize + 1)
                .toGroupChatMessagePage(pageSize)
            GroupChatPageData(
                data = GroupChatData(
                    session = session,
                    members = getMemberData(sessionId),
                    messages = page.messages,
                    summary = mSummaryDao.getLatest(sessionId)
                ),
                canLoadOlderMessages = page.canLoadOlderMessages,
                hasCharacterMessage = mMessageDao.hasCharacterMessage(sessionId)
            )
        }
    }

    /**
     * 使用稳定游标向前读取一页群聊消息。
     *
     * @param sessionId 群聊会话 ID。
     * @param beforeCreateTime 当前最早已加载消息的创建时间。
     * @param beforeMessageId 当前最早已加载消息的 ID。
     * @param pageSize 页面实际接收的最大消息数量。
     * @return 更早消息页。
     */
    suspend fun getMessagePageBefore(
        sessionId: Long,
        beforeCreateTime: Long,
        beforeMessageId: Long,
        pageSize: Int
    ): GroupChatMessagePage {
        require(pageSize > 0) { "pageSize must be positive" }
        return mMessageDao.getMessagePageBefore(
            sessionId = sessionId,
            beforeCreateTime = beforeCreateTime,
            beforeMessageId = beforeMessageId,
            limit = pageSize + 1
        ).toGroupChatMessagePage(pageSize)
    }

    /**
     * 创建群聊、成员关系、会话世界书选择，并写入已解析的开场消息。
     *
     * 处理步骤：
     * - 校验参演成员数量不少于 2 人；
     * - 插入群聊会话基础记录；
     * - 批量插入有序成员关联记录；
     * - 批量插入已规划且非空的开场白消息；
     * - 更新会话最新活跃时间戳为最后一条开场白时间。
     */
    suspend fun createSession(
        title: String,
        userName: String,
        userDescription: String,
        characterIds: List<Long>,
        lorebookEntryIds: List<Long> = emptyList(),
        activationStrategy: GroupChatSession.ActivationStrategy,
        allowSelfResponses: Boolean,
        openingMessages: List<GroupChatOpeningMessage> = emptyList(),
        createTime: Long = System.currentTimeMillis()
    ): Long {
        require(characterIds.distinct().size >= 2) {
            "A group chat requires at least two characters"
        }
        return mAppDatabase.withTransaction {
            // 插入群聊会话记录
            val sessionId = mSessionDao.insertOrReplace(
                GroupChatSession(
                    title = title,
                    createTime = createTime,
                    latestTime = createTime,
                    userName = userName,
                    userDescription = userDescription,
                    lorebookEntrySet = mGson.toJson(lorebookEntryIds.distinct()),
                    activationStrategy = activationStrategy,
                    allowSelfResponses = allowSelfResponses
                )
            )
            // 插入初始群成员列表
            mMemberDao.insertOrReplaceAll(
                characterIds.distinct().mapIndexed { index, characterId ->
                    GroupChatMember(
                        sessionId = sessionId,
                        characterId = characterId,
                        sortOrder = index
                    )
                }
            )
            // 写入开场白消息
            val selectedCharacterIds = characterIds.distinct().toSet()
            val validOpenings = openingMessages.filter {
                it.characterId in selectedCharacterIds && it.content.isNotBlank()
            }
            validOpenings.forEachIndexed { index, opening ->
                mMessageDao.insertOrReplace(
                    GroupChatMessage(
                        sessionId = sessionId,
                        createTime = createTime + index,
                        source = GroupChatMessage.Source.Character,
                        content = opening.content.trim(),
                        speakerCharacterId = opening.characterId,
                        speakerNameSnapshot = opening.characterName
                    )
                )
            }
            // 同步会话最新活跃时间戳
            if (validOpenings.isNotEmpty()) {
                mSessionDao.updateLatestTime(
                    sessionId,
                    createTime + validOpenings.lastIndex
                )
            }
            sessionId
        }
    }

    /** 写入群聊消息并同步刷新会话最近活动时间。 */
    suspend fun createMessage(
        sessionId: Long,
        source: GroupChatMessage.Source,
        content: String,
        speakerCharacterId: Long?,
        speakerNameSnapshot: String,
        generationBatchId: String? = null,
        createTime: Long = System.currentTimeMillis()
    ): Long {
        return mAppDatabase.withTransaction {
            val messageId = mMessageDao.insertOrReplace(
                GroupChatMessage(
                    sessionId = sessionId,
                    createTime = createTime,
                    source = source,
                    content = content,
                    speakerCharacterId = speakerCharacterId,
                    speakerNameSnapshot = speakerNameSnapshot,
                    generationBatchId = generationBatchId
                )
            )
            mSessionDao.updateLatestTime(sessionId, createTime)
            messageId
        }
    }

    /** 更新消息内容，并使覆盖该消息的旧摘要失效。 */
    suspend fun updateMessageContent(id: Long, content: String) {
        mAppDatabase.withTransaction {
            val message = mMessageDao.getMessageById(id) ?: return@withTransaction
            mMessageDao.updateContent(id, content)
            mSummaryDao.deleteCovering(message.sessionId, message.id)
        }
    }

    /** 删除单条消息，并清理覆盖范围已失效的摘要。 */
    suspend fun deleteMessage(id: Long) {
        mAppDatabase.withTransaction {
            val message = mMessageDao.getMessageById(id) ?: return@withTransaction
            mSummaryDao.deleteCovering(message.sessionId, message.id)
            mMessageDao.deleteById(id)
        }
    }

    /** 从指定消息起删除后续历史，用于重新生成。 */
    suspend fun deleteMessagesFrom(id: Long) {
        mAppDatabase.withTransaction {
            val message = mMessageDao.getMessageById(id) ?: return@withTransaction
            mSummaryDao.deleteCovering(message.sessionId, message.id)
            mMessageDao.deleteFrom(message.sessionId, message.id)
        }
    }

    /** 更新成员静音状态；静音成员仍保留在会话中。 */
    suspend fun updateMemberMuted(sessionId: Long, characterId: Long, muted: Boolean) {
        mMemberDao.updateMuted(sessionId, characterId, muted)
    }

    /** 将角色追加到群聊成员列表末尾。 */
    suspend fun addMember(sessionId: Long, characterId: Long) {
        mAppDatabase.withTransaction {
            val nextOrder = mMemberDao.getMembers(sessionId)
                .maxOfOrNull { it.sortOrder }
                ?.plus(1)
                ?: 0
            mMemberDao.insertOrIgnore(
                GroupChatMember(
                    sessionId = sessionId,
                    characterId = characterId,
                    sortOrder = nextOrder
                )
            )
        }
    }

    /** 移除成员并重排顺序，同时保证群聊至少保留两名成员。 */
    suspend fun removeMember(sessionId: Long, characterId: Long) {
        mAppDatabase.withTransaction {
            val members = mMemberDao.getMembers(sessionId)
            require(members.size > 2) { "A group chat requires at least two characters" }
            mMemberDao.deleteMember(sessionId, characterId)
            mMemberDao.getMembers(sessionId).forEachIndexed { index, member ->
                mMemberDao.updateSortOrder(sessionId, member.characterId, index)
            }
        }
    }

    /** 按相对偏移移动成员，并持久化新的连续排序。 */
    suspend fun moveMember(sessionId: Long, characterId: Long, offset: Int) {
        mAppDatabase.withTransaction {
            val members = mMemberDao.getMembers(sessionId).toMutableList()
            val from = members.indexOfFirst { it.characterId == characterId }
            if (from < 0) return@withTransaction
            val to = (from + offset).coerceIn(0, members.lastIndex)
            if (from == to) return@withTransaction
            val moved = members.removeAt(from)
            members.add(to, moved)
            members.forEachIndexed { index, member ->
                mMemberDao.updateSortOrder(sessionId, member.characterId, index)
            }
        }
    }

    /**
     * 按给定主键顺序批量重排群聊的全部成员。
     *
     * @param sessionId 群聊会话 ID
     * @param orderedCharacterIds 当前会话全部成员的最终角色主键顺序
     * @return 成员快照有效且顺序成功提交时返回 true
     */
    suspend fun reorderMembers(
        sessionId: Long,
        orderedCharacterIds: List<Long>
    ): Boolean = mAppDatabase.withTransaction {
        val members = mMemberDao.getMembers(sessionId)
        // 完整校验成员快照，避免拖动期间的增删操作被旧顺序覆盖
        if (orderedCharacterIds.distinct().size != orderedCharacterIds.size) {
            return@withTransaction false
        }
        if (members.map { it.characterId }.toSet() != orderedCharacterIds.toSet()) {
            return@withTransaction false
        }
        // 仅写入实际变化的连续序号
        val memberById = members.associateBy { it.characterId }
        orderedCharacterIds.forEachIndexed { index, characterId ->
            if (memberById.getValue(characterId).sortOrder != index) {
                mMemberDao.updateSortOrder(sessionId, characterId, index)
            }
        }
        true
    }

    /** 覆盖保存会话级设置。 */
    suspend fun updateSession(session: GroupChatSession) {
        mSessionDao.update(session)
    }

    /** 解析会话显式启用的世界书条目 ID。 */
    suspend fun getSessionLorebookEntryIds(session: GroupChatSession): List<Long> {
        return runCatching {
            mGson.fromJson(session.lorebookEntrySet, Array<Long>::class.java)
                ?.toList()
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    /** 保存会话显式启用的世界书条目 ID，并去除重复项。 */
    suspend fun updateSessionLorebookEntryIds(sessionId: Long, entryIds: List<Long>) {
        val session = mSessionDao.getSessionById(sessionId) ?: return
        mSessionDao.update(
            session.copy(lorebookEntrySet = mGson.toJson(entryIds.distinct()))
        )
    }

    /** 持久化世界书粘性和冷却等时序状态。 */
    suspend fun updateWorldInfoState(sessionId: Long, stateJson: String) {
        val session = mSessionDao.getSessionById(sessionId) ?: return
        mSessionDao.update(session.copy(worldInfoStateJson = stateJson))
    }

    /** 获取尚未被最新摘要覆盖的消息。 */
    suspend fun getMessagesAfterLatestSummary(sessionId: Long): List<GroupChatMessage> {
        val summary = mSummaryDao.getLatest(sessionId)
        return mMessageDao.getMessagesAfterId(sessionId, summary?.coveredMessageId ?: 0L)
    }

    /** 统计最新群聊摘要之后尚未覆盖的消息数量。 */
    suspend fun getUnsummarizedMessageCount(sessionId: Long): Int {
        return mAppDatabase.withTransaction {
            val summary = mSummaryDao.getLatest(sessionId)
            mMessageDao.getMessageCountAfterId(
                sessionId = sessionId,
                messageId = summary?.coveredMessageId ?: 0L
            )
        }
    }

    /** 新增摘要或更新指定摘要的内容与覆盖边界。 */
    suspend fun saveSummary(
        sessionId: Long,
        content: String,
        coveredMessageId: Long,
        summaryIdToUpdate: Long? = null
    ): Long {
        val now = System.currentTimeMillis()
        if (summaryIdToUpdate != null) {
            mSummaryDao.updateContent(summaryIdToUpdate, content, coveredMessageId, now)
            return summaryIdToUpdate
        }
        return mSummaryDao.insertOrReplace(
            GroupChatSummary(
                sessionId = sessionId,
                createTime = now,
                content = content,
                coveredMessageId = coveredMessageId
            )
        )
    }

    /**
     * 保存用户编辑的当前摘要。
     *
     * 清空摘要时写入边界为 0 的空快照，使全部普通消息重新进入上下文。非空内容仅在
     * 当前快照已覆盖最后一条消息时原地更新，否则插入覆盖到最新消息的新快照。
     */
    suspend fun updateCurrentSummary(
        sessionId: Long,
        content: String,
        createTime: Long = System.currentTimeMillis()
    ) {
        mAppDatabase.withTransaction {
            val latest = mSummaryDao.getLatest(sessionId)
            if (content.isBlank()) {
                if (latest?.coveredMessageId == 0L) {
                    mSummaryDao.updateContent(latest.id, "", 0L, createTime)
                    return@withTransaction
                }
                mSummaryDao.insertOrReplace(
                    GroupChatSummary(
                        sessionId = sessionId,
                        createTime = createTime,
                        content = "",
                        coveredMessageId = 0L
                    )
                )
                return@withTransaction
            }
            val latestMessageId = mMessageDao.getLatestMessage(sessionId)?.id ?: 0L
            if (latest != null && latest.coveredMessageId == latestMessageId) {
                mSummaryDao.updateContent(
                    latest.id,
                    content,
                    latestMessageId,
                    createTime
                )
            } else {
                mSummaryDao.insertOrReplace(
                    GroupChatSummary(
                        sessionId = sessionId,
                        createTime = createTime,
                        content = content,
                        coveredMessageId = latestMessageId
                    )
                )
            }
        }
    }

    /** 删除最新群聊摘要快照，使上一份摘要重新生效。 */
    suspend fun restorePreviousSummary(sessionId: Long): Boolean {
        return mAppDatabase.withTransaction {
            val latest = mSummaryDao.getLatest(sessionId)
                ?: return@withTransaction false
            val previous = mSummaryDao.getPreviousById(sessionId, latest.id)
                ?: return@withTransaction false
            mSummaryDao.delete(latest)
            previous.id > 0L
        }
    }

    suspend fun deleteSession(id: Long) {
        mSessionDao.deleteById(id)
    }

    suspend fun getLatestMessage(sessionId: Long): GroupChatMessage? {
        return mMessageDao.getLatestMessage(sessionId)
    }

    /** 读取当前群聊最近一条角色消息。 */
    suspend fun getLatestCharacterMessage(sessionId: Long): GroupChatMessage? {
        return mMessageDao.getLatestCharacterMessage(sessionId)
    }

    /** 根据主键读取一条群聊消息。 */
    suspend fun getMessageById(id: Long): GroupChatMessage? {
        return mMessageDao.getMessageById(id)
    }

    suspend fun getMessageCount(sessionId: Long): Int {
        return mMessageDao.getMessageCount(sessionId)
    }

    /** 读取群聊摘要窗口，并把真实最新消息附在末尾供 Builder 按旧规则排除。 */
    private suspend fun loadSummaryMessagesAfterId(
        sessionId: Long,
        coveredMessageId: Long,
        maxCandidateMessages: Int
    ): GroupSummaryMessageWindow {
        // 无限制调用保留旧接口的完整范围语义
        if (maxCandidateMessages == 0) {
            return GroupSummaryMessageWindow(
                messages = mMessageDao.getMessagesAfterId(sessionId, coveredMessageId),
                hasMoreCandidateMessages = false
            )
        }
        // 最新消息不参与候选窗口计数，但必须交给 Builder 执行固定排除规则
        val latestMessage = mMessageDao.getLatestMessageAfterId(
            sessionId = sessionId,
            messageId = coveredMessageId
        ) ?: return GroupSummaryMessageWindow(emptyList(), false)
        val queryLimit = if (maxCandidateMessages == Int.MAX_VALUE) {
            Int.MAX_VALUE
        } else {
            maxCandidateMessages + 1
        }
        val candidates = mMessageDao.getFirstMessagesBeforeLatestAfterId(
            sessionId = sessionId,
            messageId = coveredMessageId,
            latestCreateTime = latestMessage.createTime,
            latestMessageId = latestMessage.id,
            limit = queryLimit
        )
        return GroupSummaryMessageWindow(
            messages = candidates.take(maxCandidateMessages) + latestMessage,
            hasMoreCandidateMessages = candidates.size > maxCandidateMessages
        )
    }

    /** 补全群聊成员关系对应的角色卡，已被删除的角色不会进入业务聚合。 */
    private suspend fun getMemberData(sessionId: Long): List<GroupChatMemberData> {
        return mMemberDao.getMembers(sessionId).mapNotNull { relation ->
            mCharacterDao.getCharacterById(relation.characterId)?.let { character ->
                GroupChatMemberData(relation, character)
            }
        }
    }

    /** 将数据库倒序结果裁成页面需要的正序消息，并保留是否还有更早记录。 */
    private fun List<GroupChatMessage>.toGroupChatMessagePage(
        pageSize: Int
    ): GroupChatMessagePage {
        return GroupChatMessagePage(
            messages = take(pageSize).asReversed(),
            canLoadOlderMessages = size > pageSize
        )
    }

    /** Builder 使用的有限群聊候选窗口及其扩展标记。 */
    private data class GroupSummaryMessageWindow(
        val messages: List<GroupChatMessage>,
        val hasMoreCandidateMessages: Boolean
    )
}
