package testing

import kotlinx.coroutines.runBlocking
import org.junit.Test
import uniffi.mediagram_core.CoreException
import uniffi.mediagram_core.CoreInterface
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
 * - the profile cases are `state_db` reads/writes only
 *   (`api::state::{profiles,create_profile,choose_profile,delete_profile}`);
 *   [aBlankProfileNameIsRefused] pins `clean_name`'s own rule
 *   (`state/profiles.rs`), also checked before any row is written.
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
}
