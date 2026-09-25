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
import model.ListOfSets
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import playback.PlaybackCounters
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class PlayerActionFailureTest(
    private val action: String,
) {
    @After fun reset() = Dispatchers.resetMain()

    @Test
    fun aRepositoryExceptionDoesNotInterruptPlaybackOrInventAcknowledgedMarks() =
        runTest {
            installMainDispatcher()
            val repository = ActionRepository(action)
            val handle = FakePlayerHandle()
            val vm = actionViewModel(handle, repository)
            try {
                vm.open("s1")
                handle.emitPlaying(true)
                act(vm)
                runCurrent()
                assertEquals(PlayerUiState.Playing, vm.state.value)
                assertFalse(handle.stopCalled)
                assertTrue(assertNotNull(vm.actionNotice.value).startsWith("Could not confirm"))
                assertFalse(vm.actionNotice.value!!.contains("private-storage-detail"))
                assertTrue(
                    repository.snapshot.value.watchlist
                        .isEmpty(),
                )
                assertTrue(
                    repository.snapshot.value.kids
                        .isEmpty(),
                )
                // A successful creation before a failed membership write stays acknowledged.
                assertEquals(if (action == "createMembership") 1 else 0, repository.snapshot.value.collections.size)
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun cancellationCancelsTheActionJobWithoutInterruptingPlayback() =
        runTest {
            installMainDispatcher()
            val repository = ActionRepository(action).apply { failure = CancellationException("leaving") }
            val handle = FakePlayerHandle()
            val vm = actionViewModel(handle, repository)
            try {
                vm.open("s1")
                handle.emitPlaying(true)
                act(vm)
                runCurrent()
                assertTrue(requireNotNull(repository.job).isCancelled)
                assertNull(vm.actionNotice.value)
                assertEquals(PlayerUiState.Playing, vm.state.value)
                assertFalse(handle.stopCalled)
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    private fun act(vm: PlayerViewModel) =
        when (action) {
            "watchlist" -> vm.toggleWatchlist()
            "kids" -> vm.toggleKids()
            "membership" -> vm.setInList("missing", true)
            else -> vm.createListAndAdd("Favourites")
        }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun actions() = listOf("watchlist", "kids", "membership", "create", "createMembership")
    }
}

private class ActionRepository(
    private val failingAction: String,
    private val acknowledged: FakeWatchStateRepository = FakeWatchStateRepository(),
) : WatchStateRepository by acknowledged {
    var failure: Exception = IllegalStateException("private-storage-detail")
    var job: Job? = null

    private suspend fun writing(action: String) {
        if (action == failingAction || (action == "membership" && failingAction == "createMembership")) {
            job = currentCoroutineContext()[Job]
            throw failure
        }
    }

    override suspend fun setWatchlisted(
        setId: String,
        listed: Boolean,
    ) {
        writing("watchlist")
        acknowledged.setWatchlisted(setId, listed)
    }

    override suspend fun setKids(
        setId: String,
        marked: Boolean,
    ) {
        writing("kids")
        acknowledged.setKids(setId, marked)
    }

    override suspend fun createList(name: String): ListOfSets? {
        writing("create")
        return acknowledged.createList(name)
    }

    override suspend fun setInList(
        id: String,
        setId: String,
        included: Boolean,
    ): Boolean {
        writing("membership")
        return acknowledged.setInList(id, setId, included)
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
