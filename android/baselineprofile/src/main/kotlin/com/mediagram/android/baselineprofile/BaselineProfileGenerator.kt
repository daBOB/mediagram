package com.mediagram.android.baselineprofile

import android.util.Log
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * Records which of the app's classes and methods a television viewer touches
 * between launch and a title page, so ART compiles them ahead of time
 * instead of interpreting them while the viewer scrolls.
 *
 * Meant for a Google TV that is already signed in with a library, driven the
 * way a remote drives it: D-pad only. The journey only navigates. It never
 * presses OK on anything that changes a setting, never starts playback —
 * a test play lands in the household's Continue row — and never presses
 * Back at the library root, which would leave the app.
 *
 * Where the app is not showing a library (first-run setup, or the profile
 * picker waiting for a viewer) it records the launch alone and says so in
 * the log: picking a profile would be choosing for someone.
 */
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun libraryJourney() =
        rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()

            if (device.wait(Until.hasObject(PROFILE_PICKER), SETTLE_MS)) {
                Log.i(TAG, "profile picker is showing; recorded the launch only")
                return@collect
            }
            if (!device.wait(Until.hasObject(MOVIES_PILL), UI_TIMEOUT_MS)) {
                Log.i(TAG, "no library is showing (setup?); recorded the launch only")
                return@collect
            }

            // Home: the rows below the departments bar.
            repeat(HOME_ROW_PRESSES) { device.pressDPadDown() }
            device.settle()

            // Back up to the bar, across to the Movies pill, and open it.
            if (!device.focusMoviesPill()) {
                Log.i(TAG, "could not reach the Movies pill; recorded Home only")
                return@collect
            }
            device.pressEnter()
            if (!device.wait(Until.hasObject(MOVIES_PAGE), UI_TIMEOUT_MS)) {
                Log.i(TAG, "Movies page did not open; recorded Home only")
                return@collect
            }

            // Into the page, then down the wall at a held key's pace.
            device.pressDPadDown()
            repeat(WALL_PRESSES) {
                device.pressDPadDown()
                Thread.sleep(HELD_KEY_INTERVAL_MS)
            }
            device.settle()

            // Whatever plate has focus now: open its title page and come back.
            device.pressEnter()
            if (device.wait(Until.hasObject(TITLE_PAGE), UI_TIMEOUT_MS)) {
                device.settle()
                device.pressBack()
                device.settle()
            } else {
                Log.i(TAG, "focused item opened no title page; recorded the wall only")
            }
        }

    /** Up to the bar, then right along it until the Movies pill holds focus. */
    private fun UiDevice.focusMoviesPill(): Boolean {
        repeat(MAX_BAR_STEPS) {
            if (moviesPillFocused()) return true
            pressDPadUp()
        }
        repeat(MAX_BAR_STEPS) {
            if (moviesPillFocused()) return true
            pressDPadRight()
        }
        return moviesPillFocused()
    }

    private fun UiDevice.moviesPillFocused(): Boolean = findObject(MOVIES_PILL)?.isFocused == true

    private fun UiDevice.settle() {
        waitForIdle()
        Thread.sleep(SETTLE_MS)
    }

    private companion object {
        const val TAG = "BaselineProfile"
        const val TARGET_PACKAGE = "com.mediagram.android"
        const val UI_TIMEOUT_MS = 15_000L
        const val SETTLE_MS = 1_500L
        const val HOME_ROW_PRESSES = 6
        const val WALL_PRESSES = 40
        const val HELD_KEY_INTERVAL_MS = 80L
        const val MAX_BAR_STEPS = 12

        // The pill's text is its title plus a count ("Movies 120"), merged into one node.
        val MOVIES_PILL = By.pkg(TARGET_PACKAGE).textStartsWith("Movies")

        // Compose test tags, exposed as resource ids by TvShell; the same strings the tag constants hold.
        val PROFILE_PICKER = By.res("tv-profile-picker-first-tile")
        val MOVIES_PAGE = By.res("tv-movies-department-page")
        val TITLE_PAGE = By.res("tv-title-page-body")
    }
}
