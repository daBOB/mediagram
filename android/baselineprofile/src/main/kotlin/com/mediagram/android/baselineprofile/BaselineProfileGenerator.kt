package com.mediagram.android.baselineprofile

import android.content.pm.PackageManager
import android.util.Log
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import java.util.regex.Pattern

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
 * Run it against the TV box and nothing else, with the serial pinned (a bare
 * invocation hits every attached device):
 *
 *     ANDROID_SERIAL=192.168.0.35:5555 ./gradlew :app:generateReleaseBaselineProfile
 *
 * `gradle.properties` keeps the app installed afterwards: AGP would otherwise
 * uninstall it, taking the box's Telegram session and settings with it. A
 * device without a leanback launcher is skipped before anything is pressed.
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
            if (!isTelevision()) {
                Log.i(TAG, "not a television; nothing pressed, nothing recorded")
                return@collect
            }
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

            // The Movies page is a front page, not the wall: its last item is
            // the "All N films" pill, which opens the wall.
            if (!device.focusAllFilmsPill()) {
                Log.i(TAG, "could not reach the All films pill; recorded the Movies page only")
                return@collect
            }
            device.pressEnter()
            if (!device.wait(Until.gone(MOVIES_PAGE), UI_TIMEOUT_MS)) {
                Log.i(TAG, "the wall did not open; recorded the Movies page only")
                return@collect
            }
            device.settle()

            // Down the wall at a held key's pace.
            repeat(WALL_PRESSES) {
                device.pressDPadDown()
                Thread.sleep(HELD_KEY_INTERVAL_MS)
            }
            device.settle()

            // Whatever plate has focus now: open its title page, and Back once,
            // which returns to the wall and not to the root.
            device.pressEnter()
            if (device.wait(Until.hasObject(TITLE_PAGE), UI_TIMEOUT_MS)) {
                device.settle()
                device.pressBack()
                device.settle()
            } else {
                Log.i(TAG, "focused item opened no title page; recorded the wall only")
            }
        }

    private fun isTelevision(): Boolean =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext.packageManager
            .hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    /** Down the Movies page until its closing pill holds focus. */
    private fun UiDevice.focusAllFilmsPill(): Boolean {
        repeat(MAX_PAGE_STEPS) {
            if (findObject(ALL_FILMS_PILL)?.isFocused == true) return true
            pressDPadDown()
            waitForIdle()
        }
        return findObject(ALL_FILMS_PILL)?.isFocused == true
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
        const val MAX_PAGE_STEPS = 40

        // The pill's text is its title plus a count ("Movies 120"), merged into one node.
        val MOVIES_PILL = By.pkg(TARGET_PACKAGE).textStartsWith("Movies")

        // "All 120 films →": the page's closing pill, unlike the wall's own "All films" heading.
        val ALL_FILMS_PILL = By.pkg(TARGET_PACKAGE).text(Pattern.compile("All \\d+ films.*"))

        // Compose test tags, exposed as resource ids by TvShell; the same strings the tag constants hold.
        val PROFILE_PICKER = By.res("tv-profile-picker-first-tile")
        val MOVIES_PAGE = By.res("tv-movies-department-page")
        val TITLE_PAGE = By.res("tv-title-page-body")
    }
}
