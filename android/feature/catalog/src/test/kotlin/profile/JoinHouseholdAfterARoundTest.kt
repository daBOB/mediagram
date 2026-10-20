package catalog.profile

import data.DefaultHouseholdsRepository
import data.DefaultWatchStateRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import model.HOME_HOUSEHOLD
import org.junit.Rule
import testing.FakeCore
import testing.FakeCoreProvider
import testing.MainDispatcherRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A fresh device on an install with one household: the first sync round
 * joins it there, so Join a household is never the answer. The round is
 * heard before the households are read again; until they are, what the last
 * read said of joining is from before the round.
 */
class JoinHouseholdAfterARoundTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val core = FakeCore().apply {
        syncedOnce = false
        homes.joined = null
    }
    private val repository = DefaultWatchStateRepository(FakeCoreProvider(core), Dispatchers.Unconfined)

    /** The households' own way to the core, so their read can be held while the profiles' is not. */
    private val households = FakeCoreProvider(core)

    @Test
    fun aRoundHeardBeforeTheHouseholdsAreReadAgainNeverAsksToJoin() =
        runTest(UnconfinedTestDispatcher()) {
            repository.reload()
            val read = DefaultHouseholdsRepository(households, repository, backgroundScope, Dispatchers.Unconfined)
            val vm = JoinHouseholdViewModel(repository, read)
            val shown = mutableListOf<JoinUiState>()
            backgroundScope.launch { vm.state.collect { shown += it } }
            assertFalse(vm.state.value.needsJoin)

            val door = CompletableDeferred<Unit>()
            households.beforeCore = { door.await() }
            // The round: the core joins the only household there is.
            core.syncedOnce = true
            core.homes.joined = HOME_HOUSEHOLD
            repository.reload()
            assertTrue(repository.syncedOnce.value)
            assertFalse(vm.state.value.needsJoin)

            door.complete(Unit)
            assertEquals(HOME_HOUSEHOLD, read.view.value.joined)
            assertTrue(shown.none { it.needsJoin }, "$shown")
        }
}
