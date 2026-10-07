package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import model.Kind
import model.MediaSet
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import playback.FilmPreloadState
import ui.common.catalog.TitlePreloadUi
import kotlin.test.assertTrue

/**
 * The pill row at a real phone's own width, measured with real text rather
 * than Robolectric's default near-zero-width LEGACY mode — the only way
 * [TitlePills]' own overflow (My List and ⋯ crowded to 0dp wide once a
 * Preload pill joined the row on a 360-411dp phone) was ever going to show
 * up. `FlowRow` wraps the row instead of squeezing it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TitlePillsWidthTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { content() } }
        }
        compose.waitForIdle()
    }

    private val film =
        MediaSet(
            setId = "film-1", kind = Kind.MOVIE, title = "Dune", show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = 2021, durationSecs = 9000,
            posterPath = null, totalBytes = TOTAL,
        )

    private fun ui(state: FilmPreloadState) = TitlePreloadUi(state, null, {}, {}, {})

    private fun showWithPreload(state: FilmPreloadState) {
        show {
            TitleDetailScreen(
                film, null, {}, onOpenGenre = {},
                onToggleEditorsChoice = {},
                preload = ui(state),
            )
        }
    }

    @Test
    fun theOverflowMenuStaysReachableAlongsideAnIdlePreloadPill() {
        showWithPreload(FilmPreloadState.Idle(0L, TOTAL))
        val more = compose.onNodeWithContentDescription("More").getBoundsInRoot()
        assertTrue((more.right - more.left).value > 0f, "the ⋯ menu must stay reachable once a Preload pill is in the row")
    }

    @Test
    fun theOverflowMenuStaysReachableWithARunningBarAbove() {
        showWithPreload(FilmPreloadState.Running(TOTAL * 40 / 100, TOTAL))
        val more = compose.onNodeWithContentDescription("More").getBoundsInRoot()
        assertTrue((more.right - more.left).value > 0f, "the ⋯ menu must stay reachable while the bar is showing")
    }

    @Test
    fun theOverflowMenuStaysReachableWhenDoneOffersRemove() {
        showWithPreload(FilmPreloadState.Done)
        val more = compose.onNodeWithContentDescription("More").getBoundsInRoot()
        assertTrue((more.right - more.left).value > 0f, "Remove preload must stay reachable through the ⋯ menu")
    }

    @Test
    fun myListStaysAtFullHeightRatherThanWrappingItsTextAcrossSeveralLines() {
        showWithPreload(FilmPreloadState.Idle(0L, TOTAL))
        val myList = compose.onNodeWithContentDescription("Add to My List").getBoundsInRoot()
        assertTrue((myList.bottom - myList.top).value < MAX_SINGLE_LINE_PILL_HEIGHT_DP, "My List must not be squeezed into wrapping its own text")
    }

    private companion object {
        const val TOTAL = 5 * 1_073_741_824L

        /** Comfortably above a real single-line pill's own height, comfortably below what a wrapped one measures. */
        const val MAX_SINGLE_LINE_PILL_HEIGHT_DP = 60f
    }
}
