package testing

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import uniffi.mediagram_core.Achievements
import uniffi.mediagram_core.CoreException
import uniffi.mediagram_core.CoreInterface
import uniffi.mediagram_core.PreferenceRow
import uniffi.mediagram_core.Profile
import uniffi.mediagram_core.ProfileOutcome
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
 *   (`api::state::{profiles,choose_profile}` and `api::state::profile_roles`),
 *   each household made the way the surface makes one: the first grown-up
 *   by `createFirstAdmin`, every later one by that admin ([freshProfile]);
 *   [aBlankProfileNameIsRefused] pins `clean_name`'s own rule
 *   (`state/profiles.rs`), also checked before any row is written.
 * - the watch-state cases below are `state_db` reads/writes too
 *   (`state::rows`, `state::editors_choice`, `state::lists`,
 *   `state::preferences`) — progress, watched marks, the watchlist, Kids,
 *   the editor's choice, collections and preferences, each against a
 *   profile [freshProfile] made moments earlier,
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

    private companion object {
        /** Every grown-up's PIN in these cases. */
        const val PIN = "1234"
    }

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
    fun theFirstProfileRunsTheHouseholdAndASecondFirstIsRefused() {
        runBlocking {
            val core = core()
            val first = core.freshProfile("Alice")
            assertTrue(first.admin && first.hasPin && !first.kids && first.kidsAge == null)
            assertEquals(ProfileOutcome.NotAllowed, core.createFirstAdmin("Bert", PIN))
            assertEquals(listOf("Alice"), core.profiles().map { it.name })
        }
    }

    @Test
    fun aKidBelongsToTheGrownUpWhoMadeItAndOpensWithoutAPin() {
        runBlocking {
            val core = core()
            val admin = core.freshProfile("Ada")
            assertEquals(ProfileOutcome.Done, core.createKid(admin.id, PIN, "Kim", 6u))
            val kid = core.profiles().single { it.name == "Kim" }
            assertEquals(Profile(kid.id, "Kim", kids = true, kidsAge = 6u, parentId = admin.id), kid)
            assertEquals(ProfileOutcome.Done, core.unlockProfile(kid.id, ""))
            assertEquals(ProfileOutcome.Done, core.setKidsAge(admin.id, PIN, kid.id, 12u))
            assertEquals(12.toUByte(), core.profiles().single { it.id == kid.id }.kidsAge)
        }
    }

    /** Refused for the reasons, and in the order, the web refuses: input, name, somebody there, the PIN, the rule. */
    @Test
    fun aRefusalSaysWhyInTheWebsOrder() {
        runBlocking {
            val core = core()
            val admin = core.freshProfile("Ada")
            assertEquals(ProfileOutcome.Invalid, core.createKid(admin.id, PIN, "Kim", 7u))
            assertEquals(ProfileOutcome.NameTaken, core.createKid("nobody", PIN, " ADA ", 6u))
            assertEquals(ProfileOutcome.NotFound, core.createKid("nobody", PIN, "Kim", 6u))
            assertEquals(ProfileOutcome.WrongPin, core.createKid(admin.id, "12a4", "Kim", 6u))
            assertEquals(ProfileOutcome.NotAllowed, core.deleteProfile(admin.id, PIN, admin.id))
        }
    }

    @Test
    fun fiveWrongPinsMakeThatProfileWaitAndNoOtherOne() {
        runBlocking {
            val core = core()
            val admin = core.freshProfile("Ada")
            val other = core.freshProfile("Bo")
            repeat(5) { assertEquals(ProfileOutcome.WrongPin, core.unlockProfile(admin.id, "0000")) }
            val waiting = core.unlockProfile(admin.id, PIN)
            assertTrue(waiting is ProfileOutcome.Wait && waiting.seconds in 1u..60u, "$waiting")
            assertEquals(ProfileOutcome.Done, core.unlockProfile(other.id, PIN))
        }
    }

    @Test
    fun removingAGrownUpTakesTheKidsItIsTheParentOf() {
        runBlocking {
            val core = core()
            val admin = core.freshProfile("Ada")
            val parent = core.freshProfile("Bo")
            assertEquals(ProfileOutcome.Done, core.createKid(parent.id, PIN, "Kim", 12u))
            assertEquals(ProfileOutcome.Done, core.createKid(admin.id, PIN, "Lou", 12u))

            assertEquals(ProfileOutcome.Done, core.deleteProfile(admin.id, PIN, parent.id))

            assertEquals(listOf("Ada", "Lou"), core.profiles().map { it.name })
        }
    }

    @Test
    fun aFreshCoreHasNobodyToChoose() {
        runBlocking {
            val core = core()
            assertEquals(emptyList(), core.profiles())
            assertFalse(core.chooseProfile("nobody"))
            assertEquals(null, core.chosenProfile())
        }
    }

    /** A PIN that is not four digits is compared, fails and counts — it is never `Invalid`, which would say what shape the right one has. */
    @Test
    fun aMalformedPinIsWrongNotInvalid() {
        runBlocking {
            val core = core()
            val admin = core.freshProfile("Ada")
            assertEquals(ProfileOutcome.WrongPin, core.unlockProfile(admin.id, "12a4"))
            assertEquals(ProfileOutcome.WrongPin, core.createKid(admin.id, "", "Kim", 6u))
            assertEquals(ProfileOutcome.Done, core.unlockProfile(admin.id, PIN))
        }
    }

    /** A grown-up changes its own PIN, the admin anyone's; nobody else touches the admin's. */
    @Test
    fun aChangedPinIsTheOneThatOpens() {
        runBlocking {
            val core = core()
            val admin = core.freshProfile("Ada")
            val other = core.freshProfile("Bo")
            assertEquals(ProfileOutcome.NotAllowed, core.setPin(other.id, PIN, admin.id, "0000"))
            assertEquals(ProfileOutcome.Done, core.setPin(other.id, PIN, other.id, "5678"))
            assertEquals(ProfileOutcome.WrongPin, core.unlockProfile(other.id, PIN))
            assertEquals(ProfileOutcome.Done, core.setPin(admin.id, PIN, other.id, "2468"))
            assertEquals(ProfileOutcome.Done, core.unlockProfile(other.id, "2468"))
            assertEquals(ProfileOutcome.Invalid, core.setPin(admin.id, PIN, admin.id, "123"))
        }
    }

    /** Only a kid's own grown-up sets its limit or removes it — not even the admin, while that grown-up is here. */
    @Test
    fun aKidIsManagedByItsOwnGrownUpOnly() {
        runBlocking {
            val core = core()
            val admin = core.freshProfile("Ada")
            val parent = core.freshProfile("Bo")
            assertEquals(ProfileOutcome.Done, core.createKid(parent.id, PIN, "Kim", 12u))
            val kid = core.profiles().single { it.name == "Kim" }
            assertEquals(ProfileOutcome.NotAllowed, core.setKidsAge(admin.id, PIN, kid.id, 6u))
            assertEquals(ProfileOutcome.NotAllowed, core.deleteProfile(admin.id, PIN, kid.id))
            assertEquals(ProfileOutcome.NotAllowed, core.createKid(kid.id, "", "Lou", 6u))
            assertEquals(ProfileOutcome.Done, core.setKidsAge(parent.id, PIN, kid.id, 6u))
            assertEquals(ProfileOutcome.Done, core.deleteProfile(parent.id, PIN, kid.id))
            assertEquals(listOf("Ada", "Bo"), core.profiles().map { it.name })
        }
    }

    /** There is one admin; a second claim is refused before any PIN is compared. */
    @Test
    fun aClaimWhileThereIsAnAdminIsRefused() {
        runBlocking {
            val core = core()
            core.freshProfile("Ada")
            val other = core.freshProfile("Bo")
            assertEquals(ProfileOutcome.NotAllowed, core.claimAdmin(other.id, PIN))
            assertEquals(ProfileOutcome.NotFound, core.claimAdmin("nobody", PIN))
            assertEquals(ProfileOutcome.NotFound, core.unlockProfile("nobody", PIN))
            assertEquals(ProfileOutcome.NameTaken, core.createGrownUp(other.id, PIN, "bo", PIN))
            assertEquals(listOf("Ada"), core.profiles().filter { it.admin }.map { it.name })
        }
    }

    @Test
    fun aProfileThisCoreDoesNotHoldHasEarnedNothing() {
        runBlocking {
            assertEquals(Achievements(earned = emptyList(), next = emptyList()), core().achievements("no-such-profile", "2026-10-03", 120))
        }
    }

    @Test
    fun choosingACreatedProfileIsRememberedLocally() {
        runBlocking {
            val core = core()
            val created = core.freshProfile("Bea")
            assertTrue(core.chooseProfile(created.id))
            assertEquals(created.id, core.chosenProfile())
        }
    }

    @Test
    fun removingAProfileTakesItOffTheListAndForgetsTheChoice() {
        runBlocking {
            val core = core()
            val admin = core.freshProfile("Ada")
            val created = core.freshProfile("Chris")
            assertTrue(core.chooseProfile(created.id))
            assertEquals(ProfileOutcome.Done, core.deleteProfile(admin.id, PIN, created.id))
            assertTrue(core.profiles().none { it.id == created.id })
            assertEquals(null, core.chosenProfile())
        }
    }

    @Test
    fun aBlankProfileNameIsRefused() {
        runBlocking { assertEquals(ProfileOutcome.Invalid, core().createFirstAdmin("  ", PIN)) }
    }

    /**
     * A grown-up made the way the surface makes one: a household's first by
     * `createFirstAdmin`, every later one by that admin — all with [PIN].
     */
    private suspend fun CoreInterface.freshProfile(name: String): Profile {
        val admin = profiles().firstOrNull { it.admin }
        val made = if (admin == null) createFirstAdmin(name, PIN) else createGrownUp(admin.id, PIN, name, PIN)
        assertEquals(ProfileOutcome.Done, made, "a local profile must be creatable offline")
        return profiles().single { it.name == name }
    }

    @Test
    fun settingProgressTwiceKeepsOneRowWithTheNewestValue() {
        runBlocking {
            val core = core()
            val profile = core.freshProfile("Dana")
            core.setProgress(profile.id, "01A", 100.0, 900.0, "2026-10-03")
            core.setProgress(profile.id, "01A", 742.0, 900.0, "2026-10-03")
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
            core.setProgress(profile.id, "01A", -50.0, null, "2026-10-03")
            assertEquals(0.0, core.snapshot(profile.id).progress.single().at)
        }
    }

    @Test
    fun progressListsNewestFirst() {
        runBlocking {
            val core = core()
            val profile = core.freshProfile("Faye")
            core.setProgress(profile.id, "01A", 10.0, null, "2026-10-03")
            delay(5)
            core.setProgress(profile.id, "01B", 20.0, null, "2026-10-03")
            assertEquals(listOf("01B", "01A"), core.snapshot(profile.id).progress.map { it.setId })
        }
    }

    @Test
    fun clearingProgressForgetsThePositionWithoutMarkingItWatched() {
        runBlocking {
            val core = core()
            val profile = core.freshProfile("Gus")
            core.setProgress(profile.id, "01A", 300.0, null, "2026-10-03")
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
            core.setProgress(profile.id, "01A", 700.0, 1000.0, "2026-10-03")

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
            core.setProgress(profile.id, "01A", 300.0, null, "2026-10-03")

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
            core.setKids("01A", 12u)
            core.setKids("01A", 12u)
            assertEquals(listOf("01A"), core.snapshot(profile.id).kids)
        }
    }

    @Test
    fun removingATitleFromKidsThenAddingBackIsVisibleAgain() {
        runBlocking {
            val core = core()
            val profile = core.freshProfile("Noor")
            core.setKids("01A", 12u)
            core.setKids("01A", null)
            assertEquals(emptyList(), core.snapshot(profile.id).kids)

            core.setKids("01A", 12u)
            assertEquals(listOf("01A"), core.snapshot(profile.id).kids)
        }
    }

    /** `kidsFromSix` is the part of `kids` marked "from 6"; a mark moved to 12 leaves it. */
    @Test
    fun aKidsMarkSaysFromSixOrFromTwelve() {
        runBlocking {
            val core = core()
            val profile = core.freshProfile("Nell")
            core.setKids("01A", 6u)
            core.setKids("01B", 12u)
            val marked = core.snapshot(profile.id)
            assertEquals(setOf("01A", "01B"), marked.kids.toSet())
            assertEquals(listOf("01A"), marked.kidsFromSix)

            core.setKids("01A", 12u)
            assertEquals(emptyList(), core.snapshot(profile.id).kidsFromSix)
            assertEquals(setOf("01A", "01B"), core.snapshot(profile.id).kids.toSet())
        }
    }

    @Test
    fun kidsMarksAreSharedAcrossEveryProfile() {
        runBlocking {
            val core = core()
            val parent = core.freshProfile("Omar")
            val child = core.freshProfile("Pia")

            core.setKids("01A", 12u)

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
            val admin = core.freshProfile("Ada")
            val profile = core.freshProfile("Wren")
            core.setProgress(profile.id, "01A", 300.0, null, "2026-10-03")
            core.setWatchlisted(profile.id, "01B", true)
            assertTrue(core.setPreference(profile.id, "show:Dark", "audio", "de"))

            assertEquals(ProfileOutcome.Done, core.deleteProfile(admin.id, PIN, profile.id))

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
            core.setProgress("no-such-profile", "01A", 300.0, null, "2026-10-03")
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
