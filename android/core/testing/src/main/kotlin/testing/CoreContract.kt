package testing

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import uniffi.mediagram_core.CoreException
import uniffi.mediagram_core.CoreInterface
import uniffi.mediagram_core.PreferenceRow
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a fresh core with no Telegram session answers offline, on a data
 * directory nothing has written to yet. Run once against [FakeCore] in a
 * plain unit test ([FakeCoreContractTest], this module) and once against the
 * real generated `Core` on the tablet (`RealCoreContractTest`, core:rust's
 * `androidTest`, next to `CoreLoadsTest`) — both must agree, or the fake is
 * lying about a contract it claims to keep.
 *
 * Every case here is picked for needing neither a network round trip nor a
 * signed-in session, which is what makes the same suite safe to run in a
 * plain JVM unit test at all:
 * - [aFreshCatalogListsNoSets] / the two `NotFound` cases read
 *   `store::list_sets`/`store::total_size`/`read` — local files, and the
 *   core's own doc for `list_sets` is "an empty list before the first
 *   catalog is loaded".
 * - [revokingTheCurrentSessionIsRefused] is rejected before the core ever
 *   resolves a Telegram connection (`api::sessions::revoke_session`).
 * - [syncingALibraryThisDeviceNeverStoredFails] is answered from the local
 *   list of stored libraries before `api::state_sync::sync_state` opens a
 *   connection — as a failed outcome, not a throw, since a round never throws.
 * - the profile cases are `state_db` reads/writes only
 *   (`api::state::{profiles,create_profile,choose_profile,delete_profile}`);
 *   [aBlankProfileNameIsRefused] pins `clean_name`'s own rule
 *   (`state/profiles.rs`), also checked before any row is written.
 * - the watch-state cases below are `state_db` reads/writes too
 *   (`state::rows`, `state::editors_choice`, `state::lists`,
 *   `state::preferences`) — progress, watched marks, the watchlist, Kids,
 *   the editor's choice, collections and preferences, each against a
 *   profile [createProfile] made moments earlier,
 *   since every one of those tables but Kids and the editor's
 *   choice has a foreign key to `profiles(id)` — [aWriteForAProfileNobodyCreatedIsDropped]
 *   and [removingAProfileTakesItsWatchStateWithIt] pin that foreign key and
 *   its cascade directly. Three cases ([progressListsNewestFirst],
 *   [finishingATitleAlwaysReStampsAndClearsItsPosition],
 *   [reAddingALiveWatchlistMarkDoesNotMoveItToTheFront]) `delay` between the
 *   writes whose order the assertion depends on — the real core's clock is
 *   wall time in milliseconds, and two calls back to back could otherwise
 *   land in the same one.
 *
 * Every case has a block body, not an expression body: `assertFailsWith`
 * answers the exception it caught rather than `Unit`, and an expression body
 * would infer that as the method's return type — which JUnit4 rejects,
 * since a `@Test` method must return `void`.
 */
abstract class CoreContract {
    abstract fun core(): CoreInterface

    @Test
    fun aFreshCatalogListsNoSets() {
        runBlocking { assertEquals(emptyList(), core().listSets()) }
    }

    @Test
    fun aFreshCatalogsOneSetLookupAnswersNoneForAnyId() {
        runBlocking { assertEquals(null, core().mediaSet("no-such-set")) }
    }

    @Test
    fun readingAnUnknownSetIsNotFound() {
        runBlocking { assertFailsWith<CoreException.NotFound> { core().read("no-such-set", 0uL, 0u) } }
    }

    @Test
    fun totalSizeOfAnUnknownSetIsNotFound() {
        runBlocking { assertFailsWith<CoreException.NotFound> { core().totalSize("no-such-set") } }
    }

    @Test
    fun revokingTheCurrentSessionIsRefused() {
        runBlocking { assertFailsWith<CoreException.NotAuthorized> { core().revokeSession("0") } }
    }

    @Test
    fun syncingALibraryThisDeviceNeverStoredFails() {
        runBlocking {
            val outcome = core().syncState("no-such-library")
            assertEquals(0uL, outcome.pulled)
            assertFalse(outcome.pushed)
            assertTrue(outcome.failed != null)
        }
    }

    @Test
    fun aCreatedProfileIsListed() {
        runBlocking {
            val core = core()
            val created = core.createProfile("Alice", false)
            assertTrue(created != null && core.profiles().any { it.id == created.id && it.name == "Alice" })
        }
    }

    @Test
    fun choosingACreatedProfileIsRememberedLocally() {
        runBlocking {
            val core = core()
            val created = core.createProfile("Bea", false) ?: error("a local profile must be creatable offline")
            assertTrue(core.chooseProfile(created.id))
            assertEquals(created.id, core.chosenProfile())
        }
    }

    @Test
    fun removingAProfileTakesItOffTheList() {
        runBlocking {
            val core = core()
            val created = core.createProfile("Chris", false) ?: error("a local profile must be creatable offline")
            assertTrue(core.deleteProfile(created.id))
            assertTrue(core.profiles().none { it.id == created.id })
        }
    }

    @Test
    fun aBlankProfileNameIsRefused() {
        runBlocking { assertEquals(null, core().createProfile("  ", false)) }
    }

    private suspend fun CoreInterface.freshProfile(name: String) =
        createProfile(name, false) ?: error("a local profile must be creatable offline")

    @Test
    fun settingProgressTwiceKeepsOneRowWithTheNewestValue() {
        runBlocking {
            val core = core()
            val profile = core.freshProfile("Dana")
            core.setProgress(profile.id, "01A", 100.0, 900.0)
            core.setProgress(profile.id, "01A", 742.0, 900.0)
            val progress = core.snapshot(profile.id).progress
            assertEquals(1, progress.size)
            assertEquals(742.0, progress.single().at)
        }
    }

    @Test
    fun progressNeverStoresANegativePosition() {
        runBlocking {
            val core = core()
            val profile = core.freshProfile("Eli")
            core.setProgress(profile.id, "01A", -50.0, null)
            assertEquals(0.0, core.snapshot(profile.id).progress.single().at)
        }
    }

    @Test
    fun progressListsNewestFirst() {
        runBlocking {
            val core = core()
            val profile = core.freshProfile("Faye")
            core.setProgress(profile.id, "01A", 10.0, null)
            delay(5)
            core.setProgress(profile.id, "01B", 20.0, null)
            assertEquals(listOf("01B", "01A"), core.snapshot(profile.id).progress.map { it.setId })
        }
    }

    @Test
    fun clearingProgressForgetsThePositionWithoutMarkingItWatched() {
        runBlocking {
            val core = core()
            val profile = core.freshProfile("Gus")
            core.setProgress(profile.id, "01A", 300.0, null)
            core.clearProgress(profile.id, "01A")
            val snapshot = core.snapshot(profile.id)
            assertEquals(emptyList(), snapshot.progress)
            assertEquals(emptyList(), snapshot.watched)
        }
    }

    @Test
    fun finishingATitleAlwaysReStampsAndClearsItsPosition() {
        runBlocking {
            val core = core()
            val profile = core.freshProfile("Hana")
            core.setProgress(profile.id, "01A", 700.0, 1000.0)

            core.setWatched(profile.id, "01A", true)
            val first = core.snapshot(profile.id)
            assertEquals(emptyList(), first.progress)
            val firstFinishedAt = first.watched.single { it.setId == "01A" }.finishedAt

            delay(5)
            core.setWatched(profile.id, "01A", true)
            val second = core.snapshot(profile.id).watched
            assertEquals(1, second.count { it.setId == "01A" })
            assertTrue(second.single { it.setId == "01A" }.finishedAt > firstFinishedAt)
        }
    }

    @Test
    fun takingAMarkBackNeverTouchesAPosition() {
        runBlocking {
            val core = core()
            val profile = core.freshProfile("Ivo")
            core.setWatched(profile.id, "01A", true)
            core.setProgress(profile.id, "01A", 300.0, null)

            core.setWatched(profile.id, "01A", false)

            val snapshot = core.snapshot(profile.id)
            assertEquals(emptyList(), snapshot.watched)
            assertEquals(300.0, snapshot.progress.single().at)
        }
    }

    @Test
    fun aRemarkAfterARemovalIsWatchedAgain() {
        runBlocking {
            val core = core()
            val profile = core.freshProfile("Jael")
            core.setWatched(profile.id, "01A", true)
            core.setWatched(profile.id, "01A", false)
            core.setWatched(profile.id, "01A", true)

            assertEquals(listOf("01A"), core.snapshot(profile.id).watched.map { it.setId })
        }
    }

    @Test
    fun addingToTheWatchlistTwiceIsNotTwoRows() {
        runBlocking {
            val core = core()
            val profile = core.freshProfile("Kalle")
            core.setWatchlisted(profile.id, "01A", true)
            core.setWatchlisted(profile.id, "01A", true)
            assertEquals(listOf("01A"), core.snapshot(profile.id).watchlist)
        }
    }

    @Test
    fun removingFromTheWatchlistThenAddingBackIsVisibleAgain() {
        runBlocking {
            val core = core()
            val profile = core.freshProfile("Lior")
            core.setWatchlisted(profile.id, "01A", true)
            core.setWatchlisted(profile.id, "01A", false)
            assertEquals(emptyList(), core.snapshot(profile.id).watchlist)

            core.setWatchlisted(profile.id, "01A", true)
            assertEquals(listOf("01A"), core.snapshot(profile.id).watchlist)
        }
    }

    /** A second `true` on a mark already live must not move it — only a tombstoned or absent mark is (re)dated. */
    @Test
    fun reAddingALiveWatchlistMarkDoesNotMoveItToTheFront() {
        runBlocking {
            val core = core()
            val profile = core.freshProfile("Vico")
            core.setWatchlisted(profile.id, "01A", true)
            delay(5)
            core.setWatchlisted(profile.id, "01B", true)
            delay(5)
            core.setWatchlisted(profile.id, "01A", true)
            assertEquals(listOf("01B", "01A"), core.snapshot(profile.id).watchlist)
        }
    }

    @Test
    fun markingATitleForKidsTwiceIsNotTwoRows() {
        runBlocking {
            val core = core()
            val profile = core.freshProfile("Mira")
            core.setKids("01A", true)
            core.setKids("01A", true)
            assertEquals(listOf("01A"), core.snapshot(profile.id).kids)
        }
    }

    @Test
    fun removingATitleFromKidsThenAddingBackIsVisibleAgain() {
        runBlocking {
            val core = core()
            val profile = core.freshProfile("Noor")
            core.setKids("01A", true)
            core.setKids("01A", false)
            assertEquals(emptyList(), core.snapshot(profile.id).kids)

            core.setKids("01A", true)
            assertEquals(listOf("01A"), core.snapshot(profile.id).kids)
        }
    }

    @Test
    fun kidsMarksAreSharedAcrossEveryProfile() {
        runBlocking {
            val core = core()
            val parent = core.freshProfile("Omar")
            val child = core.freshProfile("Pia")

            core.setKids("01A", true)

            assertEquals(listOf("01A"), core.snapshot(parent.id).kids)
            assertEquals(listOf("01A"), core.snapshot(child.id).kids)
        }
    }

    @Test
    fun pinningAnEditorsChoiceReplacesTheLastPick() {
        runBlocking {
            val core = core()
            core.setEditorsChoice("01A", true)
            core.setEditorsChoice("01B", true)
            assertEquals("01B", core.editorsChoice())
        }
    }

    @Test
    fun unpinningTheEditorsChoiceClearsIt() {
        runBlocking {
            val core = core()
            core.setEditorsChoice("01A", true)
            core.setEditorsChoice("01A", false)
            assertEquals(null, core.editorsChoice())
        }
    }

    @Test
    fun creatingRenamingAndDeletingAListWorksForItsOwner() {
        runBlocking {
            val core = core()
            val profile = core.freshProfile("Quinn")
            val created = core.createCollection(profile.id, "Weekend") ?: error("a list must be creatable for its own profile")

            assertTrue(core.renameCollection(profile.id, created.id, "Weeknights"))
            assertEquals("Weeknights", core.snapshot(profile.id).collections.single().name)

            assertTrue(core.deleteCollection(profile.id, created.id))
            assertEquals(emptyList(), core.snapshot(profile.id).collections)
        }
    }

    @Test
    fun listsBelongToOneProfileAndAreUntouchableByAnother() {
        runBlocking {
            val core = core()
            val owner = core.freshProfile("Remy")
            val other = core.freshProfile("Skye")
            val created = core.createCollection(owner.id, "Weekend") ?: error("a list must be creatable for its own profile")

            assertFalse(core.renameCollection(other.id, created.id, "Hijacked"))
            assertFalse(core.deleteCollection(other.id, created.id))
            assertEquals("Weekend", core.snapshot(owner.id).collections.single().name)
        }
    }

    @Test
    fun addingAndRemovingATitleInAListIsIdempotent() {
        runBlocking {
            val core = core()
            val profile = core.freshProfile("Tao")
            val created = core.createCollection(profile.id, "Weekend") ?: error("a list must be creatable for its own profile")

            assertTrue(core.setInCollection(profile.id, created.id, "01A", true))
            assertTrue(core.setInCollection(profile.id, created.id, "01A", true))
            assertEquals(listOf("01A"), core.snapshot(profile.id).collections.single().items)

            assertTrue(core.setInCollection(profile.id, created.id, "01A", false))
            assertEquals(emptyList(), core.snapshot(profile.id).collections.single().items)
        }
    }

    /** Every per-profile table but Kids and the editor's choice cascades off `profiles(id)` — see `schema.rs`. */
    @Test
    fun removingAProfileTakesItsWatchStateWithIt() {
        runBlocking {
            val core = core()
            val profile = core.freshProfile("Wren")
            core.setProgress(profile.id, "01A", 300.0, null)
            core.setWatchlisted(profile.id, "01B", true)
            assertTrue(core.setPreference(profile.id, "show:Dark", "audio", "de"))

            assertTrue(core.deleteProfile(profile.id))

            val snapshot = core.snapshot(profile.id)
            assertEquals(emptyList(), snapshot.progress)
            assertEquals(emptyList(), snapshot.watchlist)
            assertEquals(emptyList(), core.preferences(profile.id))
        }
    }

    /** No [freshProfile] call on purpose — the id below names no profile at all. */
    @Test
    fun aWriteForAProfileNobodyCreatedIsDropped() {
        runBlocking {
            val core = core()
            core.setProgress("no-such-profile", "01A", 300.0, null)
            assertEquals(emptyList(), core.snapshot("no-such-profile").progress)
            assertEquals(null, core.createCollection("no-such-profile", "Weekend"))
            assertFalse(core.setPreference("no-such-profile", "show:Dark", "audio", "de"))
            assertEquals(emptyList(), core.preferences("no-such-profile"))
        }
    }

    @Test
    fun aPreferenceIsRememberedReplacedAndForgottenForItsProfileOnly() {
        runBlocking {
            val core = core()
            val owner = core.freshProfile("Uma")
            val other = core.freshProfile("Vic")

            assertTrue(core.setPreference(owner.id, "show:Dark", "speed", "1.5"))
            assertTrue(core.setPreference(owner.id, "show:Dark", "speed", "2"))
            assertEquals(listOf(PreferenceRow("show:Dark", "speed", "2")), core.preferences(owner.id))
            assertEquals(emptyList(), core.preferences(other.id))

            assertTrue(core.setPreference(owner.id, "show:Dark", "speed", null))
            assertEquals(emptyList(), core.preferences(owner.id))
        }
    }

    /** `preferences::set` trims rather than collapses, caps at 200 characters, refuses a blank scope or name, and forgets on a blank value. */
    @Test
    fun aPreferenceIsTrimmedAndCappedAndABlankScopeOrNameIsRefused() {
        runBlocking {
            val core = core()
            val profile = core.freshProfile("Yara")

            assertTrue(core.setPreference(profile.id, "  show:Dark  Matter ", " audio ", " de  forced "))
            assertEquals(listOf(PreferenceRow("show:Dark  Matter", "audio", "de  forced")), core.preferences(profile.id))
            assertFalse(core.setPreference(profile.id, "  ", "audio", "en"))
            assertFalse(core.setPreference(profile.id, "show:Dark  Matter", "", "en"))

            assertTrue(core.setPreference(profile.id, "show:Dark  Matter", "audio", "  "))
            assertEquals(emptyList(), core.preferences(profile.id))

            assertTrue(core.setPreference(profile.id, "show:Dark", "note", "x".repeat(201)))
            assertEquals("x".repeat(200), core.preferences(profile.id).single().value)
        }
    }
}
