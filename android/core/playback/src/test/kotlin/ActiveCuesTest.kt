package playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ported from the web's `subtitle-style.test.ts`. Nothing here mutates a
 * cue in place the way the browser's own `TextTrackCue` does — [shiftedTimes]
 * and [activeCues] compute fresh from the parsed times every call — so the
 * web's "twice with the same offset is not twice" case has nothing to prove
 * here: there is no stateful shift for a second call to drift away from.
 */
class ActiveCuesTest {

    private fun cue(start: Long, end: Long, text: String = "line") = TimedCue(start, end, text)

    // -- shiftedTimes --

    @Test
    fun appliesTheOffsetToTheTimeItWasParsedWith() {
        assertEquals(TimedCue(9_600, 11_600, "x"), shiftedTimes(TimedCue(10_000, 12_000, "x"), -400))
        assertEquals(TimedCue(11_500, 13_500, "x"), shiftedTimes(TimedCue(10_000, 12_000, "x"), 1_500))
    }

    @Test
    fun nothingMovesByNothing() {
        assertEquals(TimedCue(10_000, 12_000, "x"), shiftedTimes(TimedCue(10_000, 12_000, "x"), 0))
    }

    @Test
    fun aCueCannotStartBeforeTheFilmDoes() {
        assertEquals(TimedCue(0, 0, "x"), shiftedTimes(TimedCue(2_000, 4_000, "x"), -5_000))
    }

    @Test
    fun andNeverEndsBeforeItStarts() {
        val shifted = shiftedTimes(TimedCue(2_000, 2_500, "x"), -100_000)
        assertTrue(shifted.endMs >= shifted.startMs)
    }

    @Test
    fun changingTheOffsetIsAlwaysMeasuredFromTheOriginalTimes() {
        // Not from wherever a previous call left it — there is no "wherever"
        // here, since nothing is held between calls, but the guarantee still
        // deserves a name a future refactor could break.
        val original = TimedCue(10_000, 12_000, "x")
        shiftedTimes(original, -400)
        assertEquals(TimedCue(12_000, 14_000, "x"), shiftedTimes(original, 2_000))
    }

    // -- activeCues --

    @Test
    fun aCueIsActiveFromItsShiftedStartUpToButNotIncludingItsEnd() {
        val cues = listOf(cue(10_000, 12_000))
        assertEquals(emptyList(), activeCues(cues, positionMs = 9_999, offsetMs = 0))
        assertEquals(cues, activeCues(cues, positionMs = 10_000, offsetMs = 0))
        assertEquals(cues, activeCues(cues, positionMs = 11_999, offsetMs = 0))
        assertEquals(emptyList(), activeCues(cues, positionMs = 12_000, offsetMs = 0))
    }

    @Test
    fun onlyTheCuesTheOffsetActuallyMovesIntoRangeAreActive() {
        val early = cue(1_000, 2_000, "early")
        val late = cue(20_000, 21_000, "late")
        // Without the offset, the playhead sits in neither.
        assertEquals(emptyList(), activeCues(listOf(early, late), positionMs = 15_000, offsetMs = 0))
        // Shifted 14s earlier, "late" now covers the same playhead.
        assertEquals(listOf(cue(6_000, 7_000, "late")), activeCues(listOf(early, late), positionMs = 6_500, offsetMs = -14_000))
    }

    @Test
    fun aTrackWithNoCuesYetIsNotAnError() {
        assertEquals(emptyList(), activeCues(emptyList(), positionMs = 5_000, offsetMs = 0))
    }

    @Test
    fun moreThanOneCueCanBeActiveAtOnce() {
        val cues = listOf(cue(0, 5_000, "a"), cue(1_000, 4_000, "b"))
        assertEquals(cues, activeCues(cues, positionMs = 2_000, offsetMs = 0))
    }
}
