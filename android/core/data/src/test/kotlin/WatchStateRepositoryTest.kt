package data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import model.Profile
import model.WatchSnapshot
import uniffi.mediagram_core.ProgressRow
import uniffi.mediagram_core.StateSnapshot
import uniffi.mediagram_core.Profile as CoreProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A core whose state calls are backed by plain in-memory maps rather than
 * `FakeCore`'s fixed answers — this repository writes and immediately reads
 * back what it wrote, which a canned list of results cannot stand in for.
 */
private class StateCoreClient(
    initialProfiles: List<CoreProfile> = emptyList(),
    private var chosen: String? = null,
    private val refuseChoose: Boolean = false,
) : CoreClient by FakeCore() {

    private val profileList = initialProfiles.toMutableList()
    private val snapshots = mutableMapOf<String, StateSnapshot>()

    /** Every write call this test asked for, in order — `"setProgress p1 s1"` and so on. */
    val writes = mutableListOf<String>()

    override suspend fun profiles(): List<CoreProfile> = profileList.toList()

    override suspend fun createProfile(name: String): CoreProfile {
        val created = CoreProfile("p${profileList.size + 1}", name)
        profileList += created
        return created
    }

    override suspend fun chosenProfile(): String? = chosen

    override suspend fun chooseProfile(id: String): Boolean {
        if (refuseChoose || profileList.none { it.id == id }) return false
        chosen = id
        return true
    }

    override suspend fun snapshot(profileId: String): StateSnapshot =
        snapshots[profileId] ?: StateSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    override suspend fun setProgress(profileId: String, setId: String, at: Double, duration: Double?) {
        writes += "setProgress $profileId $setId"
        val current = snapshots[profileId] ?: emptySnapshot()
        snapshots[profileId] = current.copy(progress = current.progress + ProgressRow(setId, at, duration, 0))
    }

    override suspend fun setKids(setId: String, marked: Boolean) {
        writes += "setKids $setId $marked"
        // Global: every profile's next snapshot read sees it, same as the core.
        for (id in profileList.map { it.id }) {
            val current = snapshots[id] ?: emptySnapshot()
            snapshots[id] = current.copy(kids = if (marked) current.kids + setId else current.kids - setId)
        }
    }

    private fun emptySnapshot() = StateSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
}

class WatchStateRepositoryTest {

    @Test
    fun reloadPopulatesProfilesAndTheChosenOnesSnapshot() = runTest {
        val alice = CoreProfile("p1", "Alice")
        val core = StateCoreClient(initialProfiles = listOf(alice), chosen = "p1")
        val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)

        repository.reload()

        assertEquals(listOf(Profile("p1", "Alice")), repository.profiles.value)
        assertEquals("p1", repository.chosenProfileId.value)
        assertEquals(WatchSnapshot.Empty, repository.snapshot.value)
    }

    @Test
    fun noProfileChosenReloadsToAnEmptySnapshot() = runTest {
        val core = StateCoreClient()
        val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)

        repository.reload()

        assertNull(repository.chosenProfileId.value)
        assertEquals(WatchSnapshot.Empty, repository.snapshot.value)
    }

    @Test
    fun choosingAKnownProfileSetsItAndLoadsItsSnapshot() = runTest {
        val core = StateCoreClient(initialProfiles = listOf(CoreProfile("p1", "Alice")))
        val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)

        val chose = repository.choose("p1")

        assertTrue(chose)
        assertEquals("p1", repository.chosenProfileId.value)
    }

    @Test
    fun choosingAnUnknownProfileChangesNothing() = runTest {
        val core = StateCoreClient(refuseChoose = true)
        val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)

        val chose = repository.choose("nobody")

        assertFalse(chose)
        assertNull(repository.chosenProfileId.value)
    }

    @Test
    fun creatingAProfileAddsItWithoutChoosingIt() = runTest {
        val core = StateCoreClient()
        val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)

        val created = repository.create("Bea")

        assertEquals("Bea", created?.name)
        assertEquals(listOf(Profile(created!!.id, "Bea")), repository.profiles.value)
        assertNull(repository.chosenProfileId.value)
    }

    @Test
    fun aWriteWithNoChosenProfileDoesNothing() = runTest {
        val core = StateCoreClient()
        val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)

        repository.setProgress("set-1", 12.0, 100.0)

        assertEquals(emptyList<String>(), core.writes)
    }

    @Test
    fun setProgressWritesUnderTheChosenProfileAndRefreshesTheSnapshot() = runTest {
        val core = StateCoreClient(initialProfiles = listOf(CoreProfile("p1", "Alice")), chosen = "p1")
        val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)
        repository.reload()

        repository.setProgress("set-1", 12.0, 100.0)

        assertEquals(listOf("setProgress p1 set-1"), core.writes)
        assertEquals(listOf("set-1"), repository.snapshot.value.progress.map { it.setId })
    }

    /** [CoreClient.setKids] takes no profile id — marking is shared, not this profile's own. */
    @Test
    fun setKidsWritesGloballyRatherThanUnderAProfile() = runTest {
        val core = StateCoreClient(initialProfiles = listOf(CoreProfile("p1", "Alice")), chosen = "p1")
        val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)
        repository.reload()

        repository.setKids("set-1", true)

        assertEquals(listOf("setKids set-1 true"), core.writes)
        assertEquals(listOf("set-1"), repository.snapshot.value.kids)
    }
}
