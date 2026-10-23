package me.kafuuneko.rpclient.libs.upgrade.steps

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chibatching.kotpref.Kotpref
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.regex.RegexPlacement
import me.kafuuneko.rpclient.libs.regex.RegexScript
import me.kafuuneko.rpclient.libs.regex.RegexScriptCodec
import me.kafuuneko.rpclient.libs.regex.toDomain
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.upgrade.AppModelUpgradeVersionStore
import me.kafuuneko.rpclient.libs.upgrade.AppUpgradeManager
import me.kafuuneko.rpclient.libs.upgrade.AppVersionCodeProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Upgrade20260103Test {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var codec: RegexScriptCodec
    private var previousLegacyPreferences: Map<String, Any?> = emptyMap<String, Any?>()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        Kotpref.init(context)
        previousLegacyPreferences = AppModel.preferences.all
            .filterKeys { it in ManagedPreferenceKeys }
            .mapValues { it.value }
        clearManagedPreferences()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        codec = RegexScriptCodec(Gson())
    }

    @After
    fun tearDown() {
        database.close()
        clearManagedPreferences()
        AppModel.preferences.edit().apply {
            previousLegacyPreferences.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                }
            }
        }.commit()
    }

    @Test
    fun upgrade_migratesEveryLegacySourceOnceAndSupportsCrashSafeRetry() = runBlocking {
        val characterId = database.getCharacterDao().insertOrReplace(
            data = character(
                extensionsJson = """
                    {
                      "vendor":{"kept":true},
                      "regex_scripts":[{
                        "id":"character-script",
                        "scriptName":"Character",
                        "findRegex":"x",
                        "replaceString":"y",
                        "placement":[2]
                      }]
                    }
                """.trimIndent(),
            ),
        )
        writeLegacyPreferences(characterId)
        val upgradeManager = manager()

        upgradeManager.upgrade(CurrentVersionCode)

        val regexDao = database.getRegexScriptDao()
        val global = regexDao.getGlobalScripts().map { regexScriptEntity -> regexScriptEntity.toDomain() }
        assertEquals(listOf("global-script", "preset-script"), global.map { regexScript -> regexScript.id })
        assertFalse(global[0].disabled)
        assertTrue(global[1].disabled)
        assertEquals(
            listOf("character-script"),
            regexDao.getCharacterScripts(characterId).map { regexScriptEntity -> regexScriptEntity.scriptId },
        )
        assertTrue(regexDao.isCharacterAuthorized(characterId))
        val extensions = JsonParser.parseString(
            requireNotNull(database.getCharacterDao().getCharacterById(characterId)).extensionsJson,
        ).asJsonObject
        assertTrue(extensions.has("vendor"))
        assertFalse(extensions.has("regex_scripts"))
        LegacyPreferenceKeys.forEach { key ->
            assertFalse(AppModel.preferences.contains(key))
        }
        assertEquals(CurrentVersionCode, AppModel.lastMigratedVersionCode)
        assertEquals(CurrentVersionCode, AppModel.lastCleanedUpgradeVersionCode)

        // 模拟 Room 已提交、角色扩展已清理，但版本号写入前进程退出。
        writeLegacyPreferences(characterId)
        AppModel.lastMigratedVersionCode = 0
        AppModel.lastCleanedUpgradeVersionCode = 0
        manager().upgrade(CurrentVersionCode)

        assertEquals(2, regexDao.getGlobalScripts().size)
        assertEquals(1, regexDao.getCharacterScripts(characterId).size)
        LegacyPreferenceKeys.forEach { key ->
            assertFalse(AppModel.preferences.contains(key))
        }
        assertEquals(CurrentVersionCode, AppModel.lastMigratedVersionCode)
        assertEquals(CurrentVersionCode, AppModel.lastCleanedUpgradeVersionCode)

        // 模拟版本号已写入，但旧偏好清理前进程退出。
        writeLegacyPreferences(characterId)
        AppModel.lastCleanedUpgradeVersionCode = 0
        manager().upgrade(CurrentVersionCode)

        assertEquals(2, regexDao.getGlobalScripts().size)
        LegacyPreferenceKeys.forEach { key ->
            assertFalse(AppModel.preferences.contains(key))
        }
        assertEquals(CurrentVersionCode, AppModel.lastCleanedUpgradeVersionCode)
    }

    private fun writeLegacyPreferences(characterId: Long) {
        AppModel
            .preferences.edit()
            .putString(
                "globalRegexScriptsJson",
                codec.toJson(listOf(script("global-script"))),
            )
            .putString(
                "presetRegexScriptsJson",
                codec.toJson(listOf(script("preset-script"))),
            )
            .putBoolean("presetRegexScriptsAuthorized", false)
            .putString("authorizedCharacterRegexIdsJson", "[$characterId,999999]")
            .putInt("regexScopeMigrationVersion", 1)
            .commit()
    }

    private fun clearManagedPreferences() {
        AppModel.preferences.edit().apply {
            ManagedPreferenceKeys.forEach(::remove)
        }.commit()
    }

    private fun manager(): AppUpgradeManager {
        return AppUpgradeManager(
            versionCodeProvider = AppVersionCodeProvider { CurrentVersionCode },
            versionStore = AppModelUpgradeVersionStore(),
            upgrades = listOf(Upgrade20260103(mAppDatabase = database, mGson = Gson(), mRegexCodec = codec)),
        )
    }

    private fun script(id: String) = RegexScript(
        id = id,
        scriptName = id,
        findRegex = "x",
        replaceString = "y",
        placement = listOf(RegexPlacement.AiResponse.value),
    )

    private fun character(extensionsJson: String) = Character(
        name = "Character",
        avatar = "",
        characterTags = "[]",
        description = "",
        personality = "",
        scenario = "",
        firstMessages = "",
        examplesOfDialogue = "",
        postHistoryInstructions = "",
        extensionsJson = extensionsJson,
    )

    private companion object {
        const val CurrentVersionCode = 20260103
        val LegacyPreferenceKeys = setOf(
            "globalRegexScriptsJson",
            "presetRegexScriptsJson",
            "presetRegexScriptsAuthorized",
            "authorizedCharacterRegexIdsJson",
            "regexScopeMigrationVersion",
        )
        val ManagedPreferenceKeys = LegacyPreferenceKeys + setOf(
            "lastMigratedVersionCode",
            "lastCleanedUpgradeVersionCode",
        )
    }
}
