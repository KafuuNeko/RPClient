package me.kafuuneko.rpclient.libs.room.repository

import me.kafuuneko.rpclient.libs.room.repository.MessageImageRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chibatching.kotpref.Kotpref
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.LLMClientFactory
import me.kafuuneko.rpclient.libs.llm.LLMProviderSelectionResolver
import me.kafuuneko.rpclient.libs.llm.UnavailableLLMProviderSelectionException
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.regex.RegexScriptCodec
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.RequestLogDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LLMProviderDeletionTest {
    private lateinit var mDatabase: AppDatabase
    private lateinit var mRequestLogDatabase: RequestLogDatabase
    private lateinit var mRepository: LLMRepository
    private lateinit var mCharacterRepository: CharacterRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        Kotpref.init(context)
        AppModel.currentLLMProvider = 0L
        AppModel.summaryLLMProvider = 0L
        AppModel.llmDefaultProvidersInitialized = false
        mDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        mRequestLogDatabase = Room.inMemoryDatabaseBuilder(
            context,
            RequestLogDatabase::class.java
        ).allowMainThreadQueries().build()
        mRepository = LLMRepository(
            mDatabase,
            LLMClientFactory(
                OkHttpClient(),
                LLMRequestLogRepository(mRequestLogDatabase)
            )
        )
        val gson = Gson()
        mCharacterRepository = CharacterRepository(
            mDatabase,
            gson,
            RegexScriptCodec(gson),
            MessageImageRepository(mDatabase, FileRepository(context, mDatabase))
        )
    }

    @After
    fun tearDown() {
        mDatabase.close()
        mRequestLogDatabase.close()
        AppModel.currentLLMProvider = 0L
        AppModel.summaryLLMProvider = 0L
        AppModel.llmDefaultProvidersInitialized = false
    }

    @Test
    fun deleteProviderClearsCharacterAndSummaryAssociations() = runBlocking {
        val providerId = mRepository.saveProvider(provider("Provider"))
        val characterId = mCharacterRepository.saveCharacterWithLLMProvider(
            Character(
                name = "Character",
                avatar = "",
                characterTags = "[]",
                description = "",
                personality = "",
                scenario = "",
                firstMessages = "",
                examplesOfDialogue = "",
                postHistoryInstructions = ""
            ),
            llmProviderId = providerId
        )
        AppModel.summaryLLMProvider = providerId

        mRepository.deleteProvider(providerId)

        assertNull(mRepository.getProviderById(providerId))
        assertNotNull(mCharacterRepository.getCharacterById(characterId))
        assertEquals(0L, mCharacterRepository.getLLMProviderId(characterId))
        assertEquals(0L, AppModel.summaryLLMProvider)
    }

    @Test
    fun explicitCharacterAndSummarySelectionsDoNotSilentlyFallback() = runBlocking {
        val globalProviderId = mRepository.saveProvider(provider("Global"))
        val explicitProviderId = mRepository.saveProvider(provider("Explicit"))
        val characterId = mCharacterRepository.saveCharacterWithLLMProvider(
            Character(
                name = "Character",
                avatar = "",
                characterTags = "[]",
                description = "",
                personality = "",
                scenario = "",
                firstMessages = "",
                examplesOfDialogue = "",
                postHistoryInstructions = ""
            ),
            llmProviderId = explicitProviderId
        )
        val character = checkNotNull(mCharacterRepository.getCharacterById(characterId))
        val resolver = LLMProviderSelectionResolver(mRepository, mCharacterRepository)
        mRepository.updateCurrentProvider(globalProviderId)
        AppModel.summaryLLMProvider = explicitProviderId

        assertEquals(explicitProviderId, resolver.requireCharacterProvider(character).id)
        assertEquals(explicitProviderId, resolver.requireSummaryProvider().id)

        mRepository.updateProviderEnabled(explicitProviderId, false)

        assertEquals(
            true,
            runCatching { resolver.requireCharacterProvider(character) }
                .exceptionOrNull() is UnavailableLLMProviderSelectionException
        )
        assertEquals(
            true,
            runCatching { resolver.requireSummaryProvider() }
                .exceptionOrNull() is UnavailableLLMProviderSelectionException
        )
    }

    @Test
    fun deletingAllInitializedTemplatesDoesNotRecreateThem() = runBlocking {
        val providers = mRepository.getAllProviders()

        providers.forEach { mRepository.deleteProvider(it.id) }

        assertEquals(emptyList<LLMProvider>(), mRepository.getAllProviders())
    }

    private fun provider(name: String) = LLMProvider(
        name = name,
        providerType = LLMProviderType.Custom,
        protocol = LLMProviderProtocol.OpenAICompatible,
        baseUrl = "https://example.invalid",
        model = "model"
    )
}
