package me.kafuuneko.rpclient

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.chibatching.kotpref.Kotpref
import com.google.gson.Gson
import me.kafuuneko.rpclient.libs.character.CharacterCardRepository
import me.kafuuneko.rpclient.libs.groupchat.GroupChatOutputSanitizer
import me.kafuuneko.rpclient.libs.groupchat.GroupChatPromptBuilder
import me.kafuuneko.rpclient.libs.groupchat.GroupChatGreetingPlanner
import me.kafuuneko.rpclient.libs.groupchat.GroupChatSpeakerSelector
import me.kafuuneko.rpclient.libs.groupchat.GroupChatSummaryPromptBuilder
import me.kafuuneko.rpclient.libs.llm.LLMClientFactory
import me.kafuuneko.rpclient.libs.prompt.ChatPromptBuilder
import me.kafuuneko.rpclient.libs.prompt.FormattedHistoryBuilder
import me.kafuuneko.rpclient.libs.prompt.PromptMacroResolver
import me.kafuuneko.rpclient.libs.prompt.PromptRequestFinalizer
import me.kafuuneko.rpclient.libs.prompt.PromptTokenizerRegistry
import me.kafuuneko.rpclient.libs.prompt.SummaryPromptBuilder
import me.kafuuneko.rpclient.libs.prompt.WorldBookActivator
import me.kafuuneko.rpclient.libs.regex.RegexScriptCodec
import me.kafuuneko.rpclient.libs.regex.RegexScriptEngine
import me.kafuuneko.rpclient.libs.regex.RegexScriptRepository
import me.kafuuneko.rpclient.libs.regex.RegexScriptRuntime
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRequestLogRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/** 应用进程入口，初始化偏好存储与全局 Koin 依赖图。 */
class RPClientApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Kotpref.init(this)
        startKoin {
            androidContext(this@RPClientApp)
            modules(appModules)
        }
    }
}

/**
 * 应用级单例依赖。
 *
 * 业务对象保持无 Activity 引用；页面 ViewModel 通过 KoinComponent 按需获取这些实例。
 */
private val appModules = module {
    singleOf(::AppLibs)

    single {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    single { Gson() }

    singleOf(::LLMClientFactory)
    singleOf(::FormattedHistoryBuilder)
    singleOf(::PromptMacroResolver)
    singleOf(::WorldBookActivator)
    singleOf(::PromptTokenizerRegistry)
    single { PromptRequestFinalizer(get<PromptTokenizerRegistry>()) }
    singleOf(::ChatPromptBuilder)
    singleOf(::SummaryPromptBuilder)
    singleOf(::GroupChatPromptBuilder)
    singleOf(::GroupChatGreetingPlanner)
    singleOf(::GroupChatSpeakerSelector)
    singleOf(::GroupChatSummaryPromptBuilder)
    singleOf(::GroupChatOutputSanitizer)
    singleOf(::RegexScriptCodec)
    singleOf(::RegexScriptEngine)
    singleOf(::RegexScriptRuntime)

    single {
        Room.databaseBuilder(get(), AppDatabase::class.java, "primary.sqlite")
            .allowMainThreadQueries()
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration(true)
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .build()
    }

    singleOf(::CharacterRepository)
    singleOf(::LorebookRepository)
    singleOf(::ChatRepository)
    singleOf(::LLMRepository)
    singleOf(::LLMRequestLogRepository)
    singleOf(::FileRepository)
    singleOf(::CharacterCardRepository)
    singleOf(::GroupChatRepository)
    singleOf(::RegexScriptRepository)

}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE llm_providers ADD COLUMN promptCacheMode INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            "ALTER TABLE llm_providers ADD COLUMN promptCacheTtl INTEGER NOT NULL DEFAULT 0"
        )
    }
}
