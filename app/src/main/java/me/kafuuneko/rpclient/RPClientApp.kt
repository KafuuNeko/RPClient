package me.kafuuneko.rpclient

import android.app.Application
import androidx.room.Room
import com.chibatching.kotpref.Kotpref
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.chat.ChatArchiveCodec
import me.kafuuneko.rpclient.libs.chat.ChatArchiveRepository
import me.kafuuneko.rpclient.libs.character.CharacterCardRepository
import me.kafuuneko.rpclient.libs.core.releaseObsoletePersistedUriPermissions
import me.kafuuneko.rpclient.libs.groupchat.GroupChatOutputSanitizer
import me.kafuuneko.rpclient.libs.groupchat.GroupChatPromptBuilder
import me.kafuuneko.rpclient.libs.groupchat.GroupChatGreetingPlanner
import me.kafuuneko.rpclient.libs.groupchat.GroupChatSpeakerSelector
import me.kafuuneko.rpclient.libs.groupchat.GroupChatSummaryPromptBuilder
import me.kafuuneko.rpclient.libs.llm.LLMClientFactory
import me.kafuuneko.rpclient.libs.llm.LLMProviderSelectionResolver
import me.kafuuneko.rpclient.libs.llm.catalog.LLMModelCatalogClientFactory
import me.kafuuneko.rpclient.libs.llm.catalog.LLMModelCatalogRepository
import me.kafuuneko.rpclient.libs.story.StoryArchiveCodec
import me.kafuuneko.rpclient.libs.story.StoryArchiveRepository
import me.kafuuneko.rpclient.libs.story.StoryCharacterActivator
import me.kafuuneko.rpclient.libs.story.StoryContextSelector
import me.kafuuneko.rpclient.libs.story.StoryOutputSanitizer
import me.kafuuneko.rpclient.libs.story.StoryPromptBuilder
import me.kafuuneko.rpclient.libs.story.StorySummaryPromptBuilder
import me.kafuuneko.rpclient.libs.theme.AppThemeManager
import me.kafuuneko.rpclient.libs.prompt.ChatPromptBuilder
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehaviorProvider
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
import me.kafuuneko.rpclient.libs.regex.RegexMessageProcessor
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.RequestLogDatabase
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRequestLogRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMTokenUsageRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.libs.room.repository.StoryRepository
import me.kafuuneko.rpclient.libs.upgrade.AndroidAppVersionCodeProvider
import me.kafuuneko.rpclient.libs.upgrade.AppModelUpgradeVersionStore
import me.kafuuneko.rpclient.libs.upgrade.AppUpgradeManager
import me.kafuuneko.rpclient.libs.upgrade.steps.Upgrade20260103
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
        val koinApplication = startKoin {
            androidContext(this@RPClientApp)
            modules(appModules)
        }
        runBlocking(Dispatchers.IO) {
            contentResolver.releaseObsoletePersistedUriPermissions()
            koinApplication.koin.get<AppUpgradeManager>().upgrade()
        }
    }
}

/**
 * 应用级单例依赖。
 *
 * 业务对象保持无 Activity 引用；页面 ViewModel 通过 KoinComponent 按需获取这些实例。
 */
internal val appModules = module {
    single {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    single { Gson() }

    single {
        LLMClientFactory(
            mOkHttpClient = get(),
            mLLMRequestLogRepository = get(),
            mLLMTokenUsageRepository = get(),
            mPromptTokenizerRegistry = get()
        )
    }
    singleOf(::LLMProviderSelectionResolver)
    singleOf(::AppThemeManager)
    singleOf(::LLMModelCatalogClientFactory)
    singleOf(::LLMModelCatalogRepository)
    singleOf(::FormattedHistoryBuilder)
    singleOf(::PromptMacroResolver)
    singleOf(::WorldBookActivator)
    singleOf(::PromptTokenizerRegistry)
    single { PromptRequestFinalizer(get<PromptTokenizerRegistry>()) }
    single<ExampleDialogueBehaviorProvider> {
        ExampleDialogueBehaviorProvider {
            ExampleDialogueBehavior.fromPersistedValue(
                runCatching { AppModel.exampleDialogueBehavior }
                    .getOrDefault(ExampleDialogueBehavior.default.persistedValue)
            )
        }
    }
    singleOf(::ChatPromptBuilder)
    singleOf(::SummaryPromptBuilder)
    singleOf(::ChatArchiveCodec)
    singleOf(::ChatArchiveRepository)
    singleOf(::GroupChatPromptBuilder)
    singleOf(::GroupChatGreetingPlanner)
    singleOf(::GroupChatSpeakerSelector)
    singleOf(::GroupChatSummaryPromptBuilder)
    singleOf(::GroupChatOutputSanitizer)
    singleOf(::StoryCharacterActivator)
    singleOf(::StoryContextSelector)
    singleOf(::StoryPromptBuilder)
    singleOf(::StorySummaryPromptBuilder)
    singleOf(::StoryOutputSanitizer)
    singleOf(::StoryArchiveCodec)
    singleOf(::StoryArchiveRepository)
    singleOf(::RegexScriptCodec)
    singleOf(::RegexScriptEngine)
    singleOf(::RegexScriptRuntime)
    singleOf(::RegexMessageProcessor)

    // 业务数据升级
    singleOf(::AndroidAppVersionCodeProvider)
    singleOf(::AppModelUpgradeVersionStore)
    singleOf(::Upgrade20260103)
    single {
        AppUpgradeManager(
            versionCodeProvider = get<AndroidAppVersionCodeProvider>(),
            versionStore = get<AppModelUpgradeVersionStore>(),
            upgrades = listOf(get<Upgrade20260103>())
        )
    }

    single {
        Room.databaseBuilder(get(), AppDatabase::class.java, "primary.sqlite")
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .build()
    }

    single {
        Room.databaseBuilder(get(), RequestLogDatabase::class.java, "request_logs.sqlite")
            .build()
    }

    singleOf(::CharacterRepository)
    singleOf(::LorebookRepository)
    singleOf(::ChatRepository)
    singleOf(::LLMRepository)
    singleOf(::LLMRequestLogRepository)
    singleOf(::LLMTokenUsageRepository)
    singleOf(::FileRepository)
    singleOf(::CharacterCardRepository)
    singleOf(::GroupChatRepository)
    singleOf(::RegexScriptRepository)
    singleOf(::StoryRepository)

}
