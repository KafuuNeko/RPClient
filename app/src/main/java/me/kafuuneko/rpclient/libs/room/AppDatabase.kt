package me.kafuuneko.rpclient.libs.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import me.kafuuneko.rpclient.libs.room.dao.CharacterDao
import me.kafuuneko.rpclient.libs.room.dao.ChatMessageDao
import me.kafuuneko.rpclient.libs.room.dao.ChatSessionDao
import me.kafuuneko.rpclient.libs.room.dao.LLMProviderDao
import me.kafuuneko.rpclient.libs.room.dao.LLMRequestLogDao
import me.kafuuneko.rpclient.libs.room.dao.LorebookDao
import me.kafuuneko.rpclient.libs.room.dao.LorebookEntryDao
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.LLMRequestLog
import me.kafuuneko.rpclient.libs.room.entity.FileEntity
import me.kafuuneko.rpclient.libs.room.dao.FileDao
import me.kafuuneko.rpclient.libs.room.dao.GroupChatMemberDao
import me.kafuuneko.rpclient.libs.room.dao.GroupChatMessageDao
import me.kafuuneko.rpclient.libs.room.dao.GroupChatSessionDao
import me.kafuuneko.rpclient.libs.room.dao.GroupChatSummaryDao
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMember
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSummary

/** RPClient 的 Room 数据库入口，集中声明实体、类型转换器和 DAO。 */
@Database(
    entities = [
        Character::class,
        Lorebook::class,
        LorebookEntry::class,
        ChatSession::class,
        ChatMessage::class,
        LLMProvider::class,
        FileEntity::class,
        LLMRequestLog::class,
        GroupChatSession::class,
        GroupChatMember::class,
        GroupChatMessage::class,
        GroupChatSummary::class
    ],
    version = 1,
    autoMigrations = [],
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun getCharacterDao(): CharacterDao
    abstract fun getLorebookDao(): LorebookDao
    abstract fun getLorebookEntryDao(): LorebookEntryDao
    abstract fun getChatSessionDao(): ChatSessionDao
    abstract fun getChatMessageDao(): ChatMessageDao
    abstract fun getLLMProviderDao(): LLMProviderDao
    abstract fun getFileDao(): FileDao
    abstract fun getLLMRequestLogDao(): LLMRequestLogDao
    abstract fun getGroupChatSessionDao(): GroupChatSessionDao
    abstract fun getGroupChatMemberDao(): GroupChatMemberDao
    abstract fun getGroupChatMessageDao(): GroupChatMessageDao
    abstract fun getGroupChatSummaryDao(): GroupChatSummaryDao

}
