package setup

import data.CoreClient
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import settings.InMemoryTelegramSettings
import settings.TelegramCredentials
import settings.TelegramSettings
import uniffi.mediagram_core.CoreException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private const val NEW_HASH = "fedcba9876543210fedcba9876543210"

class SettingsIdentityFailureTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun failureClosingTheOldCoreDoesNotClaimTelegramRejectedTheIdentity() =
        runTest {
            val core = FakeCore()
            val fixture =
                SetupFixture(core = core, build = {
                    object : CoreClient by core {
                        override fun close(): Unit = throw IllegalStateException("local close failed")
                    }
                }).signedIn()

            assertFailedReplacement(fixture, fixture.settingsViewModel())
        }

    @Test
    fun failureConstructingTheReplacementDoesNotClaimTelegramRejectedTheIdentity() =
        runTest {
            val core = FakeCore()
            var builds = 0
            val fixture =
                SetupFixture(core = core, build = {
                    if (++builds > 1) throw IllegalStateException("native construction failed")
                    core
                }).signedIn()

            assertFailedReplacement(fixture, fixture.settingsViewModel())
            assertEquals(2, builds)
        }

    @Test
    fun failurePersistingVerifiedCredentialsDoesNotClaimTelegramRejectedTheIdentity() =
        runTest {
            val stored = InMemoryTelegramSettings()
            var rejectWrite = false
            val settings =
                object : TelegramSettings by stored {
                    override suspend fun write(
                        apiId: Int,
                        apiHash: String,
                    ) {
                        if (rejectWrite) throw SecurityException("keystore unavailable")
                        stored.write(apiId, apiHash)
                    }
                }
            val fixture = SetupFixture(telegram = settings).signedIn()
            val viewModel = fixture.settingsViewModel()
            rejectWrite = true

            assertFailedReplacement(fixture, viewModel)
        }

    @Test
    fun anUnclassifiedTelegramFailureDoesNotInventItsCause() =
        runTest {
            val fixture = SetupFixture().signedIn()
            val viewModel = fixture.settingsViewModel()
            fixture.core.accountFailure = CoreException.Network("RPC verification failed")

            assertFailedReplacement(fixture, viewModel)
        }

    private suspend fun assertFailedReplacement(
        fixture: SetupFixture,
        viewModel: SettingsViewModel,
    ) {
        viewModel.changeApplication("5678", NEW_HASH)

        assertEquals("The application identity could not be changed. Try again.", viewModel.state.value.notice)
        assertFalse(viewModel.state.value.busy)
        assertEquals(emptyList(), viewModel.completions.value)
        assertEquals(0, viewModel.state.value.completedActionId)
        assertEquals(TelegramCredentials(1234, WELL_FORMED_HASH), fixture.telegram.read())
    }
}
