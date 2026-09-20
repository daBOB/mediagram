package player

import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import playback.PlaybackCounters
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Whether anyone is subscribed when the player fails to build is decided
 * by nothing more than how long the cache takes to open: the handle is
 * constructed first and the ViewModel subscribes a moment later, so a
 * failure that resolves quickly has no one to tell. Nothing ever builds a
 * second player, so a failure lost that way is lost for the life of the
 * process, and the screen waits on a player that is never coming.
 */
class PlayerConstructionFailureTest {

    @Test
    fun aFailureThatLandsBeforeAnyoneSubscribesIsStillDelivered() = runTest {
        val deferred = CompletableDeferred<ExoPlayer>()
        val handle = DefaultPlayerHandle(deferred, this)

        deferred.completeExceptionally(IOException("no space left for the cache"))
        advanceUntilIdle()

        // Only now does the screen exist to care about it.
        val viewModel = PlayerViewModel(handle, PlaybackCounters())

        assertEquals(PlayerUiState.Failed("no space left for the cache"), viewModel.state.value)
    }

    @Test
    fun openingASetAfterTheFailureReportsItRatherThanWaitingForAPlayer() = runTest {
        val deferred = CompletableDeferred<ExoPlayer>()
        val handle = DefaultPlayerHandle(deferred, this)
        val viewModel = PlayerViewModel(handle, PlaybackCounters())
        deferred.completeExceptionally(IOException("no space left for the cache"))
        advanceUntilIdle()
        assertEquals(PlayerUiState.Failed("no space left for the cache"), viewModel.state.value)

        viewModel.open("s1")

        assertEquals(PlayerUiState.Failed("no space left for the cache"), viewModel.state.value)
    }
}
