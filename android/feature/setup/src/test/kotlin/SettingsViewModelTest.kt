package setup

import app.cash.turbine.test
import data.InMemoryCoreStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
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
    fun theRowsSayWhoWhereAndWhichLibrary() =
        runTest {
            val state = signedInWithLibrary().settingsViewModel().state.value

            assertEquals("A Viewer (@viewer)", state.account)
            assertEquals("Mediagram", state.library)
            assertEquals("DC 4", state.datacenter)
            assertEquals("Signed in; Telegram answered", state.connection)
            assertEquals(1234, state.apiId)
        }

    /** A row that cannot be answered says so, rather than sitting empty like one still asking. */
    @Test
    fun anUnreachableTelegramIsSaidRatherThanLeftBlank() =
        runTest {
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
    fun signingOutKeepsTheDevicesOwnAndDropsTheAccounts() =
        runTest {
            val fixture = signedInWithLibrary()
            fixture.tmdb.write("a-tmdb-key-0123456789abcdef0123")
            val viewModel = fixture.settingsViewModel()

            viewModel.completions.test {
                assertEquals(emptyList(), awaitItem())
                viewModel.signOut()
                assertEquals(SettingsEvent.SignedOut, awaitItem().single().event)
            }

            assertTrue(fixture.core.signedOut, "the login ends at Telegram, not only on this device")
            assertTrue((fixture.storage as InMemoryCoreStorage).cleared)
            assertEquals(null, fixture.library.read())
            assertEquals(TelegramCredentials(1234, WELL_FORMED_HASH), fixture.telegram.read())
        }

    @Test
    fun choosingAnotherLibraryInstallsItThenTellsTheShelves() =
        runTest {
            val fixture = signedInWithLibrary()
            val viewModel = fixture.settingsViewModel()

            viewModel.completions.test {
                assertEquals(emptyList(), awaitItem())
                viewModel.chooseLibrary("h2")
                assertEquals(SettingsEvent.LibraryChanged, awaitItem().single().event)
            }

            assertEquals("h2", fixture.core.installedHandle)
            assertEquals("h2", fixture.library.read())
            assertEquals("Films", viewModel.state.value.library)
        }

    @Test
    fun aMalformedIdentityIsRefusedBeforeAnythingIsTried() =
        runTest {
            val fixture = signedInWithLibrary()
            val viewModel = fixture.settingsViewModel()

            viewModel.changeApplication("not a number", WELL_FORMED_HASH)

            assertEquals(API_ID_ERROR, viewModel.state.value.notice)
            assertEquals(TelegramCredentials(1234, WELL_FORMED_HASH), fixture.telegram.read())
        }

    /** An unverified replacement does not imply Telegram rejected the application id. */
    @Test
    fun anUnverifiedIdentityKeepsThePreviousCredentials() =
        runTest {
            val core = FakeCore(libraries = libraries)
            val fixture = signedInWithLibrary(core)
            val viewModel = fixture.settingsViewModel()
            core.accountAnswer = null

            viewModel.changeApplication("5678", OTHER_HASH)

            assertTrue(
                viewModel.state.value.notice!!
                    .startsWith("The application identity could not be changed"),
            )
            assertFalse(viewModel.state.value.busy)
            assertEquals(TelegramCredentials(1234, WELL_FORMED_HASH), fixture.telegram.read())
        }

    @Test
    fun cancellingTheAccountLookupStopsTheRemainingRows() =
        runTest {
            val core = FakeCore(libraries = libraries, accountFailure = CancellationException("screen left"))
            val viewModel = signedInWithLibrary(core).settingsViewModel()

            assertEquals(0, core.listCalls)
            assertEquals(0, core.datacenterReads)
            assertEquals(null, viewModel.state.value.account)
            assertEquals(null, viewModel.state.value.notice)
            assertFalse(viewModel.state.value.busy)
        }

    @Test
    fun cancellingTheLibraryTitleLookupStopsTheRemainingRows() =
        runTest {
            val core = FakeCore(libraries = libraries, listFailure = CancellationException("screen left"))
            val viewModel = signedInWithLibrary(core).settingsViewModel()

            assertEquals(1, core.listCalls)
            assertEquals(0, core.datacenterReads)
            assertEquals(null, viewModel.state.value.account)
            assertEquals(null, viewModel.state.value.notice)
            assertFalse(viewModel.state.value.busy)
        }

    @Test
    fun aLibraryChangeWaitsForTheReturningScreen() =
        runTest {
            val viewModel = signedInWithLibrary().settingsViewModel()
            viewModel.chooseLibrary("h2")

            val completion =
                withTimeoutOrNull(100) {
                    viewModel.completions
                        .first { it.isNotEmpty() }
                        .single()
                        .event
                }
            assertEquals(SettingsEvent.LibraryChanged, completion)
        }

    @Test
    fun signOutWaitsForTheReturningScreen() =
        runTest {
            val viewModel = signedInWithLibrary().settingsViewModel()
            viewModel.signOut()

            val completion =
                withTimeoutOrNull(100) {
                    viewModel.completions
                        .first { it.isNotEmpty() }
                        .single()
                        .event
                }
            assertEquals(SettingsEvent.SignedOut, completion)
        }

    @Test
    fun acknowledgingAnOlderCompletionKeepsLaterActionsPending() =
        runTest {
            val viewModel = signedInWithLibrary().settingsViewModel()
            viewModel.chooseLibrary("h2")
            val first = viewModel.completions.value.single()
            viewModel.chooseLibrary("h1")
            val second = viewModel.completions.value.last()

            viewModel.acknowledgeCompletion(first.id)

            assertEquals(listOf(second), viewModel.completions.value)
            viewModel.acknowledgeCompletion(first.id)
            assertEquals(listOf(second), viewModel.completions.value)
            viewModel.acknowledgeCompletion(second.id)
            assertEquals(emptyList(), viewModel.completions.value)
            assertEquals(second.id, viewModel.state.value.completedActionId)
        }

    @Test
    fun refreshingRowsKeepsBothTheCompletionAndFormSuccessMarker() =
        runTest {
            val viewModel = signedInWithLibrary().settingsViewModel()
            viewModel.chooseLibrary("h2")
            val completion = viewModel.completions.value.single()

            viewModel.refresh()

            assertEquals(listOf(completion), viewModel.completions.value)
            assertEquals(completion.id, viewModel.state.value.completedActionId)
            viewModel.acknowledgeCompletion(completion.id)
            viewModel.refresh()
            assertEquals(emptyList(), viewModel.completions.value)
            assertEquals(completion.id, viewModel.state.value.completedActionId)
        }

    @Test
    fun aFailedInstallDoesNotCloseTheFormOrRequestNavigation() =
        runTest {
            val core = FakeCore(libraries = libraries, installFailure = IllegalStateException("offline"))
            val fixture = signedInWithLibrary(core)
            val viewModel = fixture.settingsViewModel()
            viewModel.chooseLibrary("h2")

            assertEquals(emptyList(), viewModel.completions.value)
            assertEquals(0, viewModel.state.value.completedActionId)
            assertEquals("h1", fixture.library.read())
            assertTrue(viewModel.state.value.notice != null)
        }

    @Test
    fun changingApplicationRetainsSuccessDespiteUnavailableRows() =
        runTest {
            val core = FakeCore(libraries = libraries)
            val viewModel = signedInWithLibrary(core).settingsViewModel()
            core.listFailure = IllegalStateException("offline")

            viewModel.changeApplication("5678", OTHER_HASH)

            val completion = viewModel.completions.value.single()
            assertEquals(SettingsEvent.ApplicationChanged, completion.event)
            assertEquals(completion.id, viewModel.state.value.completedActionId)
            assertEquals("—", viewModel.state.value.library)
            assertEquals(null, viewModel.state.value.notice)
            assertFalse(viewModel.state.value.busy)
        }
}
