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
import org.junit.Assert.assertEquals
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

    /** Resume sits low enough — under a line saying where — that a television's own rule would scroll to it. */
    @Test
    fun aTitlePageOpensWithItsTitleInsideTheOverscanInset() {
        show { TvTitlePage(set = film, info = info, progress = stopped, onPlay = {}) }
        waitUntilFocused("▶ Resume")
        compose.waitForIdle()

        assertAtTheTop(compose.onNodeWithText("A Film"))
    }

    @Test
    fun upToResumeFromTheOverviewBringsTheTitleBack() {
        show { TvTitlePage(set = film, info = info, progress = stopped, onPlay = {}) }
        waitUntilFocused("▶ Resume")
        compose.onNodeWithText("▶ Resume").performKeyInput { pressKey(Key.DirectionDown) }
        waitUntilFocused(info.overview!!)

        compose.onNodeWithText(info.overview!!).performKeyInput { pressKey(Key.DirectionUp) }
        waitUntilFocused("▶ Resume")
        compose.waitForIdle()

        assertAtTheTop(compose.onNodeWithText("A Film"))
    }

    /**
     * A show's art and overview stand taller than a screen leaves room for
     * above its first season, so opening it has to scroll; Up from there
     * reads the overview, and Up again brings the show's name back.
     */
    @Test
    fun upFromAShowsFirstSeasonReachesItsOverviewThenItsName() {
        show { TvCollection(collection = show, info = info, watch = WatchSnapshot.Empty, posterPath = { null }, onOpenTitle = {}, onOpenSeason = {}) }
        waitUntilFocused("Season 1")

        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
        waitUntilFocused(info.overview!!)
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
        waitUntilFocused("A Show")
        compose.waitForIdle()

        assertAtTheTop(compose.onNodeWithText("A Show"))
    }

    private fun assertAtTheTop(node: SemanticsNodeInteraction) {
        node.assertIsDisplayed()
        val inset = with(compose.density) { Overscan.vertical.toPx() }
        assertEquals(inset, node.fetchSemanticsNode().boundsInRoot.top, 1f)
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
