package player

import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [PlayerSession.save]'s own floor: a position under a second in is dropped
 * rather than landing as an ordinary pause-save. An up-next switch opens the
 * next title before the old one's own synchronous "stopped playing" event
 * has finished landing, and that event still names the set this now points
 * to — without the floor, its otherwise-ordinary pause-save would write the
 * next episode onto Continue at 0s, before a frame of it has played.
 */
class PlayerSessionTest {

    @Test
    fun aSaveUnderOneSecondInIsDropped() = runTest {
        val handle = FakePlayerHandle().apply { fakePositionMs = 500L; fakeDurationMs = 100_000L }
        val repository = FakeWatchStateRepository()
        val session = PlayerSession(this, handle, ProgressRecorder(repository))
        session.open("s1")

        session.save()
        runCurrent()

        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun aSaveAtOrPastOneSecondInIsKept() = runTest {
        val handle = FakePlayerHandle().apply { fakePositionMs = 1_000L; fakeDurationMs = 100_000L }
        val repository = FakeWatchStateRepository()
        val session = PlayerSession(this, handle, ProgressRecorder(repository))
        session.open("s1")

        session.save()
        runCurrent()

        assertEquals(listOf("setProgress s1 1.0 100.0"), repository.calls)
    }

    /**
     * `PlayerViewModel.open` calls `session.open(next)` before `handle.open`
     * triggers the old title's own synchronous "stopped playing" — by the
     * time that event lands, `openSetId` already names the title just
     * switched to, not the one that actually stopped.
     */
    @Test
    fun aSwitchsSynchronousStopEventDoesNotLandOnContinueAtZero() = runTest {
        val handle = FakePlayerHandle().apply { fakePositionMs = 0L; fakeDurationMs = null }
        val repository = FakeWatchStateRepository()
        val session = PlayerSession(this, handle, ProgressRecorder(repository))
        session.open("s2")

        session.onPlayingChanged(false) // the synchronous event, arriving early
        runCurrent()

        assertTrue(repository.calls.isEmpty())
    }
}
