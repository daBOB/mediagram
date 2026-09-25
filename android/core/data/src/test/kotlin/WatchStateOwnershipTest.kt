package data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import model.WatchSnapshot
import uniffi.mediagram_core.ListRow
import uniffi.mediagram_core.Profile
import uniffi.mediagram_core.StateSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class DelayedStateCore(
    var chosen: String = "a",
) : CoreClient by FakeCore() {
    var delaySnapshot = false
    var delayWrite = false
    var delayChoice = false
    var delayAfterChoice = false
    var prefix = "kept"
    val started = CompletableDeferred<Unit>()
    val finish = CompletableDeferred<Unit>()

    override suspend fun profiles() = listOf(Profile("a", "Alice"), Profile("b", "Bea"))

    override suspend fun chosenProfile() = chosen

    override suspend fun chooseProfile(id: String): Boolean {
        if (id == "a" && delayChoice) {
            started.complete(Unit)
            finish.await()
        }
        chosen = id
        if (delayAfterChoice) {
            started.complete(Unit)
            finish.await()
        }
        return true
    }

    override suspend fun createProfile(
        name: String,
        kids: Boolean,
    ): Profile {
        write()
        return Profile("created", name, kids)
    }

    override suspend fun snapshot(profileId: String): StateSnapshot {
        val answer = StateSnapshot(emptyList(), emptyList(), listOf("$prefix-$profileId"), emptyList(), emptyList(), null)
        if (profileId == "a" && delaySnapshot) {
            started.complete(Unit)
            finish.await()
        }
        return answer
    }

    private suspend fun write() {
        if (delayWrite) {
            started.complete(Unit)
            finish.await()
        }
    }

    override suspend fun setProgress(
        profileId: String,
        setId: String,
        at: Double,
        duration: Double?,
    ) = write()

    override suspend fun renameCollection(
        profileId: String,
        id: String,
        name: String,
    ): Boolean {
        write()
        return true
    }

    override suspend fun createCollection(
        profileId: String,
        name: String,
    ): ListRow {
        write()
        return ListRow("list", name, emptyList())
    }
}

class WatchStateOwnershipTest {
    @Test
    fun aChoiceWaitingForACoreCannotOvertakeANewerChoice() =
        runTest {
            val core = DelayedStateCore()
            val waiting = CompletableDeferred<Unit>()
            val resume = CompletableDeferred<Unit>()
            var pauseNext = false
            val provider =
                object : CoreProvider by ResolvedCoreProvider(core) {
                    override suspend fun awaitCore(): CoreClient {
                        if (pauseNext) {
                            pauseNext = false
                            waiting.complete(Unit)
                            resume.await()
                        }
                        return core
                    }
                }
            val repository = DefaultWatchStateRepository(provider, Dispatchers.Unconfined)
            repository.reload()
            pauseNext = true
            val old = async(start = CoroutineStart.UNDISPATCHED) { repository.chooseProfile("a") }
            try {
                waiting.await()
                assertTrue(repository.chooseProfile("b"))
            } finally {
                resume.complete(Unit)
            }
            assertFalse(old.await())
            assertEquals("b", core.chosen)
            assertEquals("b", repository.chosenProfileId.value)
            assertEquals(listOf("kept-b"), repository.snapshot.value.watchlist)
        }

    @Test
    fun aQueuedReloadCannotBorrowTheNextChoicesPublicationRevision() =
        runTest {
            val firstStarted = CompletableDeferred<Unit>()
            val firstDone = CompletableDeferred<Unit>()
            val secondStarted = CompletableDeferred<Unit>()
            val secondDone = CompletableDeferred<Unit>()
            val snapshotsDone = CompletableDeferred<Unit>()
            val core =
                object : CoreClient by FakeCore() {
                    var chosen = "c"

                    override suspend fun profiles() = listOf(Profile("a", "Alice"), Profile("b", "Bea"), Profile("c", "Chris"))

                    override suspend fun chosenProfile() = chosen

                    override suspend fun chooseProfile(id: String): Boolean {
                        chosen = id
                        if (id == "a") {
                            firstStarted.complete(Unit)
                            firstDone.await()
                        }
                        if (id == "b") {
                            secondStarted.complete(Unit)
                            secondDone.await()
                        }
                        return true
                    }

                    override suspend fun snapshot(profileId: String): StateSnapshot {
                        if (profileId == "a") snapshotsDone.await()
                        return StateSnapshot(emptyList(), emptyList(), listOf(profileId), emptyList(), emptyList(), null)
                    }
                }
            val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), Dispatchers.Unconfined)
            repository.reload()
            val first = async(start = CoroutineStart.UNDISPATCHED) { repository.chooseProfile("a") }
            firstStarted.await()
            val reload = async(start = CoroutineStart.UNDISPATCHED) { repository.reload() }
            val second = async(start = CoroutineStart.UNDISPATCHED) { repository.chooseProfile("b") }
            try {
                firstDone.complete(Unit)
                secondStarted.await()
                snapshotsDone.complete(Unit)
                reload.await()
                secondDone.complete(Unit)
                assertTrue(second.await())
                assertEquals("b", repository.chosenProfileId.value)
                assertEquals(listOf("b"), repository.snapshot.value.watchlist)
            } finally {
                firstDone.complete(Unit)
                secondDone.complete(Unit)
                snapshotsDone.complete(Unit)
                first.await()
                reload.await()
                second.await()
            }
        }

    @Test
    fun reloadingAnAlreadyPersistedChoiceCannotMakeItsAcknowledgementFail() =
        runTest {
            val core = DelayedStateCore()
            val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), Dispatchers.Unconfined)
            repository.reload()
            core.delayAfterChoice = true
            val choice = async(start = CoroutineStart.UNDISPATCHED) { repository.chooseProfile("b") }
            core.started.await()
            val reload = async(start = CoroutineStart.UNDISPATCHED) { repository.reload() }
            core.finish.complete(Unit)
            assertTrue(choice.await())
            reload.await()
            assertEquals("b", repository.chosenProfileId.value)
            assertEquals(listOf("kept-b"), repository.snapshot.value.watchlist)
        }

    @Test
    fun returningToTheSameProfileCannotAcceptItsOlderSnapshot() =
        runTest {
            val core = DelayedStateCore()
            val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), Dispatchers.Unconfined)
            repository.reload()
            core.delaySnapshot = true
            val old = async(start = CoroutineStart.UNDISPATCHED) { repository.reload() }
            try {
                core.started.await()
                repository.chooseProfile("b")
                core.delaySnapshot = false
                core.prefix = "new"
                repository.chooseProfile("a")
            } finally {
                core.finish.complete(Unit)
                old.await()
            }
            assertEquals("a", repository.chosenProfileId.value)
            assertEquals(listOf("new-a"), repository.snapshot.value.watchlist)
        }

    @Test
    fun resetPreventsPendingReadsAndProfileCreationFromRestoringState() =
        runTest {
            for (creating in listOf(false, true)) {
                val core = DelayedStateCore()
                val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), Dispatchers.Unconfined)
                repository.reload()
                core.delaySnapshot = !creating
                core.delayWrite = creating
                val old =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        if (creating) repository.createProfile("Chris") else repository.reload()
                    }
                try {
                    core.started.await()
                    repository.invalidate()
                    assertNull(repository.chosenProfileId.value)
                    assertEquals(emptyList(), repository.profiles.value)
                    assertEquals(WatchSnapshot.Empty, repository.snapshot.value)
                } finally {
                    core.finish.complete(Unit)
                    old.await()
                }
                assertNull(repository.chosenProfileId.value)
                assertEquals(emptyList(), repository.profiles.value)
                assertEquals(WatchSnapshot.Empty, repository.snapshot.value)
            }
        }

    @Test
    fun newerChoicesPersistAfterAnOlderChoiceFinishes() =
        runTest {
            val core = DelayedStateCore()
            val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), Dispatchers.Unconfined)
            repository.reload()
            core.delayChoice = true
            val first = async(start = CoroutineStart.UNDISPATCHED) { repository.chooseProfile("a") }
            core.started.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) { repository.chooseProfile("b") }
            try {
                assertFalse(second.isCompleted)
            } finally {
                core.finish.complete(Unit)
            }
            assertTrue(first.await())
            assertTrue(second.await())
            assertEquals("b", core.chosen)
            assertEquals("b", repository.chosenProfileId.value)
            assertEquals(listOf("kept-b"), repository.snapshot.value.watchlist)
        }

    @Test
    fun aDelayedReloadCannotOverwriteANewerProfileChoice() =
        runTest {
            val core = DelayedStateCore()
            val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), Dispatchers.Unconfined)
            repository.reload()
            core.delaySnapshot = true
            val old = async(start = CoroutineStart.UNDISPATCHED) { repository.reload() }
            try {
                core.started.await()
                assertTrue(repository.chooseProfile("b"))
                assertEquals(listOf("kept-b"), repository.snapshot.value.watchlist)
            } finally {
                core.finish.complete(Unit)
                old.await()
            }
            assertEquals("b", repository.chosenProfileId.value)
            assertEquals(listOf("kept-b"), repository.snapshot.value.watchlist)
        }

    @Test
    fun delayedWritesAndListCreationCannotPublishThePreviousProfilesSnapshot() =
        runTest {
            for (operation in listOf("progress", "rename", "create")) {
                val core = DelayedStateCore()
                val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), Dispatchers.Unconfined)
                repository.reload()
                core.delayWrite = true
                val old =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        when (operation) {
                            "progress" -> repository.setProgress("film", 12.0, 100.0)
                            "rename" -> repository.renameList("list", "Weekend")
                            else -> repository.createList("Weekend")
                        }
                    }
                try {
                    core.started.await()
                    assertTrue(repository.chooseProfile("b"))
                } finally {
                    core.finish.complete(Unit)
                    old.await()
                }
                assertEquals("b", repository.chosenProfileId.value, operation)
                assertEquals(listOf("kept-b"), repository.snapshot.value.watchlist, operation)
            }
        }

    @Test
    fun anOlderChoiceCannotPublishAfterTheNewChoiceHasFinished() =
        runTest {
            val core = DelayedStateCore()
            val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), Dispatchers.Unconfined)
            repository.reload()
            core.delaySnapshot = true
            val old = async(start = CoroutineStart.UNDISPATCHED) { repository.chooseProfile("a") }
            try {
                core.started.await()
                assertTrue(repository.chooseProfile("b"))
            } finally {
                core.finish.complete(Unit)
                old.await()
            }
            assertEquals("b", repository.chosenProfileId.value)
            assertEquals(listOf("kept-b"), repository.snapshot.value.watchlist)
        }

    @Test
    fun aReloadFromAReplacedCoreCannotRestoreItsProfilesOrSnapshot() =
        runTest {
            val first = DelayedStateCore()
            val current = MutableStateFlow<CoreClient?>(first)
            val provider =
                object : CoreProvider by ResolvedCoreProvider(first) {
                    override val core = current

                    override suspend fun awaitCore() = current.value!!
                }
            val repository = DefaultWatchStateRepository(provider, Dispatchers.Unconfined)
            repository.reload()
            first.delaySnapshot = true
            val old = async(start = CoroutineStart.UNDISPATCHED) { repository.reload() }
            try {
                first.started.await()
                current.value = DelayedStateCore("a").apply { prefix = "replacement" }
                repository.reload()
            } finally {
                first.finish.complete(Unit)
                old.await()
            }
            assertEquals("a", repository.chosenProfileId.value)
            assertEquals(listOf("replacement-a"), repository.snapshot.value.watchlist)
        }
}
