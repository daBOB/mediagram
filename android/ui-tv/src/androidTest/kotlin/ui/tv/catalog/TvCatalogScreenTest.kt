package ui.tv.catalog

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import catalog.CatalogUiState
import catalog.shelvesOf
import designsystem.Overscan
import model.Kind
import model.MediaSet
import org.junit.Assert.assertTrue
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

    /** A shelf wall's top row sits under the masthead as Home's does, and Up from it goes the same way. */
    @Test
    fun upFromAShelfWallsTopRowReturnsToTheMasthead() {
        show()
        waitUntilFocused("Film 9")
        compose.onNodeWithText("Film 9").performKeyInput { pressKey(Key.DirectionUp) }
        waitUntilFocused("Home")
        compose.onNodeWithText("Home").performKeyInput { pressKey(Key.DirectionRight) }
        waitUntilFocused("Movies")
        compose.onNodeWithText("Movies").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(isFocused() and hasText("Film", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }

        waitUntilFocused("Movies")
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

    /**
     * Every shelf the library can have, so the masthead is as full as it
     * gets: the viewer's name at its far end must still be on screen, not
     * pushed past the edge by the tabs before it.
     */
    @Test
    fun theViewersNameIsOnScreenBesideAFullMasthead() {
        show(films(10) + episodes() + lessons(), profileName = "andre")

        compose.onNodeWithText("andre").assertIsDisplayed()
        // assertIsDisplayed passes on the merged entry while only its
        // leading rule is on screen, and the clipped text beside it reports
        // bounds of nothing at all. So each label's visible width has to be
        // its whole laid-out width, ending inside the overscan-safe edge.
        val safeRight = compose.onRoot().fetchSemanticsNode().boundsInWindow.right -
            with(compose.density) { Overscan.horizontal.toPx() }
        for (text in listOf("andre", "Collections")) {
            val node = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
            val shown = node.boundsInWindow
            assertTrue("$text shows ${shown.width} of ${node.size.width}px", shown.width >= node.size.width - 1f)
            assertTrue("$text ends at ${shown.right}, past the safe edge at $safeRight", shown.right <= safeRight + 1f)
        }
    }

    @Test
    fun rightAlongTheMastheadReachesTheViewersName() {
        show(films(10) + episodes() + lessons(), profileName = "andre")
        waitUntilFocused("Film 9")
        compose.onNodeWithText("Film 9").performKeyInput { pressKey(Key.DirectionUp) }
        waitUntilFocused("Home")

        // Home, three shelves, four kept entries: eight steps to the name.
        repeat(8) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) } }

        waitUntilFocused("andre")
    }

    private fun show(
        sets: List<MediaSet> = films(10),
        profileName: String = "Ada",
    ) {
        compose.setContent {
            TvTheme {
                TvCatalogScreen(
                    state = CatalogUiState.Ready(shelvesOf(sets)),
                    profile = TvChosenProfile(name = profileName, onChoose = {}),
                    onOpenTitle = {},
                    onOpenCollection = {},
                    onOpenList = {},
                    onCreateList = {},
                )
            }
        }
    }

    private fun waitUntilFocused(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText(text) and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun films(count: Int) = (0 until count).map { set("film-$it", Kind.MOVIE, "Film $it", null, it.toLong()) }

    private fun episodes() = listOf(set("episode-0", Kind.EPISODE, "Pilot", "A Show", 0))

    private fun lessons() = listOf(set("lesson-0", Kind.TUTORIAL, "Lesson", "A Course", 0))

    private fun set(
        id: String,
        kind: Kind,
        title: String,
        show: String?,
        addedAt: Long,
    ) = MediaSet(
                setId = id,
                kind = kind,
                title = title,
                show = show,
                chapter = null,
                path = null,
                season = null,
                episodeFirst = null,
                episodeLast = null,
                year = null,
                durationSecs = null,
                posterPath = null,
                totalBytes = 0,
                addedAt = addedAt,
            )
}
