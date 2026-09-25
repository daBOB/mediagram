package ui.tv.catalog

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import catalog.CatalogUiState
import catalog.shelvesOf
import model.Kind
import model.MediaSet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ui.tv.LeavesTouchModeRule
import ui.tv.TvTheme
import ui.tv.profile.TvChosenProfile

/**
 * The D-pad paths between the masthead and Home, which only run true on a
 * real window manager — what composes where is proven without one in
 * `TvCatalogScreenStateTest`. Home's first row here is "Latest films",
 * newest first, so "Film 9" is its first plate.
 */
@RunWith(AndroidJUnit4::class)
class TvCatalogScreenTest {
    @get:Rule val touchMode = LeavesTouchModeRule()

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun homeFocusesItsFirstPlateAsSoonAsItAppears() {
        show()

        waitUntilFocused("Film 9")
    }

    @Test
    fun upFromTheTopRowReturnsToTheMasthead() {
        show()
        waitUntilFocused("Film 9")

        compose.onNodeWithText("Film 9").performKeyInput { pressKey(Key.DirectionUp) }

        waitUntilFocused("Home")
    }

    @Test
    fun downFromTheMastheadLandsOnTheFirstPlateOfTheFirstRow() {
        show()
        waitUntilFocused("Film 9")
        compose.onNodeWithText("Film 9").performKeyInput { pressKey(Key.DirectionUp) }
        waitUntilFocused("Home")

        compose.onNodeWithText("Home").performKeyInput { pressKey(Key.DirectionDown) }

        waitUntilFocused("Film 9")
    }

    @Test
    fun rightAlongARowReachesSeeAllAfterTheSixthPlate() {
        show()
        waitUntilFocused("Film 9")

        repeat(6) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) } }

        waitUntilFocused("See all")
    }

    private fun show() {
        compose.setContent {
            TvTheme {
                TvCatalogScreen(
                    state = CatalogUiState.Ready(shelvesOf(films(10))),
                    profile = TvChosenProfile(name = "Ada", onChoose = {}),
                    onOpenTitle = {},
                    onOpenCollection = {},
                    onOpenList = {},
                )
            }
        }
    }

    private fun waitUntilFocused(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText(text) and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun films(count: Int) =
        (0 until count).map {
            MediaSet(
                setId = "film-$it",
                kind = Kind.MOVIE,
                title = "Film $it",
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
                addedAt = it.toLong(),
            )
        }
}
