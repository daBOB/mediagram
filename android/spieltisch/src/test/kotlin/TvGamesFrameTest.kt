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
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The app's one screen: it opens on the poster shelf, a poster opens the host's TV page with that
 * game, Back from a running game stays on the page, Back from the lobby returns to the poster that
 * opened it with the host still running, and Back on the shelf leaves and stops it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvGamesFrameTest {
    private val durakPage = "http://127.0.0.1:8080/tv?game=durak"

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val core = FakeSpieltischCore()
    private var left = false

    @Before
    fun open() {
        val model = GameNightViewModel(NightSession(core, RecordingAnnouncer(), CoroutineScope(Dispatchers.Unconfined), "", NoMediaApp, NoImages))
        compose.setContent {
            // Leaving takes the frame away, as finishing the activity does.
            var shown by remember { mutableStateOf(true) }
            if (shown) {
                SpieltischTheme {
                    TvGamesFrame(model, shelfGames(Catalogue)) {
                        left = true
                        shown = false
                    }
                }
            }
        }
    }

    @Test
    fun theShelfOpensAndAPosterOpensItsGame() {
        compose.onNodeWithContentDescription("Buzzer-Quiz").assertExists()
        assertEquals(null, webView())

        pressPoster("Durak")

        assertEquals(durakPage, shownUrl())
    }

    @Test
    fun aGameWithArtShowsItsPosterAndOneWithoutShowsItsName() {
        compose.onNodeWithText("Grand Allee").assertExists()
        // The focused game's name is the heading; its poster carries the title in the art.
        assertEquals(1, compose.onAllNodesWithText("Buzzer-Quiz").fetchSemanticsNodes().size)
    }

    // A WebView whose layout height is WRAP_CONTENT lays the page out at zero height: 100% and vh are 0.
    @Test
    fun theGamePageIsLaidOutAtTheFrameHeight() {
        pressPoster("Durak")
        shownUrl()

        val params = assertNotNull(webView()?.layoutParams)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, params.height)
    }

    @Test
    fun goingToTheBackgroundEndsTheNightAndLeaves() {
        pressPoster("Durak")
        shownUrl()

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        assertTrue(left)
        assertEquals(1, core.stops)
    }

    @Test
    fun backInTheLobbyReturnsToThePosterAndBackThereLeavesAndStopsTheHost() {
        core.backAnswer = false
        pressPoster("Durak")
        shownUrl()

        back()

        // After the page's 400 ms to claim Back has run out.
        compose.waitUntil(timeoutMillis = 5_000) { webView() == null }
        compose.onNodeWithContentDescription("Durak").assertIsFocused()
        assertEquals(0, core.stops)

        back()

        assertTrue(left)
        assertEquals(1, core.stops)
    }

    @Test
    fun backDuringAGameStaysOnThePage() {
        core.backAnswer = true
        pressPoster("Durak")
        shownUrl()

        back()
        // The page never answers here, so Back goes to the host once its 400 ms have run out.
        afterTheBackWindow()

        assertEquals(1, core.backs)
        assertEquals(durakPage, shownUrl())
        assertEquals(0, core.stops)
    }

    @Test
    fun backThePageClaimsGoesNoFurther() {
        core.backAnswer = false
        pressPoster("Durak")
        shownUrl()

        back()
        // A settings panel was open: the page closed it and took the key.
        compose.runOnUiThread { shadowOf(assertNotNull(webView())).lastEvaluatedJavascriptCallback.onReceiveValue("\"true\"") }
        afterTheBackWindow()

        assertEquals(0, core.backs)
        assertEquals(durakPage, shownUrl())
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

    /**
     * As the remote does it: the shelf scrolls to the poster (one off screen isn't composed), focus
     * moves onto it, then OK. The focus is what Back from the game returns to.
     */
    private fun pressPoster(name: String) {
        compose.onNode(hasScrollToIndexAction() and hasAnyDescendant(hasContentDescription("Buzzer-Quiz")))
            .performScrollToNode(hasContentDescription(name))
        val poster = compose.onNodeWithContentDescription(name)
        poster.performSemanticsAction(SemanticsActions.RequestFocus)
        press(poster)
    }

    private fun press(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
}
