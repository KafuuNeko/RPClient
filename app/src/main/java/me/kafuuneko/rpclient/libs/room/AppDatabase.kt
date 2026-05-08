package me.kafuuneko.rpclient.libs.room

import androidx.room.Database
import androidx.room.RoomDatabase
import me.kafuuneko.rpclient.libs.room.dao.CharacterDao
import me.kafuuneko.rpclient.libs.room.dao.LorebookDao
import me.kafuuneko.rpclient.libs.room.dao.LorebookEntryDao
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry


@Database(
    entities = [
        Character::class,
        Lorebook::class,
        LorebookEntry::class
    ],
    version = 1,
    autoMigrations = [],
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun getCharacterDao(): CharacterDao
    abstract fun getLorebookDao(): LorebookDao
    abstract fun getLorebookEntryDao(): LorebookEntryDao

}
