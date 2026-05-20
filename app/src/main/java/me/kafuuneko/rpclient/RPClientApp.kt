package me.kafuuneko.rpclient

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.chibatching.kotpref.Kotpref
import me.kafuuneko.rpclient.libs.llm.LLMClientFactory
import me.kafuuneko.rpclient.libs.character.CharacterCardRepository
import me.kafuuneko.rpclient.libs.prompt.ChatPromptBuilder
import me.kafuuneko.rpclient.libs.prompt.FormattedHistoryBuilder
import me.kafuuneko.rpclient.libs.prompt.PromptMacroResolver
import me.kafuuneko.rpclient.libs.prompt.SummaryPromptBuilder
import me.kafuuneko.rpclient.libs.prompt.WorldBookActivator
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRequestLogRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import com.google.gson.Gson
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

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
    singleOf(::ChatPromptBuilder)
    singleOf(::SummaryPromptBuilder)

    single {
        Room.databaseBuilder(get(), AppDatabase::class.java, "primary.sqlite")
            .allowMainThreadQueries()
            .fallbackToDestructiveMigration(true)
            .build()
    }

    singleOf(::CharacterRepository)
    singleOf(::LorebookRepository)
    singleOf(::ChatRepository)
    singleOf(::LLMRepository)
    singleOf(::LLMRequestLogRepository)
    singleOf(::FileRepository)
    singleOf(::CharacterCardRepository)

}

