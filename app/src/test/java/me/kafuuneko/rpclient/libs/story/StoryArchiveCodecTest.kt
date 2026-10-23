package me.kafuuneko.rpclient.libs.story

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class StoryArchiveCodecTest {
    private val mCodec = StoryArchiveCodec(Gson())

    @Test
    fun archiveRoundTripPreservesStructureStoryAndHints() {
        val source = StoryArchive(
            story = ArchivedStory(
                title = "Rain",
                memory = "Memory",
                authorNote = "Note",
                summary = "Summary",
                includeUserPersona = true,
                ungroupedChapters = listOf(
                    element = ArchivedChapter(title = "序章", content = "正文😀", continuationGuidance = "维持雨夜氛围"),
                ),
                volumes = listOf(
                    ArchivedVolume(
                        title = "第一卷",
                        chapters = listOf(
                            ArchivedChapter("第一章", "雨夜"),
                            ArchivedChapter("第二章", "旧城"),
                        ),
                    ),
                    ArchivedVolume(title = "第二卷"),
                ),
            ),
            characterHints = listOf(
                element = StoryCharacterHint(name = "Alice", fingerprint = "abc", activationMode = "primary"),
            ),
            lorebookHints = listOf(
                element = StoryLorebookHint(
                    lorebookName = "City",
                    entryName = "Station",
                    fingerprint = "def",
                ),
            ),
        )

        assertEquals(source, mCodec.decode(mCodec.encode(source)))
    }

    @Test
    fun v2ArchiveWithoutGuidanceDefaultsToEmpty() {
        val archive = mCodec.decode(
            json = """{"format":"rpclient_story","version":2,"story":{"title":"x","ungroupedChapters":[{"title":"正文","content":"y"}],"volumes":[]}}""",
        )

        assertEquals("", archive.story.ungroupedChapters.single().continuationGuidance)
    }

    @Test
    fun v1ArchiveMapsContinuousContentToDefaultChapter() {
        val archive = mCodec.decode(
            json = """{"format":"rpclient_story","version":1,"story":{"title":"x","content":"正文😀","memory":"m","summary":"s","authorNote":"n","includeUserPersona":true}}""",
        )

        assertEquals(StoryArchive.VERSION, archive.version)
        assertEquals(
            listOf(ArchivedChapter("Chapter 1", "正文😀")),
            archive.story.ungroupedChapters,
        )
        assertEquals(emptyList<ArchivedVolume>(), archive.story.volumes)
        assertEquals("m", archive.story.memory)
        assertEquals("s", archive.story.summary)
        assertEquals("n", archive.story.authorNote)
        assertEquals(true, archive.story.includeUserPersona)
    }

    @Test
    fun olderV1ArchiveIgnoresLegacyBindingFields() {
        val archive = mCodec.decode(
            json = """{"format":"rpclient_story","version":1,"story":{"title":"x","content":"y"},"characterHints":[{"name":"Alice","fingerprint":"abc","activationMode":"auto"}],"lorebookHints":[{"lorebookName":"City","entryName":"Station","fingerprint":"def","boundCharacterName":"Alice","boundCharacterFingerprint":"abc"}]}""",
        )

        assertEquals("auto", archive.characterHints.single().activationMode)
        assertEquals(
            StoryLorebookHint(lorebookName = "City", entryName = "Station", fingerprint = "def"),
            archive.lorebookHints.single(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownMajorVersionIsRejected() {
        mCodec.decode("""{"format":"rpclient_story","version":3,"story":{"title":"x"}}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingV1ContentIsRejected() {
        mCodec.decode("""{"format":"rpclient_story","version":1,"story":{"title":"x"}}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun v2ArchiveWithoutAnyChapterIsRejected() {
        mCodec.decode(
            json = """{"format":"rpclient_story","version":2,"story":{"title":"x","ungroupedChapters":[],"volumes":[]}}""",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun v2ArchiveMissingStructureArrayIsRejected() {
        mCodec.decode(
            json = """{"format":"rpclient_story","version":2,"story":{"title":"x","ungroupedChapters":[{"title":"正文","content":"y"}]}}""",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun archiveWithTooManyChaptersIsRejectedBeforeEncoding() {
        mCodec.encode(
            archive = StoryArchive(
                story = ArchivedStory(
                    title = "x",
                    ungroupedChapters = List(size = 10_001) { ArchivedChapter("Chapter $it", "") },
                ),
            ),
        )
    }
}
