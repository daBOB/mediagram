package system

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import settings.InMemoryTmdbSettings
import uniffi.mediagram_core.CoreException
import uniffi.mediagram_core.FetchReport
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FetchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun aFreshInstallHasNoKeyStored() = runTest {
        val viewModel = FetchViewModel(FakeCoreProvider(FakeCore()), InMemoryTmdbSettings())

        assertFalse(viewModel.state.value.hasKey)
    }

    @Test
    fun savingAKeyIsReflectedWithoutEverBeingHeldItself() = runTest {
        val settings = InMemoryTmdbSettings()
        val viewModel = FetchViewModel(FakeCoreProvider(FakeCore()), settings)

        viewModel.saveKey("a-fake-key")

        assertTrue(viewModel.state.value.hasKey)
    }

    @Test
    fun fetchingWithNoKeyStoredDoesNothing() = runTest {
        val core = FakeCore()
        val viewModel = FetchViewModel(FakeCoreProvider(core), InMemoryTmdbSettings())

        viewModel.fetch()

        assertNull(core.lastKey)
        assertNull(viewModel.state.value.report)
    }

    @Test
    fun aSuccessfulFetchReportsWhatCameBack() = runTest {
        val settings = InMemoryTmdbSettings().apply { write("a-fake-key") }
        val core = FakeCore(report = FetchReport(2u, 7u, 5u, 1u, 0u, 0u))
        val viewModel = FetchViewModel(FakeCoreProvider(core), settings)

        viewModel.fetch()

        assertEquals("a-fake-key", core.lastKey)
        assertEquals(FetchReport(2u, 7u, 5u, 1u, 0u, 0u), viewModel.state.value.report)
        assertFalse(viewModel.state.value.running)
    }

    /**
     * The defect this closes: the fallback language used to be the constant
     * `en-US`, so a library with nothing to say about its own language was
     * described to its owner in a language they may not read. The device
     * knows better, and is the only thing here that does.
     */
    @Test
    fun theFallbackLanguageIsTheDevicesOwn() = runTest {
        val settings = InMemoryTmdbSettings().apply { write("a-fake-key") }
        val core = FakeCore()
        val viewModel = FetchViewModel(FakeCoreProvider(core), settings)

        viewModel.fetch()

        assertEquals(Locale.getDefault().toLanguageTag(), core.lastLanguage)
    }

    /**
     * The real regression this guards against: a fake that returns
     * immediately can never be caught mid-flight, so [FakeCore.gate] holds
     * the first call suspended while a second [FetchViewModel.fetch] call is
     * made against it — the only way to prove the in-flight guard, not merely
     * the no-key one, actually refuses a second run.
     */
    @Test
    fun aSecondFetchWhileOneIsInFlightIsRefused() = runTest {
        val settings = InMemoryTmdbSettings().apply { write("a-fake-key") }
        val gate = CompletableDeferred<Unit>()
        val core = FakeCore(report = FetchReport(1u, 0u, 0u, 0u, 0u, 0u), gate = gate)
        val viewModel = FetchViewModel(FakeCoreProvider(core), settings)

        viewModel.fetch()
        assertTrue(viewModel.state.value.running)

        viewModel.fetch()

        assertEquals(1, core.fetchCalls)

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.running)
        assertEquals(FetchReport(1u, 0u, 0u, 0u, 0u, 0u), viewModel.state.value.report)
    }

    /** A rejected key is named as such — never quoted back, and never mistaken for a network fault. */
    @Test
    fun aRejectedKeyNamesTheKeyRatherThanTheNetwork() = runTest {
        val settings = InMemoryTmdbSettings().apply { write("a-wrong-key") }
        val core = FakeCore(failure = CoreException.NotAuthorized("That key was not accepted."))
        val viewModel = FetchViewModel(FakeCoreProvider(core), settings)

        viewModel.fetch()

        assertEquals("That key was not accepted.", viewModel.state.value.error)
        assertNull(viewModel.state.value.report)
    }

    @Test
    fun dismissingAResultClearsItWithoutTouchingWhetherAKeyIsStored() = runTest {
        val settings = InMemoryTmdbSettings().apply { write("a-fake-key") }
        val core = FakeCore(report = FetchReport(1u, 0u, 0u, 0u, 0u, 0u))
        val viewModel = FetchViewModel(FakeCoreProvider(core), settings)
        viewModel.fetch()

        viewModel.dismissResult()

        assertNull(viewModel.state.value.report)
        assertTrue(viewModel.state.value.hasKey)
    }

    /** The fetch new media brings is nobody's request: it does its work and says nothing. */
    @Test
    fun aQuietFetchFillsInWithoutAReport() = runTest {
        val settings = InMemoryTmdbSettings().apply { write("a-fake-key") }
        val core = FakeCore(report = FetchReport(2u, 7u, 5u, 1u, 0u, 0u))
        val viewModel = FetchViewModel(FakeCoreProvider(core), settings)

        viewModel.fetch(quiet = true)

        assertEquals("a-fake-key", core.lastKey)
        assertNull(viewModel.state.value.report)
        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.running)
    }

    /** A result the viewer has not dismissed yet is theirs; a quiet fetch neither clears nor replaces it. */
    @Test
    fun aQuietFetchLeavesAResultStillOnScreen() = runTest {
        val settings = InMemoryTmdbSettings().apply { write("a-fake-key") }
        val core = FakeCore(report = FetchReport(1u, 0u, 0u, 0u, 0u, 0u))
        val viewModel = FetchViewModel(FakeCoreProvider(core), settings)
        viewModel.fetch()

        viewModel.fetch(quiet = true)

        assertEquals(FetchReport(1u, 0u, 0u, 0u, 0u, 0u), viewModel.state.value.report)
    }

    /** New artwork on disk is what the shelves must be rebuilt to show; none, and nothing is said. */
    @Test
    fun onlyAFetchThatLaidDownPostersSaysSo() = runTest {
        val settings = InMemoryTmdbSettings().apply { write("a-fake-key") }
        val heard = mutableListOf<Unit>()

        val withPosters = FetchViewModel(FakeCoreProvider(FakeCore(report = FetchReport(2u, 7u, 5u, 1u, 0u, 0u))), settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { withPosters.postersArrived.toList(heard) }
        withPosters.fetch(quiet = true)
        assertEquals(1, heard.size)

        heard.clear()
        val without = FetchViewModel(FakeCoreProvider(FakeCore(report = FetchReport(0u, 9u, 0u, 1u, 0u, 0u))), settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { without.postersArrived.toList(heard) }
        without.fetch()
        assertEquals(0, heard.size)
    }
}
