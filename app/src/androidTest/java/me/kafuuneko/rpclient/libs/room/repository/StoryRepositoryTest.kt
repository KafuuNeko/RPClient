package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.chat.ChatCharacterMatcher
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.entity.StoryCharacter
import me.kafuuneko.rpclient.libs.story.ArchivedChapter
import me.kafuuneko.rpclient.libs.story.StoryArchiveCodec
import me.kafuuneko.rpclient.libs.story.StoryArchiveRepository
import me.kafuuneko.rpclient.libs.story.StoryCharacterHint
import me.kafuuneko.rpclient.libs.story.StoryImportDraft
import me.kafuuneko.rpclient.libs.story.StoryImportType
import me.kafuuneko.rpclient.libs.story.StoryLorebookHint
import me.kafuuneko.rpclient.libs.story.storyTextHash
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoryRepositoryTest {
    private lateinit var mDatabase: AppDatabase
    private lateinit var mRepository: StoryRepository

    @Before
    fun setup() {
        mDatabase = Room.inMemoryDatabaseBuilder(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            klass = AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        mRepository = StoryRepository(mDatabase)
    }

    @After
    fun tearDown() {
        mDatabase.close()
    }

    @Test
    fun configuration_persistsPrimaryPersonaAndLorebookEntryAndCascadesRelations() = runBlocking {
        val characterId = mDatabase.getCharacterDao().insertOrReplace(testCharacter())
        val entryId = insertLorebookEntry()
        val storyId = mRepository.createStoryWithConfiguration(
            title = " Configured story ",
            includeUserPersona = true,
            lorebookSelections = listOf(StoryLorebookEntrySelection(entryId)),
            characterSelections = listOf(
                element = StoryCharacterSelection(
                    characterId = characterId,
                    activationMode = StoryCharacter.ACTIVATION_PRIMARY,
                ),
            ),
            createTime = 10L,
        )

        val story = requireNotNull(mRepository.getStory(storyId))
        assertEquals("Configured story", story.title)
        assertTrue(story.includeUserPersona)
        assertEquals("Chapter 1", mRepository.getStoryEditorData(storyId)?.currentChapter?.title)
        val character = mRepository.getStoryCharacterCandidates(storyId).single()
        assertEquals(StoryCharacter.ACTIVATION_PRIMARY, character.relation.activationMode)
        val lorebook = mRepository.getStoryLorebookEntryCandidates(storyId).single()
        assertEquals(entryId, lorebook.entry.id)

        mRepository.deleteStory(storyId)
        assertNull(mRepository.getStory(storyId))
        assertEquals("Alice", mDatabase.getCharacterDao().getCharacterById(characterId)?.name)
        assertEquals(entryId, mDatabase.getLorebookEntryDao().getEntryById(entryId)?.id)
    }

    @Test
    fun configuration_rejectsMultiplePrimaryCharacters() = runBlocking {
        val firstId = mDatabase.getCharacterDao().insertOrReplace(testCharacter())
        val secondId = mDatabase.getCharacterDao().insertOrReplace(testCharacter().copy(name = "Bob"))
        val multiplePrimary = runCatching {
            mRepository.createStoryWithConfiguration(
                title = "Invalid",
                lorebookSelections = emptyList(),
                characterSelections = listOf(
                    StoryCharacterSelection(firstId, StoryCharacter.ACTIVATION_PRIMARY),
                    StoryCharacterSelection(secondId, StoryCharacter.ACTIVATION_PRIMARY),
                ),
            )
        }
        assertTrue(multiplePrimary.isFailure)
        assertTrue(mRepository.getStoryOverviews().isEmpty())
    }

    @Test
    fun configuration_canAddANewUnboundLorebookEntry() = runBlocking {
        val entryId = insertLorebookEntry()
        val storyId = mRepository.createStory("Story")

        mRepository.updateStoryConfiguration(
            storyId = storyId,
            memory = "",
            summary = "",
            authorNote = "",
            lorebookSelections = listOf(StoryLorebookEntrySelection(entryId)),
            characterSelections = emptyList(),
        )

        assertEquals(
            entryId,
            mRepository.getStoryLorebookRuntimeStates(storyId).single().lorebookEntryId,
        )
    }

    @Test
    fun configuration_supportsMoreThanSqliteInQueryLimitLorebookEntries() = runBlocking {
        val lorebookId = mDatabase.getLorebookDao().insertOrReplace(Lorebook(name = "Large world"))
        val entries = (0 until 1_000).map { index ->
            LorebookEntry(
                lorebookId = lorebookId,
                name = "Entry $index",
                keywords = "[]",
                secondaryKeywords = "[]",
                order = index,
                depth = 0,
                category = "[]",
                content = "Content $index",
            )
        }
        val entryIds = mDatabase.getLorebookEntryDao().insertOrReplaceAll(entries)

        val storyId = mRepository.createStoryWithConfiguration(
            title = "Large story",
            lorebookSelections = entryIds.map(::StoryLorebookEntrySelection),
            characterSelections = emptyList(),
        )

        assertEquals(1_000, mRepository.getStoryLorebookEntryCandidates(storyId).size)
    }

    @Test
    fun generatedEdit_updatesAndRestoresEntryRuntimeStateAtomically() = runBlocking {
        val entryId = insertLorebookEntry()
        val storyId = mRepository.createStoryWithConfiguration(
            title = "Story",
            lorebookSelections = listOf(StoryLorebookEntrySelection(entryId)),
            characterSelections = emptyList(),
            createTime = 10L,
        )
        val chapter = requireNotNull(mRepository.getStoryEditorData(storyId)).currentChapter
        val saved = requireNotNull(
            value = mRepository.updateChapterDraft(
                storyId = storyId,
                chapterId = chapter.id,
                expectedChapterRevision = 0L,
                content = "Before",
                continuationGuidance = "",
                latestTime = 11L,
            ),
        )
        val previousStates = mRepository.getStoryLorebookRuntimeStates(storyId)
        val nextStates = previousStates.map { runtimeState ->
            runtimeState.copy(activatedAtStep = 1, stickyUntilStep = 3, stateSignature = "active")
        }
        val applied = requireNotNull(
            value = mRepository.applyGeneratedEdit(
                edit = StoryGeneratedEdit(
                    storyId = storyId,
                    chapterId = chapter.id,
                    baseStoryRevision = saved.storyRevision,
                    baseChapterRevision = saved.chapterRevision,
                    start = 6,
                    end = 6,
                    originalTextHash = storyTextHash(""),
                    result = " after",
                    nextWorldInfoStates = nextStates,
                ),
            ),
        )

        assertEquals("Before after", applied.content)
        assertEquals(nextStates, mRepository.getStoryLorebookRuntimeStates(storyId))
        assertNull(
            mRepository.applyGeneratedEdit(
                edit = StoryGeneratedEdit(
                    storyId = storyId,
                    chapterId = chapter.id,
                    baseStoryRevision = applied.storyRevision,
                    baseChapterRevision = applied.chapterRevision,
                    start = applied.content.length,
                    end = applied.content.length,
                    originalTextHash = storyTextHash(""),
                    result = " stale",
                    nextWorldInfoStates = nextStates.map { runtimeState ->
                        runtimeState.copy(lorebookEntryId = Long.MAX_VALUE)
                    },
                ),
            ),
        )

        val reverted = requireNotNull(
            value = mRepository.revertGeneratedEdit(
                storyId = storyId,
                chapterId = chapter.id,
                expectedStoryRevision = applied.storyRevision,
                expectedChapterRevision = applied.chapterRevision,
                start = 6,
                insertedText = " after",
                replacedText = "",
                previousWorldInfoStates = previousStates,
                previousWorldInfoGenerationStep = 0,
            ),
        )
        assertEquals("Before", reverted.content)
        assertEquals(previousStates, mRepository.getStoryLorebookRuntimeStates(storyId))
    }

    @Test
    fun updatingConfiguration_retainsRuntimeStateOnlyForStillSelectedEntries() = runBlocking {
        val firstId = mDatabase.getCharacterDao().insertOrReplace(testCharacter())
        val secondId = mDatabase.getCharacterDao().insertOrReplace(testCharacter().copy(name = "Bob"))
        val retainedEntryId = insertLorebookEntry("Retained")
        val removedEntryId = insertLorebookEntry("Removed")
        val storyId = mRepository.createStoryWithConfiguration(
            title = "Story",
            lorebookSelections = listOf(
                StoryLorebookEntrySelection(retainedEntryId),
                StoryLorebookEntrySelection(removedEntryId),
            ),
            characterSelections = listOf(
                StoryCharacterSelection(firstId, StoryCharacter.ACTIVATION_PRIMARY),
                StoryCharacterSelection(secondId, StoryCharacter.ACTIVATION_AUTO),
            ),
        )
        val activeStates = mRepository.getStoryLorebookRuntimeStates(storyId).map { runtimeState ->
            runtimeState.copy(activatedAtStep = 1, stateSignature = "active")
        }
        val chapter = requireNotNull(mRepository.getStoryEditorData(storyId)).currentChapter
        requireNotNull(
            value = mRepository.applyGeneratedEdit(
                edit = StoryGeneratedEdit(
                    storyId = storyId,
                    chapterId = chapter.id,
                    baseStoryRevision = 0L,
                    baseChapterRevision = 0L,
                    start = 0,
                    end = 0,
                    originalTextHash = storyTextHash(""),
                    result = "AI",
                    nextWorldInfoStates = activeStates,
                ),
            ),
        )

        mRepository.updateStoryConfiguration(
            storyId = storyId,
            memory = "",
            summary = "",
            authorNote = "",
            lorebookSelections = listOf(StoryLorebookEntrySelection(retainedEntryId)),
            characterSelections = listOf(
                StoryCharacterSelection(firstId, StoryCharacter.ACTIVATION_AUTO),
                StoryCharacterSelection(secondId, StoryCharacter.ACTIVATION_PRIMARY),
            ),
        )

        val state = mRepository.getStoryLorebookRuntimeStates(storyId).single()
        assertEquals(retainedEntryId, state.lorebookEntryId)
        assertEquals(1, state.activatedAtStep)
    }

    @Test
    fun archiveImport_restoresPrimaryAndLorebookHints() = runBlocking {
        val character = testCharacter()
        mDatabase.getCharacterDao().insertOrReplace(character)
        val entryId = insertLorebookEntry()
        val entry = requireNotNull(mDatabase.getLorebookEntryDao().getEntryById(entryId))
        val gson = Gson()
        val archiveRepository = StoryArchiveRepository(
            mContext = InstrumentationRegistry.getInstrumentation().targetContext,
            mAppDatabase = mDatabase,
            mCodec = StoryArchiveCodec(gson),
        )

        val storyId = archiveRepository.saveImport(
            draft = StoryImportDraft(
                title = "Imported",
                ungroupedChapters = listOf(
                    element = ArchivedChapter(title = "正文", content = "正文", continuationGuidance = "保持悬疑氛围"),
                ),
                includeUserPersona = true,
                characterHints = listOf(
                    element = StoryCharacterHint(
                        name = character.name,
                        fingerprint = ChatCharacterMatcher.fingerprintOf(character),
                        activationMode = StoryArchiveCodec.MODE_PRIMARY,
                    ),
                ),
                lorebookHints = listOf(
                    element = StoryLorebookHint(
                        lorebookName = "World",
                        entryName = entry.name,
                        fingerprint = storyTextHash(entry.content),
                    ),
                ),
                type = StoryImportType.Archive,
            ),
            title = "Confirmed title",
        )

        assertTrue(requireNotNull(mRepository.getStory(storyId)).includeUserPersona)
        assertEquals(
            StoryCharacter.ACTIVATION_PRIMARY,
            mRepository.getStoryCharacterCandidates(storyId).single().relation.activationMode,
        )
        assertEquals(
            entryId,
            mRepository.getStoryLorebookEntryCandidates(storyId).single().entry.id,
        )
        assertEquals(
            "保持悬疑氛围",
            requireNotNull(mRepository.getStoryEditorData(storyId))
                .currentChapter
                .continuationGuidance,
        )
    }

    @Test
    fun structure_deletingVolumeKeepsChaptersAndDeletingLastChapterIsRejected() = runBlocking {
        val storyId = mRepository.createStory("Novel")
        val defaultChapter = requireNotNull(mRepository.getStoryEditorData(storyId)).currentChapter
        val volumeId = mRepository.createVolume(storyId, "Volume One")
        val first = mRepository.createChapter(storyId, volumeId, "Chapter One")
        val second = mRepository.createChapter(storyId, volumeId, "Chapter Two")

        assertTrue(mRepository.deleteVolume(storyId, volumeId))
        val afterDelete = requireNotNull(mRepository.getStoryEditorData(storyId, defaultChapter.id))
        assertTrue(afterDelete.volumes.isEmpty())
        assertEquals(
            listOf(defaultChapter.id, first, second),
            afterDelete.chapters.map { storyChapterOverview -> storyChapterOverview.id },
        )
        assertTrue(afterDelete.chapters.all { storyChapterOverview -> storyChapterOverview.volumeId == null })

        assertTrue(mRepository.deleteChapter(storyId, first) != null)
        assertTrue(mRepository.deleteChapter(storyId, second) != null)
        val deletingLast = runCatching {
            mRepository.deleteChapter(storyId, defaultChapter.id)
        }
        assertTrue(deletingLast.isFailure)
        assertEquals(1, requireNotNull(mRepository.getStoryEditorData(storyId)).chapters.size)
    }

    @Test
    fun structure_reorderAllChaptersValidatesSnapshotAndPersistsAtomically() = runBlocking {
        val storyId = mRepository.createStory("Novel")
        val first = requireNotNull(mRepository.getStoryEditorData(storyId)).currentChapter
        val volumeId = mRepository.createVolume(storyId, "Volume")
        val secondId = mRepository.createChapter(storyId, volumeId, "Second")
        val otherStoryId = mRepository.createStory("Other")
        val foreignVolumeId = mRepository.createVolume(otherStoryId, "Foreign")
        val initialRevision = requireNotNull(mRepository.getStory(storyId)).revision

        // 重复成员、重复顺序和跨故事分卷都必须在写入前拒绝。
        assertFalse(
            mRepository.reorderAllChapters(
                storyId = storyId,
                placements = listOf(
                    StoryChapterPlacement(chapterId = first.id, volumeId = null, sortOrder = 0),
                    StoryChapterPlacement(chapterId = first.id, volumeId = null, sortOrder = 1),
                    StoryChapterPlacement(chapterId = secondId, volumeId = volumeId, sortOrder = 0),
                ),
            ),
        )
        assertFalse(
            mRepository.reorderAllChapters(
                storyId = storyId,
                placements = listOf(
                    StoryChapterPlacement(chapterId = first.id, volumeId = null, sortOrder = 0),
                    StoryChapterPlacement(chapterId = secondId, volumeId = null, sortOrder = 0),
                ),
            ),
        )
        assertFalse(
            mRepository.reorderAllChapters(
                storyId = storyId,
                placements = listOf(
                    StoryChapterPlacement(chapterId = first.id, volumeId = foreignVolumeId, sortOrder = 0),
                    StoryChapterPlacement(chapterId = secondId, volumeId = volumeId, sortOrder = 0),
                ),
            ),
        )
        assertEquals(initialRevision, requireNotNull(mRepository.getStory(storyId)).revision)

        // 合法完整快照一次提交章节归属、连续顺序和故事修订号。
        assertTrue(
            mRepository.reorderAllChapters(
                storyId = storyId,
                placements = listOf(
                    StoryChapterPlacement(chapterId = secondId, volumeId = volumeId, sortOrder = 0),
                    StoryChapterPlacement(chapterId = first.id, volumeId = volumeId, sortOrder = 1),
                ),
            ),
        )
        assertEquals(
            listOf(secondId, first.id),
            mDatabase.getStoryChapterDao().getByContainer(storyId, volumeId).map { chapter -> chapter.id },
        )
        assertTrue(requireNotNull(mRepository.getStory(storyId)).revision > initialRevision)
    }

    @Test
    fun chapterSave_persistsGuidanceAndUsesChapterRevision() = runBlocking {
        val storyId = mRepository.createStory("Novel")
        val first = requireNotNull(mRepository.getStoryEditorData(storyId)).currentChapter
        val secondId = mRepository.createChapter(storyId = storyId, volumeId = null, title = "Chapter Two")

        val firstWrite = requireNotNull(
            value = mRepository.updateChapterDraft(
                storyId = storyId,
                chapterId = first.id,
                expectedChapterRevision = 0L,
                content = "First",
                continuationGuidance = "Guide One",
            ),
        )
        val secondWrite = requireNotNull(
            value = mRepository.updateChapterDraft(
                storyId = storyId,
                chapterId = secondId,
                expectedChapterRevision = 0L,
                content = "Second",
                continuationGuidance = "Guide Two",
            ),
        )

        assertEquals(1L, firstWrite.chapterRevision)
        assertEquals(1L, secondWrite.chapterRevision)
        assertTrue(secondWrite.storyRevision > firstWrite.storyRevision)
        assertNull(
            mRepository.updateChapterDraft(
                storyId = storyId,
                chapterId = first.id,
                expectedChapterRevision = 0L,
                content = "Stale",
                continuationGuidance = "Stale Guide",
            ),
        )
        val savedFirst = requireNotNull(mRepository.getChapter(storyId, first.id))
        assertEquals("First", savedFirst.content)
        assertEquals("Guide One", savedFirst.continuationGuidance)
    }

    @Test
    fun generatedEdit_isRejectedAfterStoryStructureChanges() = runBlocking {
        val storyId = mRepository.createStory("Novel")
        val chapter = requireNotNull(mRepository.getStoryEditorData(storyId)).currentChapter
        val baseStoryRevision = requireNotNull(mRepository.getStory(storyId)).revision

        mRepository.createVolume(storyId, "New volume")

        assertNull(
            mRepository.applyGeneratedEdit(
                edit = StoryGeneratedEdit(
                    storyId = storyId,
                    chapterId = chapter.id,
                    baseStoryRevision = baseStoryRevision,
                    baseChapterRevision = chapter.contentRevision,
                    start = 0,
                    end = 0,
                    originalTextHash = storyTextHash(""),
                    result = "Late result",
                    nextWorldInfoStates = emptyList(),
                ),
            ),
        )
        assertEquals("", requireNotNull(mRepository.getChapter(storyId, chapter.id)).content)
    }

    private suspend fun insertLorebookEntry(name: String = "Station"): Long {
        val lorebook = mDatabase
            .getLorebookDao()
            .getAllLorebooks()
            .firstOrNull()
            ?: Lorebook(name = "World").let { it.copy(id = mDatabase.getLorebookDao().insertOrReplace(it)) }
        return mDatabase.getLorebookEntryDao().insertOrReplace(
            data = LorebookEntry(
                lorebookId = lorebook.id,
                name = name,
                keywords = "[]",
                secondaryKeywords = "[]",
                order = 0,
                depth = 0,
                category = "[]",
                content = "The station was abandoned years ago.",
            ),
        )
    }

    private fun testCharacter(): Character {
        return Character(
            name = "Alice",
            avatar = "",
            characterTags = "[]",
            description = "A careful investigator",
            personality = "Patient",
            scenario = "An old city",
            firstMessages = "",
            examplesOfDialogue = "",
            postHistoryInstructions = "",
        )
    }
}
