package me.kafuuneko.rpclient

import android.app.Application
import androidx.room.Room
import com.chibatching.kotpref.Kotpref
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

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
        Room.databaseBuilder(get(), AppDatabase::class.java, "primary.sqlite")
            .allowMainThreadQueries()
            .build()
    }

    singleOf(::CharacterRepository)
    singleOf(::LorebookRepository)

}