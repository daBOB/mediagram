package player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.MediaSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The card's −15, +15 and ↺ at the edges of a title, as the up-next card
 * sees them. The player clamps a skip itself — media3's own `seekBack`/
 * `seekForward` on the phone, `seekBy` on the television — so +15 in the
 * last seconds lands on the end, and media3 then reports the title ended:
 * the ordinary ended path, which must still count down and switch. A seek
 * back off the end is not the end any more, and must not.
 */
class UpNextSeekEdgesTest {

    private val tenMinutes = MutableStateFlow<MediaSet?>(fakeMediaSet("s1", durationSecs = 600))

    @Test
    fun aSkipThatLandsOnTheEndRunsTheOrdinaryCountdownAndSwitch() = runTest {
        val handle = FakePlayerHandle().apply { fakePositionMs = 590_000L; fakeDurationMs = 600_000L }
        val (controller, session) = buildController(this, handle, openSet = tenMinutes)
        session.open("s1")
        controller.startTitle("s1", listOf("s1", "s2"))
        runCurrent()

        handle.fakePositionMs = 600_000L // +15 from 9:50, clamped to the end
        controller.onSeeked()
        controller.onEnded() // media3 reports the end the clamped seek reached
        assertEquals(UpNextPhase.COUNTING, controller.state.value.phase)

        advanceTimeBy(10_000)
        runCurrent()

        assertEquals("s2", controller.pendingSwitch.value?.setId)
        controller.stop()
    }

    @Test
    fun aSeekBackOffTheEndStopsTheCountdown() = runTest {
        val handle = FakePlayerHandle().apply { fakePositionMs = 600_000L; fakeDurationMs = 600_000L }
        val (controller, session) = buildController(this, handle, openSet = tenMinutes)
        session.open("s1")
        controller.startTitle("s1", listOf("s1", "s2"))
        runCurrent()
        controller.onEnded()
        assertEquals(UpNextPhase.COUNTING, controller.state.value.phase)

        handle.fakePositionMs = 0L // Restart, or a scrub back to the top
        controller.onSeeked()

        assertEquals(UpNextPhase.HIDDEN, controller.state.value.phase)
        advanceTimeBy(11_000)
        runCurrent()
        assertNull(controller.pendingSwitch.value, "a viewer watching again from the top is not switched away")
    }

    @Test
    fun aSeekThatStaysOnTheEndKeepsCounting() = runTest {
        val handle = FakePlayerHandle().apply { fakePositionMs = 600_000L; fakeDurationMs = 600_000L }
        val (controller, session) = buildController(this, handle, openSet = tenMinutes)
        session.open("s1")
        controller.startTitle("s1", listOf("s1", "s2"))
        runCurrent()
        controller.onEnded()

        controller.onSeeked() // +15 pressed again on the last frame

        assertEquals(UpNextPhase.COUNTING, controller.state.value.phase)
        controller.stop()
    }

    /** With no position or length to read, a seek cannot say it left the end, so the ending stands — today's behaviour. */
    @Test
    fun aSeekNothingCanMeasureLeavesAnEndingAlone() = runTest {
        val (controller, session) = buildController(this, FakePlayerHandle())
        session.open("s1")
        controller.startTitle("s1", listOf("s1", "s2"))
        runCurrent()
        controller.onEnded()

        controller.onSeeked()

        assertEquals(UpNextPhase.COUNTING, controller.state.value.phase)
        controller.stop()
    }
}
