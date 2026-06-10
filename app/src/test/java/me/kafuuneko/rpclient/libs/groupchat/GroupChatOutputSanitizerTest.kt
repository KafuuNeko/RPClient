package me.kafuuneko.rpclient.libs.groupchat

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupChatOutputSanitizerTest {
    private val sanitizer = GroupChatOutputSanitizer()

    @Test
    fun removesCurrentSpeakerPrefixAndTrimsAtAnotherSpeaker() {
        val result = sanitizer.sanitize(
            content = "Mina: I found the archive.\nLyra: Let me see.",
            currentSpeakerName = "Mina",
            otherSpeakerNames = listOf("Lyra"),
            trimOtherSpeakers = true
        )

        assertEquals("I found the archive.", result)
    }

    @Test
    fun preservesOtherSpeakerTextWhenTrimmingIsDisabled() {
        val result = sanitizer.sanitize(
            content = "Mina: First line.\nLyra: Second line.",
            currentSpeakerName = "Mina",
            otherSpeakerNames = listOf("Lyra"),
            trimOtherSpeakers = false
        )

        assertEquals("First line.\nLyra: Second line.", result)
    }
}
