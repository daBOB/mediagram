package setup

import app.cash.turbine.test
import data.InMemoryCoreStorage
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import settings.TelegramCredentials
import uniffi.mediagram_core.LibraryChoice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val OTHER_HASH = "fedcba9876543210fedcba9876543210"

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val libraries = listOf(LibraryChoice("h1", "Mediagram"), LibraryChoice("h2", "Films"))

    private suspend fun signedInWithLibrary(core: FakeCore = FakeCore(libraries = libraries)) =
        SetupFixture(core = core).signedIn().also { it.library.write("h1") }

    @Test
    fun theRowsSayWhoWhereAndWhichLibrary() = runTest {
        val state = signedInWithLibrary().settingsViewModel().state.value

        assertEquals("A Viewer (@viewer)", state.account)
        assertEquals("Mediagram", state.library)
        assertEquals("DC 4", state.datacenter)
        assertEquals("Signed in; Telegram answered", state.connection)
        assertEquals(1234, state.apiId)
    }

    /** A row that cannot be answered says so, rather than sitting empty like one still asking. */
    @Test
    fun anUnreachableTelegramIsSaidRatherThanLeftBlank() = runTest {
        val core = FakeCore(libraries = libraries, accountAnswer = null)
        val state = signedInWithLibrary(core).settingsViewModel().state.value

        assertEquals("—", state.account)
        assertEquals("Telegram did not answer", state.connection)
    }

    /**
     * Signing out ends the login at Telegram and drops everything that was
     * the account's — catalog, channel names, chosen library — but keeps what
     * is this device's: the application identity and the TMDB key.
     */
    @Test
    fun signingOutKeepsTheDevicesOwnAndDropsTheAccounts() = runTest {
        val fixture = signedInWithLibrary()
        fixture.tmdb.write("a-tmdb-key-0123456789abcdef0123")
        val viewModel = fixture.settingsViewModel()

        viewModel.events.test {
            viewModel.signOut()
            assertEquals(SettingsEvent.SignedOut, awaitItem())
        }

        assertTrue(fixture.core.signedOut, "the login ends at Telegram, not only on this device")
        assertTrue((fixture.storage as InMemoryCoreStorage).cleared)
        assertEquals(null, fixture.library.read())
        assertEquals(TelegramCredentials(1234, WELL_FORMED_HASH), fixture.telegram.read())
    }

    @Test
    fun choosingAnotherLibraryInstallsItThenTellsTheShelves() = runTest {
        val fixture = signedInWithLibrary()
        val viewModel = fixture.settingsViewModel()

        viewModel.events.test {
            viewModel.chooseLibrary("h2")
            assertEquals(SettingsEvent.LibraryChanged, awaitItem())
        }

        assertEquals("h2", fixture.core.installedHandle)
        assertEquals("h2", fixture.library.read())
        assertEquals("Films", viewModel.state.value.library)
    }

    @Test
    fun aMalformedIdentityIsRefusedBeforeAnythingIsTried() = runTest {
        val fixture = signedInWithLibrary()
        val viewModel = fixture.settingsViewModel()

        viewModel.changeApplication("not a number", WELL_FORMED_HASH)

        assertEquals(API_ID_ERROR, viewModel.state.value.notice)
        assertEquals(TelegramCredentials(1234, WELL_FORMED_HASH), fixture.telegram.read())
    }

    /** An identity Telegram will not answer through is not kept, and the one in use stays. */
    @Test
    fun anIdentityTelegramRefusesLeavesThePreviousOneInUse() = runTest {
        val core = FakeCore(libraries = libraries)
        val fixture = signedInWithLibrary(core)
        val viewModel = fixture.settingsViewModel()
        core.accountAnswer = null

        viewModel.changeApplication("5678", OTHER_HASH)

        assertTrue(viewModel.state.value.notice!!.startsWith("Telegram did not accept"))
        assertFalse(viewModel.state.value.busy)
        assertEquals(TelegramCredentials(1234, WELL_FORMED_HASH), fixture.telegram.read())
    }
}
