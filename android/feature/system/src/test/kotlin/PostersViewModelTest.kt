package system

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import settings.InMemoryTmdbSettings
import uniffi.mediagram_core.CoreException
import uniffi.mediagram_core.PosterReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostersViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun aFreshInstallHasNoKeyStored() = runTest {
        val viewModel = PostersViewModel(FakeCoreProvider(FakeCore()), InMemoryTmdbSettings())

        assertFalse(viewModel.state.value.hasKey)
    }

    @Test
    fun savingAKeyIsReflectedWithoutEverBeingHeldItself() = runTest {
        val settings = InMemoryTmdbSettings()
        val viewModel = PostersViewModel(FakeCoreProvider(FakeCore()), settings)

        viewModel.saveKey("a-fake-key")

        assertTrue(viewModel.state.value.hasKey)
    }

    @Test
    fun fetchingWithNoKeyStoredDoesNothing() = runTest {
        val core = FakeCore()
        val viewModel = PostersViewModel(FakeCoreProvider(core), InMemoryTmdbSettings())

        viewModel.fetch()

        assertNull(core.lastKey)
        assertNull(viewModel.state.value.report)
    }

    @Test
    fun aSuccessfulFetchReportsWhatCameBack() = runTest {
        val settings = InMemoryTmdbSettings().apply { write("a-fake-key") }
        val core = FakeCore(report = PosterReport(2u, 7u, 0u, 0u))
        val viewModel = PostersViewModel(FakeCoreProvider(core), settings)

        viewModel.fetch()

        assertEquals("a-fake-key", core.lastKey)
        assertEquals(PosterReport(2u, 7u, 0u, 0u), viewModel.state.value.report)
        assertFalse(viewModel.state.value.running)
    }

    /**
     * The real regression this guards against: a fake that returns
     * immediately can never be caught mid-flight, so [FakeCore.gate] holds
     * the first call suspended while a second [PostersViewModel.fetch] call
     * is made against it — the only way to prove the in-flight guard, not
     * merely the no-key one, actually refuses a second run.
     */
    @Test
    fun aSecondFetchWhileOneIsInFlightIsRefused() = runTest {
        val settings = InMemoryTmdbSettings().apply { write("a-fake-key") }
        val gate = CompletableDeferred<Unit>()
        val core = FakeCore(report = PosterReport(1u, 0u, 0u, 0u), gate = gate)
        val viewModel = PostersViewModel(FakeCoreProvider(core), settings)

        viewModel.fetch()
        assertTrue(viewModel.state.value.running)

        viewModel.fetch()

        assertEquals(1, core.fetchCalls)

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.running)
        assertEquals(PosterReport(1u, 0u, 0u, 0u), viewModel.state.value.report)
    }

    /** A rejected key is named as such — never quoted back, and never mistaken for a network fault. */
    @Test
    fun aRejectedKeyNamesTheKeyRatherThanTheNetwork() = runTest {
        val settings = InMemoryTmdbSettings().apply { write("a-wrong-key") }
        val core = FakeCore(failure = CoreException.NotAuthorized("That key was not accepted."))
        val viewModel = PostersViewModel(FakeCoreProvider(core), settings)

        viewModel.fetch()

        assertEquals("That key was not accepted.", viewModel.state.value.error)
        assertNull(viewModel.state.value.report)
    }

    @Test
    fun dismissingAResultClearsItWithoutTouchingWhetherAKeyIsStored() = runTest {
        val settings = InMemoryTmdbSettings().apply { write("a-fake-key") }
        val viewModel = PostersViewModel(FakeCoreProvider(FakeCore(report = PosterReport(1u, 0u, 0u, 0u))), settings)
        viewModel.fetch()

        viewModel.dismissResult()

        assertNull(viewModel.state.value.report)
        assertTrue(viewModel.state.value.hasKey)
    }
}
