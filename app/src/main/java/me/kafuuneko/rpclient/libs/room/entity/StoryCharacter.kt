package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Story 与候选角色卡的关联。
 *
 * 关联只保存 Story 专属的激活方式和顺序，不复制角色卡正文；角色名称始终以角色卡为准。
 */
@Entity(
    tableName = "story_characters",
    primaryKeys = ["storyId", "characterId"],
    foreignKeys = [
        ForeignKey(
            entity = Story::class,
            parentColumns = ["id"],
            childColumns = ["storyId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Character::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("characterId"),
        Index(value = ["storyId", "sortOrder"])
    ]
)
data class StoryCharacter(
    // 所属故事 ID；故事删除时关联记录级联删除。
    val storyId: Long,
    // 关联角色 ID；角色删除时关联记录级联删除。
    val characterId: Long,
    // 角色在故事候选角色列表中的顺序，数值越小越靠前。
    val sortOrder: Int,
    // 角色在故事生成中的激活方式，取值见 ACTIVATION_* 常量。
    val activationMode: Int = ACTIVATION_AUTO
) {
    companion object {
        const val ACTIVATION_PRIMARY = 0
        const val ACTIVATION_ALWAYS = 1
        const val ACTIVATION_AUTO = 2


        fun isValidActivationMode(value: Int): Boolean {
            return value == ACTIVATION_ALWAYS
                || value == ACTIVATION_AUTO
                || value == ACTIVATION_PRIMARY
        }
    }
}
