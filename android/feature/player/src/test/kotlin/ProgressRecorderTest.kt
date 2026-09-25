package player

import kotlinx.coroutines.test.runTest
import model.WatchSnapshot
import model.Watched
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressRecorderTest {
    /**
     * The glance threshold belongs to [data.ResumePoint.resumeAt], read
     * only when a title is opened again — not to what gets saved while it
     * plays. Twelve seconds in is still worth writing down.
     */
    @Test
    fun aGlanceIsStillSavedAsProgress() =
        runTest {
            val repository = FakeWatchStateRepository()
            val recorder = ProgressRecorder(repository)

            recorder.save("s1", atSeconds = 12.0, observedDurationSeconds = 2400.0)

            assertEquals(listOf("setProgress s1 12.0 2400.0"), repository.calls)
        }

    @Test
    fun theCreditsClearProgressAndMarkTheTitleWatched() =
        runTest {
            val repository = FakeWatchStateRepository()
            val recorder = ProgressRecorder(repository)

            // Fifty seconds of a four-hour film left: inside its last minute.
            recorder.save("s1", atSeconds = 14350.0, observedDurationSeconds = 14400.0)

            assertEquals(listOf("clearProgress s1", "setWatched s1 true"), repository.calls)
        }

    @Test
    fun anUnknownRuntimeKeepsThePositionRatherThanFinishing() =
        runTest {
            val repository = FakeWatchStateRepository()
            val recorder = ProgressRecorder(repository)

            // No observed duration at all — nothing for trustedRuntime to believe.
            recorder.save("s1", atSeconds = 1200.0, observedDurationSeconds = null)

            assertEquals(listOf("setProgress s1 1200.0 null"), repository.calls)
        }

    @Test
    fun aTitleAlreadyWatchedIsNotReStampedOnAnotherFinish() =
        runTest {
            val repository =
                FakeWatchStateRepository(
                    initialSnapshot = WatchSnapshot.Empty.copy(watched = listOf(Watched("s1", 0))),
                )
            val recorder = ProgressRecorder(repository)

            recorder.save("s1", atSeconds = 14350.0, observedDurationSeconds = 14400.0)

            assertEquals(listOf("clearProgress s1"), repository.calls)
        }
}
