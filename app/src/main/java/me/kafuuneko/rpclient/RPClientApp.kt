package me.kafuuneko.rpclient

import android.app.Application
import com.chibatching.kotpref.Kotpref
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
}