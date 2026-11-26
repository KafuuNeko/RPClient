package me.kafuuneko.rpclient.libs.room

import me.kafuuneko.rpclient.libs.room.migration.MemorySchemaMigration
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import me.kafuuneko.rpclient.libs.room.dao.AgentRunDao
import me.kafuuneko.rpclient.libs.room.dao.SceneStateDao
import me.kafuuneko.rpclient.libs.room.entity.AgentRun
import me.kafuuneko.rpclient.libs.room.entity.AgentStep
import me.kafuuneko.rpclient.libs.room.entity.ConversationToolPolicy
import me.kafuuneko.rpclient.libs.room.entity.ToolInvocation
import me.kafuuneko.rpclient.libs.room.entity.SceneState
import me.kafuuneko.rpclient.libs.room.entity.SceneStateRevision
import me.kafuuneko.rpclient.libs.room.dao.GroupChatMemoryDao
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMemory
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMemoryRevision
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMemorySource
import me.kafuuneko.rpclient.libs.room.dao.ChatMemoryDao
import me.kafuuneko.rpclient.libs.room.entity.ChatMemory
import me.kafuuneko.rpclient.libs.room.entity.ChatMemoryRevision
import me.kafuuneko.rpclient.libs.room.entity.ChatMemorySource
import me.kafuuneko.rpclient.libs.room.dao.CharacterMemoryDao
import me.kafuuneko.rpclient.libs.room.entity.CharacterMemory
import me.kafuuneko.rpclient.libs.room.entity.CharacterMemoryRevision
import me.kafuuneko.rpclient.libs.room.entity.CharacterMemorySource
import me.kafuuneko.rpclient.libs.room.dao.CharacterDao
import me.kafuuneko.rpclient.libs.room.dao.CharacterLLMProviderAssociationDao
import me.kafuuneko.rpclient.libs.room.dao.ChatMessageDao
import me.kafuuneko.rpclient.libs.room.dao.ChatSessionDao
import me.kafuuneko.rpclient.libs.room.dao.ContentSearchDao
import me.kafuuneko.rpclient.libs.room.dao.FileDao
import me.kafuuneko.rpclient.libs.room.dao.GroupChatMemberDao
import me.kafuuneko.rpclient.libs.room.dao.GroupChatMessageDao
import me.kafuuneko.rpclient.libs.room.dao.GroupChatSessionDao
import me.kafuuneko.rpclient.libs.room.dao.GroupChatSummaryDao
import me.kafuuneko.rpclient.libs.room.dao.LLMProviderDao
import me.kafuuneko.rpclient.libs.room.dao.LLMTokenUsageDao
import me.kafuuneko.rpclient.libs.room.dao.LorebookDao
import me.kafuuneko.rpclient.libs.room.dao.LorebookEntryDao
import me.kafuuneko.rpclient.libs.room.dao.MessageImageDao
import me.kafuuneko.rpclient.libs.room.dao.PageBackgroundDao
import me.kafuuneko.rpclient.libs.room.dao.PromptPresetDao
import me.kafuuneko.rpclient.libs.room.dao.RegexScriptDao
import me.kafuuneko.rpclient.libs.room.dao.SearchSourceDao
import me.kafuuneko.rpclient.libs.room.dao.StoryChapterDao
import me.kafuuneko.rpclient.libs.room.dao.StoryCharacterDao
import me.kafuuneko.rpclient.libs.room.dao.StoryDao
import me.kafuuneko.rpclient.libs.room.dao.StoryLorebookEntryDao
import me.kafuuneko.rpclient.libs.room.dao.StoryVolumeDao
import me.kafuuneko.rpclient.libs.room.dao.UserPersonaDao
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.CharacterLLMProviderAssociation
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.FileEntity
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMember
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSummary
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.LLMTokenUsageRecord
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.entity.MessageImageEntity
import me.kafuuneko.rpclient.libs.room.entity.PageBackgroundEntity
import me.kafuuneko.rpclient.libs.room.entity.PromptPreset
import me.kafuuneko.rpclient.libs.room.entity.PromptPresetEntry
import me.kafuuneko.rpclient.libs.room.entity.RegexCharacterAuthorization
import me.kafuuneko.rpclient.libs.room.entity.RegexScriptEntity
import me.kafuuneko.rpclient.libs.room.entity.SearchChunk
import me.kafuuneko.rpclient.libs.room.entity.SearchChunkFts
import me.kafuuneko.rpclient.libs.room.entity.SearchProgress
import me.kafuuneko.rpclient.libs.room.entity.SearchSource
import me.kafuuneko.rpclient.libs.room.entity.Story
import me.kafuuneko.rpclient.libs.room.entity.StoryChapter
import me.kafuuneko.rpclient.libs.room.entity.StoryCharacter
import me.kafuuneko.rpclient.libs.room.entity.StoryLorebookEntry
import me.kafuuneko.rpclient.libs.room.entity.StoryVolume
import me.kafuuneko.rpclient.libs.room.entity.UserPersona
import me.kafuuneko.rpclient.libs.room.entity.UserPersonaSettings
import me.kafuuneko.rpclient.libs.room.migration.AppDatabaseAutoMigration1To2Spec
import me.kafuuneko.rpclient.libs.room.migration.AppDatabaseAutoMigration2To3Spec
import me.kafuuneko.rpclient.libs.room.migration.AppDatabaseAutoMigration3To4Spec

/** RPClient 的 Room 数据库入口，集中声明实体、类型转换器和 DAO。 */
@Database(
    entities = [
        AgentRun::class,
        AgentStep::class,
        ToolInvocation::class,
        ConversationToolPolicy::class,
        SceneState::class,
        SceneStateRevision::class,
        CharacterMemory::class,
        CharacterMemoryRevision::class,
        CharacterMemorySource::class,
        ChatMemory::class,
        ChatMemoryRevision::class,
        ChatMemorySource::class,
        GroupChatMemory::class,
        GroupChatMemoryRevision::class,
        GroupChatMemorySource::class,
        UserPersona::class,
        UserPersonaSettings::class,
        PromptPreset::class,
        PromptPresetEntry::class,
        SearchSource::class,
        SearchChunk::class,
        SearchChunkFts::class,
        SearchProgress::class,
        PageBackgroundEntity::class,
        Character::class,
        CharacterLLMProviderAssociation::class,
        Lorebook::class,
        LorebookEntry::class,
        ChatSession::class,
        ChatMessage::class,
        LLMProvider::class,
        FileEntity::class,
        GroupChatSession::class,
        GroupChatMember::class,
        GroupChatMessage::class,
        GroupChatSummary::class,
        RegexScriptEntity::class,
        RegexCharacterAuthorization::class,
        Story::class,
        StoryVolume::class,
        StoryChapter::class,
        StoryCharacter::class,
        StoryLorebookEntry::class,
        LLMTokenUsageRecord::class,
        MessageImageEntity::class
    ],
    version = 7,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = AppDatabaseAutoMigration1To2Spec::class),
        AutoMigration(from = 2, to = 3, spec = AppDatabaseAutoMigration2To3Spec::class),
        AutoMigration(from = 3, to = 4, spec = AppDatabaseAutoMigration3To4Spec::class),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 6, to = 7, spec = MemorySchemaMigration::class),
    ],
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    /** Agent 配置、运行、步骤与调用元数据。 */
    abstract fun getAgentRunDao(): AgentRunDao

    /** 剧情当前状态与可回滚修订。 */
    abstract fun getSceneStateDao(): SceneStateDao

    /** GroupChat 记忆及证据的类型化数据入口。 */
    abstract fun getGroupChatMemoryDao(): GroupChatMemoryDao

    /** Chat 记忆及证据的类型化数据入口。 */
    abstract fun getChatMemoryDao(): ChatMemoryDao

    /** Character 记忆及证据的类型化数据入口。 */
    abstract fun getCharacterMemoryDao(): CharacterMemoryDao

    /** 用户人设及默认选择。 */
    abstract fun getUserPersonaDao(): UserPersonaDao

    /** 提供提示词模板与当前选择的数据访问入口。 */
    abstract fun getPromptPresetDao(): PromptPresetDao

    /** 提供独立内容搜索的数据访问入口。 */
    abstract fun getContentSearchDao(): ContentSearchDao

    /** 提供原始业务数据的搜索投影，普通扫描不依赖 FTS 派生表。 */
    abstract fun getSearchSourceDao(): SearchSourceDao
    abstract fun getPageBackgroundDao(): PageBackgroundDao
    abstract fun getCharacterDao(): CharacterDao
    abstract fun getCharacterLLMProviderAssociationDao(): CharacterLLMProviderAssociationDao
    abstract fun getLorebookDao(): LorebookDao
    abstract fun getLorebookEntryDao(): LorebookEntryDao
    abstract fun getChatSessionDao(): ChatSessionDao
    abstract fun getChatMessageDao(): ChatMessageDao
    abstract fun getLLMProviderDao(): LLMProviderDao
    abstract fun getFileDao(): FileDao
    abstract fun getGroupChatSessionDao(): GroupChatSessionDao
    abstract fun getGroupChatMemberDao(): GroupChatMemberDao
    abstract fun getGroupChatMessageDao(): GroupChatMessageDao
    abstract fun getGroupChatSummaryDao(): GroupChatSummaryDao
    abstract fun getRegexScriptDao(): RegexScriptDao
    abstract fun getStoryDao(): StoryDao
    abstract fun getStoryVolumeDao(): StoryVolumeDao
    abstract fun getStoryChapterDao(): StoryChapterDao
    abstract fun getStoryCharacterDao(): StoryCharacterDao
    abstract fun getStoryLorebookEntryDao(): StoryLorebookEntryDao
    abstract fun getLLMTokenUsageDao(): LLMTokenUsageDao
    abstract fun getMessageImageDao(): MessageImageDao

}
