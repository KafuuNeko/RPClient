package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.feature.worldbookedit.model.WorldBookEditForm
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LorebookRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: LorebookRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LorebookRepository(database, Gson(), context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deletingLorebookClearsCharacterAssociations() = runBlocking {
        val lorebookId = repository.createLorebook("Linked lorebook")
        val characterId = database.getCharacterDao().insertOrReplace(
            Character(
                name = "Character",
                avatar = "",
                characterTags = "[]",
                description = "",
                personality = "",
                scenario = "",
                firstMessages = "",
                examplesOfDialogue = "",
                postHistoryInstructions = "",
                characterLorebookId = lorebookId
            )
        )

        repository.deleteLorebook(lorebookId)

        assertEquals(
            0L,
            database.getCharacterDao().getCharacterById(characterId)?.characterLorebookId
        )
    }

    @Test
    fun renamingLorebookThroughEditFormPreservesStoredFields() = runBlocking {
        val original = Lorebook(
            name = "Imported",
            description = "Description",
            scanDepth = 8,
            tokenBudget = 320,
            recursiveScanning = true,
            extensionsJson = """{"custom":true}"""
        )
        val lorebookId = repository.saveLorebook(original)
        val stored = repository.getLorebookById(lorebookId)
            ?: error("Lorebook should exist")
        val edited = WorldBookEditForm.from(stored, emptyList())
            .copy(name = "Renamed")
            .toLorebook()

        repository.saveLorebook(edited)

        assertEquals(stored.copy(name = "Renamed"), repository.getLorebookById(lorebookId))
    }
}
