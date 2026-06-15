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

/** 群成员关系及其对应角色卡的聚合数据。 */
data class GroupChatMemberData(
    val relation: GroupChatMember,
    val character: Character
)

/** 群聊页面和生成流程所需的会话聚合数据。 */
data class GroupChatData(
    val session: GroupChatSession,
    val members: List<GroupChatMemberData>,
    val messages: List<GroupChatMessage>,
    val summary: GroupChatSummary?
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

    /** 根据主键读取群聊会话。 */
    suspend fun getSessionById(id: Long): GroupChatSession? {
        return mSessionDao.getSessionById(id)
    }

    /** 在同一事务中读取会话、成员、消息和最新摘要。 */
    suspend fun getGroupChatData(sessionId: Long): GroupChatData? {
        return mAppDatabase.withTransaction {
            val session = mSessionDao.getSessionById(sessionId) ?: return@withTransaction null
            val members = mMemberDao.getMembers(sessionId).mapNotNull { relation ->
                mCharacterDao.getCharacterById(relation.characterId)?.let {
                    GroupChatMemberData(relation, it)
                }
            }
            GroupChatData(
                session = session,
                members = members,
                messages = mMessageDao.getMessages(sessionId),
                summary = mSummaryDao.getLatest(sessionId)
            )
        }
    }

    /** 创建群聊、成员关系、会话世界书选择，并写入已解析的开场消息。 */
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
            mMemberDao.insertOrReplaceAll(
                characterIds.distinct().mapIndexed { index, characterId ->
                    GroupChatMember(
                        sessionId = sessionId,
                        characterId = characterId,
                        sortOrder = index
                    )
                }
            )
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

    suspend fun getMessageCount(sessionId: Long): Int {
        return mMessageDao.getMessageCount(sessionId)
    }
}
