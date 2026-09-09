package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import me.kafuuneko.rpclient.libs.room.model.MessageType

/**
 * 消息图片附件关联实体，记录一条消息所关联的原图文件索引及其显示顺序。
 *
 * - 联合主键 `(messageType, messageId, position)` 天然支持同一消息关联多张图片，
 *   并为按消息读取提供前缀索引。
 * - [imageUuid] 的唯一索引保证每条附件关系持有独立的文件索引 UUID。
 * - [imageUuid] → `files.uuid` 外键使用 NO ACTION 策略，要求先删除附件关系再释放文件索引；
 *   不通过删文件静默删除消息图片。
 * - [messageId] 不构造双外键，因为 SQLite 外键无法按 [messageType] 动态选择父表；
 *   消息一侧的引用完整性由 Repository 事务校验与显式清理保证。
 */
@Entity(
    tableName = "message_images",
    primaryKeys = ["messageType", "messageId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = FileEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["imageUuid"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        // 唯一索引：每条附件关系拥有独立 UUID，不允许多条记录共享同一文件索引。
        Index(value = ["imageUuid"], unique = true),
        // 辅助索引：加速按消息批量读取时的联合查询。
        Index(value = ["messageType", "messageId"])
    ]
)
data class MessageImageEntity(
    // 消息所属的聊天类型编码（single / group），通过 Converter 映射 MessageType。
    @ColumnInfo(defaultValue = "'single'")
    val messageType: MessageType,
    // 所属消息 ID；正式记录必须关联已存在的消息。
    val messageId: Long,
    // 图片在消息内的显示顺序，非负整数；查询必须显式排序。
    val position: Int,
    // 原图文件索引 UUID，指向 files.uuid；不是哈希、URI 或远端 file ID。
    val imageUuid: String
) {
    init {
        require(position >= 0) { "图片 position 不能为负数，实际为: $position" }
    }
}
