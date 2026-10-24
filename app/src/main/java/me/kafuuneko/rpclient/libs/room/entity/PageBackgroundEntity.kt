package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 页面默认背景；每行独占文件索引，共享字节由 FileRepository 的 hash 管理。 */
@Entity(
    tableName = "page_backgrounds",
    foreignKeys = [ForeignKey(
        entity = FileEntity::class, parentColumns = ["uuid"], childColumns = ["fileUuid"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index(value = ["fileUuid"], unique = true)]
)
data class PageBackgroundEntity(
    @PrimaryKey val page: String,
    val fileUuid: String?,
    val blur: Int,
    val scrim: Float,
    val opacity: Float,
    val focalX: Float,
    val focalY: Float,
    @ColumnInfo(defaultValue = "1.0") val topBarOpacity: Float = 1f,
    @ColumnInfo(defaultValue = "1.0") val bottomBarOpacity: Float = 1f,
    @ColumnInfo(defaultValue = "1.0") val bubbleOpacity: Float = 1f,
    @ColumnInfo(defaultValue = "0") val topBarBlur: Int = 0,
    @ColumnInfo(defaultValue = "0") val bottomBarBlur: Int = 0,
    @ColumnInfo(defaultValue = "0") val bubbleBlur: Int = 0,
    @ColumnInfo(defaultValue = "0") val contentBlur: Int = 0,
    @ColumnInfo(defaultValue = "1") val followGeneral: Boolean = true,
    @ColumnInfo(defaultValue = "0") val useCharacterBackground: Boolean = false
)
