package ui.tv

import androidx.activity.ComponentActivity
import android.view.KeyEvent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import designsystem.Palette
import ui.tv.catalog.TvPlate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import catalog.CatalogUiState
import catalog.shelvesOf
import model.Kind
import model.MediaSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ui.tv.catalog.TvFetchResultDialog
import ui.tv.catalog.TvTitlePage
import ui.tv.profile.TvChosenProfile

/**
 * The library's remote behaviour that only runs true on a real window
 * manager: Back at the catalogue's root, which is the activity's own Back
 * dispatch; a centre press on Play; and the fetch report's dialog, which
 * opens a window of its own. The walk between pages is proven over the real
 * ViewModels in `TvLibraryTest`.
 */
@RunWith(AndroidJUnit4::class)
class TvLibraryRemoteTest {
    @get:Rule val touchMode = LeavesTouchModeRule()

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun backAtTheRootGoesToTheMastheadThenLeavesTheApp() {
        showRoot(restoreKey = null)
        waitUntilFocused("Film 9")

        Espresso.pressBack()
        waitUntilFocused("Home")
        // Held here: once the second Back closes it, the rule has no
        // activity left to hand out.
        val activity = compose.activity
        assertFalse(activity.isFinishing)

        Espresso.pressBackUnconditionally()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertTrue(activity.isFinishing || activity.isDestroyed)
    }

    @Test
    fun theRootLandsOnThePlateItWasToldWasOpened() {
        showRoot(restoreKey = "film-6")

        waitUntilFocused("Film 6")
    }

    @Test
    fun playHoldsTheRemoteAndACentrePressPlays() {
        var played = 0
        compose.setContent {
            TvTheme { TvShell { TvTitlePage(set = film(0), info = null, progress = null, onPlay = { played++ }) } }
        }
        waitUntilFocused("▶ Play")

        compose.onNodeWithText("▶ Play").performKeyInput { pressKey(Key.DirectionCenter) }

        compose.waitForIdle()
        assertEquals(1, played)
    }

    /**
     * A centre press on a plate is how this page is reached, and a row
     * focused in that same moment once held the remote while drawn exactly
     * as if it did not. So this looks at Play's pixels, not its semantics,
     * which said "focused" all along.
     */
    @Test
    fun playLooksFocusedWhenACentrePressOpensThePage() {
        compose.setContent {
            TvTheme {
                TvShell {
                    var open by remember { mutableStateOf(false) }
                    if (open) {
                        TvTitlePage(set = film(0), info = null, progress = null, onPlay = {})
                    } else {
                        val plate = remember { FocusRequester() }
                        LaunchedEffect(Unit) { plate.requestFocus() }
                        TvPlate(title = "Plate", posterPath = null, onOpen = { open = true }, modifier = Modifier.focusRequester(plate))
                    }
                }
            }
        }
        waitUntilFocused("Plate")

        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        waitUntilFocused("▶ Play")
        compose.waitForIdle()

        val pixels = compose.onNodeWithText("▶ Play").captureToImage().toPixelMap()
        val accent = (0 until pixels.width).any { x -> (0 until pixels.height).any { y -> pixels[x, y] == Palette.Imprint } }
        assertTrue("Play is drawn in the focus accent", accent)
    }

    @Test
    fun theFetchReportTakesTheRemoteOnOkAndBackDismissesIt() {
        var dismissed = false
        compose.setContent {
            TvTheme { TvFetchResultDialog(message = "12 posters fetched.", onDismiss = { dismissed = true }) }
        }
        waitUntilFocused("OK")

        Espresso.pressBack()

        compose.waitForIdle()
        assertTrue(dismissed)
    }

    private fun showRoot(restoreKey: String?) {
        compose.setContent {
            TvTheme {
                TvShell {
                    TvCatalogRoot(
                        state = CatalogUiState.Ready(shelvesOf((0 until 10).map(::film))),
                        profile = TvChosenProfile(name = "Ada", onChoose = {}),
                        fetching = false,
                        restoreKey = restoreKey,
                        onOpenTitle = {},
                        onOpenCollection = {},
                        onOpenList = {},
                        onCreateList = {},
                        onTabChanged = {},
                        onOpenSearch = {},
                    )
                }
            }
        }
    }

    private fun waitUntilFocused(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText(text) and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun film(index: Int) =
        MediaSet(
            setId = "film-$index",
            kind = Kind.MOVIE,
            title = "Film $index",
            show = null,
            chapter = null,
            path = null,
            season = null,
            episodeFirst = null,
            episodeLast = null,
            year = null,
            durationSecs = null,
            posterPath = null,
            totalBytes = 0,
            addedAt = index.toLong(),
        )
}
