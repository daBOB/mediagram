package com.mediagram.spieltisch

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The app's one screen: the host's TV page, the games shelf and lobby in one, opens as soon as the
 * host runs. Back from a running game stays on the page, a second Back on the shelf leaves and stops
 * the host, and a host that failed to start is tried again with OK.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvGamesFrameTest {
    private val tvPage = "http://127.0.0.1:8080/tv"

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val core = FakeSpieltischCore()
    private var left = false

    private fun show(media: MediaLibrary = MediaLibrary { MediaNight(kidsLocked = false, titles = "[]") }) {
        val model = GameNightViewModel(NightSession(core, RecordingAnnouncer(), CoroutineScope(Dispatchers.Unconfined), "", media, NoImages))
        compose.setContent {
            // Leaving takes the frame away, as finishing the activity does.
            var shown by remember { mutableStateOf(true) }
            if (shown) {
                SpieltischTheme {
                    TvGamesFrame(model) {
                        left = true
                        shown = false
                    }
                }
            }
        }
    }

    @Test
    fun thePageOpensAsSoonAsTheHostRuns() {
        show()

        assertEquals(tvPage, shownUrl())
    }

    @Test
    fun withoutTheMediaAppThePageIsToldTheFilmsAreAway() {
        show(NoMediaApp)

        assertEquals("$tvPage?films=0", shownUrl())
    }

    // A WebView whose layout height is WRAP_CONTENT lays the page out at zero height: 100% and vh are 0.
    @Test
    fun theGamePageIsLaidOutAtTheFrameHeight() {
        show()
        shownUrl()

        val params = assertNotNull(webView()?.layoutParams)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, params.height)
    }

    @Test
    fun goingToTheBackgroundEndsTheNightAndLeaves() {
        show()
        shownUrl()

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        assertTrue(left)
        assertEquals(1, core.stops)
    }

    @Test
    fun aSecondBackOnTheShelfLeavesAndStopsTheHost() {
        core.backAnswer = false
        show()
        shownUrl()

        back()
        // The page never answers here, so Back goes on once its 400 ms have run out.
        afterTheBackWindow()

        // One Back would drop every phone at the table: it only asks.
        compose.onNodeWithText("Press Back again to end the game night").assertExists()
        assertFalse(left)

        back()
        afterTheBackWindow()

        assertTrue(left)
        assertEquals(1, core.stops)
    }

    @Test
    fun theQuestionGoesAwayAndOneBackThenOnlyAsksAgain() {
        core.backAnswer = false
        show()
        shownUrl()
        back()
        afterTheBackWindow()

        compose.mainClock.advanceTimeBy(LEAVE_WINDOW_MS + 100)
        compose.onNodeWithText("Press Back again to end the game night").assertDoesNotExist()

        back()
        afterTheBackWindow()

        assertFalse(left)
        assertEquals(0, core.stops)
    }

    @Test
    fun backDuringAGameStaysOnThePage() {
        core.backAnswer = true
        show()
        shownUrl()

        back()
        afterTheBackWindow()

        assertEquals(1, core.backs)
        assertEquals(tvPage, shownUrl())
        assertEquals(0, core.stops)
    }

    @Test
    fun backThePageClaimsGoesNoFurther() {
        show()
        shownUrl()

        back()
        // A settings panel was open: the page closed it and took the key.
        compose.runOnUiThread { shadowOf(assertNotNull(webView())).lastEvaluatedJavascriptCallback.onReceiveValue("\"true\"") }
        afterTheBackWindow()

        assertEquals(0, core.backs)
        assertEquals(tvPage, shownUrl())
    }

    @Test
    fun aHostThatFailedIsTriedAgain() {
        core.startFailure = IllegalStateException("No network.")
        show()
        compose.onNodeWithText("No network.").assertExists()

        core.startFailure = null
        compose.onNodeWithText("Try again").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(tvPage, shownUrl())
        assertEquals(2, core.starts.size)
    }

    @Test
    fun backBeforeThePageIsThereLeaves() {
        core.startFailure = IllegalStateException("No network.")
        show()
        compose.onNodeWithText("No network.").assertExists()

        back()

        assertTrue(left)
    }

    private fun afterTheBackWindow() {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
    }

    /** The URL the page's WebView loaded, waiting for the host to answer first. */
    private fun shownUrl(): String? {
        compose.waitUntil(timeoutMillis = 5_000) { webView() != null }
        return shadowOf(assertNotNull(webView())).lastLoadedUrl
    }

    private fun webView(): WebView? = compose.activity.window.decorView.findWebView()

    private fun View.findWebView(): WebView? =
        when (this) {
            is WebView -> this
            is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).findWebView() }
            else -> null
        }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
}
