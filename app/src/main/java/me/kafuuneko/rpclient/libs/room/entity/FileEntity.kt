package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 文件实体类，用于在数据库中映射 UUID 和文件的 SHA-256 哈希值（用作实际存储的文件名）。
 *
 * @property uuid 唯一标识符，作为主键。
 * @property hash 文件的 SHA-256 哈希值，对应存储在私有目录下的具体文件名。
 * @property mimeType 文件的 MIME 类型（例如 "image/png", "application/pdf" 等）。
 */
@Entity(tableName = "files")
data class FileEntity(
    @PrimaryKey
    val uuid: String,
    val hash: String,
    val mimeType: String? = null
)
