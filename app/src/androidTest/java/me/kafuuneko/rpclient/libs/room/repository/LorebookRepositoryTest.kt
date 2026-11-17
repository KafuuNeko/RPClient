package me.kafuuneko.rpclient.libs.room.repository

import android.net.Uri
import java.io.File
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.feature.worldbookedit.model.WorldBookEditForm
import me.kafuuneko.rpclient.libs.character.LorebookArchiveLimitException
import me.kafuuneko.rpclient.libs.character.LorebookCodec
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        repository = LorebookRepository(mAppDatabase = database, mGson = Gson(), mContext = context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** 历史大 JSON 能从文档入口重导入；同一本书超过 ZIP 成员限制时保留原目标文件。 */
    @Test
    fun largeStandaloneExportRoundTripsAndOversizedZipLeavesDestinationIntact() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("worldbook-roundtrip-", ".json", context.cacheDir)
        try {
            val content = "A".repeat(65_536)
            val draft =
                LorebookCodec(Gson()).parseLorebook("""{"name":"Large","entries":{"0":{"content":"$content"}}}""")
            val id =
                repository.saveImport(draft.copy(entries = List(132) { draft.entries.single() }))
            val uri = Uri.fromFile(file)
            repository.exportToUri(id, uri)
            assertTrue(file.length() > 8L * 1024 * 1024)
            val restored = repository.readImportItemsFromUri(uri).drafts.single()
            assertEquals(132, restored.entries.size)
            assertTrue(restored.entries.all { it.content == content })
            // 导出失败必须发生在打开并截断目标之前。
            file.writeText("original")
            val error =
                runCatching { repository.exportArchiveToUri(listOf(id), uri) }.exceptionOrNull()
            assertTrue(error is LorebookArchiveLimitException)
            assertEquals("original", file.readText())
        } finally {
            file.delete()
        }
        Unit
    }

    @Test
    fun deletingLorebookClearsCharacterAssociations() = runBlocking {
        val lorebookId = repository.createLorebook("Linked lorebook")
        val characterId = database.getCharacterDao().insertOrReplace(
            data = Character(
                name = "Character",
                avatar = "",
                characterTags = "[]",
                description = "",
                personality = "",
                scenario = "",
                firstMessages = "",
                examplesOfDialogue = "",
                postHistoryInstructions = "",
                characterLorebookId = lorebookId,
            ),
        )

        repository.deleteLorebook(lorebookId)

        assertEquals(
            0L,
            database.getCharacterDao().getCharacterById(characterId)?.characterLorebookId,
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
            extensionsJson = """{"custom":true}""",
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
