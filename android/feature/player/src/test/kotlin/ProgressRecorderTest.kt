package player

import kotlinx.coroutines.test.runTest
import model.Progress
import testing.WatchStateFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressRecorderTest {
    private val watch = WatchStateFixture()
    private val recorder = ProgressRecorder(watch.repository)
    private val snapshot get() = watch.repository.snapshot.value

    /**
     * The glance threshold belongs to [data.ResumePoint.resumeAt], read
     * only when a title is opened again — not to what gets saved while it
     * plays. Twelve seconds in is still worth writing down.
     */
    @Test
    fun aGlanceIsStillSavedAsProgress() =
        runTest {
            recorder.save("s1", atSeconds = 12.0, observedDurationSeconds = 2400.0)

            assertEquals(listOf("s1" to 12.0), snapshot.progress.map { it.setId to it.at })
            assertEquals(2400.0, snapshot.progress.single().duration)
            assertTrue(snapshot.watched.isEmpty())
        }

    /**
     * The completion is a cleared position's only tombstone: a finish that
     * dropped the position without a watched stamp newer than it would let
     * another device's older copy of that position win the next merge and
     * put the title back on Continue.
     */
    @Test
    fun theCreditsClearProgressAndMarkTheTitleWatched() =
        runTest {
            recorder.save("s1", atSeconds = 1200.0, observedDurationSeconds = 14400.0)
            val position = snapshot.progress.single().updatedAt

            // Fifty seconds of a four-hour film left: inside its last minute.
            recorder.save("s1", atSeconds = 14350.0, observedDurationSeconds = 14400.0)

            assertEquals(emptyList<Progress>(), snapshot.progress)
            val finished = snapshot.watched.single()
            assertEquals("s1", finished.setId)
            assertTrue(finished.finishedAt > position, "the watched stamp must postdate the position it replaces")
        }

    @Test
    fun anUnknownRuntimeKeepsThePositionRatherThanFinishing() =
        runTest {
            // No observed duration at all — nothing for trustedRuntime to believe.
            recorder.save("s1", atSeconds = 1200.0, observedDurationSeconds = null)

            assertEquals(listOf(Progress("s1", 1200.0, null, snapshot.progress.single().updatedAt)), snapshot.progress)
            assertTrue(snapshot.watched.isEmpty())
        }

    @Test
    fun aTitleAlreadyWatchedIsReStampedSoNoOlderPositionComesBack() =
        runTest {
            watch.repository.setWatched("s1", true)
            val before = snapshot.watched.single().finishedAt

            recorder.save("s1", atSeconds = 14350.0, observedDurationSeconds = 14400.0)

            assertTrue(snapshot.watched.single().finishedAt > before, "finishing again must move the stamp forward")
        }
}
