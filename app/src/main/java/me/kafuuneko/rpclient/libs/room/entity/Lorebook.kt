package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "lorebooks"
)
data class Lorebook(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String
)
