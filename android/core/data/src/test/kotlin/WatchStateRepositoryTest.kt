package data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import model.Profile
import model.ProfileOutcome
import model.ProfileRequest
import model.WatchSnapshot
import testing.FakeCore
import testing.ResolvedCoreProvider
import uniffi.mediagram_core.CoreInterface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import uniffi.mediagram_core.Profile as CoreProfile

class WatchStateRepositoryTest {
    @Test
    fun reloadPopulatesProfilesAndTheChosenOnesSnapshot() =
        runTest {
            val core =
                FakeCore().apply {
                    profiles = listOf(CoreProfile("p1", "Alice"))
                    chosen = "p1"
                }
            // Seeded directly on the core, ahead of reload — a reload that
            // never actually read the snapshot would still pass against an
            // empty one.
            core.setProgress("p1", "set-1", 12.0, 100.0, "2026-10-03")
            val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)

            repository.reload()

            assertEquals(listOf(Profile("p1", "Alice")), repository.profiles.value)
            assertEquals("p1", repository.chosenProfileId.value)
            assertEquals(listOf("set-1"), repository.snapshot.value.progress.map { it.setId })
        }

    @Test
    fun noProfileChosenReloadsToAnEmptySnapshot() =
        runTest {
            val core = FakeCore()
            val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)

            repository.reload()

            assertNull(repository.chosenProfileId.value)
            assertEquals(WatchSnapshot.Empty, repository.snapshot.value)
        }

    @Test
    fun choosingAKnownProfileSetsItAndLoadsItsSnapshot() =
        runTest {
            val core = FakeCore().apply { profiles = listOf(CoreProfile("p1", "Alice")) }
            // Seeded ahead of the choice, so the assertion below only holds if
            // choosing actually loaded this profile's own snapshot.
            core.setWatchlisted("p1", "set-1", true)
            val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)

            val chose = repository.chooseProfile("p1")

            assertTrue(chose)
            assertEquals("p1", repository.chosenProfileId.value)
            assertEquals(listOf("set-1"), repository.snapshot.value.watchlist)
        }

    @Test
    fun choosingAnUnknownProfileChangesNothing() =
        runTest {
            // No profile named "nobody" exists — the real core's own rule for
            // an unknown id, which the fake now follows without needing to be
            // told to refuse.
            val core = FakeCore()
            val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)

            val chose = repository.chooseProfile("nobody")

            assertFalse(chose)
            assertNull(repository.chosenProfileId.value)
        }

    @Test
    fun aDoneRequestRereadsWhoExistsAndWhatTheyAre() =
        runTest {
            val core = FakeCore().apply { profiles = listOf(CoreProfile("a", "andre", admin = true)) }
            core.roles.pins["a"] = "1234"
            val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)
            repository.reload()

            assertEquals(ProfileOutcome.Done, repository.manage(ProfileRequest.CreateKid("a", "1234", "Mia", 6)))

            val mia = repository.profiles.value.single { it.name == "Mia" }
            assertEquals(Triple(true, 6, "a"), Triple(mia.kids, mia.kidsAge, mia.parentId))
            assertEquals(Profile("a", "andre", admin = true, hasPin = true), repository.profiles.value.single { it.id == "a" })
        }

    @Test
    fun anUnlockOrARefusalRereadsNothing() =
        runTest {
            val core = FakeCore().apply { profiles = listOf(CoreProfile("a", "andre", admin = true)) }
            core.roles.pins["a"] = "1234"
            val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)
            repository.reload()
            val reads = core.profilesCalls

            assertEquals(ProfileOutcome.Done, repository.manage(ProfileRequest.Unlock("a", "1234")))
            assertEquals(ProfileOutcome.WrongPin, repository.manage(ProfileRequest.CreateKid("a", "0000", "Mia", 6)))

            assertEquals(reads, core.profilesCalls)
        }

    /** Every reason the core gives comes through as the app's own, the wait's seconds included. */
    @Test
    fun eachReasonTheCoreGivesIsTheAppsOwn() =
        runTest {
            val core =
                FakeCore().apply {
                    profiles = listOf(CoreProfile("a", "andre", admin = true), CoreProfile("b", "Bea"), CoreProfile("k", "Kim", kids = true))
                }
            core.roles.pins["a"] = "1234"
            val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)
            repository.reload()

            assertEquals(ProfileOutcome.Invalid, repository.manage(ProfileRequest.CreateGrownUp("a", "1234", "Bo", "12")))
            assertEquals(ProfileOutcome.NameTaken, repository.manage(ProfileRequest.CreateKid("a", "1234", " KIM ", 6)))
            assertEquals(ProfileOutcome.NotFound, repository.manage(ProfileRequest.Remove("a", "1234", "nobody")))
            assertEquals(ProfileOutcome.NotAllowed, repository.manage(ProfileRequest.CreateFirstAdmin("Bo", "1234")))
            assertEquals(ProfileOutcome.NoPin, repository.manage(ProfileRequest.Unlock("b", "1234")))
            repeat(5) { assertEquals(ProfileOutcome.WrongPin, repository.manage(ProfileRequest.Unlock("a", "0000"))) }
            assertEquals(ProfileOutcome.Wait(60), repository.manage(ProfileRequest.Unlock("a", "1234")))
        }

    /** Who is watching is re-read with the list: a limit changed in Manage reaches every screen reading the chosen kid. */
    @Test
    fun aChangeToTheChosenKidRepublishesTheChosenProfile() =
        runTest {
            val core =
                FakeCore().apply {
                    profiles = listOf(CoreProfile("a", "andre", admin = true), CoreProfile("k", "Kim", kids = true, kidsAge = 12u, parentId = "a"))
                    chosen = "k"
                }
            core.roles.pins["a"] = "1234"
            val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)
            repository.reload()
            assertEquals(12, repository.chosenProfile.value?.kidsLimit)

            assertEquals(ProfileOutcome.Done, repository.manage(ProfileRequest.SetKidsAge("a", "1234", "k", 6)))

            assertEquals(6, repository.chosenProfile.value?.kidsLimit)
        }

    /** The core forgets a removed profile's choice itself; the re-read carries that out to every screen. */
    @Test
    fun removingTheChosenProfileLeavesNobodyChosen() =
        runTest {
            val core =
                FakeCore().apply {
                    profiles = listOf(CoreProfile("a", "andre", admin = true), CoreProfile("k", "Kim", kids = true, parentId = "a"))
                    chosen = "k"
                }
            core.roles.pins["a"] = "1234"
            val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)
            repository.reload()

            assertEquals(ProfileOutcome.Done, repository.manage(ProfileRequest.Remove("a", "1234", "k")))

            assertNull(repository.chosenProfileId.value)
            assertNull(repository.chosenProfile.value)
            assertEquals(listOf("a"), repository.profiles.value.map { it.id })
        }

    @Test
    fun aWriteWithNoChosenProfileDoesNothing() =
        runTest {
            // Intercepts the call itself, under any profile id at all — a
            // profile-scoped snapshot check could only ever rule out one id.
            var wrote = false
            val seeded = FakeCore().apply { profiles = listOf(CoreProfile("p1", "Alice")) }
            val core =
                object : CoreInterface by seeded {
                    override suspend fun setProgress(profileId: String, setId: String, at: Double, duration: Double?, localDay: String) {
                        wrote = true
                    }
                }
            val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)

            repository.setProgress("set-1", 12.0, 100.0)

            assertFalse(wrote)
        }

    @Test
    fun setProgressWritesUnderTheChosenProfileAndRefreshesTheSnapshot() =
        runTest {
            val core =
                FakeCore().apply {
                    profiles = listOf(CoreProfile("p1", "Alice"), CoreProfile("p2", "Ben"))
                    chosen = "p1"
                }
            val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)
            repository.reload()

            repository.setProgress("set-1", 12.0, 100.0)

            assertEquals(
                listOf("set-1"),
                repository.snapshot.value.progress
                    .map { it.setId },
            )
            // Landed under "p1", not anywhere else a wrong id could have sent it.
            assertEquals(emptyList(), core.snapshot("p2").progress)
        }

    /** `setKids` takes no profile id — marking is shared, not this profile's own. */
    @Test
    fun setKidsWritesGloballyRatherThanUnderAProfile() =
        runTest {
            val core =
                FakeCore().apply {
                    profiles = listOf(CoreProfile("p1", "Alice"), CoreProfile("p2", "Ben"))
                    chosen = "p1"
                }
            val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)
            repository.reload()

            repository.setKids("set-1", 6)
            repository.setKids("set-2", 12)

            assertEquals(mapOf("set-1" to 6, "set-2" to 12), repository.snapshot.value.kidsMarks)
            // A second profile that never wrote anything sees the same marks.
            assertEquals(listOf("set-1"), core.snapshot("p2").kidsFromSix)

            repository.setKids("set-1", null)
            assertEquals(mapOf("set-2" to 12), repository.snapshot.value.kidsMarks)
        }

    /**
     * `markFinished` must re-stamp on every call, even a repeat one with no
     * un-mark in between, and must always go through the core's own one-call
     * `setWatched` rather than a separate clear followed by a separate mark
     * — a `markFinished` that skipped the second call while already watched,
     * or that split it into two, would still land here as "finished", but
     * with a `finishedAt` that never moved and a position that only
     * sometimes cleared.
     */
    @Test
    fun markingAWatchedTitleFinishedAgainReStampsItAndDropsThePosition() =
        runTest {
            val core =
                FakeCore().apply {
                    profiles = listOf(CoreProfile("p1", "Alice"))
                    chosen = "p1"
                }
            val repository = DefaultWatchStateRepository(ResolvedCoreProvider(core), dispatcher = Dispatchers.Unconfined)
            repository.reload()

            repository.markFinished("s1")
            val first = repository.snapshot.value.watched.single().finishedAt
            repository.setProgress("s1", 30.0, 100.0)
            repository.markFinished("s1")

            assertTrue(repository.snapshot.value.watched.single().finishedAt > first)
            assertEquals(emptyList(), repository.snapshot.value.progress)
        }
}
