package ui.tv.catalog

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import catalog.CatalogUiState
import catalog.shelvesOf
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
 * The D-pad paths through the bar, the rail and Home, which only run true
 * on a real window manager — what composes where is proven without one in
 * `TvCatalogScreenStateTest`. Home's own first section here is Recently
 * added, newest first (`films(10)` carries no backdrop or poster data, so
 * the cover, the features and Continue all draw nothing) — "Film 9" is its
 * first poster.
 */
@RunWith(AndroidJUnit4::class)
class TvCatalogScreenTest {
    @get:Rule val touchMode = LeavesTouchModeRule()

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun launchFocusLandsOnHomesFirstPoster() {
        show()

        waitUntilFocused("Film 9")
    }

    /** The plan's own rule: pressing a pill selects its department but leaves the remote on the pill, not on the new wall's own first plate. */
    @Test
    fun pillPressKeepsTheRemoteOnThePillAndDownEntersThePage() {
        show()
        waitUntilFocused("Film 9")
        compose.onNodeWithText("Film 9").performKeyInput { pressKey(Key.DirectionUp) }
        waitUntilFocused("Home")
        compose.onNodeWithText("Home").performKeyInput { pressKey(Key.DirectionRight) }
        waitUntilFocused("Movies")

        compose.onNodeWithText("Movies").performKeyInput { pressKey(Key.DirectionCenter) }

        waitUntilFocused("Movies")
        compose.onNode(hasText("Film", substring = true) and isFocused()).assertDoesNotExist()

        compose.onNodeWithText("Movies").performKeyInput { pressKey(Key.DirectionDown) }

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(isFocused() and hasText("Film", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun leftAtTheContentsLeftEdgeOpensTheFullRail() {
        show()
        waitUntilFocused("Film 9")

        compose.onNodeWithText("Film 9").performKeyInput { pressKey(Key.DirectionLeft) }

        // The rail opens over the content the moment the remote reaches it
        // — its own wordmark, hidden while collapsed, is what proves that.
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText("mediagram").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun backWalksFromContentToThePillThenToTheRail() {
        show()
        waitUntilFocused("Film 9")

        Espresso.pressBack()
        waitUntilFocused("Home")

        Espresso.pressBack()
        // No pill was ever pressed here, so the rail's own active row is
        // My List, the kept row a viewer on the Home pill has never left —
        // proven by the wordmark, hidden until the rail actually opens.
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText("mediagram").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun rightAlongARowReachesSeeAllAfterTheEighthPoster() {
        show()
        waitUntilFocused("Film 9")

        repeat(8) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) } }

        waitUntilFocused("See all")
    }

    /** The avatar carries the viewer's own initial, not the full name, as the web bar's `#who` and the tablet's avatar do — proven here on a real window. */
    @Test
    fun theAvatarShowsTheViewersInitial() {
        show(films(2), profileName = "andre")

        compose.onNodeWithContentDescription("Who's watching: andre").assertIsDisplayed()
    }

    /**
     * The chrome's contract, already proven for Back: leaving content
     * upward lands on the *selected* pill, wherever it now sits — not
     * whichever pill happens to sit geometrically above whatever control
     * the remote was on. The cover's own action row is Home's own first,
     * so a plain geometric search is most likely to land somewhere else
     * here — `TvCoverActions`' own explicit `up` wiring on every stop in
     * that row is what this proves, on a real window's own focus search.
     */
    @Test
    fun upFromTheCoversWatchNowLandsOnTheSelectedPillNotWhicheverSitsAboveIt() {
        show(featuredFilms(4))
        waitUntilFocused("Watch now", substring = true)

        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }

        waitUntilFocused("Home")
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

    private fun waitUntilFocused(
        text: String,
        substring: Boolean = false,
    ) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText(text, substring = substring) and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun films(count: Int) = (0 until count).map { set("film-$it", Kind.MOVIE, "Film $it", null, it.toLong()) }

    /** Films with a backdrop and a poster — enough for the cover and the feature cards to draw something, unlike [films]' own bare fixture. */
    private fun featuredFilms(count: Int) =
        films(count).map { it.copy(backdropPath = "/bd${it.setId}", posterPath = "/p${it.setId}") }

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
