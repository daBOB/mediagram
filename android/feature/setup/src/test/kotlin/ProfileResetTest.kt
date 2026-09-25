package setup

import data.CoreClient
import data.InMemoryCoreStorage
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import uniffi.mediagram_core.Profile
import uniffi.mediagram_core.StateSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileResetTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private fun fixture(storage: InMemoryCoreStorage = InMemoryCoreStorage()): SetupFixture {
        val raw = FakeCore(authorized = true)
        val core =
            object : CoreClient by raw {
                override suspend fun profiles() = listOf(Profile("alice", "Alice"))

                override suspend fun chosenProfile() = "alice"

                override suspend fun snapshot(profileId: String) =
                    StateSnapshot(emptyList(), emptyList(), listOf("film"), emptyList(), emptyList(), null)
            }
        return SetupFixture(core = raw, storage = storage, build = { core })
    }

    @Test
    fun signingOutReopensFreshProfileStorageWithTheSameDeviceCredentials() =
        runTest {
            var closed = false
            var builds = 0
            val first =
                object : CoreClient by FakeCore(authorized = true) {
                    override suspend fun profiles() = listOf(Profile("alice", "Alice"))

                    override suspend fun chosenProfile() = "alice"

                    override fun close() {
                        closed = true
                    }
                }
            val fixture = SetupFixture(build = { if (builds++ == 0) first else FakeCore() }).signedIn()
            fixture.watchState.reload()
            val vm = fixture.settingsViewModel()
            vm.signOut()
            assertTrue(closed)
            fixture.watchState.reload()
            assertNull(fixture.watchState.chosenProfileId.value)
            assertEquals(2, builds)
            assertEquals(1234, fixture.telegram.read()?.apiId)
            assertEquals(
                SettingsEvent.SignedOut,
                vm.completions.value
                    .single()
                    .event,
            )
        }

    @Test fun successfulSignOutClearsTheRetainedProfileBeforeNavigation() =
        runTest {
            val fixture = fixture().signedIn()
            fixture.watchState.reload()
            val vm = fixture.settingsViewModel()
            vm.signOut()
            assertEquals(
                SettingsEvent.SignedOut,
                vm.completions.value
                    .single()
                    .event,
            )
            assertNull(fixture.watchState.chosenProfileId.value)
        }

    @Test fun successfulStartOverClearsTheRetainedProfileBeforeSetup() =
        runTest {
            val fixture = fixture().signedIn()
            fixture.watchState.reload()
            val vm = fixture.viewModel()
            vm.startOver()
            assertIs<SetupUiState.NeedsApplication>(vm.state.value)
            assertNull(fixture.watchState.chosenProfileId.value)
        }

    @Test fun failedStorageResetDoesNotClaimThatTheProfileWasCleared() =
        runTest {
            val fixture = fixture(InMemoryCoreStorage(IllegalStateException("refused"))).signedIn()
            fixture.watchState.reload()
            val vm = fixture.settingsViewModel()
            vm.signOut()
            assertEquals(emptyList(), vm.completions.value)
            assertEquals("alice", fixture.watchState.chosenProfileId.value)
        }
}
