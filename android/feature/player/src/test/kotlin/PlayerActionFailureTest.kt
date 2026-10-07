package player

import androidx.lifecycle.viewModelScope
import data.WatchStateRepository
import data.WatchSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import playback.PlaybackCounters
import testing.FakeHeldSets
import testing.FakeSeriesPreloader
import testing.WatchStateFixture
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class PlayerActionFailureTest(
    private val action: String,
) {
    private val watch = WatchStateFixture()

    /** The coroutine the failing write ran in, as the provider saw it. */
    private var job: Job? = null

    @After fun reset() = Dispatchers.resetMain()

    @Test
    fun aRepositoryExceptionDoesNotInterruptPlaybackOrInventAcknowledgedMarks() =
        runTest {
            installMainDispatcher()
            val handle = FakePlayerHandle()
            val vm = actionViewModel(handle, watch.repository)
            try {
                vm.open("s1")
                handle.emitPlaying(true)
                failTheActionsWrite(IllegalStateException("private-storage-detail"))
                act(vm)
                runCurrent()
                assertEquals(PlayerUiState.Playing, vm.state.value)
                assertFalse(handle.stopCalled)
                assertTrue(assertNotNull(vm.actionNotice.value).startsWith("Could not confirm"))
                assertFalse(vm.actionNotice.value!!.contains("private-storage-detail"))
                val snapshot = watch.repository.snapshot.value
                assertTrue(snapshot.watchlist.isEmpty())
                assertTrue(snapshot.kids.isEmpty())
                // A successful creation before a failed membership write stays acknowledged.
                assertEquals(if (action == "createMembership") 1 else 0, snapshot.collections.size)
                assertTrue(snapshot.collections.all { it.items.isEmpty() })
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun cancellationCancelsTheActionJobWithoutInterruptingPlayback() =
        runTest {
            installMainDispatcher()
            val handle = FakePlayerHandle()
            val vm = actionViewModel(handle, watch.repository)
            try {
                vm.open("s1")
                handle.emitPlaying(true)
                failTheActionsWrite(CancellationException("leaving"))
                act(vm)
                runCurrent()
                assertTrue(requireNotNull(job).isCancelled)
                assertNull(vm.actionNotice.value)
                assertEquals(PlayerUiState.Playing, vm.state.value)
                assertFalse(handle.stopCalled)
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    /**
     * Fails the action's write at the provider — the first one it makes, or
     * for "createMembership" the second: the list is made, filing the title
     * on it is what fails.
     */
    private fun failTheActionsWrite(failure: Exception) {
        val failing = if (action == "createMembership") 2 else 1
        var calls = 0
        watch.provider.beforeCore = {
            if (++calls == failing) {
                job = currentCoroutineContext()[Job]
                throw failure
            }
        }
    }

    private fun act(vm: PlayerViewModel) =
        when (action) {
            "watchlist" -> vm.toggleWatchlist()
            "kids" -> vm.setKidsMark(12)
            "membership" -> vm.setInList("missing", true)
            else -> vm.createListAndAdd("Favourites")
        }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun actions() = listOf("watchlist", "kids", "membership", "create", "createMembership")
    }
}

internal fun actionViewModel(
    handle: FakePlayerHandle,
    repository: WatchStateRepository,
) = PlayerViewModel(
    handle,
    PlaybackCounters(),
    repository,
    ProgressRecorder(repository),
    object : WatchSync {
        override fun onForeground() = Unit

        override fun onBackground() = Unit

        override fun soon() = Unit

        override suspend fun awaitFirstRound() = Unit
    },
    FakeCatalogRepository(),
    FakePlayerPreferences(),
    FakeSubtitleTrackSource(),
    FakePlaybackServiceController(),
    FakeSeriesPreloader(),
    FakeHeldSets(),
)
