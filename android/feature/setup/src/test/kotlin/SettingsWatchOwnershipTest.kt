package setup

import data.CoreClient
import data.DefaultWatchSync
import data.LibraryEvents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import uniffi.mediagram_core.Profile
import uniffi.mediagram_core.StateSnapshot
import uniffi.mediagram_core.SyncOutcome
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class WatchCore : CoreClient by FakeCore(authorized = true) {
    val writes = mutableListOf<String>()
    var syncs = 0
    var readFailure: Exception? = null
    var accountFailure: Exception? = null

    override suspend fun account(): uniffi.mediagram_core.AccountSummary {
        accountFailure?.let { throw it }
        return uniffi.mediagram_core.AccountSummary("Viewer", null)
    }

    override suspend fun profiles(): List<Profile> {
        readFailure?.let { throw it }
        return listOf(Profile("viewer", "Viewer"))
    }

    override suspend fun chosenProfile() = "viewer"

    override suspend fun snapshot(profileId: String) = StateSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null)

    override suspend fun setProgress(
        profileId: String,
        setId: String,
        at: Double,
        duration: Double?,
    ) {
        writes += "progress:$profileId:$setId"
    }

    override suspend fun renameCollection(
        profileId: String,
        id: String,
        name: String,
    ): Boolean {
        writes += "rename:$profileId:$id"
        return true
    }

    override suspend fun syncState(handle: String): SyncOutcome {
        syncs++
        return SyncOutcome(0u, false, null)
    }
}

class SettingsWatchOwnershipTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun acceptedReplacementRebindsSelectedProfileWithoutAProfileScreenRestartOrIncomingSync() =
        runTest {
            val first = WatchCore()
            val replacement = WatchCore()
            val cores = ArrayDeque(listOf(first, replacement))
            val fixture = SetupFixture(build = { cores.removeFirst() }).signedIn()
            fixture.library.write("films")
            fixture.watchState.reload()
            val viewModel = fixture.settingsViewModel()

            viewModel.changeApplication("5678", "fedcba9876543210fedcba9876543210")
            val sync = DefaultWatchSync(fixture.provider, fixture.library, fixture.watchState, LibraryEvents.None, backgroundScope)
            sync.awaitFirstRound()
            fixture.watchState.setProgress("film", 12.0, 100.0)
            assertTrue(fixture.watchState.renameList("list", "Weekend"))
            assertEquals(1, replacement.syncs)
            assertEquals(listOf("progress:viewer:film", "rename:viewer:list"), replacement.writes)
            assertEquals(emptyList(), first.writes)
            assertEquals(
                SettingsEvent.ApplicationChanged,
                viewModel.completions.value
                    .single()
                    .event,
            )
        }

    @Test fun failedProfileReconciliationRetriesOnlyTheReadAndKeepsAcceptedCredentials() =
        runTest {
            val first = WatchCore()
            val replacement = WatchCore().apply { readFailure = IOException("private database path") }
            val cores = ArrayDeque(listOf(first, replacement))
            val fixture = SetupFixture(build = { cores.removeFirst() }).signedIn()
            fixture.watchState.reload()
            val model = fixture.settingsViewModel()
            model.changeApplication("5678", "fedcba9876543210fedcba9876543210")
            assertSame(replacement, fixture.provider.core.value)
            assertEquals(5678, fixture.telegram.read()?.apiId)
            assertEquals(5678, model.state.value.apiId)
            assertTrue(model.state.value.profileReloadNeeded)
            assertFalse(model.state.value.busy)
            assertEquals("Application identity changed, but profiles could not be loaded. Try again.", model.state.value.notice)
            assertTrue(model.completions.value.isEmpty())
            model.refresh()
            assertTrue(model.state.value.profileReloadNeeded)
            model.changeApplication("9999", WELL_FORMED_HASH)
            assertEquals(5678, fixture.telegram.read()?.apiId)
            model.retryProfiles()
            assertTrue(model.completions.value.isEmpty())
            replacement.readFailure = null
            model.retryProfiles()
            assertFalse(model.state.value.profileReloadNeeded)
            assertTrue(fixture.watchState.renameList("list", "Weekend"))
            assertEquals(listOf("rename:viewer:list"), replacement.writes)
            assertEquals(
                SettingsEvent.ApplicationChanged,
                model.completions.value
                    .single()
                    .event,
            )
            model.retryProfiles()
            assertEquals(1, model.completions.value.size)
            assertTrue(cores.isEmpty())
        }

    @Test fun cancelledProfileReadKeepsASeparateRetryWithoutReportingARefusedIdentity() =
        runTest {
            val first = WatchCore()
            val replacement = WatchCore().apply { readFailure = CancellationException("leaving") }
            val cores = ArrayDeque(listOf(first, replacement))
            val fixture = SetupFixture(build = { cores.removeFirst() }).signedIn()
            fixture.watchState.reload()
            val model = fixture.settingsViewModel()
            model.changeApplication("5678", WELL_FORMED_HASH)
            assertFalse(model.state.value.busy)
            assertEquals(null, model.state.value.notice)
            assertTrue(model.state.value.profileReloadNeeded)
            assertTrue(model.completions.value.isEmpty())
            assertEquals(5678, fixture.telegram.read()?.apiId)
            replacement.readFailure = null
            model.retryProfiles()
            assertFalse(model.state.value.profileReloadNeeded)
            assertEquals(
                SettingsEvent.ApplicationChanged,
                model.completions.value
                    .single()
                    .event,
            )
            assertTrue(fixture.watchState.renameList("list", "Weekend"))
        }

    @Test fun refusedReplacementRestoresWatchOwnershipFromStoredCredentialsWithoutClaimingSuccess() =
        runTest {
            val first = WatchCore()
            val refused = WatchCore().apply { accountFailure = IOException("private verification details") }
            val restored = WatchCore()
            val cores = ArrayDeque(listOf(first, refused, restored))
            val fixture = SetupFixture(build = { cores.removeFirst() }).signedIn()
            fixture.watchState.reload()
            val model = fixture.settingsViewModel()
            model.changeApplication("5678", WELL_FORMED_HASH)
            assertTrue(fixture.watchState.renameList("list", "Weekend"))
            assertSame(restored, fixture.provider.core.value)
            assertEquals(listOf("rename:viewer:list"), restored.writes)
            assertTrue(first.writes.isEmpty())
            assertEquals(1234, fixture.telegram.read()?.apiId)
            assertEquals("The application identity could not be changed. Try again.", model.state.value.notice)
            assertFalse(model.state.value.profileReloadNeeded)
            assertTrue(model.completions.value.isEmpty())
        }

    @Test fun refusedReplacementRetainsTheRefusalWhenRestoringProfilesAlsoFails() =
        runTest {
            val first = WatchCore()
            val refused = WatchCore().apply { accountFailure = IOException("private verification details") }
            val restored = WatchCore().apply { readFailure = IOException("private-state-path") }
            val cores = ArrayDeque(listOf(first, refused, restored))
            val fixture = SetupFixture(build = { cores.removeFirst() }).signedIn()
            fixture.watchState.reload()
            val model = fixture.settingsViewModel()
            model.changeApplication("5678", WELL_FORMED_HASH)
            assertEquals("The application identity could not be changed. Try again.", model.state.value.notice)
            assertTrue(model.state.value.profileReloadNeeded)
            assertTrue(model.completions.value.isEmpty())
            restored.readFailure = null
            model.retryProfiles()
            assertFalse(model.state.value.profileReloadNeeded)
            assertTrue(model.completions.value.isEmpty())
            assertEquals(1234, model.state.value.apiId)
            assertTrue(fixture.watchState.renameList("list", "Weekend"))
            assertTrue(cores.isEmpty())
        }

    @Test fun cancelledReplacementLeavesWatchRecoveryAvailableWithoutClaimingFailureOrSuccess() =
        runTest {
            val first = WatchCore()
            val cancelled = WatchCore().apply { accountFailure = CancellationException("leaving") }
            val restored = WatchCore()
            val cores = ArrayDeque(listOf(first, cancelled, restored))
            val fixture = SetupFixture(build = { cores.removeFirst() }).signedIn()
            fixture.watchState.reload()
            val model = fixture.settingsViewModel()
            model.changeApplication("5678", WELL_FORMED_HASH)
            assertFalse(model.state.value.busy)
            assertEquals(null, model.state.value.notice)
            assertTrue(model.state.value.profileReloadNeeded)
            assertTrue(model.completions.value.isEmpty())
            model.retryProfiles()
            assertFalse(model.state.value.profileReloadNeeded)
            assertTrue(model.completions.value.isEmpty())
            assertEquals(1234, model.state.value.apiId)
            assertTrue(fixture.watchState.renameList("list", "Weekend"))
        }
}
