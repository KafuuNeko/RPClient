package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.entity.MessageImageEntity
import me.kafuuneko.rpclient.libs.room.model.MessageType

/**
 * 消息图片附件关系的数据库访问接口。
 *
 * - 查询结果按 `position` 排序，保证图片顺序与用户草稿一致。
 * - 批量按消息 ID 查询供 Prompt 候选窗口和分页显示使用；长 ID 列表需要
 *   在 Repository 层分批执行以规避 SQLite 查询参数上限。
 * - 本接口只表达查询与批量写入能力；附件与消息、文件索引的事务一致性
 *   由 Repository 保证。
 */
@Dao
interface MessageImageDao {
    /** 导出前仅检测附件是否存在，不加载整份历史。 */
    @Query("SELECT EXISTS(SELECT 1 FROM message_images AS images INNER JOIN chat_messages AS messages ON messages.id = images.messageId WHERE images.messageType = 'Single' AND messages.sessionId = :sessionId)")
    suspend fun hasSingleSessionImages(sessionId: Long): Boolean

    /**
     * 读取单条消息的全部图片附件，按 position 排序。
     *
     * @param messageType 消息所属的聊天类型。
     * @param messageId 消息主键。
     * @return 按显示顺序排列的附件列表。
     */
    @Query(
        """
        SELECT * FROM message_images
        WHERE messageType = :messageType AND messageId = :messageId
        ORDER BY position ASC
        """
    )
    suspend fun getByMessage(messageType: MessageType, messageId: Long): List<MessageImageEntity>

    /**
     * 按消息 ID 批次读取同类型消息的图片附件，按消息 ID 和 position 排序。
     *
     * @param messageType 消息所属的聊天类型。
     * @param messageIds 要查询的消息 ID 列表。
     * @return 按消息 ID 和显示顺序排列的附件列表。
     */
    @Query(
        """
        SELECT * FROM message_images
        WHERE messageType = :messageType AND messageId IN (:messageIds)
        ORDER BY messageId ASC, position ASC
        """
    )
    suspend fun getByMessages(
        messageType: MessageType,
        messageIds: List<Long>
    ): List<MessageImageEntity>

    /**
     * 按 imageUuid 列表批量读取附件记录。
     *
     * @param imageUuids 文件索引 UUID 列表。
     * @return 匹配的附件记录列表。
     */
    @Query("SELECT * FROM message_images WHERE imageUuid IN (:imageUuids)")
    suspend fun getByImageUuids(imageUuids: List<String>): List<MessageImageEntity>

    /**
     * 批量插入图片附件关系。
     *
     * @param images 要插入的附件实体列表。
     */
    @Insert
    suspend fun insertAll(images: List<MessageImageEntity>)

    /**
     * 删除单条消息的全部图片附件关系。
     *
     * @param messageType 消息所属的聊天类型。
     * @param messageId 消息主键。
     */
    @Query(
        """
        DELETE FROM message_images
        WHERE messageType = :messageType AND messageId = :messageId
        """
    )
    suspend fun deleteByMessage(messageType: MessageType, messageId: Long)

    /**
     * 按消息 ID 批次删除同类型消息的图片附件关系。
     *
     * @param messageType 消息所属的聊天类型。
     * @param messageIds 要删除附件的消息 ID 列表。
     */
    @Query(
        """
        DELETE FROM message_images
        WHERE messageType = :messageType AND messageId IN (:messageIds)
        """
    )
    suspend fun deleteByMessages(messageType: MessageType, messageIds: List<Long>)

    /**
     * 读取指定消息 ID 范围内的图片附件 UUID 列表，供级联清理使用。
     *
     * @param messageType 消息所属的聊天类型。
     * @param fromMessageId 起始消息 ID（含）。
     * @return 范围内全部附件的 imageUuid。
     */
    @Query(
        """
        SELECT imageUuid FROM message_images
        WHERE messageType = :messageType AND messageId >= :fromMessageId
        """
    )
    suspend fun getImageUuidsFrom(messageType: MessageType, fromMessageId: Long): List<String>

    /**
     * 删除指定消息 ID 范围内的图片附件关系，供截断重生成使用。
     *
     * @param messageType 消息所属的聊天类型。
     * @param fromMessageId 起始消息 ID（含）。
     */
    @Query(
        """
        DELETE FROM message_images
        WHERE messageType = :messageType AND messageId >= :fromMessageId
        """
    )
    suspend fun deleteFrom(messageType: MessageType, fromMessageId: Long)

    /**
     * 读取指定消息类型下全部消息 ID 列表，供会话级清理使用。
     *
     * @param messageType 消息所属的聊天类型。
     * @param messageIds 要查询的消息 ID 列表。
     * @return 范围内全部附件的 imageUuid。
     */
    @Query(
        """
        SELECT imageUuid FROM message_images
        WHERE messageType = :messageType AND messageId IN (:messageIds)
        """
    )
    suspend fun getImageUuidsByMessages(
        messageType: MessageType,
        messageIds: List<Long>
    ): List<String>
}
