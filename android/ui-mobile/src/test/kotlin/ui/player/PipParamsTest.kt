package ui.player

import kotlin.test.Test
import kotlin.test.assertEquals

/** Covers [clampedPipAspect] — the window picture-in-picture actually opens at. */
class PipParamsTest {

    @Test
    fun anOrdinaryWidescreenFilmKeepsItsOwnShape() {
        val aspect = clampedPipAspect(1920, 1080) // 16:9, well inside the clamp

        assertEquals(1920.0 / 1080.0, aspect, 0.001)
    }

    @Test
    fun anUltraWideScopeFilmIsClampedToTheWidestWindowAndroidAccepts() {
        val aspect = clampedPipAspect(3840, 1200) // 3.2:1, wider than 2.39:1

        assertEquals(2.39, aspect, 0.001)
    }

    @Test
    fun aVerticalRecordingIsClampedToTheNarrowestWindowAndroidAccepts() {
        val aspect = clampedPipAspect(1080, 3840) // 0.28:1, narrower than 1:2.39

        assertEquals(1.0 / 2.39, aspect, 0.001)
    }

    @Test
    fun noMeasuredVideoSizeYetFallsBackToWidescreen() {
        val aspect = clampedPipAspect(0, 0)

        assertEquals(16.0 / 9.0, aspect, 0.001)
    }
}

/** Covers [pipAutoEnterEligible] — whether a home gesture should shrink into picture-in-picture. */
class PipAutoEnterEligibleTest {

    @Test
    fun aTitleAlreadyPlayingIsEligible() {
        assertEquals(true, pipAutoEnterEligible(isPlaying = true, playWhenReady = false))
    }

    @Test
    fun aTitleStillBufferingWithTheIntentToPlayIsEligible() {
        // The gap the device run actually found: home pressed mid-rebuffer,
        // where isPlaying is still false but the title is about to play.
        assertEquals(true, pipAutoEnterEligible(isPlaying = false, playWhenReady = true))
    }

    @Test
    fun aPausedOrFinishedOrFailedTitleIsNotEligible() {
        assertEquals(false, pipAutoEnterEligible(isPlaying = false, playWhenReady = false))
    }
}
