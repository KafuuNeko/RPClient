package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * 已由用户授权执行内嵌 Regex 脚本的角色。
 *
 * 表中存在记录即表示授权；角色删除后授权通过外键级联清理。
 */
@Entity(
    tableName = "regex_character_authorizations",
    foreignKeys = [
        ForeignKey(
            entity = Character::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        )
    ]
)
data class RegexCharacterAuthorization(
    // 已授权角色 ID；同时作为主键，角色删除时该授权记录级联删除。
    @PrimaryKey
    val characterId: Long
)
