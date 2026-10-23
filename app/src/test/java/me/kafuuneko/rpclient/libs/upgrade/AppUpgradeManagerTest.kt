package me.kafuuneko.rpclient.libs.upgrade

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpgradeManagerTest {
    @Test
    fun upgradesRunInVersionOrderAndCheckpointEverySuccessfulStep() = runBlocking {
        val events = mutableListOf<String>()
        val store = RecordingVersionStore(events)
        val manager = manager(
            store = store,
            upgrades = listOf(
                FakeUpgrade(targetVersionCode = 30, events = events),
                FakeUpgrade(targetVersionCode = 10, events = events),
                FakeUpgrade(targetVersionCode = 20, events = events),
            ),
            currentVersionCode = 30,
        )

        manager.upgrade()

        assertEquals(
            listOf(
                "migrate-10",
                "checkpoint-10",
                "migrate-20",
                "checkpoint-20",
                "migrate-30",
                "checkpoint-30",
                "cleanup-10",
                "cleanup-checkpoint-10",
                "cleanup-20",
                "cleanup-checkpoint-20",
                "cleanup-30",
                "cleanup-checkpoint-30",
            ),
            events,
        )
        assertEquals(30, store.lastCompletedVersionCode)
        assertEquals(30, store.lastCleanedVersionCode)

        events.clear()
        manager.upgrade()

        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun completedStepsAreSkippedFutureStepsAreDeferredAndCleanupIsRetried() = runBlocking {
        val events = mutableListOf<String>()
        val store = RecordingVersionStore(events = events, initialVersionCode = 20)
        val manager = manager(
            store = store,
            upgrades = listOf(
                FakeUpgrade(targetVersionCode = 10, events = events),
                FakeUpgrade(targetVersionCode = 20, events = events),
                FakeUpgrade(targetVersionCode = 30, events = events),
                FakeUpgrade(targetVersionCode = 40, events = events),
            ),
            currentVersionCode = 30,
        )

        manager.upgrade()

        assertEquals(
            listOf(
                "migrate-30",
                "checkpoint-30",
                "cleanup-10",
                "cleanup-checkpoint-10",
                "cleanup-20",
                "cleanup-checkpoint-20",
                "cleanup-30",
                "cleanup-checkpoint-30",
            ),
            events,
        )
        assertEquals(30, store.lastCompletedVersionCode)
        assertEquals(30, store.lastCleanedVersionCode)
    }

    @Test
    fun failureStopsTheChainAndNextRunContinuesFromLastCheckpoint() = runBlocking {
        val events = mutableListOf<String>()
        val store = RecordingVersionStore(events)
        val failingUpgrade = FakeUpgrade(targetVersionCode = 20, events = events, shouldFail = true)
        val manager = manager(
            store = store,
            upgrades = listOf(
                FakeUpgrade(targetVersionCode = 10, events = events),
                failingUpgrade,
                FakeUpgrade(targetVersionCode = 30, events = events),
            ),
            currentVersionCode = 30,
        )

        val failure = runCatching { manager.upgrade() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(
            listOf(
                "migrate-10",
                "checkpoint-10",
                "migrate-20",
            ),
            events,
        )
        assertEquals(10, store.lastCompletedVersionCode)

        events.clear()
        failingUpgrade.shouldFail = false
        manager.upgrade()

        assertEquals(
            listOf(
                "migrate-20",
                "checkpoint-20",
                "migrate-30",
                "checkpoint-30",
                "cleanup-10",
                "cleanup-checkpoint-10",
                "cleanup-20",
                "cleanup-checkpoint-20",
                "cleanup-30",
                "cleanup-checkpoint-30",
            ),
            events,
        )
        assertEquals(30, store.lastCompletedVersionCode)
        assertEquals(30, store.lastCleanedVersionCode)
    }

    @Test
    fun cleanupFailureKeepsItsCheckpointAndIsRetriedWithoutRepeatingMigration() = runBlocking {
        val events = mutableListOf<String>()
        val store = RecordingVersionStore(
            events = events,
            initialVersionCode = 20,
            initialCleanedVersionCode = 10,
        )
        val cleanupFailure = FakeUpgrade(
            targetVersionCode = 20,
            events = events,
            cleanupShouldFail = true,
        )
        val manager = manager(
            store = store,
            upgrades = listOf(
                FakeUpgrade(targetVersionCode = 10, events = events),
                cleanupFailure,
            ),
            currentVersionCode = 20,
        )

        val failure = runCatching { manager.upgrade() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(listOf("cleanup-20"), events)
        assertEquals(20, store.lastCompletedVersionCode)
        assertEquals(10, store.lastCleanedVersionCode)

        events.clear()
        cleanupFailure.cleanupShouldFail = false
        manager.upgrade()

        assertEquals(
            listOf("cleanup-20", "cleanup-checkpoint-20"),
            events,
        )
        assertEquals(20, store.lastCleanedVersionCode)
    }

    @Test
    fun duplicateTargetVersionsAreRejected() {
        val store = RecordingVersionStore(mutableListOf())

        assertThrows(IllegalArgumentException::class.java) {
            manager(
                store = store,
                upgrades = listOf(
                    FakeUpgrade(targetVersionCode = 10, events = mutableListOf()),
                    FakeUpgrade(targetVersionCode = 10, events = mutableListOf()),
                ),
                currentVersionCode = 10,
            )
        }
    }

    private fun manager(
        store: AppUpgradeVersionStore,
        upgrades: List<AppUpgrade>,
        currentVersionCode: Int,
    ): AppUpgradeManager {
        return AppUpgradeManager(
            versionCodeProvider = AppVersionCodeProvider { currentVersionCode },
            versionStore = store,
            upgrades = upgrades,
        )
    }

    private class RecordingVersionStore(
        private val events: MutableList<String>,
        initialVersionCode: Int = 0,
        initialCleanedVersionCode: Int = 0
    ) : AppUpgradeVersionStore {
        private var value = initialVersionCode
        private var cleanedValue = initialCleanedVersionCode

        override var lastCompletedVersionCode: Int
            get() = value
            set(value) {
                this.value = value
                events += "checkpoint-$value"
            }

        override var lastCleanedVersionCode: Int
            get() = cleanedValue
            set(value) {
                cleanedValue = value
                events += "cleanup-checkpoint-$value"
            }
    }

    private class FakeUpgrade(
        override val targetVersionCode: Int,
        private val events: MutableList<String>,
        var shouldFail: Boolean = false,
        var cleanupShouldFail: Boolean = false
    ) : AppUpgrade {
        override suspend fun migrate() {
            events += "migrate-$targetVersionCode"
            if (shouldFail) error("Upgrade $targetVersionCode failed")
        }

        override suspend fun cleanup() {
            events += "cleanup-$targetVersionCode"
            if (cleanupShouldFail) error("Cleanup $targetVersionCode failed")
        }
    }
}
