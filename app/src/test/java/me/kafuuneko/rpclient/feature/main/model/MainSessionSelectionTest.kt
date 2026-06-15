package me.kafuuneko.rpclient.feature.main.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MainSessionSelectionTest {
    @Test
    fun sameNumericIdFromDifferentSessionTypes_remainsDistinct() {
        val selections = setOf(
            MainSessionSelection(MainSessionType.Chat, "1"),
            MainSessionSelection(MainSessionType.GroupChat, "1")
        )

        assertEquals(2, selections.size)
    }
}
