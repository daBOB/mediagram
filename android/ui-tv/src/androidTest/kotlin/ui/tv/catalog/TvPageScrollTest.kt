package ui.tv.catalog

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import catalog.CollectionKind
import catalog.Division
import catalog.Entry
import designsystem.Overscan
import model.Kind
import model.MediaSet
import model.Progress
import model.WatchSnapshot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ui.tv.LeavesTouchModeRule
import ui.tv.TvTheme
import uniffi.mediagram_core.TitleInfo

/**
 * Where a page opened from the catalogue sits when the remote lands on it,
 * and whether the remote can bring its name back once it has scrolled.
 * Only a real television scrolls a focused item the way this is about —
 * Compose moves it to a third of the way down on a device with leanback —
 * so this lives here rather than beside the pages' Robolectric tests.
 */
@RunWith(AndroidJUnit4::class)
class TvPageScrollTest {
    @get:Rule val touchMode = LeavesTouchModeRule()

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /** Resume sits in the spread, on screen from the start: nothing scrolls the page on arrival. */
    @Test
    fun aTitlePageOpensWithItsWholeSpreadOnScreen() {
        show { TvTitlePage(set = film, info = info, progress = stopped, onPlay = {}) }
        waitUntilFocused("▶ Resume from 12:30")
        compose.waitForIdle()

        assertInsideTheSafeBand(compose.onNodeWithText("A Film"))
    }

    /** Down from Resume reaches the tab row inside the safe band, and Up again brings the title back. */
    @Test
    fun theTabsRiseToTheTopAndUpToResumeBringsTheTitleBack() {
        show { TvTitlePage(set = film, info = info, progress = stopped, onPlay = {}) }
        waitUntilFocused("▶ Resume from 12:30")
        compose.onNodeWithText("▶ Resume from 12:30").performKeyInput { pressKey(Key.DirectionDown) }
        waitUntilFocused("Overview")
        compose.waitForIdle()
        assertInsideTheSafeBand(compose.onNodeWithText("Overview"))

        compose.onNodeWithText("Overview").performKeyInput { pressKey(Key.DirectionUp) }
        waitUntilFocused("▶ Resume from 12:30")
        compose.waitForIdle()

        assertInsideTheSafeBand(compose.onNodeWithText("A Film"))
    }

    /** The same walk on a show's page: down to Episodes and its first season's picker, and back up to the show's name. */
    @Test
    fun upFromAShowsPickerReachesItsPillsAndName() {
        show { TvCollection(collection = show, info = info, watch = WatchSnapshot.Empty, onPlay = {}) }
        waitUntilFocused("+ My List")

        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        waitUntilFocused("Episodes")
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        waitUntilFocused("Season 1 · one episode")
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
        waitUntilFocused("Episodes")
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
        waitUntilFocused("+ My List")
        compose.waitForIdle()

        assertInsideTheSafeBand(compose.onNodeWithText("A Show"))
    }

    private fun assertInsideTheSafeBand(node: SemanticsNodeInteraction) {
        node.assertIsDisplayed()
        val inset = with(compose.density) { Overscan.vertical.toPx() }
        assertTrue(node.fetchSemanticsNode().boundsInRoot.top >= inset - 1f)
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { TvTheme { content() } }
    }

    private fun waitUntilFocused(text: String) {
        try {
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodes(hasText(text) and isFocused()).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            val focused = compose.onAllNodes(isFocused()).fetchSemanticsNodes().map { it.config.toString() }
            throw AssertionError("waited for \"$text\" to hold the remote; focused instead: $focused", e)
        }
    }

    private val film = set("f", Kind.MOVIE, "A Film", show = null, season = null).copy(year = 2004, durationSecs = 6780)
    private val stopped = Progress("f", at = 750.0, duration = 6780.0, updatedAt = 1)
    private val info =
        TitleInfo(
            overview = "A long synopsis of what happens. ".repeat(24).trim(),
            tagline = "A line of marketing",
            genres = "Drama, Comedy",
            rating = 6.5,
            network = null,
            status = null,
        )

    private val show =
        Entry.Collection(
            key = "show-a",
            kind = CollectionKind.SHOW,
            name = "A Show",
            posterPath = null,
            posterKey = null,
            count = 2,
            chapters = 2,
            divisions =
                listOf(
                    Division("Season 1", 1, listOf(set("e1", Kind.EPISODE, "Pilot", "A Show", 1)), emptyList()),
                    Division("Season 2", 2, listOf(set("e2", Kind.EPISODE, "Return", "A Show", 2)), emptyList()),
                ),
        )

    private fun set(
        id: String,
        kind: Kind,
        title: String,
        show: String?,
        season: Int?,
    ) = MediaSet(
        setId = id,
        kind = kind,
        title = title,
        show = show,
        chapter = null,
        path = null,
        season = season,
        episodeFirst = null,
        episodeLast = null,
        year = null,
        durationSecs = null,
        posterPath = null,
        totalBytes = 0,
        addedAt = 0,
    )
}
