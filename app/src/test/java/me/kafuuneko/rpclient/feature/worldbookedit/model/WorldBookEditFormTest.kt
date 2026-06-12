package me.kafuuneko.rpclient.feature.worldbookedit.model

import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import org.junit.Assert.assertEquals
import org.junit.Test

class WorldBookEditFormTest {
    @Test
    fun renamingLorebookPreservesAdvancedFields() {
        val lorebook = Lorebook(
            id = 12L,
            name = "Imported",
            description = "Imported description",
            scanDepth = 9,
            tokenBudget = 384,
            recursiveScanning = true,
            extensionsJson = """{"custom":{"enabled":true}}"""
        )

        val saved = WorldBookEditForm.from(lorebook, emptyList())
            .copy(name = " Renamed ")
            .toLorebook()

        assertEquals(lorebook.copy(name = "Renamed"), saved)
    }
}
