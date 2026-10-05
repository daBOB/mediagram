package com.mediagram.android.baselineprofile

import android.content.pm.PackageManager
import android.util.Log
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
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
 *
 * On the box the climb back from the Home rows to the bar still misses on
 * most iterations: focus can stick on a row at the bottom edge that Up
 * does not leave, or fall into the side rail. Each iteration's profile is
 * merged into the next, so the ones that get through (4 of 13 on the box)
 * carry the Movies page, the wall and the title page into the result. Check
 * the log for at least one iteration without a "recorded ... only" line.
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
            if (holdsFocus(ALL_FILMS_PILL)) return true
            pressDPadDown()
            waitForIdle()
        }
        return holdsFocus(ALL_FILMS_PILL)
    }

    /**
     * Up to the bar, then along it until the Movies pill holds focus. Focus
     * can reach the bar anywhere along it (above the hero's dots, or on the
     * icons at its right end), so it goes left first, stopping at Home rather
     * than stepping off the bar, then right.
     */
    private fun UiDevice.focusMoviesPill(): Boolean {
        stepWhile({ !holdsFocus(MOVIES_PILL) }) { pressDPadUp() }
        stepWhile({ !holdsFocus(MOVIES_PILL) && !holdsFocus(HOME_PILL) }) { pressDPadLeft() }
        stepWhile({ !holdsFocus(MOVIES_PILL) }) { pressDPadRight() }
        return holdsFocus(MOVIES_PILL)
    }

    /** Presses [press] while [keepGoing] holds, at most the bar's length of times. */
    private inline fun UiDevice.stepWhile(keepGoing: () -> Boolean, press: () -> Unit) {
        repeat(MAX_BAR_STEPS) {
            if (!keepGoing()) return
            press()
            waitForIdle()
        }
    }

    /**
     * Whether the node carrying the label, or the node around it, holds focus:
     * a pill takes focus on its own node while its label sits in a child.
     * Asked as queries: a node found first and read afterwards goes stale
     * when the screen recomposes in between, which focus moves make it do.
     */
    private fun UiDevice.holdsFocus(label: BySelector): Boolean =
        hasObject(By.copy(label).focused(true)) ||
            hasObject(By.focused(true).hasDescendant(By.copy(label)))

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

        // The pill's title; its count (" 120") is a sibling node.
        val MOVIES_PILL = By.pkg(TARGET_PACKAGE).textStartsWith("Movies")
        val HOME_PILL = By.pkg(TARGET_PACKAGE).text("Home")

        // "All 120 films →": the page's closing pill, unlike the wall's own "All films" heading.
        val ALL_FILMS_PILL = By.pkg(TARGET_PACKAGE).text(Pattern.compile("All \\d+ films.*"))

        // Compose test tags, exposed as resource ids by TvShell; the same strings the tag constants hold.
        val PROFILE_PICKER = By.res("tv-profile-picker-first-tile")
        val MOVIES_PAGE = By.res("tv-movies-department-page")
        val TITLE_PAGE = By.res("tv-title-page-body")
    }
}
