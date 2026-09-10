package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.withTransaction
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.MessageImageEntity
import me.kafuuneko.rpclient.libs.room.model.MessageImageInput
import me.kafuuneko.rpclient.libs.room.model.MessageImagePolicy
import me.kafuuneko.rpclient.libs.room.model.MessageImageWithFile
import me.kafuuneko.rpclient.libs.room.model.MessageKey
import me.kafuuneko.rpclient.libs.room.model.MessageType
import me.kafuuneko.rpclient.libs.room.model.MessageWithImages
import me.kafuuneko.rpclient.libs.room.model.PreparedFile

/**
 * 消息附件的共享数据协调能力，由会话及角色仓库编排调用。
 * - 将文件提交、关系变更、消息变更纳入同一短事务。
 * - 类型、父消息、用户来源及会话归属在关系写入前验证。
 * - 聚合读取使用批次查询及一致快照，不改变消息分页和摘要边界。
 */
class MessageImageRepository(
    private val mDatabase: AppDatabase,
    private val mFiles: FileRepository
) {
    private val mImages = mDatabase.getMessageImageDao()
    private val mFileDao = mDatabase.getFileDao()

    /** 先持文件锁，再由文件仓库开启短事务；仅供业务仓库调用，禁止在已有事务内重新加锁。 */
    internal suspend fun <T> mutate(
        prepared: List<PreparedFile> = emptyList(),
        block: suspend FileRepository.Mutation.() -> T
    ): T = mFiles.mutate(prepared, block)

    /** 在所属消息事务内替换有序附件；既有 UUID 不允许跨消息挪用。 */
    internal suspend fun replaceInTransaction(
        mutation: FileRepository.Mutation,
        key: MessageKey,
        sessionId: Long,
        inputs: List<MessageImageInput>
    ) {
        require(inputs.size <= MessageImagePolicy.MAX_IMAGES_PER_MESSAGE) { "每条消息最多四张图片" }
        validateOwner(key, sessionId)
        val old = mImages.getByMessage(key.messageType, key.messageId)
        val oldUuids = old.map { it.imageUuid }.toSet()
        // 新附件必须来自本事务已提交索引的草稿；保留附件必须属于原消息。
        val uuids = inputs.map {
            when (it) {
                is MessageImageInput.Existing -> it.uuid.also { uuid ->
                    require(uuid in oldUuids) { "图片不属于正在编辑的消息" }
                }
                is MessageImageInput.Prepared -> it.value.file.uuid
            }
        }
        require(uuids.distinct().size == uuids.size) { "同一图片引用不能重复使用" }
        mImages.deleteByMessage(key.messageType, key.messageId)
        mImages.insertAll(uuids.mapIndexed { position, uuid ->
            MessageImageEntity(key.messageType, key.messageId, position, uuid)
        })
        mutation.removeFiles((oldUuids - uuids.toSet()).toList())
    }

    /** 在删除父消息之前释放所选消息的附件；ID 集合必须由会话仓库限定范围。 */
    internal suspend fun deleteInTransaction(
        mutation: FileRepository.Mutation,
        type: MessageType,
        ids: List<Long>
    ) {
        ids.distinct().chunked(BATCH_SIZE).forEach { batch ->
            val uuids = mImages.getImageUuidsByMessages(type, batch)
            mImages.deleteByMessages(type, batch)
            mutation.removeFiles(uuids)
        }
    }

    /** 按分支消息 ID 映射复制附件，独立 UUID 共享原图 hash，保留原有 position。 */
    internal suspend fun copyInTransaction(
        mutation: FileRepository.Mutation,
        idMap: Map<Long, Long>,
        targetSessionId: Long
    ) {
        idMap.keys.toList().chunked(BATCH_SIZE).forEach { batch ->
            val images = mImages.getByMessages(MessageType.Single, batch)
            images.groupBy { it.messageId }.forEach { (sourceId, attachments) ->
                val targetId = idMap.getValue(sourceId)
                validateOwner(MessageKey(MessageType.Single, targetId), targetSessionId)
                mImages.insertAll(attachments.map {
                    it.copy(messageId = targetId, imageUuid = mutation.copyReference(it.imageUuid).uuid)
                })
            }
        }
    }

    /** 校验多态父消息，不通过无法表达二选一归属的双外键实现。 */
    private suspend fun validateOwner(key: MessageKey, sessionId: Long) {
        // 单聊与群聊同号消息也分别校验来源和真实会话。
        when (key.messageType) {
            MessageType.Single -> {
                val message = requireNotNull(mDatabase.getChatMessageDao().getMessageById(key.messageId)) {
                    "图片所属消息不存在"
                }
                require(message.sessionId == sessionId && message.source == ChatMessage.Source.User) {
                    "图片只能属于本会话的用户消息"
                }
            }
            MessageType.Group -> {
                val message = requireNotNull(mDatabase.getGroupChatMessageDao().getMessageById(key.messageId)) {
                    "图片所属消息不存在"
                }
                require(message.sessionId == sessionId && message.source == GroupChatMessage.Source.User) {
                    "图片只能属于本会话的用户消息"
                }
            }
        }
    }

    /** 在一致事务内按调用方消息顺序批量返回正文与附件；已删除消息不返回。 */
    suspend fun getMessages(keys: List<MessageKey>): List<MessageWithImages> = mDatabase.withTransaction {
        // 两种消息使用独立 ID 空间，不能合并为只有 Long 的映射。
        val result = mutableMapOf<MessageKey, MessageWithImages>()
        keys.distinct().groupBy { it.messageType }.forEach { (type, typedKeys) ->
            typedKeys.chunked(BATCH_SIZE).forEach { batch ->
                val ids = batch.map { it.messageId }
                val images = mImages.getByMessages(type, ids)
                val files = images.map { it.imageUuid }.chunked(BATCH_SIZE)
                    .flatMap { mFileDao.getByUuids(it) }.associateBy { it.uuid }
                val byMessage = images.groupBy { it.messageId }.mapValues { (_, attachments) ->
                    attachments.map { MessageImageWithFile(it, files[it.imageUuid]) }
                }
                // 正文也批量读取，避免长历史退化成逐消息／逐图片查库。
                when (type) {
                    MessageType.Single -> mDatabase.getChatMessageDao().getByIds(ids).forEach {
                        val key = MessageKey(type, it.id)
                        result[key] = MessageWithImages(key, it.content, it.source.name, it.createTime,
                            byMessage[it.id].orEmpty(), coveredMessageId = it.coveredMessageId)
                    }
                    MessageType.Group -> mDatabase.getGroupChatMessageDao().getByIds(ids).forEach {
                        val key = MessageKey(type, it.id)
                        result[key] = MessageWithImages(key, it.content, it.source.name, it.createTime,
                            byMessage[it.id].orEmpty(), it.speakerCharacterId, it.speakerNameSnapshot)
                    }
                }
            }
        }
        keys.mapNotNull { result[it] }
    }

    private companion object {
        const val BATCH_SIZE = 900
    }
}
